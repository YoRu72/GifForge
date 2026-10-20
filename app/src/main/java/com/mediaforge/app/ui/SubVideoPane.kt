package com.mediaforge.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.mediaforge.app.R
import com.mediaforge.app.media.readVideoInfo
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.SubFile
import kotlinx.coroutines.delay

/** Controls the workspace needs from the video: the line timing buttons read the playhead and move the active line. */
class SubVideoControls(
    val playheadMs: () -> Long,
    val seekTo: (Long) -> Unit,
    val frameMs: Float,
)

/**
 * A13: video docked on top of the subtitle list. Shows the current line over the picture, a transport bar,
 * and the timing buttons: set start / set end from the playhead, nudges, "end + next start" tap timing,
 * play the active line, and follow-playhead (the grid selects the line that is on screen).
 * The overlay draws style, tags and effects (SubOverlay.kt); exact libass parity is A21.
 */
@Composable
fun SubVideoPane(
    uri: Uri,
    file: SubFile,
    events: List<AssEvent>,
    active: Int,
    onActive: (Int) -> Unit,
    onSetStart: (Long) -> Unit,
    onSetEnd: (Long) -> Unit,
    onTapChain: (Long) -> Unit,
    onNudge: (startDelta: Long, endDelta: Long) -> Unit,
    onKaraTap: ((Long) -> Unit)? = null,   // karaoke tap timing: the next syllable starts at the playhead
    onVisual: ((dx: Float, dy: Float, zoom: Float, rot: Float) -> Unit)? = null, // AEG-D: drag/pinch/twist the active line on the picture
    onStartAt: ((Long) -> Unit)? = null,   // waveform handles (absolute ms)
    onEndAt: ((Long) -> Unit)? = null,
) {
    var typeset by remember { mutableStateOf(false) } // visual typesetting mode: gestures edit the line instead of seeking
    var safeArea by remember { mutableStateOf(false) }
    var wave by remember { mutableStateOf(true) }
    var snap by remember { mutableStateOf(true) }
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); setSeekParameters(SeekParameters.EXACT); prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var frameMs by remember(uri) { mutableStateOf(1000f / 25f) }
    var aspect by remember(uri) { mutableStateOf(0f) }
    var videoH by remember(uri) { mutableStateOf(0) } // real video height: border/shadow in video pixels when ScaledBorderAndShadow is off
    LaunchedEffect(uri) {
        readVideoInfo(ctx, uri)?.let { i ->
            durationMs = i.durationMs
            if (i.width > 0 && i.height > 0) { aspect = i.width.toFloat() / i.height; videoH = i.height }
            i.fps?.takeIf { it in 5f..240f }?.let { frameMs = 1000f / it }
        }
    }

    var pos by remember { mutableLongStateOf(0L) }
    var follow by remember { mutableStateOf(false) }
    var loopLine by remember { mutableStateOf(false) }
    val activeNow by rememberUpdatedStateCompat(active)
    val eventsNow by rememberUpdatedStateCompat(events)
    LaunchedEffect(player) {
        while (true) {
            pos = player.currentPosition
            val ev = eventsNow
            if (loopLine) {
                ev.getOrNull(activeNow)?.let { e ->
                    if (player.isPlaying && (pos >= e.endMs || pos < e.startMs - 300)) player.seekTo(e.startMs)
                }
            } else if (follow && player.isPlaying) {
                val i = ev.indexOfFirst { !it.comment && pos >= it.startMs && pos < it.endMs }
                if (i >= 0 && i != activeNow) onActive(i)
            }
            delay(40)
        }
    }

    // Time always runs left to right, also on Arabic screens.
    ForceLtr {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(190.dp).background(Color.Black), contentAlignment = Alignment.BottomCenter) {
                AndroidView(
                    factory = { c -> PlayerView(c).apply { this.player = player; useController = false } },
                    modifier = Modifier.fillMaxSize(),
                )
                val shown = events.filter { !it.comment && pos >= it.startMs && pos < it.endMs }
                val asp = if (aspect > 0f) aspect else file.playResX.toFloat() / file.playResY.coerceAtLeast(1)
                SubtitleOverlay(file = file, shown = shown, posMs = pos, aspect = asp, modifier = Modifier.fillMaxSize(), videoHeight = videoH)
                if (safeArea) SafeAreaOverlay(asp)
                if (typeset && onVisual != null) {
                    // one gesture = one edit: script px per screen px from the picture's drawn size
                    androidx.compose.foundation.Canvas(
                        Modifier.fillMaxSize().pointerInput(asp, file.playResX, file.playResY) {
                            val vw: Float; val vh: Float
                            if (size.width.toFloat() / size.height > asp) { vh = size.height.toFloat(); vw = vh * asp } else { vw = size.width.toFloat(); vh = vw / asp }
                            val kx = file.playResX / vw; val ky = file.playResY / vh
                            androidx.compose.foundation.gestures.detectTransformGestures { _, pan, zoom, rot ->
                                onVisual(pan.x * kx, pan.y * ky, zoom, rot)
                            }
                        },
                    ) { drawRect(androidx.compose.ui.graphics.Color(0x2200E5FF)) }
                }
            }
            TransportBar(player, durationMs.coerceAtLeast(1L), 0L, durationMs.coerceAtLeast(1L))
            // AEG-B: the audio display with the active line's handles
            val peaksState = androidx.compose.runtime.produceState<Pair<com.mediaforge.app.media.Waveform.Peaks?, Boolean>>(null to true, uri, wave) {
                if (!wave) { value = null to false; return@produceState }
                value = (com.mediaforge.app.media.Waveform.cached(ctx, uri)) to true
                if (value.first == null) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val p = com.mediaforge.app.media.Waveform.build(ctx, uri) { partial -> value = partial to true; true }
                    value = p to false
                } else value = value.first to false
            }
            val kfs by androidx.compose.runtime.produceState(emptyList<Long>(), uri) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.mediaforge.app.media.Keyframes.read(ctx, uri) }
            }
            if (wave) WaveformBar(
                peaks = peaksState.value.first, loading = peaksState.value.second, playheadMs = pos, durationMs = durationMs,
                events = events, active = active, keyframes = kfs, snap = snap,
                onSeek = { player.pause(); player.seekTo(it) },
                onStart = { t -> onStartAt?.invoke(t) ?: onSetStart(t) }, onEnd = { t -> onEndAt?.invoke(t) ?: onSetEnd(t) },
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(onClick = { onSetStart(player.currentPosition) }, label = { Text(stringResource(R.string.sv_set_start)) })
                AssistChip(onClick = { onSetEnd(player.currentPosition) }, label = { Text(stringResource(R.string.sv_set_end)) })
                AssistChip(onClick = { onTapChain(player.currentPosition) }, label = { Text(stringResource(R.string.sv_tap_chain)) })
                AssistChip(onClick = { player.pause(); player.seekTo(events.getOrNull(active)?.startMs ?: 0L); player.play() }, label = { Text(stringResource(R.string.sv_play_line)) })
                if (onKaraTap != null) AssistChip(onClick = { onKaraTap(player.currentPosition) }, label = { Text(stringResource(R.string.ka_tap_chip)) })
                FilterChip(selected = loopLine, onClick = { loopLine = !loopLine }, label = { Text(stringResource(R.string.sv_loop_line)) })
                FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text(stringResource(R.string.sv_follow)) })
                if (onVisual != null) FilterChip(selected = typeset, onClick = { typeset = !typeset }, label = { Text(stringResource(R.string.sv_typeset)) })
                FilterChip(selected = wave, onClick = { wave = !wave }, label = { Text(stringResource(R.string.sv_wave)) })
                if (wave) FilterChip(selected = snap, onClick = { snap = !snap }, label = { Text(stringResource(R.string.sv_snap)) })
                FilterChip(selected = safeArea, onClick = { safeArea = !safeArea }, label = { Text(stringResource(R.string.sv_safe)) })
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                val f = frameMs.toLong().coerceAtLeast(1L)
                Text(stringResource(R.string.sv_start), style = MaterialTheme.typography.labelSmall)
                AssistChip(onClick = { onNudge(-f, 0) }, label = { Text("-1f") })
                AssistChip(onClick = { onNudge(-10, 0) }, label = { Text("-10") })
                AssistChip(onClick = { onNudge(10, 0) }, label = { Text("+10") })
                AssistChip(onClick = { onNudge(f, 0) }, label = { Text("+1f") })
                Text(stringResource(R.string.sv_end), style = MaterialTheme.typography.labelSmall)
                AssistChip(onClick = { onNudge(0, -f) }, label = { Text("-1f") })
                AssistChip(onClick = { onNudge(0, -10) }, label = { Text("-10") })
                AssistChip(onClick = { onNudge(0, 10) }, label = { Text("+10") })
                AssistChip(onClick = { onNudge(0, f) }, label = { Text("+1f") })
                Text(stringResource(R.string.sv_frame_step), style = MaterialTheme.typography.labelSmall)
                AssistChip(onClick = { player.pause(); player.seekTo((player.currentPosition - f).coerceAtLeast(0)) }, label = { Text("<") })
                AssistChip(onClick = { player.pause(); player.seekTo(player.currentPosition + f) }, label = { Text(">") })
            }
        }
    }
}

@Composable
private fun <T> rememberUpdatedStateCompat(v: T) = androidx.compose.runtime.rememberUpdatedState(v)

/** AEGX3: title-safe (90 %) and action-safe (80 %) frames plus the rule of thirds, drawn on the picture area only. */
@Composable
private fun SafeAreaOverlay(aspect: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val vw: Float; val vh: Float
        if (size.width / size.height > aspect) { vh = size.height; vw = vh * aspect } else { vw = size.width; vh = vw / aspect }
        val l = (size.width - vw) / 2f; val t = (size.height - vh) / 2f
        fun frame(k: Float, c: Color) {
            val mx = vw * (1 - k) / 2f; val my = vh * (1 - k) / 2f
            drawRect(c, androidx.compose.ui.geometry.Offset(l + mx, t + my), androidx.compose.ui.geometry.Size(vw - 2 * mx, vh - 2 * my), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        frame(0.9f, Color(0xAAFFEB3B)); frame(0.8f, Color(0xAAFF5252))
        for (i in 1..2) {
            drawLine(Color(0x66FFFFFF), androidx.compose.ui.geometry.Offset(l + vw * i / 3f, t), androidx.compose.ui.geometry.Offset(l + vw * i / 3f, t + vh), 1f)
            drawLine(Color(0x66FFFFFF), androidx.compose.ui.geometry.Offset(l, t + vh * i / 3f), androidx.compose.ui.geometry.Offset(l + vw, t + vh * i / 3f), 1f)
        }
    }
}
