package com.gifforge.app.ui

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
import com.gifforge.app.media.VideoInfo
import kotlin.math.roundToInt

@Composable
fun SettingsTab(state: EditorState, info: VideoInfo?) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LabeledSlider("Frame rate", "${state.fps} fps", state.fps.toFloat(), 5f..30f, 24) {
            state.fps = it.roundToInt()
        }
        LabeledSlider("Quality", "${state.quality}", state.quality.toFloat(), 10f..100f, 0) {
            state.quality = it.roundToInt()
        }
        LabeledSlider("Speed", "%.2fx".format(state.speed), state.speed, 0.25f..3f, 0) {
            state.speed = (it * 20).roundToInt() / 20f
        }
        SwitchRow("Loop forever", state.loop) { state.loop = it }
        SwitchRow("Fast encode (lower quality)", state.fast) { state.fast = it }
        Text(
            "${state.frameCount} frames will be encoded at the video's original size" +
                (info?.let {
                    val p = state.crop.rect.toPx(it.width, it.height)
                    " (${p[2]}x${p[3]})."
                } ?: "."),
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

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
