package com.mediaforge.app.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * FEAT1: the editor's pipeline (trim, crop, text, shapes, pictures, bars, timing, speed, reverse / ping-pong)
 * written as an MP4 instead of a GIF. Frames go through the same [FrameComposer] as the GIF, so what the preview
 * shows is what the video gets. Encoding is hardware H.264 through MediaCodec with an input Surface (no FFmpeg
 * needed); the source audio is copied into the file when the clip plays forward at normal speed, otherwise the
 * video is silent (said in the result dialog).
 */
object VideoEncoder {
    class Out(val file: File, val width: Int, val height: Int, val withAudio: Boolean)

    private fun even(v: Int) = (v / 2 * 2).coerceAtLeast(2)

    suspend fun encode(context: Context, uri: Uri, out: File, opts: GifOptions, onProgress: (Float) -> Unit): Result<Out> = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        val composer = FrameComposer(opts)
        var codec: MediaCodec? = null
        var surface: Surface? = null
        var muxer: MediaMuxer? = null
        try {
            retriever.setDataSource(context, uri)
            val spanMs = (opts.endMs - opts.startMs).coerceAtLeast(1)
            val fps = opts.fps.coerceIn(5, 60)
            val total = (spanMs / 1000.0 * fps).toInt().coerceAtLeast(1)
            val seq = frameOrder(total, opts.mode)

            // first frame decides the output size (shrunk to maxWidth, never upscaled, even dimensions for the encoder)
            val first = retriever.getFrameAtTime(opts.startMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: throw IllegalStateException("Could not decode the first frame")
            val probe = composer.compose(first, opts.startMs)
            var w = probe.width; var h = probe.height
            if (opts.maxWidth > 0 && w > opts.maxWidth) { h = h * opts.maxWidth / w; w = opts.maxWidth }
            w = even(w); h = even(h)
            probe.recycle()

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                // quality slider 10..100 -> ~1.5 .. 14 Mbps at 1080p, scaled by pixel count
                val px = w.toDouble() * h / (1920.0 * 1080.0)
                setInteger(MediaFormat.KEY_BIT_RATE, (px * (1_500_000 + opts.quality.coerceIn(10, 100) * 125_000)).toInt().coerceIn(300_000, 40_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply { configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
            surface = codec.createInputSurface()
            codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // audio: copied as is when the picture plays forward at normal speed (the track must be added before the muxer starts)
            val wantAudio = opts.mode == PlayMode.NORMAL && opts.speed in 0.99f..1.01f
            val audioEx = if (wantAudio) MediaExtractor().also { it.setDataSource(context, uri, null) } else null
            val audioTrackIn = audioEx?.let { ex -> (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } } ?: -1
            var audioTrackOut = -1

            val info = MediaCodec.BufferInfo()
            var track = -1
            var started = false
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val frameUs = 1_000_000.0 / fps / opts.speed.coerceIn(0.1f, 8f)

            fun drain(end: Boolean) {
                val c = codec!!
                while (true) {
                    val idx = c.dequeueOutputBuffer(info, if (end) 20_000 else 0)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer!!.addTrack(c.outputFormat)
                            if (audioEx != null && audioTrackIn >= 0) audioTrackOut = runCatching { muxer!!.addTrack(audioEx.getTrackFormat(audioTrackIn)) }.getOrDefault(-1)
                            muxer!!.start(); started = true
                        }
                        idx >= 0 -> {
                            val buf = c.getOutputBuffer(idx)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && started) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer!!.writeSampleData(track, buf, info) }
                            c.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }

            for ((k, i) in seq.withIndex()) {
                ensureActive()
                val tMs = opts.startMs + (i * 1000.0 / fps).toLong()
                val raw = retriever.getFrameAtTime(tMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: throw IllegalStateException("Could not decode frame at ${tMs}ms")
                val bmp = composer.compose(raw, tMs)
                val canvas = surface!!.lockHardwareCanvasCompat()
                try {
                    canvas.drawColor(android.graphics.Color.BLACK)
                    canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), paint)
                } finally { surface.unlockCanvasAndPost(canvas) }
                bmp.recycle()
                drain(false)
                onProgress((k + 1f) / seq.size * 0.9f)
            }
            codec.signalEndOfInputStream()
            drain(true)
            check(started) { "The encoder produced no frames" }

            var withAudio = false
            if (audioEx != null && audioTrackOut >= 0) {
                withAudio = runCatching { copyAudio(audioEx, audioTrackIn, muxer!!, audioTrackOut, opts.startMs, opts.endMs) }.getOrDefault(false)
            }
            audioEx?.release()
            muxer.stop()
            onProgress(1f)
            Result.success(Out(out, w, h, withAudio))
        } catch (e: Throwable) {
            out.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }
            runCatching { surface?.release() }
            runCatching { muxer?.release() }
            composer.release()
            retriever.release()
        }
    }

    /** Lock a software-drawable canvas on the encoder surface (hardware canvas needs API 23; software works everywhere). */
    private fun Surface.lockHardwareCanvasCompat(): android.graphics.Canvas =
        if (android.os.Build.VERSION.SDK_INT >= 23) lockHardwareCanvas() else lockCanvas(null)

    /** Writes the source's audio samples between [fromMs] and [toMs] (shifted to start at 0) into the muxer's audio track. */
    private fun copyAudio(ex: MediaExtractor, trackIn: Int, muxer: MediaMuxer, trackOut: Int, fromMs: Long, toMs: Long): Boolean {
        ex.selectTrack(trackIn)
        ex.seekTo(fromMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buf = ByteBuffer.allocate(1 shl 20)
        val info = MediaCodec.BufferInfo()
        var any = false
        while (true) {
            val n = ex.readSampleData(buf, 0)
            if (n < 0) break
            val t = ex.sampleTime
            if (t >= toMs * 1000) break
            if (t >= fromMs * 1000) {
                info.offset = 0; info.size = n; info.presentationTimeUs = t - fromMs * 1000
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(trackOut, buf, info); any = true
            }
            ex.advance()
        }
        return any
    }
}
