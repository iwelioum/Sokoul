package com.sokoul.server

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
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

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
) {
    val accounts = Accounts(db, hasher, nowMillis)
    val sessions = SessionStore(db, nowMillis)
    val links = DeviceLinks(db, sessions, nowMillis)
    val limiter = FailureLimiter(nowMillis)
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

    routing {
        get(AccessGate.HEALTH_PATH) {
            call.respond(HealthBody(status = "ok", version = SERVER_VERSION))
        }
        get(LinkPage.PATH) { LinkPage.respond(call) }
        route("/api") {
            publicRoutes(services)
            withSession(sessions) { sessionRoutes(services) }
        }
    }
}

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

    post("/link/confirm") {
        val body = call.receive<LinkConfirmRequest>()
        val name = io { services.links.confirm(body.userCode, call.session.userId) } ?: throw ApiError(404, "invalid_code")
        call.respond(LinkConfirmBody(name))
    }
}

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
