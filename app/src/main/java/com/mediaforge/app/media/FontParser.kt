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


    // ------------------------------------------------------------ glyph coverage (cmap)

    /** Which Unicode code points a face really has glyphs for. Pure Kotlin, bounds-checked. */
    fun coverage(file: File, ttcIndex: Int): Coverage? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val head = readAt(raf, 0, 12) ?: return null
                val off = if (String(head, 0, 4, Charsets.ISO_8859_1) == "ttcf") {
                    val n = u32(head, 8).coerceIn(0L, 64L).toInt()
                    if (ttcIndex >= n) return null
                    u32(readAt(raf, 12L + ttcIndex * 4, 4) ?: return null, 0)
                } else 0L
                val h = readAt(raf, off, 12) ?: return null
                val numTables = u16(h, 4).coerceAtMost(200)
                val dir = readAt(raf, off + 12, numTables * 16) ?: return null
                var cmapOff = -1L
                var cmapLen = 0
                for (i in 0 until numTables) {
                    if (String(dir, i * 16, 4, Charsets.ISO_8859_1) == "cmap") { cmapOff = u32(dir, i * 16 + 8); cmapLen = u32(dir, i * 16 + 12).toInt() }
                }
                if (cmapOff < 0) return null
                val t = readAt(raf, cmapOff, cmapLen) ?: return null
                parseCmap(t)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseCmap(t: ByteArray): Coverage? {
        if (t.size < 4) return null
        val n = u16(t, 2).coerceAtMost(64)
        var best = -1
        var bestScore = -1
        for (i in 0 until n) {
            val r = 4 + i * 8
            if (r + 8 > t.size) break
            val plat = u16(t, r)
            val enc = u16(t, r + 2)
            val o = u32(t, r + 4).toInt()
            if (o < 0 || o + 4 > t.size) continue
            val fmt = u16(t, o)
            val score = when {
                (plat == 3 && enc == 10) || (plat == 0 && enc == 4) || (plat == 0 && enc == 6) -> if (fmt == 12) 4 else -1
                (plat == 3 && enc == 1) || (plat == 0 && enc in 0..3) -> if (fmt == 4) 3 else -1
                else -> -1
            }
            if (score > bestScore) { bestScore = score; best = o }
        }
        if (best < 0) return null
        val out = ArrayList<Int>()
        when (u16(t, best)) {
            12 -> {
                val groups = u32(t, best + 12).coerceAtMost(200000L).toInt()
                for (g in 0 until groups) {
                    val p = best + 16 + g * 12
                    if (p + 12 > t.size) break
                    val a = u32(t, p).toInt()
                    val b = u32(t, p + 4).toInt()
                    if (a in 0..0x10FFFF && b >= a) { out += a; out += minOf(b, 0x10FFFF) }
                }
            }
            4 -> {
                val segX2 = u16(t, best + 6)
                val seg = segX2 / 2
                val endP = best + 14
                val startP = endP + segX2 + 2
                val deltaP = startP + segX2
                val rangeP = deltaP + segX2
                for (i in 0 until seg) {
                    if (rangeP + i * 2 + 2 > t.size) break
                    val e = u16(t, endP + i * 2)
                    val a = u16(t, startP + i * 2)
                    val ro = u16(t, rangeP + i * 2)
                    if (a > e || a == 0xFFFF) continue
                    if (ro == 0) { out += a; out += minOf(e, 0xFFFE) }
                    else {
                        var runStart = -1
                        for (c in a..minOf(e, 0xFFFE)) {
                            val gp = rangeP + i * 2 + ro + (c - a) * 2
                            val ok = gp + 2 <= t.size && u16(t, gp) != 0
                            if (ok && runStart < 0) runStart = c
                            if (!ok && runStart >= 0) { out += runStart; out += c - 1; runStart = -1 }
                        }
                        if (runStart >= 0) { out += runStart; out += minOf(e, 0xFFFE) }
                    }
                }
            }
            else -> return null
        }
        if (out.isEmpty()) return null
        val pairs = (0 until out.size / 2).map { out[it * 2] to out[it * 2 + 1] }.sortedBy { it.first }
        val flat = IntArray(pairs.size * 2)
        pairs.forEachIndexed { i, pr -> flat[i * 2] = pr.first; flat[i * 2 + 1] = pr.second }
        return Coverage(flat)
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


/** Sorted code point ranges (start, end pairs) a font covers. */
class Coverage(private val r: IntArray) {
    fun has(cp: Int): Boolean {
        var lo = 0
        var hi = r.size / 2 - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            when {
                cp < r[mid * 2] -> hi = mid - 1
                cp > r[mid * 2 + 1] -> lo = mid + 1
                else -> return true
            }
        }
        return false
    }

    fun hasAll(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cp != 32 && !has(cp)) return false
            i += Character.charCount(cp)
        }
        return true
    }

    /** Up to [n] printable code points, preferring private-use symbols, else spread over the whole font. */
    fun pick(n: Int): String {
        val all = ArrayList<Int>()
        var i = 0
        while (i < r.size && all.size < 6000) {
            for (cp in r[i]..minOf(r[i + 1], r[i] + 3000)) {
                val ty = Character.getType(cp)
                if (cp > 32 && ty != Character.CONTROL.toInt() && ty != Character.FORMAT.toInt() &&
                    ty != Character.NON_SPACING_MARK.toInt() && ty != Character.SPACE_SEPARATOR.toInt() &&
                    ty != Character.UNASSIGNED.toInt() && ty != Character.SURROGATE.toInt()) all += cp
                if (all.size >= 6000) break
            }
            i += 2
        }
        if (all.isEmpty()) return ""
        val pua = all.filter { it in 0xE000..0xF8FF }
        val src = if (pua.size >= 3) pua else all
        val step = maxOf(1, src.size / n)
        val sb = StringBuilder()
        var k = 0
        while (k < src.size && sb.codePointCount(0, sb.length) < n) { sb.appendCodePoint(src[k]); k += step }
        return sb.toString()
    }
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

    private val covCache = ConcurrentHashMap<String, Coverage?>()

    fun coverage(face: FontFace): Coverage? {
        val k = face.ref + "|" + face.file.lastModified()
        if (covCache.containsKey(k)) return covCache[k]
        val c = FontParser.coverage(face.file, face.ttcIndex)
        covCache[k] = c
        return c
    }

    /**
     * Text that this font can really draw. A font without Latin letters (Arabic, symbol and icon fonts)
     * used to show the sample in the system font, so it looked like plain Arial. Use the font's own glyphs instead.
     */
    fun previewText(face: FontFace, wanted: String): String {
        val cov = coverage(face) ?: return wanted
        if (cov.hasAll(wanted)) return wanted
        val arabic = listOf("نص تجريبي 12345", "نص تجريبي", "بسم الله", "ابتث")
        arabic.firstOrNull { cov.hasAll(it) }?.let { return it }
        if (cov.hasAll("12345") && !cov.hasAll("abcd")) return "12345"
        val own = cov.pick(9)
        return if (own.isNotEmpty()) own else wanted
    }
}
