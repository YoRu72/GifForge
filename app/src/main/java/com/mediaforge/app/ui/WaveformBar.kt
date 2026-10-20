package com.mediaforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.Waveform
import com.mediaforge.app.subs.AssEvent
import kotlin.math.abs

/**
 * AEG-B / 20.e: Aegisub's audio display on a phone. A window of the waveform around the playhead; the active line is
 * the bright range with two edge handles (drag = retime, like Aegisub's start/end markers), other lines are dim
 * bars, keyframes are vertical ticks, the playhead a white line. Pinch zooms the window (1.5 s - 30 s), a drag in
 * the middle scrolls, a tap seeks. With [snap] on, a handle within [snapMs] of a keyframe or a neighbour line edge
 * locks onto it (Aegisub snaps while Shift is held; a toggle replaces Shift).
 */
@Composable
fun WaveformBar(
    peaks: Waveform.Peaks?, loading: Boolean, playheadMs: Long, durationMs: Long,
    events: List<AssEvent>, active: Int, keyframes: List<Long>, snap: Boolean, snapMs: Long = 80,
    onSeek: (Long) -> Unit, onStart: (Long) -> Unit, onEnd: (Long) -> Unit,
) {
    var windowMs by remember { mutableFloatStateOf(6000f) }
    var centre by remember { mutableFloatStateOf(-1f) } // -1 = follow the playhead
    var dragging by remember { mutableStateOf(0) }      // 0 none, 1 start handle, 2 end handle, 3 scroll
    val line = events.getOrNull(active)
    val cm = if (centre < 0f) playheadMs.toFloat() else centre
    val left = (cm - windowMs / 2f).coerceAtLeast(0f)
    val cs = MaterialTheme.colorScheme
    val neighbours = remember(events, active) {
        events.indices.filter { it != active && !events[it].comment }.flatMap { listOf(events[it].startMs, events[it].endMs) }
    }
    fun snapped(t: Long): Long {
        if (!snap) return t
        val cands = keyframes.asSequence().filter { abs(it - t) <= snapMs } + neighbours.asSequence().filter { abs(it - t) <= snapMs }
        return cands.minByOrNull { abs(it - t) } ?: t
    }

    Box(Modifier.fillMaxWidth().height(72.dp)) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(line?.startMs, line?.endMs, windowMs, centre) {
                    val pxPerMs = size.width / windowMs
                    detectDragGestures(
                        onDragStart = { o ->
                            val l = line
                            dragging = if (l == null) 3 else {
                                val sx = (l.startMs - left) * pxPerMs; val ex = (l.endMs - left) * pxPerMs
                                when {
                                    abs(o.x - sx) < 28f -> 1
                                    abs(o.x - ex) < 28f -> 2
                                    else -> 3
                                }
                            }
                        },
                        onDragEnd = { dragging = 0 }, onDragCancel = { dragging = 0 },
                    ) { change, d ->
                        change.consume()
                        val l = line
                        when (dragging) {
                            1 -> if (l != null) onStart(snapped((l.startMs + d.x / pxPerMs).toLong()).coerceIn(0L, l.endMs - 1))
                            2 -> if (l != null) onEnd(snapped((l.endMs + d.x / pxPerMs).toLong()).coerceIn(l.startMs + 1, durationMs.coerceAtLeast(l.startMs + 1)))
                            else -> centre = (cm - d.x / pxPerMs).coerceIn(windowMs / 2f, (durationMs - windowMs / 2f).coerceAtLeast(windowMs / 2f))
                        }
                    }
                }
                .pointerInput(windowMs) {
                    detectTapGestures(onDoubleTap = { centre = -1f }) { o -> onSeek((left + o.x / size.width * windowMs).toLong().coerceIn(0L, durationMs)) }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) windowMs = (windowMs / zoom).coerceIn(1500f, 30000f) }
                },
        ) {
            val w = size.width; val h = size.height
            val pxPerMs = w / windowMs
            drawRect(Color(0xFF14181C), Offset.Zero, Size(w, h))
            // other lines
            for ((i, e) in events.withIndex()) {
                if (i == active || e.comment) continue
                val a = ((e.startMs - left) * pxPerMs).coerceIn(0f, w); val b = ((e.endMs - left) * pxPerMs).coerceIn(0f, w)
                if (b > a) drawRect(Color(0x3380A0C0), Offset(a, 0f), Size(b - a, h))
            }
            // active line range
            line?.let { l ->
                val a = ((l.startMs - left) * pxPerMs).coerceIn(-2f, w + 2f); val b = ((l.endMs - left) * pxPerMs).coerceIn(-2f, w + 2f)
                drawRect(cs.primary.copy(alpha = 0.22f), Offset(a, 0f), Size((b - a).coerceAtLeast(0f), h))
                drawLine(Color(0xFFFF5252), Offset(a, 0f), Offset(a, h), 3f)
                drawLine(Color(0xFFFFA726), Offset(b, 0f), Offset(b, h), 3f)
            }
            // keyframes
            for (k in keyframes) {
                if (k < left || k > left + windowMs) continue
                val x = (k - left) * pxPerMs
                drawLine(Color(0xFFE040FB), Offset(x, 0f), Offset(x, h * 0.25f), 2f)
            }
            // waveform
            val p = peaks
            if (p != null) {
                val cols = w.toInt()
                val mid = h / 2f
                val msPerPx = windowMs / w
                for (x in 0 until cols) {
                    val t0 = (left + x * msPerPx).toLong(); val t1 = (left + (x + 1) * msPerPx).toLong()
                    val v = p.max(t0, maxOf(t1, t0 + 1)) / 255f
                    if (v <= 0f) continue
                    val hh = v * (h / 2f - 2f)
                    drawLine(Color(0xFF66BB6A), Offset(x.toFloat(), mid - hh), Offset(x.toFloat(), mid + hh), 1f)
                }
                if (p.durationMs < left + windowMs && loading) {
                    val x = ((p.durationMs - left) * pxPerMs).coerceIn(0f, w)
                    drawRect(Color(0x22FFFFFF), Offset(x, 0f), Size(w - x, h))
                }
            } else if (loading) drawRect(Color(0x22FFFFFF), Offset.Zero, Size(w, h))
            // playhead
            val px = ((playheadMs - left) * pxPerMs)
            if (px in 0f..w) drawLine(Color.White, Offset(px, 0f), Offset(px, h), 2f)
        }
    }
}
