package com.mediaforge.app.subs

/** A subtitle file format. [parse]/[write] convert between text and the internal [SubFile]. */
interface SubFormat {
    val id: String
    val label: String
    val extensions: List<String>
    fun detect(text: String): Boolean
    fun parse(text: String): SubFile
    fun write(file: SubFile): String
}

/** A file opened from bytes: what was detected, and how it was decoded (so Save can keep the same encoding). */
class Parsed(
    val format: SubFormat,
    val file: SubFile,
    val charset: String,
    val bom: Boolean,
    val alternatives: List<String>,
)

object SubFormats {
    /** Order matters for content sniffing (TXT never sniffs; it is chosen by extension or by the user). */
    val all: List<SubFormat> = listOf(
        AssFormat, VttFormat, SrtFormat, SbvFormat, LrcFormat, MicroDvdFormat, TtmlFormat, TxtFormat,
    )

    fun byExtension(ext: String): SubFormat? = all.firstOrNull { f -> f.extensions.any { it.equals(ext.trimStart('.'), true) } }
    fun byId(id: String): SubFormat? = all.firstOrNull { it.id == id }

    /** Picks a format by file name first, then by sniffing the content. */
    fun detect(fileName: String?, text: String): SubFormat? {
        val ext = fileName?.substringAfterLast('.', "")
        val byExt = if (!ext.isNullOrEmpty()) byExtension(ext) else null
        return byExt ?: all.firstOrNull { it.detect(text) }
    }

    fun decode(bytes: ByteArray): String = Encodings.decode(bytes).text

    /** [charset] forces an encoding (user override); null = auto-detect. */
    fun parseBytes(bytes: ByteArray, fileName: String?, charset: String? = null): Parsed? {
        val d = if (charset == null) Encodings.decode(bytes) else Decoded(Encodings.decodeAs(bytes, charset), charset, false, emptyList())
        val fmt = detect(fileName, d.text) ?: return null
        return Parsed(fmt, fmt.parse(d.text), d.charset, d.bom, d.alternatives)
    }
}
