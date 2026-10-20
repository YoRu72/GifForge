package com.mediaforge.app.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * AEG-B / 20.d: the audio track as peaks for the timing display. The track is decoded once with MediaCodec to PCM,
 * downmixed, and reduced to one peak (max |sample|, 0..255) per [BLOCK_MS]; the result is cached on disk per video,
 * so the second open is instant. Decoding is progressive: [progress] is called with the peaks so far, and the
 * display can show the part that is ready (Aegisub 3.4 does the same).
 */
object Waveform {
    const val BLOCK_MS = 10

    class Peaks(val blockMs: Int, val data: ByteArray) {
        val durationMs: Long get() = data.size.toLong() * blockMs
        fun at(ms: Long): Int { val i = (ms / blockMs).toInt(); return if (i in data.indices) data[i].toInt() and 0xFF else 0 }
        /** Max peak in [fromMs, toMs). */
        fun max(fromMs: Long, toMs: Long): Int {
            var a = (fromMs / blockMs).toInt().coerceAtLeast(0)
            val b = ((toMs + blockMs - 1) / blockMs).toInt().coerceAtMost(data.size)
            var m = 0
            while (a < b) { val v = data[a].toInt() and 0xFF; if (v > m) m = v; a++ }
            return m
        }
    }

    private fun cacheFile(ctx: Context, uri: Uri): File =
        File(File(ctx.cacheDir, "peaks").apply { mkdirs() }, uri.toString().hashCode().toUInt().toString(16) + ".pk")

    fun cached(ctx: Context, uri: Uri): Peaks? = runCatching {
        val f = cacheFile(ctx, uri)
        if (!f.exists()) return null
        DataInputStream(f.inputStream().buffered()).use { s ->
            val bm = s.readInt(); val n = s.readInt()
            val d = ByteArray(n); s.readFully(d); Peaks(bm, d)
        }
    }.getOrNull()

    /**
     * Decodes the first audio track. [progress] gets partial peaks about every 2 s of audio; return false to stop.
     * Returns the full peaks (null = no audio track or decoder failure). Call off the main thread.
     */
    fun build(ctx: Context, uri: Uri, progress: (Peaks) -> Boolean = { true }): Peaks? {
        cached(ctx, uri)?.let { return it }
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            ex.setDataSource(ctx, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return null
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime).apply { configure(fmt, null, null, 0); start() }
            var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val out = ArrayList<Byte>(1 shl 16)
            var blockMax = 0
            var inBlock = 0
            var perBlock = rate * BLOCK_MS / 1000
            var sawEos = false
            var lastReport = 0
            val info = MediaCodec.BufferInfo()
            while (!sawEos) {
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    else { codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0); ex.advance() }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        rate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        perBlock = rate * BLOCK_MS / 1000
                    }
                    outIdx >= 0 -> {
                        val b = codec.getOutputBuffer(outIdx)!!
                        val sb = b.order(ByteOrder.nativeOrder()).asShortBuffer()
                        val frames = sb.remaining() / ch.coerceAtLeast(1)
                        for (i in 0 until frames) {
                            var m = 0
                            for (c in 0 until ch) { val v = abs(sb.get(i * ch + c).toInt()); if (v > m) m = v }
                            if (m > blockMax) blockMax = m
                            if (++inBlock >= perBlock) { out += (blockMax shr 7).coerceIn(0, 255).toByte(); blockMax = 0; inBlock = 0 }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawEos = true
                        if (out.size - lastReport >= 2000 / BLOCK_MS) {
                            lastReport = out.size
                            if (!progress(Peaks(BLOCK_MS, out.toByteArray()))) return null
                        }
                    }
                }
            }
            if (inBlock > 0) out += (blockMax shr 7).coerceIn(0, 255).toByte()
            val p = Peaks(BLOCK_MS, out.toByteArray())
            runCatching {
                DataOutputStream(cacheFile(ctx, uri).outputStream().buffered()).use { s -> s.writeInt(p.blockMs); s.writeInt(p.data.size); s.write(p.data) }
            }
            return p
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            ex.release()
        }
    }
}
