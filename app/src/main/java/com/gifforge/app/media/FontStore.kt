package com.gifforge.app.media

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/** Fonts imported from the device are copied into app storage so they persist. */
object FontStore {
    private val allowed = setOf("ttf", "otf", "ttc")
    private val cache = HashMap<String, Typeface>()

    private fun dir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.extension.lowercase() in allowed }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun import(ctx: Context, uri: Uri): File? {
        return try {
            var name = "font_${System.currentTimeMillis()}.ttf"
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { name = it }
            }
            if (name.substringAfterLast('.', "").lowercase() !in allowed) return null
            val dest = File(dir(ctx), name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            ctx.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { i.copyTo(it) } }
                ?: return null
            try {
                Typeface.createFromFile(dest)
            } catch (e: Exception) {
                dest.delete(); return null
            }
            cache.remove(dest.absolutePath)
            dest
        } catch (e: Exception) {
            null
        }
    }

    fun typeface(path: String?, bold: Boolean): Typeface {
        if (path == null) return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return cache.getOrPut(path) {
            try { Typeface.createFromFile(path) } catch (e: Exception) { Typeface.DEFAULT }
        }
    }
}
