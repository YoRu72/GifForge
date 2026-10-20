package com.mediaforge.app.subs

/**
 * 19.c / 19.d: ASS attachments ([Fonts] / [Graphics]) and the fonts collector.
 * Encoding is the SSA variant of uuencode (Aegisub libaegisub/ass/uuencode.cpp): bytes in groups of three become
 * four characters, each 6 bits + 33, lines of 80 characters; a short last group is padded with the needed characters
 * only, no length prefix and no "begin/end" lines.
 */
object Attachments {
    fun encode(bytes: ByteArray): List<String> {
        val sb = StringBuilder(bytes.size * 4 / 3 + 80)
        val out = ArrayList<String>()
        var i = 0
        while (i < bytes.size) {
            val n = minOf(3, bytes.size - i)
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (n > 1) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (n > 2) bytes[i + 2].toInt() and 0xFF else 0
            val chars = charArrayOf(
                ((b0 shr 2) + 33).toChar(),
                ((((b0 and 3) shl 4) or (b1 shr 4)) + 33).toChar(),
                ((((b1 and 15) shl 2) or (b2 shr 6)) + 33).toChar(),
                ((b2 and 63) + 33).toChar(),
            )
            for (k in 0 until n + 1) { sb.append(chars[k]); if (sb.length == 80) { out += sb.toString(); sb.setLength(0) } }
            i += 3
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }

    fun decode(lines: List<String>): ByteArray {
        val chars = lines.joinToString("") { it.trim() }
        val out = java.io.ByteArrayOutputStream(chars.length * 3 / 4)
        var i = 0
        while (i < chars.length) {
            val n = minOf(4, chars.length - i)
            val c = IntArray(4) { k -> if (k < n) (chars[i + k].code - 33) and 63 else 0 }
            out.write((c[0] shl 2) or (c[1] shr 4))
            if (n > 2) out.write(((c[1] and 15) shl 4) or (c[2] shr 2))
            if (n > 3) out.write(((c[2] and 3) shl 6) or c[3])
            i += 4
        }
        return out.toByteArray()
    }

    /** Font family names the script uses: every style and every \fn tag (trimmed, case kept). */
    fun fontsUsed(f: SubFile): Set<String> {
        val out = LinkedHashSet<String>()
        f.styles.forEach { out += it.fontName.trim() }
        val fn = Regex("""\\fn([^\\}]*)""")
        for (e in f.events) for (m in fn.findAll(e.text)) m.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { out += it }
        return out
    }

    /** Family names used by the script that neither the library [available] nor the attachments cover. */
    fun missing(f: SubFile, available: Set<String>): Set<String> {
        val have = available.map { it.lowercase() }.toSet()
        // attached names carry Aegisub's style flags: Name_0.ttf regular, _B bold, _I italic, _BI
        val attached = f.attachments.filter { it.section.equals("Fonts", true) }
            .map { it.name.substringBeforeLast('.').replace(Regex("_(0|B|I|BI)$"), "").lowercase() }.toSet()
        return fontsUsed(f).filter { it.lowercase() !in have && it.lowercase() !in attached && !it.equals("Arial", true) }.toSet()
    }

    fun attachFont(f: SubFile, fileName: String, bytes: ByteArray): SubFile {
        val rest = f.attachments.filter { !(it.section.equals("Fonts", true) && it.name.equals(fileName, true)) }
        return f.copy(attachments = rest + Attachment("Fonts", fileName, encode(bytes)))
    }

    fun remove(f: SubFile, name: String): SubFile = f.copy(attachments = f.attachments.filter { !it.name.equals(name, true) })
}
