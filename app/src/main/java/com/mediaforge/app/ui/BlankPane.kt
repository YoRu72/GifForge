package com.mediaforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.SubFile
import com.mediaforge.app.subs.formatAssTime
import kotlinx.coroutines.delay

/**
 * AEGX1 "dummy video": a blank canvas at the script's PlayRes aspect with its own clock, so a script without a video
 * can still be previewed: effects, karaoke, positions, styles. Play runs the clock; the slider scrubs; "Play line"
 * starts at the active line. Hidden until the owner opens it (the chip in the empty video spot).
 */
@Composable
fun BlankPane(file: SubFile, events: List<AssEvent>, active: Int, onClose: () -> Unit) {
    val dur = (events.maxOfOrNull { it.endMs } ?: 0L).coerceAtLeast(5000L) + 1000L
    var pos by remember { mutableLongStateOf(events.getOrNull(active)?.startMs ?: 0L) }
    var playing by remember { mutableStateOf(false) }
    LaunchedEffect(playing) {
        var last = System.currentTimeMillis()
        while (playing) {
            delay(33)
            val now = System.currentTimeMillis(); pos += now - last; last = now
            if (pos >= dur) { pos = 0L }
        }
    }
    LaunchedEffect(active) { if (!playing) events.getOrNull(active)?.let { pos = it.startMs } }
    val aspect = file.playResX.toFloat() / file.playResY.coerceAtLeast(1)
    ForceLtr {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF2E3A46)), contentAlignment = Alignment.Center) {
                val shown = events.filter { !it.comment && pos >= it.startMs && pos < it.endMs }
                SubtitleOverlay(file, shown, pos, aspect, Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { playing = !playing }) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null) }
                Text(formatAssTime(pos), style = MaterialTheme.typography.labelSmall)
                Slider(pos.toFloat(), { pos = it.toLong(); playing = false }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 0f..dur.toFloat())
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = { events.getOrNull(active)?.let { pos = it.startMs; playing = true } }, label = { Text(stringResource(R.string.sv_play_line)) })
                AssistChip(onClick = onClose, label = { Text(stringResource(R.string.bp_close)) })
                Text(stringResource(R.string.bp_note), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
