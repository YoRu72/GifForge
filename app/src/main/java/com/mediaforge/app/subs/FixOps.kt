package com.mediaforge.app.subs

/**
 * SE2 + AR6: Subtitle Edit style "Fix common errors". Every rule can be switched off, every single fix can be unticked
 * in the preview, and running again with the unticked fixes in `skip` gives exactly the previewed result.
 * Comment lines are never touched; override blocks {...} and the codes \N \n \h are never edited.
 */
enum class FixRule {
    EMPTY,      // drop lines with no visible text; trim stray \N
    SPACES,     // double spaces, spaces at the ends and around \N
    AR_PUNCT,   // , ; ? -> ، ؛ ؟ and "..." -> «...» in right-to-left lines; visual-order punctuation moved to the end
    AR_DIGITS,  // ٠١٢ / ۰۱۲ -> 0 1 2
    LONE_I,     // English "i" / "i'm" -> "I" / "I'm" (left-to-right lines only)
    OVERLAP,    // a line ends after the next one starts (same layer)
    SHORT_GAP,  // gap shorter than the minimum gap (flicker)
    SHORT,      // shown too briefly
    SPEED,      // more characters per second than the reading-speed profile allows
    LONG,       // shown too long
}

object FixOps {
    data class Options(
        val minMs: Long = 500,
        val maxMs: Long = 7000,
        val gapMs: Long = 24,
        val maxCps: Double = 17.0,
    )

    /** [line] = index in the list as opened (stable across rules). [after] null = the line is deleted. */
    data class Fix(val rule: FixRule, val line: Int, val before: AssEvent, val after: AssEvent?) {
        val key: String get() = rule.name + "#" + line
    }

    class Result(val events: List<AssEvent>, val fixes: List<Fix>) {
        fun count(r: FixRule) = fixes.count { it.rule == r }
    }

    private class Item(val line: Int, var e: AssEvent?)

    private val TAG = Regex("""\{[^}]*\}""")
    private fun visible(t: String) = TAG.replace(t, "").replace("\\N", "").replace("\\n", "").replace("\\h", "").trim()

    fun run(events: List<AssEvent>, rules: Set<FixRule>, opt: Options = Options(), skip: Set<String> = emptySet()): Result {
        val items = events.mapIndexed { i, e -> Item(i, e) }
        val fixes = ArrayList<Fix>()

        fun record(it: Item, r: FixRule, before: AssEvent, after: AssEvent?) {
            val fx = Fix(r, it.line, before, after)
            fixes += fx
            if (fx.key !in skip) it.e = after
        }

        fun textRule(r: FixRule, f: (String) -> String) {
            if (r !in rules) return
            for (it in items) {
                val e = it.e ?: continue
                if (e.comment) continue
                val t = f(e.text)
                if (t != e.text) record(it, r, e, e.copy(text = t))
            }
        }

        if (FixRule.EMPTY in rules) for (it in items) {
            val e = it.e ?: continue
            if (e.comment) continue
            if (visible(e.text).isEmpty() && !e.text.contains("\\p")) record(it, FixRule.EMPTY, e, null) // \p drawings are not empty
            else trimBreaks(e.text).let { t -> if (t != e.text) record(it, FixRule.EMPTY, e, e.copy(text = t)) }
        }
        textRule(FixRule.SPACES) { spaces(it) }
        textRule(FixRule.AR_PUNCT) { ArabicTools.arabicPunctuation(it) }
        textRule(FixRule.AR_DIGITS) { t -> mapOutsideCodes(t) { westernDigits(it) } }
        textRule(FixRule.LONE_I) { loneI(it) }

        // timing rules: per layer, in time order (lines of other layers may overlap on purpose)
        fun timeRule(r: FixRule, f: (cur: AssEvent, next: AssEvent?) -> AssEvent) {
            if (r !in rules) return
            val groups = items.filter { it.e?.comment == false }.groupBy { it.e!!.layer }
            for ((_, group) in groups) {
                val ordered = group.sortedWith(compareBy({ it.e!!.startMs }, { it.line }))
                for ((k, it) in ordered.withIndex()) {
                    val e = it.e ?: continue
                    val n = f(e, ordered.getOrNull(k + 1)?.e)
                    if (n != e) record(it, r, e, n)
                }
            }
        }
        fun room(e: AssEvent, nx: AssEvent?) = if (nx != null && nx.startMs >= e.startMs) nx.startMs - opt.gapMs else Long.MAX_VALUE

        timeRule(FixRule.OVERLAP) { e, nx ->
            // same start = two speakers / top + bottom on purpose: not an error
            if (nx != null && e.endMs > nx.startMs && nx.startMs > e.startMs) e.copy(endMs = maxOf(e.startMs + 1, nx.startMs - opt.gapMs)) else e
        }
        timeRule(FixRule.SHORT_GAP) { e, nx ->
            if (nx != null && nx.startMs - e.endMs in 1 until opt.gapMs && nx.startMs - opt.gapMs > e.startMs) e.copy(endMs = nx.startMs - opt.gapMs) else e
        }
        timeRule(FixRule.SHORT) { e, nx ->
            if (e.durationMs >= opt.minMs) e else minOf(e.startMs + opt.minMs, room(e, nx)).let { end -> if (end > e.endMs) e.copy(endMs = end) else e }
        }
        timeRule(FixRule.SPEED) { e, nx ->
            val need = Math.ceil(ReadOps.chars(e.text) * 1000.0 / opt.maxCps).toLong()
            if (opt.maxCps <= 0.0 || e.durationMs >= need) e
            else minOf(e.startMs + need, room(e, nx)).let { end -> if (end > e.endMs) e.copy(endMs = end) else e }
        }
        timeRule(FixRule.LONG) { e, _ -> if (e.durationMs > opt.maxMs) e.copy(endMs = e.startMs + opt.maxMs) else e }

        return Result(items.mapNotNull { it.e }, fixes)
    }

    /** Leading/trailing/doubled \N removed (override blocks at the start stay first). */
    fun trimBreaks(t: String): String {
        val lead = Regex("""^(\{[^}]*\})*""").find(t)?.value.orEmpty()
        var body = t.substring(lead.length)
        while (true) {
            val b = body.trim(' ').removePrefix("\\N").removeSuffix("\\N").replace("\\N\\N", "\\N").replace(Regex("""\\N\s+\\N"""), "\\\\N")
            if (b == body) break
            body = b
        }
        return lead + body
    }

    fun spaces(t: String): String = t
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(""" *\\N *"""), "\\\\N")
        .trim(' ')

    fun westernDigits(s: String): String = buildString {
        for (c in s) append(if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else if (c in '\u06F0'..'\u06F9') '0' + (c - '\u06F0') else c)
    }

    private val LONE = Regex("""(?<![\p{L}\p{N}'’])i(?![\p{L}\p{N}])""")
    fun loneI(t: String): String {
        val plain = AssLook.plainText(t)
        if (RtlFix.isRtl(plain) || plain.none { it in 'a'..'z' }) return t
        return mapOutsideCodes(t) { LONE.replace(it, "I") }
    }
}
