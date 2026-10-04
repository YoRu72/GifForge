package com.mediaforge.app.ui

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.media.FontCatalog
import com.mediaforge.app.media.FontFace
import com.mediaforge.app.media.FontLoad
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.AssLook
import com.mediaforge.app.subs.AssStyle
import com.mediaforge.app.subs.SubFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Look of the line, as ASS tags: font, size, bold, italic, alignment, free position and colours. The same controls the
 * GIF text tab has (alignment chips, 3x3 snap pad, X/Y sliders), so subtitles and GIF text are placed the same way.
 * Everything is physical: Left is left on the picture, also on Arabic screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LookPanel(file: SubFile, e: AssEvent, onChange: ((AssEvent) -> AssEvent) -> Unit) {
    val style = file.style(e.style) ?: AssStyle()
    val look = AssLook.resolve(style, e, e.startMs)
    var picking by remember { mutableStateOf(false) }

    fun tag(name: String, value: String?) = onChange { it.copy(text = AssLook.setTag(it.text, name, value)) }

    val an = look.alignment
    val col = (an - 1) % 3
    val row = (an - 1) / 3
    val resX = file.playResX.coerceAtLeast(1).toFloat()
    val resY = file.playResY.coerceAtLeast(1).toFloat()
    val fx = look.posX?.let { it / resX } ?: when (col) {
        0 -> look.marginL / resX
        2 -> 1f - look.marginR / resX
        else -> 0.5f
    }
    val fy = look.posY?.let { it / resY } ?: when (row) {
        2 -> look.marginV / resY
        1 -> 0.5f
        else -> 1f - look.marginV / resY
    }
    fun setPos(x: Float, y: Float) =
        tag("pos", "(${(x.coerceIn(0f, 1f) * resX).roundToInt()},${(y.coerceIn(0f, 1f) * resY).roundToInt()})")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tt_font), style = MaterialTheme.typography.labelLarge)
        Text(look.fontName, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { picking = true }) { Text(stringResource(R.string.sub_pick_font)) }
            OutlinedButton(onClick = { tag("fn", null) }) { Text(stringResource(R.string.tt_default)) }
        }

        LabeledSlider(stringResource(R.string.tt_size), look.size.roundToInt().toString(), look.size, 8f..200f, 0) { v ->
            tag("fs", v.roundToInt().toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = look.bold, onClick = { tag("b", if (look.bold) "0" else "1") }, label = { Text(stringResource(R.string.tt_bold)) })
            FilterChip(selected = look.italic, onClick = { tag("i", if (look.italic) "0" else "1") }, label = { Text(stringResource(R.string.sub_italic)) })
        }

        Text(stringResource(R.string.tt_align), style = MaterialTheme.typography.labelLarge)
        ForceLtr {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to stringResource(R.string.tt_left),
                    1 to stringResource(R.string.tt_center),
                    2 to stringResource(R.string.tt_right),
                ).forEach { (c, label) ->
                    FilterChip(selected = col == c, onClick = { tag("an", (row * 3 + c + 1).toString()) }, label = { Text(label) })
                }
            }
        }

        Text(stringResource(R.string.tt_snap), style = MaterialTheme.typography.labelLarge)
        AnPad(an) { v -> onChange { ev -> ev.copy(text = AssLook.setTag(AssLook.setTag(ev.text, "an", v.toString()), "pos", null)) } }

        ForceLtr { Column { LabeledSlider(stringResource(R.string.tt_posx), "${(fx * 100).roundToInt()}%", fx, 0f..1f, 0) { v -> setPos(v, fy) } } }
        LabeledSlider(stringResource(R.string.tt_posy), "${(fy * 100).roundToInt()}%", fy, 0f..1f, 0) { v -> setPos(fx, v) }
        if (look.posX != null) OutlinedButton(onClick = { tag("pos", null) }) { Text(stringResource(R.string.sub_pos_auto)) }

        Text(stringResource(R.string.tt_color), style = MaterialTheme.typography.labelLarge)
        Swatches(look.primary) { c -> tag("c", AssLook.colorTag(c)) }
        Text(stringResource(R.string.tt_stroke_color), style = MaterialTheme.typography.labelLarge)
        Swatches(look.outline) { c -> tag("3c", AssLook.colorTag(c)) }

        OutlinedButton(onClick = { onChange { it.copy(text = AssLook.clearLook(it.text)) } }) { Text(stringResource(R.string.sub_look_reset)) }
    }

    if (picking) FontPickerDialog(onPick = { name -> tag("fn", name); picking = false }, onClose = { picking = false })
}

/** 3x3 pad in numpad order (7 8 9 / 4 5 6 / 1 2 3): where the text sits in the frame. Physical, never mirrored. */
@Composable
private fun AnPad(selected: Int, onPick: (Int) -> Unit) {
    val glyphs = listOf("\u2196", "\u2191", "\u2197", "\u2190", "\u25CF", "\u2192", "\u2199", "\u2193", "\u2198")
    ForceLtr {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (c in 0..2) {
                        val an = (2 - r) * 3 + c + 1
                        Box(
                            Modifier
                                .size(64.dp, 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (an == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onPick(an) },
                            contentAlignment = Alignment.Center,
                        ) { Text(glyphs[r * 3 + c]) }
                    }
                }
            }
        }
    }
}

/** Fonts of the app library by family name, each shown in its own face, plus import of a new font file. */
@Composable
private fun FontPickerDialog(onPick: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var faces by remember { mutableStateOf<List<FontFace>?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        faces = withContext(Dispatchers.IO) { FontCatalog.scan(FontStore.list(ctx)) }
    }
    LaunchedEffect(Unit) { load() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { FontStore.importUri(ctx, uri) }
            if (r.imported.isEmpty()) {
                msg = if (r.unsupported.isNotEmpty()) ctx.getString(R.string.font_unsupported, r.unsupported.joinToString(", "))
                else ctx.getString(R.string.font_invalid)
            } else {
                msg = null
                load()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.tt_font)) },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.tt_add_font)) } },
        text = {
            Column {
                msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                val list = faces
                when {
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text(stringResource(R.string.sub_no_fonts))
                    else -> {
                        val families = list.distinctBy { it.family }.sortedBy { it.family.lowercase() }
                        LazyColumn(Modifier.heightIn(max = 380.dp)) {
                            items(families, key = { it.ref }) { f -> FontRow(f) { onPick(f.family) } }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FontRow(face: FontFace, onClick: () -> Unit) {
    val tf by produceState<Typeface?>(null, face.ref) {
        value = (FontStore.load(face.ref) as? FontLoad.Ready)?.typeface
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Text(face.family, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.sub_font_sample),
            fontFamily = tf?.let { FontFamily(it) },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
