package com.mediaforge.app.ui

import com.mediaforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Aspect presets + readout, shared by the video editor and the GIF cropper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropControls(crop: CropState, cw: Int, ch: Int) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.crop_hint))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CROP_PRESETS.forEach { name ->
                FilterChip(
                    selected = crop.preset == name,
                    onClick = { crop.applyPreset(name, cw, ch) },
                    label = { Text(name) },
                )
            }
        }
        val p = crop.rect.toPx(cw, ch)
        Text(stringResource(R.string.crop_size, p[2], p[3]))
        OutlinedButton(onClick = { crop.reset() }) { Text(stringResource(R.string.crop_reset)) }
    }
}
