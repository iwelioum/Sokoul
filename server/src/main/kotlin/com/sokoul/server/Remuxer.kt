package com.sokoul.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/** What ffprobe says about a media file: its container and the codecs of its first video and audio tracks. */
data class MediaProbe(val container: String, val video: String?, val audio: String?)

/**
 * ffmpeg on the server (sokoul-server D7): MKV, or audio Safari cannot play (AC3, E-AC3, DTS, TrueHD, FLAC…), becomes
 * fMP4 HLS on the fly. The video is copied; the audio is copied when Safari reads it and turned into AAC otherwise.
 *
 * Each stream gets its own folder under [workDir]; [RemuxJob.stop] kills ffmpeg and deletes it. ffmpeg only reads
 * http(s) (`-protocol_whitelist`), so a resolved address can never make it open a local file.
 */
class Remuxer(
    /** The ffmpeg program, or null when there is none: then nothing is remuxed and the client is told. */
    val ffmpeg: String?,
    private val workDir: File = java.nio.file.Files.createTempDirectory("sokoul-remux").toFile(),
) {
    val available: Boolean get() = ffmpeg != null

    private val ffprobe: String? = ffmpeg?.let { path ->
        val f = File(path)
        val sibling = File(f.parentFile ?: File("."), if (f.name.endsWith(".exe", true)) "ffprobe.exe" else "ffprobe")
        if (f.parentFile != null && sibling.isFile) sibling.path else findOnPath("ffprobe")
    }

    init {
        // Folders left by a previous run (crash, kill) go away at start.
        workDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Container and codecs of [url], read with ffprobe within 20 s; null when it cannot tell. */
    suspend fun probe(url: String, headers: Map<String, String>): MediaProbe? {
        val probe = ffprobe ?: return null
        return withContext(Dispatchers.IO) {
            val cmd = listOf(probe, "-v", "error") + inputOptions(headers) +
                listOf("-show_entries", "format=format_name:stream=codec_type,codec_name", "-of", "json", "-i", url)
            val process = runCatching { ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start() }.getOrNull()
                ?: return@withContext null
            try {
                val out = process.inputStream.bufferedReader().use { r -> r.readText().take(MAX_PROBE_OUTPUT) }
                if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) return@withContext null
                parseProbe(out)
            } finally {
                process.destroyForcibly()
            }
        }
    }

    /** Starts ffmpeg for [id]: the playlist appears as `index.m3u8` in [RemuxJob.dir]. */
    fun start(id: String, url: String, headers: Map<String, String>, probe: MediaProbe?): RemuxJob? {
        val program = ffmpeg ?: return null
        val dir = File(workDir, Secrets.sha256(id).take(12).joinToString("") { "%02x".format(it) })
        dir.deleteRecursively()
        dir.mkdirs()
        val copyAudio = probe?.audio in SAFARI_AUDIO
        val cmd = mutableListOf(program, "-nostdin", "-loglevel", "error") + inputOptions(headers) +
            listOf("-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5", "-i", url) +
            listOf("-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn", "-c:v", "copy") +
            (if (probe?.video == "hevc") listOf("-tag:v", "hvc1") else emptyList()) +
            (if (copyAudio) listOf("-c:a", "copy") else listOf("-c:a", "aac", "-b:a", "192k", "-ac", "2")) +
            listOf(
                "-f", "hls", "-hls_time", "6", "-hls_list_size", "0", "-hls_playlist_type", "event",
                "-hls_segment_type", "fmp4", "-hls_fmp4_init_filename", "init.mp4",
                "-hls_flags", "temp_file+independent_segments",
                // Relative names in its own folder: ffmpeg writes the init segment beside its working folder.
                "-hls_segment_filename", "seg_%05d.m4s",
                "index.m3u8",
            )
        val process = runCatching {
            ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).redirectOutput(File(dir, "ffmpeg.log")).start()
        }.getOrElse {
            log.warn("ffmpeg could not start ({})", it::class.simpleName)
            dir.deleteRecursively()
            return null
        }
        return RemuxJob(dir, process)
    }

    companion object {
        private val log = LoggerFactory.getLogger(Remuxer::class.java)
        private const val MAX_PROBE_OUTPUT = 64 * 1024

        /** Audio codecs Safari plays in fMP4 HLS on iPhone and macOS, as the spec counts them. */
        val SAFARI_AUDIO = setOf("aac", "mp3", "alac")

        /** Containers Safari opens directly as a file. */
        private val SAFARI_CONTAINERS = listOf("mp4", "mov")

        /** ffmpeg on PATH, or the configured [configured] when it is an existing file. */
        fun find(configured: String?): String? {
            configured?.trim()?.takeIf { it.isNotEmpty() }?.let { return if (File(it).isFile) File(it).absolutePath else null }
            return findOnPath("ffmpeg")
        }

        private fun findOnPath(name: String): String? {
            val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", true)
            val names = if (windows) listOf("$name.exe", name) else listOf(name)
            return System.getenv("PATH").orEmpty().split(File.pathSeparatorChar).filter { it.isNotBlank() }
                .flatMap { dir -> names.map { File(dir, it) } }
                .firstOrNull { it.isFile && it.canExecute() }?.absolutePath
        }

        /** Whether Safari would fail on this file: a container it does not open, or an audio track it does not play. */
        fun needsRemux(probe: MediaProbe?, url: String): Boolean {
            if (probe == null) {
                val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
                return ext in setOf("mkv", "avi", "ts", "flv", "wmv")
            }
            val container = SAFARI_CONTAINERS.any { probe.container.contains(it) }
            val audio = probe.audio == null || probe.audio in SAFARI_AUDIO
            return !container || !audio
        }

        fun parseProbe(json: String): MediaProbe? {
            val o = runCatching { Json.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
            val format = (o["format"] as? JsonObject)?.jStr("format_name") ?: return null
            val streams = o.jArr("streams").mapNotNull { it as? JsonObject }
            fun codec(type: String) = streams.firstOrNull { it.jStr("codec_type") == type }?.jStr("codec_name")
            return MediaProbe(format, codec("video"), codec("audio"))
        }

        /** The extractor's headers for ffmpeg: `-user_agent` and `-headers`, CR and LF removed. */
        fun inputOptions(headers: Map<String, String>): List<String> {
            fun clean(s: String) = s.filterNot { it == '\r' || it == '\n' }
            val ua = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
            val rest = headers.filterKeys { !it.equals("User-Agent", true) && HEADER_NAME.matches(it) }
            val opts = mutableListOf("-protocol_whitelist", "http,https,tcp,tls,crypto")
            if (ua != null) opts += listOf("-user_agent", clean(ua))
            if (rest.isNotEmpty()) opts += listOf("-headers", rest.entries.joinToString("") { "${it.key}: ${clean(it.value)}\r\n" })
            return opts
        }

        private val HEADER_NAME = Regex("^[A-Za-z0-9-]{1,64}$")
    }
}

/** One running remux: ffmpeg writing fMP4 HLS into [dir]. */
class RemuxJob(val dir: File, private val process: Process) {
    val alive: Boolean get() = process.isAlive

    fun stop() {
        process.destroy()
        if (!process.waitFor(3, TimeUnit.SECONDS)) process.destroyForcibly()
        dir.deleteRecursively()
    }
}
