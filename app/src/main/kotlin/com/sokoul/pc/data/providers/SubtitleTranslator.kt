package com.sokoul.pc.data.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Turns lines of text into another language. An interface so the player can be tested without the network. */
fun interface Translator {
    /** Source text to its translation; a text that failed is left out, and the caller keeps the original. */
    suspend fun translate(texts: List<String>, target: String): Map<String, String>
}

/**
 * Subtitle translation through Google's public "gtx" endpoint, no key. The texts of a batch are
 * joined by a separator and split again; when the count does not match, each text goes alone.
 */
class GoogleTranslator(private val http: OkHttpClient, private val json: Json = Json) : Translator {

    override suspend fun translate(texts: List<String>, target: String): Map<String, String> {
        val unique = texts.map(::plain).filter { it.isNotBlank() }.distinct()
        if (unique.isEmpty()) return emptyMap()
        val out = HashMap<String, String>()
        // Batches of at most 4000 bytes, three requests at a time.
        for (wave in batches(unique).chunked(CONCURRENT)) {
            coroutineScope {
                wave.map { batch -> async { runCatching { translateBatch(batch, target) }.getOrDefault(emptyMap()) } }.awaitAll()
            }.forEach(out::putAll)
        }
        return texts.mapNotNull { t -> out[plain(t)]?.let { t to it } }.toMap()
    }

    private suspend fun translateBatch(batch: List<String>, target: String): Map<String, String> {
        if (batch.size > 1) {
            val parts = runCatching { request(batch.joinToString(SEPARATOR), target)?.split(SPLIT) }.getOrNull()
            if (parts != null && parts.size == batch.size) return batch.zip(parts.map { it.trim() }).filter { it.second.isNotEmpty() }.toMap()
        }
        return coroutineScope {
            batch.map { t -> async { t to runCatching { request(t, target)?.trim() }.getOrNull() } }.awaitAll()
        }.mapNotNull { (t, r) -> r?.takeIf { it.isNotEmpty() }?.let { t to it } }.toMap()
    }

    private suspend fun request(text: String, target: String): String? = withContext(Dispatchers.IO) {
        val url = ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client", "gtx").addQueryParameter("sl", "auto")
            .addQueryParameter("tl", target).addQueryParameter("dt", "t").build()
        val req = Request.Builder().url(url).post(FormBody.Builder().add("q", text).build()).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            val root = json.parseToJsonElement(res.body?.string().orEmpty()) as? JsonArray ?: return@use null
            (root.firstOrNull() as? JsonArray)?.joinToString("") { seg -> ((seg as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull.orEmpty() }
        }
    }

    private fun batches(texts: List<String>): List<List<String>> {
        val out = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var bytes = 0
        for (t in texts) {
            val size = t.toByteArray().size + SEPARATOR.length
            if (cur.isNotEmpty() && bytes + size > MAX_BYTES) {
                out += cur
                cur = ArrayList()
                bytes = 0
            }
            cur += t
            bytes += size
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    companion object {
        private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private const val SEPARATOR = "\n\n||||\n\n"
        private val SPLIT = Regex("""\s*\|{4}\s*""")
        private const val MAX_BYTES = 4000
        private const val CONCURRENT = 3
        private val TAGS = Regex("<[^>]*>")

        fun plain(text: String): String = text.replace(TAGS, "").trim()

        /** Languages offered, French and English first. */
        val TARGETS = listOf("fr", "en", "es", "de", "it", "pt", "nl", "ar", "tr", "pl", "ru", "ja", "ko", "zh")
    }
}
