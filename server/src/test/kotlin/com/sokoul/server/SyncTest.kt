package com.sokoul.server

import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Lot 6: last writer wins per item, tombstones, a per-account cursor, accounts kept apart, and bounded requests. */
class SyncTest {
    @get:Rule val tmp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun profile(id: String, at: Long, name: String = "Lou", kids: Boolean = false, pinHash: String? = null) =
        SyncChange("profile", id, id, at, name = name, avatar = 2, kids = kids, pinHash = pinHash, pinSalt = if (pinHash != null) "p1" else null)

    private fun resume(profile: String, key: String, at: Long, position: Long = 60_000, deleted: Boolean = false) =
        SyncChange(
            "resume", profile, key, at, deleted = deleted, kind = "tv", tmdbId = 1399, season = 1, episode = 2,
            positionMs = position, durationMs = 3_600_000, finished = false, title = "Game of Thrones", image = "/a.jpg",
        )

    private fun push(changes: List<SyncChange>) = json.encodeToString(SyncPushRequest(changes))

    private fun store(limits: SyncLimits = SyncLimits(), now: () -> Long = { 1_800_000_000_000L }): Pair<SyncStore, Database> {
        val db = Database.open(tmp.newFolder())
        listOf("u1", "u2").forEach { db.update("INSERT INTO users (id, created_at) VALUES (?, ?)", it, 1L) }
        return SyncStore(db, now, limits) to db
    }

    @Test fun `migration 3 adds the sync tables after 1 and 2`() {
        val (_, db) = store()
        assertEquals(3, db.schemaVersion())
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getString(1) }
        assertTrue(tables.containsAll(listOf("sync_cursor", "sync_profiles", "sync_resume", "sync_my_list", "sync_title_alerts", "users", "sessions")))
    }

    @Test fun `the latest change of an item wins and a tie keeps the server's copy`() {
        val (s, _) = store()
        assertEquals(SyncPushBody(1, 0, 1), s.push("u1", listOf(resume("P", "tv:1399:1:2", at = 1_000, position = 10))))
        // Older: ignored. Same time, other value: the server keeps its own.
        assertEquals(SyncPushBody(0, 2, 1), s.push("u1", listOf(resume("P", "tv:1399:1:2", at = 999, position = 20), resume("P", "tv:1399:1:2", at = 1_000, position = 30))))
        assertEquals(10L, s.pull("u1", 0).changes.single().positionMs)
        // Newer: applied, with the next sequence number.
        assertEquals(SyncPushBody(1, 0, 2), s.push("u1", listOf(resume("P", "tv:1399:1:2", at = 1_001, position = 40))))
        val page = s.pull("u1", 0)
        assertEquals(40L, page.changes.single().positionMs)
        assertEquals(2L, page.changes.single().seq)
        assertEquals(2L, page.cursor)
    }

    @Test fun `a deletion is a tombstone that beats older copies and loses to newer ones`() {
        val (s, _) = store()
        s.push("u1", listOf(resume("P", "movie:603", at = 1_000)))
        s.push("u1", listOf(resume("P", "movie:603", at = 2_000, deleted = true)))
        val tomb = s.pull("u1", 1).changes.single()
        assertTrue(tomb.deleted)
        assertNull(tomb.title)
        assertNull(tomb.positionMs)
        // A device that was offline sends its older copy: the tombstone stays.
        assertEquals(1, s.push("u1", listOf(resume("P", "movie:603", at = 1_500))).ignored)
        // Watched again later: the item comes back.
        assertEquals(1, s.push("u1", listOf(resume("P", "movie:603", at = 3_000))).applied)
        assertFalse(s.pull("u1", 2).changes.single().deleted)
    }

    @Test fun `the cursor pages through changes in order, across item types`() {
        val (s, _) = store(SyncLimits(pageSize = 3))
        s.push("u1", listOf(profile("P", 1)) + (1..6).map { resume("P", "movie:$it", at = 10L + it) })
        s.push("u1", listOf(SyncChange("my_list", "P", "movie:9", 5, kind = "movie", tmdbId = 9, title = "Nine")))
        var since = 0L
        val seen = mutableListOf<Long>()
        var pages = 0
        do {
            val page = s.pull("u1", since)
            seen += page.changes.map { it.seq!! }
            since = page.cursor
            pages++
        } while (page.more)
        assertEquals((1L..8L).toList(), seen)
        assertEquals(3, pages)
        // Nothing new: an empty page that keeps the cursor.
        assertEquals(SyncPullBody(emptyList(), 8, false), s.pull("u1", 8))
        // A re-sent change that loses moves nothing.
        s.push("u1", listOf(resume("P", "movie:1", at = 1)))
        assertEquals(8L, s.pull("u1", 8).cursor)
    }

    @Test fun `a cursor the server never gave asks the device to start again`() {
        val (s, _) = store()
        s.push("u1", listOf(profile("P", 1)))
        try {
            s.pull("u1", 5); fail()
        } catch (e: ApiError) {
            assertEquals(409, e.status); assertEquals("cursor_ahead", e.code)
        }
        try {
            s.pull("u1", -1); fail()
        } catch (e: ApiError) {
            assertEquals("invalid_cursor", e.code)
        }
    }

    @Test fun `profiles keep their kids flag and only the code's digest`() {
        val (s, db) = store()
        s.push("u1", listOf(profile("P", 1, kids = true, pinHash = "ab".repeat(32))))
        val p = s.pull("u1", 0).changes.single()
        assertEquals(true, p.kids)
        assertEquals("ab".repeat(32), p.pinHash)
        assertEquals("p1", p.pinSalt)
        assertEquals(listOf("name", "avatar", "kids", "pin_hash", "pin_salt").sorted(),
            db.query("PRAGMA table_info(sync_profiles)") { it.getString("name") }.filter { it in setOf("name", "avatar", "kids", "pin_hash", "pin_salt", "pin") }.sorted())
    }

    @Test fun `limits refuse oversized requests, bad fields and too many items`() {
        val (s, _) = store(SyncLimits(maxChangesPerRequest = 5, maxItemsPerUser = 4, maxProfilesPerUser = 2))
        fun refused(code: String, changes: List<SyncChange>) = try {
            s.push("u1", changes); fail("expected $code")
        } catch (e: ApiError) {
            assertEquals(code, e.code)
        }
        refused("too_many_changes", (1..6).map { resume("P", "movie:$it", 1) })
        refused("invalid_change", listOf(resume("P", "x".repeat(201), 1)))
        refused("invalid_change", listOf(resume("P", "movie:1", 0)))
        refused("invalid_change", listOf(SyncChange("password", "P", "k", 1)))
        refused("invalid_change", listOf(profile("P", 1).copy(name = "n".repeat(41))))
        refused("invalid_change", listOf(profile("P", 1).copy(key = "other")))
        refused("invalid_change", listOf(resume("P", "movie:1", 1).copy(overview = "o".repeat(4_001))))
        refused("too_many_profiles", listOf(profile("A", 1), profile("B", 1), profile("C", 1)))
        // The refused requests left nothing behind.
        assertEquals(0L, s.pull("u1", 0).cursor)
        s.push("u1", (1..4).map { resume("P", "movie:$it", 1) })
        refused("too_many_items", listOf(resume("P", "movie:5", 1)))
        // Updating an existing item is still possible at the limit.
        assertEquals(1, s.push("u1", listOf(resume("P", "movie:1", 2))).applied)
    }

    @Test fun `a clock far ahead is brought back to the server's time`() {
        val now = 1_800_000_000_000L
        val (s, _) = store(now = { now })
        s.push("u1", listOf(resume("P", "movie:1", now + 365L * 86_400_000)))
        assertEquals(now + SyncLimits().maxClockSkewMillis, s.pull("u1", 0).changes.single().updatedAt)
    }

    @Test fun `routes need a session and keep each account's data to itself`() = testApplication {
        val server = TestServer(tmp).also { it.install(this) }
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/sync", null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.postJson("/api/sync", push(listOf(profile("P", 1)))).status)

        val owner = client.setupOwner()
        val code = client.postJson("/api/invites", token = owner).json().str("code")
        val member = client.postJson("/api/auth/join", """{"code":"$code","name":"Sam","passphrase":"sam's long phrase","deviceName":"Tablet"}""").json().str("token")

        val pushed = client.postJson("/api/sync", push(listOf(profile("P", 1), resume("P", "tv:1399:1:2", 5))), token = owner)
        assertEquals(HttpStatusCode.OK, pushed.status)
        assertEquals("""{"applied":2,"ignored":0,"cursor":2}""", pushed.bodyAsText())

        // The member sees nothing of the owner's, and the same keys in their account are their own.
        assertEquals("""{"changes":[],"cursor":0,"more":false}""", client.getWith("/api/sync?since=0", member).bodyAsText())
        val theirs = client.postJson("/api/sync", push(listOf(resume("P", "tv:1399:1:2", 1, position = 7))), token = member).json()
        assertEquals("1", theirs.str("applied"))
        assertEquals("1", theirs.str("cursor"))
        val ownerPage = json.decodeFromString<SyncPullBody>(client.getWith("/api/sync?since=0", owner).bodyAsText())
        assertEquals(listOf(1L, 2L), ownerPage.changes.map { it.seq })
        assertEquals(60_000L, ownerPage.changes.last().positionMs)
        // One row per account for the same key.
        assertEquals(listOf(2), server.db.query("SELECT COUNT(DISTINCT user_id) FROM sync_resume WHERE item_key = ?", "tv:1399:1:2") { it.getInt(1) })

        assertEquals(HttpStatusCode.BadRequest, client.getWith("/api/sync?since=abc", owner).status)
        assertEquals("""{"error":"cursor_ahead"}""", client.getWith("/api/sync?since=99", owner).bodyAsText())
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/api/sync", """{"changes":[{"type":"resume"}]}""", token = owner).status)
        val bad = client.postJson("/api/sync", push(listOf(resume("P", "", 1))), token = owner)
        assertEquals("""{"error":"invalid_change"}""", bad.bodyAsText())
    }
}
