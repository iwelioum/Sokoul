package com.sokoul.pc.party

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import com.sokoul.pc.playback.session.PlayRequest
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.math.abs

/*
 * Watch party: several people watch the same title together, each with their own source, kept on the
 * same moment through the Socket.IO server of API/watchpartyAPI (rooms, invitation code, chat, play/pause/seek).
 */

/** What everyone watches. Each viewer opens it with their own sources: only the title travels. */
data class PartyMedia(val kind: String, val tmdbId: Int, val season: Int?, val episode: Int?, val title: String, val image: String?) {
    /** The server only needs a non-empty `src`; Sokoul's own scheme says which title it is. */
    val src: String get() = "sokoul:$kind:$tmdbId:${season ?: 0}:${episode ?: 0}"

    fun request() = PlayRequest(kind, tmdbId, season, episode, title, season?.let { "S${it}E$episode" }, image)

    companion object {
        fun of(r: PlayRequest) = PartyMedia(r.kind, r.tmdbId, r.season, r.episode, r.title, r.image)

        fun fromServer(o: JsonObject): PartyMedia? {
            val parts = (o["src"] as? JsonPrimitive)?.contentOrNull?.split(':') ?: return null
            if (parts.size != 5 || parts[0] != "sokoul") return null
            val season = parts[3].toIntOrNull()?.takeIf { it > 0 }
            return PartyMedia(
                parts[1], parts[2].toIntOrNull() ?: return null, season, parts[4].toIntOrNull()?.takeIf { season != null },
                (o["title"] as? JsonPrimitive)?.contentOrNull ?: "Sokoul", (o["poster"] as? JsonPrimitive)?.contentOrNull,
            )
        }
    }
}

/** The room's playback as last told by the server, and when this device heard it. */
data class PartyPlayback(
    val playing: Boolean, val positionSec: Double, val heardAtMs: Long,
    val serverAtMs: Long? = null, val applyAtMs: Long? = null, val clockOffsetMs: Double? = null,
) {
    /** Where the room is now: a playing room has moved on since it was heard. */
    fun positionAt(nowMs: Long): Double {
        val elapsed = if (serverAtMs != null && clockOffsetMs != null) nowMs + clockOffsetMs - serverAtMs
            else (nowMs - (applyAtMs ?: heardAtMs)).toDouble()
        return if (playing) positionSec + elapsed.coerceAtLeast(0.0) / 1000 else positionSec
    }
}

data class PartyMessage(val id: String, val from: String, val text: String, val system: Boolean)

/** What the local player must do to rejoin the room. */
data class SyncAction(val setPlaying: Boolean?, val seekToSec: Double?, val playbackRate: Double? = null) {
    val none: Boolean get() = setPlaying == null && seekToSec == null && playbackRate == null
}

/** The decisions alone, so they can be checked without a server or a player. */
object PartySync {
    /** Beyond this gap the local player jumps to the room's moment; below, small network delays are left alone. */
    const val DRIFT_SEC = 2.0

    fun follow(room: PartyPlayback, localPlaying: Boolean, localSec: Double, nowMs: Long): SyncAction {
        val target = room.positionAt(nowMs)
        return SyncAction(
            setPlaying = room.playing.takeIf { it != localPlaying },
            seekToSec = target.takeIf { abs(it - localSec) > DRIFT_SEC },
        )
    }

    /** A change worth telling the room: play, pause, or a jump the normal flow of time does not explain. */
    fun localChange(wasPlaying: Boolean, wasSec: Double, wasAtMs: Long, playing: Boolean, sec: Double, nowMs: Long): String? {
        if (playing != wasPlaying) return if (playing) "play" else "pause"
        val expected = if (wasPlaying) wasSec + (nowMs - wasAtMs) / 1000.0 else wasSec
        return if (abs(sec - expected) > DRIFT_SEC * 2) "seek" else null
    }
}

sealed interface PartyState {
    data object Off : PartyState
    data object Connecting : PartyState
    data class In(val code: String, val host: Boolean) : PartyState
    data class Failed(val message: String) : PartyState
}

/** One watch party at a time: the REST calls to create or join, then the room's socket. */
class WatchParty(private val http: OkHttpClient) {
    private val sync = PartySyncEngine()
    private val pendingProbes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val probeSequence = java.util.concurrent.atomic.AtomicLong()
    var syncMode by mutableStateOf("classic")
        private set
    var syncSupported by mutableStateOf(false)
        private set
    private val json = Json { ignoreUnknownKeys = true }
    var state by mutableStateOf<PartyState>(PartyState.Off)
        private set
    var media by mutableStateOf<PartyMedia?>(null)
        private set
    /** The room's playback, newest first; the player follows it. */
    var remote by mutableStateOf<PartyPlayback?>(null)
        private set
    val participants = mutableStateListOf<String>()
    val messages = mutableStateListOf<PartyMessage>()
    private var socket: Socket? = null

    /** Called when the room's title changes, so the app can open it. */
    var onMedia: (PartyMedia) -> Unit = {}

    val active: Boolean get() = state is PartyState.In

    suspend fun create(server: String, nickname: String, what: PartyMedia, positionSec: Double, mode: String = "classic"): Boolean {
        val body = buildJsonObject {
            put("nickname", nickname)
            put("maxParticipants", 10)
            put("syncMode", mode.takeIf { it in setOf("classic", "pro") } ?: "classic")
            put("media", buildJsonObject {
                put("src", what.src)
                put("title", what.title)
                what.image?.let { put("poster", it) }
                put("mediaType", what.kind)
                put("mediaId", what.tmdbId.toString())
                what.season?.let { put("seasonNumber", it) }
                what.episode?.let { put("episodeNumber", it) }
                put("position", positionSec)
            })
        }
        val answer = post(server, "api/watchparty/create", body) ?: return false
        media = what
        return connect(server, answer, nickname, host = true)
    }

    suspend fun join(server: String, code: String, nickname: String): Boolean {
        val body = buildJsonObject {
            put("roomCode", code.trim().uppercase())
            put("nickname", nickname)
        }
        val answer = post(server, "api/watchparty/join", body) ?: return false
        return connect(server, answer, nickname, host = false)
    }

    private suspend fun post(server: String, path: String, body: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        set { state = PartyState.Connecting }
        val url = server.trimEnd('/') + "/" + path
        runCatching {
            http.newCall(Request.Builder().url(url).post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { res ->
                val o = json.parseToJsonElement(res.body?.string().orEmpty()).jsonObject
                if (!res.isSuccessful || (o["success"] as? JsonPrimitive)?.booleanOrNull != true) {
                    set { state = PartyState.Failed((o["message"] as? JsonPrimitive)?.contentOrNull ?: "HTTP ${res.code}") }
                    null
                } else {
                    o
                }
            }
        }.getOrElse {
            set { state = PartyState.Failed(it.javaClass.simpleName) }
            null
        }
    }

    private fun connect(server: String, answer: JsonObject, nickname: String, host: Boolean): Boolean {
        val roomId = (answer["roomId"] as? JsonPrimitive)?.contentOrNull ?: return false
        val token = (answer["token"] as? JsonPrimitive)?.contentOrNull ?: return false
        val code = (answer["roomCode"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        socket?.off()
        socket?.disconnect()
        pendingProbes.clear()
        sync.reset()
        set { remote = null; syncMode = "classic"; syncSupported = false }
        val options = IO.Options.builder()
            .setQuery("roomId=${enc(roomId)}&nickname=${enc(nickname)}&token=${enc(token)}")
            .setTransports(arrayOf("websocket"))
            .setReconnection(true)
            .build()
        val s = IO.socket(server.trimEnd('/') + "/watchparty", options)
        s.on(Socket.EVENT_CONNECT) { set { state = PartyState.In(code, host) } }
        s.on(Socket.EVENT_CONNECT_ERROR) { set { if (state !is PartyState.In) state = PartyState.Failed("connect") } }
        s.on("error") { args -> set { state = PartyState.Failed(obj(args)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "error") } }
        s.on("room:info") { args -> obj(args)?.let(::roomInfo) }
        s.on("room:participants") { args ->
            val list = (args.firstOrNull() as? org.json.JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("nickname") } }.orEmpty()
            set {
                participants.clear()
                participants.addAll(list)
            }
        }
        s.on("room:playback") { args -> obj(args)?.let(::playback) }
        s.on("playback:state") { args -> obj(args)?.let(::playback) }
        s.on("sync:modeChanged") { args -> obj(args)?.let { o -> updateMode(text(o, "mode")) } }
        s.on("sync:probeResult") { args -> obj(args)?.let { o ->
            val id = text(o, "probeId") ?: return@let
            val sent = pendingProbes.remove(id) ?: return@let
            val received = number(o, "serverReceivedAt") ?: return@let
            val serverSent = number(o, "serverSentAt") ?: return@let
            sync.probe(sent, received, serverSent, System.currentTimeMillis())
        } }
        s.on("playback:schedule") { args -> obj(args)?.let { o ->
            val action = text(o, "action") ?: return@let
            val position = (o["position"] as? JsonPrimitive)?.doubleOrNull ?: return@let
            val scheduled = number(o, "scheduledAt") ?: return@let
            val serverNow = number(o, "serverNow") ?: return@let
            sync.schedule(action, position, scheduled, serverNow, System.currentTimeMillis())
        } }
        s.on("media:updated") { args -> obj(args)?.let(PartyMedia::fromServer)?.let(::mediaChanged) }
        s.on("room:chat") { args ->
            obj(args)?.let { o ->
                val text = (o["text"] as? JsonPrimitive)?.contentOrNull ?: return@let
                val msg = PartyMessage(
                    (o["id"] as? JsonPrimitive)?.contentOrNull ?: text.hashCode().toString(),
                    (o["senderNickname"] as? JsonPrimitive)?.contentOrNull.orEmpty(), text,
                    (o["type"] as? JsonPrimitive)?.contentOrNull == "system",
                )
                set {
                    if (messages.none { it.id == msg.id }) messages += msg
                    while (messages.size > MAX_MESSAGES) messages.removeAt(0)
                }
            }
        }
        socket = s
        s.connect()
        return true
    }

    private fun roomInfo(o: JsonObject) {
        updateMode(text(o, "syncMode"))
        (o["media"] as? JsonObject)?.let(PartyMedia::fromServer)?.let(::mediaChanged)
    }

    private fun mediaChanged(m: PartyMedia) {
        if (m == media) return
        sync.resetMedia()
        set { media = m; remote = null }
        onMedia(m)
    }

    private fun playback(o: JsonObject) {
        val playing = (o["isPlaying"] as? JsonPrimitive)?.booleanOrNull ?: return
        val position = (o["position"] as? JsonPrimitive)?.doubleOrNull ?: (o["position"] as? JsonPrimitive)?.intOrNull?.toDouble() ?: return
        val room = PartyPlayback(playing, position, System.currentTimeMillis(), number(o, "updatedAt"))
        if (sync.ingest(room)) set { remote = room }
    }

    private fun updateMode(mode: String?) {
        if (mode !in setOf("classic", "pro")) return
        sync.setMode(mode!!)
        set { syncMode = mode; syncSupported = true }
    }

    /** Mode changes are acknowledged by room info/events; guests and older servers cannot expose this control. */
    fun setSyncMode(mode: String): Boolean {
        val connection = socket ?: return false
        if ((state as? PartyState.In)?.host != true || !syncSupported || !connection.connected() || mode !in setOf("classic", "pro")) return false
        connection.emit("sync:setMode", JSONObject().put("mode", mode))
        return true
    }

    /** Called by the player effect; old servers may ignore probes without affecting classic playback. */
    fun probeClock() {
        val active = socket ?: return
        if (!active.connected()) return
        val now = System.currentTimeMillis()
        pendingProbes.entries.removeAll { now - it.value > 10_000 }
        if (pendingProbes.size >= 8) return
        val id = probeSequence.incrementAndGet().toString()
        pendingProbes[id] = now
        active.emit("sync:probe", JSONObject().put("probeId", id).put("clientSentAt", now))
    }

    fun follow(localPlaying: Boolean, localSec: Double, nowMs: Long): SyncAction = sync.tick(localPlaying, localSec, nowMs)

    private fun text(o: JsonObject, key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    private fun number(o: JsonObject, key: String): Long? = (o[key] as? JsonPrimitive)?.doubleOrNull
        ?.takeIf { it.isFinite() && it >= 0 && it <= 100_000_000_000_000.0 }?.toLong()

    /** Tells the room what this viewer did. The server ignores it from a guest without control. */
    fun tell(playing: Boolean, positionSec: Double, reason: String) {
        socket?.emit("playback:update", JSONObject().put("isPlaying", playing).put("position", positionSec).put("reason", reason))
    }

    /** The host moved to another title (next episode, another film): everyone follows. */
    fun changeMedia(what: PartyMedia) {
        if ((state as? PartyState.In)?.host != true || what == media) return
        sync.resetMedia()
        remote = null
        media = what
        socket?.emit(
            "media:change",
            JSONObject().put("src", what.src).put("title", what.title).put("poster", what.image).put("mediaType", what.kind)
                .put("mediaId", what.tmdbId.toString()).put("seasonNumber", what.season).put("episodeNumber", what.episode),
        )
    }

    fun say(text: String) {
        if (text.isNotBlank()) socket?.emit("chat:message", JSONObject().put("text", text.take(MAX_CHAT)))
    }

    fun leave() {
        socket?.off()
        socket?.disconnect()
        socket = null
        pendingProbes.clear()
        sync.reset()
        syncMode = "classic"
        syncSupported = false
        state = PartyState.Off
        media = null
        remote = null
        participants.clear()
        messages.clear()
    }

    private fun obj(args: Array<out Any?>): JsonObject? =
        (args.firstOrNull() as? JSONObject)?.let { runCatching { json.parseToJsonElement(it.toString()).jsonObject }.getOrNull() }

    // The name of the charset, not the Charset: the second form only exists from Android 13, and the TV app aims at Android 8.
    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    /** Socket callbacks come on the socket's own thread: state changes go through a snapshot. */
    private inline fun set(crossinline block: () -> Unit) = Snapshot.withMutableSnapshot { block() }

    private companion object {
        const val MAX_MESSAGES = 60
        const val MAX_CHAT = 500
    }
}
