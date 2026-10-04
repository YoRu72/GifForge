package com.mediaforge.app.media

import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

data class FontAxis(val tag: String, val min: Float, val def: Float, val max: Float) {
    val label: String
        get() = when (tag) {
            "wght" -> "Weight"; "wdth" -> "Width"; "slnt" -> "Slant"; "ital" -> "Italic"; "opsz" -> "Optical size"
            else -> tag
        }
}

data class FontFace(
    val file: File,
    val ttcIndex: Int,
    val faceCount: Int,
    val family: String,
    val style: String,
    val fullName: String,
    val weight: Int,
    val italic: Boolean,
    val axes: List<FontAxis>,
    val color: Boolean,
    val cff: Boolean,
) {
    /** Stable id used by text layers: plain path, or path::N for faces inside a collection. */
    val ref: String get() = if (ttcIndex == 0) file.absolutePath else "${file.absolutePath}::$ttcIndex"
    val isVariable: Boolean get() = axes.isNotEmpty()
}

/** Reads font metadata (names, weight, variable axes, color tables) straight from the file. Pure Kotlin, bounds-checked. */
object FontParser {
    private const val MAX_TABLE = 4 * 1024 * 1024

    fun parse(file: File): List<FontFace> {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = readAt(raf, 0, 12) ?: return emptyList()
                if (String(head, 0, 4, Charsets.ISO_8859_1) == "ttcf") {
                    val n = u32(head, 8).coerceIn(0L, 64L).toInt()
                    val offs = readAt(raf, 12, n * 4) ?: return emptyList()
                    (0 until n).mapNotNull { i -> face(file, raf, u32(offs, i * 4), i, n) }
                } else {
                    listOfNotNull(face(file, raf, 0L, 0, 1))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun face(file: File, raf: RandomAccessFile, off: Long, idx: Int, count: Int): FontFace? {
        return try {
            val h = readAt(raf, off, 12) ?: return null
            val version = String(h, 0, 4, Charsets.ISO_8859_1)
            val numTables = u16(h, 4).coerceAtMost(200)
            val dir = readAt(raf, off + 12, numTables * 16) ?: return null
            val tables = HashMap<String, LongArray>()
            for (i in 0 until numTables) {
                val o = i * 16
                tables[String(dir, o, 4, Charsets.ISO_8859_1)] = longArrayOf(u32(dir, o + 8), u32(dir, o + 12))
            }
            fun table(tag: String): ByteArray? = tables[tag]?.let { readAt(raf, it[0], it[1].toInt()) }

            val names = table("name")?.let { nameTable(it) } ?: emptyMap()
            val family = names[16] ?: names[1] ?: file.nameWithoutExtension
            val style = names[17] ?: names[2] ?: "Regular"
            val full = names[4] ?: "$family $style"

            var weight = 400
            var italic = false
            table("OS/2")?.let { os2 ->
                if (os2.size >= 6) weight = u16(os2, 4).takeIf { it in 1..1000 } ?: 400
                if (os2.size >= 64) italic = (u16(os2, 62) and 1) != 0
            }
            table("head")?.let { hd -> if (hd.size >= 46 && (u16(hd, 44) and 2) != 0) italic = true }

            val axes = table("fvar")?.let { fvar(it) } ?: emptyList()
            val color = listOf("COLR", "CBDT", "sbix", "SVG ").any { it in tables }

            FontFace(file, idx, count, family, style, full, weight, italic, axes, color, version == "OTTO")
        } catch (e: Exception) {
            null
        }
    }

    private fun fvar(t: ByteArray): List<FontAxis> {
        if (t.size < 16) return emptyList()
        val axesOff = u16(t, 4)
        val count = u16(t, 8).coerceAtMost(32)
        val size = u16(t, 10)
        if (size < 20) return emptyList()
        val out = ArrayList<FontAxis>()
        for (i in 0 until count) {
            val o = axesOff + i * size
            if (o + 20 > t.size) break
            fun fixed(p: Int) = u32(t, p).toInt() / 65536f
            out += FontAxis(String(t, o, 4, Charsets.ISO_8859_1), fixed(o + 4), fixed(o + 8), fixed(o + 12))
        }
        return out
    }

    private fun nameTable(t: ByteArray): Map<Int, String> {
        if (t.size < 6) return emptyMap()
        val count = u16(t, 2)
        val strOff = u16(t, 4)
        val best = HashMap<Int, Pair<Int, String>>()
        val mac: Charset = try { Charset.forName("x-MacRoman") } catch (e: Exception) { Charsets.ISO_8859_1 }
        for (i in 0 until count) {
            val r = 6 + i * 12
            if (r + 12 > t.size) break
            val platform = u16(t, r)
            val lang = u16(t, r + 4)
            val id = u16(t, r + 6)
            if (id !in intArrayOf(1, 2, 4, 16, 17)) continue
            val len = u16(t, r + 8)
            val so = strOff + u16(t, r + 10)
            if (so + len > t.size || len == 0) continue
            val score = when {
                platform == 3 && lang == 0x409 -> 4
                platform == 3 -> 2
                platform == 0 -> 2
                platform == 1 && lang == 0 -> 3
                else -> 1
            }
            val cs = if (platform == 1) mac else Charsets.UTF_16BE
            val text = String(t, so, len, cs).trim()
            if (text.isEmpty()) continue
            if ((best[id]?.first ?: 0) < score) best[id] = score to text
        }
        return best.mapValues { it.value.second }
    }

    private fun readAt(raf: RandomAccessFile, pos: Long, len: Int): ByteArray? {
        if (pos < 0 || len < 0 || len > MAX_TABLE || pos + len > raf.length()) return null
        val b = ByteArray(len)
        raf.seek(pos)
        raf.readFully(b)
        return b
    }

    private fun u16(b: ByteArray, o: Int): Int = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)
    private fun u32(b: ByteArray, o: Int): Long =
        (u16(b, o).toLong() shl 16) or u16(b, o + 2).toLong()
}

/** Cached metadata lookups so big font lists scan once. */
object FontCatalog {
    private val cache = ConcurrentHashMap<String, Triple<Long, Long, List<FontFace>>>()

    fun faces(file: File): List<FontFace> {
        val key = file.absolutePath
        val lm = file.lastModified()
        val len = file.length()
        cache[key]?.let { if (it.first == lm && it.second == len) return it.third }
        val r = FontParser.parse(file)
        cache[key] = Triple(lm, len, r)
        return r
    }

    fun scan(files: List<File>): List<FontFace> = files.flatMap { faces(it) }

    fun face(ref: String): FontFace? {
        val (path, idx) = FontStore.splitRef(ref)
        return faces(File(path)).firstOrNull { it.ttcIndex == idx }
    }
}
