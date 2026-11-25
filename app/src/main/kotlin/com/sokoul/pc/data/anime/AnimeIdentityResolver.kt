package com.sokoul.pc.data.anime

import com.sokoul.iptv.data.anime.AnimeEntry
import com.sokoul.iptv.data.anime.AnimeExtrasParse
import com.sokoul.iptv.data.anime.AnimeIdentity
import com.sokoul.iptv.data.anime.AnimeIdsParse
import com.sokoul.iptv.data.anime.AnimeIndex
import com.sokoul.iptv.data.anime.AnimeType
import com.sokoul.iptv.data.anime.EntryEpisode
import com.sokoul.iptv.data.anime.EpisodeMapper
import com.sokoul.iptv.data.anime.EpisodeRef
import com.sokoul.iptv.data.anime.Homonym
import com.sokoul.iptv.data.anime.SeaDexRelease
import com.sokoul.iptv.data.anime.SkipInterval
import com.sokoul.iptv.data.net.TmdbInfo
import com.sokoul.iptv.data.net.TmdbItem
import com.sokoul.pc.data.providers.ProviderHttp
import com.sokoul.pc.data.providers.RequestPriority
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

/** What the resolver asks of the catalog. */
interface AnimeTmdb {
    suspend fun details(kind: String, id: Int): TmdbInfo?
    suspend fun seasonSize(tvId: Int, season: Int): Int?
    suspend fun search(query: String): List<TmdbItem>
}

/**
 * Links a title of the catalog to the anime databases by ids, never by a likeness of names. In order: the list kept on
 * the device (Fribb), then the id services (ARM, animeapi), then the names list (manami) and AniList for an exact name
 * confirmed by type and year. Kitsu fills what is missing. Each service has a time limit and a cache: one being down
 * only moves on to the next, and nothing here ever holds a page or the player for long.
 */
class AnimeIdentityResolver(
    private val lists: AnimeLists?,
    private val http: ProviderHttp,
    private val tmdb: AnimeTmdb,
    private val aniList: AniListClient? = null,
    private val enabled: () -> Boolean = { true },
) {
    private val known = ConcurrentHashMap<String, Result>()

    /** Where each step of a lookup ends, for the tools. */
    @Volatile var trace: ((String) -> Unit)? = null

    private fun note(step: String) = trace?.invoke(step)

    private class Result(val identity: AnimeIdentity?, val at: Long = System.currentTimeMillis())

    /** Lookups running now: the player asks for the same identity three times at once (IPTV, Anime-Sama, Real-Debrid). */
    private val running = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<AnimeIdentity?>>()
    private val sizesKnown = ConcurrentHashMap<String, Pair<Int?, Long>>()

    /** Fribb's list on the device, without asking the network; null while it is not there or when the identity is off. */
    suspend fun localIndex(): AnimeIndex? = if (enabled()) lists?.ids() else null

    /** The identity of a catalog title, or null when it is not an anime or when nothing links it for sure. */
    suspend fun identity(kind: String, tmdbId: Int, info: TmdbInfo? = null): AnimeIdentity? {
        if (!enabled()) return null
        val key = "$kind:$tmdbId"
        // An identity found holds for the session. "Nothing found" is asked again after a while: a lookup whose
        // services timed out once left an anime without Kitsu, SeaDex and its romaji names until the app restarted.
        known[key]?.takeIf { it.identity != null || System.currentTimeMillis() - it.at < MISS_RETRY_MS }?.let { return it.identity }
        // One lookup per title at a time, run on its own: the player asks three times at once, and one of them giving up
        // (Anime-Sama's time limit) neither stops nor restarts it for the others. Its calls to AniList go before the rows'
        // (2026-11-24).
        val lookup = running.computeIfAbsent(key) {
            background.async(RequestPriority.Urgent, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                val found = try {
                    resolve(kind, tmdbId, info)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    null
                }
                known[key] = Result(found)
                found
            }
        }
        lookup.invokeOnCompletion { running.remove(key, lookup) }
        lookup.start()
        return lookup.await()
    }

    private suspend fun resolve(kind: String, tmdbId: Int, given: TmdbInfo?): AnimeIdentity? = coroutineScope {
        val series = kind == "tv"
        val local = lists?.ids()
        note("lists " + (local?.size ?: "none"))
        val fromList = if (series) local?.series(tmdbId).orEmpty() else local?.movie(tmdbId).orEmpty()
        val info = given ?: if (fromList.isEmpty()) timed { tmdb.details(kind, tmdbId) } else null
        // Not in the list, and no animation from Asia: no question asked anywhere.
        if (fromList.isEmpty() && info != null && !looksAnime(info)) return@coroutineScope null
        val sources = mutableListOf<String>()
        var entries = fromList.also { if (it.isNotEmpty()) sources += "fribb" }
        if (entries.isEmpty()) {
            val path = if (series) "tv" else "movie"
            val arm = async { timed { online("arm", "https://arm.haglund.dev/api/v2/themoviedb?id=$tmdbId", AnimeIdsParse::arm) } }
            val api = async { timed { online("animeapi", "https://animeapi.my.id/themoviedb/$path/$tmdbId", AnimeIdsParse::animeApi) } }
            entries = pickType((arm.await().orEmpty() + api.await().orEmpty()), series)
            if (entries.isNotEmpty()) sources += "tmdb-id"
        }
        val imdb = info?.imdbId
        if (entries.isEmpty() && imdb != null) {
            val listed = pickType(local?.imdb(imdb).orEmpty(), series)
            val arm = async { timed { online("arm", "https://arm.haglund.dev/api/v2/imdb?id=$imdb", AnimeIdsParse::arm) } }
            val api = async { timed { online("animeapi", "https://animeapi.my.id/imdb/$imdb", AnimeIdsParse::animeApi) } }
            entries = merge(listed + pickType(arm.await().orEmpty() + api.await().orEmpty(), series))
            if (entries.isNotEmpty()) sources += "imdb-id"
        }
        if (entries.isEmpty() && info != null) {
            entries = byExactName(info, series)
            if (entries.isNotEmpty()) sources += "name"
        }
        note("entries " + entries.size + " by " + sources)
        if (entries.isEmpty()) return@coroutineScope null
        entries = merge(entries.map { e -> if (e.mal == null || e.anilist == null) fillFromKitsu(e) else e })
        val details = info ?: timed { tmdb.details(kind, tmdbId) }
        val first = entries.firstOrNull { it.type == AnimeType.TV || it.type == AnimeType.ONA || it.type == AnimeType.MOVIE } ?: entries.first()
        // The names release groups write: Kitsu's English, romaji and short ones (one quick call), else AniList's,
        // whose calls are spaced out and wait behind those of the Anime page.
        val names = async {
            first.kitsu?.let { k -> timed { onlineText("kitsu", "https://kitsu.app/api/edge/anime/$k?fields%5Banime%5D=titles,canonicalTitle,abbreviatedTitles")?.let(AnimeIdsParse::kitsuNames) } }
                ?.takeIf { it.isNotEmpty() }
                ?: first.anilist?.let { id -> aniList?.let { a -> timed { a.byId(id) } } }?.let { t -> listOfNotNull(t.english, t.romaji, t.native) }.orEmpty()
        }
        val others = async { homonyms(tmdbId, details) }
        val identity = AnimeIdentity(tmdbId, series, entries, others.await(), sources, names.await())
        note("names " + identity.names.size + ", homonyms " + identity.homonyms.size)
        identity
    }

    /** Entries of the right kind: seasons for a series, films for a film. */
    private fun pickType(all: List<AnimeEntry>, series: Boolean) =
        all.filter { if (series) it.type != AnimeType.MOVIE else it.type == AnimeType.MOVIE || it.type == AnimeType.SPECIAL || it.type == AnimeType.OTHER }

    /** The same entry told by several services: one entry with what each knows. */
    private fun merge(all: List<AnimeEntry>): List<AnimeEntry> {
        val out = mutableListOf<AnimeEntry>()
        for (e in all) {
            val i = out.indexOfFirst { it.agreesWith(e) }
            if (i >= 0) out[i] = out[i].fill(e) else out += e
        }
        return out
    }

    /**
     * No id links it: a name exactly the same, in two sources that agree (manami and AniList), with the same type and a
     * year within one. Anything less links nothing.
     */
    private suspend fun byExactName(info: TmdbInfo, series: Boolean): List<AnimeEntry> {
        val names = listOfNotNull(info.originalTitle, info.title).distinct()
        val year = info.year
        fun fits(e: AnimeEntry) = (if (series) e.type == AnimeType.TV || e.type == AnimeType.ONA else e.type == AnimeType.MOVIE) &&
            (year == null || e.year == null || kotlin.math.abs(e.year!! - year) <= 1)
        val local = lists?.names()
        val fromNames = names.flatMap { local?.named(it).orEmpty() }.filter(::fits).distinct()
        val fromAniList = aniList?.let { a ->
            names.firstNotNullOfOrNull { n -> timed { a.find(n, MediaKind.ANIME) }?.takeIf { t -> exact(t, n) } }
        }?.let { t ->
            val type = if (t.format == "MOVIE") AnimeType.MOVIE else AnimeType.of(t.format)
            AnimeEntry(type, anilist = t.id, episodes = t.episodes, year = t.year)
        }?.takeIf(::fits)
        // Two sources that agree, or one single exact candidate from a list that knows every name.
        val agreed = fromNames.filter { e -> fromAniList != null && e.agreesWith(fromAniList) }
        if (agreed.isNotEmpty()) return agreed
        if (fromAniList == null && fromNames.size == 1 && local != null) return fromNames
        if (fromAniList != null && fromNames.isEmpty() && local == null) {
            // No names list on this device (TV): AniList's exact name, confirmed by an id service that knows its id.
            val ids = timed { online("arm", "https://arm.haglund.dev/api/v2/ids?source=anilist&id=${fromAniList.anilist}", AnimeIdsParse::arm) }.orEmpty()
            return ids.filter { it.agreesWith(fromAniList) }.map { it.fill(fromAniList) }
        }
        return emptyList()
    }

    private fun exact(t: AniTitle, name: String) =
        listOfNotNull(t.english, t.romaji, t.native).any { AnimeIndex.plain(it) == AnimeIndex.plain(name) }

    private suspend fun fillFromKitsu(e: AnimeEntry): AnimeEntry {
        val kitsu = e.kitsu ?: return e
        val more = timed { onlineText("kitsu", "https://kitsu.app/api/edge/anime/$kitsu/mappings")?.let { AnimeIdsParse.kitsuMappings(kitsu, it) } }
        return if (more != null && (more.mal == e.mal || e.mal == null)) e.fill(more) else e
    }

    /** Works with the same name that are not this one: their year and platform keep their versions out. */
    private suspend fun homonyms(tmdbId: Int, info: TmdbInfo?): List<Homonym> = coroutineScope {
        info ?: return@coroutineScope emptyList()
        val names = listOfNotNull(info.title, info.originalTitle).map(AnimeIndex::plain).filter { it.length >= 3 }.distinct()
        val searched = names.map { n -> async { timed { tmdb.search(n) }.orEmpty() } }.flatMap { it.await() }
        note("homonym search " + searched.size)
        val found = searched.distinctBy { it.id to it.mediaType }
            .filter { it.id != tmdbId && (it.mediaType == "tv" || it.mediaType == "movie") }
            .filter { item -> listOfNotNull(item.title, item.originalTitle).map(AnimeIndex::plain).any { t -> names.any { t == it || t.startsWith("$it ") } } }
            .take(MAX_HOMONYMS)
        found.map { item ->
            async {
                val animated = ANIMATION in item.genreIds
                val networks = if (animated || item.mediaType != "tv") emptyList() else timed { tmdb.details("tv", item.id) }?.brands?.map { it.name }.orEmpty()
                Homonym(item.id, item.title, item.year, animated, networks)
            }
        }.map { it.await() }
    }

    /** Where an episode of the catalog is in each anime database. */
    suspend fun episode(identity: AnimeIdentity?, tmdbId: Int, season: Int, episode: Int): EpisodeRef {
        return EpisodeMapper.map(season, episode, seasonSizes(tmdbId, season), identity?.entries.orEmpty())
    }

    /**
     * The sizes of seasons 1 to [last], asked side by side (one after the other before 2026-11-24: ten seasons, ten
     * waits) and remembered for an hour; a size not known is asked again next time.
     */
    internal suspend fun seasonSizes(tmdbId: Int, last: Int): List<Int?> = coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(SEASONS_AT_ONCE)
        (1..last).map { s ->
            async {
                val key = "$tmdbId:$s"
                sizesKnown[key]?.takeIf { it.first != null && System.currentTimeMillis() - it.second < SIZE_MS }?.let { return@async it.first }
                gate.withPermit { timed { tmdb.seasonSize(tmdbId, s) } }.also { sizesKnown[key] = it to System.currentTimeMillis() }
            }
        }.awaitAll()
    }

    /** The length of an episode, from Kitsu, when the catalog does not tell it. */
    suspend fun episodeMinutes(ref: EpisodeRef): Int? {
        val kitsu = ref.kitsu?.id ?: return null
        return timed { onlineText("kitsu", "https://kitsu.app/api/edge/anime/$kitsu?fields%5Banime%5D=episodeLength")?.let(AnimeIdsParse::kitsuEpisodeMinutes) }
    }

    /** The releases the SeaDex community keeps for this anime. */
    suspend fun seaDex(anilistId: Int): List<SeaDexRelease> =
        timed { onlineText("seadex", "https://releases.moe/api/collections/entries/records?filter=(alID=$anilistId)&expand=trs", DAY_MS)?.let(AnimeExtrasParse::seaDex) }.orEmpty()

    /** The opening and ending of an episode, from AniSkip. */
    suspend fun skips(mal: EntryEpisode): List<SkipInterval> {
        val text = timed { onlineText("aniskip", "https://api.aniskip.com/v2/skip-times/${mal.id}/${mal.episode}?types=op&types=ed&episodeLength=0") }
        val found = text?.let(AnimeExtrasParse::aniSkip).orEmpty()
        note("aniskip ${mal.id}/${mal.episode}: " + (if (text == null) "no answer" else "${found.size} found"))
        return found
    }


    private suspend fun online(service: String, url: String, parse: (String) -> List<AnimeEntry>): List<AnimeEntry>? =
        onlineText(service, url)?.let(parse)

    private suspend fun onlineText(service: String, url: String, ttl: Long = WEEK_MS): String? =
        http.get(service, url.toHttpUrl(), url.substringAfter("://"), ttl, mapOf("User-Agent" to "SokoulTV/2.0"))?.toString()

    /**
     * A call with a time limit that holds even when the call blocks (the HTTP clients here do): it runs on its own, and
     * past the limit the lookup goes on without it, the call ending in the background.
     */
    private suspend fun <T> timed(block: suspend () -> T): T? = try {
        // The call keeps the caller's priority (the player's): it runs in its own scope, which would lose it.
        val priority = currentCoroutineContext()[RequestPriority] ?: RequestPriority.Normal
        val call = background.async(priority) { runCatching { block() }.getOrNull() }
        withTimeoutOrNull(CALL_MS) { call.await() }
    } catch (c: CancellationException) {
        throw c
    } catch (_: Exception) {
        null
    }

    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    private fun looksAnime(info: TmdbInfo) =
        info.originalLanguage?.take(2) in ASIAN && (info.genreIds.isEmpty() || ANIMATION in info.genreIds || info.genres.any { it.contains("nim", ignoreCase = true) })

    private companion object {
        // The lookup runs while the services are asked (20 s and more on the TV): a call may take its time.
        const val CALL_MS = 10_000L
        const val WEEK_MS = 7 * 24 * 3_600_000L
        const val DAY_MS = 24 * 3_600_000L
        const val MAX_HOMONYMS = 6
        const val SEASONS_AT_ONCE = 4
        const val SIZE_MS = 3_600_000L
        const val ANIMATION = 16
        val ASIAN = setOf("ja", "zh", "ko")
    }
}

/** How long "no identity" is believed before the services are asked again. */
private const val MISS_RETRY_MS = 5 * 60_000L
