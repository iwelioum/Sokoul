package com.sokoul.server

import com.sokoul.credentials.ServiceCheck
import com.sokoul.iptv.data.net.SportsClient
import com.sokoul.iptv.data.net.SportScores
import com.sokoul.iptv.data.net.SportsFailure
import com.sokoul.iptv.data.net.SportsResult
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.application.ApplicationStopped
import io.ktor.util.AttributeKey
import com.sokoul.sources.SourceQuery
import com.sokoul.sources.hosts.HostExtractor
import com.sokoul.sources.hosts.Hosts
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable data class ErrorBody(val error: String)
@Serializable data class HealthBody(val status: String, val version: String)
@Serializable data class MeBody(val id: String, val name: String, val role: String)
@Serializable data class SessionBody(val token: String, val sessionId: String, val user: MeBody)
@Serializable data class SetupRequest(val name: String, val passphrase: String, val deviceName: String? = null)
@Serializable data class LoginRequest(val name: String, val passphrase: String, val deviceName: String? = null)
@Serializable data class JoinRequest(val code: String, val name: String, val passphrase: String, val deviceName: String? = null)
@Serializable data class InviteBody(val code: String, val expiresAt: Long)
@Serializable data class DeviceBody(val id: String, val deviceName: String, val createdAt: Long, val lastUsedAt: Long, val current: Boolean)
@Serializable data class SessionsBody(val sessions: List<DeviceBody>)
@Serializable data class LinkStartRequest(val deviceName: String? = null)
@Serializable data class LinkStartBody(
    val deviceCode: String,
    val userCode: String,
    val verifyPath: String,
    val expiresIn: Int,
    val interval: Int,
)
@Serializable data class LinkConfirmRequest(val userCode: String)
@Serializable data class LinkConfirmBody(val deviceName: String)
@Serializable data class LinkPollRequest(val deviceCode: String)
@Serializable data class LinkTokenBody(val token: String, val sessionId: String, val user: MeBody)
@Serializable data class SetupStatusBody(val needsOwner: Boolean)

/** No valid Sokoul session: 401 `unauthorized`. */
class UnauthorizedException : RuntimeException(null, null, false, false)

/** An expected refusal: answered as [status] with `{"error":"<code>"}`, plus `Retry-After` when set. */
class ApiError(val status: Int, val code: String, val retryAfterSeconds: Long? = null) :
    RuntimeException(code, null, false, false)

/** Everything the routes need, built on one database and one clock (tests pass a fake clock and cheaper hashing). */
class Services(
    val db: Database,
    val nowMillis: () -> Long = System::currentTimeMillis,
    hasher: PassphraseHasher = PassphraseHasher(),
    syncLimits: SyncLimits = SyncLimits(),
    /** The vault's master key file (`ServerConfig.vaultKeyFile`); read, or created, the first time the vault is used. */
    vaultKeyFile: File,
    credentialChecker: CredentialChecker = CredentialChecker(ServiceCheck(defaultCheckHttp())::check),
    /** Outside services of the web client (Lot 7): TMDB, the sites, the hosts, ffmpeg. Tests pass fakes. */
    upstreamHttp: OkHttpClient = defaultUpstreamHttp(),
    tmdbBaseUrl: String = "https://api.themoviedb.org",
    sourceFactory: SourceFactory = DefaultSources(upstreamHttp),
    hostFor: (String) -> HostExtractor? = Hosts::forUrl,
    remuxer: Remuxer = Remuxer(null),
    /** Tests only: lets the relay reach a MockWebServer on loopback. */
    relayAllowPrivate: Boolean = false,
    sportsBase: String = "http://127.0.0.1:25565",
    sportsKey: String? = null,
) {
    val accounts = Accounts(db, hasher, nowMillis)
    val sessions = SessionStore(db, nowMillis)
    val links = DeviceLinks(db, sessions, nowMillis)
    val limiter = FailureLimiter(nowMillis)
    val sync = SyncStore(db, nowMillis, syncLimits)
    val vault: Vault by lazy { Vault.open(db, vaultKeyFile, credentialChecker, nowMillis) }

    /** Each PUT of a credential asks the real service: 20 per 15 minutes per account. */
    val credentialsLimiter = RateLimiter(nowMillis, max = 20, windowMillis = 15 * 60_000L)
    /** Listing versions asks every site: 30 per 10 minutes per account. */
    val versionsLimiter = RateLimiter(nowMillis, max = 30, windowMillis = 10 * 60_000L)
    /** Starting a stream asks a host and may start ffmpeg: 20 per 10 minutes per account. */
    val startLimiter = RateLimiter(nowMillis, max = 20, windowMillis = 10 * 60_000L)

    val tmdb = TmdbGateway(upstreamHttp, tmdbBaseUrl, nowMillis)
    val catalog = Catalog(tmdb, { userId -> vault.reveal(userId, "tmdb") }, db)
    val sagas = WebSagas(catalog, tmdb)
    val animation = WebAnimation(upstreamHttp, catalog, tmdb)
    val editorial = Editorial(catalog, tmdb, sagas, animation, db)
    val relay = StreamRelay(
        upstreamHttp, remuxer,
        sessionLive = sessions::isLive,
        sessionOfToken = { token -> sessions.lookup(token)?.sessionId },
        nowMillis = nowMillis,
        allowPrivate = relayAllowPrivate,
    )
    private val debrid = WebDebrid(upstreamHttp, { userId -> vault.reveal(userId, "realdebrid") })
    val playback = Playback(catalog, tmdb, sourceFactory, hostFor, relay, nowMillis, debrid = debrid)
    val subtitles = WebSubtitles(upstreamHttp, { userId, service -> vault.reveal(userId, service) }, nowMillis)
    val live = LiveTv(upstreamHttp, { userId -> vault.reveal(userId, "iptv") }, relay, nowMillis, relayAllowPrivate)
    val sports = SportsClient(upstreamHttp, { sportsBase }, { sportsKey })
    val sportScores = SportScores(upstreamHttp.newBuilder().callTimeout(8, TimeUnit.SECONDS).build())
}

private val log = LoggerFactory.getLogger("com.sokoul.server.Server")
private val SessionKey = AttributeKey<SessionPrincipal>("SokoulSession")
private val PreconditionRequired = HttpStatusCode(428, "Precondition Required")

/** The session established by the session gate; only valid inside [withSession] routes. */
val ApplicationCall.session: SessionPrincipal get() = attributes[SessionKey]

/**
 * The HTTP application. [access] is the network lock, [sessions] the session lock; tests may pass their own.
 */
fun Application.module(
    config: ServerConfig,
    services: Services,
    access: AccessGate = AccessGate(config),
    sessions: SessionAuthenticator = services.sessions,
    /** The web client's folder; Main fills it from `web.dir` or [WebFiles.locate]. Null: no web client. */
    webDir: File? = config.webDir,
) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true })
    }

    install(CallLogging) {
        level = Level.INFO
        logger = LoggerFactory.getLogger("com.sokoul.server.Calls")
        // Method, path and status only: no query string (may hold codes), no headers (hold tokens), no bodies.
        format { call -> "${call.response.status()?.value ?: "-"} ${call.request.httpMethod.value} ${call.request.path()}" }
    }

    install(StatusPages) {
        exception<NeedsKeyException> { call, e ->
            call.respond(HttpStatusCode.Conflict, NeedsKeyBody("needs_key", e.service, e.reason))
        }
        exception<UnauthorizedException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorBody("unauthorized"))
        }
        exception<ApiError> { call, e ->
            e.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(HttpStatusCode.fromValue(e.status), ErrorBody(e.code))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("bad_request"))
        }
        exception<NotFoundException> { call, _ ->
            call.respond(HttpStatusCode.NotFound, ErrorBody("not_found"))
        }
        exception<Throwable> { call, cause ->
            // The class only: messages may quote input.
            log.error("Unhandled {} on {} {}", cause::class.qualifiedName, call.request.httpMethod.value, call.request.path())
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("internal_error"))
        }
        status(HttpStatusCode.NotFound) { call, status -> call.respond(status, ErrorBody("not_found")) }
        status(HttpStatusCode.MethodNotAllowed) { call, status -> call.respond(status, ErrorBody("method_not_allowed")) }
    }

    // The network lock runs before routing for every path (unknown ones included) and ends the call when it refuses.
    intercept(ApplicationCallPipeline.Plugins) {
        val decision = access.check(call.request.path(), call.request.local.remoteAddress) { name ->
            call.request.headers[name]
        }
        if (decision is AccessGate.Decision.Deny) {
            AccessGate.logDenied(decision.reason, call.request.httpMethod.value, call.request.path())
            call.respond(HttpStatusCode.Forbidden, ErrorBody("access_denied"))
            finish()
        }
    }

    // Expired streams and idle remuxes go every minute; every ffmpeg stops with the server.
    val sweeper = launch {
        while (isActive) {
            delay(60_000)
            runCatching { services.relay.sweep() }
        }
    }
    monitor.subscribe(ApplicationStopped) {
        sweeper.cancel()
        services.relay.stopAll()
    }

    val web = webDir?.let(::WebFiles)

    routing {
        get(AccessGate.HEALTH_PATH) {
            call.respond(HealthBody(status = "ok", version = SERVER_VERSION))
        }
        get(LinkPage.PATH) { LinkPage.respond(call) }
        route("/api") {
            publicRoutes(services)
            withSession(sessions) {
                sessionRoutes(services)
                catalogRoutes(services)
                playRoutes(services)
                liveRoutes(services)
            }
            // The player cannot send a token: the stream id is the capability, bound to its session (StreamRelay).
            get("/stream/{id}/{rest...}") {
                services.relay.serve(call, call.parameters["id"].orEmpty(), call.parameters.getAll("rest").orEmpty())
            }
        }
        if (web != null) {
            get("/") { web.respond(call, emptyList()) }
            get("/{path...}") {
                val segments = call.parameters.getAll("path").orEmpty()
                // An unknown API route stays a JSON 404, never the app shell.
                if (segments.firstOrNull() == "api") throw NotFoundException()
                web.respond(call, segments)
            }
        }
    }
}

/** The web client's catalogue (openspec web-client 1.2), with the caller's own TMDB key. */
private fun Route.catalogRoutes(services: Services) {
    val catalog = services.catalog
    fun ApplicationCall.language() = TmdbGateway.languageOf(request.headers[HttpHeaders.AcceptLanguage])

    get("/catalog/home") {
        call.respond(catalog.home(call.session.userId, call.language(), call.profileId()))
    }

    get("/catalog/title/{kind}/{id}") {
        val kind = kindParam(call.parameters["kind"])
        call.respond(catalog.title(call.session.userId, call.language(), kind, idParam(call.parameters["id"])))
    }

    get("/catalog/season/{id}/{season}") {
        val season = call.parameters["season"]?.toIntOrNull()?.takeIf { it in 0..999 } ?: throw ApiError(400, "invalid_season")
        call.respond(catalog.season(call.session.userId, call.language(), idParam(call.parameters["id"]), season))
    }

    get("/catalog/search") {
        val q = call.request.queryParameters["q"].orEmpty()
        if (q.length > 100) throw ApiError(400, "query_too_long")
        val filter = call.request.queryParameters["type"]?.also {
            if (it !in setOf("movie", "tv", "person")) throw ApiError(400, "invalid_kind")
        }
        call.respond(catalog.search(call.session.userId, call.language(), q, call.page(), filter))
    }
    get("/catalog/person/{id}") {
        call.respond(catalog.person(call.session.userId, call.language(), idParam(call.parameters["id"])))
    }
    get("/catalog/followed/news") {
        val profile = call.profileId() ?: throw ApiError(400, "invalid_profile")
        call.respond(catalog.followedNews(call.session.userId, call.language(), profile))
    }

    get("/catalog/browse/{section}") {
        call.respond(catalog.browse(call.session.userId, call.language(), call.parameters["section"].orEmpty(), call.page()))
    }
    get("/catalog/collections") {
        call.respond(services.sagas.list(call.language(), call.page()))
    }
    get("/catalog/collections/{id}") {
        call.respond(services.sagas.detail(call.session.userId, call.language(), call.parameters["id"].orEmpty()))
    }
    get("/catalog/sections/{section}") {
        call.respond(services.editorial.plan(call.parameters["section"].orEmpty(), call.language()))
    }
    get("/catalog/sections/{section}/rows/{row}") {
        call.respond(services.editorial.row(call.session.userId, call.language(), call.parameters["section"].orEmpty(),
            call.parameters["row"].orEmpty(), call.page(), call.profileId()))
    }
    get("/catalog/shelf/{family}/{id}") {
        call.respond(services.editorial.filter(call.session.userId, call.language(), call.parameters["family"].orEmpty(),
            call.parameters["id"].orEmpty(), call.page()))
    }
    get("/catalog/upcoming") {
        call.respond(services.editorial.upcoming(call.session.userId, call.language(), call.page()))
    }
    get("/catalog/animation/calendar") {
        call.respond(services.animation.schedule(java.time.LocalDate.now()))
    }
    get("/catalog/animation/search") {
        val query = call.request.queryParameters["q"]?.trim()
        if ((query?.length ?: 0) > 100) throw ApiError(400, "query_too_long")
        val kind = if (call.request.queryParameters["kind"] == "manga") com.sokoul.pc.data.anime.MediaKind.MANGA
            else com.sokoul.pc.data.anime.MediaKind.ANIME
        call.respond(services.animation.shelf(com.sokoul.pc.data.anime.AniQuery(kind, search = query,
            page = call.page(), sort = if (query.isNullOrBlank()) "POPULARITY_DESC" else "SEARCH_MATCH")))
    }
    get("/catalog/animation/{id}") {
        call.respond(services.animation.work(call.session.userId, call.language(), idParam(call.parameters["id"])))
    }
    get("/catalog/animation/{id}/chapters") {
        call.respond(services.animation.chapters(idParam(call.parameters["id"]), call.language()))
    }
    get("/catalog/animation/{id}/chapters/{chapter}/pages") {
        val chapter = call.parameters["chapter"].orEmpty()
        val allowed = services.animation.chapters(idParam(call.parameters["id"]), call.language()).any { it.id == chapter }
        if (!allowed) throw ApiError(404, "not_found")
        call.respond(services.animation.pages(chapter))
    }
    get("/service-guides") { call.respond(webServiceGuides(call.language())) }
}

private fun ApplicationCall.profileId(): String? = request.queryParameters["profile"]?.takeIf(String::isNotEmpty)?.also {
    if (!PROFILE_ID.matches(it)) throw ApiError(400, "invalid_profile")
}

private fun ApplicationCall.page(): Int = request.queryParameters["page"]?.let {
    it.toIntOrNull()?.takeIf { number -> number in 1..500 } ?: throw ApiError(400, "invalid_page")
} ?: 1

@Serializable private data class SportsStartRequest(val serverKey: String)
@Serializable private data class GuideRequest(val ids: List<String>)
private val CHANNEL_ID = Regex("[A-Za-z0-9_-]{1,64}")

private fun <T : Any> SportsResult<T>.available(): T = value ?: throw when (failure) {
    SportsFailure.VipRequired -> ApiError(403, "sports_access_required")
    SportsFailure.InvalidResponse -> ApiError(404, "not_found")
    else -> ApiError(502, "sports_unavailable")
}

private fun Route.liveRoutes(services: Services) {
    get("/live/channels") {
        val params = call.request.queryParameters
        val text = params["q"]?.trim()
        val group = params["group"]
        if ((text?.length ?: 0) > 100 || (group?.length ?: 0) > 160) throw ApiError(400, "query_too_long")
        val ids = params["ids"]?.split(',')?.filter(String::isNotEmpty)
        if (ids != null && (ids.size > 100 || ids.any { !CHANNEL_ID.matches(it) })) throw ApiError(400, "invalid_channel")
        call.respond(services.live.channels(call.session.userId, call.page(), params["sports"] == "true", group, text, ids?.toSet()))
    }
    post("/live/guide") {
        val body = call.receive<GuideRequest>()
        if (body.ids.size !in 1..8 || body.ids.any { !CHANNEL_ID.matches(it) }) throw ApiError(400, "invalid_channel")
        call.respond(services.live.guides(call.session.userId, body.ids))
    }
    get("/live/guide/{id}") {
        call.respond(services.live.guide(call.session.userId, call.parameters["id"].orEmpty()))
    }
    get("/live/replay/{id}") {
        call.respond(services.live.archives(call.session.userId, call.parameters["id"].orEmpty()))
    }
    post("/live/replay/start") {
        services.startLimiter.check("user:" + call.session.userId)
        call.respond(services.live.startReplay(call.session, call.receive<ReplayRequest>()))
    }
    get("/live/vod/{kind}") {
        val params = call.request.queryParameters
        val text = params["q"]?.trim()
        val group = params["group"]
        if ((text?.length ?: 0) > 100 || (group?.length ?: 0) > 160) throw ApiError(400, "query_too_long")
        call.respond(services.live.vod.browse(call.session.userId, call.parameters["kind"].orEmpty(), call.page(), group, text))
    }
    get("/live/vod/{kind}/{id}") {
        val id = call.parameters["id"]?.toLongOrNull()?.takeIf { it > 0 } ?: throw ApiError(400, "invalid_id")
        call.respond(services.live.vod.details(call.session.userId, call.parameters["kind"].orEmpty(), id))
    }
    post("/live/vod/{kind}/start") {
        services.startLimiter.check("user:" + call.session.userId)
        call.respond(services.live.vod.start(call.session, call.parameters["kind"].orEmpty(), call.receive<ProviderPlayRequest>()))
    }
    post("/live/start") {
        services.startLimiter.check("user:" + call.session.userId)
        val body = call.receive<ChannelRequest>()
        call.respond(services.live.start(call.session, body.channelId))
    }
    delete("/live/stream/{id}") {
        val session = call.session
        val id = call.parameters["id"].orEmpty()
        if (!io { services.relay.stopLive(session.sessionId, id) }) throw ApiError(404, "not_found")
        call.respond(HttpStatusCode.NoContent)
    }
    get("/sports/events") {
        call.respond(services.sports.events().available())
    }
    get("/sports/scores") {
        call.respond(services.sportScores.matches().sortedBy { it.start }.map { it.webScore() })
    }
    get("/sports/events/{id}/servers") {
        call.respond(services.sports.servers(call.parameters["id"].orEmpty()).available())
    }
    post("/sports/events/{id}/start") {
        services.startLimiter.check("user:" + call.session.userId)
        val body = call.receive<SportsStartRequest>()
        val url = services.sports.resolve(call.parameters["id"].orEmpty(), body.serverKey).available()
        call.respond(services.relay.register(call.session.sessionId, ResolvedMedia(
            url, hls = true, headers = emptyMap(), live = true, sportsProxy = true,
        )))
    }
}

/** Versions, start (sokoul-server 8.1). */
private fun Route.playRoutes(services: Services) {
    get("/play/versions") {
        val query = call.workQuery()
        services.versionsLimiter.check("user:" + call.session.userId)
        call.respond(services.playback.versions(call.session, query))
    }
    get("/play/subtitles") {
        services.versionsLimiter.check("subtitles:" + call.session.userId)
        val languages = call.request.queryParameters["languages"]?.split(',')?.distinct()
            ?: listOf("fr", "en")
        if (languages.size !in 1..4 || languages.any { it.length != 2 || it.any { c -> c !in 'a'..'z' } }) {
            throw ApiError(400, "invalid_language")
        }
        call.respond(services.subtitles.search(call.session, call.workQuery(), languages))
    }
    get("/play/subtitles/{id}") {
        services.startLimiter.check("subtitles:" + call.session.userId)
        call.respond(services.subtitles.cues(call.session, call.parameters["id"].orEmpty()))
    }

    post("/play/start") {
        val body = call.receive<StartRequest>()
        if (body.versionId.length > 64) throw ApiError(404, "unknown_version")
        services.startLimiter.check("user:" + call.session.userId)
        call.respond(services.playback.start(call.session, body.versionId))
    }
}

private fun ApplicationCall.workQuery(): SourceQuery {
    val params = request.queryParameters
    val kind = kindParam(params["kind"])
    val id = idParam(params["id"])
    val season = params["season"]?.let { it.toIntOrNull()?.takeIf { n -> n in 0..999 } ?: throw ApiError(400, "invalid_season") }
    val episode = params["episode"]?.let { it.toIntOrNull()?.takeIf { n -> n in 1..9999 } ?: throw ApiError(400, "invalid_episode") }
    if (kind == "tv" && (season == null || episode == null)) throw ApiError(400, "episode_required")
    return SourceQuery(kind, id, season.takeIf { kind == "tv" }, episode.takeIf { kind == "tv" })
}

private val PROFILE_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

private fun kindParam(raw: String?): String = raw?.takeIf { it in Catalog.KINDS } ?: throw ApiError(400, "invalid_kind")

private fun idParam(raw: String?): Int = raw?.toIntOrNull()?.takeIf { it > 0 } ?: throw ApiError(400, "invalid_id")

/** Routes reachable without a session (still behind the network lock): setup, sign-in, joining, the TV side of linking. */
private fun Route.publicRoutes(services: Services) {
    val accounts = services.accounts
    val limiter = services.limiter

    // Whether the server still waits for its owner: behind the network lock like every /api route, so it never leaks
    // through /health.
    get("/setup") {
        call.respond(SetupStatusBody(needsOwner = !io { services.accounts.hasUsers() }))
    }

    post("/setup") {
        val local = AccessGate.isDirectLoopback(call.request.local.remoteAddress) { call.request.headers[it] }
        if (!local) throw ApiError(403, "setup_local_only")
        val body = call.receive<SetupRequest>()
        val (user, issued) = io {
            val owner = accounts.createOwner(body.name, body.passphrase)
            owner to services.sessions.create(owner.id, SessionStore.cleanDeviceName(body.deviceName))
        }
        call.respond(HttpStatusCode.Created, sessionBody(user, issued))
    }

    post("/auth/login") {
        val body = call.receive<LoginRequest>()
        val addressKey = "address:" + call.clientAddress()
        val accountKey = "account:" + body.name.trim().lowercase()
        limiter.retryAfterSeconds(addressKey, accountKey)?.let { throw ApiError(429, "too_many_attempts", it) }
        val user = io { accounts.verify(body.name, body.passphrase) }
        if (user == null) {
            limiter.recordFailure(addressKey, accountKey)
            throw ApiError(401, "invalid_credentials")
        }
        limiter.reset(addressKey, accountKey)
        val issued = io { services.sessions.create(user.id, SessionStore.cleanDeviceName(body.deviceName)) }
        call.respond(sessionBody(user, issued))
    }

    post("/auth/join") {
        val body = call.receive<JoinRequest>()
        val addressKey = "address:" + call.clientAddress()
        limiter.retryAfterSeconds(addressKey)?.let { throw ApiError(429, "too_many_attempts", it) }
        val (user, issued) = io {
            val member = try {
                accounts.join(body.code, body.name, body.passphrase)
            } catch (e: ApiError) {
                if (e.code == "invalid_invite") limiter.recordFailure(addressKey)
                throw e
            }
            member to services.sessions.create(member.id, SessionStore.cleanDeviceName(body.deviceName))
        }
        call.respond(HttpStatusCode.Created, sessionBody(user, issued))
    }

    post("/link/start") {
        val body = call.receive<LinkStartRequest>()
        val started = io { services.links.start(SessionStore.cleanDeviceName(body.deviceName)) }
        call.respond(
            LinkStartBody(
                deviceCode = started.deviceCode,
                userCode = started.userCode,
                verifyPath = "${LinkPage.PATH}?code=${started.userCode}",
                expiresIn = DeviceLinks.TTL_SECONDS,
                interval = DeviceLinks.INTERVAL_SECONDS,
            ),
        )
    }

    post("/link/poll") {
        val body = call.receive<LinkPollRequest>()
        when (val result = io { services.links.poll(body.deviceCode) }) {
            DeviceLinks.Poll.Pending -> call.respond(PreconditionRequired, ErrorBody("pending"))
            DeviceLinks.Poll.Expired -> call.respond(HttpStatusCode.Gone, ErrorBody("expired"))
            DeviceLinks.Poll.SlowDown -> throw ApiError(429, "slow_down", DeviceLinks.INTERVAL_SECONDS.toLong())
            is DeviceLinks.Poll.Linked -> {
                // The linked device learns who it now belongs to in the same answer, as a login does.
                val who = io { services.sessions.lookup(result.session.token) } ?: throw ApiError(410, "expired")
                call.respond(LinkTokenBody(result.session.token, result.session.sessionId, MeBody(who.userId, who.name, who.role.wire)))
            }
        }
    }
}

/** Routes for a signed-in device. */
private fun Route.sessionRoutes(services: Services) {
    get("/me") {
        val s = call.session
        call.respond(MeBody(id = s.userId, name = s.name, role = s.role.wire))
    }

    get("/sessions") {
        val s = call.session
        val list = io { services.sessions.list(s.userId) }
        call.respond(SessionsBody(list.map { DeviceBody(it.id, it.deviceName, it.createdAt, it.lastUsedAt, it.id == s.sessionId) }))
    }

    delete("/sessions/{id}") {
        val id = call.parameters["id"].orEmpty()
        if (!io { services.sessions.revoke(call.session.userId, id) }) throw ApiError(404, "not_found")
        call.respond(HttpStatusCode.NoContent)
    }

    post("/auth/logout") {
        val s = call.session
        io { services.sessions.revoke(s.userId, s.sessionId) }
        call.respond(HttpStatusCode.NoContent)
    }

    post("/invites") {
        val s = call.session
        if (s.role != Role.OWNER) throw ApiError(403, "owner_only")
        val invite = io { services.accounts.createInvite(User(s.userId, s.name, s.role)) }
        call.respond(HttpStatusCode.Created, InviteBody(invite.code, invite.expiresAt))
    }

    // Profiles and progress of the caller's account (Lot 6): what changed after a cursor, and this device's changes.
    get("/sync") {
        val raw = call.request.queryParameters["since"]
        val since = if (raw == null) 0L else raw.toLongOrNull() ?: throw ApiError(400, "invalid_cursor")
        call.respond(io { services.sync.pull(call.session.userId, since) })
    }

    post("/sync") {
        call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let { if (it > MAX_SYNC_BODY_BYTES) throw ApiError(413, "too_large") }
        val body = call.receive<SyncPushRequest>()
        call.respond(io { services.sync.push(call.session.userId, body.changes) })
    }

    // The caller's own credentials vault (Lot 5): masks and verdicts only; no route ever returns a stored value.
    get("/credentials") {
        call.respond(io { services.vault.list(call.session.userId) })
    }

    put("/credentials/{service}") {
        val service = call.parameters["service"].orEmpty()
        if (service !in Vault.SERVICES) throw ApiError(404, "unknown_service")
        services.credentialsLimiter.check("user:" + call.session.userId)
        val length = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: throw ApiError(411, "length_required")
        if (length > Vault.MAX_BODY_BYTES) throw ApiError(413, "too_large")
        val body = call.receive<CredentialPutRequest>()
        when (val r = withContext(Dispatchers.IO) { services.vault.put(call.session.userId, service, body.value, body.onlyIfAbsent) }) {
            is Vault.PutResult.Stored -> call.respond(r.info)
            is Vault.PutResult.Rejected -> {
                val status = when (r.code) {
                    "bad_format" -> 400
                    "already_configured" -> 409
                    "refused" -> 422
                    else -> 502
                }
                call.respond(HttpStatusCode.fromValue(status), CredentialRefusedBody(r.code, r.reason))
            }
        }
    }

    delete("/credentials/{service}") {
        val service = call.parameters["service"].orEmpty()
        if (!io { services.vault.delete(call.session.userId, service) }) throw ApiError(404, "not_found")
        call.respond(HttpStatusCode.NoContent)
    }

    post("/link/confirm") {
        val body = call.receive<LinkConfirmRequest>()
        val name = io { services.links.confirm(body.userCode, call.session.userId) } ?: throw ApiError(404, "invalid_code")
        call.respond(LinkConfirmBody(name))
    }
}

/** The vault's checks: short timeouts, as a person waits for the answer. */
fun defaultCheckHttp(): OkHttpClient = OkHttpClient.Builder()
    .onlyPublicInternet()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build()

/** TMDB, the streaming sites, the hosts and the relay: bounded, as a person waits for each. */
fun defaultUpstreamHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

/** 1 000 changes with long overviews stay far below this. */
private const val MAX_SYNC_BODY_BYTES = 8L * 1024 * 1024

private fun sessionBody(user: User, issued: IssuedSession) =
    SessionBody(issued.token, issued.sessionId, MeBody(user.id, user.name, user.role.wire))

/** Database and hashing work leaves the request threads. */
private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

/**
 * The caller's address for rate limiting. Behind the tunnel every socket comes from `cloudflared` on loopback, so
 * Cloudflare's `Cf-Connecting-IP` is used; it can be trusted because the network lock refuses Cloudflare headers
 * without a valid Access assertion (and refuses them outright in local mode).
 */
private fun ApplicationCall.clientAddress(): String =
    request.headers["Cf-Connecting-IP"]?.trim()?.takeIf { it.isNotEmpty() } ?: request.local.remoteAddress

private object SessionSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation =
        RouteSelectorEvaluation.Transparent

    override fun toString(): String = "(sokoul session)"
}

/** Children of this route need a session: 401 `unauthorized` otherwise. */
private fun Route.withSession(sessions: SessionAuthenticator, build: Route.() -> Unit) {
    val child = createChild(SessionSelector)
    child.install(createRouteScopedPlugin("SokoulSessionGate") {
        onCall { call ->
            val principal = sessions.authenticate(call) ?: throw UnauthorizedException()
            call.attributes.put(SessionKey, principal)
        }
    })
    child.build()
}
