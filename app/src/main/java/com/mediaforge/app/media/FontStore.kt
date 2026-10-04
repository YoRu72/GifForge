package com.mediaforge.app.media

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface FontLoad {
    data object Loading : FontLoad
    data object Failed : FontLoad
    data object Blocked : FontLoad
    class Ready(val typeface: Typeface) : FontLoad
}

data class ImportResult(val imported: List<File>, val failed: Int, val unsupported: Set<String>) {
    operator fun plus(o: ImportResult) = ImportResult(imported + o.imported, failed + o.failed, unsupported + o.unsupported)
    companion object { val EMPTY = ImportResult(emptyList(), 0, emptySet()) }
}

/**
 * Font library. Imports TTF, OTF, TTC/OTC collections, WOFF, WOFF2 and ZIP archives of fonts
 * (web fonts are converted to plain TTF/OTF). Loading is crash-guarded: one font parsed at a time,
 * marked "pending" first so a native crash blocks only that font on the next launch.
 * A font reference is a path, or path::N for the N-th face of a collection.
 */
object FontStore {
    private val diskExts = setOf("ttf", "otf", "ttc", "otc")
    private val fontExts = diskExts + setOf("woff", "woff2")
    private const val MAX_FONT = 64 * 1024 * 1024
    private const val MAX_ZIP_TOTAL = 400L * 1024 * 1024

    private val cache = LruCache<String, Typeface>(48)
    private val loadLock = Mutex()
    private val safe: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())
    private var app: Context? = null

    fun init(ctx: Context) {
        app = ctx.applicationContext
        recoverFromCrash()
    }

    private fun prefs() = app!!.getSharedPreferences("fontguard", Context.MODE_PRIVATE)
    private fun strings(key: String): Set<String> = prefs().getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun recoverFromCrash() {
        val pending = strings("pending")
        if (pending.isEmpty()) return
        prefs().edit()
            .putStringSet("blocked", strings("blocked") + pending)
            .putStringSet("pending", emptySet())
            .commit()
    }

    fun splitRef(ref: String): Pair<String, Int> {
        val i = ref.lastIndexOf("::")
        return if (i > 0) ref.substring(0, i) to (ref.substring(i + 2).toIntOrNull() ?: 0) else ref to 0
    }

    fun isBlocked(ref: String) = ref in strings("blocked")
    fun unblock(ref: String) { prefs().edit().putStringSet("blocked", strings("blocked") - ref).apply() }
    private fun unblockAll(path: String) {
        prefs().edit().putStringSet("blocked", strings("blocked").filterNot { it.startsWith(path) }.toSet()).apply()
    }

    private fun build(ref: String, vars: String?): Typeface? {
        val (path, idx) = splitRef(ref)
        return if (Build.VERSION.SDK_INT >= 26) {
            val b = Typeface.Builder(File(path))
            if (idx > 0) b.setTtcIndex(idx)
            if (!vars.isNullOrBlank()) b.setFontVariationSettings(vars)
            b.build()
        } else {
            Typeface.createFromFile(path)
        }
    }

    private fun create(ref: String, vars: String? = null): Typeface? {
        val guard = ref !in safe // a file already parsed this session needs no disk marker
        val p = prefs()
        if (guard) p.edit().putStringSet("pending", strings("pending") + ref).commit() // must survive a native crash
        return try {
            val tf = build(ref, vars) ?: if (!vars.isNullOrBlank()) build(ref, null) else null
            if (tf != null) safe.add(ref)
            tf
        } catch (e: Throwable) {
            null
        } finally {
            if (guard) p.edit().putStringSet("pending", strings("pending") - ref).apply()
        }
    }

    private fun key(ref: String, vars: String?) = if (vars.isNullOrBlank()) ref else "$ref|$vars"

    // ------------------------------------------------------------ library

    fun dir(ctx: Context) = File(ctx.filesDir, "fonts").apply { mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.extension.lowercase() in diskExts }?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun systemFonts(): List<File> =
        listOf("/system/fonts", "/product/fonts", "/system_ext/fonts")
            .flatMap { File(it).listFiles()?.toList() ?: emptyList() }
            .filter { it.extension.lowercase() in diskExts }
            .sortedBy { it.name.lowercase() }

    fun delete(file: File) {
        file.delete()
        cache.evictAll()
        unblockAll(file.absolutePath)
    }

    // ------------------------------------------------------------ import

    private fun displayName(ctx: Context, uri: Uri): String? =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun sanitize(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "font" }

    private fun readCapped(input: InputStream, max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun sniff(b: ByteArray): String? {
        if (b.size < 12) return null
        val t = String(b, 0, 4, Charsets.ISO_8859_1)
        return when {
            t == "wOFF" -> "woff"
            t == "wOF2" -> "woff2"
            t == "OTTO" || t == "true" || t == "ttcf" ||
                (b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0) -> "sfnt"
            else -> null
        }
    }

    private fun sfntExt(b: ByteArray): String = when (String(b, 0, 4, Charsets.ISO_8859_1)) {
        "ttcf" -> "ttc"
        "OTTO" -> "otf"
        else -> "ttf"
    }

    private fun woff2ToSfnt(ctx: Context, bytes: ByteArray): ByteArray? {
        val tin = File(ctx.cacheDir, "w2_${System.nanoTime()}.woff2")
        val tout = File(ctx.cacheDir, "w2_${System.nanoTime()}.ttf")
        return try {
            tin.writeBytes(bytes)
            if (GifskiNative.nativeWoff2ToSfnt(tin.absolutePath, tout.absolutePath) && tout.exists()) tout.readBytes() else null
        } catch (e: Throwable) {
            null
        } finally {
            tin.delete(); tout.delete()
        }
    }

    /** Detects the real format from the file's bytes, converts web fonts, validates, and stores it. */
    private fun processBytes(ctx: Context, base: String, bytes: ByteArray): File? {
        val data: ByteArray = when (sniff(bytes) ?: return null) {
            "woff" -> Woff.toSfnt(bytes) ?: return null
            "woff2" -> woff2ToSfnt(ctx, bytes) ?: return null
            else -> bytes
        }
        val dest = File(dir(ctx), "$base.${sfntExt(data)}")
        dest.writeBytes(data)
        cache.evictAll()
        unblockAll(dest.absolutePath)
        if (FontParser.parse(dest).isEmpty() || create(dest.absolutePath) == null) {
            dest.delete()
            return null
        }
        return dest
    }

    private fun importZip(ctx: Context, uri: Uri): ImportResult {
        var res = ImportResult.EMPTY
        var entries = 0
        var total = 0L
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                while (entries < 500 && total < MAX_ZIP_TOTAL) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || e.name.startsWith("__MACOSX")) continue
                    val leaf = e.name.substringAfterLast('/')
                    if (leaf.startsWith("._")) continue
                    val ext = leaf.substringAfterLast('.', "").lowercase()
                    when {
                        ext in fontExts -> {
                            entries++
                            val bytes = readCapped(zip, MAX_FONT)
                            if (bytes == null) { res += ImportResult(emptyList(), 1, emptySet()); continue }
                            total += bytes.size
                            val f = processBytes(ctx, sanitize(leaf.substringBeforeLast('.')), bytes)
                            res += if (f != null) ImportResult(listOf(f), 0, emptySet()) else ImportResult(emptyList(), 1, emptySet())
                        }
                        ext in setOf("pfb", "pfa", "pfm", "bdf", "pcf", "fon", "eot", "dfont", "otb") ->
                            res += ImportResult(emptyList(), 0, setOf(".$ext"))
                    }
                }
            }
        }
        return res
    }

    /** Imports one file: a font, a web font, or a ZIP full of fonts. */
    fun importUri(ctx: Context, uri: Uri): ImportResult {
        return try {
            val name = displayName(ctx, uri) ?: "font_${System.currentTimeMillis()}"
            val ext = name.substringAfterLast('.', "").lowercase()
            when {
                ext == "zip" -> importZip(ctx, uri)
                ext in fontExts -> {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { readCapped(it, MAX_FONT) }
                    val f = bytes?.let { processBytes(ctx, sanitize(name.substringBeforeLast('.', name)), it) }
                    if (f != null) ImportResult(listOf(f), 0, emptySet()) else ImportResult(emptyList(), 1, emptySet())
                }
                else -> ImportResult(emptyList(), 0, setOf(if (ext.isEmpty()) "unknown" else ".$ext"))
            }
        } catch (e: Exception) {
            ImportResult(emptyList(), 1, emptySet())
        }
    }

    /** Imports every font (and font ZIP) found in a chosen folder, recursively and bounded. */
    suspend fun importTree(ctx: Context, tree: Uri): ImportResult = withContext(Dispatchers.IO) {
        var res = ImportResult.EMPTY
        val root = DocumentFile.fromTreeUri(ctx, tree) ?: return@withContext res
        val queue = ArrayDeque<Pair<DocumentFile, Int>>()
        queue.add(root to 0)
        var seen = 0
        while (queue.isNotEmpty() && seen < 3000) {
            val (dir, depth) = queue.removeFirst()
            for (f in dir.listFiles()) {
                seen++
                if (f.isDirectory) {
                    if (depth < 6) queue.add(f to depth + 1)
                } else {
                    val ext = (f.name ?: "").substringAfterLast('.', "").lowercase()
                    if (ext in fontExts || ext == "zip") res += importUri(ctx, f.uri)
                }
            }
        }
        res
    }

    // ------------------------------------------------------------ loading

    /** Safe, async load for previews: one at a time, cached, never throws. */
    suspend fun load(ref: String, vars: String? = null): FontLoad = withContext(Dispatchers.IO) {
        if (isBlocked(ref)) return@withContext FontLoad.Blocked
        val k = key(ref, vars)
        cache.get(k)?.let { return@withContext FontLoad.Ready(it) }
        loadLock.withLock {
            val hit = cache.get(k)
            if (hit != null) FontLoad.Ready(hit)
            else {
                val tf = create(ref, vars)
                if (tf == null) FontLoad.Failed else { cache.put(k, tf); FontLoad.Ready(tf) }
            }
        }
    }

    /** Synchronous lookup used by the text renderer; falls back to the default font if anything is wrong. */
    fun typeface(ref: String?, bold: Boolean, vars: String? = null): Typeface {
        if (ref == null) return if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val k = key(ref, vars)
        cache.get(k)?.let { return it }
        if (isBlocked(ref)) return Typeface.DEFAULT
        val tf = create(ref, vars) ?: return Typeface.DEFAULT
        cache.put(k, tf)
        return tf
    }
}
