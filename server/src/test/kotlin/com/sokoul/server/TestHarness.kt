package com.sokoul.server

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.rules.TemporaryFolder
import java.util.Properties

/** One server on a fresh database with a hand-driven clock and cheap hashing (the iteration count is per user). */
class TestServer(
    tmp: TemporaryFolder,
    val config: ServerConfig = ServerConfig.from(Properties()),
    /** Accepts everything by default: no test reaches a real service. */
    checker: CredentialChecker = CredentialChecker { _, _ -> com.sokoul.credentials.CheckResult(com.sokoul.credentials.Verdict.Accepted, 200) },
) {
    var now: Long = 1_800_000_000_000L
    val db: Database = Database.open(tmp.newFolder())
    val services = Services(db, { now }, PassphraseHasher(1_000), vaultKeyFile = java.io.File(tmp.newFolder(), "vault.key"), credentialChecker = checker)

    fun install(builder: ApplicationTestBuilder, access: AccessGate = AccessGate(config)) {
        builder.application { module(config, services, access) }
    }

    fun advance(millis: Long) {
        now += millis
    }

    companion object {
        const val OWNER = "Ana"
        const val OWNER_PASS = "correct horse battery"
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

suspend fun HttpClient.postJson(
    path: String,
    body: String = "{}",
    token: String? = null,
    extra: HttpRequestBuilder.() -> Unit = {},
): HttpResponse = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    extra()
}

suspend fun HttpClient.getWith(path: String, token: String?): HttpResponse = get(path) {
    token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
}

suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

fun JsonObject.str(name: String): String = getValue(name).jsonPrimitive.content

/** Creates the owner through `/api/setup` and returns its session token. */
suspend fun HttpClient.setupOwner(
    name: String = TestServer.OWNER,
    passphrase: String = TestServer.OWNER_PASS,
    device: String = "PC",
): String {
    val r = postJson("/api/setup", """{"name":"$name","passphrase":"$passphrase","deviceName":"$device"}""")
    check(r.status.value == 201) { "setup answered ${r.status}" }
    return r.json().str("token")
}

suspend fun HttpClient.login(name: String, passphrase: String, device: String = "Phone", extra: HttpRequestBuilder.() -> Unit = {}) =
    postJson("/api/auth/login", """{"name":"$name","passphrase":"$passphrase","deviceName":"$device"}""", extra = extra)
