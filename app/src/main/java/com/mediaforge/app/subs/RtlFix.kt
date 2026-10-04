package com.mediaforge.app.subs

/**
 * Fixes punctuation that sits on the wrong side of right-to-left lines (Arabic, Hebrew, Persian, Urdu).
 * Files typed or exported "visually" often show `.hello` instead of `hello.` - the punctuation comes first in
 * logical order. Pure functions: nothing is changed unless a line is clearly RTL.
 */
object RtlFix {
    private val LEAD = Regex("""^([.!?\u2026\u060C\u061F\u061B:;]+)\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
    private val TAGS = Regex("""^(\{[^}]*\})*""")
    private const val END_PUNCT = ".!?\u2026\u060C\u061F\u061B:;"

    fun isRtl(s: String): Boolean {
        var rtl = 0
        var other = 0
        for (c in s) {
            if (!c.isLetter()) continue
            when (Character.getDirectionality(c)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                else -> other++
            }
        }
        return rtl > 0 && rtl >= other
    }

    /** One visual line (no \N inside). Override tags at the start are kept in place. */
    fun fixLine(line: String): String {
        val tags = TAGS.find(line)?.value ?: ""
        val body = line.substring(tags.length)
        if (!isRtl(body)) return line
        val m = LEAD.matchEntire(body.trim()) ?: return line
        val rest = m.groupValues[2]
        if (rest.isEmpty() || rest.last() in END_PUNCT) return line
        return tags + rest + m.groupValues[1]
    }

    fun fixText(text: String): String = text.split("\\N").joinToString("\\N") { fixLine(it) }

    fun fixEvents(events: List<AssEvent>): List<AssEvent> =
        events.map { e -> val t = fixText(e.text); if (t == e.text) e else e.copy(text = t) }
}
