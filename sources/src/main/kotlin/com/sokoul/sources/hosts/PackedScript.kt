package com.sokoul.sources.hosts

import java.util.regex.Matcher

/**
 * Reader for the classic JavaScript packer, `eval(function(p,a,c,k,e,d){…}('<template>', radix, count, '<words>'.split('|')…))`.
 *
 * The packer replaces every word of a script by its index in a dictionary, written in base `radix` with the digits
 * `0-9a-zA-Z`. Unpacking reads each word of the template back as a number and puts the dictionary entry in its place.
 * Nothing is evaluated.
 */
object PackedScript {
    private val opening = Regex("""eval\s*\(\s*function\s*\(\s*p\s*,\s*a\s*,\s*c\s*,\s*k\s*,\s*e\s*,\s*[dr]\s*\)""")
    private val argumentsStart = Regex("""\}\s*\(\s*(?=['"])""")
    private val numbers = Regex("""\s*,\s*(\d{1,3})\s*,\s*(\d{1,5})\s*,\s*""")
    private val splitCall = Regex("""\s*\.\s*split\s*\(\s*(['"])\|\1\s*\)""")
    private val word = Regex("""\b\w+\b""")

    /** Every packed script of [page], unpacked, in page order. */
    fun unpackAll(page: String): List<String> {
        val scripts = mutableListOf<String>()
        var from = 0
        while (scripts.size < 16) {
            val start = opening.find(page, from) ?: break
            from = start.range.last + 1
            unpackAt(page, from)?.let { scripts.add(it) }
        }
        return scripts
    }

    /** The page's text followed by its unpacked scripts: where a host's player setup may sit. */
    fun withUnpacked(page: String): List<String> = listOf(page) + unpackAll(page)

    private fun unpackAt(page: String, from: Int): String? {
        val args = argumentsStart.find(page, from) ?: return null
        val template = JsString.read(page, args.range.last + 1) ?: return null
        val counts = numbers.prefixAt(page, template.end) ?: return null
        val radix = counts.group(1).toInt()
        val count = counts.group(2).toInt()
        val dictionary = JsString.read(page, counts.end()) ?: return null
        if (splitCall.prefixAt(page, dictionary.end) == null) return null
        if (radix !in 2..62) return null
        val words = dictionary.value.split('|')
        if (count > words.size) return null
        return expand(template.value, radix, words.subList(0, count))
    }

    /** A match of this pattern starting exactly at [index] of [text], or null. */
    private fun Regex.prefixAt(text: String, index: Int): Matcher? {
        if (index > text.length) return null
        val matcher = toPattern().matcher(text).region(index, text.length)
        return matcher.takeIf { it.lookingAt() }
    }

    /** Puts each dictionary entry back in place of its number. */
    internal fun expand(template: String, radix: Int, words: List<String>): String = word.replace(template) { match ->
        val index = valueOf(match.value, radix)
        if (index != null && index < words.size && words[index].isNotEmpty()) words[index] else match.value
    }

    /** [token] read as a base-[radix] number in the packer's digits, or null when it is not one of its numbers. */
    private fun valueOf(token: String, radix: Int): Int? {
        if (token.length > 4 || (token.length > 1 && token[0] == '0')) return null
        var value = 0
        for (c in token) {
            val digit = when (c) {
                in '0'..'9' -> c - '0'
                in 'a'..'z' -> c - 'a' + 10
                in 'A'..'Z' -> c - 'A' + 36
                else -> return null
            }
            if (digit >= radix) return null
            value = value * radix + digit
        }
        return value
    }
}

/** A JavaScript string literal read from source text. */
internal object JsString {
    class Literal(val value: String, val end: Int)

    /** The literal starting at [start] (on its opening quote), unescaped, and the index just after its closing quote. */
    fun read(text: String, start: Int): Literal? {
        val quote = text.getOrNull(start)?.takeIf { it == '\'' || it == '"' } ?: return null
        val out = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == quote -> return Literal(out.toString(), i + 1)
                c == '\\' && i + 1 < text.length -> {
                    val next = text[i + 1]
                    i += 2
                    when (next) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'v' -> out.append('\u000B')
                        '0' -> out.append('\u0000')
                        'x' -> hex(text, i, 2)?.let { out.append(it.toChar()); i += 2 } ?: out.append('x')
                        'u' -> hex(text, i, 4)?.let { out.append(it.toChar()); i += 4 } ?: out.append('u')
                        '\n' -> Unit
                        else -> out.append(next)
                    }
                    continue
                }
                c == '\n' -> return null
                else -> out.append(c)
            }
            i++
        }
        return null
    }

    private fun hex(text: String, at: Int, length: Int): Int? =
        text.substring(at, minOf(text.length, at + length)).takeIf { it.length == length }?.toIntOrNull(16)
}
