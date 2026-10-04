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
