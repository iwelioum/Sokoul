package com.sokoul.pc.sync

import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.account.Problems
import com.sokoul.pc.account.SokoulAccount
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One synced item, as `GET/POST /api/sync` carry it (docs/server.md, "Sync"). [type] is `profile`, `resume`, `my_list`
 * or `title_alert`; [profile] the profile's account UUID; [key] the item's stable key (the resume key, "movie:603" for
 * a list entry or an alert, the profile's UUID for a profile). Only the fields of its type are set.
 */
@Serializable
data class SyncChange(
    val type: String,
    val profile: String,
    val key: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val seq: Long? = null,
    val name: String? = null,
    val avatar: Int? = null,
    val kids: Boolean? = null,
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val kind: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val image: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
    val finished: Boolean? = null,
    val subtitle: String? = null,
    val poster: String? = null,
    val backdrop: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val told: String? = null,
) {
    companion object {
        const val PROFILE = "profile"
        const val RESUME = "resume"
        const val MY_LIST = "my_list"
        const val TITLE_ALERT = "title_alert"
    }
}

@Serializable data class SyncPage(val changes: List<SyncChange> = emptyList(), val cursor: Long = 0, val more: Boolean = false)
@Serializable data class SyncPushAnswer(val applied: Int = 0, val ignored: Int = 0, val cursor: Long = 0)
@Serializable private data class SyncPushBody(val changes: List<SyncChange>)

/** How the engine reaches the server; tests use an in-memory one. */
interface SyncTransport {
    /** What changed in the account after [since]. A `cursor_ahead` problem means the server never gave that cursor. */
    suspend fun pull(since: Long): AccountResult<SyncPage>

    suspend fun push(changes: List<SyncChange>): AccountResult<SyncPushAnswer>
}

internal val syncJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

/** The real transport: this device's Sokoul session ([SokoulAccount]); a `401` signs the device out there. */
class HttpSyncTransport(private val account: SokoulAccount) : SyncTransport {
    override suspend fun pull(since: Long): AccountResult<SyncPage> =
        when (val r = account.getWithSession("/api/sync?since=$since")) {
            is AccountResult.Failed -> r
            is AccountResult.Ok -> decode { syncJson.decodeFromString<SyncPage>(r.value) }
        }

    override suspend fun push(changes: List<SyncChange>): AccountResult<SyncPushAnswer> =
        when (val r = account.postWithSession("/api/sync", syncJson.encodeToString(SyncPushBody(changes)))) {
            is AccountResult.Failed -> r
            is AccountResult.Ok -> decode { syncJson.decodeFromString<SyncPushAnswer>(r.value) }
        }

    private fun <T> decode(read: () -> T): AccountResult<T> =
        runCatching { AccountResult.Ok(read()) }.getOrElse { AccountResult.Failed(Problems.notSokoul) }
}
