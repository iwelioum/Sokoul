package com.sokoul.iptv.data.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable enum class SportKind { Football, Basketball }

/** The competitions the Sport page follows, by their ESPN path. */
/**
 * The competitions the Sport page follows, by their ESPN path, with the channels that usually show them in France:
 * ESPN's own broadcasters are American ("USA Net"), and asked for France it names none (2026-11-23).
 */
@Serializable enum class SportLeague(val path: String, val label: String, val kind: SportKind, val frenchChannels: List<String>) {
    Ligue1("soccer/fra.1", "Ligue 1", SportKind.Football, listOf("Ligue 1+", "beIN Sports")),
    Premier("soccer/eng.1", "Premier League", SportKind.Football, listOf("Canal+")),
    Champions("soccer/uefa.champions", "Ligue des champions", SportKind.Football, listOf("Canal+")),
    Europa("soccer/uefa.europa", "Ligue Europa", SportKind.Football, listOf("Canal+")),
    LaLiga("soccer/esp.1", "LaLiga", SportKind.Football, listOf("beIN Sports")),
    SerieA("soccer/ita.1", "Serie A", SportKind.Football, listOf("beIN Sports")),
    Bundesliga("soccer/ger.1", "Bundesliga", SportKind.Football, listOf("beIN Sports")),
    Nba("basketball/nba", "NBA", SportKind.Basketball, listOf("beIN Sports")),
}

@Serializable enum class MatchState { Upcoming, Live, Finished, Off }

@Serializable data class SportTeam(val abbr: String, val name: String, val color: Long?, val logo: String?, val score: String?)

/** One match: who, when, where, how it stands, and who shows it. */
@Serializable data class SportMatch(
    val id: String,
    val league: SportLeague,
    val start: Long,
    val state: MatchState,
    /** The clock of a live match ("5'", "Q3 4:12"); null otherwise. */
    val clock: String?,
    val venue: String?,
    val home: SportTeam,
    val away: SportTeam,
    val broadcasts: List<String>,
    /** The broadcasters are the league's usual ones in France, not named for this match. */
    val usualBroadcasts: Boolean = false,
) {
    val title get() = "${home.name} – ${away.name}"
    val scored get() = state == MatchState.Live || state == MatchState.Finished
}

/** ESPN's public scoreboards, read as Sokoul's matches. Pure parsing: tested on a saved answer. */
object SportScoresParse {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject
    private fun JsonObject.arr(k: String) = (this[k] as? JsonArray).orEmpty()

    fun matches(body: String, league: SportLeague): List<SportMatch> = runCatching {
        val root = json.parseToJsonElement(body) as JsonObject
        root.arr("events").mapNotNull { e -> (e as? JsonObject)?.let { match(it, league) } }
    }.getOrDefault(emptyList())

    private fun match(e: JsonObject, league: SportLeague): SportMatch? {
        val c = e.arr("competitions").firstOrNull() as? JsonObject ?: return null
        val start = e.str("date")?.let { parseDate(it) } ?: return null
        val type = c.obj("status")?.obj("type")
        val state = when (type?.str("state")) {
            "pre" -> MatchState.Upcoming
            "in" -> MatchState.Live
            "post" -> if (type.str("completed") == "true" || type.str("name")?.contains("FINAL") == true) MatchState.Finished else MatchState.Off
            else -> MatchState.Off
        }
        val teams = c.arr("competitors").mapNotNull { it as? JsonObject }
        val home = teams.firstOrNull { it.str("homeAway") == "home" } ?: teams.getOrNull(0) ?: return null
        val away = teams.firstOrNull { it.str("homeAway") == "away" } ?: teams.getOrNull(1) ?: return null
        val clock = if (state == MatchState.Live) (c.obj("status")?.str("displayClock")?.takeIf { it.isNotBlank() && it != "0'" && it != "0:00" } ?: type?.str("shortDetail")) else null
        return SportMatch(
            id = e.str("id") ?: return null,
            league = league,
            start = start,
            state = state,
            clock = clock,
            venue = c.obj("venue")?.str("fullName"),
            home = team(home) ?: return null,
            away = team(away) ?: return null,
            broadcasts = c.arr("broadcasts").flatMap { b -> (b as? JsonObject)?.arr("names").orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } }.distinct(),
        )
    }

    private fun team(o: JsonObject): SportTeam? {
        val t = o.obj("team") ?: return null
        return SportTeam(
            abbr = t.str("abbreviation") ?: t.str("shortDisplayName") ?: return null,
            name = t.str("shortDisplayName") ?: t.str("displayName") ?: return null,
            color = t.str("color")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it },
            logo = t.str("logo"),
            score = o.str("score"),
        )
    }

    /** ESPN writes "2026-11-22T19:45Z" (no seconds). */
    fun parseDate(s: String): Long? = runCatching { Instant.parse(if (s.length == 17) s.dropLast(1) + ":00Z" else s).toEpochMilli() }.getOrNull()
}

/** The matches of the followed leagues from yesterday to the day after tomorrow, kept a minute. */
class SportScores(private val http: OkHttpClient) {
    @Volatile private var kept: Pair<Long, List<SportMatch>>? = null

    suspend fun matches(force: Boolean = false, now: Long = System.currentTimeMillis()): List<SportMatch> {
        kept?.takeIf { !force && now - it.first < KEEP_MS }?.let { return it.second }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val f = DateTimeFormatter.BASIC_ISO_DATE
        // One day per call: ESPN refuses a range of dates (HTTP 400, 2026-11-23).
        val days = (-1L..2L).map { today.plusDays(it).format(f) }
        val all = withContext(Dispatchers.IO) {
            coroutineScope {
                SportLeague.entries.flatMap { league -> days.map { league to it } }.map { (league, day) ->
                    async {
                        try {
                            val url = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/scoreboard?dates=$day&region=fr&lang=fr"
                            http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).await().use { r ->
                                if (r.isSuccessful) SportScoresParse.matches(r.body.string(), league).map { m ->
                                    // No broadcaster named for France: the league's usual French channels.
                                    if (m.broadcasts.isEmpty()) m.copy(broadcasts = league.frenchChannels, usualBroadcasts = true) else m
                                } else emptyList()
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten().distinctBy { it.league to it.id }
            }
        }
        if (all.isNotEmpty()) kept = now to all
        return all
    }

    private companion object {
        const val KEEP_MS = 60_000L
    }
}
