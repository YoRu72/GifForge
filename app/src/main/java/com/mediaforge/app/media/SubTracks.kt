package com.mediaforge.app.media

import android.content.Context
import android.media.MediaExtractor
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One subtitle stream inside a video container (soft subs). [index] is the FFmpeg stream index. */
data class SubTrack(val index: Int, val codec: String, val lang: String, val title: String, val textBased: Boolean)

/**
 * Soft-sub detection and extraction. FFprobe/FFmpeg are reached by reflection (same as VideoExporter), so the
 * app builds without the engine; then MediaExtractor is the fallback (lists tracks, cannot extract).
 */
object SubTracks {
    private const val PROBE = "com.arthenica.ffmpegkit.FFprobeKit"
    private const val KIT = "com.arthenica.ffmpegkit.FFmpegKit"
    private const val CFG = "com.arthenica.ffmpegkit.FFmpegKitConfig"
    private val textCodecs = setOf("ass", "ssa", "subrip", "srt", "mov_text", "webvtt", "text", "ttml", "subviewer", "microdvd")

    fun canExtract(): Boolean = try { Class.forName(KIT); true } catch (_: Throwable) { false }

    /** Path FFmpeg can read: the SAF protocol string when available, else a cached copy. */
    private fun input(ctx: Context, uri: Uri): String? = try {
        Class.forName(CFG).getMethod("getSafParameterForRead", Context::class.java, Uri::class.java).invoke(null, ctx, uri) as? String
    } catch (_: Throwable) { copyUriToCache(ctx, uri, "tracks/src_video")?.absolutePath }

    suspend fun list(ctx: Context, uri: Uri): List<SubTrack> = withContext(Dispatchers.IO) {
        viaProbe(ctx, uri) ?: viaExtractor(ctx, uri)
    }

    private fun viaProbe(ctx: Context, uri: Uri): List<SubTrack>? { return try {
        val path = input(ctx, uri) ?: return null
        val sess = Class.forName(PROBE).getMethod("getMediaInformation", String::class.java).invoke(null, path)!!
        val info = sess.javaClass.getMethod("getMediaInformation").invoke(sess) ?: return null
        val streams = info.javaClass.getMethod("getStreams").invoke(info) as List<*>
        streams.mapNotNull { s ->
            s ?: return@mapNotNull null
            fun str(m: String) = s.javaClass.getMethod(m).invoke(s) as? String
            if (str("getType") != "subtitle") return@mapNotNull null
            val idx = (s.javaClass.getMethod("getIndex").invoke(s) as Number).toInt()
            val codec = str("getCodec").orEmpty()
            val tags = s.javaClass.getMethod("getTags").invoke(s) // org.json.JSONObject or null
            fun tag(k: String) = tags?.javaClass?.getMethod("optString", String::class.java)?.invoke(tags, k) as? String ?: ""
            SubTrack(idx, codec, tag("language"), tag("title"), codec in textCodecs)
        }
    } catch (_: Throwable) { null } }

    private fun viaExtractor(ctx: Context, uri: Uri): List<SubTrack> = try {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(ctx, uri, null)
            (0 until ex.trackCount).mapNotNull { i ->
                val f = ex.getTrackFormat(i)
                val mime = f.getString("mime").orEmpty()
                if (!(mime.startsWith("text/") || mime.startsWith("application/x-subrip") || mime.contains("ssa") || mime.contains("vobsub") || mime.contains("pgs"))) return@mapNotNull null
                val lang = if (f.containsKey("language")) f.getString("language").orEmpty() else ""
                SubTrack(i, mime.substringAfter('/'), lang, "", false)
            }
        } finally { ex.release() }
    } catch (_: Throwable) { emptyList() }

    /** Extracts one text track to an .ass file in the cache (styles kept when the source was ASS). Null on failure. */
    suspend fun extract(ctx: Context, uri: Uri, track: SubTrack): File? = withContext(Dispatchers.IO) {
        if (!canExtract() || !track.textBased) return@withContext null
        try {
            val path = input(ctx, uri) ?: return@withContext null
            val out = File(ctx.cacheDir, "tracks").apply { mkdirs() }.let { File(it, "track_${track.index}${if (track.lang.isNotBlank()) "_" + track.lang else ""}.ass") }
            out.delete()
            val args = arrayOf("-y", "-i", path, "-map", "0:${track.index}", "-c:s", "ass", out.absolutePath)
            val sess = Class.forName(KIT).getMethod("executeWithArguments", Array<String>::class.java).invoke(null, args)!!
            val rc = sess.javaClass.getMethod("getReturnCode").invoke(sess)
            val ok = Class.forName("com.arthenica.ffmpegkit.ReturnCode").getMethod("isSuccess", Class.forName("com.arthenica.ffmpegkit.ReturnCode")).invoke(null, rc) as Boolean
            if (ok && out.length() > 0) out else null
        } catch (_: Throwable) { null }
    }
}
