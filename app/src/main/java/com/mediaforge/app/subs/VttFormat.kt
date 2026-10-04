package com.mediaforge.app.subs

/** WebVTT (.vtt): cues, <i><b><u> tags, <v Speaker> -> actor, align/line settings <-> \an. */
object VttFormat : SubFormat {
    override val id = "vtt"
    override val label = "WebVTT (.vtt)"
    override val extensions = listOf("vtt")

    private val CUE = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})(.*)""")
    private val VOICE = Regex("""^<v(?:\.[^\s>]*)?\s+([^>]*)>""")
    private val ALIGN = Regex("""align:(\w+)""")
    private val LINE = Regex("""line:(-?[\d.]+)(%?)""")
    private val AN = Regex("""\\an([1-9])""")

    override fun detect(text: String): Boolean = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("WEBVTT")

    override fun parse(text: String): SubFile {
        val norm = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val events = ArrayList<AssEvent>()
        for (block in norm.split(Regex("\n[ \t]*\n"))) {
            val lines = block.trim('\n').split('\n')
            val at = lines.indexOfFirst { CUE.containsMatchIn(it) }
            if (at < 0) continue // header, NOTE, STYLE, REGION blocks
            val m = CUE.find(lines[at])!!
            val start = parseClock(m.groupValues[1]) ?: continue
            val end = parseClock(m.groupValues[2]) ?: continue
            var actor = ""
            val body = lines.drop(at + 1).mapIndexed { i, l ->
                var line = l
                if (i == 0) VOICE.find(line)?.let { v -> actor = v.groupValues[1].trim(); line = line.substring(v.range.last + 1) }
                decodeEntities(SrtFormat.srtToAss(line))
            }.joinToString("\\N")
            val an = anFrom(m.groupValues[3])
            events += AssEvent(startMs = start, endMs = end, actor = actor, text = (if (an != null) "{\\an$an}" else "") + body)
        }
        return SubFile.blank().copy(events = events)
    }

    /** align:start/end + line:% -> numpad alignment; null = default bottom centre. */
    private fun anFrom(settings: String): Int? {
        val col = when (ALIGN.find(settings)?.groupValues?.get(1)) { "start", "left" -> 1; "end", "right" -> 3; else -> 2 }
        var row = 0
        val line = LINE.find(settings)
        val v = line?.groupValues?.get(1)?.toFloatOrNull()
        if (line != null && v != null) {
            row = if (line.groupValues[2] == "%") (if (v < 34) 6 else if (v < 67) 3 else 0) else (if (v in 0f..2f) 6 else 0)
        }
        val an = row + col
        return if (an == 2) null else an
    }

    private fun settingsFor(text: String): String {
        val an = AN.find(text)?.groupValues?.get(1)?.toInt() ?: return ""
        val col = (an - 1) % 3
        val row = (an - 1) / 3
        val sb = StringBuilder()
        if (col == 0) sb.append(" align:start") else if (col == 2) sb.append(" align:end")
        if (row == 2) sb.append(" line:0") else if (row == 1) sb.append(" line:50%")
        return sb.toString()
    }

    override fun write(file: SubFile): String {
        val sb = StringBuilder("WEBVTT\n\n")
        var n = 1
        for (e in file.events) {
            if (e.comment) continue
            sb.append(n++).append('\n')
            sb.append(formatClock(e.startMs)).append(" --> ").append(formatClock(e.endMs)).append(settingsFor(e.text)).append('\n')
            if (e.actor.isNotBlank()) sb.append("<v ").append(e.actor).append('>')
            sb.append(SrtFormat.assToSrt(e.text, setOf("i", "b", "u"), escape = true).trim('\n').replace(Regex("\n{2,}"), "\n"))
            sb.append("\n\n")
        }
        return sb.toString()
    }
}
