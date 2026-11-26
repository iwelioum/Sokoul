package com.sokoul.pc.i18n

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** An interface language: its code, the language and region TMDB is asked in, and whether it reads right to left. */
data class InterfaceLanguage(val code: String, val tmdb: String, val region: String, val rtl: Boolean = false)

/** The interface languages Sokoul ships. Any other system language shows in English. */
object InterfaceLanguages {
    val all = listOf(
        InterfaceLanguage("en", "en-US", "US"),
        InterfaceLanguage("fr", "fr-FR", "FR"),
        InterfaceLanguage("es", "es-ES", "ES"),
        InterfaceLanguage("de", "de-DE", "DE"),
        InterfaceLanguage("nl", "nl-NL", "NL"),
        InterfaceLanguage("it", "it-IT", "IT"),
        InterfaceLanguage("pt", "pt-BR", "BR"),
        InterfaceLanguage("ar", "ar-SA", "SA", rtl = true),
        InterfaceLanguage("tr", "tr-TR", "TR"),
    )

    fun of(code: String?): InterfaceLanguage? = all.firstOrNull { it.code == code?.lowercase()?.take(2) }

    /** The language the interface shows for [locale]: its own when shipped, English otherwise. */
    fun current(locale: Locale = Locale.getDefault()): InterfaceLanguage = of(locale.language) ?: all.first()
}

/**
 * The interface texts of each language, keyed by their English text, read from `i18n/<code>.tsv` (shared with the TV):
 * one `english<TAB>translation` line per text, `\n` and `\t` escaped. A key holding `{1}`, `{2}`… is a template for an
 * English text built with values ("No {1} audio · Playing in {2}"); the translation places the same numbers where its
 * grammar wants them. A text that is not there shows in English.
 */
object Translations {
    private class Catalogue(val exact: Map<String, String>, val templates: List<Pair<Regex, String>>)

    private val catalogues = ConcurrentHashMap<String, Catalogue>()
    private val placeholder = Regex("""\{(\d+)\}""")

    private fun unescape(s: String) = s.replace("\\t", "\t").replace("\\n", "\n")

    /** Parses a catalogue's lines; exposed for the tests. */
    fun parse(lines: Sequence<String>): Map<String, String> = lines
        .filter { it.isNotBlank() && !it.startsWith("#") && '\t' in it }
        .associate { line -> unescape(line.substringBefore('\t')) to unescape(line.substringAfter('\t')) }
        .filterValues { it.isNotBlank() }

    private fun load(code: String): Catalogue = catalogues.getOrPut(code) {
        val map = runCatching {
            Translations::class.java.classLoader.getResourceAsStream("i18n/$code.tsv")?.bufferedReader(Charsets.UTF_8)?.use { parse(it.lineSequence()) }
        }.getOrNull().orEmpty()
        // A template needs words of its own: "{1} · {2}" would match, and rewrite, any text at all.
        val templates = map.filterKeys { placeholder.containsMatchIn(it) && placeholder.replace(it, "").any(Char::isLetter) }.map { (key, value) ->
            // "{1}" becomes a lazy group; everything else is matched literally.
            val pattern = key.split(placeholder).joinToString("(.+?)") { Regex.escape(it) }
            Regex("^$pattern$", RegexOption.DOT_MATCHES_ALL) to value
        }
        Catalogue(map, templates)
    }

    /** The text in [code], or null when the catalogue does not have it. */
    fun lookup(code: String, english: String): String? {
        if (code == "en") return null
        val catalogue = load(code)
        catalogue.exact[english]?.let { return it }
        for ((pattern, translation) in catalogue.templates) {
            val found = pattern.matchEntire(english) ?: continue
            val values = found.groupValues.drop(1)
            return placeholder.replace(translation) { m -> values.getOrNull(m.groupValues[1].toInt() - 1) ?: m.value }
        }
        return null
    }

    /** How many texts a language has, for the completeness test and the diagnostics. */
    fun size(code: String): Int = load(code).exact.size

    /** Forgets loaded catalogues (tests). */
    fun reset() = catalogues.clear()
}
