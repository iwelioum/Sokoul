package com.sokoul.server

import com.sokoul.server.TestServer.Companion.DAY
import com.sokoul.server.TestServer.Companion.HOUR
import com.sokoul.server.TestServer.Companion.MINUTE
import com.sokoul.server.TestServer.Companion.OWNER
import com.sokoul.server.TestServer.Companion.OWNER_PASS
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun lastUsed(s: TestServer): Long = s.db.query("SELECT last_used_at FROM sessions") { it.getLong(1) }.single()

    @Test fun `the database holds only the token's SHA-256, never the token`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val token = client.setupOwner()
        val raw = Secrets.fromB64url(token)!!
        assertEquals(32, raw.size)

        val hash = s.db.query("SELECT token_hash FROM sessions") { it.getBytes(1) }.single()
        assertArrayEquals(Secrets.sha256(raw), hash)

        // No column of any row, read as text or as bytes, contains the token.
        s.db.connection { c ->
            c.query("SELECT * FROM sessions") { rs ->
                for (i in 1..rs.metaData.columnCount) {
                    val bytes = rs.getBytes(i) ?: continue
                    assertFalse(String(bytes, Charsets.ISO_8859_1).contains(token))
                    assertFalse(bytes.contentEquals(raw))
                    assertFalse(String(bytes, Charsets.ISO_8859_1).contains(String(raw, Charsets.ISO_8859_1)))
                }
            }
        }
    }

    @Test fun `malformed or unknown bearer tokens are refused`() = testApplication {
        TestServer(tmp).install(this)
        client.setupOwner()
        for (value in listOf("Bearer", "Bearer ", "Basic abc", "Bearer not-base64!!", "Bearer " + Secrets.b64url(Secrets.bytes(32)))) {
            val r = client.get("/api/me") { header(HttpHeaders.Authorization, value) }
            assertEquals(value, HttpStatusCode.Unauthorized, r.status)
            assertEquals("""{"error":"unauthorized"}""", r.bodyAsText())
        }
    }

    @Test fun `a session expires 30 days after its last use and use slides it`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val token = client.setupOwner()
        val created = s.now

        s.advance(29 * DAY)
        assertEquals(HttpStatusCode.OK, client.getWith("/api/me", token).status)
        assertEquals(created + 29 * DAY, lastUsed(s))

        s.advance(29 * DAY) // 58 days after creation, 29 after the last use
        assertEquals(HttpStatusCode.OK, client.getWith("/api/me", token).status)

        s.advance(30 * DAY)
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/me", token).status)
    }

    @Test fun `last use is recorded at most once an hour`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val token = client.setupOwner()
        val created = s.now
        s.advance(30 * MINUTE)
        client.getWith("/api/me", token)
        assertEquals(created, lastUsed(s))
        s.advance(31 * MINUTE)
        client.getWith("/api/me", token)
        assertEquals(created + 61 * MINUTE, lastUsed(s))
        s.advance(HOUR - 1)
        client.getWith("/api/me", token)
        assertEquals(created + 61 * MINUTE, lastUsed(s))
    }

    @Test fun `the device list shows the caller's sessions and marks the current one`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val pc = client.setupOwner(device = "PC")
        s.advance(2 * HOUR)
        val phone = client.login(OWNER, OWNER_PASS, device = "Phone").json().str("token")

        val list = client.getWith("/api/sessions", phone).json().getValue("sessions").jsonArray.map { it.jsonObject }
        assertEquals(listOf("Phone", "PC"), list.map { it.str("deviceName") })
        assertEquals(listOf(true, false), list.map { it.str("current").toBoolean() })
        assertEquals(s.now, list[0].str("createdAt").toLong())
        assertEquals(s.now - 2 * HOUR, list[1].str("lastUsedAt").toLong())

        val fromPc = client.getWith("/api/sessions", pc).json().getValue("sessions").jsonArray.map { it.jsonObject }
        assertEquals(listOf(false, true), fromPc.map { it.getValue("current").jsonPrimitive.content.toBoolean() })
    }

    @Test fun `revoking a device ends its session, and only the owner of a session can revoke it`() = testApplication {
        TestServer(tmp).install(this)
        val pc = client.setupOwner(device = "PC")
        val tvLogin = client.login(OWNER, OWNER_PASS, device = "Salon TV").json()
        val tv = tvLogin.str("token")
        val tvId = tvLogin.str("sessionId")

        // A member cannot touch the owner's devices.
        val code = client.postJson("/api/invites", token = pc).json().str("code")
        val member = client.postJson("/api/auth/join", """{"code":"$code","name":"Sam","passphrase":"sam's long phrase"}""").json().str("token")
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/sessions/$tvId") { header(HttpHeaders.Authorization, "Bearer $member") }.status)
        assertEquals(HttpStatusCode.OK, client.getWith("/api/me", tv).status)

        val revoke = client.delete("/api/sessions/$tvId") { header(HttpHeaders.Authorization, "Bearer $pc") }
        assertEquals(HttpStatusCode.NoContent, revoke.status)
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/me", tv).status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/sessions/$tvId") { header(HttpHeaders.Authorization, "Bearer $pc") }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/sessions/nope") { header(HttpHeaders.Authorization, "Bearer $pc") }.status)

        val devices = client.getWith("/api/sessions", pc).json().getValue("sessions").jsonArray
        assertEquals(listOf("PC"), devices.map { (it as JsonObject).str("deviceName") })
    }

    @Test fun `logout ends the current session only`() = testApplication {
        TestServer(tmp).install(this)
        val pc = client.setupOwner()
        val phone = client.login(OWNER, OWNER_PASS).json().str("token")
        assertEquals(HttpStatusCode.NoContent, client.postJson("/api/auth/logout", token = phone).status)
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/me", phone).status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/api/auth/logout", token = phone).status)
        assertEquals(HttpStatusCode.OK, client.getWith("/api/me", pc).status)
    }
}
