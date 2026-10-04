package com.mediaforge.app.subs

import java.util.Locale

/** Advanced SubStation Alpha (.ass) and SubStation Alpha (.ssa) - the lossless internal format. */
object AssFormat : SubFormat {
    override val id = "ass"
    override val label = "Advanced SubStation Alpha (.ass)"
    override val extensions = listOf("ass", "ssa")

    private val STYLE_FORMAT = "Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, " +
        "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, " +
        "Alignment, MarginL, MarginR, MarginV, Encoding"
    private val EVENT_FORMAT = "Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"
    private val DEFAULT_STYLE_COLS = STYLE_FORMAT.split(",").map { it.trim().lowercase() }
    private val DEFAULT_EVENT_COLS = EVENT_FORMAT.split(",").map { it.trim().lowercase() }

    override fun detect(text: String): Boolean =
        text.contains("[Script Info]", true) || text.contains("[V4+ Styles]", true) ||
            text.contains("[V4 Styles]", true) || text.contains("\n[Events]", true)

    // ------------------------------------------------------------ parse

    override fun parse(text: String): SubFile {
        val lines = text.removePrefix("\uFEFF").split("\r\n", "\n", "\r")
        var section = ""
        val info = ArrayList<InfoLine>()
        val garbage = ArrayList<InfoLine>()
        val styles = ArrayList<AssStyle>()
        val events = ArrayList<AssEvent>()
        val attachments = ArrayList<Attachment>()
        val extradata = ArrayList<String>()
        val others = LinkedHashMap<String, MutableList<String>>()
        var styleCols = DEFAULT_STYLE_COLS
        var eventCols = DEFAULT_EVENT_COLS
        var legacyStyles = false
        var attName: String? = null
        var attLines = ArrayList<String>()

        fun flushAttachment(sec: String) {
            val n = attName
            if (n != null) attachments += Attachment(sec, n, attLines.toList())
            attName = null
            attLines = ArrayList()
        }

        for (line in lines) {
            val t = line.trim()
            if (t.startsWith("[") && t.endsWith("]") && t.length > 2) {
                if (section.equals("Fonts", true) || section.equals("Graphics", true)) flushAttachment(section)
                section = t.substring(1, t.length - 1).trim()
                if (!isKnown(section)) others.getOrPut(section) { ArrayList() }
                if (section.startsWith("V4", true) && section.contains("Styles", true)) legacyStyles = !section.contains("+")
                continue
            }
            when {
                section.equals("Script Info", true) -> {
                    if (t.isEmpty()) continue
                    val i = t.indexOf(':')
                    if (t.startsWith(";") || t.startsWith("!:") || i < 0) info += InfoLine("", t)
                    else info += InfoLine(t.substring(0, i).trim(), t.substring(i + 1).trim())
                }
                section.equals("Aegisub Project Garbage", true) -> {
                    val i = t.indexOf(':')
                    if (t.isNotEmpty() && i > 0) garbage += InfoLine(t.substring(0, i).trim(), t.substring(i + 1).trim())
                }
                section.startsWith("V4", true) && section.contains("Styles", true) -> {
                    val i = t.indexOf(':')
                    if (i < 0) continue
                    val key = t.substring(0, i).trim().lowercase()
                    val value = t.substring(i + 1).trimStart()
                    if (key == "format") styleCols = value.split(",").map { it.trim().lowercase() }
                    else if (key == "style") styles += styleFrom(styleCols, value.split(",", limit = styleCols.size).map { it.trim() }, legacyStyles)
                }
                section.equals("Events", true) -> {
                    val i = t.indexOf(':')
                    if (i < 0) continue
                    val key = t.substring(0, i).trim().lowercase()
                    val value = line.substring(line.indexOf(':') + 1).removePrefix(" ")
                    when (key) {
                        "format" -> eventCols = value.split(",").map { it.trim().lowercase() }
                        "dialogue", "comment" -> eventFrom(eventCols, value, key == "comment")?.let { events += it }
                    }
                }
                section.equals("Fonts", true) || section.equals("Graphics", true) -> {
                    val lower = t.lowercase()
                    if (lower.startsWith("fontname:") || lower.startsWith("filename:")) {
                        flushAttachment(section)
                        attName = t.substring(t.indexOf(':') + 1).trim()
                    } else if (t.isEmpty()) {
                        flushAttachment(section)
                    } else if (attName != null) {
                        attLines.add(t)
                    }
                }
                section.equals("Aegisub Extradata", true) -> if (t.isNotEmpty()) extradata += t
                else -> others[section]?.add(line)
            }
        }
        if (section.equals("Fonts", true) || section.equals("Graphics", true)) flushAttachment(section)

        return SubFile(
            info = info,
            garbage = garbage,
            styles = styles.ifEmpty { listOf(AssStyle()) },
            events = events,
            attachments = attachments,
            extradata = extradata,
            otherSections = others.map { (k, v) -> k to v.dropLastWhile { it.isBlank() } },
        )
    }

    private fun isKnown(s: String) =
        s.equals("Script Info", true) || s.equals("Aegisub Project Garbage", true) ||
            (s.startsWith("V4", true) && s.contains("Styles", true)) || s.equals("Events", true) ||
            s.equals("Fonts", true) || s.equals("Graphics", true) || s.equals("Aegisub Extradata", true)

    private fun legacyToNumpad(a: Int): Int = when (a) {
        in 1..3 -> a
        in 5..7 -> a + 2   // top: 5,6,7 -> 7,8,9
        in 9..11 -> a - 5  // middle: 9,10,11 -> 4,5,6
        else -> 2
    }

    private fun styleFrom(cols: List<String>, f: List<String>, legacy: Boolean): AssStyle {
        fun s(name: String): String? = cols.indexOf(name).let { if (it in f.indices) f[it] else null }
        fun i(name: String, d: Int) = s(name)?.toIntOrNull() ?: d
        fun fl(name: String, d: Float) = s(name)?.toFloatOrNull() ?: d
        fun flag(name: String) = (s(name)?.toIntOrNull() ?: 0) != 0
        val align = i("alignment", 2)
        return AssStyle(
            name = s("name") ?: "Default",
            fontName = s("fontname") ?: "Arial",
            fontSize = fl("fontsize", 48f),
            primary = parseAssColor(s("primarycolour") ?: "&H00FFFFFF"),
            secondary = parseAssColor(s("secondarycolour") ?: "&H000000FF"),
            outline = parseAssColor(s("outlinecolour") ?: s("tertiarycolour") ?: "&H00000000"),
            back = parseAssColor(s("backcolour") ?: "&H00000000"),
            bold = flag("bold"), italic = flag("italic"), underline = flag("underline"), strikeOut = flag("strikeout"),
            scaleX = fl("scalex", 100f), scaleY = fl("scaley", 100f),
            spacing = fl("spacing", 0f), angle = fl("angle", 0f),
            borderStyle = i("borderstyle", 1),
            outlineWidth = fl("outline", 2f), shadow = fl("shadow", 2f),
            alignment = if (legacy) legacyToNumpad(align) else align.coerceIn(1, 9),
            marginL = i("marginl", 10), marginR = i("marginr", 10), marginV = i("marginv", 10),
            encoding = i("encoding", 1),
        )
    }

    private fun eventFrom(cols: List<String>, value: String, comment: Boolean): AssEvent? {
        val f = value.split(",", limit = cols.size)
        fun s(name: String): String? = cols.indexOf(name).let { if (it in f.indices) f[it] else null }
        val start = parseAssTime(s("start") ?: return null) ?: return null
        val end = parseAssTime(s("end") ?: return null) ?: return null
        return AssEvent(
            comment = comment,
            layer = (s("layer") ?: s("marked"))?.trim()?.removePrefix("Marked=")?.toIntOrNull() ?: 0,
            startMs = start,
            endMs = end,
            style = s("style")?.trim() ?: "Default",
            actor = s("name")?.trim() ?: s("actor")?.trim() ?: "",
            marginL = s("marginl")?.trim()?.toIntOrNull() ?: 0,
            marginR = s("marginr")?.trim()?.toIntOrNull() ?: 0,
            marginV = s("marginv")?.trim()?.toIntOrNull() ?: 0,
            effect = s("effect")?.trim() ?: "",
            text = s("text") ?: "",
        )
    }

    // ------------------------------------------------------------ write

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        sb.append("[Script Info]\n")
        var info = file.info
        if (info.isEmpty()) info = listOf(InfoLine("", "; Script generated by MediaForge"))
        if (info.none { it.key.equals("ScriptType", true) }) {
            val firstReal = info.indexOfFirst { it.key.isNotEmpty() }.let { if (it < 0) info.size else it }
            info = info.take(firstReal) + InfoLine("ScriptType", "v4.00+") + info.drop(firstReal)
        }
        for (l in info) sb.append(if (l.key.isEmpty()) l.value else "${l.key}: ${l.value}").append('\n')

        if (file.garbage.isNotEmpty()) {
            sb.append("\n[Aegisub Project Garbage]\n")
            for (l in file.garbage) sb.append("${l.key}: ${l.value}\n")
        }

        sb.append("\n[V4+ Styles]\nFormat: ").append(STYLE_FORMAT).append('\n')
        for (st in file.styles) sb.append(styleLine(st)).append('\n')

        sb.append("\n[Events]\nFormat: ").append(EVENT_FORMAT).append('\n')
        for (e in file.events) sb.append(eventLine(e)).append('\n')

        for (sec in file.attachments.map { it.section }.distinct()) {
            sb.append("\n[").append(sec).append("]\n")
            val key = if (sec.equals("Graphics", true)) "filename" else "fontname"
            for (a in file.attachments.filter { it.section == sec }) {
                sb.append("$key: ${a.name}\n")
                for (l in a.lines) sb.append(l).append('\n')
                sb.append('\n')
            }
        }
        if (file.extradata.isNotEmpty()) {
            sb.append("\n[Aegisub Extradata]\n")
            for (l in file.extradata) sb.append(l).append('\n')
        }
        for ((name, body) in file.otherSections) {
            sb.append("\n[").append(name).append("]\n")
            for (l in body) sb.append(l).append('\n')
        }
        return sb.toString()
    }

    private fun clean(s: String) = s.replace(',', ';').replace("\r", "").replace("\n", " ")

    private fun styleLine(s: AssStyle): String {
        fun b(v: Boolean) = if (v) "-1" else "0"
        return "Style: " + listOf(
            clean(s.name), clean(s.fontName), formatAssNumber(s.fontSize),
            formatAssColor(s.primary), formatAssColor(s.secondary), formatAssColor(s.outline), formatAssColor(s.back),
            b(s.bold), b(s.italic), b(s.underline), b(s.strikeOut),
            formatAssNumber(s.scaleX), formatAssNumber(s.scaleY), formatAssNumber(s.spacing), formatAssNumber(s.angle),
            s.borderStyle.toString(), formatAssNumber(s.outlineWidth), formatAssNumber(s.shadow),
            s.alignment.toString(), s.marginL.toString(), s.marginR.toString(), s.marginV.toString(), s.encoding.toString(),
        ).joinToString(",")
    }

    private fun eventLine(e: AssEvent): String {
        val text = e.text.replace("\r\n", "\\N").replace("\n", "\\N").replace("\r", "\\N")
        return (if (e.comment) "Comment: " else "Dialogue: ") + listOf(
            e.layer.toString(), formatAssTime(e.startMs), formatAssTime(e.endMs), clean(e.style), clean(e.actor),
            e.marginL.toString(), e.marginR.toString(), e.marginV.toString(), clean(e.effect),
        ).joinToString(",") + "," + text
    }
}
