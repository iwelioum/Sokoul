package com.sokoul.pc

import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.Resume
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.account.AccountProblem
import com.sokoul.pc.account.AccountResult
import com.sokoul.pc.account.Problems
import com.sokoul.pc.data.L
import com.sokoul.pc.data.MyList
import com.sokoul.pc.data.Profiles
import com.sokoul.pc.data.TitleAlerts
import com.sokoul.pc.sync.SyncChange
import com.sokoul.pc.sync.SyncEngine
import com.sokoul.pc.sync.SyncPage
import com.sokoul.pc.sync.SyncPushAnswer
import com.sokoul.pc.sync.SyncTransport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Lot 6 on the app side: two devices of one account, an in-memory server with the server's rules. */
class SyncEngineTest {
    /** The server's rules (SyncStore): last writer wins per item, a tie keeps the server's copy, a per-account cursor. */
    class FakeServer : SyncTransport {
        var online = true
        var pageSize = 1_000
        val pushes = mutableListOf<List<SyncChange>>()
        private val rows = LinkedHashMap<Triple<String, String, String>, SyncChange>()
        private var seq = 0L

        fun items(type: String) = rows.values.filter { it.type == type }

        override suspend fun pull(since: Long): AccountResult<SyncPage> {
            if (!online) return AccountResult.Failed(Problems.unreachable)
            if (since > seq) return AccountResult.Failed(AccountProblem("cursor_ahead", L("", "")))
            val all = rows.values.filter { it.seq!! > since }.sortedBy { it.seq }
            val page = all.take(pageSize)
            return AccountResult.Ok(SyncPage(page, page.lastOrNull()?.seq ?: since, all.size > pageSize))
        }

        override suspend fun push(changes: List<SyncChange>): AccountResult<SyncPushAnswer> {
            if (!online) return AccountResult.Failed(Problems.unreachable)
            pushes += changes
            var applied = 0
            var ignored = 0
            for (c in changes) {
                val k = Triple(c.type, c.profile, c.key)
                val stored = rows[k]
                if (stored != null && c.updatedAt <= stored.updatedAt) { ignored++; continue }
                rows.remove(k)
                rows[k] = (if (c.deleted) SyncChange(c.type, c.profile, c.key, c.updatedAt, deleted = true) else c).copy(seq = ++seq)
                applied++
            }
            return AccountResult.Ok(SyncPushAnswer(applied, ignored, seq))
        }
    }

    private val files = mutableListOf<File>()
    private val dbs = mutableListOf<DesktopDb>()
    private var now = 1_800_000_000_000L
    private var user: String? = "user-1"

    private fun device(): DesktopDb =
        DesktopDb(File.createTempFile("sync", ".db").also { files += it; it.delete() }).also { dbs += it }

    private fun engine(db: DesktopDb, server: SyncTransport, reloaded: () -> Unit = {}) =
        SyncEngine(db, server, userId = { user }, afterRemoteChange = reloaded, clock = { now })

    private fun watched(id: Int, at: Long, position: Long = 600_000, title: String = "Film $id") =
        Resume(Resume.key("movie", id, null, null), "movie", id, null, null, title, null, "/p$id.jpg", position, 6_000_000, at, false)

    private fun episode(at: Long) = Resume(Resume.key("tv", 1399, 1, 2), "tv", 1399, 1, 2, "Game of Thrones", "S1 E2", null, 1_200_000, 3_600_000, at, false)

    @After
    fun close() {
        dbs.forEach { it.close() }
        files.forEach { File(it.path + "-wal").delete(); File(it.path + "-shm").delete(); it.delete() }
    }

    @Test
    fun theFirstSignInUploadsEveryProfileAndItsItemsWithKidsAndOnlyTheCodeDigest() = runBlocking {
        val db = device()
        val profiles = Profiles(db)
        val kid = profiles.add("Lou", kids = true)
        val parent = profiles.add("Sam", kids = false)
        profiles.setPin(parent, "4821")
        db.saveResume(watched(603, now - 5_000))
        db.putResumeOf(kid.id, episode(now - 4_000))
        MyList(db).toggle(TmdbItem(27205, "Inception", null, 2010, "/i.jpg", null, null, "A thief", mediaType = "movie"))
        db.putSetting(Profiles.keyFor(TitleAlerts.SETTING, kid.id), """[{"kind":"tv","id":1399,"title":"Game of Thrones","told":"S1E2"}]""")
        val server = FakeServer()

        assertTrue(engine(db, server).syncNow())

        val sent = server.items(SyncChange.PROFILE)
        assertEquals(3, sent.size)
        val lou = sent.single { it.name == "Lou" }
        assertEquals(true, lou.kids)
        assertTrue(SyncEngine.isUuid(lou.profile))
        val sam = sent.single { it.name == "Sam" }
        assertNotNull(sam.pinHash)
        assertFalse(server.pushes.flatten().any { c -> listOf(c.pinHash, c.pinSalt, c.name, c.title).any { it?.contains("4821") == true } })
        // The first profile of every device is the account's one profile, derived from the user id.
        assertTrue(sent.any { it.profile == SyncEngine.mainUuid("user-1") })
        assertEquals(setOf("movie:603" to SyncEngine.mainUuid("user-1"), "tv:1399:1:2" to lou.profile),
            server.items(SyncChange.RESUME).map { it.key to it.profile }.toSet())
        assertEquals("Inception", server.items(SyncChange.MY_LIST).single().title)
        assertEquals("S1E2", server.items(SyncChange.TITLE_ALERT).single().told)
        // Nothing left to send: a second cycle pushes nothing.
        val before = server.pushes.size
        assertTrue(engine(db, server).syncNow())
        assertEquals(before, server.pushes.size)
    }

    @Test
    fun changesMergeBothWaysAndTwoProfilesStayApart() = runBlocking {
        val server = FakeServer()
        val tv = device()
        val tvProfiles = Profiles(tv)
        val kid = tvProfiles.add("Lou", kids = true)
        tvProfiles.setPin(kid, "1234")
        tv.saveResume(watched(603, now - 60_000, position = 1_000_000))
        tv.putResumeOf(kid.id, episode(now - 50_000))
        var tvReloads = 0
        val tvSync = engine(tv, server) { tvReloads++ }
        assertTrue(tvSync.syncNow())

        val pc = device()
        val pcList = MyList(pc)
        pcList.toggle(TmdbItem(550, "Fight Club", null, 1999, "/f.jpg", null, null, null, mediaType = "movie"))
        val pcSync = engine(pc, server)
        assertTrue(pcSync.syncNow())

        // The PC now has the TV's kids profile under its account UUID, with its own resume point and its code.
        val pcProfiles = Profiles(pc)
        val lou = pcProfiles.all.single { it.name == "Lou" }
        assertTrue(lou.kids)
        assertTrue(SyncEngine.isUuid(lou.id))
        assertTrue(pcProfiles.pinMatches(lou, "1234"))
        assertFalse(pcProfiles.pinMatches(lou, "0000"))
        assertEquals(1_000_000L, pc.resumeOf(Profiles.MAIN, "movie:603")?.positionMs)
        assertNull(pc.resumeOf(Profiles.MAIN, "tv:1399:1:2"))
        assertEquals(listOf("tv:1399:1:2"), pc.resumeRowsOf(lou.id).map { it.key })
        assertTrue(pc.resumeRowsOf(lou.id).none { it.key == "movie:603" })

        // And the TV gets the PC's list on its next cycle, in the first profile only.
        now += 1_000
        assertTrue(tvSync.syncNow())
        assertEquals(listOf(550), MyList(tv).items.map { it.id })
        assertNull(tv.setting(Profiles.keyFor(MyList.SETTING, kid.id)))
        assertTrue(tvReloads > 0)
    }

    @Test
    fun aNewerLocalValueIsNeverOverwrittenAndWinsOnTheServer() = runBlocking {
        val server = FakeServer()
        val tv = device()
        tv.saveResume(watched(603, now, position = 100_000))
        val tvSync = engine(tv, server)
        assertTrue(tvSync.syncNow())
        val pc = device()
        val pcSync = engine(pc, server)
        assertTrue(pcSync.syncNow())

        // The TV goes offline and plays on; meanwhile the PC writes an older position for the same film.
        server.online = false
        tv.saveResume(watched(603, now + 20_000, position = 900_000))
        assertFalse(tvSync.syncNow())
        server.online = true
        pc.saveResume(watched(603, now + 10_000, position = 300_000))
        assertTrue(pcSync.syncNow())

        // Back online, the TV keeps its newer position and the account takes it; the PC follows.
        assertTrue(tvSync.syncNow())
        assertEquals(900_000L, tv.resumeOf(Profiles.MAIN, "movie:603")?.positionMs)
        assertEquals(900_000L, server.items(SyncChange.RESUME).single().positionMs)
        assertTrue(pcSync.syncNow())
        assertEquals(900_000L, pc.resumeOf(Profiles.MAIN, "movie:603")?.positionMs)
    }

    @Test
    fun aDeletionTravelsAsATombstone() = runBlocking {
        val server = FakeServer()
        val tv = device()
        tv.saveResume(watched(603, now))
        tv.saveResume(watched(604, now))
        MyList(tv).toggle(TmdbItem(550, "Fight Club", null, 1999, null, null, null, null, mediaType = "movie"))
        val tvSync = engine(tv, server)
        assertTrue(tvSync.syncNow())
        val pc = device()
        val pcSync = engine(pc, server)
        assertTrue(pcSync.syncNow())
        assertEquals(2, pc.resumeRowsOf(Profiles.MAIN).size)

        now += 60_000
        tv.deleteResume("movie:603")
        MyList(tv).let { it.toggle(it.items.single()) }
        assertTrue(tvSync.syncNow())
        assertTrue(server.items(SyncChange.RESUME).single { it.key == "movie:603" }.deleted)

        val pushed = server.pushes.size
        assertTrue(pcSync.syncNow())
        assertEquals(listOf("movie:604"), pc.resumeRowsOf(Profiles.MAIN).map { it.key })
        assertTrue(MyList(pc).items.isEmpty())
        // Applying the deletions is not a change of the PC's own: nothing goes back up.
        assertTrue(pcSync.syncNow())
        assertEquals(pushed, server.pushes.size)
    }

    @Test
    fun offlineChangesWaitThenFlushAndTheReasonIsKept() = runBlocking {
        val server = FakeServer().apply { online = false }
        val db = device()
        val sync = engine(db, server)
        db.saveResume(watched(603, now))
        assertFalse(sync.syncNow())
        assertEquals("unreachable", sync.status.value.problem?.code)
        assertNull(sync.status.value.lastSyncedAt)

        // Still offline, one more change; nothing is lost while waiting, even across a restart of the engine.
        db.saveResume(watched(604, now + 1))
        assertFalse(engine(db, server).syncNow())
        server.online = true
        now += 10_000
        val again = engine(db, server)
        assertTrue(again.syncNow())
        assertEquals(setOf("movie:603", "movie:604"), server.items(SyncChange.RESUME).map { it.key }.toSet())
        assertNull(again.status.value.problem)
        assertEquals(now, again.status.value.lastSyncedAt)
    }

    @Test
    fun aProfileRemovedOnAnotherDeviceLeavesWithItsData() = runBlocking {
        val server = FakeServer()
        val tv = device()
        val tvProfiles = Profiles(tv)
        val kid = tvProfiles.add("Lou", kids = true)
        tv.putResumeOf(kid.id, episode(now))
        val tvSync = engine(tv, server)
        assertTrue(tvSync.syncNow())
        val pc = device()
        val pcSync = engine(pc, server)
        assertTrue(pcSync.syncNow())
        val lou = Profiles(pc).all.single { it.name == "Lou" }
        assertEquals(1, pc.resumeRowsOf(lou.id).size)

        now += 1_000
        tvProfiles.remove(tvProfiles.all.single { it.id == kid.id })
        assertTrue(tvSync.syncNow())
        assertTrue(pcSync.syncNow())
        assertTrue(Profiles(pc).all.none { it.name == "Lou" })
        assertTrue(pc.resumeRowsOf(lou.id).isEmpty())
    }

    @Test
    fun theAccountsFirstProfileKeepsItsNameAndPagesAreAllRead() = runBlocking {
        val server = FakeServer().apply { pageSize = 2 }
        val tv = device()
        tv.putSetting("profiles", """[{"id":"","name":"Salon"}]""")
        (1..5).forEach { tv.saveResume(watched(it, now + it)) }
        assertTrue(engine(tv, server).syncNow())
        val pc = device()
        pc.putSetting("profiles", """[{"id":"","name":"Bureau"}]""")
        assertTrue(engine(pc, server).syncNow())
        // Both first profiles are one account profile: the account's name stays, the PC takes it.
        assertEquals("Salon", Profiles(pc).all.single().name)
        assertEquals("Salon", server.items(SyncChange.PROFILE).single().name)
        assertEquals(5, pc.resumeRowsOf(Profiles.MAIN).size)
    }

    @Test
    fun signedOutNothingHappens() = runBlocking {
        user = null
        val server = FakeServer()
        val db = device()
        db.saveResume(watched(603, now))
        assertFalse(engine(db, server).syncNow())
        assertTrue(server.pushes.isEmpty())
        assertNull(db.setting("sync.user"))
    }

    @Test
    fun theAccountPanelLineSaysWhenOrWhy() {
        val at = java.time.LocalDate.of(2026, 11, 25).atTime(14, 32).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val locale = java.util.Locale.getDefault()
        java.util.Locale.setDefault(java.util.Locale.ENGLISH)
        try {
            assertEquals("Last synced at 14:32", com.sokoul.pc.account.syncLine(com.sokoul.pc.sync.SyncStatus(at), at + 60_000))
        assertEquals("Not synced yet", com.sokoul.pc.account.syncLine(com.sokoul.pc.sync.SyncStatus(), at))
        assertTrue(com.sokoul.pc.account.syncLine(com.sokoul.pc.sync.SyncStatus(at, Problems.unreachable), at).startsWith("Not synced: The Sokoul server does not answer"))
        } finally {
            java.util.Locale.setDefault(locale)
        }
    }
}
