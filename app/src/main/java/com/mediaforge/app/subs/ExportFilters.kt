package com.mediaforge.app.subs

/**
 * AEGX5: Aegisub "Export" filters, applied to a copy of the script right before it is written (the open file is
 * not changed). Clean Tags follows Aegisub's cleantags.lua idea: merge adjacent blocks, drop empty blocks and tags
 * overridden later in the same block.
 */
object ExportFilters {
    enum class Filter { CLEAN_TAGS, STRIP_COMMENTS, STRIP_EFFECTS, STRIP_KARAOKE, STRIP_MF_NOTES, SORT_BY_TIME }

    fun apply(f: SubFile, filters: Set<Filter>): SubFile {
        var ev = f.events
        if (Filter.STRIP_COMMENTS in filters) ev = ev.filter { !it.comment }
        if (Filter.STRIP_EFFECTS in filters) ev = ev.map { e -> e.copy(text = e.text.replace(TRANSFORM, "").replace(MOVE_TO_POS) { m -> "\\pos(${m.groupValues[1]},${m.groupValues[2]})" }) }
        if (Filter.STRIP_KARAOKE in filters) ev = ev.map { e -> e.copy(text = Karaoke.parse(e.text).let { k -> if (k.isKaraoke) k.prefix + k.syls.joinToString("") { it.text } else e.text }) }
        if (Filter.STRIP_MF_NOTES in filters) ev = ev.map { e -> e.copy(text = MF_NOTE.replace(e.text, "")) }
        if (Filter.CLEAN_TAGS in filters) ev = ev.map { e -> e.copy(text = cleanTags(e.text)) }
        if (Filter.SORT_BY_TIME in filters) ev = ev.sortedWith(compareBy({ it.startMs }, { it.endMs }))
        return f.copy(events = ev)
    }

    private val TRANSFORM = Regex("""\\t\((?:[^()]|\([^)]*\))*\)""")
    private val MOVE_TO_POS = Regex("""\\move\(\s*(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*,[^)]*\)""")
    private val MF_NOTE = Regex(""" ?MF1;[^\s}]+""")
    private val BLOCK = Regex("""\{[^}]*\}""")
    private val TAG = Regex("""\\(t\((?:[^()]|\([^)]*\))*\)|[a-zA-Z0-9]+(?:\([^)]*\))?[^\\]*)""")

    /** Merges adjacent override blocks and drops tags that a later tag of the same name in the same block overrides. */
    fun cleanTags(text: String): String {
        // 1. merge "}{" (but never into the MF1 note block: its note must stay the block's last item)
        val fx = Regex("""^\{[^}]*MF1;[^}]*\}""").find(text)?.value.orEmpty()
        var t = fx + text.substring(fx.length).replace("}{", "")
        // 2. within each block keep only the last of each repeated simple tag (not \t, not karaoke, not \r)
        t = BLOCK.replace(t) { m ->
            val inner = m.value.substring(1, m.value.length - 1)
            if (inner.contains("MF1;") || inner.isBlank()) return@replace if (inner.isBlank()) "" else m.value
            val tags = TAG.findAll(inner).map { it.value }.toList()
            if (tags.isEmpty() || tags.joinToString("") != inner) return@replace m.value // comment or drawing block: keep
            val names = tags.map { tagName(it) }
            val keep = tags.indices.filter { i ->
                val n = names[i]
                n == null || n.startsWith("t(") || n in KEEP_ALL || names.lastIndexOf(n) == i
            }
            val out = keep.joinToString("") { tags[it] }
            if (out.isEmpty()) "" else "{$out}"
        }
        return t
    }

    private val KEEP_ALL = setOf("k", "K", "kf", "ko", "r", "p")
    private fun tagName(tag: String): String? {
        if (tag.startsWith("\\t(")) return "t(" + tag.hashCode()
        return Regex("""^\\([1-4]?[a-zA-Z]+)""").find(tag)?.groupValues?.get(1)
    }
}

/** 23.c: quality checks that only report (the Fix dialog changes things; this list points at lines to look at). */
object Qc {
    enum class Kind { OVERLAP, SHORT_GAP, TOO_FAST, TOO_LONG_LINE, TOO_MANY_LINES, SHORT, LONG, EMPTY, MISSING_STYLE }
    data class Issue(val line: Int, val kind: Kind, val value: String)

    data class Limits(val maxCps: Double = 17.0, val maxLineChars: Int = 42, val maxLines: Int = 2, val minMs: Long = 500, val maxMs: Long = 7000, val minGapMs: Long = 24)

    fun run(f: SubFile, l: Limits = Limits()): List<Issue> {
        val out = ArrayList<Issue>()
        val styles = f.styles.map { it.name }.toSet()
        val byLayer = f.events.indices.filter { !f.events[it].comment }.groupBy { f.events[it].layer }
        for ((_, idx) in byLayer) {
            val ordered = idx.sortedBy { f.events[it].startMs }
            for ((k, i) in ordered.withIndex()) {
                val e = f.events[i]
                val nx = ordered.getOrNull(k + 1)?.let { f.events[it] }
                if (nx != null && nx.startMs > e.startMs && e.endMs > nx.startMs) out += Issue(i, Kind.OVERLAP, "${e.endMs - nx.startMs} ms")
                else if (nx != null && nx.startMs - e.endMs in 1 until l.minGapMs) out += Issue(i, Kind.SHORT_GAP, "${nx.startMs - e.endMs} ms")
            }
        }
        for ((i, e) in f.events.withIndex()) {
            if (e.comment) continue
            val plain = AssLook.plainText(e.text)
            if (plain.isBlank() && !e.text.contains("\\p")) { out += Issue(i, Kind.EMPTY, ""); continue }
            val cps = ReadOps.cps(e)
            if (cps > l.maxCps) out += Issue(i, Kind.TOO_FAST, String.format(java.util.Locale.US, "%.1f", cps))
            val lines = plain.split('\n')
            val longest = lines.maxOf { ReadOps.chars(it) }
            if (longest > l.maxLineChars) out += Issue(i, Kind.TOO_LONG_LINE, longest.toString())
            if (lines.size > l.maxLines) out += Issue(i, Kind.TOO_MANY_LINES, lines.size.toString())
            if (e.durationMs < l.minMs) out += Issue(i, Kind.SHORT, "${e.durationMs} ms")
            if (e.durationMs > l.maxMs) out += Issue(i, Kind.LONG, "${e.durationMs / 1000} s")
            if (e.style !in styles) out += Issue(i, Kind.MISSING_STYLE, e.style)
        }
        return out.sortedWith(compareBy({ it.line }, { it.kind.ordinal }))
    }
}
