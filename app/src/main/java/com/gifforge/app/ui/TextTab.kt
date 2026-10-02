package com.gifforge.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gifforge.app.media.ALIGN_CENTER
import com.gifforge.app.media.ALIGN_LEFT
import com.gifforge.app.media.ALIGN_RIGHT
import com.gifforge.app.media.FontStore
import kotlin.math.roundToInt

private val palette = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFFFF9800, 0xFFF44336,
    0xFFE91E63, 0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4, 0xFF4CAF50,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTab(state: EditorState) {
    val ctx = LocalContext.current
    var fonts by remember { mutableStateOf(FontStore.list(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val f = FontStore.import(ctx, uri)
            if (f == null) {
                msg = "That file isn't a usable .ttf / .otf font."
            } else {
                msg = null
                fonts = FontStore.list(ctx)
                state.selected()?.let { s -> state.update(s.id) { it.copy(fontPath = f.absolutePath) } }
            }
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.overlays.forEach { o ->
                FilterChip(
                    selected = o.id == state.selectedId,
                    onClick = { state.selectedId = o.id },
                    label = { Text(o.text.take(12).ifBlank { "(empty)" }) },
                )
            }
            AssistChip(onClick = { state.addOverlay() }, label = { Text("+ Add text") })
        }

        val sel = state.selected()
        if (sel == null) {
            Text("Add a text layer, then drag it on the preview to place it.")
            return@Column
        }
        fun edit(f: (com.gifforge.app.media.TextOverlay) -> com.gifforge.app.media.TextOverlay) =
            state.update(sel.id, f)

        OutlinedTextField(
            value = sel.text,
            onValueChange = { v -> edit { it.copy(text = v) } },
            label = { Text("Text") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Font", style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = sel.fontPath == null,
                onClick = { edit { it.copy(fontPath = null) } },
                label = { Text("Default") },
            )
            fonts.forEach { f ->
                FilterChip(
                    selected = sel.fontPath == f.absolutePath,
                    onClick = { edit { it.copy(fontPath = f.absolutePath) } },
                    label = { Text(f.nameWithoutExtension.take(18)) },
                )
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Add font from device") }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (sel.fontPath == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sel.bold, onClick = { edit { it.copy(bold = !it.bold) } }, label = { Text("Bold") })
            }
        }

        Text("Alignment", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ALIGN_LEFT to "Left", ALIGN_CENTER to "Center", ALIGN_RIGHT to "Right").forEach { (a, label) ->
                FilterChip(selected = sel.align == a, onClick = { edit { it.copy(align = a) } }, label = { Text(label) })
            }
        }

        LabeledSlider("Size", "${"%.1f".format(sel.sizePct)}%", sel.sizePct, 2f..30f, 0) { v ->
            edit { it.copy(sizePct = (v * 2).roundToInt() / 2f) }
        }
        LabeledSlider("Stroke", "${(sel.strokeRatio * 100).roundToInt()}%", sel.strokeRatio, 0f..0.3f, 0) { v ->
            edit { it.copy(strokeRatio = (v * 100).roundToInt() / 100f) }
        }
        LabeledSlider("Position X", "${(sel.posX * 100).roundToInt()}%", sel.posX, 0f..1f, 0) { v ->
            edit { it.copy(posX = v) }
        }
        LabeledSlider("Position Y", "${(sel.posY * 100).roundToInt()}%", sel.posY, 0f..1f, 0) { v ->
            edit { it.copy(posY = v) }
        }

        Text("Text color", style = MaterialTheme.typography.labelLarge)
        Swatches(sel.color) { c -> edit { it.copy(color = c) } }
        Text("Stroke color", style = MaterialTheme.typography.labelLarge)
        Swatches(sel.strokeColor) { c -> edit { it.copy(strokeColor = c) } }

        OutlinedButton(onClick = { state.removeSelected() }) { Text("Delete this text") }
    }
}

@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        palette.forEach { c ->
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        if (c == selected) 3.dp else 1.dp,
                        if (c == selected) MaterialTheme.colorScheme.primary else Color.Gray,
                        CircleShape,
                    )
                    .clickable { onPick(c) },
            )
        }
    }
}
