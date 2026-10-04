package com.mediaforge.app.ui

import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.LayerBlend
import kotlin.math.roundToInt

/** Opacity + blend mode picker shared by text layers and shapes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayerLookControls(
    opacity: Float,
    blend: LayerBlend,
    onOpacity: (Float) -> Unit,
    onBlend: (LayerBlend) -> Unit,
) {
    LabeledSlider(stringResource(R.string.ly_opacity), "${(opacity * 100).roundToInt()}%", opacity, 0.05f..1f, 0) {
        onOpacity((it * 100).roundToInt() / 100f)
    }
    Text(stringResource(R.string.ly_blend), style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LayerBlend.entries.filter { it.available }.forEach { b ->
            FilterChip(selected = blend == b, onClick = { onBlend(b) }, label = { Text(b.label) })
        }
    }
    if (blend != LayerBlend.NORMAL) {
        Text(
            stringResource(R.string.ly_blend_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
