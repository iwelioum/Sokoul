package com.sokoul.server

/**
 * In-memory failure counter for sign-in: after [maxFailures] failures within [windowMillis] on one key (a remote
 * address, or an account name), that key is blocked until the oldest failure leaves the window. Memory only: a
 * restart forgets, which is acceptable for a private instance behind Cloudflare Access.
 */
class FailureLimiter(
    private val nowMillis: () -> Long,
    private val maxFailures: Int = 5,
    private val windowMillis: Long = 15 * 60_000L,
    private val maxKeys: Int = 10_000,
) {
    private val failures = HashMap<String, ArrayDeque<Long>>()

    /** Seconds until every key in [keys] may try again, or null when none is blocked. */
    @Synchronized
    fun retryAfterSeconds(vararg keys: String): Long? {
        val now = nowMillis()
        val waits = keys.mapNotNull { key ->
            val list = prune(key, now) ?: return@mapNotNull null
            if (list.size < maxFailures) null else list.first() + windowMillis - now
        }
        return waits.maxOrNull()?.let { maxOf(1, (it + 999) / 1000) }
    }

    @Synchronized
    fun recordFailure(vararg keys: String) {
        val now = nowMillis()
        if (failures.size >= maxKeys) failures.keys.toList().forEach { prune(it, now) }
        keys.forEach { failures.getOrPut(it) { ArrayDeque() }.addLast(now) }
    }

    @Synchronized
    fun reset(vararg keys: String) {
        keys.forEach { failures.remove(it) }
    }

    private fun prune(key: String, now: Long): ArrayDeque<Long>? {
        val list = failures[key] ?: return null
        while (list.isNotEmpty() && list.first() + windowMillis <= now) list.removeFirst()
        if (list.isEmpty()) failures.remove(key)
        return list.takeIf { it.isNotEmpty() }
    }
}
