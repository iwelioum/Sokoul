package com.sokoul.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import org.slf4j.LoggerFactory
import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.time.Duration
import java.util.Base64

/**
 * First lock of design D4: who may reach the server at all.
 *
 * - **Access mode** (`access.teamDomain` + `access.audience` set): every request that came through Cloudflare, and every
 *   request from outside the home, must carry a `Cf-Access-Jwt-Assertion` signed (RS256) by the team's Cloudflare
 *   Access keys, for this application's audience, from this team's issuer, within its validity window (60 s leeway).
 *   Direct requests from this machine, or from the LAN when `lan=true`, pass without one.
 * - **Local mode** (no Access): only loopback callers, plus private-LAN callers when `lan=true`. A request that went
 *   through Cloudflare (it carries `Cf-*` headers) is refused, so a tunnel set up before Access is configured does not
 *   open the server to the internet.
 *
 * The token is never logged; refusals log only the reason.
 */
class AccessGate(
    private val config: ServerConfig,
    private val keys: AccessKeys? = config.access?.let { AccessKeys(HttpKeyFetcher(it.certsUrl)) },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    sealed interface Decision {
        data object Allow : Decision
        data class Deny(val reason: String) : Decision
    }

    /** Decides for one request. [header] looks a request header up by name (case-insensitive). */
    suspend fun check(path: String, remoteAddress: String, header: (String) -> String?): Decision {
        if (path == HEALTH_PATH) return Decision.Allow
        val access = config.access
        if (access == null) {
            if (CLOUDFLARE_HEADERS.any { header(it) != null }) return Decision.Deny("cloudflare_without_access")
            return if (isAllowedLocal(remoteAddress, config.lan)) Decision.Allow else Decision.Deny("remote_address")
        }
        // Devices at home (the PC itself, and the TV or tablet on the LAN when `lan=true`) reach the server directly; only
        // what came through Cloudflare, which always adds its `Cf-*` headers, must show an Access assertion.
        val throughCloudflare = CLOUDFLARE_HEADERS.any { header(it) != null }
        if (!throughCloudflare && isAllowedLocal(remoteAddress, config.lan)) return Decision.Allow
        val token = header(ASSERTION_HEADER)?.trim()
        if (token.isNullOrEmpty()) return Decision.Deny("missing_assertion")
        return verifyAssertion(token, access)
    }

    private suspend fun verifyAssertion(token: String, access: ServerConfig.AccessConfig): Decision {
        val parts = token.split('.')
        if (parts.size != 3) return Decision.Deny("malformed")
        val header = decodeJson(parts[0]) ?: return Decision.Deny("malformed")
        val payload = decodeJson(parts[1]) ?: return Decision.Deny("malformed")
        val signature = decodeB64(parts[2]) ?: return Decision.Deny("malformed")

        if (header.string("alg") != "RS256") return Decision.Deny("algorithm")
        val kid = header.string("kid") ?: return Decision.Deny("no_kid")
        val key = keys?.key(kid) ?: return Decision.Deny("unknown_key")

        val ok = runCatching {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
                verify(signature)
            }
        }.getOrDefault(false)
        if (!ok) return Decision.Deny("signature")

        if (payload.string("iss") != access.issuer) return Decision.Deny("issuer")
        val aud = when (val a = payload["aud"]) {
            is JsonPrimitive -> listOfNotNull(a.contentOrNull)
            is JsonArray -> a.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        if (access.audience !in aud) return Decision.Deny("audience")
        val now = nowSeconds()
        val exp = payload.long("exp") ?: return Decision.Deny("no_expiry")
        if (now > exp + LEEWAY_SECONDS) return Decision.Deny("expired")
        val nbf = payload.long("nbf")
        if (nbf != null && now < nbf - LEEWAY_SECONDS) return Decision.Deny("not_yet_valid")
        return Decision.Allow
    }

    companion object {
        const val HEALTH_PATH = "/health"
        const val ASSERTION_HEADER = "Cf-Access-Jwt-Assertion"
        const val LEEWAY_SECONDS = 60L
        private val CLOUDFLARE_HEADERS = listOf(ASSERTION_HEADER, "Cf-Connecting-IP", "Cf-Ray", "Cf-Access-Authenticated-User-Email")
        private val log = LoggerFactory.getLogger(AccessGate::class.java)
        private val json = Json { ignoreUnknownKeys = true }
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

        private fun decodeB64(s: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(s) }.getOrNull()

        private fun decodeJson(s: String): JsonObject? = decodeB64(s)?.let { bytes ->
            runCatching { json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }.getOrNull()
        }

        private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.long(name: String): Long? = (this[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

        /**
         * Whether [remoteAddress] (an IP literal from the socket; never resolved through DNS) is loopback, or a
         * private-LAN address when [lan] is on.
         */
        fun isAllowedLocal(remoteAddress: String, lan: Boolean): Boolean {
            val address = parseIpLiteral(remoteAddress) ?: return remoteAddress == "localhost"
            if (address.isLoopbackAddress) return true
            if (!lan) return false
            return when (address) {
                is Inet4Address -> address.isSiteLocalAddress || address.isLinkLocalAddress
                // fc00::/7 (unique local) and fe80::/10 (link local).
                is Inet6Address -> address.isLinkLocalAddress || (address.address[0].toInt() and 0xFE) == 0xFC
                else -> false
            }
        }

        private fun parseIpLiteral(raw: String): InetAddress? {
            val s = raw.trim().removePrefix("[").removeSuffix("]").substringBefore('%')
            val literal = IPV4.matches(s) || (s.contains(':') && s.all { it.isLetterOrDigit() || it == ':' || it == '.' })
            if (!literal) return null
            // A literal is parsed, not looked up. An IPv4-mapped IPv6 address comes back as an Inet4Address.
            return runCatching { InetAddress.getByName(s) }.getOrNull()
        }

        /**
         * A request made on this machine itself: a loopback caller, and no Cloudflare header (so not relayed by the
         * tunnel, whose `cloudflared` also connects from loopback). Used for first-start setup.
         */
        fun isDirectLoopback(remoteAddress: String, header: (String) -> String?): Boolean =
            CLOUDFLARE_HEADERS.none { header(it) != null } && isAllowedLocal(remoteAddress, lan = false)

        internal fun logDenied(reason: String, method: String, path: String) {
            log.warn("Access denied ({}) for {} {}", reason, method, path)
        }
    }
}

/** Downloads the team's JWKS document (`/cdn-cgi/access/certs`). Injectable for tests. */
fun interface KeyFetcher {
    suspend fun fetchJwks(): String
}

class HttpKeyFetcher(private val url: String) : KeyFetcher {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    override suspend fun fetchJwks(): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Access certs answered ${response.statusCode()}" }
        response.body()
    }
}

/**
 * Cache of the team's RSA keys by `kid`. An unknown `kid` triggers a refresh, at most once per
 * [refreshIntervalMillis] (5 min), so forged tokens with random key ids cannot make the server hammer Cloudflare.
 */
class AccessKeys(
    private val fetcher: KeyFetcher,
    private val refreshIntervalMillis: Long = 5 * 60_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    @Volatile private var keys: Map<String, RSAPublicKey> = emptyMap()
    private var lastFetch: Long? = null

    suspend fun key(kid: String): RSAPublicKey? {
        keys[kid]?.let { return it }
        return mutex.withLock {
            keys[kid]?.let { return@withLock it }
            val last = lastFetch
            if (last != null && nowMillis() - last < refreshIntervalMillis) return@withLock null
            lastFetch = nowMillis()
            val fresh = runCatching { parseJwks(fetcher.fetchJwks()) }
                .onFailure { log.warn("Could not refresh Access keys: {}", it::class.simpleName) }
                .getOrNull()
            if (!fresh.isNullOrEmpty()) keys = fresh
            keys[kid]
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccessKeys::class.java)
        private val json = Json { ignoreUnknownKeys = true }

        /** RSA keys of a JWKS document by `kid`; other key types are skipped. */
        fun parseJwks(document: String): Map<String, RSAPublicKey> {
            val root = json.parseToJsonElement(document).jsonObject
            val list = root["keys"] as? JsonArray ?: return emptyMap()
            val factory = KeyFactory.getInstance("RSA")
            return list.mapNotNull { element ->
                val jwk = element as? JsonObject ?: return@mapNotNull null
                fun field(name: String) = (jwk[name] as? JsonPrimitive)?.contentOrNull
                if (field("kty") != "RSA") return@mapNotNull null
                val kid = field("kid") ?: return@mapNotNull null
                val n = field("n") ?: return@mapNotNull null
                val e = field("e") ?: return@mapNotNull null
                runCatching {
                    val decoder = Base64.getUrlDecoder()
                    val spec = RSAPublicKeySpec(BigInteger(1, decoder.decode(n)), BigInteger(1, decoder.decode(e)))
                    kid to factory.generatePublic(spec) as RSAPublicKey
                }.getOrNull()
            }.toMap()
        }
    }
}
