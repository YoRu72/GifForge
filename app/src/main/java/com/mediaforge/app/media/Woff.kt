package com.mediaforge.app.media

import java.nio.ByteBuffer
import java.util.zip.Inflater

/** WOFF 1.0 -> plain sfnt (TTF/OTF). WOFF2 is handled natively (Rust). */
object Woff {
    private const val MAX_TABLE = 64 * 1024 * 1024

    fun toSfnt(src: ByteArray): ByteArray? = try { convert(src) } catch (e: Exception) { null }

    private fun convert(src: ByteArray): ByteArray? {
        if (src.size < 44) return null
        val bb = ByteBuffer.wrap(src)
        val flavor = bb.getInt(4)
        val numTables = bb.getShort(12).toInt() and 0xFFFF
        if (numTables == 0 || numTables > 200 || 44 + numTables * 20 > src.size) return null

        class E(val tag: Int, val off: Int, val comp: Int, val orig: Int, val sum: Int)
        val entries = ArrayList<E>(numTables)
        var dataSize = 0L
        for (i in 0 until numTables) {
            val o = 44 + i * 20
            val e = E(bb.getInt(o), bb.getInt(o + 4), bb.getInt(o + 8), bb.getInt(o + 12), bb.getInt(o + 16))
            if (e.off < 0 || e.comp < 0 || e.orig < 0 || e.orig > MAX_TABLE || e.off.toLong() + e.comp > src.size) return null
            entries += e
            dataSize += (e.orig + 3L) and 3L.inv()
        }
        if (dataSize > 3L * MAX_TABLE) return null

        val dirSize = 12 + 16 * numTables
        val out = ByteBuffer.allocate((dirSize + dataSize).toInt())
        var sel = 0
        while ((1 shl (sel + 1)) <= numTables) sel++
        val searchRange = 16 * (1 shl sel)
        out.putInt(flavor)
        out.putShort(numTables.toShort())
        out.putShort(searchRange.toShort())
        out.putShort(sel.toShort())
        out.putShort((numTables * 16 - searchRange).toShort())

        var pos = dirSize
        for (e in entries) { // directory (WOFF tables are already sorted by tag)
            out.putInt(e.tag); out.putInt(e.sum); out.putInt(pos); out.putInt(e.orig)
            pos += (e.orig + 3) and 3.inv()
        }
        for (e in entries) {
            val raw: ByteArray = if (e.comp < e.orig) {
                val inf = Inflater()
                try {
                    inf.setInput(src, e.off, e.comp)
                    val buf = ByteArray(e.orig)
                    var n = 0
                    while (n < e.orig && !inf.finished()) {
                        val r = inf.inflate(buf, n, e.orig - n)
                        if (r == 0 && (inf.needsInput() || inf.needsDictionary())) break
                        n += r
                    }
                    if (n != e.orig) return null
                    buf
                } finally { inf.end() }
            } else {
                if (e.comp != e.orig) return null
                src.copyOfRange(e.off, e.off + e.orig)
            }
            out.put(raw)
            repeat(((e.orig + 3) and 3.inv()) - e.orig) { out.put(0) }
        }
        return out.array()
    }
}
