package com.sokoul.server

import com.sokoul.credentials.CheckResult
import com.sokoul.credentials.Verdict
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import javax.crypto.AEADBadTagException

/** Lot 5 (tasks 6.1–6.2): sealed values, owner-only key file, masks only, one user's keys never another's. */
class VaultTest {
    @get:Rule val tmp = TemporaryFolder()

    private val tmdbKey = "0123456789abcdef0123456789abcdef"
    private val accept = CredentialChecker { _, _ -> CheckResult(Verdict.Accepted, 200) }

    private fun db(): Database = Database.open(tmp.newFolder()).also { db ->
        listOf("u1", "u2").forEach { db.update("INSERT INTO users (id, created_at) VALUES (?, ?)", it, 1L) }
    }

    @Test fun `migration 4 adds the vault tables`() {
        val db = db()
        assertEquals(4, db.schemaVersion())
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getString(1) }
        assertTrue(tables.containsAll(listOf("credentials", "vault_keys")))
    }

    @Test fun `a value comes back from the seal and the wrong associated data or key fails`() {
        val key = Secrets.bytes(32)
        val blob = Aead.seal(key, "secret".toByteArray(), "u1|tmdb".toByteArray())
        assertEquals("secret", Aead.open(key, blob, "u1|tmdb".toByteArray()).toString(Charsets.UTF_8))
        assertEquals(1 + Aead.NONCE_BYTES + 6 + 16, blob.size)
        // A fresh nonce each time: the same value never gives the same blob.
        assertFalse(blob.contentEquals(Aead.seal(key, "secret".toByteArray(), "u1|tmdb".toByteArray())))
        for (aad in listOf("u2|tmdb", "u1|omdb")) {
            try {
                Aead.open(key, blob, aad.toByteArray())
                fail("opened with $aad")
            } catch (_: AEADBadTagException) {
            }
        }
        try {
            Aead.open(Secrets.bytes(32), blob, "u1|tmdb".toByteArray())
            fail("opened with another key")
        } catch (_: AEADBadTagException) {
        }
    }

    @Test fun `the key file is created once, owner only, and reused`() {
        val keyFile = File(tmp.newFolder(), "keys/vault.key")
        val db = db()
        val first = Vault.open(db, keyFile, accept)
        val bytes = keyFile.readBytes()
        assertEquals(32, bytes.size)
        runBlocking { first.put("u1", "tmdb", tmdbKey) }
        val again = Vault.open(db, keyFile, accept)
        assertArrayEquals(bytes, keyFile.readBytes())
        assertEquals(tmdbKey, again.reveal("u1", "tmdb"))
        assertEquals(listOf("vault.key"), keyFile.parentFile.list()!!.toList())

        val path = keyFile.toPath()
        Files.getFileAttributeView(path, PosixFileAttributeView::class.java)?.let { view ->
            assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), view.readAttributes().permissions())
        }
        Files.getFileAttributeView(path, AclFileAttributeView::class.java)?.let { view ->
            if (Files.getFileAttributeView(path, PosixFileAttributeView::class.java) == null) {
                assertEquals(1, view.acl.size)
                assertEquals(view.owner, view.acl.single().principal())
            }
        }
    }

    @Test fun `a missing or different key file stops the vault instead of making new keys`() {
        val keyFile = File(tmp.newFolder(), "vault.key")
        val db = db()
        Vault.open(db, keyFile, accept)
        val other = File(tmp.newFolder(), "vault.key")
        try {
            Vault.open(db, other, accept)
            fail("opened without the key file")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("missing"))
        }
        assertFalse(other.exists())
        VaultKeyFile.loadOrCreate(other)
        try {
            Vault.open(db, other, accept)
            fail("opened with another key file")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("does not open"))
        }
    }

    @Test fun `the hint never shows the value and the database never holds it in clear`() {
        val db = db()
        val vault = Vault.open(db, File(tmp.newFolder(), "vault.key"), accept)
        val xtream = """{"server":"http://iptv.example:8080","username":"lou","password":"hunter2-and-more"}"""
        val stored = runBlocking { vault.put("u1", "tmdb", tmdbKey) } as Vault.PutResult.Stored
        runBlocking { vault.put("u1", "iptv", xtream) }
        assertEquals("•••• cdef", stored.info.hint)
        assertEquals("accepted", stored.info.verdict)
        assertEquals("•••• more", vault.list("u1").first { it.service == "iptv" }.hint)
        assertEquals("••••", Vault.hint("tmdb", "abc123"))
        assertEquals("•••• 78", Vault.hint("tmdb", "12345678"))
        assertTrue(vault.list("u1").none { tmdbKey in it.hint || it.hint.length >= tmdbKey.length })
        assertTrue(vault.reveal("u1", "iptv")!!.contains("hunter2-and-more"))

        // Every byte of the file (and of the WAL, where recent pages live) is searched for the clear values.
        val files = db.file.parentFile.listFiles()!!.filter { it.name.startsWith(Database.FILE_NAME) }
        val haystack = files.joinToString("") { it.readBytes().toString(Charsets.ISO_8859_1) }
        for (needle in listOf(tmdbKey, "hunter2-and-more", tmdbKey.substring(0, 16))) {
            assertFalse("found $needle in clear", haystack.contains(needle))
            assertFalse(haystack.contains(String(needle.toByteArray(Charsets.UTF_16LE), Charsets.ISO_8859_1)))
        }
    }

    @Test fun `a refused or unreachable value is not stored and the reason is plain`() {
        val db = db()
        var verdict = Verdict.Refused
        val vault = Vault.open(db, File(tmp.newFolder(), "vault.key"), CredentialChecker { _, _ -> CheckResult(verdict, if (verdict == Verdict.Refused) 401 else null) })
        val refused = runBlocking { vault.put("u1", "tmdb", tmdbKey) } as Vault.PutResult.Rejected
        assertEquals("refused", refused.code)
        assertEquals("tmdb refused it (HTTP 401).", refused.reason)
        assertFalse(refused.reason.contains(tmdbKey))
        verdict = Verdict.Unreachable
        assertEquals("unreachable", (runBlocking { vault.put("u1", "tmdb", tmdbKey) } as Vault.PutResult.Rejected).code)
        assertEquals(0, db.query("SELECT COUNT(*) FROM credentials") { it.getInt(1) }.single())
        // A refused new value leaves the accepted old one in place.
        verdict = Verdict.Accepted
        runBlocking { vault.put("u1", "tmdb", tmdbKey) }
        verdict = Verdict.Refused
        runBlocking { vault.put("u1", "tmdb", "ffffffffffffffffffffffffffffffff") }
        assertEquals(tmdbKey, vault.reveal("u1", "tmdb"))
    }

    @Test fun `values that cannot be one are refused before any check`() {
        var asked = 0
        val vault = Vault.open(db(), File(tmp.newFolder(), "vault.key"), CredentialChecker { _, _ -> asked++; CheckResult(Verdict.Accepted) })
        val bad = listOf(
            "tmdb" to "", "tmdb" to "has space", "tmdb" to "a".repeat(Vault.MAX_KEY_LENGTH + 1), "tmdb" to "line\nbreak",
            "iptv" to "ftp://x", "iptv" to """{"server":"http://x","username":"u"}""", "iptv" to "http://" + "a".repeat(Vault.MAX_IPTV_LENGTH),
            "unknown" to tmdbKey, "tvmaze" to tmdbKey,
        )
        for ((service, value) in bad) {
            assertEquals("$service:$value", "bad_format", (runBlocking { vault.put("u1", service, value) } as Vault.PutResult.Rejected).code)
        }
        assertEquals(0, asked)
    }

    @Test fun `users are kept apart`() {
        val vault = Vault.open(db(), File(tmp.newFolder(), "vault.key"), accept)
        runBlocking { vault.put("u1", "tmdb", tmdbKey) }
        assertNull(vault.reveal("u2", "tmdb"))
        assertTrue(vault.list("u2").isEmpty())
        assertFalse(vault.delete("u2", "tmdb"))
        assertEquals(tmdbKey, vault.reveal("u1", "tmdb"))
    }

    // ---- Routes

    private suspend fun io.ktor.client.HttpClient.putCredential(service: String, value: String, token: String?): HttpResponse =
        put("/api/credentials/$service") {
            contentType(ContentType.Application.Json)
            setBody(Json.encodeToString(CredentialPutRequest.serializer(), CredentialPutRequest(value)))
            token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }

    @Test fun `the routes store, list and delete the caller's own keys and never return a value`() = testApplication {
        val server = TestServer(tmp)
        server.install(this)
        val owner = client.setupOwner()
        val invite = client.postJson("/api/invites", token = owner).json().str("code")
        val member = client.postJson("/api/auth/join", """{"code":"$invite","name":"Lou","passphrase":"another long passphrase"}""").json().str("token")

        assertEquals(HttpStatusCode.Unauthorized, client.putCredential("tmdb", tmdbKey, null).status)
        assertEquals(HttpStatusCode.Unauthorized, client.getWith("/api/credentials", null).status)

        val put = client.putCredential("tmdb", tmdbKey, owner)
        assertEquals(HttpStatusCode.OK, put.status)
        val putText = put.bodyAsText()
        assertFalse(putText.contains(tmdbKey))
        assertEquals("•••• cdef", put.json().str("hint"))

        val listed = client.getWith("/api/credentials", owner)
        val text = listed.bodyAsText()
        assertFalse(text.contains(tmdbKey))
        val items = Json.parseToJsonElement(text).jsonArray
        assertEquals(1, items.size)
        val item = items.single().jsonObject
        assertEquals(setOf("service", "hint", "verdict", "checkedAt"), item.keys)
        assertEquals("accepted", item.getValue("verdict").jsonPrimitive.content)

        // The member sees nothing of the owner's, and cannot delete it.
        assertEquals("[]", client.getWith("/api/credentials", member).bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.delete("/api/credentials/tmdb") { header(HttpHeaders.Authorization, "Bearer $member") }.status)
        assertEquals(tmdbKey, server.services.vault.reveal(ownerId(server), "tmdb"))

        assertEquals(HttpStatusCode.NotFound, client.putCredential("nope", tmdbKey, owner).status)
        val bad = client.putCredential("tmdb", "not a key", owner)
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("bad_format", bad.json().str("error"))
        val huge = client.putCredential("tmdb", "a".repeat(9_000), owner)
        assertEquals(HttpStatusCode.PayloadTooLarge, huge.status)

        assertEquals(HttpStatusCode.NoContent, client.delete("/api/credentials/tmdb") { header(HttpHeaders.Authorization, "Bearer $owner") }.status)
        assertEquals("[]", client.getWith("/api/credentials", owner).bodyAsText())
    }

    @Test fun `a refused key answers refused with the reason and stores nothing`() = testApplication {
        val server = TestServer(tmp, checker = CredentialChecker { _, _ -> CheckResult(Verdict.Refused, 401) })
        server.install(this)
        val owner = client.setupOwner()
        val r = client.putCredential("realdebrid", tmdbKey, owner)
        assertEquals(422, r.status.value)
        val body = r.json()
        assertEquals("refused", body.str("error"))
        assertNotEquals("", body.str("reason"))
        assertFalse(r.bodyAsText().contains(tmdbKey))
        assertEquals("[]", client.getWith("/api/credentials", owner).bodyAsText())
    }

    @Test fun `an unreachable service answers unreachable`() = testApplication {
        val server = TestServer(tmp, checker = CredentialChecker { _, _ -> CheckResult(Verdict.Unreachable, failure = "SocketTimeoutException") })
        server.install(this)
        val owner = client.setupOwner()
        val r = client.putCredential("omdb", "abcd1234", owner)
        assertEquals(502, r.status.value)
        assertEquals("unreachable", r.json().str("error"))
    }

    private fun ownerId(server: TestServer): String =
        server.db.query("SELECT id FROM users WHERE role = 'owner'") { it.getString(1) }.single()
}
