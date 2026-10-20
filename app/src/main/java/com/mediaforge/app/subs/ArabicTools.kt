package com.mediaforge.app.subs

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * AR3: Arabic subtitle editing tools. Pure text functions; the screen applies them to the caret, the line or the file.
 * Override blocks {...} and the ASS escapes \N \n \h are never changed.
 */
object ArabicTools {
    const val LRM = "‎"
    const val RLM = "‏"
    const val ALM = "؜"   // Arabic letter mark
    const val LRI = "⁦"
    const val RLI = "⁧"
    const val FSI = "⁨"
    const val PDI = "⁩"
    private val CONTROLS = Regex("[‎‏؜‪-‮⁦-⁩]")
    private val LEAD_TAGS = Regex("""^(\{[^}]*\})*""")

    /** Text with [s] put in place of the selection [start, end); returns the new text and the caret after [s]. */
    fun insert(text: String, start: Int, end: Int, s: String): Pair<String, Int> {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        return text.substring(0, a) + s + text.substring(b) to a + s.length
    }

    /** Wraps the selection in an isolate (RLI...PDI or LRI...PDI); with no selection, inserts an empty pair and puts the caret inside. */
    fun isolate(text: String, start: Int, end: Int, rtl: Boolean): Pair<String, Int> {
        val a = minOf(start, end).coerceIn(0, text.length)
        val b = maxOf(start, end).coerceIn(0, text.length)
        val open = if (rtl) RLI else LRI
        val out = text.substring(0, a) + open + text.substring(a, b) + PDI + text.substring(b)
        return out to (if (a == b) a + 1 else b + 2)
    }

    /** True when the line opens (after its tags) with a right-to-left mark. */
    fun isForcedRtl(text: String): Boolean {
        val tags = LEAD_TAGS.find(text)?.value.orEmpty()
        return text.startsWith(RLM, tags.length)
    }

    /**
     * Per-line direction: puts an RLM at the start of every visual line (after the leading tags), so a line that starts
     * with a Latin word, a number or punctuation is still laid out right to left by libass and players; again = removes it.
     */
    fun toggleRtl(text: String): String {
        val on = isForcedRtl(text)
        val tags = LEAD_TAGS.find(text)?.value.orEmpty()
        val parts = text.substring(tags.length).split("\\N")
        return tags + parts.joinToString("\\N") { p -> if (on) p.removePrefix(RLM) else if (p.startsWith(RLM)) p else RLM + p }
    }

    /** Removes every direction mark and embedding/isolate control (cleanup after a bad export). */
    fun stripControls(text: String): String = mapOutsideCodes(text) { CONTROLS.replace(it, "") }

    /**
     * Latin punctuation -> Arabic in right-to-left lines: , ; ? become ، ؛ ؟ and "..." becomes «...».
     * A comma between two digits (1,000) and anything inside {...} stay as they are. Leading punctuation that a
     * visual-order file put at the start moves to the end ([RtlFix]).
     */
    fun arabicPunctuation(text: String): String = text.split("\\N").joinToString("\\N") { line ->
        if (!RtlFix.isRtl(AssLook.plainText(line))) line else RtlFix.fixLine(mapOutsideCodes(line) { punct(it) })
    }

    private fun punct(s: String): String {
        val sb = StringBuilder(s.length)
        var quoteOpen = false
        val quotes = s.count { it == '"' }
        for ((i, c) in s.withIndex()) {
            val p = s.getOrNull(i - 1)
            val n = s.getOrNull(i + 1)
            when {
                c == ',' && !(p?.isDigit() == true && n?.isDigit() == true) -> sb.append('،')
                c == ';' -> sb.append('؛')
                c == '?' -> sb.append('؟')
                c == '"' && quotes % 2 == 0 -> { sb.append(if (quoteOpen) '»' else '«'); quoteOpen = !quoteOpen }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- normalisation (A23 / AR6)

    private val TASHKEEL = Regex("[\u064B-\u0652\u0670\u0640]") // harakat, shadda, sukun, dagger alef, tatweel
    private val TATWEEL = Regex("\u0640")

    /** Hamza forms of alef (أ إ آ) -> ا, alef maqsura ى -> ي, Persian ک ی -> Arabic ك ي, ta marbuta kept. Outside tags. */
    fun normalizeLetters(text: String): String = mapOutsideCodes(text) { s ->
        s.replace('\u0623', '\u0627').replace('\u0625', '\u0627').replace('\u0622', '\u0627')
            .replace('\u0649', '\u064A').replace('\u06A9', '\u0643').replace('\u06CC', '\u064A')
    }

    fun stripTashkeel(text: String): String = mapOutsideCodes(text) { TASHKEEL.replace(it, "") }

    fun stripTatweel(text: String): String = mapOutsideCodes(text) { TATWEEL.replace(it, "") }

    // ---------------------------------------------------------------- mojibake repair (A25)

    /** One way a file can have been decoded wrongly: its bytes were really [real] but were read as [readAs]. */
    data class Repair(val readAs: String, val real: String)

    val REPAIRS = listOf(
        Repair("windows-1252", "UTF-8"),       // UTF-8 Arabic shown as Ù…Ø±Ø­Ø¨Ø§
        Repair("ISO-8859-1", "UTF-8"),
        Repair("windows-1252", "windows-1256"), // Windows Arabic shown as ãÑÍÈÇ
        Repair("ISO-8859-1", "windows-1256"),
        Repair("windows-1256", "UTF-8"),       // UTF-8 read as Windows Arabic: ظ…ط±ط­ط¨ط§
    )

    private fun strict(bytes: ByteArray, cs: Charset): String? = try {
        cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    } catch (e: CharacterCodingException) { null }

    private fun bytesOf(s: String, cs: Charset): ByteArray? = try {
        cs.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(s)).let { b -> ByteArray(b.remaining()).also { b.get(it) } }
    } catch (e: CharacterCodingException) { null }

    /** [s] re-read with [r]; null when the text cannot be the result of that mistake (lossless check both ways). */
    fun repair(s: String, r: Repair): String? {
        val bytes = bytesOf(s, Charset.forName(r.readAs)) ?: return null
        return strict(bytes, Charset.forName(r.real))
    }

    /** Share of letters that are Arabic (0..1). */
    fun arabicRatio(s: String): Double {
        var ar = 0
        var all = 0
        for (c in s) if (c.isLetter()) { all++; if (c in '؀'..'ۿ' || c in 'ݐ'..'ݿ' || c in 'ﭐ'..'﻿') ar++ }
        return if (all == 0) 0.0 else ar.toDouble() / all
    }

    /** The repair that turns most of [texts] into Arabic; null when no repair is clearly better than the text as it is. */
    fun guessRepair(texts: List<String>): Repair? {
        val sample = texts.joinToString("\n").take(20000)
        if (sample.none { it.code >= 0x80 }) return null
        val now = arabicRatio(sample)
        return REPAIRS.mapNotNull { r -> repair(sample, r)?.let { r to arabicRatio(it) } }
            .filter { it.second >= 0.5 && it.second > now + 0.3 }
            .maxByOrNull { it.second }?.first
    }

    /** Repairs every line's text and actor; lines the repair cannot read stay as they were. */
    fun repairFile(f: SubFile, r: Repair): Pair<SubFile, Int> {
        var n = 0
        val ev = f.events.map { e ->
            val t = repair(e.text, r) ?: e.text
            val a = repair(e.actor, r) ?: e.actor
            if (t != e.text || a != e.actor) { n++; e.copy(text = t, actor = a) } else e
        }
        return f.copy(events = ev) to n
    }
}
