package com.mediaforge.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediaforge.app.R
import com.mediaforge.app.media.FrameGrabber
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.AssLook
import com.mediaforge.app.subs.AssStyle
import com.mediaforge.app.subs.MotionApply
import com.mediaforge.app.subs.PointTracker
import com.mediaforge.app.subs.SubFile
import com.mediaforge.app.subs.TrackPt
import com.mediaforge.app.subs.formatAssTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val WORK_W = 480 // working width: enough detail to track, small enough to be quick

/**
 * MOT1 + MOT2 + MOT4: make the active line follow something in the video.
 * Auto: tap the object on the first frame, Track (the patch is followed frame by frame; it stops where it is lost).
 * Manual: move through the line, tap where the object is at each chosen moment (straight moves between the taps).
 * Output: one line per frame (frame-exact, Aegisub-Motion style) or fewer lines with \move segments.
 */
@Composable
fun MotionDialog(file: SubFile, e: AssEvent, video: Uri, fps: Float, onDismiss: () -> Unit, onApply: (List<AssEvent>) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var auto by remember { mutableStateOf(true) }
    var tView by remember { mutableStateOf(0L) }          // ms from line start, shown frame
    val frame by produceState<Bitmap?>(null, tView) { value = withContext(Dispatchers.IO) { FrameGrabber.frameAt(ctx, video, e.startMs + tView, WORK_W) } }
    var pick by remember { mutableStateOf<Offset?>(null) }  // auto: start point (working px)
    var half by remember { mutableFloatStateOf(12f) }
    var path by remember { mutableStateOf<List<TrackPt>>(emptyList()) } // working px
    var keys by remember { mutableStateOf<List<TrackPt>>(emptyList()) } // manual keypoints (working px)
    var progress by remember { mutableFloatStateOf(-1f) }
    var lostAt by remember { mutableStateOf<Long?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var perFrame by remember { mutableStateOf(false) }
    var tol by remember { mutableFloatStateOf(1.5f) }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    val fw = frame?.width ?: WORK_W
    val fh = frame?.height ?: (WORK_W * file.playResY / file.playResX.coerceAtLeast(1))
    val kx = file.playResX.toFloat() / fw
    val ky = file.playResY.toFloat() / fh
    val style = file.style(e.style) ?: AssStyle()
    val anchor = AssLook.anchor(file, style, e)

    fun runTrack() {
        val p = pick ?: return
        job?.cancel()
        path = emptyList(); lostAt = null; progress = 0f
        job = scope.launch(Dispatchers.Default) {
            val tracker = PointTracker(half = half.roundToInt(), radius = 24)
            val out = ArrayList<TrackPt>()
            var first = true
            FrameGrabber.frames(ctx, video, e.startMs, e.endMs, fps, WORK_W) { t, g, bmp ->
                bmp.recycle()
                if (first) { tracker.start(g, p.x, p.y); out += TrackPt(t, p.x, p.y, 1f); first = false }
                else {
                    val (x, y, c) = tracker.next(g)
                    if (c < tracker.lostBelow) { lostAt = t; return@frames false }
                    out += TrackPt(t, x, y, c)
                }
                progress = (t.toFloat() / e.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                path = out.toList()
                job?.isActive != false
            }
            progress = -1f
        }
    }

    Dialog(onDismissRequest = { job?.cancel(); onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.mt_title), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(auto, { auto = true }, { Text(stringResource(R.string.mt_auto)) })
                    FilterChip(!auto, { auto = false }, { Text(stringResource(R.string.mt_manual)) })
                }
                Text(stringResource(if (auto) R.string.mt_auto_hint else R.string.mt_manual_hint), style = MaterialTheme.typography.bodySmall)
                // the picture is physical: never mirrored on Arabic screens
                ForceLtr {
                    Box(Modifier.fillMaxWidth().aspectRatio(fw.toFloat() / fh).background(Color.Black)) {
                        val b = frame
                        if (b == null) CircularProgressIndicator(Modifier.align(Alignment.Center))
                        else Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize().pointerInput(auto, tView, fw, fh) {
                            detectTapGestures { o ->
                                val x = o.x / size.width * fw
                                val y = o.y / size.height * fh
                                if (auto) { pick = Offset(x, y); path = emptyList(); lostAt = null }
                                else keys = (keys.filter { kotlin.math.abs(it.tMs - tView) > 20 } + TrackPt(tView, x, y)).sortedBy { it.tMs }
                            }
                        }) {
                            val sx = size.width / fw
                            val sy = size.height / fh
                            val pts = if (auto) path else keys
                            for (i in 1 until pts.size) drawLine(Color(0xFF00E5FF), Offset(pts[i - 1].x * sx, pts[i - 1].y * sy), Offset(pts[i].x * sx, pts[i].y * sy), 3f)
                            keys.takeIf { !auto }?.forEach { drawCircle(Color(0xFFFFEB3B), 7f, Offset(it.x * sx, it.y * sy)) }
                            pick?.takeIf { auto }?.let { p ->
                                val r = half * sx
                                drawRect(Color(0xFFFF4081), Offset(p.x * sx - r, p.y * sy - r), androidx.compose.ui.geometry.Size(2 * r, 2 * r), style = Stroke(3f))
                            }
                            // where the text sits now
                            drawCircle(Color.White, 6f, Offset(anchor.first / kx * sx, anchor.second / ky * sy), style = Stroke(2f))
                        }
                    }
                }
                ForceLtr {
                    Column {
                        Text(formatAssTime(e.startMs + tView), style = MaterialTheme.typography.labelMedium)
                        Slider(tView.toFloat(), { tView = it.toLong() }, valueRange = 0f..e.durationMs.coerceAtLeast(1).toFloat())
                    }
                }
                if (auto) {
                    LabeledSlider(stringResource(R.string.mt_patch), "${(half * 2 + 1).roundToInt()} px", half, 6f..30f, 0) { half = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(enabled = pick != null && progress < 0f, onClick = { tView = 0L; runTrack() }) { Text(stringResource(R.string.mt_track)) }
                        if (progress >= 0f) TextButton(onClick = { job?.cancel(); progress = -1f }) { Text(stringResource(R.string.cancel)) }
                    }
                    if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    if (path.isNotEmpty()) Text(stringResource(R.string.mt_tracked, path.size), style = MaterialTheme.typography.bodySmall)
                    lostAt?.let { Text(stringResource(R.string.mt_lost, formatAssTime(e.startMs + it)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                } else {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        keys.forEach { k -> AssistChip(onClick = { keys = keys - k }, label = { Text(formatAssTime(e.startMs + k.tMs) + "  ✕") }) }
                    }
                }
                Text(stringResource(R.string.mt_output), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!perFrame, { perFrame = false }, { Text(stringResource(R.string.mt_out_move)) })
                    FilterChip(perFrame, { perFrame = true }, { Text(stringResource(R.string.mt_out_frames)) })
                }
                if (!perFrame) LabeledSlider(stringResource(R.string.mt_tolerance), String.format(java.util.Locale.US, "%.1f px", tol), tol, 0.5f..6f, 0) { tol = it }
                val src = if (auto) path else keys
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { job?.cancel(); onDismiss() }) { Text(stringResource(R.string.cancel)) }
                    Button(enabled = src.size >= 2 && progress < 0f, onClick = {
                        val scaled = src.map { it.copy(x = it.x * kx, y = it.y * ky) }
                        // manual keypoints: straight moves between them (no simplification needed); frames: one line each
                        val out = if (!auto && perFrame) {
                            val step = (1000f / (if (fps in 5f..240f) fps else 25f)).toLong().coerceAtLeast(1)
                            val dense = (0..e.durationMs step step).map { t -> MotionApply.at(scaled, t).let { (x, y) -> TrackPt(t, x, y) } }
                            MotionApply.apply(e, dense, anchor.first, anchor.second, perFrame = true)
                        } else MotionApply.apply(e, scaled, anchor.first, anchor.second, perFrame, if (auto) tol else 0.01f)
                        onApply(out)
                    }) { Text(stringResource(R.string.mt_apply)) }
                }
                Box(Modifier.height(8.dp))
            }
        }
    }
}
