package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Turns a GIF into a hidden, all-intra H.264 video so the one video editor can edit it with every tool
 * (trim, crop, text, shapes, bars, timing, speed, reverse...). Cached in the app cache folder.
 * Transparent areas become white. Very small GIFs are enlarged to at least 128 px on the short side,
 * very large ones shrunk to 1920 px on the long side (encoders need sane sizes).
 */
object GifProxy {
    class Proxy(val file: File, val fpsHint: Int, val durationMs: Long)

    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    suspend fun build(ctx: Context, uri: Uri, onProgress: (Float) -> Unit): Result<Proxy> =
        withContext(Dispatchers.Default) {
            val src = File(ctx.cacheDir, "gifsrc_${System.currentTimeMillis()}.gif")
            try {
                val copied = withContext(Dispatchers.IO) { copyUriToCache(ctx, uri, src.name) }
                    ?: return@withContext Result.failure(Exception("Couldn't open this file"))
                val meta = readGifMeta(copied)
                    ?: return@withContext Result.failure(Exception("Couldn't read this GIF"))
                val n = meta.frames
                if (n < 1 || meta.delaysCs.size < n) return@withContext Result.failure(Exception("This GIF has no frames"))
                val totalMs = meta.timeAtMs(n)
                val fps = (n * 1000.0 / totalMs.coerceAtLeast(1)).roundToInt().coerceIn(5, 30)

                val key = uri.toString().hashCode().toUInt().toString(16)
                val out = File(ctx.cacheDir, "mfproxy_${key}_${copied.length()}.mp4")
                if (out.exists() && out.length() > 0) return@withContext Result.success(Proxy(out, fps, totalMs))

                var last: Throwable? = null
                for (name in candidateEncoders()) {
                    try {
                        encode(copied, meta, out, name, onProgress)
                        return@withContext Result.success(Proxy(out, fps, totalMs))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        last = e
                        out.delete()
                    }
                }
                Result.failure(Exception("The phone's video encoder refused this GIF" + (last?.message?.let { " ($it)" } ?: "")))
            } finally {
                src.delete()
            }
        }

    /** Default encoder first, then software encoders as a fallback. */
    private fun candidateEncoders(): List<String?> {
        val software = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { ci ->
                    ci.isEncoder && ci.supportedTypes.any { it.equals(MIME, true) } &&
                        (ci.name.startsWith("OMX.google") || ci.name.startsWith("c2.android"))
                }
                .map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
        return listOf<String?>(null) + software
    }

    private suspend fun encode(src: File, meta: GifMeta, out: File, codecName: String?, onProgress: (Float) -> Unit) {
        val n = meta.frames
        val pts = LongArray(n)
        var acc = 0L
        for (i in 0 until n) { pts[i] = acc; acc += meta.delaysCs[i] * 10_000L } // microseconds

        val encoder = if (codecName == null) MediaCodec.createEncoderByType(MIME) else MediaCodec.createByCodecName(codecName)
        val tmp = File(out.parentFile, out.name + ".tmp")
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        var reader = 0L
        try {
            val caps = encoder.codecInfo.getCapabilitiesForType(MIME).videoCapabilities
            val longSide = maxOf(meta.width, meta.height)
            val shortSide = minOf(meta.width, meta.height)
            val scale = when {
                longSide > 1920 -> 1920f / longSide
                shortSide < 128 -> 128f / shortSide
                else -> 1f
            }
            val wa = caps.widthAlignment.coerceAtLeast(2)
            val ha = caps.heightAlignment.coerceAtLeast(2)
            fun align(v: Float, a: Int) = ((v / a).roundToInt() * a).coerceAtLeast(a)
            val w = align(meta.width * scale, wa)
            val h = align(meta.height * scale, ha)
            if (!caps.isSizeSupported(w, h)) throw IllegalStateException("size ${w}x$h not supported")

            val fmt = MediaFormat.createVideoFormat(MIME, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, (w.toLong() * h * 30).coerceIn(4_000_000L, 80_000_000L).toInt())
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0) // every frame is a keyframe: exact seeking, no reordering
            }
            encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surf = encoder.createInputSurface().also { surface = it }
            encoder.start()
            val mux = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
            reader = GifskiNative.nativeGifOpen(src.absolutePath)
            check(reader != 0L) { "Couldn't read GIF frames" }

            val producerDone = AtomicBoolean(false)
            var started = false
            coroutineScope {
                // Drain encoded frames on another thread so the input surface never stalls.
                val drainer = async(Dispatchers.IO) {
                    val info = MediaCodec.BufferInfo()
                    var track = -1
                    var didStart = false
                    var idx = 0
                    var idle = 0
                    while (true) {
                        ensureActive()
                        val r = encoder.dequeueOutputBuffer(info, 10_000)
                        when {
                            r == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                                if (producerDone.get() && ++idle > 500) throw IllegalStateException("encoder timed out")
                            }
                            r == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                                track = mux.addTrack(encoder.outputFormat)
                                mux.start()
                                didStart = true
                            }
                            r >= 0 -> {
                                idle = 0
                                val buf = encoder.getOutputBuffer(r)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                                if (buf != null && info.size > 0 && didStart) {
                                    buf.position(info.offset)
                                    buf.limit(info.offset + info.size)
                                    info.presentationTimeUs = pts[idx.coerceAtMost(n - 1)] // real GIF timing
                                    mux.writeSampleData(track, buf, info)
                                    idx++
                                }
                                val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                                encoder.releaseOutputBuffer(r, false)
                                if (eos) break
                            }
                        }
                    }
                    didStart
                }

                val bmp = Bitmap.createBitmap(meta.width, meta.height, Bitmap.Config.ARGB_8888)
                val dst = Rect(0, 0, w, h)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                try {
                    for (i in 0 until n) {
                        ensureActive()
                        val bytes = GifskiNative.nativeGifNext(reader) ?: break
                        if (bytes.size != meta.width * meta.height * 4) throw IllegalStateException("bad frame size")
                        bmp.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
                        val c = surf.lockHardwareCanvas()
                        try {
                            c.drawColor(Color.WHITE) // GIF transparency becomes white
                            c.drawBitmap(bmp, null, dst, paint)
                        } finally {
                            surf.unlockCanvasAndPost(c)
                        }
                        onProgress((i + 1f) / n)
                    }
                } finally {
                    bmp.recycle()
                }
                producerDone.set(true)
                encoder.signalEndOfInputStream()
                started = drainer.await()
            }
            check(started) { "encoder produced no video" }
            mux.stop()
            if (!tmp.renameTo(out)) throw IllegalStateException("couldn't save the converted video")
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { surface?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            if (reader != 0L) GifskiNative.nativeGifClose(reader)
            tmp.delete()
        }
    }
}
