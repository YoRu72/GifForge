package com.gifforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.gifforge.app.media.VideoInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimTab(state: EditorState, info: VideoInfo?, player: Player) {
    if (info == null || state.durationMs <= 0) {
        Text("Loading video...", Modifier.padding(16.dp))
        return
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Start ${fmtTime(state.startMs)}")
            Text("End ${fmtTime(state.endMs)}")
        }
        RangeSlider(
            value = state.startMs.toFloat()..state.endMs.toFloat(),
            onValueChange = { r ->
                val s = r.start.toLong()
                val e = r.endInclusive.toLong()
                if (e - s >= MIN_GAP_MS) {
                    val startMoved = s != state.startMs
                    state.startMs = s
                    state.endMs = e
                    player.pause()
                    player.seekTo(if (startMoved) s else e)
                }
            },
            valueRange = 0f..state.durationMs.toFloat(),
        )
        val len = state.endMs - state.startMs
        Text("Length ${fmtTime(len)} - ${state.frameCount} frames")
        if (state.frameCount > 300) {
            Text(
                "Long GIFs are slow to encode and produce big files.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.setStart(player.currentPosition) }) { Text("Start = playhead") }
            OutlinedButton(onClick = { state.setEnd(player.currentPosition) }) { Text("End = playhead") }
        }
        Button(onClick = {
            player.seekTo(state.startMs)
            player.play()
        }) { Text("Preview selection") }
    }
}
