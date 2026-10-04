package com.mediaforge.app.subs

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.math.max

/** Result of decoding a subtitle file. [alternatives] are other plausible charsets for a "wrong text? try..." picker. */
class Decoded(val text: String, val charset: String, val bom: Boolean, val alternatives: List<String>)

/**
 * Encoding detection for subtitle files: BOMs, UTF-16 without BOM, strict UTF-8, then scoring of legacy
 * charsets (Arabic Windows-1256 / ISO-8859-6, Cyrillic, Greek, Hebrew, Thai, Turkish, Central European,
 * Western, Japanese, Chinese, Korean). Heuristic only; always let the user override with [decodeAs].
 */
object Encodings {
    val choices = listOf(
        "UTF-8", "UTF-16LE", "UTF-16BE", "windows-1256", "ISO-8859-6", "windows-1252", "windows-1250", "windows-1254",
        "windows-1251", "KOI8-R", "ISO-8859-5", "windows-1253", "ISO-8859-7", "windows-1255", "ISO-8859-8", "windows-874",
        "Shift_JIS", "GBK", "Big5", "EUC-KR",
    )

    private val LATIN = listOf("windows-1252", "windows-1250", "windows-1254")
    private val NON_LATIN = listOf(
        "windows-1256", "ISO-8859-6", "windows-1251", "KOI8-R", "ISO-8859-5", "windows-1253", "ISO-8859-7",
        "windows-1255", "ISO-8859-8", "windows-874", "Shift_JIS", "GBK", "Big5", "EUC-KR",
    )

    private fun charset(name: String): Charset? = try { Charset.forName(name) } catch (e: Exception) { null }

    private fun strict(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) {
        null
    }

    private fun b(v: Int) = v.toByte()

    fun decode(bytes: ByteArray, hint: String? = null): Decoded {
        if (bytes.size >= 3 && bytes[0] == b(0xEF) && bytes[1] == b(0xBB) && bytes[2] == b(0xBF)) {
            return Decoded(String(bytes, 3, bytes.size - 3, Charsets.UTF_8), "UTF-8", true, emptyList())
        }
        if (bytes.size >= 2 && bytes[0] == b(0xFF) && bytes[1] == b(0xFE)) {
            return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE), "UTF-16LE", true, emptyList())
        }
        if (bytes.size >= 2 && bytes[0] == b(0xFE) && bytes[1] == b(0xFF)) {
            return Decoded(String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE), "UTF-16BE", true, emptyList())
        }
        guessUtf16(bytes)?.let { cs -> strict(bytes, cs)?.let { return Decoded(it, cs.name(), false, emptyList()) } }
        strict(bytes, Charsets.UTF_8)?.let { return Decoded(it, "UTF-8", false, emptyList()) }

        val ranked = rank(bytes, hint)
        val best = ranked.firstOrNull()?.first ?: "windows-1252"
        val cs = charset(best) ?: Charsets.ISO_8859_1
        val text = strict(bytes, cs) ?: String(bytes, cs)
        return Decoded(text, best, false, ranked.drop(1).take(3).map { it.first })
    }

    /** Forced decoding chosen by the user (never fails; strips a matching BOM). */
    fun decodeAs(bytes: ByteArray, charsetName: String): String {
        val cs = charset(charsetName) ?: Charsets.UTF_8
        var off = 0
        if (cs == Charsets.UTF_8 && bytes.size >= 3 && bytes[0] == b(0xEF) && bytes[1] == b(0xBB) && bytes[2] == b(0xBF)) off = 3
        if ((cs == Charsets.UTF_16LE && bytes.size >= 2 && bytes[0] == b(0xFF) && bytes[1] == b(0xFE)) ||
            (cs == Charsets.UTF_16BE && bytes.size >= 2 && bytes[0] == b(0xFE) && bytes[1] == b(0xFF))
        ) off = 2
        return String(bytes, off, bytes.size - off, cs)
    }

    /** Encodes for saving. Characters the charset can't hold become '?', so prefer UTF-8 for new files. */
    fun encode(text: String, charsetName: String, bom: Boolean): ByteArray {
        val cs = charset(charsetName) ?: Charsets.UTF_8
        val body = text.toByteArray(cs)
        val mark = when {
            !bom -> ByteArray(0)
            cs == Charsets.UTF_8 -> byteArrayOf(b(0xEF), b(0xBB), b(0xBF))
            cs == Charsets.UTF_16LE -> byteArrayOf(b(0xFF), b(0xFE))
            cs == Charsets.UTF_16BE -> byteArrayOf(b(0xFE), b(0xFF))
            else -> ByteArray(0)
        }
        return mark + body
    }

    private fun guessUtf16(bytes: ByteArray): Charset? {
        val n = minOf(bytes.size, 4000) / 2 * 2
        if (n < 8) return null
        var evenZero = 0
        var oddZero = 0
        var i = 0
        while (i < n) {
            if (bytes[i] == 0.toByte()) evenZero++
            if (bytes[i + 1] == 0.toByte()) oddZero++
            i += 2
        }
        val half = n / 2
        return when {
            oddZero > half * 0.3 && evenZero < half * 0.02 -> Charsets.UTF_16LE
            evenZero > half * 0.3 && oddZero < half * 0.02 -> Charsets.UTF_16BE
            else -> null
        }
    }

    /** Ranks legacy charsets for [bytes], best first. */
    private fun rank(bytes: ByteArray, hint: String?): List<Pair<String, Double>> {
        // How much of the real text (not tags / ASS headers) is high bytes vs. ASCII letters?
        var high = 0
        var ascii = 0
        val tagBlock = Regex("\\{[^}]*\\}")
        val htmlTag = Regex("<[^>]*>")
        val stamp = Regex("^\\s*\\[[^\\]]*\\]")
        for (line in String(bytes, Charsets.ISO_8859_1).split('\n')) {
            if (line.none { it.code >= 0x80 }) continue
            var p = line
            if (p.startsWith("Dialogue:") || p.startsWith("Comment:")) p = p.split(",", limit = 10).last()
            p = stamp.replace(htmlTag.replace(tagBlock.replace(p, ""), ""), "")
            for (c in p) if (c.code >= 0x80) high++ else if (c in 'a'..'z' || c in 'A'..'Z') ascii++
        }
        val highRatio = if (high + ascii == 0) 0.0 else high.toDouble() / (high + ascii)

        fun scoreAll(names: List<String>): List<Pair<String, Double>> {
            val ordered = if (hint != null && hint in names) listOf(hint) + names.filter { it != hint } else names
            return ordered.mapIndexedNotNull { idx, name ->
                val cs = charset(name) ?: return@mapIndexedNotNull null
                val txt = strict(bytes, cs) ?: return@mapIndexedNotNull null
                var s = score(txt)
                if (name == "windows-1254" && turkishBytes(bytes) >= 3) s += 0.3
                name to (s + (ordered.size - idx) * 1e-6) // earlier in the list wins ties
            }.sortedByDescending { it.second }
        }

        var scored = scoreAll(if (highRatio >= 0.45) NON_LATIN else LATIN)
        if (scored.isEmpty() && highRatio >= 0.45) scored = scoreAll(LATIN)
        if (scored.isEmpty()) scored = listOf("windows-1252" to 0.0)
        return scored
    }

    private fun turkishBytes(bytes: ByteArray): Int {
        var n = 0
        for (x in bytes) when (x.toInt() and 0xFF) { 0xD0, 0xDD, 0xDE, 0xF0, 0xFD, 0xFE -> n++ }
        return n
    }

    /** Plausibility of decoded text: real letters of one script, sane case, Arabic "AL" bigram, no controls. */
    private fun score(text: String): Double {
        var letters = 0
        var nonAscii = 0
        var bad = 0
        var lower = 0
        var cased = 0
        var alAl = 0
        var marks = 0
        var prev = 0
        val scripts = HashMap<Character.UnicodeScript, Int>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            if (cp < 0x80) { prev = cp; continue }
            nonAscii++
            if (!Character.isDefined(cp) || Character.isISOControl(cp) || cp == 0xFFFD) bad++
            if (Character.isLetter(cp)) {
                letters++
                scripts.merge(Character.UnicodeScript.of(cp), 1) { a, c -> a + c }
                if (Character.isLowerCase(cp)) { lower++; cased++ } else if (Character.isUpperCase(cp)) cased++
            }
            if (cp in 0x064B..0x0652 || cp == 0x0640) marks++
            if (cp == 0x0644 && prev == 0x0627) alAl++ // ال
            prev = cp
        }
        if (nonAscii == 0) return 0.0
        val dominant = scripts.values.maxOrNull() ?: 0
        var s = letters.toDouble() / nonAscii - 2.0 * bad / nonAscii
        s += 0.5 * dominant / max(1, letters)
        if (cased > 0) s += 0.5 * lower.toDouble() / cased
        s += 2.0 * alAl / max(1, letters)
        s -= 1.0 * marks / max(1, letters)
        return s
    }
}
