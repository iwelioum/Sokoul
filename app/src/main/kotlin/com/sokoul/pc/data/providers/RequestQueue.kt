package com.sokoul.pc.data.providers

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.PriorityQueue
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
