package com.mediaforge.app.subs

import java.util.Locale

/**
 * Lyrics (.lrc): `[mm:ss.xx]text`, several stamps per line, [ti:] [ar:] [al:] [by:] [offset:] tags.
 * Enhanced LRC word stamps `<mm:ss.xx>` become karaoke \k tags (and back on export).
 * LRC has no end times: a line lasts until the next stamp (an empty stamped line ends the previous one).
 */
object LrcFormat : SubFormat {
    override val id = "lrc"
    override val label = "Lyrics (.lrc)"
    override val extensions = listOf("lrc")

    private val STAMP = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?\]""")
    private val WORD = Regex("""<(\d+):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val META = Regex("""^\[([A-Za-z#]+):(.*)\]\s*$""")
    private val K = Regex("""\\[kK][fo]?(\d+)""")

    override fun detect(text: String): Boolean =
        Regex("""^\[\d+:\d{2}(?:[.:]\d{1,3})?\]""", RegexOption.MULTILINE).containsMatchIn(text) && !text.contains("-->")

    private fun ms(min: String, sec: String, frac: String): Long {
        val f = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        return min.toLong() * 60000 + sec.toLong() * 1000 + f
    }

    private class Entry(val t: Long, val text: String)

    override fun parse(text: String): SubFile {
        val meta = ArrayList<InfoLine>()
        var offset = 0L
        val entries = ArrayList<Entry>()
        for (raw in text.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            var pos = 0
            val stamps = ArrayList<Long>()
            while (pos < line.length) {
                val m = STAMP.find(line, pos)
                if (m == null || m.range.first != pos) break
                stamps += ms(m.groupValues[1], m.groupValues[2], m.groupValues[3])
                pos = m.range.last + 1
            }
            if (stamps.isEmpty()) {
                META.find(line)?.let { mm ->
                    val k = mm.groupValues[1].lowercase()
                    val v = mm.groupValues[2].trim()
                    if (k == "offset") offset = v.toLongOrNull() ?: 0L else meta += InfoLine("lrc:$k", v)
                }
                continue
            }
            val body = line.substring(pos).trim()
            for (t in stamps) entries += Entry(t, body)
        }
        entries.sortBy { it.t }
        val events = ArrayList<AssEvent>()
        for ((i, e) in entries.withIndex()) {
            if (e.text.isBlank()) continue
            val nextT = entries.drop(i + 1).firstOrNull { it.t > e.t }?.t
            val endAbs = nextT ?: (e.t + 4000)
            val start = (e.t - offset).coerceAtLeast(0L)
            val end = (endAbs - offset).coerceAtLeast(start + 1)
            events += AssEvent(startMs = start, endMs = end, text = wordsToKaraoke(e.text, endAbs))
        }
        val base = SubFile.blank()
        return base.copy(info = base.info + meta, events = events)
    }

    /** `<t1>word <t2>word` -> `{\k..}word {\k..}word` using the gaps between stamps. */
    private fun wordsToKaraoke(raw: String, lineEndAbs: Long): String {
        val marks = WORD.findAll(raw).toList()
        if (marks.isEmpty()) return raw
        val sb = StringBuilder(raw.substring(0, marks[0].range.first))
        for ((i, m) in marks.withIndex()) {
            val t = ms(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val nextT = if (i + 1 < marks.size) marks[i + 1].let { ms(it.groupValues[1], it.groupValues[2], it.groupValues[3]) } else lineEndAbs
            val textEnd = if (i + 1 < marks.size) marks[i + 1].range.first else raw.length
            sb.append("{\\k").append(((nextT - t) / 10).coerceAtLeast(0)).append('}')
            sb.append(raw.substring(m.range.last + 1, textEnd))
        }
        return sb.toString()
    }

    private fun stamp(t: Long): String =
        "%02d:%02d.%02d".format(Locale.US, t / 60000, (t / 1000) % 60, (t % 1000) / 10)

    /** Keeps karaoke timing as enhanced `<mm:ss.xx>` stamps; everything else is plain text. */
    fun assToLrc(t: String, startMs: Long): String {
        val sb = StringBuilder()
        var cum = startMs
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '{') {
                val j = t.indexOf('}', i)
                if (j > 0) {
                    K.find(t.substring(i + 1, j))?.let { k ->
                        sb.append('<').append(stamp(cum)).append('>')
                        cum += k.groupValues[1].toLong() * 10
                    }
                    i = j + 1
                    continue
                }
            }
            if (c == '\\' && i + 1 < t.length && (t[i + 1] == 'N' || t[i + 1] == 'n' || t[i + 1] == 'h')) {
                sb.append(' ')
                i += 2
                continue
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        val metaLines = file.info.filter { it.key.startsWith("lrc:") }
        for (l in metaLines) sb.append('[').append(l.key.removePrefix("lrc:")).append(':').append(l.value).append("]\n")
        if (metaLines.none { it.key == "lrc:ti" }) file.info("Title")?.takeIf { it.isNotBlank() }?.let { sb.append("[ti:").append(it).append("]\n") }
        val list = file.events.filter { !it.comment }.sortedBy { it.startMs }
        for ((i, e) in list.withIndex()) {
            sb.append('[').append(stamp(e.startMs)).append(']').append(assToLrc(e.text, e.startMs)).append('\n')
            val next = list.getOrNull(i + 1)
            if (next == null || next.startMs > e.endMs + 100) sb.append('[').append(stamp(e.endMs)).append("]\n") // blank stamp ends the line
        }
        return sb.toString()
    }
}
