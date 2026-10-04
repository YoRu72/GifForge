package com.mediaforge.app.subs

import java.util.Locale

/** SubRip (.srt). Basic <i> <b> <u> <s> <font color> tags map to ASS \i \b \u \s \c. */
object SrtFormat : SubFormat {
    override val id = "srt"
    override val label = "SubRip (.srt)"
    override val extensions = listOf("srt")

    private val TIME = Regex("""(\d+):(\d{1,2}):(\d{1,2})[,.](\d{1,3})""")
    private val ARROW = Regex("""(\d+:\d{1,2}:\d{1,2}[,.]\d{1,3})\s*-->\s*(\d+:\d{1,2}:\d{1,2}[,.]\d{1,3})""")

    override fun detect(text: String): Boolean = ARROW.containsMatchIn(text) && !text.contains("WEBVTT")

    private fun parseTime(s: String): Long? {
        val m = TIME.matchEntire(s.trim()) ?: return null
        val (h, mi, se, frac) = m.destructured
        val ms = when (frac.length) { 1 -> frac.toInt() * 100; 2 -> frac.toInt() * 10; else -> frac.toInt() }
        return ((h.toLong() * 60 + mi.toLong()) * 60 + se.toLong()) * 1000 + ms
    }

    override fun parse(text: String): SubFile {
        val norm = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val events = ArrayList<AssEvent>()
        for (block in norm.split(Regex("\n[ \t]*\n"))) {
            val lines = block.trim('\n').split('\n')
            val at = lines.indexOfFirst { ARROW.containsMatchIn(it) }
            if (at < 0) continue
            val m = ARROW.find(lines[at])!!
            val start = parseTime(m.groupValues[1]) ?: continue
            val end = parseTime(m.groupValues[2]) ?: continue
            val body = lines.drop(at + 1).joinToString("\\N") { srtToAss(it) }
            events += AssEvent(startMs = start, endMs = end, text = body)
        }
        return SubFile.blank().copy(events = events)
    }

    private val FONT_COLOR = Regex("""<font[^>]*?color\s*=\s*["']?#?([0-9a-fA-F]{6})["']?[^>]*>""", RegexOption.IGNORE_CASE)
    private val TAG_MAP = listOf("i", "b", "u", "s")

    internal fun srtToAss(line: String): String {
        var t = FONT_COLOR.replace(line) { m ->
            val rgb = m.groupValues[1]
            "{\\c&H${rgb.substring(4, 6)}${rgb.substring(2, 4)}${rgb.substring(0, 2)}&}".uppercase(Locale.US).replace("\\C&H", "\\c&H")
        }
        t = t.replace(Regex("</font\\s*>", RegexOption.IGNORE_CASE), "{\\c}")
        for (x in TAG_MAP) {
            t = t.replace(Regex("<$x>", RegexOption.IGNORE_CASE), "{\\\\${x}1}")
            t = t.replace(Regex("</$x>", RegexOption.IGNORE_CASE), "{\\\\${x}0}")
        }
        return t.replace(Regex("<[^>]+>"), "")
    }

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        var n = 1
        for (e in file.events) {
            if (e.comment) continue
            sb.append(n++).append('\n')
            sb.append(fmt(e.startMs)).append(" --> ").append(fmt(e.endMs)).append('\n')
            sb.append(assToSrt(e.text).trim('\n').replace(Regex("\n{2,}"), "\n")).append("\n\n")
        }
        return sb.toString()
    }

    private fun fmt(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return "%02d:%02d:%02d,%03d".format(Locale.US, t / 3600000, (t / 60000) % 60, (t / 1000) % 60, t % 1000)
    }

    private val SIMPLE = Regex("""\\([ibus])(\d+)""")

    /** Drops override blocks but keeps italic/bold/underline/strike as HTML-style tags. */
    fun assToSrt(t: String, keys: Set<String> = setOf("i", "b", "u", "s"), escape: Boolean = false): String {
        val sb = StringBuilder()
        val open = HashMap<String, Boolean>()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '{') {
                val j = t.indexOf('}', i)
                if (j > 0) {
                    for (m in SIMPLE.findAll(t.substring(i + 1, j))) {
                        val key = m.groupValues[1]
                        if (key !in keys) continue
                        val on = m.groupValues[2] != "0"
                        if (on != (open[key] == true)) {
                            sb.append(if (on) "<$key>" else "</$key>")
                            open[key] = on
                        }
                    }
                    i = j + 1
                    continue
                }
            }
            if (c == '\\' && i + 1 < t.length) {
                when (t[i + 1]) {
                    'N', 'n' -> { sb.append('\n'); i += 2; continue }
                    'h' -> { sb.append(' '); i += 2; continue }
                }
            }
            if (escape) {
                when (c) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    else -> sb.append(c)
                }
            } else sb.append(c)
            i++
        }
        for ((k, on) in open) if (on) sb.append("</$k>")
        return sb.toString()
    }
}
