package com.sokoul.pc.playback.sources
import com.sokoul.desktop.data.AudioIntent
import com.sokoul.desktop.data.PlaybackPrefs
import com.sokoul.desktop.player.AudioChoice
import com.sokoul.iptv.domain.AudioLanguage
import com.sokoul.pc.playback.session.PlayVersion

/** How much is known of a version's audio: a past viewing heard it, its name declares it, its label implies it, or nothing. */
enum class AudioProof { Observed, Declared, Implied, Unknown }

/**
 * What a version's audio is worth against the viewer's wanted languages.
 * @property codes the languages the version may hold (ISO 639-1), as far as [proof] goes
 * @property heard twice the place in the wanted list of the first language it holds (a probable French, in a MULTI that
 *   does not name it, counts as French); then twice the list's size when nothing tells, and one more when
 *   it says what it holds and nothing wanted is in it
 * @property matches whether it holds, or probably holds, a wanted language
 * @property original whether it holds the work's language, when that can be told
 * @property dubLast 1 for a foreign dub of unknown language when the original is wanted and the work's language is unknown
 */
data class AudioVerdict(
    val codes: Set<String>,
    val proof: AudioProof,
    val heard: Int,
    val matches: Boolean,
    val original: Boolean?,
    val foreignDub: Boolean,
    val dubLast: Int,
    /** The old VF/MULTI/VOSTFR/VO label, kept for display and filters. */
    val language: AudioLanguage?,
)

/**
 * The one judgement of a version's audio and the one choice of an audio track, for any language, shared by the ranking,
 * the source preferences, the upgrade offer, the session's language checks and the PC and TV players. Pure and tested.
 *
 * "Original" is the work's own language from the catalog. Only when it is unknown do the file's hints count (a track named
 * "Original", the other track of a MULTI, a "Dual Audio" release), as introduced by PRs #5 and #6.
 */
object AudioMatch {
    private val ORIGINAL = AudioIntent.ORIGINAL

    /** "Dual Audio", or two other languages named ("[spa, eng]", "ITA.ENG", flags): a dub beside something else. */
    fun foreignDub(v: PlayVersion) = v.foreignDub || v.announcedAudio.count { it != "FR" } >= 2

    /** The languages a version may hold and how sure that is. A past viewing replaces what the name says. */
    fun languagesOf(v: PlayVersion, fact: SourceFact?, workLanguage: String?): Pair<Set<String>, AudioProof> {
        val work = workLanguage?.lowercase()?.take(2)?.takeIf { it.isNotBlank() }
        fact?.codes?.takeIf { it.isNotEmpty() }?.let { return it.map(String::lowercase).toSet() to AudioProof.Observed }
        val declared = v.announcedAudio.map(String::lowercase).toSet()
        // What the French scene's labels imply: VF is French, VO/VOSTFR the work's language, MULTI both.
        val label = fact?.language ?: v.language
        val implied = when (label) {
            AudioLanguage.VF -> setOf("fr")
            AudioLanguage.VO, AudioLanguage.VOSTFR -> setOfNotNull(work)
            // A "Dual Audio" MULTI that names no French is a dub beside the original (anime: English and Japanese).
            AudioLanguage.MULTI -> if (v.foreignDub && "fr" !in declared) setOfNotNull(work) else setOfNotNull("fr", work)
            null -> emptySet()
        }
        // A file that declares its languages without the work's one is no original, whatever its label or an old verdict said.
        val trusted = if (declared.isNotEmpty() && work != null && work !in declared) implied - work else implied
        return when {
            declared.isNotEmpty() -> (declared + trusted) to AudioProof.Declared
            trusted.isNotEmpty() -> trusted to AudioProof.Implied
            else -> emptySet<String>() to AudioProof.Unknown
        }
    }

    fun verdict(v: PlayVersion, prefs: PlaybackPrefs, frenchSystem: Boolean = false, fact: SourceFact? = null, workLanguage: String? = null): AudioVerdict =
        verdict(v, prefs.intent, fact, workLanguage)

    fun verdict(v: PlayVersion, intent: AudioIntent, fact: SourceFact?, workLanguage: String?): AudioVerdict {
        val work = workLanguage?.lowercase()?.take(2)?.takeIf { it.isNotBlank() }
        val wanted = intent.resolved(work)
        val (codes, proof) = languagesOf(v, fact, work)
        val dub = foreignDub(v)
        val label = (fact?.language ?: v.language)
        fun holds(entry: String): Boolean = when {
            entry != ORIGINAL -> entry in codes
            // The work's language unknown: the labels that promise the original, never a dub.
            else -> !dub && label in setOf(AudioLanguage.VO, AudioLanguage.VOSTFR, AudioLanguage.MULTI)
        }
        val index = wanted.indexOfFirst(::holds)
        val unknownRank = 2 * wanted.size
        // Without the work's language, a release naming one language that is no dub may well be the original.
        val mayBeOriginal = work == null && ORIGINAL in wanted && !dub && proof != AudioProof.Unknown
        val heard = when {
            // A probable French (a MULTI that does not name it) ranks as French: the picture decides between them, and
            // the declared one only wins at the same picture (proof, in the ranking's last steps). Ranked after, a 442 MB
            // SD VF played before a 1080p Blu-ray MULTI, then IPTV at 720x406 (Spartacus on the TV, 2026-11-19). A MULTI
            // without French is still left before it starts, its tracks heard with the sound held.
            index >= 0 -> 2 * index
            proof == AudioProof.Unknown || mayBeOriginal -> unknownRank
            // It says what it holds, and nothing wanted is in it: after those that may still have it.
            else -> unknownRank + 1
        }
        val original = work?.let { if (proof == AudioProof.Unknown) null else it in codes }
        val dubLast = if (work == null && ORIGINAL in wanted && label == null && dub) 1 else 0
        return AudioVerdict(codes, proof, heard, index >= 0, original, dub, dubLast, label)
    }

    /** Whether the wanted languages ask for something other than the original alone (a dub in a given language). */
    fun wantsLanguage(intent: AudioIntent): Boolean = intent.languages.any { it != ORIGINAL }

    private val commentary = Regex("""(?i)\b(comment\w*|description|descriptive|audiodescription|ad)\b""")
    private val originalLabel = Regex("""\b(original|originale|VO|VOST)\b""", RegexOption.IGNORE_CASE)

    private val quebec = Regex("""fr[-_]ca\b|\bvfq\b|qu[ée]b[ée]c|canad""", RegexOption.IGNORE_CASE)

    private fun main(tracks: List<Pair<Int, String>>) = tracks.filterNot { commentary.containsMatchIn(it.second) }.ifEmpty { tracks }

    /** The language of a track's label (ISO 639-1), or null when the label names none. */
    fun trackLanguage(label: String): String? = AudioChoice.languageOf(label)

    /**
     * The audio track to select among [tracks] (id, label), or null to keep the engine's own choice.
     * @param wanted the wanted languages in order, [AudioIntent.ORIGINAL] standing for the work's language
     * @param workLanguage the work's original language (ISO 639-1), when the catalog knows it
     */
    fun pickTrack(tracks: List<Pair<Int, String>>, wanted: List<String>, workLanguage: String? = null): Int? {
        val main = main(tracks)
        val work = workLanguage?.lowercase()?.take(2)?.takeIf { it.isNotBlank() }
        val firstLanguage = wanted.firstOrNull { it != ORIGINAL }
        for (entry in wanted) {
            if (entry != ORIGINAL) {
                val inLanguage = main.filter { AudioChoice.matches(entry, it.second) }
                // France's French before Quebec's when both are there: Apple lists fr-CA first in an Apple TV+ trailer,
                // and it was the one heard (2026-11-24). A VFQ alone is still taken.
                (inLanguage.firstOrNull { !quebec.containsMatchIn(it.second) } ?: inLanguage.firstOrNull())?.let { return it.first }
                continue
            }
            // The work's own language first: an "Original" label on another language contradicts the catalog.
            if (work != null) {
                main.firstOrNull { AudioChoice.matches(work, it.second) }?.let { return it.first }
                continue
            }
            main.firstOrNull { originalLabel.containsMatchIn(it.second) }?.let { return it.first }
            // Without the work's language: in a file that also holds the first wanted language (often marked as the
            // default), the other track is the original one.
            val dubbed = firstLanguage ?: "fr"
            if (main.any { AudioChoice.matches(dubbed, it.second) }) main.firstOrNull { !AudioChoice.matches(dubbed, it.second) }?.let { return it.first }
        }
        return null
    }

    /** One wanted language, for the older callers: "" is the original. */
    fun pickTrack(tracks: List<Pair<Int, String>>, wanted: String, workLanguage: String? = null): Int? =
        pickTrack(tracks, if (wanted.isBlank()) listOf(ORIGINAL) else listOf(wanted, ORIGINAL), workLanguage)

    /** Every track is named and none is in a wanted language that can be told ("original" without the work's language cannot). */
    fun missingWanted(tracks: List<Pair<Int, String>>, intent: AudioIntent, workLanguage: String?): Boolean {
        val codes = intent.resolved(workLanguage).filter { it != ORIGINAL }
        if (codes.isEmpty() || tracks.isEmpty()) return false
        if (tracks.any { AudioChoice.languageOf(it.second) == null }) return false
        return tracks.none { t -> codes.any { AudioChoice.matches(it, t.second) } }
    }

    /** The first wanted language that can be told, to name in a notice ("No Italian audio"). */
    fun firstWanted(intent: AudioIntent, workLanguage: String?): String? =
        intent.resolved(workLanguage).firstOrNull { it != ORIGINAL }

    /**
     * Subtitles to show among [tracks] for the audio track [audioLabel] playing: none when off, the given language, or in
     * "auto" the interface language when the audio heard is another one.
     */
    fun pickSubtitle(tracks: List<Pair<Int, String>>, mode: String, audioLabel: String?, interfaceLanguage: String): Int? {
        val lang = when (mode) {
            AudioIntent.SUBTITLES_OFF, "" -> return null
            AudioIntent.SUBTITLES_AUTO -> interfaceLanguage.lowercase().take(2).takeIf { audioLabel == null || !AudioChoice.matches(it, audioLabel) } ?: return null
            else -> mode
        }
        return tracks.firstOrNull { it.first >= 0 && AudioChoice.matches(lang, it.second) }?.first
    }
}
