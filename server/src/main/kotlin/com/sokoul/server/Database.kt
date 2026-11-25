package com.sokoul.server

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * The server's one SQLite file, `sokoul-server.db` in the data folder (design D3).
 *
 * Every connection has WAL, foreign keys and a busy timeout. Statements go through [update] and [query], which only
 * take parameters as bound values: SQL text is never built from input.
 */
class Database private constructor(val file: File) {

    private val url = "jdbc:sqlite:${file.absolutePath}"
    private val sqliteConfig = SQLiteConfig().apply {
        setJournalMode(SQLiteConfig.JournalMode.WAL)
        enforceForeignKeys(true)
        setBusyTimeout(BUSY_TIMEOUT_MS)
        setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
    }

    /** Opens a connection for [block] and closes it afterwards. */
    fun <T> connection(block: (Connection) -> T): T =
        sqliteConfig.createConnection(url).use(block)

    /** Runs [block] in one transaction: committed when it returns, rolled back when it throws. */
    fun <T> transaction(block: (Connection) -> T): T = connection { c ->
        c.autoCommit = false
        try {
            block(c).also { c.commit() }
        } catch (t: Throwable) {
            c.rollback()
            throw t
        }
    }

    fun update(sql: String, vararg args: Any?): Int = connection { it.update(sql, *args) }

    fun <T> query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
        connection { it.query(sql, *args, row = row) }

    /** Current schema version, 0 for a new file. */
    fun schemaVersion(): Int = connection { c -> Migrations.currentVersion(c) }

    companion object {
        const val FILE_NAME = "sokoul-server.db"
        const val BUSY_TIMEOUT_MS = 5_000

        /** Opens (and creates) the database in [dataDir] and applies pending migrations. */
        fun open(dataDir: File, migrations: List<Migration> = Migrations.ALL): Database {
            dataDir.mkdirs()
            require(dataDir.isDirectory) { "dataDir is not a folder" }
            val db = Database(File(dataDir, FILE_NAME))
            Migrations.apply(db, migrations)
            return db
        }
    }
}

fun Connection.update(sql: String, vararg args: Any?): Int =
    prepareStatement(sql).use { st -> st.bind(args); st.executeUpdate() }

fun <T> Connection.query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
    prepareStatement(sql).use { st ->
        st.bind(args)
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
    }

private fun PreparedStatement.bind(args: Array<out Any?>) {
    args.forEachIndexed { i, v ->
        val index = i + 1
        when (v) {
            null -> setObject(index, null)
            is String -> setString(index, v)
            is Int -> setInt(index, v)
            is Long -> setLong(index, v)
            is Boolean -> setInt(index, if (v) 1 else 0)
            is Double -> setDouble(index, v)
            is ByteArray -> setBytes(index, v)
            else -> throw IllegalArgumentException("Unsupported SQL parameter type ${v::class.simpleName}")
        }
    }
}

/** One schema step. [statements] run in order inside the migration's transaction. */
class Migration(val version: Int, val description: String, val statements: List<String>)

object Migrations {
    /** Ordered list; never edit a shipped migration, append a new one. */
    val ALL: List<Migration> = listOf(
        Migration(
            1, "users and sessions",
            listOf(
                """
                CREATE TABLE users (
                    id TEXT PRIMARY KEY,
                    created_at INTEGER NOT NULL
                )
                """,
                """
                CREATE TABLE sessions (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    token_hash BLOB NOT NULL UNIQUE,
                    device_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    last_used_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL,
                    revoked_at INTEGER
                )
                """,
                "CREATE INDEX sessions_user ON sessions(user_id)",
            ),
        ),
    )

    fun currentVersion(c: Connection): Int {
        c.update(
            "CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, description TEXT NOT NULL, " +
                "applied_at INTEGER NOT NULL)",
        )
        return c.query("SELECT COALESCE(MAX(version), 0) FROM schema_version") { it.getInt(1) }.single()
    }

    /** Applies every migration newer than the file's version, each in its own transaction. Returns how many ran. */
    fun apply(db: Database, migrations: List<Migration>): Int {
        require(migrations.zipWithNext().all { (a, b) -> b.version == a.version + 1 } &&
            (migrations.isEmpty() || migrations.first().version == 1)) { "Migrations must be numbered 1, 2, 3…" }
        val known = migrations.lastOrNull()?.version ?: 0
        var applied = 0
        db.transaction { c ->
            val current = currentVersion(c)
            check(current <= known) { "Database schema $current is newer than this server ($known)" }
        }
        for (m in migrations) {
            db.transaction { c ->
                // Re-read inside the transaction: another process may have migrated meanwhile.
                if (currentVersion(c) >= m.version) return@transaction
                m.statements.forEach { c.update(it.trimIndent()) }
                c.update(
                    "INSERT INTO schema_version (version, description, applied_at) VALUES (?, ?, ?)",
                    m.version, m.description, System.currentTimeMillis(),
                )
                applied++
            }
        }
        return applied
    }
}
