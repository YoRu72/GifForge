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
    val alternatives: List<String> = emptyList(), // A25: other plausible charsets when the encoding was guessed
    val uri: android.net.Uri? = null,
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
    // formats without styles (SRT, VTT, SBV, LRC, MicroDVD, TTML, TXT) open in the translator's base style
    val file = if (p.format.id == "ass") p.file else com.mediaforge.app.subs.StyleOps.withBase(p.file, baseStyle())
    return SubDoc(name, p.format, file, p.charset, p.bom, p.alternatives, uri)
}

fun writeSubDoc(ctx: Context, uri: Uri, doc: SubDoc, file: SubFile): Boolean = try {
    val bytes = Encodings.encode(doc.format.write(file), doc.charset, doc.bom)
    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } != null
} catch (e: Exception) {
    false
}

fun newSubDoc(): SubDoc {
    val f = com.mediaforge.app.subs.SubFormats.byId("ass") ?: com.mediaforge.app.subs.AssFormat
    return SubDoc("new.ass", f, com.mediaforge.app.subs.StyleOps.withBase(SubFile.blank(), baseStyle()), "UTF-8", false)
}

/** The base style chosen in Settings (font, size, outline, shadow... for every new or imported script). */
fun baseStyle(): com.mediaforge.app.subs.AssStyle = com.mediaforge.app.subs.StyleOps.base(com.mediaforge.app.Prefs.subBaseStyle.value)

/** Recent subtitle/video files, newest first, kept in preferences (uri per line). */
object Recents {
    private fun list(raw: String) = raw.split('\n').filter { it.isNotBlank() }
    fun get(raw: String): List<Uri> = list(raw).map { Uri.parse(it) }
    fun push(raw: String, uri: Uri, max: Int = 8): String =
        (listOf(uri.toString()) + list(raw).filter { it != uri.toString() }).take(max).joinToString("\n")
}
