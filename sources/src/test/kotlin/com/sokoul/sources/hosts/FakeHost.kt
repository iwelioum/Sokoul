package com.sokoul.sources.hosts

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

/**
 * A host played by a [MockWebServer]: answers by exact path (with query) first, then by path alone; 404 otherwise.
 * Extractors take the server's own addresses, so no DNS or TLS is involved.
 */
internal class FakeHost : AutoCloseable {
    private val routes = HashMap<String, () -> MockResponse>()
    val requests: MutableList<RecordedRequest> = Collections.synchronizedList(mutableListOf())
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val route = request.path?.let { routes[it] }
                    ?: request.requestUrl?.encodedPath?.let { routes[it] }
                    ?: return MockResponse().setResponseCode(404)
                return route()
            }
        }
        start()
    }

    /** The extractors' fetcher: short timeout, and the test server's local address allowed. */
    val fetcher = PageFetcher(OkHttpClient(), timeout = 1.seconds, allowTarget = { true })

    fun url(path: String): HttpUrl = server.url(path)

    fun page(path: String, body: String, code: Int = 200) {
        routes[path] = { MockResponse().setResponseCode(code).setBody(body) }
    }

    fun status(path: String, code: Int) {
        routes[path] = { MockResponse().setResponseCode(code) }
    }

    fun redirect(path: String, location: String, code: Int = 302) {
        routes[path] = { MockResponse().setResponseCode(code).setHeader("Location", location) }
    }

    /** [path] never answers. */
    fun silent(path: String) {
        routes[path] = { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
    }

    fun answer(path: String, response: () -> MockResponse) {
        routes[path] = response
    }

    fun requestTo(path: String): RecordedRequest = synchronized(requests) { requests.first { it.requestUrl?.encodedPath == path } }

    override fun close() = server.shutdown()
}

internal fun assertFailed(expected: HostFailure, result: HostResult) = assertEquals(HostResult.Failed(expected), result)

internal fun HostResult.asMedia(): HostResult.Media =
    this as? HostResult.Media ?: throw AssertionError("expected media, got $this")

/** The media address the synthetic pages hide. */
internal const val MASTER = "https://cdn.fixture.test/master.m3u8"

/** Packs [script] the way the classic JavaScript packer does, every word replaced by its base-[radix] index. */
internal fun pack(script: String, radix: Int = 36): String {
    val words = Regex("""\b\w+\b""").findAll(script).map { it.value }.distinct().toList()
    fun encode(n: Int): String {
        val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        return if (n < radix) digits[n].toString() else encode(n / radix) + digits[n % radix]
    }
    val template = Regex("""\b\w+\b""").replace(script) { encode(words.indexOf(it.value)) }
    val escaped = template.replace("\\", "\\\\").replace("'", "\\'")
    return "eval(function(p,a,c,k,e,d){while(c--)if(k[c])p=p.replace(new RegExp('\\\\b'+c.toString(a)+'\\\\b','g'),k[c]);return p}" +
        "('$escaped',$radix,${words.size},'${words.joinToString("|")}'.split('|'),0,{}))"
}

/** LZW compression over characters, codes from 256 for multi-character entries (the inverse of [Codecs.lzw]). */
internal fun lzwPack(text: String): String {
    val dictionary = HashMap<String, Int>()
    val out = StringBuilder()
    var current = ""
    fun emit(entry: String) {
        out.append(if (entry.length == 1) entry[0] else dictionary.getValue(entry).toChar())
    }
    for (c in text) {
        val joined = current + c
        if (current.isEmpty() || joined in dictionary) {
            current = joined
            continue
        }
        emit(current)
        dictionary[joined] = 256 + dictionary.size
        current = c.toString()
    }
    if (current.isNotEmpty()) emit(current)
    return out.toString()
}
