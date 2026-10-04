package com.mediaforge.app.subs

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** TTML / DFXP (.ttml .dfxp .xml): <p begin end>, <br/>, styled <span>, alignment attributes. */
object TtmlFormat : SubFormat {
    override val id = "ttml"
    override val label = "TTML / DFXP (.ttml)"
    override val extensions = listOf("ttml", "dfxp", "xml")

    private val P = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""")
    private val STYLE = Regex("""<style\b([^>]*?)/?>""")
    private val TOK = Regex("""<(/?)(span|br)\b([^>]*?)(/?)>""")
    private val CLOCK = Regex("""^(\d+):(\d{2}):(\d{2})(?:\.(\d+)|:(\d+)(?:\.\d+)?)?$""")
    private val OFFSET = Regex("""^(\d+(?:\.\d+)?)(h|ms|m|s|f)$""")
    private val SIMPLE = Regex("""\\([ibus])(\d+)""")
    private val AN = Regex("""\\an([1-9])""")

    override fun detect(text: String): Boolean = Regex("""<tt[\s>]""").containsMatchIn(text) && text.contains("<p")

    private fun attr(attrs: String, name: String): String? =
        Regex("""(?:^|\s|:)$name\s*=\s*["']([^"']*)["']""").find(attrs)?.groupValues?.get(1)

    private fun time(s: String, fps: Double): Long? {
        val t = s.trim()
        CLOCK.find(t)?.let { m ->
            var ms = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000
            if (m.groupValues[4].isNotEmpty()) ms += ("0." + m.groupValues[4]).toDouble().times(1000).roundToInt()
            else if (m.groupValues[5].isNotEmpty()) ms += (m.groupValues[5].toLong() * 1000 / fps).roundToLong()
            return ms
        }
        OFFSET.find(t)?.let { m ->
            val v = m.groupValues[1].toDouble()
            val ms = when (m.groupValues[2]) { "h" -> v * 3600000; "m" -> v * 60000; "s" -> v * 1000; "ms" -> v; else -> v * 1000 / fps }
            return ms.roundToLong()
        }
        return null
    }

    override fun parse(text: String): SubFile {
        val t = text.removePrefix("\uFEFF")
        val ttAttrs = Regex("""<tt\b([^>]*)>""").find(t)?.groupValues?.get(1) ?: ""
        val fps = attr(ttAttrs, "frameRate")?.toDoubleOrNull() ?: 30.0
        val styles = HashMap<String, String>()
        for (m in STYLE.findAll(t)) attr(m.groupValues[1], "id")?.let { styles[it] = m.groupValues[1] }
        val events = ArrayList<AssEvent>()
        for (m in P.findAll(t)) {
            val a = m.groupValues[1]
            val begin = attr(a, "begin")?.let { time(it, fps) } ?: continue
            val end = attr(a, "end")?.let { time(it, fps) } ?: attr(a, "dur")?.let { time(it, fps) }?.plus(begin) ?: continue
            val styleAttrs = attr(a, "style")?.let { styles[it] } ?: ""
            val all = "$styleAttrs $a"
            val an = anFrom(all)
            val (on, _) = spanTags(all)
            events += AssEvent(startMs = begin, endMs = end, text = (if (an != null) "{\\an$an}" else "") + on + inner(m.groupValues[2]))
        }
        return SubFile.blank().copy(events = events)
    }

    private fun anFrom(attrs: String): Int? {
        val h = attr(attrs, "textAlign")
        val v = attr(attrs, "displayAlign")
        if (h == null && v == null) return null
        val col = when (h) { "left", "start" -> 1; "right", "end" -> 3; else -> 2 }
        val row = when (v) { "before" -> 6; "center" -> 3; else -> 0 }
        val an = row + col
        return if (an == 2) null else an
    }

    /** ASS tags that switch a TTML style on, and the ones that switch it off again. */
    private fun spanTags(attrs: String): Pair<String, String> {
        val on = StringBuilder()
        val off = StringBuilder()
        if (attr(attrs, "fontStyle") == "italic") { on.append("{\\i1}"); off.append("{\\i0}") }
        if (attr(attrs, "fontWeight") == "bold") { on.append("{\\b1}"); off.append("{\\b0}") }
        val deco = attr(attrs, "textDecoration") ?: ""
        if (deco.contains("underline")) { on.append("{\\u1}"); off.append("{\\u0}") }
        if (deco.contains("lineThrough")) { on.append("{\\s1}"); off.append("{\\s0}") }
        attr(attrs, "color")?.let { c ->
            val hex = c.trim().removePrefix("#")
            val rgb = if (hex.length >= 6 && hex.take(6).all { it.isLetterOrDigit() }) hex.take(6) else namedColor(c.trim().lowercase())
            if (rgb != null) {
                on.append("{\\c&H").append(rgb.substring(4, 6)).append(rgb.substring(2, 4)).append(rgb.substring(0, 2)).append("&}")
                off.append("{\\c}")
            }
        }
        return on.toString() to off.toString()
    }

    private fun namedColor(n: String): String? = when (n) {
        "white" -> "FFFFFF"; "black" -> "000000"; "red" -> "FF0000"; "yellow" -> "FFFF00"; "cyan" -> "00FFFF"
        "lime" -> "00FF00"; "green" -> "008000"; "blue" -> "0000FF"; "magenta" -> "FF00FF"; else -> null
    }

    private fun inner(raw: String): String {
        val sb = StringBuilder()
        val stack = ArrayList<String>()
        var last = 0
        for (m in TOK.findAll(raw)) {
            sb.append(plain(raw.substring(last, m.range.first)))
            last = m.range.last + 1
            val closing = m.groupValues[1] == "/"
            when {
                m.groupValues[2] == "br" -> sb.append("\\N")
                closing -> if (stack.isNotEmpty()) sb.append(stack.removeAt(stack.size - 1))
                m.groupValues[4] == "/" -> {}
                else -> { val (on, off) = spanTags(m.groupValues[3]); sb.append(on); stack.add(off) }
            }
        }
        sb.append(plain(raw.substring(last)))
        return sb.toString()
    }

    private fun plain(s: String): String =
        decodeXml(s.replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ")

    private fun decodeXml(s: String): String {
        var t = Regex("""&#x([0-9a-fA-F]+);""").replace(s) { String(Character.toChars(it.groupValues[1].toInt(16))) }
        t = Regex("""&#(\d+);""").replace(t) { String(Character.toChars(it.groupValues[1].toInt())) }
        return t.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")
    }

    // ---------------------------------------------------------------- write

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:tts=\"http://www.w3.org/ns/ttml#styling\" xml:lang=\"en\">\n")
        sb.append("  <body>\n    <div>\n")
        for (e in file.events) {
            if (e.comment) continue
            sb.append("      <p begin=\"").append(formatClock(e.startMs)).append("\" end=\"").append(formatClock(e.endMs)).append('"')
            AN.find(e.text)?.groupValues?.get(1)?.toInt()?.let { an ->
                val col = (an - 1) % 3
                val row = (an - 1) / 3
                sb.append(" tts:textAlign=\"").append(if (col == 0) "left" else if (col == 2) "right" else "center").append('"')
                sb.append(" tts:displayAlign=\"").append(if (row == 2) "before" else if (row == 1) "center" else "after").append('"')
            }
            sb.append('>').append(assToTtml(e.text)).append("</p>\n")
        }
        sb.append("    </div>\n  </body>\n</tt>\n")
        return sb.toString()
    }

    private fun assToTtml(t: String): String {
        val sb = StringBuilder()
        val st = BooleanArray(4) // i b u s
        var open = false
        fun reopen() {
            if (open) { sb.append("</span>"); open = false }
            if (st.any { it }) {
                sb.append("<span")
                if (st[0]) sb.append(" tts:fontStyle=\"italic\"")
                if (st[1]) sb.append(" tts:fontWeight=\"bold\"")
                val deco = listOfNotNull(if (st[2]) "underline" else null, if (st[3]) "lineThrough" else null)
                if (deco.isNotEmpty()) sb.append(" tts:textDecoration=\"").append(deco.joinToString(" ")).append('"')
                sb.append('>')
                open = true
            }
        }
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '{') {
                val j = t.indexOf('}', i)
                if (j > 0) {
                    var changed = false
                    for (m in SIMPLE.findAll(t.substring(i + 1, j))) {
                        val idx = "ibus".indexOf(m.groupValues[1])
                        val on = m.groupValues[2] != "0"
                        if (st[idx] != on) { st[idx] = on; changed = true }
                    }
                    if (changed) reopen()
                    i = j + 1
                    continue
                }
            }
            if (c == '\\' && i + 1 < t.length) {
                when (t[i + 1]) {
                    'N', 'n' -> { sb.append("<br/>"); i += 2; continue }
                    'h' -> { sb.append(' '); i += 2; continue }
                }
            }
            when (c) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                else -> sb.append(c)
            }
            i++
        }
        if (open) sb.append("</span>")
        return sb.toString()
    }
}
