package com.sokoul.pc.dev

import com.sokoul.desktop.data.Catalog
import com.sokoul.desktop.data.DesktopDb
import com.sokoul.desktop.data.DesktopDebrid
import com.sokoul.desktop.data.DesktopPrefs
import com.sokoul.desktop.data.ProviderConfig
import com.sokoul.iptv.data.debrid.RealDebridApi
import com.sokoul.pc.data.TmdbCatalog
import com.sokoul.pc.playback.session.ConnectionGate
import com.sokoul.pc.playback.session.PlayRequest
import com.sokoul.pc.playback.session.PlayVersion
import com.sokoul.pc.playback.sources.DebridTransfers
import com.sokoul.pc.playback.sources.DeviceCaps
import com.sokoul.pc.playback.sources.LocalServerKey
import com.sokoul.pc.playback.sources.RealSources
import com.sokoul.pc.playback.sources.StreamProbe
import com.sokoul.pc.playback.sources.VersionRanking
import com.sokoul.pc.playback.sources.WebSources
import com.sokoul.pc.startup.SetupState
import com.sokoul.pc.startup.StartupRoute
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.system.exitProcess

/**
 * Runs the app's real source search for a set of titles with the keys of the local database, and prints what was found:
 * counts by origin, quality and announced language, how long each search took, and the first versions of the ranking the
 * player uses on the PC and on a 4K tablet. Never prints a key, a token or a link.
 *
 * Arguments: `kind:tmdbId[:season:episode]:Label`…, e.g. `movie:693134:Dune2 tv:37854:1:1:OnePiece`.
 */
fun main(args: Array<String>): Unit = runBlocking {
    val db = DesktopDb.open()
    val catalog = Catalog(db)
    val pcCatalog = TmdbCatalog(db, catalog) { StartupRoute.state(db) == SetupState.Done }
    val debrid = DesktopDebrid(db, catalog)
    val local = com.sokoul.pc.playback.sources.NativeEmbedResolver(catalog.http)
    val sources = RealSources(
        db, debrid, catalog,
        DebridTransfers(RealDebridApi(catalog.http, { ProviderConfig.key(db, "realdebrid") })),
        ConnectionGate(), StreamProbe(catalog.http),
        anime = pcCatalog.animeIdentity,
        web = WebSources(
            catalog.http,
            baseUrl = { ProviderConfig.key(db, "mainapi_url") ?: ProviderConfig.MAINAPI_DEFAULT_URL },
            accessKey = { ProviderConfig.key(db, "mainapi") ?: LocalServerKey.read() },
            probe = StreamProbe(catalog.http),
            localResolver = local,
        ),
        animeSama = com.sokoul.pc.playback.sources.AnimeSamaSource(
            catalog.http,
            baseUrl = { ProviderConfig.key(db, "mainapi_url") ?: ProviderConfig.MAINAPI_DEFAULT_URL },
            accessKey = { ProviderConfig.key(db, "mainapi") ?: LocalServerKey.read() },
            probe = StreamProbe(catalog.http),
            localResolver = local,
            direct = com.sokoul.sources.animesama.AnimeSama(catalog.http),
        ),
    )
    println("configured: " + ProviderConfig.statuses(db).filter { it.configured }.joinToString { it.id })
    val prefs = DesktopPrefs(db).playback()
    println("prefs: start=${prefs.startMode} bestFirst=${prefs.bestQualityFirst} maxQ=${prefs.maxQuality} maxSize=${prefs.maxSize} origin=${prefs.origin}")
    val tablet = DeviceCaps(hdr10 = true, dolbyVision = false, maxHeight = 2560, decodes4k = true)
    for (arg in args) {
        val p = arg.split(':')
        val kind = p[0]
        val id = p[1].toInt()
        val (season, episode, label) = if (kind == "tv") Triple(p[2].toInt(), p[3].toInt(), p.getOrElse(4) { "" }) else Triple(null, null, p.getOrElse(2) { "" })
        val request = PlayRequest(kind, id, season, episode, label, null, null)
        com.sokoul.pc.playback.session.StartClock.start("audit $label")
        val start = System.currentTimeMillis()
        val firstAt = mutableMapOf<String, Long>()
        var last = emptyList<PlayVersion>()
        withTimeoutOrNull(90_000) {
            sources.find(request, refresh = true).collect { u ->
                u.versions.forEach { v -> firstAt.putIfAbsent(v.origin.name, System.currentTimeMillis() - start) }
                last = u.versions
            }
        }
        val took = System.currentTimeMillis() - start
        println("\n=== $label ($kind $id${season?.let { " S${it}E$episode" } ?: ""}) — ${last.size} versions in ${took / 1000.0} s; first by origin (ms): $firstAt")
        if (System.getenv("AUDIT_EP") != null) {
            val rank = VersionRanking.rank(last, prefs.copy(bestQualityFirst = true), true, false, tablet, workLanguage = null)
            println("  rejected ${last.count { it.rejected != null }} ${last.mapNotNull { it.rejected?.name }.groupingBy { it }.eachCount()}")
            // The episode each of the first files says it holds, read from its name.
            rank.take(8).forEach { v -> println("    ${v.origin} ${v.name.take(90)}") }
        }
        com.sokoul.pc.playback.session.StartClock.steps().filter { st -> listOf("anime", "web", "identity", "iptv", "sama").any { st.contains(it, true) } }
            .forEach { println("  · ${it.take(160)}") }
        val byOrigin = last.groupBy { it.origin.name }
        byOrigin.forEach { (o, vs) ->
            println("  $o: ${vs.size} | quality ${vs.groupingBy { it.quality?.name ?: "?" }.eachCount()} | lang ${vs.groupingBy { it.language?.name ?: "?" }.eachCount()} | ready ${vs.groupingBy { it.readiness.name }.eachCount()}")
        }
        fun show(title: String, list: List<PlayVersion>) {
            println("  $title:")
            list.take(6).forEachIndexed { i, v -> println("    ${i + 1}. ${v.origin} ${v.readiness} · ${v.summary()}") }
        }
        // Opens the first web versions as the player would, to tell a link found from a link that plays.
        last.filter { it.origin.name == "Web" || it.origin.name == "Iptv" }.sortedBy { it.origin.name }.take(5).forEach { v ->
            val t0 = System.currentTimeMillis()
            val r = runCatching { withTimeoutOrNull(40_000) { v.open(false) {} } }
            val what = r.exceptionOrNull()?.let { (it as? com.sokoul.pc.playback.session.PlayFailureException)?.failure?.toString() ?: it.javaClass.simpleName } ?: if (r.getOrNull() == null) "timeout" else "PLAYS"
            println("  open ${v.summary().take(50)} -> $what in ${System.currentTimeMillis() - t0} ms")
        }
        val fr = true
        if (System.getenv("AUDIT_RANK") != null) show("PC ranking", VersionRanking.rank(last, prefs, fr, prefs.startMode != com.sokoul.desktop.data.StartMode.WaitBest && !prefs.bestQualityFirst, DeviceCaps.Pc, workLanguage = null))
        val tv = prefs.copy(bestQualityFirst = true)
        if (System.getenv("AUDIT_RANK") != null) VersionRanking.rank(last, tv, fr, false, tablet, workLanguage = "ja").withIndex()
            .filter { it.value.origin.name != "Debrid" }.forEach { println("    #${it.index + 1} ${it.value.origin} ${it.value.name.take(45)} ${it.value.language ?: ""} ${it.value.announcedAudio}") }
        if (System.getenv("AUDIT_RANK") != null) show("Tablet ranking", VersionRanking.rank(last, tv, fr, false, tablet, workLanguage = null))
    }
    db.close()
    exitProcess(0)
}
