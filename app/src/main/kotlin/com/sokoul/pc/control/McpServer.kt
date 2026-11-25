package com.sokoul.pc.control

import com.sokoul.desktop.data.AppDirs
import com.sokoul.pc.data.PcLog
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors

/**
 * The app's tools (AppBridge) as a Model Context Protocol server, so that an assistant, an editor or any MCP client
 * can search, open pages and drive the player.
 *
 * Streamable HTTP transport, JSON responses only (no server-initiated messages), on the loopback interface only. Every
 * request needs the bearer token kept in `mcp-token.txt` of the data folder, and a browser page cannot reach it: any
 * Origin other than localhost is refused (DNS rebinding). Off unless SOKOUL_MCP is set: "1" uses [DEFAULT_PORT], a
 * number picks the port. PC only: Android has no com.sun.net.httpserver, so the TV build leaves this file out.
 */
class McpServer(
    private val registry: () -> ToolRegistry?,
    private val port: Int,
    private val tokenFile: File = File(AppDirs.data, TOKEN_FILE),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var server: HttpServer? = null
    val token: String by lazy { loadOrCreateToken() }

    fun start() {
        token // Created before the first request, so the file exists as soon as the server listens.
        val s = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        s.createContext(PATH) { ex -> runCatching { handle(ex) }.onFailure { PcLog.event("mcp: ${it.javaClass.simpleName}"); runCatching { ex.close() } } }
        s.executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "sokoul-mcp").apply { isDaemon = true } }
        s.start()
        server = s
        PcLog.event("mcp server on http://127.0.0.1:$port$PATH")
    }

    /** The port really used (useful when 0 asked the system for a free one). */
    val boundPort: Int get() = server?.address?.port ?: port

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun handle(ex: HttpExchange) {
        val origin = ex.requestHeaders.getFirst("Origin")
        if (origin != null && !LOCAL_ORIGIN.matches(origin)) return reply(ex, 403, null)
        val auth = ex.requestHeaders.getFirst("Authorization").orEmpty()
        if (!sameSecret(auth.removePrefix("Bearer ").trim(), token)) return reply(ex, 401, null)
        when (ex.requestMethod) {
            "POST" -> Unit
            // No stream of server messages: GET is not offered, and there is no session to delete.
            "GET", "DELETE" -> return reply(ex, 405, null)
            else -> return reply(ex, 405, null)
        }
        val body = ex.requestBody.use { it.readBytes().decodeToString() }
        val message = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val answer: JsonElement? = when (message) {
            is JsonObject -> answer(message)
            is kotlinx.serialization.json.JsonArray -> buildJsonArray { message.forEach { m -> (m as? JsonObject)?.let(::answer)?.let(::add) } }.takeIf { it.isNotEmpty() }
            else -> error(JsonNull, PARSE_ERROR, "Parse error")
        }
        // Notifications and responses from the client get no answer.
        if (answer == null) reply(ex, 202, null) else reply(ex, 200, answer.toString())
    }

    /** The answer to one JSON-RPC message, or null for a notification. */
    private fun answer(msg: JsonObject): JsonObject? {
        val id = msg["id"] ?: return null
        val method = (msg["method"] as? JsonPrimitive)?.contentOrNull ?: return null
        val params = msg["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" -> result(id, buildJsonObject {
                put("protocolVersion", (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull ?: PROTOCOL_VERSION)
                putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                putJsonObject("serverInfo") {
                    put("name", "sokoul")
                    put("title", "Sokoul")
                    put("version", VERSION)
                }
                put("instructions", INSTRUCTIONS)
            })
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, buildJsonObject {
                putJsonArray("tools") { registry()?.tools?.forEach { add(describe(it)) } }
            })
            "tools/call" -> result(id, call(params))
            else -> error(id, METHOD_NOT_FOUND, "Unknown method: $method")
        }
    }

    private fun describe(tool: AppTool) = buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("inputSchema", tool.inputSchema())
        putJsonObject("annotations") {
            put("readOnlyHint", tool.risk == ToolRisk.Read)
            put("destructiveHint", false)
            // Opening a page or a stream twice lands in the same place; a setting toggled twice does not.
            put("idempotentHint", tool.risk != ToolRisk.Setting)
            put("openWorldHint", tool.risk == ToolRisk.Read)
        }
    }

    /** Runs a tool on the interface thread, as a click would, and turns its result into MCP content. */
    private fun call(params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val args = (params["arguments"] as? JsonObject) ?: JsonObject(emptyMap())
        val reg = registry() ?: return toolText("Sokoul is still starting. Try again in a few seconds.", true)
        // Numbers may arrive as JSON numbers; the tools read every argument as text.
        val flat = JsonObject(args.mapValues { (_, v) -> if (v is JsonPrimitive) JsonPrimitive(v.content) else v })
        val result = runBlocking(Dispatchers.Swing) { withTimeoutOrNull(TOOL_TIMEOUT_MS) { reg.call(name, flat, ToolPolicy.Everything) } }
        return when (result) {
            null -> toolText("$name did not finish within ${TOOL_TIMEOUT_MS / 1000} s.", true)
            is ToolResult.Done -> buildJsonObject {
                putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", result.value.toString()) }) }
                (result.value as? JsonObject)?.let { put("structuredContent", it) }
                put("isError", false)
            }
            is ToolResult.Refused -> toolText(result.reason, true)
            is ToolResult.Failed -> toolText(result.message, true)
        }
    }

    private fun toolText(text: String, isError: Boolean) = buildJsonObject {
        putJsonArray("content") { add(buildJsonObject { put("type", "text"); put("text", text) }) }
        put("isError", isError)
    }

    private fun result(id: JsonElement, value: JsonElement) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", value)
    }

    private fun error(id: JsonElement, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }

    private fun reply(ex: HttpExchange, status: Int, body: String?) {
        val bytes = body?.toByteArray(Charsets.UTF_8)
        if (bytes != null) ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(status, bytes?.size?.toLong() ?: -1L)
        bytes?.let { b -> ex.responseBody.use { it.write(b) } }
        ex.close()
    }

    /** A random token, written once with only the owner allowed to read it where the file system allows. */
    private fun loadOrCreateToken(): String {
        val file = tokenFile
        file.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.length >= MIN_TOKEN }?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val value = bytes.joinToString("") { "%02x".format(it) }
        file.writeText(value)
        file.setReadable(false, false)
        file.setReadable(true, true)
        return value
    }

    private fun sameSecret(a: String, b: String) = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    companion object {
        const val DEFAULT_PORT = 8765
        const val PATH = "/mcp"
        const val TOKEN_FILE = "mcp-token.txt"
        private const val PROTOCOL_VERSION = "2025-06-18"
        private const val VERSION = "1.0.0"
        private const val TOOL_TIMEOUT_MS = 60_000L
        private const val MIN_TOKEN = 32
        private const val PARSE_ERROR = -32700
        private const val METHOD_NOT_FOUND = -32601
        private val LOCAL_ORIGIN = Regex("^https?://(localhost|127\\.0\\.0\\.1|\\[::1])(:\\d+)?$")
        private const val INSTRUCTIONS =
            "Sokoul is a media centre (films, series, anime, manga, live TV). Call get_state first to see the screen. " +
                "Use search_titles then open_title or play; list_versions and choose_version drive the player."

        /** Starts the server when SOKOUL_MCP asks for it; null otherwise. */
        fun fromEnvironment(registry: () -> ToolRegistry?): McpServer? {
            val raw = System.getenv("SOKOUL_MCP")?.trim().orEmpty()
            if (raw.isEmpty() || raw == "0") return null
            val port = raw.toIntOrNull()?.takeIf { it in 1_024..65_535 } ?: DEFAULT_PORT
            return runCatching { McpServer(registry, port).also { it.start() } }
                .onFailure { PcLog.event("mcp server not started: ${it.javaClass.simpleName}") }
                .getOrNull()
        }
    }
}
