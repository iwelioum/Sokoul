package com.sokoul.server

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class AccessGateTest {
    private val team = "team.cloudflareaccess.com"
    private val aud = "aud-tag-123"
    private val now = 1_800_000_000L
    private val jwt = TestJwt()

    private fun accessConfig() = ServerConfig.from(Properties().apply {
        setProperty("access.teamDomain", team)
        setProperty("access.audience", aud)
    })

    private fun gate(fetches: IntArray = IntArray(1), clock: () -> Long = { 0L }) = AccessGate(
        accessConfig(),
        AccessKeys({ fetches[0]++; jwt.jwks }, nowMillis = clock),
        nowSeconds = { now },
    )

    private fun payload(
        aud: String = "[\"${this.aud}\"]",
        iss: String = "https://$team",
        exp: Long = now + 600,
        nbf: Long = now - 10,
    ) = """{"aud":$aud,"iss":"$iss","exp":$exp,"nbf":$nbf,"iat":${now - 10},"email":"owner@example.com"}"""

    /** A request as cloudflared delivers it: from loopback, with Cloudflare's headers, and [token] as the assertion. */
    private suspend fun AccessGate.check(token: String?, path: String = "/api/me", remote: String = "127.0.0.1", viaCloudflare: Boolean = true) =
        check(path, remote) { name ->
            when {
                name.equals(AccessGate.ASSERTION_HEADER, true) -> token
                viaCloudflare && name.equals("Cf-Connecting-IP", true) -> "203.0.113.9"
                else -> null
            }
        }

    @Test fun `valid assertion passes`() = runTest {
        assertEquals(AccessGate.Decision.Allow, gate().check(jwt.token(payload())))
        // A single string audience is accepted too, from any remote (the tunnel decides who reaches us).
        assertEquals(AccessGate.Decision.Allow, gate().check(jwt.token(payload(aud = "\"$aud\"")), remote = "203.0.113.9"))
    }

    @Test fun `missing assertion is denied`() = runTest {
        assertEquals(AccessGate.Decision.Deny("missing_assertion"), gate().check(null))
        assertEquals(AccessGate.Decision.Deny("missing_assertion"), gate().check(""))
    }

    @Test fun `health needs nothing`() = runTest {
        assertEquals(AccessGate.Decision.Allow, gate().check(null, path = "/health", remote = "203.0.113.9"))
    }

    @Test fun `wrong audience, issuer and expired tokens are denied`() = runTest {
        assertEquals(AccessGate.Decision.Deny("audience"), gate().check(jwt.token(payload(aud = "[\"other\"]"))))
        assertEquals(AccessGate.Decision.Deny("issuer"), gate().check(jwt.token(payload(iss = "https://other.cloudflareaccess.com"))))
        assertEquals(AccessGate.Decision.Deny("expired"), gate().check(jwt.token(payload(exp = now - 61))))
        assertEquals(AccessGate.Decision.Deny("not_yet_valid"), gate().check(jwt.token(payload(nbf = now + 61))))
    }

    @Test fun `leeway of 60 seconds`() = runTest {
        assertEquals(AccessGate.Decision.Allow, gate().check(jwt.token(payload(exp = now - 59))))
        assertEquals(AccessGate.Decision.Allow, gate().check(jwt.token(payload(nbf = now + 59))))
    }

    @Test fun `forged signatures are denied`() = runTest {
        val other = TestJwt()
        assertEquals(AccessGate.Decision.Deny("signature"), gate().check(jwt.token(payload(), signWith = other.keyPair)))
        // Payload swapped after signing.
        val good = jwt.token(payload()).split('.')
        val tampered = listOf(good[0], TestJwt.b64(payload(aud = "[\"x\",\"$aud\"]").toByteArray()), good[2]).joinToString(".")
        assertEquals(AccessGate.Decision.Deny("signature"), gate().check(tampered))
        // Unsigned / other algorithm.
        val none = TestJwt.b64("""{"alg":"none","kid":"${jwt.kid}"}""".toByteArray()) + "." + good[1] + "."
        assertEquals(AccessGate.Decision.Deny("algorithm"), gate().check(none))
        assertEquals(AccessGate.Decision.Deny("malformed"), gate().check("not-a-jwt"))
    }

    @Test fun `unknown kid refreshes keys at most every five minutes`() = runTest {
        val fetches = IntArray(1)
        var clock = 0L
        val g = gate(fetches) { clock }
        assertEquals(AccessGate.Decision.Allow, g.check(jwt.token(payload())))
        assertEquals(1, fetches[0])
        // Cached key: no fetch.
        g.check(jwt.token(payload()))
        assertEquals(1, fetches[0])
        // Unknown kid within 5 min: no fetch, denied.
        assertEquals(AccessGate.Decision.Deny("unknown_key"), g.check(jwt.token(payload(), kid = "rotated")))
        assertEquals(1, fetches[0])
        clock = 5 * 60_000L
        g.check(jwt.token(payload(), kid = "rotated"))
        assertEquals(2, fetches[0])
    }

    @Test fun `local mode accepts loopback only`() = runTest {
        val local = AccessGate(ServerConfig.from(Properties()))
        assertEquals(AccessGate.Decision.Allow, local.check(null, viaCloudflare = false, remote = "127.0.0.1"))
        assertEquals(AccessGate.Decision.Allow, local.check(null, viaCloudflare = false, remote = "0:0:0:0:0:0:0:1"))
        assertEquals(AccessGate.Decision.Deny("remote_address"), local.check(null, viaCloudflare = false, remote = "192.168.0.20"))
        assertEquals(AccessGate.Decision.Deny("remote_address"), local.check(null, viaCloudflare = false, remote = "203.0.113.9"))
        assertEquals(AccessGate.Decision.Deny("remote_address"), local.check(null, viaCloudflare = false, remote = "evil.example"))
    }

    @Test fun `local mode refuses traffic that came through Cloudflare`() = runTest {
        val local = AccessGate(ServerConfig.from(Properties()))
        val decision = local.check("/api/me", "127.0.0.1") { name -> if (name == "Cf-Connecting-IP") "203.0.113.9" else null }
        assertEquals(AccessGate.Decision.Deny("cloudflare_without_access"), decision)
    }

    @Test fun `lan mode adds private addresses`() {
        assertTrue(AccessGate.isAllowedLocal("192.168.0.20", lan = true))
        assertTrue(AccessGate.isAllowedLocal("10.1.2.3", lan = true))
        assertTrue(AccessGate.isAllowedLocal("172.16.0.1", lan = true))
        assertTrue(AccessGate.isAllowedLocal("fd12:3456::1", lan = true))
        assertTrue(AccessGate.isAllowedLocal("[fe80::1%eth0]", lan = true))
        assertFalse(AccessGate.isAllowedLocal("172.32.0.1", lan = true))
        assertFalse(AccessGate.isAllowedLocal("8.8.8.8", lan = true))
        assertFalse(AccessGate.isAllowedLocal("2001:db8::1", lan = true))
        assertFalse(AccessGate.isAllowedLocal("192.168.0.20", lan = false))
    }

    @Test fun `jwks parsing skips non-RSA keys`() {
        val doc = """{"keys":[{"kid":"ec","kty":"EC","crv":"P-256","x":"a","y":"b"},${jwt.jwks.removePrefix("{\"keys\":[").removeSuffix("]}")}],"public_cert":{"kid":"x","cert":"..."}}"""
        assertEquals(setOf(jwt.kid), AccessKeys.parseJwks(doc).keys)
    }

    @Test fun `in access mode home devices reach the server directly`() = runTest {
        val direct = { _: String -> null }
        assertEquals(AccessGate.Decision.Allow, gate().check("/api/me", "127.0.0.1", direct))
        assertEquals(AccessGate.Decision.Deny("missing_assertion"), gate().check("/api/me", "192.168.0.108", direct))
        assertEquals(AccessGate.Decision.Deny("missing_assertion"), gate().check("/api/me", "203.0.113.9", direct))
        val lanConfig = ServerConfig.from(Properties().apply {
            setProperty("access.teamDomain", team)
            setProperty("access.audience", aud)
            setProperty("lan", "true")
        })
        val lanGate = AccessGate(lanConfig, AccessKeys({ jwt.jwks }), nowSeconds = { now })
        assertEquals(AccessGate.Decision.Allow, lanGate.check("/api/me", "192.168.0.108", direct))
        assertEquals(AccessGate.Decision.Deny("missing_assertion"), lanGate.check(null, remote = "192.168.0.108"))
    }
}
