package com.mediaforge.app.ui

import com.mediaforge.app.media.labelRes
import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import com.mediaforge.app.media.PlayMode
import com.mediaforge.app.media.VideoInfo
import com.mediaforge.app.media.barHeightPx
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(state: EditorState, info: VideoInfo?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSlider(stringResource(R.string.set_fps), stringResource(R.string.unit_fps, state.fps), state.fps.toFloat(), 5f..30f, 24) {
            state.fps = it.roundToInt()
        }
        LabeledSlider(stringResource(R.string.set_quality), "${state.quality}", state.quality.toFloat(), 10f..100f, 0) {
            state.quality = it.roundToInt()
        }
        LabeledSlider(stringResource(R.string.st_speed), "%.2fx".format(state.speed), state.speed, 0.25f..3f, 0) {
            state.speed = (it * 20).roundToInt() / 20f
        }
        Text(stringResource(R.string.st_playback), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlayMode.entries.forEach { m ->
                FilterChip(selected = state.playMode == m, onClick = { state.playMode = m }, label = { Text(stringResource(m.labelRes())) })
            }
        }
        Text(stringResource(R.string.set_max_width), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        MaxWidthChips(state.maxWidth) { state.maxWidth = it }
        SwitchRow(stringResource(R.string.set_loop), state.loop) { state.loop = it }
        SwitchRow(stringResource(R.string.set_fast), state.fast) { state.fast = it }
        val dims = info?.let {
            val p = state.crop.rect.toPx(it.width, it.height)
            var w = p[2]
            var h = p[3] + barHeightPx(p[3], state.topBar) + barHeightPx(p[3], state.bottomBar)
            if (state.maxWidth in 1 until w) { h = (h.toLong() * state.maxWidth / w).toInt().coerceAtLeast(1); w = state.maxWidth }
            "${w}x$h"
        }
        Text(
            "${state.encodedFrames} frames, output size ${dims ?: "same as the video"}.",
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(valueText)
    }
    Slider(value = value.coerceIn(range), onValueChange = onChange, valueRange = range, steps = steps)
}

/** Output width limit presets; 0 means keep the original size. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MaxWidthChips(value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0 to stringResource(R.string.st_original), 1080 to "1080 px", 720 to "720 px", 480 to "480 px", 360 to "360 px").forEach { (v, label) ->
            FilterChip(selected = value == v, onClick = { onChange(v) }, label = { Text(label) })
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
