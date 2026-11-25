package com.sokoul.pc.account

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.pc.data.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * A secret the device keeps (a session token, a link's device code). Its [toString] never shows it, so it cannot reach
 * a log, an error or a data class's text by accident; [reveal] is called only where the value goes on the wire or to
 * storage.
 */
class Secret(private val value: String) {
    fun reveal(): String = value
    override fun toString(): String = "Secret(***)"
    override fun equals(other: Any?): Boolean = other is Secret && other.value == value
    override fun hashCode(): Int = value.hashCode()
}

/** Who is signed in: `owner` or `member`. */
@Serializable
data class AccountUser(val id: String, val name: String, val role: String) {
    val owner: Boolean get() = role == "owner"
}

/** This device's Sokoul session: what later code (profiles and progress sync, Lot 6) calls the server with. */
data class SokoulSession(val server: String, val token: Secret, val sessionId: String, val user: AccountUser?)

/** One signed-in device of the account, as `GET /api/sessions` lists it. */
@Serializable
data class DeviceSession(val id: String, val deviceName: String, val createdAt: Long, val lastUsedAt: Long, val current: Boolean)

/** A TV link in progress: the code to show, the QR's address, and the secret only this device polls with. */
data class LinkCode(val deviceCode: Secret, val userCode: String, val verifyUrl: String, val expiresAt: Long, val intervalSeconds: Int)

/** Why a call failed, in words a person understands. [code] is the server's stable code, for tests and decisions. */
data class AccountProblem(val code: String, val message: L, val retryAfterSeconds: Long? = null) {
    fun text(): String = message.text()
}

sealed interface AccountResult<out T> {
    data class Ok<T>(val value: T) : AccountResult<T>
    data class Failed(val problem: AccountProblem) : AccountResult<Nothing>
}

/** How a TV link ended: the device is signed in, the code ran out (show a new one), or something to tell the person. */
sealed interface LinkOutcome {
    data class Linked(val session: SokoulSession) : LinkOutcome
    data object Expired : LinkOutcome
    data class Failed(val problem: AccountProblem) : LinkOutcome
}

private val accountJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** Where the address and the session live. The token is stored like the app's service keys (ProviderConfig). */
interface AccountStore {
    var serverUrl: String?
    var session: StoredSession?
}

data class StoredSession(val token: Secret, val sessionId: String, val user: AccountUser?)

/** Kept in the app's database: the address as setting `sokoul_server_url`, the token as the key `sokoul_session`. */
class DbAccountStore(private val db: DesktopDb) : AccountStore {
    override var serverUrl: String?
        get() = ProviderConfig.sokoulServerUrl(db)
        set(value) = ProviderConfig.setSokoulServerUrl(db, value)

    override var session: StoredSession?
        get() {
            val token = ProviderConfig.key(db, SESSION_KEY) ?: return null
            val meta = db.setting(SESSION_META)?.let { runCatching { accountJson.decodeFromString<SessionMeta>(it) }.getOrNull() } ?: return null
            return StoredSession(Secret(token), meta.sessionId, meta.user)
        }
        set(value) {
            ProviderConfig.setKey(db, SESSION_KEY, value?.token?.reveal())
            db.putSetting(SESSION_META, value?.let { accountJson.encodeToString(SessionMeta(it.sessionId, it.user)) })
        }

    @Serializable private data class SessionMeta(val sessionId: String, val user: AccountUser? = null)

    companion object {
        const val SESSION_KEY = "sokoul_session"
        private const val SESSION_META = "sokoul_session_meta"
    }
}

/** In memory: tests and renders. */
class MemoryAccountStore(override var serverUrl: String? = null, override var session: StoredSession? = null) : AccountStore

/**
 * The app side of the private Sokoul server's accounts (docs/server.md): sign-in, the TV link by code and QR, the
 * devices of the account, sign-out. Every call runs on the IO dispatcher and answers with a plain-language problem
 * instead of throwing. Nothing here logs; problems never carry a token, a passphrase or a device code.
 */
class SokoulAccount(
    private val store: AccountStore,
    private val http: OkHttpClient,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The server's address without a trailing slash, or null when none was given. */
    val server: String? get() = store.serverUrl

    /** The session of this device, or null when signed out. Later code (sync) calls the server with it. */
    fun session(): SokoulSession? {
        val base = store.serverUrl ?: return null
        val s = store.session ?: return null
        return SokoulSession(base, s.token, s.sessionId, s.user)
    }

    /** Checks that [address] answers as a Sokoul server (`/health`), then keeps it. A new address signs this device out. */
    suspend fun setServer(address: String): AccountResult<String> {
        val url = normalizeServer(address) ?: return AccountResult.Failed(Problems.badAddress)
        val health = call(Request.Builder().url("$url/health").get().build())
        if (health is Call.Failed) return AccountResult.Failed(health.problem)
        health as Call.Answer
        val ok = health.status == 200 && runCatching { json.decodeFromString<HealthBody>(health.body).status == "ok" }.getOrDefault(false)
        if (!ok) return AccountResult.Failed(if (health.status == 403) Problems.forCode("access_denied") else Problems.notSokoul)
        if (url != store.serverUrl) store.session = null
        store.serverUrl = url
        return AccountResult.Ok(url)
    }

    /** Signs in with a name and passphrase (PC, tablet: a device with a keyboard). */
    suspend fun login(name: String, passphrase: String, deviceName: String): AccountResult<SokoulSession> =
        signIn("/api/auth/login", json.encodeToString(LoginBody(name.trim(), passphrase, deviceName)))

    /** Creates the owner on a server that has no account yet; the server accepts it only from its own PC. */
    /** Whether the server still waits for its owner (null when it cannot say: offline, or an older server). */
    suspend fun needsOwner(): Boolean? {
        val base = store.serverUrl ?: return null
        val r = call(Request.Builder().url("$base/api/setup").get().build()) as? Call.Answer ?: return null
        if (r.status != 200) return null
        return runCatching { json.decodeFromString<SetupStatus>(r.body).needsOwner }.getOrNull()
    }
    @Serializable private data class SetupStatus(val needsOwner: Boolean)

    suspend fun setupOwner(name: String, passphrase: String, deviceName: String): AccountResult<SokoulSession> =
        signIn("/api/setup", json.encodeToString(LoginBody(name.trim(), passphrase, deviceName)))

    private suspend fun signIn(path: String, body: String): AccountResult<SokoulSession> {
        val base = store.serverUrl ?: return AccountResult.Failed(Problems.noServer)
        return when (val r = call(post("$base$path", body))) {
            is Call.Failed -> AccountResult.Failed(r.problem)
            is Call.Answer -> if (r.status in 200..299) {
                val parsed = runCatching { json.decodeFromString<SessionBody>(r.body) }.getOrNull()
                    ?: return AccountResult.Failed(Problems.notSokoul)
                store.session = StoredSession(Secret(parsed.token), parsed.sessionId, parsed.user)
                AccountResult.Ok(session()!!)
            } else AccountResult.Failed(r.problem())
        }
    }

    /** Asks the server for a code to show on this device (no session needed). */
    suspend fun startLink(deviceName: String): AccountResult<LinkCode> {
        val base = store.serverUrl ?: return AccountResult.Failed(Problems.noServer)
        return when (val r = call(post("$base/api/link/start", json.encodeToString(LinkStartBody(deviceName))))) {
            is Call.Failed -> AccountResult.Failed(r.problem)
            is Call.Answer -> if (r.status == 200) {
                val b = runCatching { json.decodeFromString<LinkStartAnswer>(r.body) }.getOrNull()
                    ?: return AccountResult.Failed(Problems.notSokoul)
                AccountResult.Ok(LinkCode(Secret(b.deviceCode), b.userCode, base + b.verifyPath, clock() + b.expiresIn * 1000L, b.interval.coerceAtLeast(1)))
            } else AccountResult.Failed(r.problem())
        }
    }

    /**
     * Polls until the code is confirmed on a phone or computer, respecting the server's interval: `428 pending` waits,
     * `429 slow_down` waits 5 s longer each time (RFC 8628), `410 expired` (or the code's own time running out) ends with
     * [LinkOutcome.Expired]. A network hiccup is retried with a growing wait until the code runs out. Once linked, the
     * session is stored and the account's name read.
     */
    suspend fun awaitLink(code: LinkCode): LinkOutcome {
        val base = store.serverUrl ?: return LinkOutcome.Failed(Problems.noServer)
        var interval = code.intervalSeconds.toLong()
        var waitSeconds = interval
        val body = json.encodeToString(LinkPollBody(code.deviceCode.reveal()))
        while (true) {
            sleep(waitSeconds * 1000)
            if (clock() >= code.expiresAt) return LinkOutcome.Expired
            when (val r = call(post("$base/api/link/poll", body))) {
                is Call.Failed -> waitSeconds = (waitSeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
                is Call.Answer -> when {
                    r.status == 200 -> {
                        val t = runCatching { json.decodeFromString<LinkTokenAnswer>(r.body) }.getOrNull()
                            ?: return LinkOutcome.Failed(Problems.notSokoul)
                        store.session = StoredSession(Secret(t.token), t.sessionId, t.user)
                        // An older server answers without the user: its name then comes from /api/me.
                        if (t.user == null) when (val me = me()) {
                            is AccountResult.Ok -> Unit
                            is AccountResult.Failed -> if (store.session == null) return LinkOutcome.Failed(me.problem)
                        }
                        return LinkOutcome.Linked(session()!!)
                    }
                    r.status == 428 -> waitSeconds = interval
                    r.status == 429 && r.code == "slow_down" -> {
                        interval += SLOW_DOWN_STEP_SECONDS
                        waitSeconds = maxOf(interval, r.retryAfter ?: 0)
                    }
                    r.status == 410 -> return LinkOutcome.Expired
                    r.status >= 500 -> waitSeconds = (waitSeconds * 2).coerceAtMost(MAX_BACKOFF_SECONDS)
                    else -> return LinkOutcome.Failed(r.problem())
                }
            }
        }
    }

    /** Who is signed in. Refreshes the stored name and role. */
    suspend fun me(): AccountResult<AccountUser> = authed(Request.Builder().get(), "/api/me") { body ->
        json.decodeFromString<AccountUser>(body).also { user -> store.session?.let { store.session = it.copy(user = user) } }
    }

    /** The account's signed-in devices, most recent first. */
    suspend fun sessions(): AccountResult<List<DeviceSession>> = authed(Request.Builder().get(), "/api/sessions") { body ->
        json.decodeFromString<SessionsAnswer>(body).sessions
    }

    /** Signs another device out. Revoking this device's own session is a sign-out. */
    suspend fun revoke(sessionId: String): AccountResult<Unit> {
        if (sessionId == store.session?.sessionId) return logout()
        return authed(Request.Builder().delete(), "/api/sessions/$sessionId") { }
    }

    /** Signs this device out: the server forgets the session, and so does the device, even when the server is unreachable. */
    suspend fun logout(): AccountResult<Unit> {
        val r = authed(Request.Builder().post(EMPTY_JSON.toRequestBody(JSON_TYPE)), "/api/auth/logout") { }
        store.session = null
        return if (r is AccountResult.Failed && r.problem.code == "unauthorized") AccountResult.Ok(Unit) else r
    }

    /** `GET` [path] with this device's session (account sync): the body, or the plain-language problem. */
    suspend fun getWithSession(path: String): AccountResult<String> = authed(Request.Builder().get(), path) { it }

    /** `POST` [body] as JSON to [path] with this device's session (account sync). */
    suspend fun postWithSession(path: String, body: String): AccountResult<String> =
        authed(Request.Builder().post(body.toRequestBody(JSON_TYPE)), path) { it }

    /** A call with this device's session. A `401` means the session is gone: the device forgets it. */
    private suspend fun <T> authed(builder: Request.Builder, path: String, read: (String) -> T): AccountResult<T> {
        val base = store.serverUrl ?: return AccountResult.Failed(Problems.noServer)
        val s = store.session ?: return AccountResult.Failed(Problems.forCode("unauthorized"))
        val request = builder.url("$base$path").header("Authorization", "Bearer " + s.token.reveal()).build()
        return when (val r = call(request)) {
            is Call.Failed -> AccountResult.Failed(r.problem)
            is Call.Answer -> when {
                r.status in 200..299 -> runCatching { AccountResult.Ok(read(r.body)) }.getOrElse { AccountResult.Failed(Problems.notSokoul) }
                r.status == 401 -> {
                    store.session = null
                    AccountResult.Failed(Problems.forCode("unauthorized"))
                }
                else -> AccountResult.Failed(r.problem())
            }
        }
    }

    private sealed interface Call {
        data class Answer(val status: Int, val body: String, val code: String?, val retryAfter: Long?) : Call {
            fun problem(): AccountProblem = Problems.forCode(code ?: if (status >= 500) "server_error" else "unexpected", retryAfter)
        }
        data class Failed(val problem: AccountProblem) : Call
    }

    private suspend fun call(request: Request): Call = withContext(Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val code = if (resp.isSuccessful) null else runCatching { json.decodeFromString<ErrorAnswer>(body).error }.getOrNull()
                Call.Answer(resp.code, body, code, resp.header("Retry-After")?.trim()?.toLongOrNull())
            }
        } catch (_: IOException) {
            // The exception's text names the address only; it is not kept, so nothing of the request can leak through it.
            Call.Failed(Problems.unreachable)
        } catch (_: IllegalArgumentException) {
            Call.Failed(Problems.badAddress)
        }
    }

    private fun post(url: String, body: String) = Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE)).build()

    @Serializable private data class HealthBody(val status: String = "", val version: String = "")
    @Serializable private data class ErrorAnswer(val error: String)
    @Serializable private data class LoginBody(val name: String, val passphrase: String, val deviceName: String) {
        override fun toString(): String = "LoginBody(name=$name, passphrase=***, deviceName=$deviceName)"
    }
    @Serializable private data class SessionBody(val token: String, val sessionId: String, val user: AccountUser) {
        override fun toString(): String = "SessionBody(token=***, sessionId=$sessionId, user=$user)"
    }
    @Serializable private data class LinkStartBody(val deviceName: String)
    @Serializable private data class LinkStartAnswer(val deviceCode: String, val userCode: String, val verifyPath: String, val expiresIn: Int, val interval: Int) {
        override fun toString(): String = "LinkStartAnswer(deviceCode=***, userCode=$userCode)"
    }
    @Serializable private data class LinkPollBody(val deviceCode: String) {
        override fun toString(): String = "LinkPollBody(deviceCode=***)"
    }
    @Serializable private data class LinkTokenAnswer(val token: String, val sessionId: String, val user: AccountUser? = null) {
        override fun toString(): String = "LinkTokenAnswer(token=***, sessionId=$sessionId)"
    }
    @Serializable private data class SessionsAnswer(val sessions: List<DeviceSession>)

    override fun toString(): String = "SokoulAccount(server=${store.serverUrl}, signedIn=${store.session != null})"

    companion object {
        /** The new Sokoul server's port (docs/server.md). */
        const val DEFAULT_PORT = 25570
        const val SLOW_DOWN_STEP_SECONDS = 5L
        const val MAX_BACKOFF_SECONDS = 30L
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val EMPTY_JSON = "{}"
        private val json get() = accountJson

        /**
         * "192.168.1.20" → "http://192.168.1.20:25570"; a full address keeps its scheme, port and path (no trailing
         * slash). Null when it cannot be an address.
         */
        fun normalizeServer(address: String): String? {
            val raw = address.trim().trimEnd('/')
            if (raw.isEmpty() || raw.any { it.isWhitespace() }) return null
            val withScheme = if ("://" in raw) raw else "http://$raw"
            val url: HttpUrl = withScheme.toHttpUrlOrNull() ?: return null
            val explicitPort = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*:\\d+").containsMatchIn(withScheme)
            val port = if ("://" !in raw && !explicitPort) DEFAULT_PORT else url.port
            val built = url.newBuilder().port(port).build().toString().trimEnd('/')
            return built
        }
    }
}

/** The plain-language problems of the account flows, by the server's stable codes. */
object Problems {
    val noServer = AccountProblem("no_server", L("No Sokoul server address yet.", "Pas encore d'adresse de serveur Sokoul."))
    val badAddress = AccountProblem("bad_address", L("This is not an address. Example: 192.168.1.20", "Ce n'est pas une adresse. Exemple : 192.168.1.20"))
    val unreachable = AccountProblem(
        "unreachable",
        L(
            "The Sokoul server does not answer. Check that it is running and that this device is on the same network.",
            "Le serveur Sokoul ne répond pas. Vérifiez qu'il est lancé et que cet appareil est sur le même réseau.",
        ),
    )
    val notSokoul = AccountProblem("not_sokoul", L("This address does not answer like a Sokoul server.", "Cette adresse ne répond pas comme un serveur Sokoul."))

    fun forCode(code: String, retryAfterSeconds: Long? = null): AccountProblem = AccountProblem(
        code,
        when (code) {
            "invalid_credentials" -> L("Wrong name or passphrase.", "Nom ou phrase secrète incorrects.")
            "too_many_attempts" -> {
                val minutes = ((retryAfterSeconds ?: 60L) + 59) / 60
                L("Too many tries. Wait $minutes min, then try again.", "Trop d'essais. Attendez $minutes min, puis réessayez.")
            }
            "weak_passphrase" -> L("The passphrase needs at least 12 characters.", "La phrase secrète doit faire au moins 12 caractères.")
            "invalid_name" -> L("Choose a name of 1 to 40 characters.", "Choisissez un nom de 1 à 40 caractères.")
            "already_set_up" -> L("This server already has its owner. Sign in instead.", "Ce serveur a déjà son propriétaire. Connectez-vous plutôt.")
            "setup_local_only" -> L(
                "The first account can only be created on the PC that runs the server.",
                "Le premier compte ne peut être créé que sur le PC qui fait tourner le serveur.",
            )
            "access_denied" -> L(
                "The Sokoul server refuses this device. On the server's PC, allow devices of the home network.",
                "Le serveur Sokoul refuse cet appareil. Sur le PC du serveur, autorisez les appareils du réseau de la maison.",
            )
            "unauthorized" -> L("This device is no longer signed in. Link it again.", "Cet appareil n'est plus connecté. Reliez-le à nouveau.")
            "too_many_links" -> L("The server is busy. Try again in a minute.", "Le serveur est occupé. Réessayez dans une minute.")
            "not_found" -> L("This device is already signed out.", "Cet appareil est déjà déconnecté.")
            "server_error" -> L("The Sokoul server had a problem. Try again.", "Le serveur Sokoul a rencontré un problème. Réessayez.")
            else -> L("The Sokoul server refused. Try again.", "Le serveur Sokoul a refusé. Réessayez.")
        },
        retryAfterSeconds,
    )
}
