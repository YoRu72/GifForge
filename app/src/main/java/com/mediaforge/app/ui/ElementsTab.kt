package com.mediaforge.app.ui

import com.mediaforge.app.R
import androidx.compose.ui.res.stringResource
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
        Text(stringResource(R.string.el_add_shape), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShapeKind.entries.forEach { k ->
                AssistChip(onClick = { state.addElement(k) }, label = { Text("+ ${k.label}") })
            }
        }
        Text(stringResource(R.string.el_add_emoji), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EMOJI.forEach { e -> AssistChip(onClick = { state.addEmoji(e) }, label = { Text(e) }) }
        }
        Text(stringResource(R.string.el_emoji_note), style = MaterialTheme.typography.bodySmall)

        if (state.elements.isNotEmpty()) {
            Text(stringResource(R.string.el_yours), style = MaterialTheme.typography.labelLarge)
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
            Text(stringResource(R.string.el_empty))
            return@Column
        }
        fun edit(f: (ShapeElement) -> ShapeElement) = state.updateElement(sel.id, f)

        if (!sel.isLine) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.el_filled))
                Switch(checked = sel.filled, onCheckedChange = { v -> edit { it.copy(filled = v) } })
            }
            if (sel.filled) {
                Text(stringResource(R.string.el_fill_color), style = MaterialTheme.typography.labelLarge)
                Swatches(sel.fill or (0xFF shl 24)) { c -> edit { it.copy(fill = c) } }
                LabeledSlider(stringResource(R.string.el_fill_opacity), "${(sel.fillAlpha * 100).roundToInt()}%", sel.fillAlpha, 0.05f..1f, 0) { v ->
                    edit { it.copy(fillAlpha = (v * 100).roundToInt() / 100f) }
                }
            }
        }
        Text(if (sel.isLine) stringResource(R.string.el_color) else stringResource(R.string.el_outline_color), style = MaterialTheme.typography.labelLarge)
        Swatches(sel.stroke) { c -> edit { it.copy(stroke = c) } }
        LabeledSlider(
            if (sel.isLine) stringResource(R.string.el_thickness) else stringResource(R.string.el_outline_thick), "${"%.1f".format(sel.strokePct)}%",
            sel.strokePct, 0f..10f, 0,
        ) { v -> edit { it.copy(strokePct = (v * 10).roundToInt() / 10f) } }

        LabeledSlider(stringResource(R.string.el_width), "${(sel.w * 100).roundToInt()}%", sel.w, 0.02f..1.5f, 0) { v -> edit { it.copy(w = v) } }
        if (sel.kind != ShapeKind.LINE) {
            LabeledSlider(
                if (sel.kind == ShapeKind.ARROW) stringResource(R.string.el_arrow_head) else stringResource(R.string.el_height),
                "${(sel.h * 100).roundToInt()}%", sel.h, 0.02f..1.5f, 0,
            ) { v -> edit { it.copy(h = v) } }
        }
        LabeledSlider(stringResource(R.string.el_rotation), "${sel.rotation.roundToInt()}\u00B0", sel.rotation, -180f..180f, 0) { v ->
            edit { it.copy(rotation = v.roundToInt().toFloat()) }
        }
        LabeledSlider(stringResource(R.string.tt_posx), "${(sel.cx * 100).roundToInt()}%", sel.cx, 0f..1f, 0) { v -> edit { it.copy(cx = v) } }
        LabeledSlider(stringResource(R.string.tt_posy), "${(sel.cy * 100).roundToInt()}%", sel.cy, 0f..1f, 0) { v -> edit { it.copy(cy = v) } }

        TimingControls(sel.timed, sel.fromMs, sel.toMs, state, player) { t, f, e -> edit { it.copy(timed = t, fromMs = f, toMs = e) } }

        LayerLookControls(sel.opacity, sel.blend, { v -> edit { it.copy(opacity = v) } }, { b -> edit { it.copy(blend = b) } })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.duplicateSelectedElement() }) { Text(stringResource(R.string.el_duplicate)) }
            OutlinedButton(onClick = { state.removeSelectedElement() }) { Text(stringResource(R.string.el_delete)) }
        }
    }
}
