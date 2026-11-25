package com.sokoul.server

import com.sokoul.server.TestServer.Companion.SECOND
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceLinksTest {
    @get:Rule val tmp = TemporaryFolder()

    private val userCodeShape = Regex("^[${Secrets.CODE_ALPHABET}]{4}-[${Secrets.CODE_ALPHABET}]{4}$")

    @Test fun `a TV links through a signed-in phone and gets its token exactly once`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val phone = client.setupOwner(device = "Phone")

        val start = client.postJson("/api/link/start", """{"deviceName":"Salon TV"}""")
        assertEquals(HttpStatusCode.OK, start.status)
        val link = start.json()
        val deviceCode = link.str("deviceCode")
        val userCode = link.str("userCode")
        assertEquals(32, Secrets.fromB64url(deviceCode)!!.size)
        assertTrue(userCode, userCodeShape.matches(userCode))
        assertEquals("/link?code=$userCode", link.str("verifyPath"))
        assertEquals("600", link.str("expiresIn"))
        assertEquals("3", link.str("interval"))

        val pending = client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""")
        assertEquals(428, pending.status.value)
        assertEquals("""{"error":"pending"}""", pending.bodyAsText())

        // Typed on the phone: lower case, no dash.
        val confirm = client.postJson("/api/link/confirm", """{"userCode":"${userCode.lowercase().replace("-", "")}"}""", token = phone)
        assertEquals(HttpStatusCode.OK, confirm.status)
        assertEquals("""{"deviceName":"Salon TV"}""", confirm.bodyAsText())

        s.advance(3 * SECOND)
        val linked = client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""")
        assertEquals(HttpStatusCode.OK, linked.status)
        val tv = linked.json().str("token")
        assertEquals(TestServer.OWNER, linked.json().getValue("user").jsonObject.str("name"))
        val me = client.getWith("/api/me", tv).json()
        assertEquals(TestServer.OWNER, me.str("name"))
        val devices = client.getWith("/api/sessions", phone).json().getValue("sessions").jsonArray.map { it.jsonObject.str("deviceName") }
        assertTrue(devices.contains("Salon TV"))

        s.advance(3 * SECOND)
        val again = client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""")
        assertEquals(HttpStatusCode.Gone, again.status)
        assertEquals("""{"error":"expired"}""", again.bodyAsText())
        assertEquals(2, s.db.query("SELECT COUNT(*) FROM sessions") { it.getInt(1) }.single())

        // Neither code is stored in clear.
        s.db.connection { c ->
            c.query("SELECT * FROM device_links") { rs ->
                for (i in 1..rs.metaData.columnCount) {
                    val text = rs.getBytes(i)?.let { String(it, Charsets.ISO_8859_1) } ?: continue
                    assertFalse(text.contains(deviceCode))
                    assertFalse(text.contains(userCode.replace("-", "")))
                }
            }
        }
    }

    @Test fun `confirming needs a session and the right live code, once`() = testApplication {
        TestServer(tmp).install(this)
        val phone = client.setupOwner()
        val userCode = client.postJson("/api/link/start", """{"deviceName":"TV"}""").json().str("userCode")

        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/api/link/confirm", """{"userCode":"$userCode"}""").status)
        for (wrong in listOf("BBBB-BBBB", "AAAA-AAAA", "12", "")) {
            val r = client.postJson("/api/link/confirm", """{"userCode":"$wrong"}""", token = phone)
            assertEquals(wrong, HttpStatusCode.NotFound, r.status)
            assertEquals("""{"error":"invalid_code"}""", r.bodyAsText())
        }
        assertEquals(HttpStatusCode.OK, client.postJson("/api/link/confirm", """{"userCode":"$userCode"}""", token = phone).status)
        assertEquals(HttpStatusCode.NotFound, client.postJson("/api/link/confirm", """{"userCode":"$userCode"}""", token = phone).status)
    }

    @Test fun `a link expires after 10 minutes`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val phone = client.setupOwner()
        val link = client.postJson("/api/link/start", """{"deviceName":"TV"}""").json()
        s.advance(600 * SECOND)
        val confirm = client.postJson("/api/link/confirm", """{"userCode":"${link.str("userCode")}"}""", token = phone)
        assertEquals(HttpStatusCode.NotFound, confirm.status)
        val poll = client.postJson("/api/link/poll", """{"deviceCode":"${link.str("deviceCode")}"}""")
        assertEquals(HttpStatusCode.Gone, poll.status)
        // Unknown and malformed device codes look the same.
        assertEquals(HttpStatusCode.Gone, client.postJson("/api/link/poll", """{"deviceCode":"${Secrets.b64url(Secrets.bytes(32))}"}""").status)
        assertEquals(HttpStatusCode.Gone, client.postJson("/api/link/poll", """{"deviceCode":"x"}""").status)
    }

    @Test fun `polling faster than the interval is slowed down`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        client.setupOwner()
        val deviceCode = client.postJson("/api/link/start", """{"deviceName":"TV"}""").json().str("deviceCode")
        assertEquals(428, client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""").status.value)
        s.advance(2 * SECOND)
        val fast = client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""")
        assertEquals(HttpStatusCode.TooManyRequests, fast.status)
        assertEquals("""{"error":"slow_down"}""", fast.bodyAsText())
        assertNotNull(fast.headers["Retry-After"])
        s.advance(1 * SECOND)
        assertEquals(428, client.postJson("/api/link/poll", """{"deviceCode":"$deviceCode"}""").status.value)
    }

    @Test fun `link routes need no session but stay behind the network lock`() = testApplication {
        TestServer(tmp).install(this)
        val viaTunnel: io.ktor.client.request.HttpRequestBuilder.() -> Unit = { header("Cf-Connecting-IP", "203.0.113.9") }
        assertEquals(HttpStatusCode.Forbidden, client.postJson("/api/link/start", """{"deviceName":"TV"}""", extra = viaTunnel).status)
        assertEquals(HttpStatusCode.Forbidden, client.postJson("/api/link/poll", """{"deviceCode":"x"}""", extra = viaTunnel).status)
        assertEquals(HttpStatusCode.Forbidden, client.get("/link") { viaTunnel() }.status)
        assertEquals(HttpStatusCode.OK, client.postJson("/api/link/start", """{}""").status)
        assertEquals(HttpStatusCode.OK, client.get("/link").status)
    }

    @Test fun `the link page speaks French or English and loads nothing from outside`() = testApplication {
        TestServer(tmp).install(this)
        val fr = client.get("/link?code=BCDF-GHJK") { header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8") }
        assertEquals(HttpStatusCode.OK, fr.status)
        val html = fr.bodyAsText()
        assertTrue(html.contains("<html lang=\"fr\">"))
        assertTrue(html.contains("Connecter une TV"))
        assertFalse(Regex("(src|href)=\"https?:").containsMatchIn(html))
        val csp = fr.headers["Content-Security-Policy"].orEmpty()
        assertTrue(csp.startsWith("default-src 'none'"))
        assertEquals("no-store", fr.headers["Cache-Control"])

        val en = client.get("/link").bodyAsText()
        assertTrue(en.contains("<html lang=\"en\">"))
        assertTrue(en.contains("Link a TV"))

        assertEquals("en", LinkPage.language("en-US,fr;q=0.5"))
        assertEquals("fr", LinkPage.language("de-DE, fr;q=0.7, en;q=0.3"))
        assertEquals("en", LinkPage.language("de"))
        assertEquals("en", LinkPage.language(null))
    }
}
