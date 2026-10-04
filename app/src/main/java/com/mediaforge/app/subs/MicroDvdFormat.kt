package com.mediaforge.app.subs

import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MicroDVD (.sub): `{startFrame}{endFrame}text`, `|` = line break, optional first line `{1}{1}fps`.
 * The frame rate is kept in the script info key `sub:fps` so a save writes the same frames back.
 */
object MicroDvdFormat : SubFormat {
    override val id = "sub"
    override val label = "MicroDVD (.sub)"
    override val extensions = listOf("sub")

    private const val DEFAULT_FPS = 23.976
    private val LINE = Regex("""^\{(\d+)\}\{(\d*)\}(.*)$""")
    private val STYLE = Regex("""\{[yY]:([ibusIBUS]+)\}""")
    private val COLOR = Regex("""\{[cC]:[$]?([0-9a-fA-F]{6})\}""")

    override fun detect(text: String): Boolean = Regex("""^\{\d+\}\{\d*\}""", RegexOption.MULTILINE).containsMatchIn(text)

    override fun parse(text: String): SubFile {
        var fps = DEFAULT_FPS
        var declared = false
        class Raw(val a: Long, val b: Long?, val t: String)
        val raws = ArrayList<Raw>()
        for (line in text.removePrefix("\uFEFF").split("\r\n", "\n", "\r")) {
            val m = LINE.find(line.trim()) ?: continue
            val a = m.groupValues[1].toLong()
            val b = m.groupValues[2].toLongOrNull()
            val t = m.groupValues[3]
            if (raws.isEmpty() && !declared && a <= 1 && (b ?: 0L) <= 1 && t.trim().toDoubleOrNull() != null) {
                fps = t.trim().toDouble().takeIf { it in 1.0..120.0 } ?: DEFAULT_FPS
                declared = true
                continue
            }
            raws += Raw(a, b, t)
        }
        val events = raws.map { r ->
            val start = (r.a * 1000.0 / fps).roundToInt().toLong()
            val end = r.b?.takeIf { it > r.a }?.let { (it * 1000.0 / fps).roundToInt().toLong() } ?: (start + 2000)
            AssEvent(startMs = start, endMs = end, text = toAss(r.t))
        }
        val base = SubFile.blank()
        return base.copy(info = base.info + InfoLine("sub:fps", "%.3f".format(Locale.US, fps)), events = events)
    }

    private fun toAss(raw: String): String {
        val prefix = StringBuilder()
        for (m in STYLE.findAll(raw)) for (c in m.groupValues[1].lowercase()) prefix.append("{\\").append(c).append("1}")
        var t = raw.replace(Regex("""\{[yY]:[^}]*\}"""), "")
        t = COLOR.replace(t) { "{\\c&H" + it.groupValues[1].uppercase(Locale.US) + "&}" } // MicroDVD colour is already BGR
        t = t.replace(Regex("""\{[a-zA-Z]:[^}]*\}"""), "") // font, size, position codes are dropped
        return prefix.toString() + t.replace("|", "\\N")
    }

    override fun write(file: SubFile): String {
        val fps = file.info("sub:fps")?.toDoubleOrNull()?.takeIf { it in 1.0..120.0 } ?: DEFAULT_FPS
        val sb = StringBuilder("{1}{1}").append("%.3f".format(Locale.US, fps)).append('\n')
        for (e in file.events) {
            if (e.comment) continue
            val f1 = (e.startMs * fps / 1000.0).roundToInt()
            val f2 = max((e.endMs * fps / 1000.0).roundToInt(), f1 + 1)
            val flags = listOf("i", "b", "u", "s").filter { Regex("\\\\${it}1").containsMatchIn(e.text) }
            val style = if (flags.isEmpty()) "" else "{y:" + flags.joinToString("") + "}"
            val plain = SrtFormat.assToSrt(e.text, emptySet()).trim('\n').replace("\n", "|")
            sb.append('{').append(f1).append("}{").append(f2).append('}').append(style).append(plain).append('\n')
        }
        return sb.toString()
    }
}
