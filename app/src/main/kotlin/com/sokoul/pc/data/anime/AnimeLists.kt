package com.sokoul.pc.data.anime

import com.sokoul.iptv.data.anime.AnimeIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/**
 * The anime lists kept on the device: Fribb's (ids and place in the catalog) everywhere, manami's (names) on PC only, as
 * it weighs 60 MB. Downloaded the first time they are needed, then again once a week (manami's once a month), in the
 * background. A new list replaces the old one only once it is whole and valid; until then, and when the download fails,
 * the old one serves.
 */
class AnimeLists(
    private val dir: File,
    private val http: OkHttpClient,
    private val withNames: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fribbUrl: String = FRIBB_URL,
    private val manamiUrl: String = MANAMI_URL,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val minEntries: Int = MIN_ENTRIES,
) {
    private val lock = Mutex()
    private var ids: AnimeIndex? = null
    private var names: AnimeIndex? = null
    private val refreshing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val tried = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val download = http.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).build()

    /** The id list, or null while it is not on the device yet (its download then starts). Never waits for the network. */
    suspend fun ids(): AnimeIndex? = lock.withLock { ids ?: load(FRIBB).also { ids = it } }.also { refreshIfOld(FRIBB) }

    suspend fun names(): AnimeIndex? {
        if (!withNames) return null
        return lock.withLock { names ?: load(MANAMI).also { names = it } }.also { refreshIfOld(MANAMI) }
    }

    /** Starts what is missing or old, without waiting: called at start-up so that the lists are there when needed. */
    fun warmUp() {
        refreshIfOld(FRIBB)
        if (withNames) refreshIfOld(MANAMI)
    }

    private suspend fun load(name: String): AnimeIndex? = withContext(Dispatchers.IO) {
        val f = File(dir, "$name.tsv")
        if (!f.isFile) return@withContext null
        runCatching { f.bufferedReader().use(AnimeIndex::read) }.getOrNull()?.takeIf { it.size > 0 }
    }

    private fun refreshIfOld(name: String) {
        val f = File(dir, "$name.tsv")
        if (f.isFile && clock() - f.lastModified() < maxAge(name)) return
        // A download that failed is not tried again at each lookup, only some hours later.
        if (f.isFile && clock() - (tried[name] ?: Long.MIN_VALUE / 2) < RETRY_MS) return
        if (!refreshing.add(name)) return
        tried[name] = clock()
        scope.launch {
            try {
                if (refresh(name)) lock.withLock { if (name == FRIBB) ids = load(FRIBB) else names = load(MANAMI) }
            } finally {
                refreshing.remove(name)
            }
        }
    }

    /** Downloads one list, builds its index aside, checks it, then puts it in place in one move. */
    suspend fun refresh(name: String): Boolean = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val raw = File(dir, "$name.json.part")
        val built = File(dir, "$name.tsv.part")
        try {
            val url = if (name == FRIBB) fribbUrl else manamiUrl
            val ok = runCatching {
                download.newCall(Request.Builder().url(url).header("User-Agent", "SokoulTV/2.0").build()).execute().use { r ->
                    if (!r.isSuccessful) return@use false
                    val body = r.body ?: return@use false
                    raw.outputStream().use { out -> body.byteStream().copyTo(out) }
                    true
                }
            }.getOrDefault(false)
            if (!ok) return@withContext false
            val written = runCatching {
                raw.inputStream().buffered().use { input ->
                    built.bufferedWriter().use { out -> if (name == FRIBB) AnimeIndex.writeFribb(input, out) else AnimeIndex.writeManami(input, out) }
                }
            }.getOrDefault(0)
            if (written < minEntries) return@withContext false
            val index = runCatching { built.bufferedReader().use(AnimeIndex::read) }.getOrNull() ?: return@withContext false
            // A list that does not know One Piece is not the list.
            val sane = if (name == FRIBB) index.series(ONE_PIECE_TMDB).isNotEmpty() else index.anilist(ONE_PIECE_ANILIST) != null
            if (!sane) return@withContext false
            Files.move(built.toPath(), File(dir, "$name.tsv").toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            true
        } finally {
            raw.delete()
            built.delete()
        }
    }

    companion object {
        const val FRIBB = "fribb"
        const val MANAMI = "manami"
        const val FRIBB_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
        const val MANAMI_URL = "https://github.com/manami-project/anime-offline-database/releases/latest/download/anime-offline-database-minified.json"
        private const val WEEK_MS = 7 * 24 * 3_600_000L
        private const val MONTH_MS = 30 * 24 * 3_600_000L
        private const val RETRY_MS = 6 * 3_600_000L

        /**
         * Fribb's list changes every week and places the new seasons: weekly. manami's (60 MB) has not changed since
         * 2026-07-04 and only adds names: the file on the device serves, asked again once a month at most (2026-11-24).
         */
        fun maxAge(name: String): Long = if (name == MANAMI) MONTH_MS else WEEK_MS
        /** Both lists hold some 40,000 entries: far fewer is a truncated file. */
        const val MIN_ENTRIES = 20_000
        private const val ONE_PIECE_TMDB = 37854
        private const val ONE_PIECE_ANILIST = 21
    }
}
