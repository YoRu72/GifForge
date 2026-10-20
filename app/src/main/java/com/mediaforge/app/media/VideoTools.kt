package com.mediaforge.app.media

import android.content.Context
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/** FEAT1: join clips into one MP4, extract the audio track. Hardware H.264, no FFmpeg. */
object VideoTools {
    private fun even(v: Int) = (v / 2 * 2).coerceAtLeast(2)

    /**
     * Joins [clips] in order. The output takes the first clip's size (others are letterboxed into it), 30 fps,
     * quality 10..100. Audio of every clip is copied as is (AAC in each, the muxer writes one track: works when the
     * clips share the audio format, which phone recordings do; otherwise the joined file is silent).
     */
    suspend fun join(context: Context, clips: List<Uri>, out: File, quality: Int, onProgress: (Float) -> Unit): Result<VideoEncoder.Out> = withContext(Dispatchers.Default) {
        require(clips.size >= 2)
        var codec: MediaCodec? = null
        var surface: android.view.Surface? = null
        var muxer: MediaMuxer? = null
        try {
            val infos = clips.map { u ->
                MediaMetadataRetriever().let { r ->
                    r.setDataSource(context, u)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    r.release()
                    Triple(if (rot == 90 || rot == 270) h else w, if (rot == 90 || rot == 270) w else h, d)
                }
            }
            val w = even(minOf(infos[0].first, 1920)); val h = even(infos[0].second * w / infos[0].first.coerceAtLeast(1))
            val fps = 30
            val totalMs = infos.sumOf { it.third }.coerceAtLeast(1L)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                val px = w.toDouble() * h / (1920.0 * 1080.0)
                setInteger(MediaFormat.KEY_BIT_RATE, (px * (1_500_000 + quality.coerceIn(10, 100) * 125_000)).toInt().coerceIn(300_000, 40_000_000))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            }
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply { configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
            surface = codec.createInputSurface(); codec.start()
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // audio: the first clip's audio format defines the track; samples of every clip are appended with shifted times
            val firstAudio = MediaExtractor().also { it.setDataSource(context, clips[0], null) }
            val aIn = (0 until firstAudio.trackCount).firstOrNull { firstAudio.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: -1
            val audioFmt = if (aIn >= 0) firstAudio.getTrackFormat(aIn) else null
            firstAudio.release()
            var aOut = -1
            var vTrack = -1
            var started = false
            val info = MediaCodec.BufferInfo()
            fun drain(end: Boolean) {
                val c = codec!!
                while (true) {
                    val idx = c.dequeueOutputBuffer(info, if (end) 20_000 else 0)
                    when {
                        idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!end) return
                        idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            vTrack = muxer!!.addTrack(c.outputFormat)
                            if (audioFmt != null) aOut = runCatching { muxer!!.addTrack(audioFmt) }.getOrDefault(-1)
                            muxer!!.start(); started = true
                        }
                        idx >= 0 -> {
                            val buf = c.getOutputBuffer(idx)!!
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0 && started) { buf.position(info.offset); buf.limit(info.offset + info.size); muxer!!.writeSampleData(vTrack, buf, info) }
                            c.releaseOutputBuffer(idx, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                        }
                    }
                }
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var doneMs = 0L
            var frameIndex = 0L
            for ((ci, u) in clips.withIndex()) {
                val r = MediaMetadataRetriever(); r.setDataSource(context, u)
                val d = infos[ci].third
                val n = (d / 1000.0 * fps).toInt().coerceAtLeast(1)
                for (i in 0 until n) {
                    ensureActive()
                    val t = (i * 1000.0 / fps).toLong()
                    val bmp = r.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                    val canvas = if (android.os.Build.VERSION.SDK_INT >= 23) surface!!.lockHardwareCanvas() else surface!!.lockCanvas(null)
                    try {
                        canvas.drawColor(android.graphics.Color.BLACK)
                        // letterbox into the output
                        val k = minOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                        val dw = (bmp.width * k).toInt(); val dh = (bmp.height * k).toInt()
                        canvas.drawBitmap(bmp, null, Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
                    } finally { surface.unlockCanvasAndPost(canvas) }
                    bmp.recycle()
                    frameIndex++
                    drain(false)
                    onProgress(((doneMs + t).toFloat() / totalMs * 0.9f).coerceIn(0f, 0.9f))
                }
                r.release()
                doneMs += d
            }
            codec.signalEndOfInputStream(); drain(true)
            check(started) { "The encoder produced no frames" }
            var withAudio = false
            if (aOut >= 0) {
                var offsetUs = 0L
                val buf = ByteBuffer.allocate(1 shl 20)
                val ai = MediaCodec.BufferInfo()
                for ((ci, u) in clips.withIndex()) {
                    val ex = MediaExtractor(); ex.setDataSource(context, u, null)
                    val tr = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                    if (tr != null && sameAudio(ex.getTrackFormat(tr), audioFmt!!)) {
                        ex.selectTrack(tr)
                        while (true) {
                            val sz = ex.readSampleData(buf, 0); if (sz < 0) break
                            ai.offset = 0; ai.size = sz; ai.presentationTimeUs = ex.sampleTime + offsetUs
                            ai.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                            runCatching { muxer!!.writeSampleData(aOut, buf, ai); withAudio = true }
                            ex.advance()
                        }
                    }
                    ex.release()
                    offsetUs += infos[ci].third * 1000
                }
            }
            muxer.stop()
            onProgress(1f)
            Result.success(VideoEncoder.Out(out, w, h, withAudio))
        } catch (e: Throwable) {
            out.delete()
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        } finally {
            runCatching { codec?.stop() }; runCatching { codec?.release() }; runCatching { surface?.release() }; runCatching { muxer?.release() }
        }
    }

    private fun sameAudio(a: MediaFormat, b: MediaFormat): Boolean =
        a.getString(MediaFormat.KEY_MIME) == b.getString(MediaFormat.KEY_MIME) &&
            a.getInteger(MediaFormat.KEY_SAMPLE_RATE) == b.getInteger(MediaFormat.KEY_SAMPLE_RATE) &&
            a.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == b.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

    /** Copies the audio track into an .m4a (AAC) without re-encoding; null when the video has no AAC audio. */
    suspend fun extractAudio(context: Context, uri: Uri, out: File): File? = withContext(Dispatchers.IO) {
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            ex.setDataSource(context, uri, null)
            val tr = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/mp4a") == true } ?: return@withContext null
            val fmt = ex.getTrackFormat(tr)
            ex.selectTrack(tr)
            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val t = muxer.addTrack(fmt); muxer.start()
            val buf = ByteBuffer.allocate(1 shl 20); val info = MediaCodec.BufferInfo()
            while (true) {
                val n = ex.readSampleData(buf, 0); if (n < 0) break
                info.offset = 0; info.size = n; info.presentationTimeUs = ex.sampleTime
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(t, buf, info); ex.advance()
            }
            muxer.stop()
            out
        } catch (e: Exception) { out.delete(); null } finally { runCatching { muxer?.release() }; ex.release() }
    }
}
