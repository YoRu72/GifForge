package com.mediaforge.app.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.mediaforge.app.subs.AssFormat
import com.mediaforge.app.subs.SrtFormat
import com.mediaforge.app.subs.SubFile
import com.mediaforge.app.ui.ExportMode
import com.mediaforge.app.ui.VideoExportSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A17.a-A17.d: video export through FFmpeg. The engine is reached by reflection on the FFmpegKit API
 * (package com.arthenica.ffmpegkit - the maintained forks keep it), so the app still builds and runs when the
 * dependency is absent: [available] is then false and the chooser says so. Gradle property `ffmpegKit` in
 * gradle.properties names the artifact; blank = no engine.
 */
object VideoExporter {
    private const val KIT = "com.arthenica.ffmpegkit.FFmpegKit"
    private const val RC = "com.arthenica.ffmpegkit.ReturnCode"

    fun available(): Boolean = try { Class.forName(KIT); true } catch (e: Throwable) { false }

    fun cancel() { try { Class.forName(KIT).getMethod("cancel").invoke(null) } catch (_: Throwable) {} }

    /** `ass` filter argument: paths need `\`, `:` and `'` escaped. */
    private fun esc(s: String) = s.replace("\\", "\\\\").replace(":", "\\:").replace("'", "\\'")

    /** Runs FFmpeg with an argument list and returns (success, full log). Reflection keeps the build engine-optional. */
    private fun run(args: List<String>): Pair<Boolean, String> {
        val kit = Class.forName(KIT)
        val session = kit.getMethod("executeWithArguments", Array<String>::class.java).invoke(null, args.toTypedArray())!!
        val rc = session.javaClass.getMethod("getReturnCode").invoke(session)
        val ok = Class.forName(RC).getMethod("isSuccess", Class.forName(RC)).invoke(null, rc) as Boolean
        val log = (session.javaClass.getMethod("getAllLogsAsString").invoke(session) as? String).orEmpty()
        return ok to log
    }

    /** Names of the encoders/filters this FFmpeg build really contains (probed once). */
    private val encoders: Set<String> by lazy { probe("-encoders") }
    private val filters: Set<String> by lazy { probe("-filters") }
    private fun probe(flag: String): Set<String> = try {
        run(listOf("-hide_banner", flag)).second.lineSequence()
            .mapNotNull { l -> l.trim().split(Regex("\\s+")).getOrNull(1) }.toSet()
    } catch (_: Throwable) { emptySet() }

    /** One usable encoder: [crf] = quality-based (x264/x265/vpx/svt), otherwise bitrate-based (openh264/mediacodec). */
    class Enc(val name: String, val crfBased: Boolean)

    /** Best available encoder for [codec]; null when the build has none. Order = quality first, then what Android builds usually ship. */
    private fun pick(codec: String): Enc? {
        val order = when (codec) {
            "hevc" -> listOf("libx265" to true, "hevc_mediacodec" to false)
            "vp9" -> listOf("libvpx-vp9" to true)
            "av1" -> listOf("libsvtav1" to true, "libaom-av1" to true)
            else -> listOf("libx264" to true, "libopenh264" to false, "h264_mediacodec" to false)
        }
        return order.firstOrNull { it.first in encoders }?.let { Enc(it.first, it.second) }
    }

    /** CRF -> bitrate (bits/s) for the bitrate-only encoders: 8 Mbps at 1080p/CRF 23, x2 per 6 CRF steps, scaled by pixel count. */
    private fun bitrateFor(crf: Int, w: Int, h: Int): Int {
        val px = (w.coerceAtLeast(2).toDouble() * h.coerceAtLeast(2)) / (1920.0 * 1080.0)
        val b = 8_000_000.0 * px * Math.pow(2.0, (23 - crf) / 6.0)
        return b.coerceIn(400_000.0, 40_000_000.0).toInt()
    }

    private fun videoSize(f: File): Triple<Int, Int, Int> = try {
        val r = MediaMetadataRetriever(); r.setDataSource(f.absolutePath)
        val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
        val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        r.release()
        if (rot == 90 || rot == 270) Triple(h, w, rot) else Triple(w, h, rot)
    } catch (_: Throwable) { Triple(1920, 1080, 0) }

    class Plan(val args: List<String>, val note: String)

    fun buildArgs(mode: ExportMode, st: VideoExportSettings, video: File, subs: File, fontsDir: File?, out: File): Plan {
        val a = mutableListOf("-y", "-hide_banner", "-i", video.absolutePath)
        if (mode == ExportMode.SOFT) { a += listOf("-i", subs.absolutePath); a += listOf("-map", "0", "-map", "1") }
        var note = ""
        val filterList = mutableListOf<String>()
        if (st.height > 0 && st.codec != "copy") filterList += "scale=-2:${st.height}"
        if (mode == ExportMode.HARD) {
            filterList += "ass=filename='${esc(subs.absolutePath)}'" + (fontsDir?.let { ":fontsdir='${esc(it.absolutePath)}'" } ?: "")
        }
        // Burning needs a re-encode, so "copy" is treated as H.264 there.
        var codec = if (mode == ExportMode.HARD && st.codec == "copy") "h264" else st.codec
        if (filterList.isNotEmpty()) a += listOf("-vf", filterList.joinToString(","))
        if (codec == "copy") {
            a += listOf("-c:v", "copy")
        } else {
            var enc = pick(codec)
            if (enc == null && codec != "h264") { // e.g. no VP9/AV1 in this build: fall back to H.264 and say so
                note = codec.uppercase() + " -> H.264 (not in this FFmpeg build)"
                codec = "h264"; enc = pick("h264")
            }
            if (enc == null) throw IllegalStateException("NO_ENCODER")
            val (w0, h0) = videoSize(video).let { it.first to it.second }
            val h = if (st.height > 0) st.height else h0
            val w = if (st.height > 0) (w0.toDouble() * st.height / h0.coerceAtLeast(1)).toInt() else w0
            a += listOf("-c:v", enc.name)
            when (enc.name) {
                "libx264" -> a += listOf("-crf", "${st.crf}", "-preset", "veryfast")
                "libx265" -> a += listOf("-crf", "${st.crf}", "-preset", "veryfast") + (if (st.container == "mp4") listOf("-tag:v", "hvc1") else emptyList())
                "libvpx-vp9" -> a += listOf("-crf", "${st.crf + 6}", "-b:v", "0", "-row-mt", "1", "-cpu-used", "4")
                "libsvtav1" -> a += listOf("-crf", "${st.crf + 8}", "-preset", "8")
                "libaom-av1" -> a += listOf("-crf", "${st.crf + 8}", "-b:v", "0", "-cpu-used", "6")
                "hevc_mediacodec" -> a += listOf("-b:v", "${bitrateFor(st.crf + 4, w, h)}") + (if (st.container == "mp4") listOf("-tag:v", "hvc1") else emptyList())
                else -> a += listOf("-b:v", "${bitrateFor(st.crf, w, h)}") // libopenh264, h264_mediacodec
            }
            a += listOf("-pix_fmt", "yuv420p")
            if (!enc.crfBased && note.isEmpty()) note = "${enc.name} (fixed bitrate)"
        }
        a += if (st.audioCopy) listOf("-c:a", "copy") else listOf("-c:a", "aac", "-b:a", "160k")
        if (mode == ExportMode.SOFT) {
            a += listOf("-c:s", if (st.container == "mkv") "ass" else "mov_text", "-metadata:s:s:0", "language=${st.subLang}")
        }
        a += out.absolutePath
        return Plan(a, note)
    }

    class Outcome(val ok: Boolean, val file: File?, val log: String, val note: String = "")

    /** Keeps only the lines that explain a failure; the FFmpeg banner and stream dump bury them otherwise. */
    private fun cleanLog(log: String): String {
        val key = Regex("(?i)error|unknown|invalid|no such|failed|not found|unable|unsupported|cannot|could not|permission|no space|incorrect")
        val hits = log.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && key.containsMatchIn(it) && !it.startsWith("--") && !it.contains("configuration:") }.toList()
        return (if (hits.isNotEmpty()) hits.takeLast(8).joinToString("\n") else log.takeLast(600)).take(900)
    }

    /** Runs the whole export in the app cache; the caller copies [Outcome.file] to the chosen destination. */
    suspend fun export(
        ctx: Context, videoUri: Uri, file: SubFile, mode: ExportMode, st: VideoExportSettings,
    ): Outcome = withContext(Dispatchers.IO) {
        if (!available()) return@withContext Outcome(false, null, "no engine")
        val dir = File(ctx.cacheDir, "export").apply { deleteRecursively(); mkdirs() }
        val video = copyUriToCache(ctx, videoUri, "export/src_video") ?: return@withContext Outcome(false, null, "cannot read video")
        val softMp4 = mode == ExportMode.SOFT && st.container == "mp4"
        val subs = File(dir, if (softMp4) "subs.srt" else "subs.ass")
        subs.writeText(if (softMp4) SrtFormat.write(file) else AssFormat.write(file), Charsets.UTF_8)
        val out = File(dir, "out." + st.container)
        try {
            if (mode == ExportMode.HARD && filters.isNotEmpty() && "ass" !in filters)
                return@withContext Outcome(false, null, "This FFmpeg build has no 'ass' filter (libass), so subtitles cannot be burned in. Use soft subtitles, or a build with libass.")
            val plan = buildArgs(mode, st, video, subs, runCatching { FontStore.dir(ctx) }.getOrNull(), out)
            var (ok, log) = run(plan.args)
            var note = plan.note
            // Hardware encoders can refuse odd sizes/profiles: retry once with the software OpenH264 path before giving up.
            if (!ok && "mediacodec" in plan.args.joinToString(" ") && "libopenh264" in encoders) {
                val retry = plan.args.map { if (it.endsWith("_mediacodec")) "libopenh264" else it }
                val r = run(retry); ok = r.first; log = r.second; note = "libopenh264 (fixed bitrate)"
            }
            video.delete()
            if (ok && out.exists() && out.length() > 0) Outcome(true, out, "", note) else Outcome(false, null, cleanLog(log))
        } catch (e: IllegalStateException) {
            Outcome(false, null, if (e.message == "NO_ENCODER") "This FFmpeg build has no H.264 encoder. Choose Copy (soft subtitles) or use a build with libx264/libopenh264." else (e.message ?: "error"))
        } catch (e: Throwable) {
            Outcome(false, null, e.message ?: e.javaClass.simpleName)
        }
    }
}
