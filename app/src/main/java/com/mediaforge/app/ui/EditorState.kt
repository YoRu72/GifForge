package com.mediaforge.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mediaforge.app.media.CaptionBar
import com.mediaforge.app.Prefs
import com.mediaforge.app.media.GifOptions
import com.mediaforge.app.media.PlayMode
import com.mediaforge.app.media.ShapeElement
import com.mediaforge.app.media.ShapeKind
import com.mediaforge.app.media.TextOverlay

const val MIN_GAP_MS = 200L

/** Everything the user can tweak. Step 4 adds text overlays here. */
class EditorState {
    var durationMs by mutableLongStateOf(0L)
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(0L)

    var fps by mutableIntStateOf(Prefs.defFps.value)
    var quality by mutableIntStateOf(Prefs.defQuality.value)
    var maxWidth by mutableIntStateOf(Prefs.defMaxWidth.value) // 0 = original
    var playMode by mutableStateOf(PlayMode.NORMAL)
    var speed by mutableFloatStateOf(1f)
    var loop by mutableStateOf(Prefs.defLoop.value)
    var fast by mutableStateOf(Prefs.defFast.value)

    val crop = CropState()
    var overlays by mutableStateOf<List<TextOverlay>>(emptyList())
    var selectedId by mutableStateOf<Long?>(null)
    var elements by mutableStateOf<List<ShapeElement>>(emptyList())
    var selectedElId by mutableStateOf<Long?>(null)
    var topBar by mutableStateOf<CaptionBar?>(null)
    var bottomBar by mutableStateOf<CaptionBar?>(null)

    fun selected(): TextOverlay? = overlays.firstOrNull { it.id == selectedId }
    fun addOverlay() {
        val o = TextOverlay(id = System.nanoTime())
        overlays = overlays + o
        selectedId = o.id
    }
    fun removeSelected() {
        overlays = overlays.filter { it.id != selectedId }
        selectedId = overlays.lastOrNull()?.id
    }
    fun update(id: Long, f: (TextOverlay) -> TextOverlay) {
        overlays = overlays.map { if (it.id == id) f(it) else it }
    }

    val hasTimed: Boolean get() = overlays.any { it.timed } || elements.any { it.timed }

    fun selectedElement(): ShapeElement? = elements.firstOrNull { it.id == selectedElId }
    fun addElement(kind: ShapeKind) {
        val e = ShapeElement.default(kind, System.nanoTime())
        elements = elements + e
        selectedElId = e.id
    }
    fun duplicateSelectedElement() {
        val s = selectedElement() ?: return
        val c = s.copy(id = System.nanoTime(), cx = (s.cx + 0.05f).coerceAtMost(1f), cy = (s.cy + 0.05f).coerceAtMost(1f))
        elements = elements + c
        selectedElId = c.id
    }
    fun removeSelectedElement() {
        elements = elements.filter { it.id != selectedElId }
        selectedElId = elements.lastOrNull()?.id
    }
    fun updateElement(id: Long, f: (ShapeElement) -> ShapeElement) {
        elements = elements.map { if (it.id == id) f(it) else it }
    }
    /** Emoji are ordinary text layers (system emoji font), no outline. */
    fun addEmoji(e: String) {
        val o = TextOverlay(id = System.nanoTime(), text = e, bold = false, sizePct = 20f, strokeRatio = 0f, posY = 0.5f)
        overlays = overlays + o
        selectedId = o.id
    }

    val frameCount: Int
        get() = ((endMs - startMs) / 1000.0 * fps).toInt().coerceAtLeast(1)

    /** Frames that actually go into the GIF (ping-pong plays forward then back). */
    val encodedFrames: Int
        get() = frameCount.let { n -> if (playMode == PlayMode.PINGPONG && n >= 3) 2 * n - 2 else n }

    fun setStart(ms: Long) { startMs = ms.coerceIn(0L, (endMs - MIN_GAP_MS).coerceAtLeast(0L)) }
    fun setEnd(ms: Long) { endMs = ms.coerceIn(startMs + MIN_GAP_MS, durationMs) }

    fun toOptions() = GifOptions(
        startMs = startMs, endMs = endMs, fps = fps,
        quality = quality, fast = fast, loop = loop, speed = speed, overlays = overlays,
        crop = crop.rect.takeIf { !it.isFull },
        topBar = topBar, bottomBar = bottomBar, elements = elements,
        maxWidth = maxWidth, mode = playMode,
    )
}

fun fmtTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val m = t / 60000
    val s = (t % 60000) / 1000
    val d = (t % 1000) / 100
    return "%d:%02d.%d".format(m, s, d)
}
