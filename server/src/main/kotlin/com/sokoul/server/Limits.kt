package com.sokoul.server

/**
 * A sliding-window rate limit: at most [max] calls per key within [windowMillis]. Memory only, like [FailureLimiter].
 * Used where one call costs the server real work or reaches an outside service (vault checks, starting a stream).
 */
class RateLimiter(
    private val nowMillis: () -> Long,
    private val max: Int,
    private val windowMillis: Long,
    private val maxKeys: Int = 10_000,
) {
    private val calls = HashMap<String, ArrayDeque<Long>>()

    /** Counts one call for [key]: null when allowed, or the seconds to wait when the key is over its limit. */
    @Synchronized
    fun acquire(key: String): Long? {
        val now = nowMillis()
        if (calls.size >= maxKeys) calls.keys.toList().forEach { prune(it, now) }
        val list = calls.getOrPut(key) { ArrayDeque() }
        while (list.isNotEmpty() && list.first() + windowMillis <= now) list.removeFirst()
        if (list.size >= max) return maxOf(1, (list.first() + windowMillis - now + 999) / 1000)
        list.addLast(now)
        return null
    }

    private fun prune(key: String, now: Long) {
        val list = calls[key] ?: return
        while (list.isNotEmpty() && list.first() + windowMillis <= now) list.removeFirst()
        if (list.isEmpty()) calls.remove(key)
    }

    /** Throws `429 too_many_requests` with `Retry-After` when [key] is over its limit. */
    fun check(key: String) {
        acquire(key)?.let { throw ApiError(429, "too_many_requests", it) }
    }
}

/** At most [perKey] tasks at once per key (a user's source resolutions); a further one is refused, not queued. */
class ConcurrencyLimiter(private val perKey: Int) {
    private val running = HashMap<String, Int>()

    @Synchronized
    fun tryAcquire(key: String): Boolean {
        val n = running[key] ?: 0
        if (n >= perKey) return false
        running[key] = n + 1
        return true
    }

    @Synchronized
    fun release(key: String) {
        val n = (running[key] ?: return) - 1
        if (n <= 0) running.remove(key) else running[key] = n
    }

    /** Runs [block] holding one slot of [key], or throws `429 busy` when the key has no slot left. */
    suspend fun <T> run(key: String, block: suspend () -> T): T {
        if (!tryAcquire(key)) throw ApiError(429, "busy", 5)
        try {
            return block()
        } finally {
            release(key)
        }
    }
}
