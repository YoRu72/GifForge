package com.mediaforge.app.ui

import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

/** Subtitle-style timing: a layer can be limited to a window A..B (source-video time) set from the playhead. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimingControls(
    timed: Boolean,
    fromMs: Long,
    toMs: Long,
    state: EditorState,
    player: Player,
    onChange: (Boolean, Long, Long) -> Unit,
) {
    val dur = state.durationMs.coerceAtLeast(MIN_GAP_MS * 2)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tm_range))
        Switch(
            checked = timed,
            onCheckedChange = { on ->
                if (on) {
                    val p = player.currentPosition.coerceIn(state.startMs, state.endMs)
                    onChange(true, p, maxOf(minOf(p + 2000L, dur), p + MIN_GAP_MS))
                } else onChange(false, fromMs, toMs)
            },
        )
    }
    if (!timed) return
    val a0 = fromMs.coerceIn(0L, dur - MIN_GAP_MS)
    val b0 = toMs.coerceIn(a0 + MIN_GAP_MS, dur)
    Text(stringResource(R.string.tm_appears, fmtTime(a0), fmtTime(b0)))
    LtrRangeSlider(
        value = a0.toFloat()..b0.toFloat(),
        onValueChange = { r ->
            val a = r.start.toLong()
            val b = r.endInclusive.toLong()
            if (b - a >= MIN_GAP_MS) onChange(true, a, b)
        },
        valueRange = 0f..dur.toFloat(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val a = player.currentPosition.coerceIn(0L, dur - MIN_GAP_MS)
            onChange(true, a, maxOf(b0, a + MIN_GAP_MS))
        }) { Text(stringResource(R.string.tm_a_ph)) }
        OutlinedButton(onClick = {
            val b = player.currentPosition.coerceIn(a0 + MIN_GAP_MS, dur)
            onChange(true, a0, b)
        }) { Text(stringResource(R.string.tm_b_ph)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { player.pause(); player.seekTo(a0) }) { Text(stringResource(R.string.tm_go_a)) }
        OutlinedButton(onClick = { player.pause(); player.seekTo(b0) }) { Text(stringResource(R.string.tm_go_b)) }
    }
    Text(
        stringResource(R.string.tm_hint),
        style = MaterialTheme.typography.bodySmall,
    )
}
