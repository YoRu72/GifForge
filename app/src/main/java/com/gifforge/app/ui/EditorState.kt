package com.gifforge.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gifforge.app.media.GifOptions
import com.gifforge.app.media.TextOverlay

const val MIN_GAP_MS = 200L

/** Everything the user can tweak. Step 4 adds text overlays here. */
class EditorState {
    var durationMs by mutableLongStateOf(0L)
    var startMs by mutableLongStateOf(0L)
    var endMs by mutableLongStateOf(0L)

    var fps by mutableIntStateOf(15)
    var quality by mutableIntStateOf(90)
    var speed by mutableFloatStateOf(1f)
    var loop by mutableStateOf(true)
    var fast by mutableStateOf(false)

    val crop = CropState()
    var overlays by mutableStateOf<List<TextOverlay>>(emptyList())
    var selectedId by mutableStateOf<Long?>(null)

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

    val frameCount: Int
        get() = ((endMs - startMs) / 1000.0 * fps).toInt().coerceAtLeast(1)

    fun setStart(ms: Long) { startMs = ms.coerceIn(0L, (endMs - MIN_GAP_MS).coerceAtLeast(0L)) }
    fun setEnd(ms: Long) { endMs = ms.coerceIn(startMs + MIN_GAP_MS, durationMs) }

    fun toOptions() = GifOptions(
        startMs = startMs, endMs = endMs, fps = fps,
        quality = quality, fast = fast, loop = loop, speed = speed, overlays = overlays,
        crop = crop.rect.takeIf { !it.isFull },
    )
}

fun fmtTime(ms: Long): String {
    val t = ms.coerceAtLeast(0L)
    val m = t / 60000
    val s = (t % 60000) / 1000
    val d = (t % 1000) / 100
    return "%d:%02d.%d".format(m, s, d)
}
