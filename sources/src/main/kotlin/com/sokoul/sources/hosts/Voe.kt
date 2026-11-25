package com.sokoul.sources.hosts

import com.sokoul.sources.LinkFormat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl

/** Voe: an obfuscated JSON player setup, whose marker list ships in the player bundle (see HOSTS.md). */
class Voe(private val fetcher: PageFetcher) : HostExtractor {
    override val host = "Voe"

    override fun handles(url: HttpUrl): Boolean {
        val name = HostUrls.nameOf(url)
        return name == "voe" || unblock.matches(name) || name in ALIASES || url.host.lowercase().split('.').dropLast(1).contains("voe")
    }

    override suspend fun extract(embedUrl: HttpUrl): HostResult = hostResult {
        val asked = mapOf("User-Agent" to BROWSER_UA, "Referer" to "https://voe.sx/")
        val page = fetcher.followHops(fetcher.get(embedUrl, asked), asked) { jsonScript.containsMatchIn(it.text) }
        val config = encryptedConfig(page, asked)
        val address = config?.let { streamOf(it) } ?: plainStream(page.text) ?: fail(HostFailure.NotFound)
        media(address, PLAYBACK, LinkFormat.File)
    }

    /** The decoded setup of an obfuscated page, with the markers of its own bundle (or the older ones). */
    private suspend fun encryptedConfig(page: Page, asked: Map<String, String>): JsonObject? {
        val payload = payloadPattern.find(page.text)
        if (payload != null) {
            val markers = page.url.resolve(payload.groupValues[2])?.let { bundle ->
                try {
                    markersIn(fetcher.get(bundle, asked + ("Referer" to page.url.toString())).text)
                } catch (e: HostError) {
                    null
                }
            }
            return markers?.let { decode(payload.groupValues[1], it) } ?: decode(payload.groupValues[1], OLD_MARKERS)
        }
        val lone = jsonArrayScript.find(page.text)?.groupValues?.get(1) ?: return null
        return decode(lone, OLD_MARKERS)
    }

    private fun streamOf(config: JsonObject): String? {
        val found = listOf("source", "file", "direct_access_url").mapNotNull { key ->
            (config[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.startsWith("http") }
        }
        return found.firstOrNull(MediaText::isPlaylist) ?: found.firstOrNull()
    }

    private fun plainStream(text: String): String? = listOf("hls", "mp4").firstNotNullOfOrNull { key ->
        MediaText.stringField(text, key)?.takeIf { it.startsWith("http") }
    }

    internal companion object {
        private val PLAYBACK = mapOf("Referer" to "https://voe.sx/", "Origin" to "https://voe.sx", "User-Agent" to BROWSER_UA)
        private val OLD_MARKERS = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
        private val jsonScript = Regex("""<script[^>]*type\s*=\s*["']\s*application/json""", RegexOption.IGNORE_CASE)
        private val payloadPattern = Regex(
            """application/json["']?\s*>\s*\[\s*"([^"]+)"\s*]\s*</script>\s*<script[^>]*\ssrc\s*=\s*["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val jsonArrayScript = Regex("""application/json["']?[^>]*>\s*\[\s*"([^"]{40,})"\s*]""", RegexOption.IGNORE_CASE)
        private val markerArray = Regex("""\[\s*'[^\w\s']{2}'(?:\s*,\s*'[^\w\s']{2}'){0,11}\s*]""")
        private val unblock = Regex("""^(?:v-?o-?e-?)?un-?bl[o0]?c?k\d{0,2}(?:-?voe)?$""")

        /** The first array of two-symbol strings in the player bundle: the markers of the day. */
        internal fun markersIn(bundle: String): List<String>? =
            markerArray.find(bundle)?.value?.let { array -> Regex("""'([^']{2})'""").findAll(array).map { it.groupValues[1] }.toList() }

        /** ROT13, markers out, Base64, every code − 3, reversed, Base64, JSON. Null when any step does not hold. */
        internal fun decode(payload: String, markers: List<String>): JsonObject? {
            var text = rot13(payload)
            for (marker in markers) text = text.replace(marker, "")
            val first = Codecs.base64(text) ?: return null
            val shifted = String(CharArray(first.size) { ((first[it].toInt() and 0xFF) - 3).toChar() }).reversed()
            val second = Codecs.base64(shifted) ?: return null
            return runCatching { Json.parseToJsonElement(second.decodeToString()) as? JsonObject }.getOrNull()
        }

        private fun rot13(text: String): String = buildString(text.length) {
            for (c in text) append(
                when (c) {
                    in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    else -> c
                },
            )
        }

        /** Voe's throw-away domain names (the part before the TLD), which rotate about monthly. */
        val ALIASES = setOf(
            "19turanosephantasia", "20demidistance9elongations", "30sensualizeexpression", "321naturelikefurfuroid",
            "35volitantplimsoles5", "449unceremoniousnasoseptal", "745mingiestblissfully", "adrianmissionminute",
            "alleneconomicmatter", "antecoxalbobbing1010", "anthonysaline", "apinchcaseation", "audaciousdefaulthouse",
            "auraleanline", "availedsmallest", "bigclatterhomesguideservice", "boonlessbestselling244", "bradleyviewdoctor",
            "brittneystandardwestern", "brucevotewithin", "caseyimpactstation", "charlestoughrace", "christopheruntilpoint",
            "chromotypic", "chuckle-tube", "cindyeyefinal", "claudiosepulchral", "conscientiousedu", "counterclockwisejacky",
            "crownmakermacaronicism", "crystaltreatmenteast", "cyamidpulverulence530", "dianaavoidthey", "diananatureforeign",
            "donaldlineelse", "edwardarriveoften", "effortlessexperim", "ellenpoliticalfollow", "erikcoldperson", "figeterpiazine",
            "fittingcentermondaysunday", "fraudclatterflyingcar", "gamoneinterrupted", "garylargeavailable", "generatesnitrosate",
            "goofy-banana", "graceaddresscommunity", "greaseball6eventual20", "guidon40hyporadius9", "heatherdiscussionwhen",
            "housecardsummerbutton", "ianrequireadult", "jamessoundcost", "jamiesamewalk", "jasminetesttry", "jayservicestuff",
            "jeanprofessorcentral", "jefferycontrolmodel", "jennifercertaindevelopment", "jennifereconomicgive", "jessicachoosemake",
            "jessicayeahcatch", "jilliandescribecompany", "johnalwayssame", "johnbeyondnation", "jonathansociallike",
            "josephseveralconcern", "juliewomanwish", "kathleenmemberhistory", "kellywhatcould", "kennethofficialitem", "kinoger",
            "kristiesoundsimply", "lancewhosedifficult", "launchreliantcleaverriver", "lauradaydo", "letsupload", "lisatrialidea",
            "loriwithinfamily", "lukecomparetwo", "lukesitturn", "mariatheserepublican", "marissasharecareer", "matriculant401merited",
            "matthewhotelscience", "maxfinishseveral", "metagnathtuggers", "michaelapplysome", "mikaylaarealike", "nathanfromsubject",
            "nectareousoverelate", "nonesnanking", "ogladaj", "pamelachangemission", "paulkitchendark", "preferciseaccurate",
            "prepareddare", "ralphysuccessfull", "realfinanceblogcenter", "rebeccaneverbase", "rebeccapracticeloss",
            "reputationsheriffkennethsand", "richardsignfish", "roberteachfinal", "robertordercharacter", "robertplacespace",
            "sandratableother", "sandrataxeight", "scatch176duplicities", "sethniceletter", "shannonpersonalcost",
            "simpulumlamerop", "smoki", "stevenfamilyedge", "stevenimaginelittle", "strawberriesporail", "telyn610zoanthropy",
            "timberwoodanotia", "timmaybealready", "toddpartneranimal", "toxitabellaeatrebates306", "tracylocalschool",
            "uptodatefinishconferenceroom", "valeronevijao", "walterprettytheir", "wolfdyslectic", "yodelswartlike",
            "claireguessthousand", "derricknationtop", "jamesbornmain", "jeremyparticipantanything", "megankiddifference",
            "teresapoliticallearn",
        )
    }
}
