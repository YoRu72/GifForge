package com.mediaforge.app.ui

import com.mediaforge.app.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import com.mediaforge.app.media.FontCatalog
import com.mediaforge.app.media.FontFace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.mediaforge.app.media.ALIGN_CENTER
import com.mediaforge.app.media.ALIGN_LEFT
import com.mediaforge.app.media.ALIGN_RIGHT
import com.mediaforge.app.media.FontStore
import kotlin.math.abs
import kotlin.math.roundToInt

internal val palette = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFFEB3B, 0xFFFF9800, 0xFFF44336,
    0xFFE91E63, 0xFF9C27B0, 0xFF2196F3, 0xFF00BCD4, 0xFF4CAF50,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextTab(state: EditorState, player: Player, onBrowseFonts: () -> Unit) {
    val ctx = LocalContext.current
    var fonts by remember { mutableStateOf(FontStore.list(ctx)) }
    var msg by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val r = withContext(Dispatchers.IO) { FontStore.importUri(ctx, uri) }
            val f = r.imported.firstOrNull()
            if (f == null) {
                msg = if (r.unsupported.isNotEmpty()) ctx.getString(R.string.font_unsupported, r.unsupported.joinToString(", "))
                else ctx.getString(R.string.font_invalid)
            } else {
                msg = null
                fonts = FontStore.list(ctx)
                state.selected()?.let { s -> state.update(s.id) { it.copy(fontPath = f.absolutePath, fontVars = null) } }
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
            Text(stringResource(R.string.tt_empty))
            return@Column
        }
        fun edit(f: (com.mediaforge.app.media.TextOverlay) -> com.mediaforge.app.media.TextOverlay) =
            state.update(sel.id, f)

        OutlinedTextField(
            value = sel.text,
            onValueChange = { v -> edit { it.copy(text = v) } },
            label = { Text(stringResource(R.string.tt_text)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.tt_font), style = MaterialTheme.typography.labelLarge)
        val face by produceState<FontFace?>(null, sel.fontPath) {
            value = sel.fontPath?.let { p -> withContext(Dispatchers.IO) { FontCatalog.face(p) } }
        }
        Text(
            face?.let { "${it.family} - ${it.style}" }
                ?: sel.fontPath?.substringAfterLast('/')?.substringBeforeLast('.') ?: stringResource(R.string.tt_default_font),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onBrowseFonts) { Text(stringResource(R.string.tt_browse)) }
            OutlinedButton(onClick = { edit { it.copy(fontPath = null) } }) { Text(stringResource(R.string.tt_default)) }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.tt_add_font)) }
        face?.let { f ->
            if (f.isVariable && Build.VERSION.SDK_INT >= 26) {
                VariationControls(f, sel.fontVars) { v -> edit { it.copy(fontVars = v) } }
            }
        }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (sel.fontPath == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sel.bold, onClick = { edit { it.copy(bold = !it.bold) } }, label = { Text(stringResource(R.string.tt_bold)) })
            }
        }

        Text(stringResource(R.string.tt_align), style = MaterialTheme.typography.labelLarge)
        ForceLtr { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ALIGN_LEFT to stringResource(R.string.tt_left), ALIGN_CENTER to stringResource(R.string.tt_center), ALIGN_RIGHT to stringResource(R.string.tt_right)).forEach { (a, label) ->
                FilterChip(selected = sel.align == a, onClick = { edit { it.copy(align = a) } }, label = { Text(label) })
            }
        } }

        Text(stringResource(R.string.tt_snap), style = MaterialTheme.typography.labelLarge)
        PositionPad(sel.posX, sel.posY) { x, y ->
            edit {
                it.copy(
                    posX = x, posY = y,
                    align = when { x < 0.25f -> ALIGN_LEFT; x > 0.75f -> ALIGN_RIGHT; else -> ALIGN_CENTER },
                )
            }
        }

        LabeledSlider(stringResource(R.string.tt_size), "${"%.1f".format(sel.sizePct)}%", sel.sizePct, 2f..30f, 0) { v ->
            edit { it.copy(sizePct = (v * 2).roundToInt() / 2f) }
        }
        LabeledSlider(stringResource(R.string.tt_stroke), "${(sel.strokeRatio * 100).roundToInt()}%", sel.strokeRatio, 0f..0.3f, 0) { v ->
            edit { it.copy(strokeRatio = (v * 100).roundToInt() / 100f) }
        }
        LabeledSlider(stringResource(R.string.tt_posx), "${(sel.posX * 100).roundToInt()}%", sel.posX, 0f..1f, 0) { v ->
            edit { it.copy(posX = v) }
        }
        LabeledSlider(stringResource(R.string.tt_posy), "${(sel.posY * 100).roundToInt()}%", sel.posY, 0f..1f, 0) { v ->
            edit { it.copy(posY = v) }
        }

        Text(stringResource(R.string.tt_color), style = MaterialTheme.typography.labelLarge)
        Swatches(sel.color) { c -> edit { it.copy(color = c) } }
        Text(stringResource(R.string.tt_stroke_color), style = MaterialTheme.typography.labelLarge)
        Swatches(sel.strokeColor) { c -> edit { it.copy(strokeColor = c) } }

        TimingControls(sel.timed, sel.fromMs, sel.toMs, state, player) { t, f, e -> edit { it.copy(timed = t, fromMs = f, toMs = e) } }

        LayerLookControls(sel.opacity, sel.blend, { v -> edit { it.copy(opacity = v) } }, { b -> edit { it.copy(blend = b) } })

        OutlinedButton(onClick = { state.removeSelected() }) { Text(stringResource(R.string.tt_delete)) }
    }
}

@Composable
internal fun Swatches(selected: Int, onPick: (Int) -> Unit) {
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

/** 3x3 pad: tap a cell to snap the selected text to that spot (edges keep a small margin). */
@Composable
private fun PositionPad(posX: Float, posY: Float, onPick: (Float, Float) -> Unit) {
    val glyphs = listOf("\u2196", "\u2191", "\u2197", "\u2190", "\u25CF", "\u2192", "\u2199", "\u2193", "\u2198")
    ForceLtr { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (r in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0..2) {
                    val x = c / 2f
                    val y = r / 2f
                    val on = abs(posX - x) < 0.02f && abs(posY - y) < 0.02f
                    Box(
                        Modifier
                            .size(64.dp, 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { onPick(x, y) },
                        contentAlignment = Alignment.Center,
                    ) { Text(glyphs[r * 3 + c]) }
                }
            }
        }
    } }
}

@Composable
private fun VariationControls(face: FontFace, vars: String?, onChange: (String?) -> Unit) {
    val current = remember(vars) { parseVars(vars) }
    Text(stringResource(R.string.tt_axes), style = MaterialTheme.typography.labelLarge)
    face.axes.forEach { ax ->
        val v = (current[ax.tag] ?: ax.def).coerceIn(ax.min, ax.max)
        val shown = if (ax.max - ax.min >= 20f) "%.0f".format(Locale.US, v) else "%.2f".format(Locale.US, v)
        LabeledSlider(ax.label, shown, v, ax.min..ax.max, 0) { nv ->
            onChange(buildVars(current + (ax.tag to nv)))
        }
    }
    OutlinedButton(onClick = { onChange(null) }) { Text(stringResource(R.string.tt_axes_reset)) }
}

private fun parseVars(s: String?): Map<String, Float> {
    if (s.isNullOrBlank()) return emptyMap()
    val out = HashMap<String, Float>()
    Regex("'(\\w{4})'\\s+(-?[0-9.]+)").findAll(s).forEach { m ->
        m.groupValues[2].toFloatOrNull()?.let { out[m.groupValues[1]] = it }
    }
    return out
}

private fun buildVars(m: Map<String, Float>): String =
    m.entries.joinToString(", ") { "'${it.key}' ${"%.2f".format(Locale.US, it.value)}" }
