package com.sokoul.pc.data.providers

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * How soon a call to a rate-limited service should go out. Set around the work with `withContext(RequestPriority.Urgent)`:
 * the title page and the player go before the rows of a page, which can wait their turn (2026-11-24).
 */
enum class RequestPriority : CoroutineContext.Element {
    Background, Normal, Urgent;

    override val key: CoroutineContext.Key<*> get() = Key

    companion object Key : CoroutineContext.Key<RequestPriority>
}

/** The priority the calling code asked for, Normal when it said nothing. */
suspend fun currentPriority(): RequestPriority = currentCoroutineContext()[RequestPriority] ?: RequestPriority.Normal

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

/**
 * Spreads the calls to a service out: one every [gapMs], the most urgent first, then in order of arrival. A turn is taken
 * only when the call really goes out, and a caller cancelled while waiting leaves no slot behind it. The previous queue
 * reserved a slot on arrival and never gave it back: each abandoned row pushed every later call 2 s further (2026-11-24).
 */
class Pacer(private val gapMs: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private class Ticket(val priority: Int, val order: Long)

    private val lock = Any()
    private val queue = PriorityQueue<Ticket>(compareByDescending<Ticket> { it.priority }.thenBy { it.order })
    private var order = 0L
    private var nextAt = 0L
    private val changes = MutableStateFlow(0L)

    /** Waits for this caller's turn; returns when its call may go out. */
    suspend fun turn(priority: RequestPriority = RequestPriority.Normal) {
        val me = synchronized(lock) { Ticket(priority.ordinal, order++).also { queue.add(it); changes.value = changes.value + 1 } }
        try {
            while (true) {
                val seen = changes.value
                val wait: Long? = synchronized(lock) {
                    if (queue.peek() !== me) null else {
                        val now = clock()
                        val w = nextAt - now
                        if (w <= 0) {
                            queue.poll()
                            nextAt = now + gapMs
                            changes.value = changes.value + 1
                            return
                        }
                        w
                    }
                }
                // Not first: wait until the head changes. First: until the gap is over, or until someone more urgent comes.
                if (wait == null) changes.first { it != seen } else withTimeoutOrNull(wait) { changes.first { it != seen } }
            }
        } finally {
            synchronized(lock) { if (queue.remove(me)) changes.value = changes.value + 1 }
        }
    }

    /** Callers waiting for their turn, for the tests. */
    val waiting: Int get() = synchronized(lock) { queue.size }
}
