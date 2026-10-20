package com.mediaforge.app.media

import android.content.Context
import com.mediaforge.app.subs.AssFormat
import com.mediaforge.app.subs.SubFile
import java.io.File

/** A18.f: crash recovery. The open script is written (as ASS, which keeps everything) to app storage a moment after each change. */
object Autosave {
    private fun f(ctx: Context, name: String) =
        File(File(ctx.filesDir, "autosave").apply { mkdirs() }, name.hashCode().toUInt().toString(16) + ".ass")

    fun write(ctx: Context, name: String, file: SubFile) { runCatching { f(ctx, name).writeText(AssFormat.write(file), Charsets.UTF_8) } }

    // ---------------------------------------------------------------- AEGX10 version history
    private fun hdir(ctx: Context, name: String) = File(File(ctx.filesDir, "history"), name.hashCode().toUInt().toString(16)).apply { mkdirs() }

    /** Keeps a dated copy (at most [keep]; a new one only when the text changed and 2 minutes passed since the last). */
    fun snapshot(ctx: Context, name: String, file: SubFile, keep: Int = 12) { runCatching {
        val d = hdir(ctx, name)
        val txt = AssFormat.write(file)
        val last = d.listFiles()?.maxByOrNull { it.name }
        if (last != null && (System.currentTimeMillis() - last.lastModified() < 120_000 || last.readText() == txt)) return
        File(d, System.currentTimeMillis().toString() + ".ass").writeText(txt, Charsets.UTF_8)
        d.listFiles()?.sortedBy { it.name }?.dropLast(keep)?.forEach { it.delete() }
    } }

    class Version(val timeMs: Long, val lines: Int, val file: File)

    fun versions(ctx: Context, name: String): List<Version> = runCatching {
        hdir(ctx, name).listFiles()?.sortedByDescending { it.name }?.map { f ->
            Version(f.name.removeSuffix(".ass").toLongOrNull() ?: f.lastModified(), f.readLines().count { it.startsWith("Dialogue:") || it.startsWith("Comment:") }, f)
        }
    }.getOrNull().orEmpty()

    fun load(v: Version): SubFile? = runCatching { AssFormat.parse(v.file.readText(Charsets.UTF_8)) }.getOrNull()
    fun clearAll(ctx: Context) { runCatching { File(ctx.filesDir, "autosave").deleteRecursively() } }
    fun clear(ctx: Context, name: String) { runCatching { f(ctx, name).delete() } }

    /** The recovered script when one exists and differs from [opened]. */
    fun recover(ctx: Context, name: String, opened: SubFile): SubFile? = runCatching {
        val x = f(ctx, name)
        if (!x.exists()) return null
        val txt = x.readText(Charsets.UTF_8)
        if (txt == AssFormat.write(opened)) null else AssFormat.parse(txt)
    }.getOrNull()
}
