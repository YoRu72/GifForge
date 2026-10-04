package com.mediaforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import kotlinx.coroutines.delay

/**
 * Slim play/seek bar that lives BELOW the video (never over it), so text overlays can't
 * collide with it and pressing it never covers the preview.
 */
@Composable
fun TransportBar(player: ExoPlayer, durationMs: Long, startMs: Long, endMs: Long) {
    var pos by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            if (!scrubbing) pos = player.currentPosition
            playing = player.isPlaying
            delay(50)
        }
    }

    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { if (player.isPlaying) player.pause() else player.play() },
            modifier = Modifier.size(36.dp),
        ) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pause" else "Play") }

        Text(fmtTime(pos), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)

        SeekTrack(
            pos = pos, duration = durationMs, startMs = startMs, endMs = endMs,
            modifier = Modifier.weight(1f),
            onScrubStart = {
                scrubbing = true
                player.pause()
                player.setSeekParameters(SeekParameters.CLOSEST_SYNC) // fast, smooth scrubbing
            },
            onSeek = { ms -> pos = ms; player.seekTo(ms) },
            onScrubEnd = {
                player.setSeekParameters(SeekParameters.EXACT)
                player.seekTo(pos)
                scrubbing = false
            },
        )

        Text(fmtTime(durationMs), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun SeekTrack(
    pos: Long,
    duration: Long,
    startMs: Long,
    endMs: Long,
    modifier: Modifier,
    onScrubStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubEnd: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Canvas(
        modifier
            .fillMaxHeight()
            .pointerInput(duration) {
                detectTapGestures { o ->
                    if (duration > 0) {
                        onScrubStart()
                        onSeek((o.x / size.width * duration).toLong().coerceIn(0L, duration))
                        onScrubEnd()
                    }
                }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        if (duration > 0) {
                            onScrubStart()
                            onSeek((o.x / size.width * duration).toLong().coerceIn(0L, duration))
                        }
                    },
                    onDragEnd = { onScrubEnd() },
                    onDragCancel = { onScrubEnd() },
                ) { change, _ ->
                    change.consume()
                    if (duration > 0) onSeek((change.position.x / size.width * duration).toLong().coerceIn(0L, duration))
                }
            },
    ) {
        if (duration <= 0) return@Canvas
        val w = size.width
        val cy = size.height / 2f
        val thin = 3.dp.toPx()
        fun x(ms: Long) = (ms.toFloat() / duration).coerceIn(0f, 1f) * w

        drawLine(track, Offset(0f, cy), Offset(w, cy), thin, StrokeCap.Round)
        if (endMs > startMs) { // the part that will become the GIF
            drawLine(primary.copy(alpha = 0.35f), Offset(x(startMs), cy), Offset(x(endMs), cy), thin * 2.2f, StrokeCap.Round)
        }
        drawLine(primary, Offset(0f, cy), Offset(x(pos), cy), thin, StrokeCap.Round)
        drawCircle(primary, 6.dp.toPx(), Offset(x(pos), cy))
    }
}
