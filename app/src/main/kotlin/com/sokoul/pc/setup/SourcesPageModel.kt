package com.sokoul.pc.setup

import com.sokoul.pc.data.L
import com.sokoul.pc.startup.onboarding.Copy
import com.sokoul.pc.startup.onboarding.OnboardingModel
import com.sokoul.pc.startup.onboarding.SourceKind
import com.sokoul.pc.startup.onboarding.SourceStatus

/** The two groups of Settings › Sources and keys: what most people have, then the keys only experts need. */
enum class SourcesSection { Yours, Expert }

/** What "Enter…" opens from a guide: one of the first launch's sheets, a single key, or nothing (no key exists). */
enum class SourceEntry { Iptv, RealDebrid, Subtitles, Server, Key, None }

/**
 * One card of the page. [id] is its test tag and, for [SourceEntry.Key], the stored key's id; [guides] are the
 * [ServiceGuides] it explains (the subtitles card has two services, either is enough).
 */
data class SourceCardSpec(
    val id: String,
    val section: SourcesSection,
    val kind: SourceKind,
    val guides: List<String>,
    val entry: SourceEntry,
    val title: () -> String,
)

/** Where a card stands, as its right side shows it: the state, then a masked value or a short summary. */
data class SourceCardState(val status: SourceStatus, val value: String?, val problem: String? = null)

object SourcesPage {
    val cards: List<SourceCardSpec> = listOf(
        SourceCardSpec("iptv", SourcesSection.Yours, SourceKind.Iptv, listOf("iptv"), SourceEntry.Iptv) { Copy.iptvTitle },
        SourceCardSpec("realdebrid", SourcesSection.Yours, SourceKind.RealDebrid, listOf("realdebrid"), SourceEntry.RealDebrid) { Copy.debridTitle },
        SourceCardSpec("subtitles", SourcesSection.Yours, SourceKind.Subtitles, listOf("opensubtitles", "subdl"), SourceEntry.Subtitles) { Copy.subtitlesTitle },
        SourceCardSpec("mainapi", SourcesSection.Yours, SourceKind.Server, listOf("mainapi"), SourceEntry.Server) { Copy.serverTitle },
    ) + OnboardingModel.ADVANCED_KEYS.map { k ->
        // TVmaze answers without a key: its guide says so, and there is nothing to enter.
        SourceCardSpec(k.id, SourcesSection.Expert, SourceKind.Advanced, listOf(k.id), if (k.id == "tvmaze") SourceEntry.None else SourceEntry.Key) { k.provider }
    }

    fun section(s: SourcesSection) = cards.filter { it.section == s }

    /** The card's state from what the first launch's model knows; a key card reads its own key. */
    fun state(card: SourceCardSpec, model: OnboardingModel): SourceCardState {
        if (card.entry == SourceEntry.Key || card.entry == SourceEntry.None) {
            val status = model.keyStatus(card.id)
            model.keyErrors[card.id]?.let { return SourceCardState(SourceStatus.Problem, status?.hint, it) }
            return when {
                card.entry == SourceEntry.None -> SourceCardState(SourceStatus.Ready, noKeyNeeded.text())
                status?.configured != true -> SourceCardState(SourceStatus.Empty, null)
                status.source == builtInLabel() -> SourceCardState(SourceStatus.Ready, builtIn.text())
                else -> SourceCardState(SourceStatus.Ready, status.hint)
            }
        }
        val view = model.sources[card.kind]
        val status = view?.status ?: SourceStatus.Empty
        val value = when (card.kind) {
            SourceKind.RealDebrid -> model.keyHint("realdebrid")?.let { hint -> listOfNotNull(view?.summary?.takeIf { it != Copy.debridSaved }, hint).joinToString(" · ") }
            SourceKind.Subtitles -> listOf("opensubtitles" to "OpenSubtitles", "subdl" to "Subdl")
                .mapNotNull { (id, name) -> model.keyStatus(id)?.takeIf { it.configured }?.let { name } }.joinToString(" · ").ifEmpty { null }
            else -> view?.summary
        }
        return SourceCardState(status, if (status == SourceStatus.Ready) value ?: view?.summary else null, view?.problem.takeIf { status == SourceStatus.Problem })
    }

    /** "themoviedb.org/settings/api": the link as a person types it, under the QR. */
    fun shortLink(url: String): String = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

    /**
     * A warning before asking the service, when [value] cannot be [id]'s key: null when it fits or when the service's
     * format is not known for certain ([ServiceGuide.keyFormat]).
     */
    fun keyFormatWarning(id: String, value: String): String? {
        val guide = ServiceGuides.of(id) ?: return null
        val format = guide.keyFormat ?: return null
        val v = value.trim()
        if (v.isEmpty() || format.matches(v)) return null
        // TMDB's page shows a short key and a long "Read Access Token" (a JWT starting eyJ): the long one is the usual mistake.
        if (id == "tmdb" && (v.startsWith("eyJ") || v.length > 32)) {
            return L("This looks like the long token; paste the 32-character key.", "Ceci ressemble au long jeton ; collez la clé de 32 caractères.").text()
        }
        val name = guide.name
        return L("This doesn't look like a $name key. Check that you copied all of it.", "Cela ne ressemble pas à une clé ${name}. Vérifiez que vous l'avez copiée en entier.").text()
    }

    private val noKeyNeeded = L("No key needed", "Aucune clé nécessaire")
    private val builtIn = L("Built-in key", "Clé intégrée")
    private fun builtInLabel() = builtIn.text()
}

/** The state of one stored key (entered, built in, or absent), with its mask. */
fun OnboardingModel.keyStatus(id: String) = providerStatuses().firstOrNull { it.id == id }
