package com.sokoul.pc.playback.sources
import com.sokoul.pc.playback.session.PlayFailure
import com.sokoul.pc.playback.session.PlayFailureException
import com.sokoul.pc.playback.session.StartClock
import com.sokoul.pc.playback.session.Transfer
import com.sokoul.iptv.data.debrid.DebridQuery
import com.sokoul.iptv.data.debrid.DebridStream
import com.sokoul.iptv.data.debrid.RdException
import com.sokoul.iptv.data.debrid.RdFile
import com.sokoul.iptv.data.debrid.RdTorrent
import com.sokoul.iptv.data.debrid.RealDebridApi
import com.sokoul.iptv.data.debrid.ReleaseName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Turns a Real-Debrid version into a direct link through the account, following its download. A version known to the
 * account is reused. Otherwise it is added with only the right file. If it is not ready after a few seconds, a quick try
 * removes it again so nothing piles up, and [link] with `keep` stays and reports the progress until it can play.
 */
class DebridTransfers(
    private val api: RealDebridApi,
    private val pollMs: Long = POLL_MS,
    private val quickPollMs: Long = QUICK_POLL_MS,
    private val stallMs: Long = STALL_MS,
    private val nobodyMs: Long = NOBODY_MS,
) {
    /** What the account already holds, by torrent hash. An account that does not answer simply tells nothing. */
    suspend fun account(): Map<String, RdTorrent> = runCatching { api.torrents(ACCOUNT_LIMIT).filter { it.status !in DEAD }.associateBy { it.hash } }
        .getOrElse { if (it is CancellationException) throw it else emptyMap() }

    /** Torrents this app added, by hash: only these may be removed again. One that was already in the account is the user's. */
    private val added = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Removes the torrent a try added for [hash], when another version won a race after this one had got its link too.
     * Nothing happens to a torrent that was in the account before.
     */
    suspend fun forget(hash: String) {
        val id = added.remove(hash.lowercase()) ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { api.deleteTorrent(id) } }
    }

    suspend fun link(stream: DebridStream, query: DebridQuery, keep: Boolean, onProgress: (Transfer) -> Unit): String {
        val hash = stream.infoHash ?: throw PlayFailureException(PlayFailure.NoSource)
        try {
            // A season pack can be there several times, each copy holding the episodes that were asked of it.
            val copies = api.torrents(ACCOUNT_LIMIT).filter { it.hash.equals(hash, ignoreCase = true) && it.status !in DEAD }
            StartClock.note("rd ${hash.take(6)} account listed, ${copies.size} copies")
            var readyCopyMissingFile = false
            for (copy in copies) {
                val info = api.torrentInfo(copy.id)
                if (pick(info.files.filter { it.selected }, stream, query) != null || !info.ready) return fromExisting(copy.id, stream, query, keep, onProgress)
                readyCopyMissingFile = true
            }
            // Never add a duplicate or silently change selected files on an already-ready account copy.
            if (readyCopyMissingFile) throw PlayFailureException(PlayFailure.NotReady(null))
            return fromNew(hash, stream, query, keep, onProgress)
        } catch (e: RdException) {
            if (e.apiCode == TORRENT_ALREADY_ACTIVE) {
                try {
                    val copies = api.torrents(RECONCILE_LIMIT).filter { it.hash.equals(hash, ignoreCase = true) && it.status !in DEAD }
                    var readyCopyMissingFile = false
                    for (copy in copies) {
                        val info = api.torrentInfo(copy.id)
                        if (pick(info.files.filter { it.selected }, stream, query) != null || !info.ready) {
                            return fromExisting(copy.id, stream, query, keep, onProgress)
                        }
                        readyCopyMissingFile = true
                    }
                    if (readyCopyMissingFile) throw PlayFailureException(PlayFailure.NotReady(null))
                } catch (failure: PlayFailureException) {
                    throw failure
                } catch (lookup: RdException) {
                    throw PlayFailureException(failureOf(lookup))
                }
                throw PlayFailureException(PlayFailure.NotReady(null))
            }
            // Real-Debrid's own reason in the source timings ("infringing_file", "torrent_file_invalid"…): a refused
            // version otherwise only says "dead link".
            StartClock.note("rd ${hash.take(6)} refused: HTTP ${e.code} code ${e.apiCode} ${e.message.orEmpty().take(60)}")
            throw PlayFailureException(failureOf(e))
        }
    }

    private suspend fun fromExisting(id: String, stream: DebridStream, query: DebridQuery, keep: Boolean, onProgress: (Transfer) -> Unit): String {
        val info = api.torrentInfo(id)
        if (info.ready) {
            val file = pick(info.files.filter { it.selected }, stream, query)
            val link = file?.let { linkFor(info, it) }
            if (link != null) return unrestricted(link, query)
            // Ready, but with other files chosen when it was added: this episode was not part of the download.
            throw PlayFailureException(PlayFailure.NotReady(null))
        }
        if (!keep) throw PlayFailureException(PlayFailure.NotReady(info.progress.coerceIn(0, 100)))
        return follow(id, stream, query, onProgress)
    }

    private suspend fun fromNew(hash: String, stream: DebridStream, query: DebridQuery, keep: Boolean, onProgress: (Transfer) -> Unit): String {
        val id = api.addMagnet("magnet:?xt=urn:btih:$hash")
        added[hash.lowercase()] = id
        StartClock.note("rd ${hash.take(6)} added")
        var keepTorrent = false
        try {
            var info = api.torrentInfo(id)
            var waits = 0
            while (info.files.isEmpty() && info.status !in DEAD && waits < METADATA_POLLS) {
                delay(quickPollMs)
                info = api.torrentInfo(id)
                waits++
            }
            StartClock.note("rd ${hash.take(6)} files ${info.files.size}, status ${info.status}")
            if (info.status in DEAD) throw PlayFailureException(PlayFailure.DeadLink)
            if (info.files.isEmpty()) {
                if (!keep) throw PlayFailureException(PlayFailure.NotReady(null))
                // Picked by hand: the torrent stays, and its file can be chosen once Real-Debrid has read it.
                keepTorrent = true
                return followOwn(id, stream, query, onProgress)
            }
            val file = pick(info.files, stream, query) ?: throw PlayFailureException(PlayFailure.FakeFile)
            api.selectFiles(id, listOf(file.id))
            repeat(READY_POLLS) { attempt ->
                info = api.torrentInfo(id)
                if (info.ready) return unrestricted(linkFor(info, file) ?: throw PlayFailureException(PlayFailure.DeadLink), query).also { keepTorrent = true }
                if (info.status in DEAD) throw PlayFailureException(PlayFailure.DeadLink)
                if (attempt < READY_POLLS - 1) delay(quickPollMs)
            }
            if (!keep) throw PlayFailureException(PlayFailure.NotReady(info.progress.coerceIn(0, 100)))
            keepTorrent = true
            return followOwn(id, stream, query, onProgress)
        } finally {
            // Also when the try is cancelled (another version won a race): the removal itself must not be cancelled.
            if (!keepTorrent) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { runCatching { api.deleteTorrent(id) } }
        }
    }

    /** A torrent added here that goes nowhere is removed. One that was already in the account stays: it is the user's. */
    private suspend fun followOwn(id: String, stream: DebridStream, query: DebridQuery, onProgress: (Transfer) -> Unit): String = try {
        follow(id, stream, query, onProgress)
    } catch (e: PlayFailureException) {
        api.deleteTorrent(id)
        throw e
    } catch (c: CancellationException) {
        // Left for another version before anything came: nothing stays behind on the account. What has begun goes on.
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            runCatching { if (api.torrentInfo(id).progress <= 0) api.deleteTorrent(id) }
        }
        throw c
    }

    /** Waits for the download, reporting it every two seconds. Cancelling leaves it running in the account. */
    private suspend fun follow(id: String, stream: DebridStream, query: DebridQuery, onProgress: (Transfer) -> Unit): String {
        var lastPercent = -1
        var shownPercent = 0
        var movedAt = System.currentTimeMillis()
        val startedAt = movedAt
        while (true) {
            val info = api.torrentInfo(id)
            // Nobody has this file: not one byte and nobody to ask. Waiting three minutes would only look like a breakdown.
            if (info.progress <= 0 && info.speedBps == 0L && info.seeders == 0 && System.currentTimeMillis() - startedAt >= nobodyMs) {
                throw PlayFailureException(PlayFailure.NotReady(0))
            }
            if (info.progress != lastPercent) {
                lastPercent = info.progress
                movedAt = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - movedAt >= stallMs) {
                // No one has the file: waiting longer would hold the player on this version for nothing.
                throw PlayFailureException(PlayFailure.NotReady(info.progress.coerceIn(0, 100)))
            }
            if (info.status in DEAD) throw PlayFailureException(PlayFailure.DeadLink)
            if (info.status == WAITING_FILES && info.files.isNotEmpty()) {
                val file = pick(info.files, stream, query) ?: throw PlayFailureException(PlayFailure.FakeFile)
                api.selectFiles(id, listOf(file.id))
            }
            if (info.ready) {
                val file = pick(info.files.filter { it.selected }, stream, query) ?: throw PlayFailureException(PlayFailure.FakeFile)
                return unrestricted(linkFor(info, file) ?: throw PlayFailureException(PlayFailure.DeadLink), query)
            }
            // Real-Debrid starts over from 0 for the last step, once the file is downloaded: a person must not see it go back.
            shownPercent = maxOf(shownPercent, info.progress.coerceIn(0, 100))
            onProgress(Transfer(shownPercent, info.speedBps, info.seeders, info.bytes))
            delay(pollMs)
        }
    }

    /** Honor explicit files and episode numbers; ambiguous movie packs require catalog identity. */
    private fun pick(files: List<RdFile>, s: DebridStream, q: DebridQuery): RdFile? {
        if (files.isEmpty()) return null
        val season = q.season.takeIf { q.series }
        val episode = q.episode.takeIf { q.series }
        val byName = s.filename?.let { n -> files.firstOrNull { it.name.equals(n, ignoreCase = true) && ReleaseName.isVideo(it.path) } }
        if (season != null && episode != null) {
            // Never "the largest video" of a pack where the episode is missing.
            // Anime: by the number since the first episode too ("One Piece - 062" is S02E01), and the file named by the
            // search service only when its own number does not say another episode.
            if (q.absolute != null) {
                return ReleaseName.pickFile(files, season, episode, q.absolute)
                    ?: byName?.takeIf { f -> com.sokoul.iptv.data.debrid.DebridStreams.episodeMatches(f.name, season, episode, q.absolute) != false }
            }
            // A file without a readable episode is taken only under the name the search service gave for this episode:
            // a pack already in the account holds only the episode selected the last time, and that one is not this one.
            return ReleaseName.pickFile(files, season, episode)?.takeIf { f -> ReleaseName.episodeTag(f.name) != null || s.filename == null || f.name.equals(s.filename, ignoreCase = true) }
                ?: byName?.takeIf { f -> ReleaseName.episodeTag(f.name).let { it == null || it == season to episode } }
        }
        val byIndex = s.fileIdx?.let { files.sortedBy { f -> f.id }.getOrNull(it) }?.takeIf { ReleaseName.isVideo(it.path) }
        fun compatible(file: RdFile) = !com.sokoul.iptv.data.debrid.DebridStreams.wrongMovie(file.name, s.movieTitles, s.movieYear)
        return byName?.takeIf(::compatible) ?: byIndex?.takeIf { compatible(it) &&
            (files.count { f -> ReleaseName.isVideo(f.path) } == 1 ||
                com.sokoul.iptv.data.debrid.DebridStreams.movieTitleMatches(it.name, s.movieTitles)) }
            ?: ReleaseName.pickFile(files, null, null, movieTitles = s.movieTitles, movieYear = s.movieYear)
    }

    /** Links follow the order of the chosen files. */
    /**
     * The playable link of [link], refused when Real-Debrid names a file of another episode: a pack's file chosen by
     * mistake (an index, the file selected last time) is never played as this episode (PR #7, The Handmaid's Tale S04E06).
     */
    private suspend fun unrestricted(link: String, query: DebridQuery): String {
        val file = api.unrestrict(link)
        if (query.series && com.sokoul.iptv.data.debrid.DebridStreams.episodeMatches(file.filename, query.season, query.episode, query.absolute) == false) {
            StartClock.note("rd file ${file.filename.take(60)} is another episode")
            throw PlayFailureException(PlayFailure.Doubtful(com.sokoul.pc.playback.session.Doubt.OtherEpisode))
        }
        return file.url
    }

    private fun linkFor(info: RdTorrent, file: RdFile): String? {
        val selected = info.files.filter { it.selected }.sortedBy { it.id }
        return info.links.getOrNull(selected.indexOfFirst { it.id == file.id }) ?: info.links.singleOrNull()
    }

    companion object {
        private const val POLL_MS = 2_000L
        private const val STALL_MS = 180_000L
        // Real-Debrid finds a file's peers within seconds when there are any: 25 s held each dead version on screen in the
        // "wait for the best" start, twice in a row for a Spartacus episode (2026-11-19).
        private const val NOBODY_MS = 12_000L
        private const val QUICK_POLL_MS = 1_000L
        private const val ACCOUNT_LIMIT = 100
        private const val RECONCILE_LIMIT = 5000
        private const val TORRENT_ALREADY_ACTIVE = 33
        private const val METADATA_POLLS = 4
        private const val READY_POLLS = 3
        private const val WAITING_FILES = "waiting_files_selection"
        private val DEAD = setOf("error", "magnet_error", "virus", "dead")

        /** Real-Debrid's documented codes. */
        fun failureOf(e: RdException): PlayFailure = when {
            e.code == 401 || e.apiCode == 8 || e.apiCode == 9 -> PlayFailure.KeyRefused
            e.apiCode == 21 || e.apiCode == 23 || e.apiCode == 36 || e.apiCode == 18 -> PlayFailure.DebridLimit
            e.apiCode == 35 -> PlayFailure.Copyright
            e.apiCode == 28 || e.apiCode == 24 -> PlayFailure.DeadLink
            // Still "too many requests" after the API's own retries: Real-Debrid is to be left alone for now. Taken as a passing
            // network error, it had the player ask for every version in turn for three minutes (2026-11-08).
            e.code == 429 || e.apiCode == 34 -> PlayFailure.DebridLimit
            e.code == 503 || e.apiCode == 25 || e.apiCode == 19 -> PlayFailure.Network
            else -> PlayFailure.Unknown
        }
    }
}
