package com.sokoul.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Properties

class ServerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val local = ServerConfig.from(Properties())

    private fun ApplicationTestBuilder.app(
        config: ServerConfig = local,
        access: AccessGate = AccessGate(config),
        sessions: SessionAuthenticator? = null,
    ) {
        val services = Services(Database.open(tmp.newFolder()), hasher = PassphraseHasher(1_000), vaultKeyFile = java.io.File(tmp.newFolder(), "vault.key"))
        application { module(config, services, access, sessions ?: services.sessions) }
    }

    @Test fun `health answers without auth`() = testApplication {
        app()
        val r = client.get("/health")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"status":"ok","version":"$SERVER_VERSION"}""", r.bodyAsText())
    }

    @Test fun `api me is refused without a session`() = testApplication {
        app()
        val r = client.get("/api/me")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
        assertEquals("""{"error":"unauthorized"}""", r.bodyAsText())
    }

    @Test fun `api me answers once a session is found`() = testApplication {
        app(sessions = { SessionPrincipal("u1", "s1", "Ana", Role.OWNER) })
        val r = client.get("/api/me")
        assertEquals(HttpStatusCode.OK, r.status)
        assertEquals("""{"id":"u1","name":"Ana","role":"owner"}""", r.bodyAsText())
    }

    @Test fun `errors are stable codes without details`() = testApplication {
        app()
        val r = client.get("/nope?code=123")
        assertEquals(HttpStatusCode.NotFound, r.status)
        assertEquals("""{"error":"not_found"}""", r.bodyAsText())
    }

    @Test fun `unexpected failures answer internal_error without the message`() = testApplication {
        app(sessions = { error("secret detail") })
        val r = client.get("/api/me")
        assertEquals(HttpStatusCode.InternalServerError, r.status)
        assertEquals("""{"error":"internal_error"}""", r.bodyAsText())
    }

    @Test fun `with Access configured, the assertion is required and a valid one reaches the session gate`() = testApplication {
        val jwt = TestJwt()
        val now = System.currentTimeMillis() / 1000
        val config = ServerConfig.from(Properties().apply {
            setProperty("access.teamDomain", "team.cloudflareaccess.com")
            setProperty("access.audience", "aud-1")
        })
        app(config, AccessGate(config, AccessKeys({ jwt.jwks })))

        // As cloudflared delivers them: Cloudflare adds Cf-Connecting-IP to everything that came through the tunnel.
        val missing = client.get("/api/me") { header("Cf-Connecting-IP", "203.0.113.9") }
        assertEquals(HttpStatusCode.Forbidden, missing.status)
        assertEquals("""{"error":"access_denied"}""", missing.bodyAsText())

        val forged = client.get("/api/me") {
            header("Cf-Connecting-IP", "203.0.113.9")
            header(AccessGate.ASSERTION_HEADER, TestJwt().token("""{"aud":"aud-1","iss":"https://team.cloudflareaccess.com","exp":${now + 600}}""", kid = jwt.kid))
        }
        assertEquals(HttpStatusCode.Forbidden, forged.status)

        val valid = client.get("/api/me") {
            header("Cf-Connecting-IP", "203.0.113.9")
            header(AccessGate.ASSERTION_HEADER, jwt.token("""{"aud":["aud-1"],"iss":"https://team.cloudflareaccess.com","exp":${now + 600}}"""))
        }
        // Past the network lock, the session lock still refuses: no session token.
        assertEquals(HttpStatusCode.Unauthorized, valid.status)

        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

    @Test fun `local mode refuses requests relayed by Cloudflare`() = testApplication {
        app()
        val r = client.get("/api/me") { header("Cf-Connecting-IP", "203.0.113.9") }
        assertEquals(HttpStatusCode.Forbidden, r.status)
        assertTrue(r.bodyAsText().contains("access_denied"))
    }
}
