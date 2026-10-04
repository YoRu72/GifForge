package com.mediaforge.app.media

import android.content.Context
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

    fun buildArgs(mode: ExportMode, st: VideoExportSettings, video: File, subs: File, fontsDir: File?, out: File): List<String> {
        val a = mutableListOf("-y", "-i", video.absolutePath)
        if (mode == ExportMode.SOFT) { a += listOf("-i", subs.absolutePath); a += listOf("-map", "0", "-map", "1") }
        val scale = if (st.height > 0 && st.codec != "copy") "scale=-2:${st.height}" else null
        val filters = mutableListOf<String>()
        if (scale != null) filters += scale
        if (mode == ExportMode.HARD) {
            filters += "ass=filename='${esc(subs.absolutePath)}'" + (fontsDir?.let { ":fontsdir='${esc(it.absolutePath)}'" } ?: "")
        }
        // Burning needs a re-encode, so "copy" is treated as H.264 there.
        val codec = if (mode == ExportMode.HARD && st.codec == "copy") "h264" else st.codec
        if (filters.isNotEmpty()) a += listOf("-vf", filters.joinToString(","))
        when (codec) {
            "copy" -> a += listOf("-c:v", "copy")
            "hevc" -> a += listOf("-c:v", "libx265", "-crf", "${st.crf}", "-preset", "veryfast", "-pix_fmt", "yuv420p") + (if (st.container == "mp4") listOf("-tag:v", "hvc1") else emptyList())
            "vp9" -> a += listOf("-c:v", "libvpx-vp9", "-crf", "${st.crf + 6}", "-b:v", "0", "-row-mt", "1", "-cpu-used", "4", "-pix_fmt", "yuv420p")
            "av1" -> a += listOf("-c:v", "libsvtav1", "-crf", "${st.crf + 8}", "-preset", "8", "-pix_fmt", "yuv420p")
            else -> a += listOf("-c:v", "libx264", "-crf", "${st.crf}", "-preset", "veryfast", "-pix_fmt", "yuv420p")
        }
        a += if (st.audioCopy) listOf("-c:a", "copy") else listOf("-c:a", "aac", "-b:a", "160k")
        if (mode == ExportMode.SOFT) {
            a += listOf("-c:s", if (st.container == "mkv") "ass" else "mov_text", "-metadata:s:s:0", "language=${st.subLang}")
        }
        a += out.absolutePath
        return a
    }

    class Outcome(val ok: Boolean, val file: File?, val log: String)

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
        val args = buildArgs(mode, st, video, subs, runCatching { FontStore.dir(ctx) }.getOrNull(), out)
        try {
            val kit = Class.forName(KIT)
            val session = kit.getMethod("executeWithArguments", Array<String>::class.java).invoke(null, args.toTypedArray())!!
            val rc = session.javaClass.getMethod("getReturnCode").invoke(session)
            val ok = Class.forName(RC).getMethod("isSuccess", Class.forName(RC)).invoke(null, rc) as Boolean
            val log = (session.javaClass.getMethod("getAllLogsAsString").invoke(session) as? String).orEmpty()
            video.delete()
            if (ok && out.exists() && out.length() > 0) Outcome(true, out, "") else Outcome(false, null, log.takeLast(1500))
        } catch (e: Throwable) {
            Outcome(false, null, e.message ?: e.javaClass.simpleName)
        }
    }
}
