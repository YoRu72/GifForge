package com.mediaforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.mediaforge.app.media.ShapeElement
import com.mediaforge.app.media.ShapeKind
import kotlin.math.roundToInt

private val EMOJI = listOf(
    "😀", "😂", "🤣", "😍", "😎", "🥳", "🤔", "😭", "😡", "😱", "🤯", "💀",
    "🔥", "💯", "❤️", "👍", "👏", "🙏", "👀", "🎉", "✨", "⭐", "👑", "🚀", "💩",
)

/** Shapes (drag / pinch / twist on the preview) and emoji. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementsTab(state: EditorState, player: Player) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add shape", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeKind.entries.forEach { k ->
                AssistChip(onClick = { state.addElement(k) }, label = { Text("+ ${k.label}") })
            }
        }
        Text("Add emoji", style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EMOJI.forEach { e -> AssistChip(onClick = { state.addEmoji(e) }, label = { Text(e) }) }
        }
        Text("Emoji are text layers: move and resize them in the Text tab.", style = MaterialTheme.typography.bodySmall)

        if (state.elements.isNotEmpty()) {
            Text("Your shapes", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.elements.forEachIndexed { i, el ->
                    FilterChip(
                        selected = el.id == state.selectedElId,
                        onClick = { state.selectedElId = el.id },
                        label = { Text("${el.kind.label} ${i + 1}") },
                    )
                }
            }
        }
        val sel = state.selectedElement()
        if (sel == null) {
            Text("Add a shape, then drag it on the preview. Pinch to resize, twist to rotate.")
            return@Column
        }
        fun edit(f: (ShapeElement) -> ShapeElement) = state.updateElement(sel.id, f)

        if (!sel.isLine) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filled")
                Switch(checked = sel.filled, onCheckedChange = { v -> edit { it.copy(filled = v) } })
            }
            if (sel.filled) {
                Text("Fill color", style = MaterialTheme.typography.labelLarge)
                Swatches(sel.fill or (0xFF shl 24)) { c -> edit { it.copy(fill = c) } }
                LabeledSlider("Fill opacity", "${(sel.fillAlpha * 100).roundToInt()}%", sel.fillAlpha, 0.05f..1f, 0) { v ->
                    edit { it.copy(fillAlpha = (v * 100).roundToInt() / 100f) }
                }
            }
        }
        Text(if (sel.isLine) "Color" else "Outline color", style = MaterialTheme.typography.labelLarge)
        Swatches(sel.stroke) { c -> edit { it.copy(stroke = c) } }
        LabeledSlider(
            if (sel.isLine) "Thickness" else "Outline thickness", "${"%.1f".format(sel.strokePct)}%",
            sel.strokePct, 0f..10f, 0,
        ) { v -> edit { it.copy(strokePct = (v * 10).roundToInt() / 10f) } }

        LabeledSlider("Width", "${(sel.w * 100).roundToInt()}%", sel.w, 0.02f..1.5f, 0) { v -> edit { it.copy(w = v) } }
        if (sel.kind != ShapeKind.LINE) {
            LabeledSlider(
                if (sel.kind == ShapeKind.ARROW) "Arrow head size" else "Height",
                "${(sel.h * 100).roundToInt()}%", sel.h, 0.02f..1.5f, 0,
            ) { v -> edit { it.copy(h = v) } }
        }
        LabeledSlider("Rotation", "${sel.rotation.roundToInt()}\u00B0", sel.rotation, -180f..180f, 0) { v ->
            edit { it.copy(rotation = v.roundToInt().toFloat()) }
        }
        LabeledSlider("Position X", "${(sel.cx * 100).roundToInt()}%", sel.cx, 0f..1f, 0) { v -> edit { it.copy(cx = v) } }
        LabeledSlider("Position Y", "${(sel.cy * 100).roundToInt()}%", sel.cy, 0f..1f, 0) { v -> edit { it.copy(cy = v) } }

        TimingControls(sel.timed, sel.fromMs, sel.toMs, state, player) { t, f, e -> edit { it.copy(timed = t, fromMs = f, toMs = e) } }

        LayerLookControls(sel.opacity, sel.blend, { v -> edit { it.copy(opacity = v) } }, { b -> edit { it.copy(blend = b) } })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.duplicateSelectedElement() }) { Text("Duplicate") }
            OutlinedButton(onClick = { state.removeSelectedElement() }) { Text("Delete") }
        }
    }
}
