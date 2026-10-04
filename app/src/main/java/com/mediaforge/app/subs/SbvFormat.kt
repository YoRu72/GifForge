package com.mediaforge.app.subs

import java.util.Locale

/** YouTube SBV (.sbv): `H:MM:SS.mmm,H:MM:SS.mmm` then text, blocks separated by a blank line. No styling. */
object SbvFormat : SubFormat {
    override val id = "sbv"
    override val label = "YouTube SBV (.sbv)"
    override val extensions = listOf("sbv")

    private val T = Regex("""^(\d+:\d{2}:\d{2}\.\d{1,3}),(\d+:\d{2}:\d{2}\.\d{1,3})\s*$""")

    override fun detect(text: String): Boolean = Regex(T.pattern, RegexOption.MULTILINE).containsMatchIn(text)

    override fun parse(text: String): SubFile {
        val norm = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        val events = ArrayList<AssEvent>()
        for (block in norm.split(Regex("\n[ \t]*\n"))) {
            val lines = block.trim('\n').split('\n')
            val m = T.find(lines.firstOrNull()?.trim() ?: continue) ?: continue
            val start = parseClock(m.groupValues[1]) ?: continue
            val end = parseClock(m.groupValues[2]) ?: continue
            events += AssEvent(startMs = start, endMs = end, text = lines.drop(1).joinToString("\\N"))
        }
        return SubFile.blank().copy(events = events)
    }

    private fun fmt(ms: Long): String {
        val t = ms.coerceAtLeast(0L)
        return "%d:%02d:%02d.%03d".format(Locale.US, t / 3600000, (t / 60000) % 60, (t / 1000) % 60, t % 1000)
    }

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        for (e in file.events) {
            if (e.comment) continue
            sb.append(fmt(e.startMs)).append(',').append(fmt(e.endMs)).append('\n')
            sb.append(SrtFormat.assToSrt(e.text, emptySet()).trim('\n').replace(Regex("\n{2,}"), "\n")).append("\n\n")
        }
        return sb.toString()
    }
}
