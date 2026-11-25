package com.sokoul.sources

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS over HTTPS for the catalogue sites only (Anime-Sama, Coflix, French-Stream): some providers answer a blocking
 * address for them. Resolvers are reached by IP; private, loopback or link-local answers are refused; every other host
 * keeps the system resolver.
 */
internal class CatalogueDns(http: OkHttpClient, private val now: () -> Long = System::currentTimeMillis) : Dns {
    private val client = http.newBuilder().dns(Dns.SYSTEM).callTimeout(6, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    override fun lookup(hostname: String): List<InetAddress> {
        if (!CATALOGUE.matches(hostname)) return Dns.SYSTEM.lookup(hostname)
        cache[hostname]?.takeIf { it.first > now() }?.let { return it.second }
        for (resolver in RESOLVERS) {
            val answer = runCatching { ask(resolver, hostname) }.getOrNull() ?: continue
            cache[hostname] = (now() + answer.second * 1000L) to answer.first
            return answer.first
        }
        throw java.net.UnknownHostException("anime_sama_dns_unavailable")
    }

    private fun ask(resolver: String, hostname: String): Pair<List<InetAddress>, Long>? {
        val url = "$resolver?name=$hostname&type=A".toHttpUrlOrNull() ?: return null
        client.newCall(Request.Builder().url(url).header("Accept", "application/dns-json").build()).execute().use { response ->
            if (!response.isSuccessful) return null
            return parse(response.body?.string() ?: return null)
        }
    }

    companion object {
        /** The catalogue hosts some providers block, as the server lists them (`API/Mainapi/utils/animeSamaDns.js`). */
        val CATALOGUE = Regex("(?:[a-z0-9-]+\\.)*(?:anime-sama|coflix|french-?stream)\\.[a-z]{2,10}", RegexOption.IGNORE_CASE)
        private val RESOLVERS = listOf("https://1.1.1.1/dns-query", "https://8.8.8.8/resolve")

        /** Public A records of a DNS JSON answer and their shortest TTL (at least 60 s), or null when there is none. */
        fun parse(body: String): Pair<List<InetAddress>, Long>? {
            val o = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
            if ((o["Status"] as? JsonPrimitive)?.contentOrNull != "0") return null
            val records = (o["Answer"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
                .filter { (it["type"] as? JsonPrimitive)?.contentOrNull == "1" }
            val addresses = records.mapNotNull { r ->
                (r["data"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) }
                    ?.let { runCatching { InetAddress.getByName(it) }.getOrNull() }
            }.filter(com.sokoul.extract.PublicNetwork::isPublic)
            if (addresses.isEmpty()) return null
            val ttl = records.mapNotNull { (it["TTL"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }.minOrNull() ?: 60L
            return addresses to ttl.coerceAtLeast(60L)
        }
    }
}
