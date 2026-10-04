package com.mediaforge.app.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.mediaforge.app.subs.Encodings
import com.mediaforge.app.subs.SubFile
import com.mediaforge.app.subs.SubFormat
import com.mediaforge.app.subs.SubFormats

/** An open subtitle document: the model plus how it was read, so Save keeps format and encoding. */
class SubDoc(
    val name: String,
    val format: SubFormat,
    val file: SubFile,
    val charset: String,
    val bom: Boolean,
)

fun displayName(ctx: Context, uri: Uri): String = try {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: (uri.lastPathSegment ?: "subtitle")
} catch (e: Exception) {
    uri.lastPathSegment ?: "subtitle"
}

/** Reads and parses a subtitle file; null when the format is not recognised. */
fun readSubDoc(ctx: Context, uri: Uri, charset: String? = null): SubDoc? {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val name = displayName(ctx, uri)
    val p = SubFormats.parseBytes(bytes, name, charset) ?: return null
    return SubDoc(name, p.format, p.file, p.charset, p.bom)
}

fun writeSubDoc(ctx: Context, uri: Uri, doc: SubDoc, file: SubFile): Boolean = try {
    val bytes = Encodings.encode(doc.format.write(file), doc.charset, doc.bom)
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
} catch (e: Exception) {
    false
}

fun newSubDoc(): SubDoc {
    val f = com.mediaforge.app.subs.SubFormats.byId("ass") ?: com.mediaforge.app.subs.AssFormat
    return SubDoc("new.ass", f, SubFile.blank(), "UTF-8", false)
}

/** Recent subtitle/video files, newest first, kept in preferences (uri per line). */
object Recents {
    private fun list(raw: String) = raw.split('\n').filter { it.isNotBlank() }
    fun get(raw: String): List<Uri> = list(raw).map { Uri.parse(it) }
    fun push(raw: String, uri: Uri, max: Int = 8): String =
        (listOf(uri.toString()) + list(raw).filter { it != uri.toString() }).take(max).joinToString("\n")
}
