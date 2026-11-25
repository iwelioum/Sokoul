package com.sokoul.server

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
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

@Serializable data class ErrorBody(val error: String)
@Serializable data class HealthBody(val status: String, val version: String)
@Serializable data class MeBody(val userId: String, val sessionId: String)

/** No valid Sokoul session: 401 `unauthorized`. */
class UnauthorizedException : RuntimeException(null, null, false, false)

private val log = LoggerFactory.getLogger("com.sokoul.server.Server")
private val SessionKey = AttributeKey<SessionPrincipal>("SokoulSession")

/** The session established by the session gate; only valid inside `/api` routes. */
val ApplicationCall.session: SessionPrincipal get() = attributes[SessionKey]

/**
 * The HTTP application. [access] is the network lock, [sessions] the session lock; tests pass their own.
 */
fun Application.module(
    config: ServerConfig,
    access: AccessGate = AccessGate(config),
    sessions: SessionAuthenticator = RefuseAllSessions,
) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; explicitNulls = false })
    }

    install(CallLogging) {
        level = Level.INFO
        logger = LoggerFactory.getLogger("com.sokoul.server.Calls")
        // Method, path and status only: no query string (may hold codes), no headers (hold tokens).
        format { call -> "${call.response.status()?.value ?: "-"} ${call.request.httpMethod.value} ${call.request.path()}" }
    }

    install(StatusPages) {
        exception<UnauthorizedException> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, ErrorBody("unauthorized"))
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
        route("/api") {
            install(createRouteScopedPlugin("SokoulSessionGate") {
                onCall { call ->
                    val principal = sessions.authenticate(call) ?: throw UnauthorizedException()
                    call.attributes.put(SessionKey, principal)
                }
            })
            get("/me") {
                val s = call.session
                call.respond(MeBody(userId = s.userId, sessionId = s.sessionId))
            }
        }
    }
}
