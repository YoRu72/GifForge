package com.mediaforge.app.ui

import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.ALIGN_CENTER
import com.mediaforge.app.media.ALIGN_LEFT
import com.mediaforge.app.media.ALIGN_RIGHT
import com.mediaforge.app.media.CaptionBar
import kotlin.math.roundToInt

/** Meme caption bars: a strip above and/or below the video. [onPickFont] gets 1 (top) or 2 (bottom). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarsTab(state: EditorState, onPickFont: (Int) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.bar_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        BarEditor(stringResource(R.string.bar_top), state.topBar, { state.topBar = it }) { onPickFont(1) }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        BarEditor(stringResource(R.string.bar_bottom), state.bottomBar, { state.bottomBar = it }) { onPickFont(2) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarEditor(title: String, bar: CaptionBar?, onChange: (CaptionBar?) -> Unit, onFont: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(checked = bar != null, onCheckedChange = { on -> onChange(if (on) CaptionBar() else null) })
    }
    val b = bar ?: return
    OutlinedTextField(
        value = b.text,
        onValueChange = { onChange(b.copy(text = it)) },
        label = { Text(stringResource(R.string.bar_caption)) },
        modifier = Modifier.fillMaxWidth(),
    )
    LabeledSlider(stringResource(R.string.bar_height), stringResource(R.string.bar_height_val, b.heightPct.roundToInt()), b.heightPct, 8f..50f, 0) {
        onChange(b.copy(heightPct = it.roundToInt().toFloat()))
    }
    Text(stringResource(R.string.bar_align), style = MaterialTheme.typography.labelLarge)
    ForceLtr { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(ALIGN_LEFT to stringResource(R.string.al_left), ALIGN_CENTER to stringResource(R.string.al_center), ALIGN_RIGHT to stringResource(R.string.al_right)).forEach { (a, label) ->
            FilterChip(selected = b.align == a, onClick = { onChange(b.copy(align = a)) }, label = { Text(label) })
        }
        if (b.fontPath == null) {
            FilterChip(selected = b.bold, onClick = { onChange(b.copy(bold = !b.bold)) }, label = { Text(stringResource(R.string.bold)) })
        }
    } }
    Text(
        stringResource(R.string.bar_font, b.fontPath?.substringAfterLast('/')?.substringBeforeLast('.') ?: stringResource(R.string.fonts_default)),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onFont) { Text(stringResource(R.string.bar_browse)) }
        OutlinedButton(onClick = { onChange(b.copy(fontPath = null, fontVars = null)) }) { Text(stringResource(R.string.default_)) }
    }
    Text(stringResource(R.string.bar_color), style = MaterialTheme.typography.labelLarge)
    Swatches(b.bg) { onChange(b.copy(bg = it)) }
    Text(stringResource(R.string.text_color), style = MaterialTheme.typography.labelLarge)
    Swatches(b.color) { onChange(b.copy(color = it)) }
}
