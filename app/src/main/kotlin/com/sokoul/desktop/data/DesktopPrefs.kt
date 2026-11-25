package com.sokoul.desktop.data

// Réglages de lecture et de télévision de la tablette (`app/.../data/Settings.kt`), mêmes choix et mêmes libellés,
// rangés dans la table `settings` du PC.

enum class SubtitleSize(val label: String, val vlcRelFontSize: Int) { SMALL("Petite", 20), NORMAL("Normale", 16), LARGE("Grande", 12), HUGE("Très grande", 8) }
enum class SubtitleColor(val label: String, val vlcColor: Int) { WHITE("Blanc", 0xFFFFFF), YELLOW("Jaune", 0xFFFF00), CYAN("Cyan", 0x22D3EE), GREEN("Vert", 0x4ADE80) }

/** The accent of progress bars. WHITE is Sokoul's own look and the default. */
enum class AccentColor(val id: String, val argb: Long, val english: String, val french: String) {
    WHITE("white", 0xFFFFFFFF, "White", "Blanc"),
    RED("red", 0xFFFF453A, "Red", "Rouge"),
    BLUE("blue", 0xFF0A84FF, "Blue", "Bleu"),
    GREEN("green", 0xFF30D158, "Green", "Vert"),
    PURPLE("purple", 0xFFBF5AF2, "Purple", "Violet"),
    ORANGE("orange", 0xFFFF9F0A, "Orange", "Orange"),
    PINK("pink", 0xFFFF375F, "Pink", "Rose"),
    YELLOW("yellow", 0xFFFFD60A, "Yellow", "Jaune");

    companion object {
        fun of(id: String?): AccentColor = entries.firstOrNull { it.id == id } ?: WHITE
    }
}

/** The face of the subtitles Sokoul draws itself; [vlcName] is the family libVLC is asked for on the stream's own subtitles. */
enum class SubtitleFont(val id: String, val english: String, val french: String, val vlcName: String?) {
    STANDARD("standard", "Standard", "Standard", null),
    ATKINSON("atkinson", "Atkinson Hyperlegible", "Atkinson Hyperlegible", null),
    SYSTEM("system", "System", "Système", "Arial"),
    SERIF("serif", "Serif", "Serif", "Times New Roman"),
    MONOSPACE("monospace", "Monospace", "Chasse fixe", "Consolas");

    companion object {
        fun of(id: String?): SubtitleFont = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

/** How the picture fills the screen: fit, fill or stretch. */
enum class VideoFormat { FIT, FILL, STRETCH }
enum class SubtitleBackground(val label: String) { OUTLINE("Contour"), BOX("Fond noir"), NONE("Aucun") }
enum class SubtitlePosition(val label: String, val marginPx: Int) { LOW("En bas", 0), HIGH("Plus haut", 120) }
enum class BufferProfile(val label: String, val liveMs: Int, val vodMs: Int) {
    LOW("Faible (zapping rapide)", 800, 3000), NORMAL("Normal", 1500, 8000), HIGH("Élevé (connexion instable)", 4000, 15000)
}

/** What happens when the best version is not ready yet. */
enum class StartMode { ReadyFirst, WaitBest, Ask }
/** The audio choice before languages could be ordered; read once to build an [AudioIntent], never written again. */
enum class AudioPreference { System, Vf, Multi, Vostfr, Vo }
enum class SubtitlePreference { System, Off }

/** What to do when a file has none of the wanted languages: let the engine's track play, or ask the viewer. */
enum class AudioFallback { EngineDefault, Ask }

/**
 * The audio the viewer wants, in any language: ISO 639-1 codes in order of preference, where [ORIGINAL] stands for the
 * work's own language (from the catalog). It only orders versions and tracks; nothing is ever hidden because of it.
 * @property subtitles [SUBTITLES_OFF], [SUBTITLES_AUTO] (the interface language when the audio heard is another one), or a code
 */
data class AudioIntent(
    val languages: List<String> = listOf(ORIGINAL),
    val fallback: AudioFallback = AudioFallback.EngineDefault,
    val subtitles: String = SUBTITLES_AUTO,
) {
    /** The codes to look for, "original" replaced by the work's language when the catalog knows it. */
    fun resolved(workLanguage: String?): List<String> =
        languages.map { if (it == ORIGINAL) workLanguage?.lowercase()?.take(2)?.takeIf { w -> w.isNotBlank() } ?: ORIGINAL else it }.distinct()

    fun encode(): String = languages.joinToString(",")

    companion object {
        const val ORIGINAL = "original"
        const val SUBTITLES_OFF = "off"
        const val SUBTITLES_AUTO = "auto"

        fun decode(raw: String?): List<String>? = raw?.split(',')?.map { it.trim().lowercase() }
            ?.filter { it == ORIGINAL || Regex("[a-z]{2}").matches(it) }?.distinct()?.takeIf { it.isNotEmpty() }

        /** The intent an earlier preference meant ([AudioPreference] and [SubtitlePreference]), for the migration. */
        fun fromLegacy(audio: AudioPreference, subtitles: SubtitlePreference, systemLanguage: String): AudioIntent {
            val system = systemLanguage.lowercase().take(2).ifBlank { "en" }
            val languages = when (audio) {
                AudioPreference.Vf, AudioPreference.Multi -> listOf("fr", ORIGINAL)
                AudioPreference.Vo, AudioPreference.Vostfr -> listOf(ORIGINAL)
                AudioPreference.System -> listOf(system, ORIGINAL)
            }
            val subs = when {
                subtitles == SubtitlePreference.Off -> SUBTITLES_OFF
                audio == AudioPreference.Vostfr -> "fr"
                else -> SUBTITLES_AUTO
            }
            return AudioIntent(languages.distinct(), AudioFallback.EngineDefault, subs)
        }
    }
}
enum class MaxQuality(val height: Int) { P720(720), P1080(1080), P2160(2160) }
enum class MaxSize(val bytes: Long) { G10(10_000_000_000L), G25(25_000_000_000L), G50(50_000_000_000L), None(Long.MAX_VALUE) }
enum class PreferredOrigin { Automatic, Debrid, Iptv }

/** What the player's logic reads from the settings when it starts a title. */
data class PlaybackPrefs(
    val startMode: StartMode = StartMode.ReadyFirst,
    val audio: AudioPreference = AudioPreference.System,
    val subtitles: SubtitlePreference = SubtitlePreference.System,
    val maxQuality: MaxQuality = MaxQuality.P2160,
    val maxSize: MaxSize = MaxSize.G25,
    val origin: PreferredOrigin = PreferredOrigin.Automatic,
    /** The languages wanted, in order; [audio] and [subtitles] only remain to read older settings. */
    val intent: AudioIntent = AudioIntent.fromLegacy(audio, subtitles, java.util.Locale.getDefault().language),
    /** The sharpest picture first (4K, HDR and Dolby Vision included) instead of SDR first on a PC. */
    val bestQualityFirst: Boolean = false,
)

/** Ce que le lecteur lit des réglages au moment d'ouvrir un titre. */
data class PlayerPrefs(
    /** The wanted audio languages in order; [AudioIntent.ORIGINAL] is resolved with the stream's work language. */
    val audioLanguages: List<String> = listOf("fr", AudioIntent.ORIGINAL),
    val subtitleFont: SubtitleFont = SubtitleFont.STANDARD,
    val subtitleLanguage: String = "",
    val subtitleSize: SubtitleSize = SubtitleSize.NORMAL,
    val subtitleColor: SubtitleColor = SubtitleColor.WHITE,
    val subtitleBackground: SubtitleBackground = SubtitleBackground.OUTLINE,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.LOW,
    val buffer: BufferProfile = BufferProfile.NORMAL,
) {
    /** Options libVLC du média pour l'apparence des sous-titres (appliquées dès le titre suivant). */
    fun subtitleOptions(): List<String> = buildList {
        add(":freetype-rel-fontsize=${subtitleSize.vlcRelFontSize}")
        add(":freetype-color=${subtitleColor.vlcColor}")
        subtitleFont.vlcName?.let { add(":freetype-font=$it") }
        when (subtitleBackground) {
            SubtitleBackground.OUTLINE -> { add(":freetype-outline-thickness=4"); add(":freetype-background-opacity=0") }
            SubtitleBackground.BOX -> { add(":freetype-outline-thickness=0"); add(":freetype-background-opacity=160"); add(":freetype-background-color=0") }
            SubtitleBackground.NONE -> { add(":freetype-outline-thickness=0"); add(":freetype-background-opacity=0"); add(":freetype-shadow-opacity=160") }
        }
        if (subtitlePosition.marginPx > 0) add(":sub-margin=${subtitlePosition.marginPx}")
    }
}

/** Réglages typés du PC (lecture, télévision et guide) ; une valeur absente = celle de la tablette par défaut. */
class DesktopPrefs(private val db: DesktopDb) {
    private inline fun <reified E : Enum<E>> enum(key: String, default: E): E =
        db.setting(key)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

    var subtitleLanguage: String
        get() = db.setting(K_SUBS).orEmpty()
        set(v) = db.putSetting(K_SUBS, v.ifBlank { null })
    var subtitleSize: SubtitleSize
        get() = enum(K_SUB_SIZE, SubtitleSize.NORMAL)
        set(v) = db.putSetting(K_SUB_SIZE, v.name)
    var subtitleColor: SubtitleColor
        get() = enum(K_SUB_COLOR, SubtitleColor.WHITE)
        set(v) = db.putSetting(K_SUB_COLOR, v.name)
    var subtitleBackground: SubtitleBackground
        get() = enum(K_SUB_BG, SubtitleBackground.OUTLINE)
        set(v) = db.putSetting(K_SUB_BG, v.name)
    var subtitlePosition: SubtitlePosition
        get() = enum(K_SUB_POS, SubtitlePosition.LOW)
        set(v) = db.putSetting(K_SUB_POS, v.name)
    var autoNext: Boolean
        get() = db.setting(K_AUTONEXT) != "0"
        set(v) = db.putSetting(K_AUTONEXT, if (v) "1" else "0")
    var buffer: BufferProfile
        get() = enum(K_BUFFER, BufferProfile.NORMAL)
        set(v) = db.putSetting(K_BUFFER, v.name)
    var userAgent: String
        get() = db.setting(Settings.USER_AGENT) ?: Settings.DEFAULT_USER_AGENT
        set(v) = db.putSetting(Settings.USER_AGENT, v.trim().ifBlank { null })
    var resumeLastChannel: Boolean
        get() = db.setting(K_START) == "1"
        set(v) = db.putSetting(K_START, if (v) "1" else "0")
    var zapInverted: Boolean
        get() = db.setting(K_ZAP) == "1"
        set(v) = db.putSetting(K_ZAP, if (v) "1" else "0")
    var epgOffsetMinutes: Int
        get() = db.setting(Settings.EPG_OFFSET)?.toIntOrNull() ?: 0
        set(v) = db.putSetting(Settings.EPG_OFFSET, v.toString())
    var lastChannel: String?
        get() = db.setting(K_LAST_CHANNEL)
        set(v) = db.putSetting(K_LAST_CHANNEL, v)

    /** Address of the watch party server (API/watchpartyAPI). */
    var partyServer: String
        // On the PC the server usually runs beside the app; on a TV "localhost" is the TV itself, so it starts with only the scheme.
        get() = db.setting(K_PARTY_SERVER) ?: if (com.sokoul.pc.Platform.IS_TV) "http://" else "http://localhost:25566"
        set(v) = db.putSetting(K_PARTY_SERVER, v.ifBlank { null })

    /** The name shown to the others in a watch party. */
    var partyNickname: String
        get() = db.setting(K_PARTY_NICK) ?: com.sokoul.pc.data.Profiles.defaultName().take(24)
        set(v) = db.putSetting(K_PARTY_NICK, v.take(24).ifBlank { null })

    /** Minutes without input before the screensaver; 0 turns it off. */
    var screensaverMinutes: Int
        get() = db.setting(K_SCREENSAVER)?.toIntOrNull() ?: 5
        set(v) = db.putSetting(K_SCREENSAVER, v.toString())

    /** Interface language: "auto" (the system's), "fr" or "en". */
    var language: String
        get() = db.setting(K_LANGUAGE) ?: "auto"
        set(v) = db.putSetting(K_LANGUAGE, v.takeIf { it != "auto" })

    var accent: AccentColor
        get() = AccentColor.of(db.setting(K_ACCENT))
        set(v) = db.putSetting(K_ACCENT, v.id)

    var subtitleFont: SubtitleFont
        get() = SubtitleFont.of(db.setting(K_SUB_FONT))
        set(v) = db.putSetting(K_SUB_FONT, v.id)

    /** Ask who is watching at start, when there is more than one profile ("Ne plus demander" turns it off). */
    var profilePrompt: Boolean
        get() = db.setting(K_PROFILE_PROMPT) != "0"
        set(v) = db.putSetting(K_PROFILE_PROMPT, if (v) "1" else "0")

    /** Entries of the top menu the user hid (section ids). Settings always stays. */
    var railHidden: Set<String>
        get() = db.setting(K_RAIL_HIDDEN)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        set(v) = db.putSetting(K_RAIL_HIDDEN, v.sorted().joinToString(",").ifEmpty { null })

    /** The home page's own order and hidden rows, as JSON (see HomeLayout). */
    var homeRowsJson: String?
        get() = db.setting(K_HOME_ROWS)
        set(v) = db.putSetting(K_HOME_ROWS, v)

    /** The display settings back to what they were at first: accent, menu, home rows, subtitle style and screensaver. */
    fun resetDisplay() {
        accent = AccentColor.WHITE
        railHidden = emptySet()
        homeRowsJson = null
        resetSubtitleStyle()
        screensaverMinutes = 5
    }

    /** Back to the defaults of the subtitle style. */
    fun resetSubtitleStyle() {
        subtitleSize = SubtitleSize.NORMAL
        subtitleColor = SubtitleColor.WHITE
        subtitleBackground = SubtitleBackground.OUTLINE
        subtitlePosition = SubtitlePosition.LOW
        subtitleFont = SubtitleFont.STANDARD
    }

    /** The picture format chosen in the player, kept for every title. */
    var videoFormat: VideoFormat
        get() = enum(K_VIDEO_FORMAT, VideoFormat.FIT)
        set(v) = db.putSetting(K_VIDEO_FORMAT, v.name)

    fun player() = PlayerPrefs(audioIntent.languages, subtitleFont, subtitleLanguage, subtitleSize, subtitleColor, subtitleBackground, subtitlePosition, buffer)

    /** The sound level, kept between sessions. */
    var volume: Int
        get() = db.setting(K_VOLUME)?.toIntOrNull() ?: 100
        set(v) = db.putSetting(K_VOLUME, v.toString())

    /** The muted picture of the focused channel. It holds one of the account's connections while it shows. */
    /** Trailers that start by themselves on pages. The button still plays them. */
    var autoTrailers: Boolean
        get() = (db.setting("pc.auto_trailers")?.let { it != "off" } ?: !com.sokoul.pc.Platform.IS_TV) && System.getenv("SOKOUL_NO_TRAILERS").isNullOrBlank()
        set(v) = db.putSetting("pc.auto_trailers", if (v) "on" else "off")
    /** The sound of a trailer behind a title page. Off until asked for. */
    var heroSound: Boolean
        get() = db.setting("pc.hero_sound") == "on"
        set(v) = db.putSetting("pc.hero_sound", if (v) "on" else "off")
    var livePreview: Boolean
        get() = db.setting(K_LIVE_PREVIEW) != "off"
        set(v) = db.putSetting(K_LIVE_PREVIEW, if (v) "on" else "off")
    var startMode: StartMode
        get() = enum(K_START_MODE, StartMode.ReadyFirst)
        set(v) = db.putSetting(K_START_MODE, v.name)
    var audioPreference: AudioPreference
        get() = enum(K_AUDIO_PREF, AudioPreference.System)
        set(v) = db.putSetting(K_AUDIO_PREF, v.name)
    var subtitlePreference: SubtitlePreference
        get() = enum(K_SUB_PREF, SubtitlePreference.System)
        set(v) = db.putSetting(K_SUB_PREF, v.name)
    var maxQuality: MaxQuality
        get() = enum(K_MAX_QUALITY, MaxQuality.P2160)
        set(v) = db.putSetting(K_MAX_QUALITY, v.name)
    var maxSize: MaxSize
        get() = enum(K_MAX_SIZE, MaxSize.G25)
        set(v) = db.putSetting(K_MAX_SIZE, v.name)
    var preferredOrigin: PreferredOrigin
        get() = enum(K_ORIGIN, PreferredOrigin.Automatic)
        set(v) = db.putSetting(K_ORIGIN, v.name)

    /**
     * The audio wanted, in any language. Read from its own settings; the first time, built from the earlier VF/VO/VOSTFR
     * choice and saved, the earlier keys being left as they were.
     */
    var audioIntent: AudioIntent
        get() {
            val languages = AudioIntent.decode(db.setting(K_AUDIO_LANGUAGES))
                ?: return AudioIntent.fromLegacy(audioPreference, subtitlePreference, com.sokoul.pc.i18n.Strings.language().code).also { audioIntent = it }
            return AudioIntent(languages, enum(K_AUDIO_FALLBACK, AudioFallback.EngineDefault), db.setting(K_SUBTITLE_MODE) ?: AudioIntent.SUBTITLES_AUTO)
        }
        set(v) {
            db.putSetting(K_AUDIO_LANGUAGES, v.encode())
            db.putSetting(K_AUDIO_FALLBACK, v.fallback.name)
            db.putSetting(K_SUBTITLE_MODE, v.subtitles)
        }

    var searchServices: Set<com.sokoul.iptv.data.debrid.SourceAddon>
        get() = db.setting(DesktopDebrid.SERVICES)?.let(com.sokoul.iptv.data.debrid.DebridAddons::parse)
            ?: com.sokoul.iptv.data.debrid.SourceAddon.entries.toSet()
        set(value) {
            if (value.isNotEmpty()) db.putSetting(DesktopDebrid.SERVICES, value.joinToString(",") { it.name })
        }

    fun playback() = PlaybackPrefs(startMode, audioPreference, subtitlePreference, maxQuality, maxSize, preferredOrigin, audioIntent, bestQualityFirst)

    /**
     * Off by default on the PC: on an SDR screen an HDR stream through libVLC looks washed out, so 1080p SDR comes first.
     * On by default on the TV, which says itself what it shows (DeviceCaps): a 4K set got the 1080p found first.
     */
    var bestQualityFirst: Boolean
        get() = db.setting("play.best_quality_first")?.let { it == "true" } ?: com.sokoul.pc.Platform.IS_TV
        set(value) = db.putSetting("play.best_quality_first", value.toString())

    var rememberSourceHosters: Boolean
        get() = db.setting("source.rememberHosters") != "false"
        set(value) = db.putSetting("source.rememberHosters", value.toString())

    fun sourceOrder(anime: Boolean): com.sokoul.pc.playback.sources.SourcePreferenceOrder {
        val prefix = if (anime) "source.anime" else "source.moviesTv"
        val defaults = com.sokoul.pc.playback.sources.SourcePreferences
        val remembered = if (rememberSourceHosters) defaults.SOURCES.mapNotNull { source ->
            db.setting("$prefix.remembered.$source")?.takeIf { it in defaults.HOSTERS }?.let { source to it }
        }.toMap() else emptyMap()
        return com.sokoul.pc.playback.sources.SourcePreferenceOrder(
            defaults.normalize(db.setting("$prefix.sources").orEmpty().split(','), defaults.SOURCES),
            defaults.normalize(db.setting("$prefix.hosters").orEmpty().split(','), defaults.HOSTERS), remembered,
        )
    }

    fun setSourceOrder(anime: Boolean, sources: List<String>, hosters: List<String>) {
        val prefix = if (anime) "source.anime" else "source.moviesTv"
        val defaults = com.sokoul.pc.playback.sources.SourcePreferences
        db.putSetting("$prefix.sources", defaults.normalize(sources, defaults.SOURCES).joinToString(","))
        db.putSetting("$prefix.hosters", defaults.normalize(hosters, defaults.HOSTERS).joinToString(","))
    }

    fun rememberSourceHoster(version: com.sokoul.pc.playback.session.PlayVersion) {
        if (!rememberSourceHosters || version.rejected != null) return
        val defaults = com.sokoul.pc.playback.sources.SourcePreferences
        val source = defaults.source(version) ?: return
        val hoster = defaults.hoster(version) ?: return
        val prefix = if (defaults.anime(version)) "source.anime" else "source.moviesTv"
        db.putSetting("$prefix.remembered.$source", hoster)
    }

    /** What the players need: the wanted audio languages in order, and the subtitle mode ("off", "auto" or a code). */
    fun engine(): PlayerPrefs {
        val intent = audioIntent
        return player().copy(audioLanguages = intent.languages, subtitleLanguage = intent.subtitles)
    }
    companion object {
        const val K_START_MODE = "play.start_mode"
        private const val K_LIVE_PREVIEW = "pc.live_preview"
        private const val K_VOLUME = "pc.volume"
        const val K_AUDIO_PREF = "play.audio_pref"
        const val K_SUB_PREF = "play.sub_pref"
        const val K_AUDIO_LANGUAGES = "play.audio_languages"
        const val K_AUDIO_FALLBACK = "play.audio_fallback"
        const val K_SUBTITLE_MODE = "play.subtitle_mode"
        const val K_MAX_QUALITY = "play.max_quality"
        const val K_MAX_SIZE = "play.max_size"
        const val K_ORIGIN = "play.origin"
        const val K_AUDIO = "play.audio"
        const val K_SUBS = "play.subs"
        const val K_SUB_SIZE = "sub.size"
        const val K_SUB_COLOR = "sub.color"
        const val K_SUB_BG = "sub.bg"
        const val K_SUB_POS = "sub.pos"
        const val K_VIDEO_FORMAT = "play.video_format"
        const val K_ACCENT = "ui.accent"
        const val K_LANGUAGE = "ui.language"
        const val K_SUB_FONT = "sub.font"
        const val K_PROFILE_PROMPT = "profile.prompt"
        const val K_RAIL_HIDDEN = "ui.rail_hidden"
        const val K_HOME_ROWS = "ui.home_rows"
        const val K_SCREENSAVER = "pc.screensaver_minutes"
        const val K_PARTY_SERVER = "party.server"
        const val K_PARTY_NICK = "party.nickname"
        const val K_AUTONEXT = "play.autonext"
        const val K_BUFFER = "play.cache"
        const val K_START = "tv.start"
        const val K_ZAP = "tv.zap"
        const val K_LAST_CHANNEL = "tv.last_channel"

        /** Langues proposées (celles de la tablette). */
        val languages = listOf(
            "fr" to "Français", "en" to "Anglais", "ar" to "Arabe", "es" to "Espagnol", "de" to "Allemand",
            "it" to "Italien", "pt" to "Portugais", "tr" to "Turc", "nl" to "Néerlandais",
        )

        fun languageName(code: String) = languages.firstOrNull { it.first == code }?.second ?: code.ifBlank { "Automatique" }

        fun offsetLabel(min: Int) = when {
            min == 0 -> "Aucun"
            min > 0 -> "+${min / 60} h"
            else -> "−${-min / 60} h"
        }

        /** Libellé du sens du zapping (clavier : Page préc. / Page suiv.). */
        fun zapLabel(inverted: Boolean) = if (inverted) "Page préc. : chaîne suivante · Page suiv. : précédente" else "Page préc. : chaîne précédente · Page suiv. : suivante"

        /** Pas de zapping selon le sens choisi : Page préc. = −1 par défaut. */
        fun zapStep(delta: Int, inverted: Boolean) = if (inverted) -delta else delta

        /** Heure du guide décalée du réglage (programmes du fournisseur mal calés). */
        fun shiftGuide(t: Long, offsetMinutes: Int) = t + offsetMinutes * 60_000L
    }
}
