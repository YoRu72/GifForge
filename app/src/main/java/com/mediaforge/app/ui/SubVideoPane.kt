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
import kotlinx.coroutines.delay

/** Controls the workspace needs from the video: the line timing buttons read the playhead and move the active line. */
class SubVideoControls(
    val playheadMs: () -> Long,
    val seekTo: (Long) -> Unit,
    val frameMs: Float,
)

/** Removes override blocks and breaks so the overlay shows the line as plain text. */
private fun overlayText(s: String): String =
    s.replace(Regex("\\{[^}]*\\}"), "").replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ")

/**
 * A13: video docked on top of the subtitle list. Shows the current line over the picture, a transport bar,
 * and the timing buttons: set start / set end from the playhead, nudges, "end + next start" tap timing,
 * play the active line, and follow-playhead (the grid selects the line that is on screen).
 * The overlay is a plain preview; the real ASS renderer arrives with A21.
 */
@Composable
fun SubVideoPane(
    uri: Uri,
    events: List<AssEvent>,
    active: Int,
    onActive: (Int) -> Unit,
    onSetStart: (Long) -> Unit,
    onSetEnd: (Long) -> Unit,
    onTapChain: (Long) -> Unit,
    onNudge: (startDelta: Long, endDelta: Long) -> Unit,
) {
    val ctx = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(uri)); setSeekParameters(SeekParameters.EXACT); prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }

    var durationMs by remember(uri) { mutableLongStateOf(0L) }
    var frameMs by remember(uri) { mutableStateOf(1000f / 25f) }
    LaunchedEffect(uri) {
        readVideoInfo(ctx, uri)?.let { i ->
            durationMs = i.durationMs
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
                if (shown.isNotEmpty()) {
                    Text(
                        shown.joinToString("\n") { overlayText(it.text) },
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp).background(Color(0x99000000)),
                        color = Color.White, textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                    )
                }
            }
            TransportBar(player, durationMs.coerceAtLeast(1L), 0L, durationMs.coerceAtLeast(1L))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(onClick = { onSetStart(player.currentPosition) }, label = { Text(stringResource(R.string.sv_set_start)) })
                AssistChip(onClick = { onSetEnd(player.currentPosition) }, label = { Text(stringResource(R.string.sv_set_end)) })
                AssistChip(onClick = { onTapChain(player.currentPosition) }, label = { Text(stringResource(R.string.sv_tap_chain)) })
                AssistChip(onClick = { player.pause(); player.seekTo(events.getOrNull(active)?.startMs ?: 0L); player.play() }, label = { Text(stringResource(R.string.sv_play_line)) })
                FilterChip(selected = loopLine, onClick = { loopLine = !loopLine }, label = { Text(stringResource(R.string.sv_loop_line)) })
                FilterChip(selected = follow, onClick = { follow = !follow }, label = { Text(stringResource(R.string.sv_follow)) })
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
