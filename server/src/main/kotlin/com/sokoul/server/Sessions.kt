package com.sokoul.server

import io.ktor.server.application.ApplicationCall

/** The signed-in user and device behind a request. */
data class SessionPrincipal(val userId: String, val sessionId: String)

/**
 * Second lock of design D4: every `/api` route needs a Sokoul session.
 * Lot 4 implements the real lookup (hashed tokens in `sessions`); until then [RefuseAllSessions] keeps the gate shut.
 */
fun interface SessionAuthenticator {
    /** The session behind [call], or null to refuse with 401. */
    suspend fun authenticate(call: ApplicationCall): SessionPrincipal?
}

/** No accounts exist yet (Lot 4), so no request has a session. */
object RefuseAllSessions : SessionAuthenticator {
    override suspend fun authenticate(call: ApplicationCall): SessionPrincipal? = null
}
