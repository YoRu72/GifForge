package com.mediaforge.app.subs

/** SE2 + AR6 first part: Fix Common Errors rules. Pure functions; the dialog counts each rule, then applies the chosen ones. */
enum class FixRule { EMPTY, SPACES, OVERLAP, SHORT, SPEED, LONG, AR_PUNCT, AR_DIGITS }

object FixOps {
    const val MIN_MS = 500L
    const val MAX_MS = 7000L
    private val TAG = Regex("\\{[^}]*\\}")
    private val ARABIC = Regex("[\\u0600-\\u06FF]")

    /** Applies [f] to the visible text only; override blocks {...} stay untouched. */
    private fun plainMap(s: String, f: (String) -> String): String {
        val sb = StringBuilder(); var last = 0
        for (m in TAG.findAll(s)) { sb.append(f(s.substring(last, m.range.first))).append(m.value); last = m.range.last + 1 }
        return sb.append(f(s.substring(last))).toString()
    }
    private fun visible(s: String) = TAG.replace(s, "").replace("\\N", "").replace("\\n", "").replace("\\h", "").isBlank()

    fun apply(events: List<AssEvent>, rule: FixRule): List<AssEvent> = when (rule) {
        FixRule.EMPTY -> events.filterNot { !it.comment && visible(it.text) }
        FixRule.SPACES -> events.map { e -> e.copy(text = plainMap(e.text) { it.replace(Regex(" {2,}"), " ").replace(" \\N", "\\N").replace("\\N ", "\\N") }.trim()) }
        FixRule.OVERLAP -> events.mapIndexed { i, e ->
            val n = events.getOrNull(i + 1)
            if (!e.comment && n != null && !n.comment && n.startMs > e.startMs && e.endMs > n.startMs) e.copy(endMs = n.startMs) else e
        }
        FixRule.SHORT -> events.mapIndexed { i, e ->
            if (e.comment || e.endMs - e.startMs >= MIN_MS) e else {
                val room = events.getOrNull(i + 1)?.takeIf { !it.comment && it.startMs > e.startMs }?.startMs ?: Long.MAX_VALUE
                val end = minOf(e.startMs + MIN_MS, room)
                if (end > e.endMs) e.copy(endMs = end) else e
            }
        }
        // too fast: give the line more time (never into the next line); lines with no room are left for the owner
        FixRule.SPEED -> events.mapIndexed { i, e ->
            if (e.comment || ReadOps.cps(e) <= ReadOps.max()) e else {
                val need = Math.ceil(ReadOps.chars(e.text) * 1000.0 / ReadOps.max()).toLong()
                val room = events.getOrNull(i + 1)?.takeIf { !it.comment && it.startMs > e.startMs }?.startMs ?: Long.MAX_VALUE
                val end = minOf(e.startMs + need, room)
                if (end > e.endMs) e.copy(endMs = end) else e
            }
        }
        FixRule.LONG -> events.map { e -> if (!e.comment && e.endMs - e.startMs > MAX_MS) e.copy(endMs = e.startMs + MAX_MS) else e }
        FixRule.AR_PUNCT -> events.map { e ->
            if (!ARABIC.containsMatchIn(e.text)) e else e.copy(text = plainMap(e.text) { it.replace(',', '،').replace(';', '؛').replace('?', '؟') })
        }
        FixRule.AR_DIGITS -> events.map { e ->
            e.copy(text = plainMap(e.text) { s -> buildString { for (c in s) append(if (c in '\u0660'..'\u0669') '0' + (c - '\u0660') else if (c in '\u06F0'..'\u06F9') '0' + (c - '\u06F0') else c) } })
        }
    }

    /** How many lines the rule would change (removed lines count too). */
    fun count(events: List<AssEvent>, rule: FixRule): Int {
        val r = apply(events, rule)
        return if (r.size != events.size) events.size - r.size else events.indices.count { events[it] != r[it] }
    }

    fun applyAll(events: List<AssEvent>, rules: Set<FixRule>): List<AssEvent> =
        FixRule.values().filter { it in rules }.fold(events) { acc, r -> apply(acc, r) }
}
