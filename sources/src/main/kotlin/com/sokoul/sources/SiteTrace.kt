package com.sokoul.sources

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.InterruptedIOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * The first reason a site could not be read during one search, carried in the coroutine context so that the scrapers'
 * page readers keep their shape (a page or null) and the search still tells "no such work" from "could not ask".
 */
internal class SiteTrace : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SiteTrace>

    @Volatile var failure: String? = null
        private set

    fun note(reason: String) {
        if (failure == null) failure = reason
    }

    /** Notes a refused or failed answer to [asked], or one that ended on another site than the one asked. */
    fun answer(asked: HttpUrl, response: Response) {
        if (!response.isSuccessful) failureOf(response.code)?.let(::note)
        else if (response.request.url.host.removePrefix("www.") != asked.host.removePrefix("www.")) note(SourceResult.HOST_CHANGED)
    }

    /** Notes a request that did not get an answer. */
    fun error(e: Exception) = note(if (e is InterruptedIOException) SourceResult.TIMEOUT else SourceResult.NETWORK)
}

/** The failure an HTTP status means, or null when the site answered (a 404 is an answer: no such page). */
internal fun failureOf(code: Int): String? = when (code) {
    403, 429, 503 -> SourceResult.BLOCKED
    in 500..599 -> SourceResult.NETWORK
    else -> null
}

/** The trace of the search running here, or null outside one. */
internal suspend fun siteTrace(): SiteTrace? = currentCoroutineContext()[SiteTrace]

/**
 * Runs one search with a fresh trace: links found are [SourceResult.Found]; none, after a page could not be read, is
 * [SourceResult.Unavailable] with the first reason; none from a site that answered is [SourceResult.NotFound].
 */
internal suspend fun traced(search: suspend () -> List<SourceLink>?): SourceResult {
    val trace = SiteTrace()
    val links = withContext(trace) { search() }.orEmpty().distinctBy { it.url }
    val failure = trace.failure
    return when {
        links.isNotEmpty() -> SourceResult.Found(links)
        failure != null -> SourceResult.Unavailable(failure)
        else -> SourceResult.NotFound
    }
}
