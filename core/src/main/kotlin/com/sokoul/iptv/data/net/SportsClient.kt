package com.sokoul.iptv.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

@Serializable data class SportsEvent(val id: String, val name: String, val sport: String, val live: Boolean, val startsAt: Long?, val poster: String? = null)
@Serializable data class SportsServer(val key: String, val title: String)
enum class SportsFailure { NotConfigured, VipRequired, Unavailable, InvalidResponse }
data class SportsResult<T>(val value: T? = null, val failure: SportsFailure? = null)

object SportsParse {
    fun text(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    fun events(root: JsonObject): List<SportsEvent> = (root["metas"] as? JsonArray).orEmpty().mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val id = text(item,"id")?.takeIf { it.matches(Regex("streamed_[a-zA-Z0-9_-]{1,250}")) } ?: return@mapNotNull null
        val title = text(item,"name")?.takeIf(String::isNotBlank)?.take(200) ?: return@mapNotNull null
        SportsEvent(id,title,text(item,"_sport").orEmpty().take(60), (item["_isLive"] as? JsonPrimitive)?.booleanOrNull == true,
            (item["_timestamp"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 },
            text(item, "poster")?.takeIf { it.startsWith("https://") && it.length <= 2048 })
    }.distinctBy { it.id }.take(500)

    fun servers(root: JsonObject): List<SportsServer> = (root["streams"] as? JsonArray).orEmpty().mapNotNull { entry ->
        val item = entry as? JsonObject ?: return@mapNotNull null
        val key = text(item,"_streamedKey")?.takeIf { it.matches(Regex("[a-f0-9]{24}")) } ?: return@mapNotNull null
        SportsServer(key,text(item,"title")?.take(200) ?: "Streamed")
    }.distinctBy { it.key }.take(64)

    /** Only the configured server's signed media relay is accepted, never an embed URL. */
    fun media(raw: String?, base: String, now: Long = System.currentTimeMillis()): String? = runCatching {
        val uri = URI(raw ?: return null)
        val server = URI(base)
        val values = uri.rawQuery.orEmpty().split('&').mapNotNull { piece ->
            val p = piece.split('=',limit=2)
            if (p.size == 2) p[0] to URLDecoder.decode(p[1],"UTF-8") else null
        }.toMap()
        val port = if (uri.port < 0) if (uri.scheme == "https") 443 else 80 else uri.port
        val basePort = if (server.port < 0) if (server.scheme == "https") 443 else 80 else server.port
        raw.takeIf {
            uri.scheme in setOf("http","https") && uri.scheme == server.scheme && uri.host.equals(server.host,true) &&
                uri.userInfo == null && uri.fragment == null && uri.path == "/streamed-proxy" && port in setOf(basePort,25569) &&
                values["sig"]?.matches(Regex("[A-Za-z0-9_-]{43}")) == true && (values["exp"]?.toLongOrNull() ?: 0) > now / 1000 &&
                values["url"]?.startsWith("https://") == true
        }
    }.getOrNull()
}

/** Reads public event metadata and resolves only the server explicitly selected by the viewer. */
class SportsClient(http: OkHttpClient, private val baseUrl: () -> String?, private val accessKey: () -> String?) {
    private val client = http.newBuilder().callTimeout(30,TimeUnit.SECONDS).followRedirects(false).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun events() = fetch("/api/livetv/catalog/tv/streamed_all",false) { SportsParse.events(it) }

    suspend fun servers(eventId: String): SportsResult<List<SportsServer>> {
        if (!eventId.matches(Regex("streamed_[a-zA-Z0-9_-]{1,250}"))) return SportsResult(failure=SportsFailure.InvalidResponse)
        return fetch("/api/livetv/stream/tv/${eventId}",false) { SportsParse.servers(it) }
    }

    suspend fun resolve(eventId: String, serverKey: String): SportsResult<String> {
        if (!eventId.matches(Regex("streamed_[a-zA-Z0-9_-]{1,250}")) || !serverKey.matches(Regex("[a-f0-9]{24}"))) return SportsResult(failure=SportsFailure.InvalidResponse)
        return fetch("/api/livetv/streamed/native/${eventId}/${serverKey}",true) { root ->
            val url = SportsParse.media(SportsParse.text(root,"url"),baseUrl().orEmpty()) ?: throw IllegalArgumentException()
            url
        }
    }

    private suspend fun <T> fetch(path: String, vip: Boolean, parse: (JsonObject) -> T): SportsResult<T> {
        val base = baseUrl()?.trim()?.trimEnd('/')?.takeIf { runCatching { URI(it).let { u -> u.scheme in setOf("http","https") && u.host != null && u.userInfo == null && u.query == null && u.fragment == null } }.getOrDefault(false) }
            ?: return SportsResult(failure=SportsFailure.NotConfigured)
        val key = accessKey()?.takeIf(String::isNotBlank)
        if (vip && key == null) return SportsResult(failure=SportsFailure.VipRequired)
        return withTimeoutOrNull(45_000) { withContext(Dispatchers.IO) {
            try {
                repeat(4) { attempt ->
                    val request = Request.Builder().url(base+path).header("Accept","application/json").apply { if (key != null) header("x-access-key",key) }.build()
                    client.newCall(request).await().use { response ->
                        if (response.code == 202) {
                            if (attempt == 3) return@withContext SportsResult<T>(failure=SportsFailure.Unavailable)
                        } else {
                            if (response.code == 403) return@withContext SportsResult<T>(failure=SportsFailure.VipRequired)
                            if (!response.isSuccessful) return@withContext SportsResult<T>(failure=SportsFailure.Unavailable)
                            val body = response.body ?: return@withContext SportsResult<T>(failure=SportsFailure.InvalidResponse)
                            val source = body.source()
                            source.request(2_097_153)
                            if (source.buffer.size > 2_097_152) return@withContext SportsResult<T>(failure=SportsFailure.InvalidResponse)
                            val root = json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: return@withContext SportsResult<T>(failure=SportsFailure.InvalidResponse)
                            return@withContext SportsResult(value=parse(root))
                        }
                    }
                    delay(750)
                }
                SportsResult<T>(failure=SportsFailure.Unavailable)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IllegalArgumentException) { SportsResult<T>(failure=SportsFailure.InvalidResponse) }
            catch (_: Exception) { SportsResult<T>(failure=SportsFailure.Unavailable) }
        } } ?: SportsResult(failure=SportsFailure.Unavailable)
    }
}
