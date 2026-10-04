package com.mediaforge.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.mediaforge.app.media.AssPreview
import com.mediaforge.app.media.FontCatalog
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.SubFile
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Font family name (lower case) -> font ref, for the fonts in the app library. Regular faces win over bold or italic. */
internal fun fontRefMap(ctx: Context): Map<String, String> {
    val out = HashMap<String, String>()
    val faces = FontCatalog.scan(FontStore.list(ctx)).sortedBy { if (it.weight in 350..500 && !it.italic) 0 else 1 }
    for (f in faces) out.putIfAbsent(f.family.trim().lowercase(Locale.ROOT), f.ref)
    return out
}

/** The typeface for an ASS font name: a library font when the family matches, else the system's best guess. */
internal fun faceFor(refs: Map<String, String>, name: String, bold: Boolean): Typeface {
    val ref = refs[name.trim().lowercase(Locale.ROOT)]
    return if (ref != null) FontStore.typeface(ref, bold) else Typeface.create(name, if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** Two bitmaps used in turn, so a frame the screen still reads is never the one being redrawn. */
private class FrameBuffers {
    private val bmps = arrayOfNulls<Bitmap>(2)
    private var next = 0
    fun get(w: Int, h: Int): Bitmap {
        next = 1 - next
        val b = bmps[next]
        if (b != null && b.width == w && b.height == h) return b
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmps[next] = it }
    }
}

/**
 * Styled subtitle preview on top of the video: font, size, colours, outline, shadow, blur, scale, rotation,
 * alignment, position and the effect keyframes at [posMs]. [aspect] is the video's width / height.
 */
@Composable
fun SubtitleOverlay(file: SubFile, shown: List<AssEvent>, posMs: Long, aspect: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val refs by produceState(emptyMap<String, String>(), ctx) {
        value = withContext(Dispatchers.IO) { fontRefMap(ctx) }
    }
    val buffers = remember { FrameBuffers() }
    Canvas(modifier) {
        if (shown.isEmpty() || aspect <= 0f || size.width <= 0f || size.height <= 0f) return@Canvas
        val vw: Float
        val vh: Float
        if (size.width / size.height > aspect) { vh = size.height; vw = vh * aspect } else { vw = size.width; vh = vw / aspect }
        val bw = minOf(vw, 1280f).toInt().coerceAtLeast(2)
        val bh = (bw / aspect).toInt().coerceAtLeast(2)
        val bmp = buffers.get(bw, bh)
        bmp.eraseColor(0)
        AssPreview.draw(android.graphics.Canvas(bmp), bw, bh, file, shown, posMs) { name, bold -> faceFor(refs, name, bold) }
        val left = (size.width - vw) / 2f
        val top = (size.height - vh) / 2f
        drawIntoCanvas { c -> c.nativeCanvas.drawBitmap(bmp, null, RectF(left, top, left + vw, top + vh), null) }
    }
}
