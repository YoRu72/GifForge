package com.mediaforge.app.subs

/**
 * Plain text script: one line of text per event, `#` starts a comment line. There are no times in a text
 * file, so imported lines get 0:00:00.00 - 0:00:00.00 and are timed later (timing tools, waveform).
 * Never auto-detected: chosen by the .txt extension or by the user.
 */
object TxtFormat : SubFormat {
    override val id = "txt"
    override val label = "Plain text (.txt)"
    override val extensions = listOf("txt")

    override fun detect(text: String): Boolean = false

    override fun parse(text: String): SubFile {
        val events = ArrayList<AssEvent>()
        for (raw in text.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) events += AssEvent(comment = true, startMs = 0, endMs = 0, text = line.trimStart('#').trim())
            else events += AssEvent(startMs = 0, endMs = 0, text = line)
        }
        return SubFile.blank().copy(events = events)
    }

    override fun write(file: SubFile): String {
        val sb = StringBuilder()
        for (e in file.events) {
            val plain = SrtFormat.assToSrt(e.text, emptySet()).replace('\n', ' ').trim()
            sb.append(if (e.comment) "# $plain" else plain).append('\n')
        }
        return sb.toString()
    }
}
