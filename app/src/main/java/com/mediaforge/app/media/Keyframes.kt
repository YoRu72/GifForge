package com.mediaforge.app.media

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * Video keyframes (sync samples) in ms, for the Timing Post-Processor's scene snapping (Aegisub reads them through
 * ffms2; here MediaExtractor jumps from sync sample to sync sample, so nothing is decoded and a film takes seconds).
 */
object Keyframes {
    private val cache = ConcurrentHashMap<String, List<Long>>()

    /** Sorted keyframe times; empty when the video cannot be read. Call off the main thread. */
    fun read(ctx: Context, uri: Uri): List<Long> = cache.getOrPut(uri.toString()) {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return@getOrPut emptyList()
            ex.selectTrack(track)
            val out = ArrayList<Long>()
            var pos = 0L
            var guard = 0
            while (guard++ < 200_000) {
                ex.seekTo(pos, MediaExtractor.SEEK_TO_NEXT_SYNC)
                val t = ex.sampleTime
                if (t < 0 || (out.isNotEmpty() && t / 1000 <= out.last())) break
                out += t / 1000
                pos = t + 1
            }
            out
        } catch (e: Exception) {
            emptyList()
        } finally {
            ex.release()
        }
    }
}
