package com.mediaforge.app.ui

import androidx.compose.ui.res.stringResource
import com.mediaforge.app.R
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mediaforge.app.media.GifMeta
import com.mediaforge.app.media.readGifFrame
import com.mediaforge.app.media.timeAtMs
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/** Trim a GIF by frames: range slider, +/-1 frame nudges and stills of the first and last kept frame. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifTrimPanel(meta: GifMeta, src: File, startF: Int, endF: Int, onChange: (Int, Int) -> Unit) {
    val last = (meta.frames - 1).coerceAtLeast(0)
    if (last == 0) {
        Text(stringResource(R.string.gt_single), Modifier.padding(16.dp))
        return
    }
    var startBmp by remember { mutableStateOf<Bitmap?>(null) }
    var endBmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(startF, src) { delay(150); startBmp = readGifFrame(src, meta, startF) }
    LaunchedEffect(endF, src) { delay(150); endBmp = readGifFrame(src, meta, endF) }

    val keptMs = meta.timeAtMs(endF + 1) - meta.timeAtMs(startF)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.gt_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.gt_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        LtrRangeSlider(
            value = startF.toFloat()..endF.toFloat(),
            onValueChange = { r ->
                val s = r.start.roundToInt().coerceIn(0, last)
                val e = r.endInclusive.roundToInt().coerceIn(s, last)
                onChange(s, e)
            },
            valueRange = 0f..last.toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.gt_start_line, startF + 1, fmtTime(meta.timeAtMs(startF))))
            Text(stringResource(R.string.gt_end_line, endF + 1, fmtTime(meta.timeAtMs(endF + 1))))
        }
        Text(stringResource(R.string.gt_keeping, endF - startF + 1, meta.frames, fmtTime(keptMs)))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gt_start), Modifier.width(44.dp))
            OutlinedButton(onClick = { onChange((startF - 1).coerceAtLeast(0), endF) }) { Text(stringResource(R.string.gt_minus1)) }
            OutlinedButton(onClick = { onChange((startF + 1).coerceAtMost(endF), endF) }) { Text(stringResource(R.string.gt_plus1)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gt_end), Modifier.width(44.dp))
            OutlinedButton(onClick = { onChange(startF, (endF - 1).coerceAtLeast(startF)) }) { Text(stringResource(R.string.gt_minus1)) }
            OutlinedButton(onClick = { onChange(startF, (endF + 1).coerceAtMost(last)) }) { Text(stringResource(R.string.gt_plus1)) }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Still(stringResource(R.string.gt_first), startBmp, Modifier.weight(1f))
            Still(stringResource(R.string.gt_last), endBmp, Modifier.weight(1f))
        }
        OutlinedButton(onClick = { onChange(0, last) }, enabled = startF > 0 || endF < last) { Text(stringResource(R.string.gt_reset)) }
    }
}

@Composable
private fun Still(label: String, bmp: Bitmap?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(
            Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), label, Modifier.fillMaxWidth().height(110.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}
