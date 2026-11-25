package com.sokoul.server

import com.sokoul.server.TestServer.Companion.DAY
import com.sokoul.server.TestServer.Companion.MINUTE
import com.sokoul.server.TestServer.Companion.OWNER
import com.sokoul.server.TestServer.Companion.OWNER_PASS
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

class AccountsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `passphrases use PBKDF2-SHA256 with 310000 rounds and a 16-byte salt`() {
        val hasher = PassphraseHasher()
        assertEquals(310_000, hasher.iterations)
        val h = hasher.hash("correct horse battery")
        assertEquals(16, h.salt.size)
        assertEquals(32, h.hash.size)
        assertEquals(310_000, h.iterations)
        assertTrue(hasher.verify("correct horse battery", h))
        assertFalse(hasher.verify("correct horse batterY", h))
        // Two hashes of one passphrase differ (fresh salt).
        assertFalse(hasher.hash("correct horse battery").hash.contentEquals(h.hash))
    }

    @Test fun `setup creates the owner once, stores no clear passphrase and returns a working session`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        assertEquals("""{"needsOwner":true}""", client.get("/api/setup").bodyAsText())
        val r = client.postJson("/api/setup", """{"name":"  $OWNER ","passphrase":"$OWNER_PASS","deviceName":"PC"}""")
        assertEquals("""{"needsOwner":false}""", client.get("/api/setup").bodyAsText())
        assertEquals(HttpStatusCode.Created, r.status)
        val body = r.json()
        val me = client.getWith("/api/me", body.str("token")).json()
        assertEquals(OWNER, me.str("name"))
        assertEquals("owner", me.str("role"))

        val row = s.db.query("SELECT passphrase_hash, passphrase_salt, passphrase_iterations FROM users") {
            Triple(it.getBytes(1), it.getBytes(2), it.getInt(3))
        }.single()
        assertEquals(16, row.second.size)
        assertEquals(1_000, row.third)
        assertFalse(String(row.first, Charsets.ISO_8859_1).contains(OWNER_PASS))

        val again = client.postJson("/api/setup", """{"name":"Bob","passphrase":"another long passphrase"}""")
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("""{"error":"already_set_up"}""", again.bodyAsText())
        assertEquals(1, s.db.query("SELECT COUNT(*) FROM users") { it.getInt(1) }.single())
    }

    @Test fun `setup checks the passphrase length and the name`() = testApplication {
        TestServer(tmp).install(this)
        val short = client.postJson("/api/setup", """{"name":"Ana","passphrase":"elevenchars"}""")
        assertEquals(HttpStatusCode.BadRequest, short.status)
        assertEquals("""{"error":"weak_passphrase"}""", short.bodyAsText())
        val noName = client.postJson("/api/setup", """{"name":"   ","passphrase":"$OWNER_PASS"}""")
        assertEquals("""{"error":"invalid_name"}""", noName.bodyAsText())
        val malformed = client.postJson("/api/setup", """{"name":"Ana"}""")
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test fun `setup is refused when the request came through the tunnel`() = testApplication {
        val jwt = TestJwt()
        val config = ServerConfig.from(Properties().apply {
            setProperty("access.teamDomain", "team.cloudflareaccess.com")
            setProperty("access.audience", "aud-1")
        })
        val s = TestServer(tmp, config)
        s.install(this, AccessGate(config, AccessKeys({ jwt.jwks })))
        val assertion = jwt.token("""{"aud":"aud-1","iss":"https://team.cloudflareaccess.com","exp":${System.currentTimeMillis() / 1000 + 600}}""")
        val r = client.postJson("/api/setup", """{"name":"Ana","passphrase":"$OWNER_PASS"}""") {
            header("Cf-Connecting-IP", "203.0.113.9")
            header(AccessGate.ASSERTION_HEADER, assertion)
        }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertEquals("""{"error":"setup_local_only"}""", r.bodyAsText())
        assertFalse(s.services.accounts.hasUsers())
    }

    @Test fun `login accepts the right passphrase, any name case, and refuses the rest alike`() = testApplication {
        TestServer(tmp).install(this)
        client.setupOwner()
        val ok = client.login("ana", OWNER_PASS)
        assertEquals(HttpStatusCode.OK, ok.status)
        assertEquals("owner", ok.json().getValue("user").let { (it as kotlinx.serialization.json.JsonObject).str("role") })

        val wrong = client.login(OWNER, "wrong passphrase!")
        val unknown = client.login("Nobody", OWNER_PASS)
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals(wrong.bodyAsText(), unknown.bodyAsText())
        assertEquals("""{"error":"invalid_credentials"}""", wrong.bodyAsText())
    }

    @Test fun `five failures from one address lock sign-in for 15 minutes`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        client.setupOwner()
        // Different names, same address: the address is what gets locked.
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.login("guess$it", "not the passphrase").status) }
        val locked = client.login(OWNER, OWNER_PASS)
        assertEquals(HttpStatusCode.TooManyRequests, locked.status)
        assertEquals("""{"error":"too_many_attempts"}""", locked.bodyAsText())
        assertNotNull(locked.headers["Retry-After"])

        s.advance(14 * MINUTE)
        assertEquals(HttpStatusCode.TooManyRequests, client.login(OWNER, OWNER_PASS).status)
        s.advance(1 * MINUTE)
        assertEquals(HttpStatusCode.OK, client.login(OWNER, OWNER_PASS).status)
    }

    @Test fun `five failures on one account lock it from every address`() = testApplication {
        val jwt = TestJwt()
        val config = ServerConfig.from(Properties().apply {
            setProperty("access.teamDomain", "team.cloudflareaccess.com")
            setProperty("access.audience", "aud-1")
        })
        TestServer(tmp, config).install(this, AccessGate(config, AccessKeys({ jwt.jwks })))
        client.setupOwner() // direct, from this machine
        val assertion = jwt.token("""{"aud":"aud-1","iss":"https://team.cloudflareaccess.com","exp":${System.currentTimeMillis() / 1000 + 600}}""")
        repeat(5) { i ->
            val r = client.login(OWNER, "not the passphrase") {
                header("Cf-Connecting-IP", "203.0.113.$i")
                header(AccessGate.ASSERTION_HEADER, assertion)
            }
            assertEquals(HttpStatusCode.Unauthorized, r.status)
        }
        val fresh = client.login(OWNER, OWNER_PASS) {
            header("Cf-Connecting-IP", "198.51.100.7")
            header(AccessGate.ASSERTION_HEADER, assertion)
        }
        assertEquals(HttpStatusCode.TooManyRequests, fresh.status)
    }

    @Test fun `failure limiter counts per key within a sliding window`() {
        var now = 0L
        val limiter = FailureLimiter({ now })
        repeat(4) { limiter.recordFailure("a") }
        assertEquals(null, limiter.retryAfterSeconds("a"))
        limiter.recordFailure("a")
        assertEquals(900L, limiter.retryAfterSeconds("a", "b"))
        assertEquals(null, limiter.retryAfterSeconds("b"))
        now += 15 * MINUTE
        assertEquals(null, limiter.retryAfterSeconds("a"))
        repeat(5) { limiter.recordFailure("c") }
        limiter.reset("c")
        assertEquals(null, limiter.retryAfterSeconds("c"))
    }

    @Test fun `only the owner invites, and a member joins once with the code`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val owner = client.setupOwner()
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/api/invites").status)

        val invite = client.postJson("/api/invites", token = owner)
        assertEquals(HttpStatusCode.Created, invite.status)
        val body = invite.json()
        val code = body.str("code")
        assertTrue(code, Regex("^[${Secrets.CODE_ALPHABET}]{4}(-[${Secrets.CODE_ALPHABET}]{4}){2}$").matches(code))
        assertEquals(s.now + DAY, body.str("expiresAt").toLong())
        // Stored hashed only.
        val stored = s.db.query("SELECT code_hash FROM invites") { it.getBytes(1) }.single()
        assertArrayEquals(Secrets.sha256(code.replace("-", "")), stored)

        val joined = client.postJson("/api/auth/join", """{"code":"${code.lowercase()}","name":"Sam","passphrase":"sam's long phrase","deviceName":"Tablet"}""")
        assertEquals(HttpStatusCode.Created, joined.status)
        val member = joined.json().str("token")
        val me = client.getWith("/api/me", member).json()
        assertEquals("Sam", me.str("name"))
        assertEquals("member", me.str("role"))

        val reuse = client.postJson("/api/auth/join", """{"code":"$code","name":"Eve","passphrase":"eve's long phrase"}""")
        assertEquals(HttpStatusCode.BadRequest, reuse.status)
        assertEquals("""{"error":"invalid_invite"}""", reuse.bodyAsText())

        val byMember = client.postJson("/api/invites", token = member)
        assertEquals(HttpStatusCode.Forbidden, byMember.status)
        assertEquals("""{"error":"owner_only"}""", byMember.bodyAsText())

        assertEquals(HttpStatusCode.OK, client.login("sam", "sam's long phrase").status)
    }

    @Test fun `an invitation expires after 24 hours and names stay unique`() = testApplication {
        val s = TestServer(tmp).also { it.install(this) }
        val owner = client.setupOwner()
        val first = client.postJson("/api/invites", token = owner).json().str("code")
        val taken = client.postJson("/api/auth/join", """{"code":"$first","name":"ANA","passphrase":"a long passphrase"}""")
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("""{"error":"name_taken"}""", taken.bodyAsText())

        s.advance(DAY)
        val late = client.postJson("/api/auth/join", """{"code":"$first","name":"Sam","passphrase":"a long passphrase"}""")
        assertEquals(HttpStatusCode.BadRequest, late.status)
        assertEquals("""{"error":"invalid_invite"}""", late.bodyAsText())

        val wrong = client.postJson("/api/auth/join", """{"code":"BBBB-BBBB-BBBB","name":"Sam","passphrase":"a long passphrase"}""")
        assertEquals("""{"error":"invalid_invite"}""", wrong.bodyAsText())
        assertEquals(1, s.db.query("SELECT COUNT(*) FROM users") { it.getInt(1) }.single())
    }
}
