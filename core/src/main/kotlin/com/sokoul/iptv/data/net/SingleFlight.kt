package com.sokoul.iptv.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.ConcurrentHashMap

/**
 * One piece of work per key at a time: callers asking for the same key while it runs share its answer. Nothing is held
 * while the work runs, and a caller that goes away (cancelled) never leaves the others waiting: they take the work over
 * (2026-11-24; a stripe lock held the whole call and blocked unrelated keys behind it).
 */
class SingleFlight<K : Any, V> {
    private val running = ConcurrentHashMap<K, CompletableDeferred<V>>()

    suspend fun run(key: K, block: suspend () -> V): V {
        while (true) {
            val mine = CompletableDeferred<V>()
            val other = running.putIfAbsent(key, mine)
            if (other != null) {
                try {
                    return other.await()
                } catch (c: CancellationException) {
                    // The one doing the work was cancelled, not this caller: do it here.
                    currentCoroutineContext().ensureActive()
                    continue
                }
            }
            try {
                val value = block()
                running.remove(key, mine)
                mine.complete(value)
                return value
            } catch (t: Throwable) {
                running.remove(key, mine)
                mine.completeExceptionally(t)
                throw t
            }
        }
    }

    /** How many keys are being worked on, for the tests. */
    val size: Int get() = running.size
}
