package com.sokoul.desktop.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Base SQLite du PC (sqlite-jdbc). Tables reprises du schéma Room de l'app Android, limitées à ce que le PC utilise.
 * Accès synchronisé : une seule connexion, appelée depuis Dispatchers.IO.
 */
class DesktopDb(file: File) : AutoCloseable {
    private val databaseDirectory = file.absoluteFile.parentFile
    private val databaseUrl = "jdbc:sqlite:${file.absolutePath}"
    private val writeAheadLog = File(file.absolutePath + "-wal")
    private val conn: Connection = DriverManager.getConnection(databaseUrl).apply {
        createStatement().use {
            it.execute("PRAGMA busy_timeout=5000")
            it.execute("PRAGMA journal_mode=WAL")
            it.execute("PRAGMA journal_size_limit=$WAL_RETAINED_BYTES")
            it.execute("PRAGMA foreign_keys=ON")
        }
    }
    /** Provider catalogue reads use a separate WAL reader so a long EPG write cannot freeze browsing. */
    private val providerReadLock = Any()
    private val providerReadConnectionLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
            createStatement().use { it.execute("PRAGMA busy_timeout=5000"); it.execute("PRAGMA query_only=ON") }
        }
    }
    private val providerReadConnection by providerReadConnectionLazy

    /**
     * The small reads the screens make (what was watched, settings, favourites) on their own WAL reader: on the main
     * lock they waited for the guide import, and the Chromecast HD was killed for not answering a key (ANR, 2026-11-22).
     */
    private val lightReadLock = Any()
    private val lightReadConnectionLazy = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").apply {
            createStatement().use { it.execute("PRAGMA busy_timeout=5000"); it.execute("PRAGMA query_only=ON") }
        }
    }

    /** A read that never waits for a writer. Inside a transaction of this thread, the writer's connection sees its own rows. */
    private fun <T> light(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        if (Thread.holdsLock(this)) query(conn, sql, *args, map = map)
        else synchronized(lightReadLock) { query(lightReadConnectionLazy.value, sql, *args, map = map) }

    private fun <T> catalogue(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        if (Thread.holdsLock(this)) query(conn, sql, *args, map = map)
        else synchronized(providerReadLock) { query(providerReadConnection, sql, *args, map = map) }

    init { migrate() }

    internal fun hasRoomForBackgroundSync(): Boolean =
        databaseDirectory.usableSpace >= BACKGROUND_SYNC_FREE_BYTES

    /** Reclaim storage after large imports without queueing behind an active transaction or reader. */
    internal fun checkpointStorage() {
        if (writeAheadLog.length() <= WAL_RETAINED_BYTES) return
        DriverManager.getConnection(databaseUrl).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA busy_timeout=0")
                statement.executeQuery("PRAGMA wal_checkpoint(TRUNCATE)").use { it.next() }
            }
        }
    }

    private fun migrate() = exec(
        """CREATE TABLE IF NOT EXISTS playlists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, type TEXT NOT NULL,
            url TEXT NOT NULL, username TEXT, password TEXT, epg_url TEXT, user_agent TEXT, synced_at INTEGER NOT NULL DEFAULT 0)""",
        """CREATE TABLE IF NOT EXISTS categories(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL,
            hidden INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(playlist_id, id))""",
        """CREATE TABLE IF NOT EXISTS channels(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, number INTEGER NOT NULL, name TEXT NOT NULL,
            logo TEXT, category_id TEXT NOT NULL, epg_key TEXT, name_key TEXT NOT NULL, url TEXT, user_agent TEXT, referrer TEXT,
            position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        "CREATE INDEX IF NOT EXISTS channels_cat ON channels(playlist_id, category_id, position)",
        """CREATE TABLE IF NOT EXISTS programs(playlist_id INTEGER NOT NULL, channel_key TEXT NOT NULL, start INTEGER NOT NULL,
            stop INTEGER NOT NULL, title TEXT NOT NULL, description TEXT, PRIMARY KEY(playlist_id, channel_key, start))""",
        "CREATE INDEX IF NOT EXISTS programs_time ON programs(playlist_id, stop)",
        """CREATE TABLE IF NOT EXISTS favorites(playlist_id INTEGER NOT NULL, type TEXT NOT NULL, id TEXT NOT NULL, added_at INTEGER NOT NULL,
            PRIMARY KEY(playlist_id, type, id))""",
        """CREATE TABLE IF NOT EXISTS progress(playlist_id INTEGER NOT NULL, type TEXT NOT NULL, id TEXT NOT NULL, title TEXT NOT NULL,
            position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL, updated_at INTEGER NOT NULL, finished INTEGER NOT NULL,
            PRIMARY KEY(playlist_id, type, id))""",
        "CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        """CREATE TABLE IF NOT EXISTS movies(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL,
            year INTEGER, poster TEXT, stream_url TEXT, rating REAL, category_id TEXT, extension TEXT, added INTEGER, tmdb_id TEXT,
            position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        """CREATE TABLE IF NOT EXISTS series(playlist_id INTEGER NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL,
            year INTEGER, poster TEXT, backdrop TEXT, plot TEXT, rating REAL, category_id TEXT, last_modified INTEGER,
            tmdb_id TEXT, position INTEGER NOT NULL, PRIMARY KEY(playlist_id, id))""",
        """CREATE TABLE IF NOT EXISTS provider_categories(playlist_id INTEGER NOT NULL, kind TEXT NOT NULL, id TEXT NOT NULL, name TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(playlist_id,kind,id))""",
        "CREATE INDEX IF NOT EXISTS movies_category_page ON movies(playlist_id,category_id,position)",
        "CREATE INDEX IF NOT EXISTS series_category_page ON series(playlist_id,category_id,position)",
        "CREATE INDEX IF NOT EXISTS movies_playlist_page ON movies(playlist_id,position)",
        "CREATE INDEX IF NOT EXISTS series_playlist_page ON series(playlist_id,position)",
        "CREATE INDEX IF NOT EXISTS movies_search ON movies(playlist_id, name)",
        "CREATE INDEX IF NOT EXISTS series_search ON series(playlist_id, name)",
        "CREATE TABLE IF NOT EXISTS tmdb_cache(key TEXT PRIMARY KEY, json TEXT NOT NULL, fetched_at INTEGER NOT NULL)",
        // Real-Debrid torrents Sokoul asked for, and the film or episode each one last played: the daily cleanup's memory.
        "CREATE TABLE IF NOT EXISTS rd_torrents(hash TEXT PRIMARY KEY, added_at INTEGER NOT NULL, played_key TEXT, played_at INTEGER)",
        // reprise des films et épisodes TMDB (lus via Real-Debrid ou l'IPTV) : rangée « Reprendre » de l'accueil
        """CREATE TABLE IF NOT EXISTS resume(key TEXT PRIMARY KEY, kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL, season INTEGER,
            episode INTEGER, title TEXT NOT NULL, subtitle TEXT, image TEXT, position_ms INTEGER NOT NULL, duration_ms INTEGER NOT NULL,
            updated_at INTEGER NOT NULL, finished INTEGER NOT NULL)""",
        // enregistrements du direct (programmés, en cours, terminés, échecs)
        """CREATE TABLE IF NOT EXISTS recordings(id INTEGER PRIMARY KEY AUTOINCREMENT, playlist_id INTEGER NOT NULL, channel_id TEXT NOT NULL,
            channel_name TEXT NOT NULL, channel_logo TEXT, title TEXT NOT NULL, start_at INTEGER NOT NULL, end_at INTEGER NOT NULL,
            status TEXT NOT NULL, path TEXT, bytes INTEGER NOT NULL DEFAULT 0, error TEXT, created_at INTEGER NOT NULL)""",
    )

    init {
        runCatching { exec("ALTER TABLE movies ADD COLUMN stream_url TEXT") }
        // The programme's picture, added after the first release: an older base gets the column, empty until the next sync.
        runCatching { exec("ALTER TABLE programs ADD COLUMN icon TEXT") }
        // Profiles: each resume row belongs to one. The first profile is "", so rows written before profiles stay its own.
        runCatching { exec("ALTER TABLE resume ADD COLUMN profile TEXT NOT NULL DEFAULT ''") }
        runCatching { exec("CREATE INDEX IF NOT EXISTS resume_profile ON resume(profile, updated_at)") }
        runCatching { exec("ALTER TABLE channels ADD COLUMN catchup_type TEXT") }
        runCatching { exec("ALTER TABLE channels ADD COLUMN catchup_source TEXT") }
        runCatching { exec("ALTER TABLE channels ADD COLUMN catchup_days INTEGER NOT NULL DEFAULT 0") }
        // Account sync (com.sokoul.pc.sync): the last state of every synced item as the server knows it, and whether
        // this device changed it since its last push. Comparing it with the live tables finds local changes.
        exec(
            """CREATE TABLE IF NOT EXISTS sync_shadow(type TEXT NOT NULL, profile TEXT NOT NULL, key TEXT NOT NULL, payload TEXT,
                updated_at INTEGER NOT NULL, deleted INTEGER NOT NULL DEFAULT 0, dirty INTEGER NOT NULL DEFAULT 1, PRIMARY KEY(type, profile, key))""",
            "CREATE INDEX IF NOT EXISTS sync_shadow_dirty ON sync_shadow(dirty)",
        )
    }

    /** The profile whose resume points and history are read and written. "" is the first profile. */
    @Volatile var profile: String = ""

    /** The stored key of a resume point: the first profile keeps the plain key, the others get theirs prefixed. */
    private fun scoped(key: String) = scopedFor(key, profile)
    private fun scopedFor(key: String, scope: String) = if (scope.isEmpty()) key else "$scope|$key"

    private fun programOf(r: java.sql.ResultSet) =
        Program(r.getString("channel_key"), r.getLong("start"), r.getLong("stop"), r.getString("title"), tidyDescription(r.getString("description")), r.getString("icon"))

    @Synchronized
    private fun exec(vararg sql: String) = conn.createStatement().use { st -> sql.forEach { st.execute(it) } }

    @Synchronized
    fun <T> tx(block: () -> T): T {
        conn.autoCommit = false
        try {
            val r = block(); conn.commit(); return r
        } catch (e: Throwable) {
            conn.rollback(); throw e
        } finally {
            conn.autoCommit = true
        }
    }

    // --- playlists ---------------------------------------------------------------------------------------

    fun playlists(): List<Playlist> = light("SELECT * FROM playlists ORDER BY id") { it.toPlaylist() }

    @Synchronized
    fun upsertPlaylist(p: Playlist): Long {
        val existing = playlists().firstOrNull { sameSource(it, p) }
        return if (existing != null) {
            update(
                "UPDATE playlists SET name=?, type=?, url=?, username=?, password=?, epg_url=?, user_agent=? WHERE id=?",
                p.name, p.type.name, p.url, p.username, p.password ?: existing.password, p.epgUrl, p.userAgent, existing.id,
            )
            existing.id
        } else {
            conn.prepareStatement(
                "INSERT INTO playlists(name, type, url, username, password, epg_url, user_agent) VALUES(?,?,?,?,?,?,?)",
                java.sql.Statement.RETURN_GENERATED_KEYS,
            ).use { st ->
                bind(st, p.name, p.type.name, p.url, p.username, p.password, p.epgUrl, p.userAgent)
                st.executeUpdate()
                st.generatedKeys.use { k -> k.next(); k.getLong(1) }
            }
        }
    }

    @Synchronized
    fun markSynced(pid: Long, at: Long) = update("UPDATE playlists SET synced_at=? WHERE id=?", at, pid)

    @Synchronized
    fun deletePlaylist(pid: Long) = tx {
        listOf("channels", "categories", "programs", "favorites", "progress", "movies", "series", "provider_categories").forEach { update("DELETE FROM $it WHERE playlist_id=?", pid) }
        update("DELETE FROM playlists WHERE id=?", pid)
    }

    // --- catégories et chaînes -------------------------------------------------------------------------

    fun categories(pid: Long): List<Category> =
        light("SELECT * FROM categories WHERE playlist_id=? ORDER BY position", pid) {
            Category(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), it.getInt("position"), it.getInt("hidden") != 0, it.getInt("locked") != 0)
        }

    /** Remplace catégories et chaînes d'une playlist ; garde les drapeaux masqué / verrouillé déjà posés. */
    @Synchronized
    fun replaceLive(pid: Long, cats: List<Category>, channels: List<Channel>) = tx {
        val flags = categories(pid).associateBy { it.id }
        update("DELETE FROM categories WHERE playlist_id=?", pid)
        update("DELETE FROM channels WHERE playlist_id=?", pid)
        conn.prepareStatement("INSERT OR REPLACE INTO categories VALUES(?,?,?,?,?,?)").use { st ->
            cats.forEach { c ->
                val f = flags[c.id]
                bind(st, pid, c.id, c.name, c.position, if (f?.hidden ?: c.hidden) 1 else 0, if (f?.locked ?: c.locked) 1 else 0)
                st.addBatch()
            }
            st.executeBatch()
        }
        conn.prepareStatement("INSERT OR REPLACE INTO channels(playlist_id,id,number,name,logo,category_id,epg_key,name_key,url,user_agent,referrer,position,catchup_type,catchup_source,catchup_days) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            channels.forEachIndexed { i, c ->
                bind(st, pid, c.id, c.number, c.name, c.logo, c.categoryId, c.epgKey, c.nameKey, c.url, c.userAgent, c.referrer, c.position,
                    c.catchupType, c.catchupSource, c.catchupDays.coerceAtLeast(0))
                st.addBatch()
                if (i % 2000 == 1999) st.executeBatch()
            }
            st.executeBatch()
        }
    }

    @Synchronized
    fun setCategoryFlags(pid: Long, id: String, hidden: Boolean, locked: Boolean) =
        update("UPDATE categories SET hidden=?, locked=? WHERE playlist_id=? AND id=?", if (hidden) 1 else 0, if (locked) 1 else 0, pid, id)

    fun channels(pid: Long, categoryId: String?): List<Channel> = when (categoryId) {
        null, SpecialGroup.ALL -> catalogue(
            "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 ORDER BY g.position, c.position", pid,
        ) { it.toChannel() }
        SpecialGroup.FAVORITES -> catalogue(
            "SELECT c.* FROM channels c JOIN favorites f ON f.playlist_id=c.playlist_id AND f.type='LIVE' AND f.id=c.id " +
                "WHERE c.playlist_id=? ORDER BY f.added_at", pid,
        ) { it.toChannel() }
        else -> catalogue("SELECT * FROM channels WHERE playlist_id=? AND category_id=? ORDER BY position", pid, categoryId) { it.toChannel() }
    }

    /** The channels sharing a normalized name, in visible groups. */
    fun channelsByNameKey(pid: Long, key: String): List<Channel> = light(
        "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
            "WHERE c.playlist_id=? AND c.name_key=? AND g.hidden=0 AND g.locked=0 ORDER BY c.position", pid, key,
    ) { it.toChannel() }

    fun channelsByIds(pid: Long, ids: List<String>): List<Channel> {
        if (ids.isEmpty()) return emptyList()
        return light("SELECT * FROM channels WHERE playlist_id=? AND id IN (${ids.joinToString(",") { "?" }})", pid, *ids.toTypedArray()) { it.toChannel() }
    }

    @Synchronized
    fun channelsByEpgKey(pid: Long, key: String): List<Channel> = query(
        "SELECT * FROM channels WHERE playlist_id=? AND epg_key=? ORDER BY position", pid, key,
    ) { it.toChannel() }

    /** Toutes les chaînes, groupes masqués et verrouillés compris (guide, recherche du rapprochement). */
    @Synchronized
    fun allChannels(pid: Long): List<Channel> = query("SELECT * FROM channels WHERE playlist_id=? ORDER BY position", pid) { it.toChannel() }

    /** Recherche par nom dans les groupes visibles (insensible aux accents grâce à la clé normalisée). */
    @Synchronized
    fun searchChannels(pid: Long, key: String, limit: Int = 200): List<Channel> = query(
        "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
            "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 AND (c.name_key LIKE ? OR lower(c.name) LIKE ?) ORDER BY g.position, c.position LIMIT ?",
        pid, "%$key%", "%$key%", limit,
    ) { it.toChannel() }

    /** Bounded TV search pages; numeric input also matches the displayed provider number. */
    @Synchronized
    fun searchTvChannels(pid: Long, key: String, offset: Int, limit: Int): List<Channel> = query(
        "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
            "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 AND (c.name_key LIKE ? OR lower(c.name) LIKE ? OR c.number=?) " +
            // Relevance first (number, exact name, name prefix) so "france 2" is not buried under "France 24".
            "ORDER BY CASE WHEN c.number=? THEN 0 WHEN c.name_key=? THEN 1 WHEN c.name_key LIKE ? THEN 2 ELSE 3 END, " +
            "g.position, c.position, c.id LIMIT ? OFFSET ?",
        pid, "%$key%", "%$key%", key.toIntOrNull() ?: -1, key.toIntOrNull() ?: -1, key, "$key%", limit, offset,
    ) { it.toChannel() }

    @Synchronized
    fun searchTvPrograms(pid: Long, text: String, from: Long, until: Long, offset: Int, limit: Int, namePrefix: String? = null): List<Pair<Program, Channel>> {
        if (namePrefix != null) {
            val local = searchTvPrograms(pid, text, from, until, offset, limit, namePrefix, regionOnly = true)
            if (local.isNotEmpty() || offset > 0) return local
        }
        return searchTvPrograms(pid, text, from, until, offset, limit, null, regionOnly = false)
    }

    @Synchronized
    private fun searchTvPrograms(pid: Long, text: String, from: Long, until: Long, offset: Int, limit: Int, namePrefix: String?, regionOnly: Boolean): List<Pair<Program, Channel>> {
        val region = if (regionOnly && namePrefix != null) " AND c.name LIKE ?" else ""
        val regionArgs: Array<Any?> = if (region.isEmpty()) emptyArray() else arrayOf("$namePrefix%")
        // COALESCE(epg_key, name_key) has no index: matching it per programme scanned every channel for each row
        // (15 s on a PC for "on now" with ~28k channels). Read the visible keys once, walk the time window in
        // start order, and resolve the channels of the page in one pass.
        val visible = HashSet<String>()
        query(
            "SELECT DISTINCT COALESCE(c.epg_key,c.name_key) FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0$region", pid, *regionArgs,
        ) { it.getString(1) }.filterNotNullTo(visible)
        if (visible.isEmpty()) return emptyList()
        val like = "%${text.trim().lowercase()}%"
        val rows = ArrayList<Program>(limit)
        var skipped = 0
        var cursor = 0
        while (rows.size < limit) {
            val batch = query(
                // Day-long rows are provider placeholders ("no programme information"), not programmes.
                "SELECT p.* FROM programs p WHERE p.playlist_id=? AND p.stop>? AND p.start<? AND lower(p.title) LIKE ? " +
                    "AND p.stop-p.start<=? ORDER BY p.start,p.channel_key LIMIT ? OFFSET ?",
                pid, from, until, like, MAX_PROGRAM_MS, PROGRAM_SCAN_BATCH, cursor,
            ) { programOf(it) }
            if (batch.isEmpty()) break
            cursor += batch.size
            for (p in batch) {
                if (p.channelKey !in visible) continue
                if (skipped < offset) { skipped++; continue }
                rows += p
                if (rows.size == limit) break
            }
        }
        if (rows.isEmpty()) return emptyList()
        val keys = rows.map { it.channelKey }.distinct()
        val byKey = query(
            "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND COALESCE(c.epg_key,c.name_key) IN (${keys.joinToString(",") { "?" }}) AND g.hidden=0 AND g.locked=0$region " +
                "ORDER BY g.position,c.position", pid, *keys.toTypedArray(), *regionArgs,
        ) { it.toChannel() }.groupBy { it.epgKey ?: it.nameKey }
        return rows.flatMap { p -> byKey[p.channelKey].orEmpty().take(32).map { p to it } }
    }

    /**
     * Recherche dans le guide (« Au programme » de la tablette) : programmes pas encore finis dont le titre contient
     * [text], chacun avec la première chaîne visible qui le diffuse (programmes sans chaîne visible écartés).
     */
    @Synchronized
    fun searchPrograms(pid: Long, text: String, now: Long, limit: Int = 30): List<Pair<Program, Channel>> {
        val progs = query(
            "SELECT * FROM programs WHERE playlist_id=? AND stop>? AND lower(title) LIKE ? ORDER BY start LIMIT ?",
            pid, now, "%${text.trim().lowercase()}%", limit * 4,
        ) { programOf(it) }
        val keys = progs.mapTo(LinkedHashSet()) { it.channelKey }
        if (keys.isEmpty()) return emptyList()
        val byKey = query(
            "SELECT c.* FROM channels c JOIN categories g ON g.playlist_id=c.playlist_id AND g.id=c.category_id " +
                "WHERE c.playlist_id=? AND g.hidden=0 AND g.locked=0 AND c.epg_key IN (${keys.joinToString(",") { "?" }}) ORDER BY g.position, c.position",
            pid, *keys.toTypedArray(),
        ) { it.toChannel() }.groupBy { it.epgKey }.mapValues { it.value.first() }
        return progs.mapNotNull { p -> byKey[p.channelKey]?.let { p to it } }.take(limit)
    }

    @Synchronized
    fun channelCount(pid: Long): Int = query("SELECT COUNT(*) FROM channels WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    @Synchronized
    fun replaceVod(pid: Long, movies: List<Movie>, series: List<Series>) = tx {
        update("DELETE FROM movies WHERE playlist_id=?", pid)
        update("DELETE FROM series WHERE playlist_id=?", pid)
        // colonnes nommées : sur une base plus ancienne, stream_url a été ajoutée en dernier (ALTER TABLE)
        conn.prepareStatement("INSERT INTO movies(playlist_id, id, name, year, poster, stream_url, rating, category_id, extension, added, tmdb_id, position) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            movies.forEachIndexed { i, movie ->
                bind(st, pid, movie.id, movie.name, movie.year, movie.poster, movie.streamUrl, movie.rating, movie.categoryId, movie.extension, movie.added, movie.tmdbId, i)
                st.addBatch()
                if (i % 500 == 499) st.executeBatch()
            }
            st.executeBatch()
        }
        conn.prepareStatement("INSERT INTO series(playlist_id, id, name, year, poster, backdrop, plot, rating, category_id, last_modified, tmdb_id, position) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)").use { st ->
            series.forEachIndexed { i, item ->
                bind(st, pid, item.id, item.name, item.year, item.poster, item.backdrop, item.plot, item.rating, item.categoryId, item.lastModified, item.tmdbId, i)
                st.addBatch()
                if (i % 500 == 499) st.executeBatch()
            }
            st.executeBatch()
        }
    }

    @Synchronized
    fun movies(pid: Long, search: String? = null, limit: Int = 500, categoryId: String? = null, offset: Int = 0): List<Movie> {
        val (where, args) = providerFilter(pid, categoryId, search)
        return query("SELECT * FROM movies WHERE $where ORDER BY position LIMIT ? OFFSET ?", *args, limit.coerceIn(1, 500), offset.coerceAtLeast(0)) {
            val year = (it.getObject("year") as? Number)?.toInt()
            val rating = (it.getObject("rating") as? Number)?.toDouble()
            val added = (it.getObject("added") as? Number)?.toLong()
            Movie(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), rating,
                it.getString("category_id"), it.getString("extension"), added, it.getString("tmdb_id"), it.getString("stream_url"))
        }
    }

    /** A dedicated read connection keeps the VOD catalogue responsive during serialized guide imports. */
    fun providerMoviesPage(pid: Long, search: String? = null, limit: Int = 37, categoryId: String? = null, offset: Int = 0): List<Movie> =
        synchronized(providerReadLock) {
            val (where, args) = providerFilter(pid, categoryId, search)
            query(providerReadConnection, "SELECT * FROM movies WHERE $where ORDER BY position LIMIT ? OFFSET ?", *args, limit.coerceIn(1, 500), offset.coerceAtLeast(0)) {
                val year = (it.getObject("year") as? Number)?.toInt()
                val rating = (it.getObject("rating") as? Number)?.toDouble()
                val added = (it.getObject("added") as? Number)?.toLong()
                Movie(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), rating,
                    it.getString("category_id"), it.getString("extension"), added, it.getString("tmdb_id"), it.getString("stream_url"))
            }
        }

    @Synchronized
    fun series(pid: Long, search: String? = null, limit: Int = 500, categoryId: String? = null, offset: Int = 0): List<Series> {
        val (where, args) = providerFilter(pid, categoryId, search)
        return query("SELECT * FROM series WHERE $where ORDER BY position LIMIT ? OFFSET ?", *args, limit.coerceIn(1, 500), offset.coerceAtLeast(0)) {
            val year = (it.getObject("year") as? Number)?.toInt()
            val rating = (it.getObject("rating") as? Number)?.toDouble()
            val modified = (it.getObject("last_modified") as? Number)?.toLong()
            Series(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), it.getString("backdrop"),
                it.getString("plot"), rating, it.getString("category_id"), modified, it.getString("tmdb_id"))
        }
    }

    fun providerSeriesPage(pid: Long, search: String? = null, limit: Int = 37, categoryId: String? = null, offset: Int = 0): List<Series> =
        synchronized(providerReadLock) {
            val (where, args) = providerFilter(pid, categoryId, search)
            query(providerReadConnection, "SELECT * FROM series WHERE $where ORDER BY position LIMIT ? OFFSET ?", *args, limit.coerceIn(1, 500), offset.coerceAtLeast(0)) {
                val year = (it.getObject("year") as? Number)?.toInt()
                val rating = (it.getObject("rating") as? Number)?.toDouble()
                val modified = (it.getObject("last_modified") as? Number)?.toLong()
                Series(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), year, it.getString("poster"), it.getString("backdrop"),
                    it.getString("plot"), rating, it.getString("category_id"), modified, it.getString("tmdb_id"))
            }
        }

    fun providerPageCount(pid: Long, kind: ProviderKind, categoryId: String? = null, search: String? = null): Int =
        synchronized(providerReadLock) {
            val (where, args) = providerFilter(pid, categoryId, search)
            val table = if (kind == ProviderKind.Movies) "movies" else "series"
            query(providerReadConnection, "SELECT COUNT(*) FROM $table WHERE $where", *args) { it.getInt(1) }.first()
        }

    @Synchronized
    fun movieCount(pid: Long): Int = query("SELECT COUNT(*) FROM movies WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    @Synchronized
    fun providerMovie(pid: Long, id: String): Movie? = query("SELECT * FROM movies WHERE playlist_id=? AND id=?", pid, id) {
        Movie(it.getLong("playlist_id"), it.getString("id"), it.getString("name"), (it.getObject("year") as? Number)?.toInt(),
            it.getString("poster"), (it.getObject("rating") as? Number)?.toDouble(), it.getString("category_id"),
            it.getString("extension"), (it.getObject("added") as? Number)?.toLong(), it.getString("tmdb_id"), it.getString("stream_url"))
    }.firstOrNull()

    @Synchronized
    fun seriesCount(pid: Long): Int = query("SELECT COUNT(*) FROM series WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    private fun providerFilter(pid: Long, categoryId: String?, search: String?): Pair<String, Array<Any>> {
        val terms = arrayListOf("playlist_id=?")
        val args = arrayListOf<Any>(pid)
        categoryId?.let { terms += "category_id=?"; args += it }
        search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let {
            terms += "instr(lower(name),?)>0"
            args += it
        }
        return terms.joinToString(" AND ") to args.toTypedArray()
    }

    @Synchronized
    fun providerCount(pid: Long, kind: ProviderKind, categoryId: String? = null, search: String? = null): Int {
        val (where, args) = providerFilter(pid, categoryId, search)
        val table = if (kind == ProviderKind.Movies) "movies" else "series"
        return query("SELECT COUNT(*) FROM $table WHERE $where", *args) { it.getInt(1) }.first()
    }

    @Synchronized
    fun replaceProviderCategories(pid: Long, kind: ProviderKind, categories: List<ProviderCategory>) = tx {
        update("DELETE FROM provider_categories WHERE playlist_id=? AND kind=?", pid, kind.name)
        conn.prepareStatement("INSERT OR REPLACE INTO provider_categories VALUES(?,?,?,?,?)").use { statement ->
            categories.forEachIndexed { index, category ->
                bind(statement, pid, kind.name, category.id, category.title, index)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    @Synchronized
    fun providerCategories(pid: Long, kind: ProviderKind): List<ProviderCategory> {
        val table = if (kind == ProviderKind.Movies) "movies" else "series"
        val counts = query("SELECT category_id,COUNT(*) FROM $table WHERE playlist_id=? GROUP BY category_id", pid) { it.getString(1) to it.getInt(2) }.toMap()
        val saved = query("SELECT id,name FROM provider_categories WHERE playlist_id=? AND kind=? ORDER BY position", pid, kind.name) {
            ProviderCategory(it.getString(1), it.getString(2), counts[it.getString(1)] ?: 0)
        }
        val known = saved.mapTo(hashSetOf()) { it.id }
        return saved + counts.entries.filter { it.key != null && it.key !in known }.map { ProviderCategory(it.key, it.key, it.value) }
    }

    @Synchronized
    fun applyGuideOffset(pid: Long, settingKey: String, minutes: Int, deltaMs: Long) = tx {
        // A temporary copy avoids primary-key collisions while shifting adjacent programme rows.
        exec("DROP TABLE IF EXISTS temp.provider_guide_shift")
        conn.prepareStatement("CREATE TEMP TABLE provider_guide_shift AS SELECT * FROM programs WHERE playlist_id=?").use { statement ->
            bind(statement, pid); statement.executeUpdate()
        }
        try {
            update("DELETE FROM programs WHERE playlist_id=?", pid)
            update("INSERT INTO programs(playlist_id,channel_key,start,stop,title,description,icon) SELECT playlist_id,channel_key,start+?,stop+?,title,description,icon FROM provider_guide_shift", deltaMs, deltaMs)
            putSetting(settingKey, minutes.toString())
        } finally { exec("DROP TABLE IF EXISTS temp.provider_guide_shift") }
    }

    // --- guide ---------------------------------------------------------------------------------------

    /**
     * The guide is read (a large XMLTV, parsed lazily by [programs]) outside the lock and written in short batches.
     * Holding the database for the whole download blocked every write of the interface behind it, and the TV killed the
     * app for not answering (three ANRs while searching and playing, 2026-11-18). The old guide is cleared only once
     * the first programme to keep has arrived, so an empty or failed download leaves it in place.
     */
    fun replacePrograms(pid: Long, programs: Sequence<Program>, keepFrom: Long): Int {
        val batch = ArrayList<Program>(GUIDE_BATCH)
        var cleared = false
        var n = 0
        fun flush() {
            if (batch.isEmpty()) return
            // The old guide goes in slices, each its own short hold of the lock: deleted at once, tens of thousands of rows
            // held the database for seconds on the Chromecast's storage, and every read waited (ANR, 2026-11-22).
            if (!cleared) {
                while (true) {
                    val gone = synchronized(this) {
                        update("DELETE FROM programs WHERE rowid IN (SELECT rowid FROM programs WHERE playlist_id=? LIMIT $GUIDE_DELETE_SLICE)", pid)
                    }
                    if (gone < GUIDE_DELETE_SLICE) break
                }
            }
            synchronized(this) {
                tx {
                    // Named columns: on an older base, icon was added last (ALTER TABLE).
                    conn.prepareStatement("INSERT OR REPLACE INTO programs(playlist_id, channel_key, start, stop, title, description, icon) VALUES(?,?,?,?,?,?,?)").use { st ->
                        batch.forEach { p -> bind(st, pid, p.channelKey, p.start, p.end, p.title, p.description, p.icon); st.addBatch() }
                        st.executeBatch()
                    }
                }
                cleared = true
            }
            n += batch.size
            batch.clear()
        }
        for (p in programs) {
            if (p.end < keepFrom) continue
            batch += p
            if (batch.size >= GUIDE_BATCH) flush()
        }
        flush()
        return n
    }

    /**
     * Writes asked by the interface thread go to one background writer, in order: waiting there for the database lock
     * (held by a sync) froze the screen. Elsewhere, and inside a transaction, they stay synchronous.
     */
    private fun write(block: () -> Unit) {
        val name = Thread.currentThread().name
        if ((name == "main" || name.startsWith("AWT-EventQueue")) && !Thread.holdsLock(this)) {
            writer.execute { runCatching { synchronized(this) { block() } }.onFailure { System.err.println("Sokoul db write failed: ${it.javaClass.simpleName}") } }
        } else {
            synchronized(this) { block() }
        }
    }

    /** Queued on the writer thread from any thread: for bookkeeping nobody waits for. */
    private fun writeLater(block: () -> Unit) {
        writer.execute { runCatching { synchronized(this) { block() } }.onFailure { System.err.println("Sokoul db write failed: ${it.javaClass.simpleName}") } }
    }

    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "sokoul-db-writer").apply { isDaemon = true } }

    fun programs(pid: Long, keys: Collection<String>, from: Long, to: Long): List<Program> {
        if (keys.isEmpty()) return emptyList()
        val marks = keys.joinToString(",") { "?" }
        return catalogue(
            "SELECT * FROM programs WHERE playlist_id=? AND channel_key IN ($marks) AND stop>? AND start<? ORDER BY start",
            pid, *keys.toTypedArray(), from, to,
        ) { programOf(it) }
    }

    /** Chaînes rapprochées du guide par leur nom : (id de chaîne, identifiant du guide). */
    @Synchronized
    fun assignEpgKeys(pid: Long, links: List<Pair<String, String>>) = tx {
        conn.prepareStatement("UPDATE channels SET epg_key=? WHERE playlist_id=? AND id=?").use { st ->
            links.forEach { (id, key) -> bind(st, key, pid, id); st.addBatch() }
            st.executeBatch()
        }
    }

    @Synchronized
    fun programCount(pid: Long): Int = query("SELECT COUNT(*) FROM programs WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    // --- favoris, progression, réglages ----------------------------------------------------------------

    fun favoriteIds(pid: Long, type: String = "LIVE"): Set<String> =
        light("SELECT id FROM favorites WHERE playlist_id=? AND type=?", pid, type) { it.getString(1) }.toSet()

    fun addFavorite(pid: Long, type: String, id: String, at: Long) =
        write { update("INSERT OR IGNORE INTO favorites VALUES(?,?,?,?)", pid, type, id, at) }

    fun removeFavorite(pid: Long, type: String, id: String) =
        write { update("DELETE FROM favorites WHERE playlist_id=? AND type=? AND id=?", pid, type, id) }

    /** Progression : la plus récente l'emporte. */
    @Synchronized
    fun mergeProgress(pid: Long, type: String, id: String, title: String, pos: Long, dur: Long, at: Long, finished: Boolean) {
        val current = query("SELECT updated_at FROM progress WHERE playlist_id=? AND type=? AND id=?", pid, type, id) { it.getLong(1) }.firstOrNull()
        if (current != null && current >= at) return
        update("INSERT OR REPLACE INTO progress VALUES(?,?,?,?,?,?,?,?)", pid, type, id, title, pos, dur, at, if (finished) 1 else 0)
    }

    @Synchronized
    fun progressCount(pid: Long): Int = query("SELECT COUNT(*) FROM progress WHERE playlist_id=?", pid) { it.getInt(1) }.first()

    /**
     * Settings kept in memory once read: the screens read them while composing, and a long query of a worker (an IPTV
     * catalog search) held the database for 100 ms, freezing the interface (trace on the TV, 2026-11-08). All writes go
     * through [putSetting], which keeps the copy right.
     */
    private val settingsCache = java.util.concurrent.ConcurrentHashMap<String, java.util.Optional<String>>()

    // A setting known to be absent is cached too: reading it again under the lock blocked the interface behind a long
    // IPTV sync, and the TV was killed for not answering (ANR, 2026-11-09).
    fun setting(key: String): String? = settingsCache[key]?.let { return it.orElse(null) } ?: run {
        val v = light("SELECT value FROM settings WHERE key=?", key) { it.getString(1) }.firstOrNull()
        settingsCache[key] = java.util.Optional.ofNullable(v)
        v
    }

    fun putSetting(key: String, value: String?) {
        // The copy first: the screen reads it back at once, the row follows on the writer.
        settingsCache[key] = java.util.Optional.ofNullable(value)
        write { if (value == null) update("DELETE FROM settings WHERE key=?", key) else update("INSERT OR REPLACE INTO settings VALUES(?,?)", key, value) }
    }

    // --- cache TMDB, reprise --------------------------------------------------------------------------

    @Synchronized
    fun tmdbGet(key: String): Pair<String, Long>? =
        query("SELECT json, fetched_at FROM tmdb_cache WHERE key=?", key) { it.getString(1) to it.getLong(2) }.firstOrNull()

    @Synchronized
    fun tmdbPut(key: String, json: String, at: Long) { update("INSERT OR REPLACE INTO tmdb_cache VALUES(?,?,?)", key, json, at) }

    /** Removes the cached answers of a kind ([prefix]) older than [before]: big answers must not pile up. */
    @Synchronized
    fun purgeCache(prefix: String, before: Long) { update("DELETE FROM tmdb_cache WHERE key LIKE ? AND fetched_at < ?", "$prefix%", before) }

    fun saveResume(r: Resume) {
        val scope = profile
        write {
            update(
                "INSERT OR REPLACE INTO resume(key, kind, tmdb_id, season, episode, title, subtitle, image, position_ms, duration_ms, updated_at, finished, profile) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                scopedFor(r.key, scope), r.kind, r.tmdbId, r.season, r.episode, r.title, r.subtitle, r.image, r.positionMs, r.durationMs, r.updatedAt, r.finished, scope,
            )
        }
    }

    /**
     * A torrent Sokoul asked Real-Debrid for. Always written by the writer thread, never waited for: called before each
     * Real-Debrid try, it waited behind the guide's sync after a start and every 4K timed out, the IPTV playing instead
     * (2026-11-24).
     */
    fun rdNoteAdded(hash: String) = writeLater {
        update("INSERT OR IGNORE INTO rd_torrents(hash, added_at) VALUES(?,?)", hash.lowercase(), System.currentTimeMillis())
    }

    /** A torrent that gave the film or episode [resumeKey] its link; written later, as [rdNoteAdded]. */
    fun rdNotePlayed(hash: String, resumeKey: String) = writeLater {
        val now = System.currentTimeMillis()
        update("INSERT INTO rd_torrents(hash, added_at, played_key, played_at) VALUES(?,?,?,?) " +
            "ON CONFLICT(hash) DO UPDATE SET played_key=excluded.played_key, played_at=excluded.played_at", hash.lowercase(), now, resumeKey, now)
    }

    data class RdKnown(val addedAt: Long, val playedKey: String?, val playedAt: Long?)

    fun rdKnown(hash: String): RdKnown? = light("SELECT added_at, played_key, played_at FROM rd_torrents WHERE hash=?", hash.lowercase()) {
        RdKnown(it.getLong(1), it.getString(2), it.getLong(3).takeIf { _ -> !it.wasNull() })
    }.firstOrNull()

    fun resume(key: String): Resume? = light("SELECT * FROM resume WHERE key=?", scoped(key)) { it.toResume() }.firstOrNull()

    /** « Reprendre » : lectures commencées et non terminées, la plus récente d'abord ; une seule ligne par série. */
    fun resumeList(limit: Int = 20): List<Resume> =
        light("SELECT * FROM resume WHERE profile=? AND finished=0 AND position_ms > 0 ORDER BY updated_at DESC LIMIT ?", profile, limit * 3) { it.toResume() }
            .distinctBy { "${it.kind}:${it.tmdbId}" }.take(limit)

    // --- enregistrements ----------------------------------------------------------------------------------

    @Synchronized
    fun recordings(pid: Long): List<Recording> = query("SELECT * FROM recordings WHERE playlist_id=? ORDER BY start_at", pid) { it.toRecording() }

    @Synchronized
    fun insertRecording(r: Recording): Long {
        update(
            "INSERT INTO recordings(playlist_id, channel_id, channel_name, channel_logo, title, start_at, end_at, status, path, bytes, error, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            r.playlistId, r.channelId, r.channelName, r.channelLogo, r.title, r.startAt, r.endAt, r.status.name, r.path, r.bytes, r.error, r.createdAt,
        )
        return query("SELECT last_insert_rowid()") { it.getLong(1) }.first()
    }

    @Synchronized
    fun updateRecording(id: Long, status: RecordingStatus, path: String? = null, bytes: Long? = null, error: String? = null) =
        update("UPDATE recordings SET status=?, path=COALESCE(?, path), bytes=COALESCE(?, bytes), error=? WHERE id=?", status.name, path, bytes, error, id)

    @Synchronized
    fun deleteRecording(id: Long) = update("DELETE FROM recordings WHERE id=?", id)

    @Synchronized
    fun updateRecordingEnd(id: Long, end: Long) = update("UPDATE recordings SET end_at=? WHERE id=?", end, id)

    private fun ResultSet.toRecording() = Recording(
        getLong("id"), getLong("playlist_id"), getString("channel_id"), getString("channel_name"), getString("channel_logo"), getString("title"),
        getLong("start_at"), getLong("end_at"), RecordingStatus.valueOf(getString("status")), getString("path"), getLong("bytes"), getString("error"), getLong("created_at"),
    )

    /** Historique : tout ce qui a été regardé (terminé compris), le plus récent d'abord. */
    fun history(limit: Int = 300): List<Resume> =
        light("SELECT * FROM resume WHERE profile=? AND (finished=1 OR position_ms > 0) ORDER BY updated_at DESC LIMIT ?", profile, limit) { it.toResume() }

    @Synchronized
    fun deleteResume(key: String) = update("DELETE FROM resume WHERE key=?", scoped(key))

    @Synchronized
    fun clearHistory() = update("DELETE FROM resume WHERE profile=?", profile)

    /** Removes everything a deleted profile had watched. */
    @Synchronized
    fun forgetProfile(id: String) { if (id.isNotEmpty()) update("DELETE FROM resume WHERE profile=?", id) }

    /** Séries commencées (identifiants TMDB), pour « À venir ». */
    fun startedSeries(): List<Int> = light("SELECT DISTINCT tmdb_id FROM resume WHERE profile=? AND kind='tv'", profile) { it.getInt(1) }

    /** Films vus jusqu'au bout (identifiants TMDB) : part déjà vue d'une saga. */
    fun finishedMovies(): Set<Int> =
        light("SELECT tmdb_id FROM resume WHERE profile=? AND kind='movie' AND finished=1", profile) { it.getInt(1) }.toHashSet()

    /** Dernière lecture d'une œuvre (épisode terminé compris) : point de départ de « Reprendre » sur une série. */
    fun allOf(kind: String, tmdbId: Int): List<Resume> =
        light("SELECT * FROM resume WHERE profile=? AND kind=? AND tmdb_id=?", profile, kind, tmdbId) { it.toResume() }

    fun lastOf(kind: String, tmdbId: Int): Resume? =
        light("SELECT * FROM resume WHERE profile=? AND kind=? AND tmdb_id=? ORDER BY updated_at DESC LIMIT 1", profile, kind, tmdbId) { it.toResume() }.firstOrNull()

    // --- account sync (com.sokoul.pc.sync) ------------------------------------------------------------

    /** One synced item as last pushed or pulled. [profile] is the profile's account UUID; [payload] null when deleted. */
    data class ShadowRow(val type: String, val profile: String, val key: String, val payload: String?, val updatedAt: Long, val deleted: Boolean, val dirty: Boolean)

    @Synchronized
    fun shadowRows(): List<ShadowRow> = query("SELECT * FROM sync_shadow") { it.toShadow() }

    @Synchronized
    fun dirtyShadowRows(limit: Int): List<ShadowRow> =
        query("SELECT * FROM sync_shadow WHERE dirty=1 ORDER BY CASE type WHEN 'profile' THEN 0 ELSE 1 END, updated_at LIMIT ?", limit) { it.toShadow() }

    @Synchronized
    fun putShadow(rows: List<ShadowRow>) = tx {
        rows.forEach { r ->
            update("INSERT OR REPLACE INTO sync_shadow VALUES(?,?,?,?,?,?,?)", r.type, r.profile, r.key, r.payload, r.updatedAt, r.deleted, r.dirty)
        }
    }

    /** Pushed: clean again, unless the item changed meanwhile (its time moved on). */
    @Synchronized
    fun markShadowClean(rows: List<ShadowRow>) = tx {
        rows.forEach { r -> update("UPDATE sync_shadow SET dirty=0 WHERE type=? AND profile=? AND key=? AND updated_at=?", r.type, r.profile, r.key, r.updatedAt) }
    }

    @Synchronized
    fun dropShadow(type: String, profile: String, key: String) = update("DELETE FROM sync_shadow WHERE type=? AND profile=? AND key=?", type, profile, key)

    /** Everything of a profile but its own row: its items went with it. */
    @Synchronized
    fun dropShadowItems(profile: String) = update("DELETE FROM sync_shadow WHERE profile=? AND type<>'profile'", profile)

    @Synchronized
    fun clearShadow() = update("DELETE FROM sync_shadow")

    /** Every resume point (history included) of the profile [scope], whatever the current profile is. */
    fun resumeRowsOf(scope: String): List<Resume> = light("SELECT * FROM resume WHERE profile=?", scope) { it.toResume() }

    fun resumeOf(scope: String, key: String): Resume? = light("SELECT * FROM resume WHERE key=?", scopedFor(key, scope)) { it.toResume() }.firstOrNull()

    /** Writes a resume point of the profile [scope], synchronously (the sync runs on Dispatchers.IO). */
    @Synchronized
    fun putResumeOf(scope: String, r: Resume) {
        update(
            "INSERT OR REPLACE INTO resume(key, kind, tmdb_id, season, episode, title, subtitle, image, position_ms, duration_ms, updated_at, finished, profile) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
            scopedFor(r.key, scope), r.kind, r.tmdbId, r.season, r.episode, r.title, r.subtitle, r.image, r.positionMs, r.durationMs, r.updatedAt, r.finished, scope,
        )
    }

    @Synchronized
    fun deleteResumeOf(scope: String, key: String) = update("DELETE FROM resume WHERE key=?", scopedFor(key, scope))

    private fun ResultSet.toShadow() = ShadowRow(
        getString("type"), getString("profile"), getString("key"), getString("payload"), getLong("updated_at"), getInt("deleted") == 1, getInt("dirty") == 1,
    )

    private fun ResultSet.toResume() = Resume(
        getString("key").substringAfter('|'), getString("kind"), getInt("tmdb_id"), (getObject("season") as? Number)?.toInt(), (getObject("episode") as? Number)?.toInt(),
        getString("title"), getString("subtitle"), getString("image"), getLong("position_ms"), getLong("duration_ms"), getLong("updated_at"), getInt("finished") == 1,
    )

    override fun close() {
        conn.close()
        if (providerReadConnectionLazy.isInitialized()) providerReadConnection.close()
        if (lightReadConnectionLazy.isInitialized()) lightReadConnectionLazy.value.close()
    }

    // --- outils -------------------------------------------------------------------------------------

    private fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = query(conn, sql, *args, map = map)

    private fun <T> query(connection: Connection, sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { st ->
            bind(st, *args)
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }

    private fun update(sql: String, vararg args: Any?): Int = conn.prepareStatement(sql).use { st -> bind(st, *args); st.executeUpdate() }

    private fun bind(st: java.sql.PreparedStatement, vararg args: Any?) {
        args.forEachIndexed { i, a ->
            when (a) {
                null -> st.setObject(i + 1, null)
                is Int -> st.setInt(i + 1, a)
                is Long -> st.setLong(i + 1, a)
                is Boolean -> st.setInt(i + 1, if (a) 1 else 0)
                else -> st.setString(i + 1, a.toString())
            }
        }
    }

    private fun ResultSet.toPlaylist() = Playlist(
        getLong("id"), getString("name"), PlaylistType.valueOf(getString("type")), getString("url"), getString("username"),
        getString("password"), getString("epg_url"), getString("user_agent"), getLong("synced_at"),
    )

    private fun ResultSet.toChannel() = Channel(
        getLong("playlist_id"), getString("id"), getInt("number"), getString("name"), getString("logo"), getString("category_id"),
        getString("epg_key"), getString("name_key"), getString("url"), getString("user_agent"), getString("referrer"), getInt("position"),
        getString("catchup_type"), getString("catchup_source"), getInt("catchup_days").coerceAtLeast(0),
    )

    companion object {
        private const val WAL_RETAINED_BYTES = 32L * 1024 * 1024
        private const val BACKGROUND_SYNC_FREE_BYTES = 256L * 1024 * 1024
        /** Programmes written per lock: a few milliseconds each, so the interface never waits long behind the guide. */
        /** Programmes written per hold of the lock: short enough that a read waits a fraction of a second, not seconds. */
        private const val GUIDE_BATCH = 500
        private const val GUIDE_DELETE_SLICE = 2000
        /** Programmes read per step when walking a guide window in start order. */
        private const val PROGRAM_SCAN_BATCH = 500
        /** Longer guide rows are provider placeholders, not programmes. */
        private const val MAX_PROGRAM_MS = 12 * 3_600_000L

        /** Même playlist : même serveur (sans « / » final) et même identifiant. */
        fun sameSource(a: Playlist, b: Playlist) =
            a.type == b.type && a.url.trimEnd('/').equals(b.url.trimEnd('/'), ignoreCase = true) && (a.username ?: "") == (b.username ?: "")

        fun open(): DesktopDb = DesktopDb(File(AppDirs.data, "sokoul.db"))
    }
}
