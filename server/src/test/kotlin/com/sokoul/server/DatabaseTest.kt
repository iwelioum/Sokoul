package com.sokoul.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.SQLException

class DatabaseTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `migrations create the schema once and are idempotent`() {
        val dir = tmp.newFolder("data")
        val db = Database.open(dir)
        assertEquals(Migrations.ALL.size, db.schemaVersion())
        assertTrue(db.file.isFile)
        assertEquals("sokoul-server.db", db.file.name)

        // Second start: nothing to apply, same version, one row per migration.
        assertEquals(0, Migrations.apply(db, Migrations.ALL))
        val again = Database.open(dir)
        assertEquals(Migrations.ALL.size, again.schemaVersion())
        assertEquals(listOf(Migrations.ALL.size), again.query("SELECT COUNT(*) FROM schema_version") { it.getInt(1) })
        val tables = again.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name") { it.getString(1) }
        assertTrue(tables.containsAll(listOf("schema_version", "sessions", "users")))
    }

    @Test fun `WAL, foreign keys and busy timeout are on`() {
        val db = Database.open(tmp.newFolder("data"))
        db.connection { c ->
            assertEquals("wal", c.query("PRAGMA journal_mode") { it.getString(1) }.single().lowercase())
            assertEquals(1, c.query("PRAGMA foreign_keys") { it.getInt(1) }.single())
            assertEquals(Database.BUSY_TIMEOUT_MS, c.query("PRAGMA busy_timeout") { it.getInt(1) }.single())
        }
        try {
            db.update(
                "INSERT INTO sessions (id, user_id, token_hash, device_name, created_at, last_used_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "s1", "nobody", ByteArray(32), "TV", 1L, 1L, 2L,
            )
            throw AssertionError("A session for an unknown user must be refused")
        } catch (_: SQLException) {
        }
    }

    @Test fun `parameters are bound, not spliced`() {
        val db = Database.open(tmp.newFolder("data"))
        val hostile = "x'); DROP TABLE users; --"
        db.update("INSERT INTO users (id, created_at) VALUES (?, ?)", hostile, 1L)
        assertEquals(listOf(hostile), db.query("SELECT id FROM users WHERE id = ?", hostile) { it.getString(1) })
    }

    @Test fun `a later migration applies on top and a failing one rolls back`() {
        val dir = tmp.newFolder("data")
        val db = Database.open(dir)
        val next = Migrations.ALL + Migration(2, "extra", listOf("CREATE TABLE extra (id INTEGER PRIMARY KEY)"))
        assertEquals(1, Migrations.apply(db, next))
        assertEquals(2, db.schemaVersion())

        val broken = next + Migration(3, "broken", listOf("CREATE TABLE t3 (id INTEGER)", "NOT SQL"))
        try {
            Migrations.apply(db, broken)
            throw AssertionError("Broken migration must throw")
        } catch (_: SQLException) {
        }
        assertEquals(2, db.schemaVersion())
        assertEquals(emptyList<String>(), db.query("SELECT name FROM sqlite_master WHERE name = 't3'") { it.getString(1) })
    }

    @Test(expected = IllegalStateException::class)
    fun `a database newer than the server is refused`() {
        val dir = tmp.newFolder("data")
        val db = Database.open(dir)
        Migrations.apply(db, Migrations.ALL + Migration(2, "future", listOf("CREATE TABLE future (id INTEGER)")))
        Database.open(dir) // knows only version 1
    }
}
