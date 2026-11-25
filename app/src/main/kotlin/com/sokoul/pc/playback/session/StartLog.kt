package com.sokoul.pc.playback.session

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The last starts, kept across restarts in the data folder: the title, then each step of [StartClock] (searches, tries,
 * Real-Debrid refusals and pauses, first image). Always on, on every device, so that a start that went wrong can be read
 * afterwards: on 2026-11-24 a film fell to IPTV on the PC and nothing said why, the PC keeping no log outside debug mode.
 * Holds no address, key or link (steps name versions and hash prefixes only), and is passed through [com.sokoul.iptv.data.net.Redact].
 */
object StartLog {
    data class Entry(val at: String, val title: String, val steps: List<String>) {
        /** "Image in 3.9 s · 4K · HDR · MULTI" or the last failure, for a one-line summary. */
        fun outcome(): String {
            val image = steps.lastOrNull { "first image" in it }
            val tried = steps.lastOrNull { " try " in it }?.substringAfter(" try ")?.removeSuffix(" (wait)")
            if (image != null) {
                val ms = Regex("""\+(\d+) ms""").find(image)?.groupValues?.get(1)?.toLongOrNull()
                return listOfNotNull(ms?.let { "%.1f s".format(it / 1000.0) }, tried).joinToString(" · ")
            }
            return steps.lastOrNull { "failed" in it || "gave up" in it }?.substringAfter(" ms ") ?: (steps.lastOrNull()?.substringAfter(" ms ") ?: "")
        }
        val played get() = steps.any { "first image" in it }
    }

    private val entries = ArrayDeque<Entry>()
    @Volatile private var loaded = false
    private val saver = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "sokoul-start-log").apply { isDaemon = true } }
    private val stamp = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    @Volatile var file: () -> File? = { runCatching { File(com.sokoul.desktop.data.AppDirs.data, FILE) }.getOrNull() }

    @Synchronized fun begin(title: String, what: String) {
        load()
        entries.addLast(Entry(LocalDateTime.now().format(stamp), title, listOf(what)))
        while (entries.size > MAX_STARTS) entries.removeFirst()
        save()
    }

    @Synchronized fun add(line: String) {
        load()
        val last = entries.removeLastOrNull() ?: return
        entries.addLast(last.copy(steps = (last.steps + com.sokoul.iptv.data.net.Redact.text(line)).takeLast(MAX_STEPS)))
        save()
    }

    /** Newest first. */
    @Synchronized fun recent(): List<Entry> { load(); return entries.reversed() }

    /** Points the log at another file (null: memory only) and reads it afresh; for tests that need a known log. */
    @Synchronized internal fun open(at: () -> File?) {
        file = at
        entries.clear()
        loaded = false
    }

    private fun load() {
        if (loaded) return
        loaded = true
        val text = runCatching { file()?.takeIf { it.isFile }?.readText() }.getOrNull() ?: return
        text.split("\n## ").filter { it.isNotBlank() }.forEach { block ->
            val lines = block.removePrefix("## ").lines()
            val head = lines.first().split(" · ", limit = 2)
            if (head.size == 2) entries.addLast(Entry(head[0], head[1], lines.drop(1).filter { it.isNotBlank() }))
        }
        while (entries.size > MAX_STARTS) entries.removeFirst()
    }

    private fun save() {
        val text = entries.joinToString("\n") { e -> "## ${e.at} · ${e.title}\n" + e.steps.joinToString("\n") }
        saver.execute { runCatching { file()?.writeText(text) } }
    }

    private const val FILE = "start-log.txt"
    private const val MAX_STARTS = 20
    private const val MAX_STEPS = 80
}
