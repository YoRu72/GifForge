package com.mediaforge.app.subs

/**
 * FX9 / 22.d karaoke, ported from Aegisub (libaegisub/ass/karaoke.cpp + src/ass_karaoke.cpp): a line is a list of
 * syllables, each with a \k-type tag and a duration; times are kept in ms from the line start (ASS writes centiseconds).
 * \K is read as \kf like Aegisub. Leading override blocks before the first \k (look tags, the MF1 effect block) stay
 * in [Kara.prefix] so they keep their place. Arabic: a split may never cut a joined word (AR8), see [canSplit].
 */
data class Syl(val startMs: Long, val durMs: Long, val tag: String = "\\k", val text: String = "")

data class Kara(val prefix: String, val syls: List<Syl>) {
    val isKaraoke: Boolean get() = syls.size > 1 || syls.firstOrNull()?.durMs?.let { it > 0 } == true
}

object Karaoke {
    private val BLOCK = Regex("""\{[^}]*\}""")
    private val KTAG = Regex("""\\(kf|ko|k|K)(\d+)""")

    fun hasKaraoke(text: String): Boolean = BLOCK.findAll(text).any { KTAG.containsMatchIn(it.value) }

    /** Splits [text] into syllables, the way Aegisub's ParseKaraokeSyllables does. */
    fun parse(text: String): Kara {
        val prefix = StringBuilder()
        val syls = ArrayList<Syl>()
        var cur = StringBuilder()
        var curPlain = 0          // letters in cur (tags do not count, like Aegisub's syl.text)
        var seen = false          // plain text or a \k tag met: blocks from now on belong to syllables
        var anyK = false
        var start = 0L
        var dur = 0L
        var tag = "\\k"
        var last = 0

        fun plain(p: String) {
            if (p.isEmpty()) return
            seen = true
            cur.append(p)
            curPlain += p.length
        }
        fun block(b: String) { if (!seen) prefix.append(b) else cur.append(b) }

        for (m in BLOCK.findAll(text)) {
            plain(text.substring(last, m.range.first))
            last = m.range.last + 1
            val inner = m.value.substring(1, m.value.length - 1)
            val ks = KTAG.findAll(inner).toList()
            if (ks.isEmpty()) { block(m.value); continue }
            var pos = 0
            for (k in ks) {
                val before = inner.substring(pos, k.range.first)
                if (before.isNotEmpty()) block("{$before}")
                // a syllable with no time and no letters is not kept; its tags move on to the next one (Aegisub)
                if (dur > 0 || curPlain > 0) {
                    syls += Syl(start, dur, tag, cur.toString())
                    cur = StringBuilder()
                    curPlain = 0
                }
                start += dur
                seen = true
                anyK = true
                tag = when (k.groupValues[1]) { "K", "kf" -> "\\kf"; "ko" -> "\\ko"; else -> "\\k" }
                dur = k.groupValues[2].toLong() * 10
                pos = k.range.last + 1
            }
            val after = inner.substring(pos)
            if (after.isNotEmpty()) cur.append("{").append(after).append("}")
        }
        plain(text.substring(last))
        if (!anyK) return Kara(prefix.toString(), listOf(Syl(0, 0, "\\k", cur.toString())))
        syls += Syl(start, dur, tag, cur.toString())
        return Kara(prefix.toString(), syls)
    }

    /** Line text again: prefix, then `{\kNN}text` per syllable (duration rounded to centiseconds like Aegisub). */
    fun build(k: Kara): String = k.prefix + k.syls.joinToString("") { "{" + it.tag + ((it.durMs + 5) / 10) + "}" + it.text }

    /** Aegisub Normalize: the last syllable reaches the line end; syllables past the end are cut. */
    fun normalize(syls: List<Syl>, lineMs: Long): List<Syl> {
        if (syls.isEmpty()) return syls
        val lastEnd = syls.last().let { it.startMs + it.durMs }
        return when {
            lastEnd < lineMs -> syls.dropLast(1) + syls.last().let { it.copy(durMs = it.durMs + lineMs - lastEnd) }
            lastEnd > lineMs -> syls.map { s -> if (s.startMs > lineMs) s.copy(startMs = lineMs, durMs = 0) else s.copy(durMs = minOf(s.durMs, lineMs - s.startMs)) }
            else -> syls
        }
    }

    private fun plainLen(s: String) = AssLook.plainText(s).length

    /** Aegisub DoAddSplit: text after [pos] becomes a new syllable; the time is shared by text length, rounded to 10 ms. */
    fun split(syls: List<Syl>, i: Int, pos: Int): List<Syl> {
        val s = syls.getOrNull(i) ?: return syls
        val p = pos.coerceIn(0, s.text.length)
        val a = s.text.substring(0, p)
        val b = s.text.substring(p)
        val la = plainLen(a)
        val lb = plainLen(b)
        val nd = when {
            b.isEmpty() || lb == 0 -> 0L
            a.isEmpty() || la == 0 -> s.durMs
            else -> (s.durMs * lb / (la + lb) + 5) / 10 * 10
        }
        val first = s.copy(text = a, durMs = s.durMs - nd)
        val second = Syl(first.startMs + first.durMs, nd, s.tag, b)
        return syls.take(i) + first + second + syls.drop(i + 1)
    }

    /** Aegisub AutoSplit: one syllable per word (the space stays with the word before it). */
    fun autoSplit(syls: List<Syl>): List<Syl> {
        var out = syls
        while (true) {
            val last = out.last()
            val sp = spaceOutsideTags(last.text)
            if (sp < 0 || sp + 1 >= last.text.length) return out
            out = split(out, out.size - 1, sp + 1)
        }
    }

    private fun spaceOutsideTags(t: String): Int {
        var depth = 0
        for ((i, c) in t.withIndex()) {
            if (c == '{') depth++ else if (c == '}') depth = maxOf(0, depth - 1) else if (c == ' ' && depth == 0) return i
        }
        return -1
    }

    /** Aegisub RemoveSplit: syllable [i] joins the one before it. */
    fun merge(syls: List<Syl>, i: Int): List<Syl> {
        if (i <= 0 || i >= syls.size) return syls
        val p = syls[i - 1]
        val s = syls[i]
        return syls.take(i - 1) + p.copy(durMs = p.durMs + s.durMs, text = p.text + s.text) + syls.drop(i + 1)
    }

    /** Aegisub SetStartTime: moves the boundary before syllable [i] to [t] (ms from line start), within its neighbours. */
    fun setStart(syls: List<Syl>, i: Int, t: Long): List<Syl> {
        if (i <= 0 || i >= syls.size) return syls
        val p = syls[i - 1]
        val s = syls[i]
        val tt = t.coerceIn(p.startMs, s.startMs + s.durMs)
        val d = tt - s.startMs
        return syls.take(i - 1) + p.copy(durMs = p.durMs + d) + s.copy(startMs = tt, durMs = s.durMs - d) + syls.drop(i + 1)
    }

    /** Changes one syllable's length; the next syllable gives or takes the difference (the line length stays). */
    fun nudge(syls: List<Syl>, i: Int, deltaMs: Long): List<Syl> = if (i + 1 < syls.size) setStart(syls, i + 1, syls[i + 1].startMs + deltaMs) else syls

    fun setTagType(syls: List<Syl>, tag: String): List<Syl> = syls.map { it.copy(tag = tag) }

    // ---------------------------------------------------------------- Arabic safety (AR8)

    // letters that never join the letter after them: a karaoke cut after them leaves the word shaped as before
    private const val NON_JOINING = "\u0627\u0623\u0625\u0622\u0671\u062F\u0630\u0631\u0632\u0648\u0624\u0629\u0621\u0649"
    private fun isArabicLetter(c: Char) = c in '\u0620'..'\u064A' || c in '\u066E'..'\u06D3' || c in '\u06FA'..'\u06FF'
    private fun isMark(c: Char) = c in '\u064B'..'\u065F' || c == '\u0670' || c == '\u0640'

    /**
     * May the text be cut before index [pos]? Never between a letter and its marks, never inside a joined Arabic word
     * (a cut there would draw the halves as separate unjoined shapes in libass), never inside {...}.
     */
    fun canSplit(text: String, pos: Int): Boolean {
        if (pos <= 0 || pos >= text.length) return false
        var depth = 0
        for (i in 0 until pos) { if (text[i] == '{') depth++ else if (text[i] == '}') depth = maxOf(0, depth - 1) }
        if (depth > 0) return false
        val next = text[pos]
        if (isMark(next)) return false
        var j = pos - 1
        while (j >= 0 && isMark(text[j])) j--
        if (j < 0) return true
        val prev = text[j]
        if (!isArabicLetter(prev) || !isArabicLetter(next)) return true
        return prev in NON_JOINING
    }

    // ---------------------------------------------------------------- line commands

    /** Aegisub "Split by karaoke": every syllable becomes its own line with its own times; the k tags go. */
    fun splitLine(e: AssEvent): List<AssEvent> {
        val k = parse(e.text)
        if (!k.isKaraoke) return listOf(e)
        val syls = normalize(k.syls, e.durationMs)
        return syls.filter { AssLook.plainText(it.text).isNotBlank() }.map { s ->
            e.copy(startMs = e.startMs + s.startMs, endMs = e.startMs + s.startMs + s.durMs, text = k.prefix + s.text.trim())
        }.ifEmpty { listOf(e) }
    }

    /** Aegisub "Join as karaoke": the lines become one line, each line's text a syllable lasting its line (gaps become empty syllables). */
    fun joinLines(lines: List<AssEvent>): AssEvent {
        val sorted = lines.sortedBy { it.startMs }
        val first = sorted.first()
        val syls = ArrayList<Syl>()
        var t = first.startMs
        for (l in sorted) {
            if (l.startMs > t) { syls += Syl(t - first.startMs, l.startMs - t, "\\k", ""); t = l.startMs }
            val body = Karaoke.parse(l.text).let { k -> if (k.isKaraoke) k.syls.joinToString("") { it.text } else k.syls.first().text }
            val d = (l.endMs - maxOf(l.startMs, t)).coerceAtLeast(0L)
            syls += Syl(t - first.startMs, d, "\\k", body.trim() + " ")
            t += d
        }
        val last = syls.last()
        syls[syls.size - 1] = last.copy(text = last.text.trimEnd())
        return first.copy(endMs = t, text = build(Kara("", syls)))
    }

    /** Fresh karaoke on a plain line: one syllable per word, times by length (Aegisub's auto split on a new line). */
    fun fromPlain(e: AssEvent): String {
        val k = parse(e.text)
        if (k.isKaraoke) return e.text
        val one = listOf(Syl(0, e.durationMs.coerceAtLeast(0), "\\k", k.syls.first().text))
        return build(Kara(k.prefix, autoSplit(one)))
    }

    /** Which syllable is being sung at [t] ms from the line start (-1 before the first, size after the last). */
    fun current(syls: List<Syl>, t: Long): Int {
        for ((i, s) in syls.withIndex()) if (t < s.startMs + s.durMs) return if (t < s.startMs) i - 1 else i
        return syls.size
    }
}
