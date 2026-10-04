package com.mediaforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Slider
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.Curve
import com.mediaforge.app.subs.EffectProp
import com.mediaforge.app.subs.Effects
import com.mediaforge.app.subs.Keyframe
import com.mediaforge.app.subs.Track
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

private fun propLabel(p: EffectProp) = when (p) {
    EffectProp.OPACITY -> R.string.fx_p_opacity
    EffectProp.BLUR -> R.string.fx_p_blur
    EffectProp.BORDER -> R.string.fx_p_bord
    EffectProp.SHADOW -> R.string.fx_p_shad
    EffectProp.SCALE -> R.string.fx_p_scale
    EffectProp.ROTATION -> R.string.fx_p_rot
    EffectProp.SPACING -> R.string.fx_p_spacing
}

private fun curveLabel(c: Curve) = when (c) {
    Curve.LINEAR -> R.string.fx_c_linear
    Curve.EASE_IN -> R.string.fx_c_in
    Curve.EASE_OUT -> R.string.fx_c_out
    Curve.HOLD -> R.string.fx_c_hold
}

/** Number of keyframe effects stored in a line (for the section badge). */
fun effectCount(e: AssEvent): Int = Effects.parse(e.text).size

/**
 * Effects under the line (Alight Motion style): every effect is a track with keyframes on a value-over-time
 * graph. Opacity makes the text fade; blur, border, shadow, scale, rotation and spacing work the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsPanel(event: AssEvent, onChange: ((AssEvent) -> AssEvent) -> Unit) {
    val tracks = Effects.parse(event.text)
    val dur = event.durationMs.coerceAtLeast(1L)
    var picked by remember { mutableStateOf<EffectProp?>(null) }
    var selKey by remember { mutableStateOf(0) }
    var graphView by remember { mutableStateOf(false) } // false = amount controls, true = keyframe graph

    fun commit(list: List<Track>) = onChange { it.copy(text = Effects.apply(it.text, list)) }
    fun replace(t: Track) = commit(tracks.map { if (it.prop == t.prop) t else it })

    // keep a valid effect picked: the first one, or none when the line has no effects
    val cur = tracks.firstOrNull { it.prop == picked } ?: tracks.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tracks.forEach { t ->
                FilterChip(
                    selected = cur?.prop == t.prop, onClick = { picked = t.prop; selKey = 0 },
                    label = { Text(stringResource(propLabel(t.prop))) },
                )
            }
        }
        Text(stringResource(R.string.fx_add), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectProp.entries.filter { p -> tracks.none { it.prop == p } }.forEach { p ->
                FilterChip(
                    selected = false,
                    onClick = { commit(tracks + Effects.neutral(p, dur)); picked = p; selKey = 0 },
                    label = { Text("+ " + stringResource(propLabel(p))) },
                )
            }
        }
        if (cur != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(propLabel(cur.prop)), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        IconButton(onClick = { commit(tracks.filter { it.prop != cur.prop }); picked = null }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.fx_remove))
                        }
                    }
                    // two small icons switch between the two menus of an effect: amount and keyframe graph
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(!graphView, { graphView = false }, { Icon(Icons.Filled.Tune, stringResource(R.string.fx_view_amount)) })
                        FilterChip(graphView, { graphView = true }, { Icon(Icons.Filled.ShowChart, stringResource(R.string.fx_view_graph)) })
                    }
                    if (cur.prop == EffectProp.OPACITY) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(false, { replace(Effects.fadeIn(dur)); selKey = 0 }, { Text(stringResource(R.string.fx_fade_in)) })
                            FilterChip(false, { replace(Effects.fadeOut(dur)); selKey = 0 }, { Text(stringResource(R.string.fx_fade_out)) })
                            FilterChip(false, { replace(Effects.fadeBoth(dur)); selKey = 0 }, { Text(stringResource(R.string.fx_fade_both)) })
                        }
                    }
                    if (!graphView) {
                        // amount: a flat effect has one strength for the whole line; an animated one edits the selected keyframe
                        val flat = cur.keys.all { it.value == cur.keys.first().value }
                        val idx = selKey.coerceIn(0, cur.keys.size - 1)
                        val shown = if (flat) cur.keys.first().value else cur.keys[idx].value
                        Text(
                            stringResource(if (flat) R.string.fx_amount_all else R.string.fx_amount_key, fmt(shown)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ForceLtr {
                            Slider(
                                value = shown.coerceIn(cur.prop.min, cur.prop.max),
                                onValueChange = { v ->
                                    val r = (v * 10f).roundToInt() / 10f
                                    replace(cur.copy(keys = cur.keys.mapIndexed { i, x -> if (flat || i == idx) x.copy(value = r) else x }))
                                },
                                valueRange = cur.prop.min..cur.prop.max,
                            )
                        }
                        if (!flat) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                cur.keys.forEachIndexed { i, kk ->
                                    FilterChip(idx == i, { selKey = i }, { Text("${i + 1}: ${fmt(kk.value)}") })
                                }
                            }
                        }
                        Text(stringResource(R.string.fx_amount_hint), style = MaterialTheme.typography.bodySmall)
                    } else {
                    KeyframeGraph(cur, dur, selKey, { selKey = it }, ::replace)
                    Text(stringResource(R.string.fx_graph_hint), style = MaterialTheme.typography.bodySmall)
                    val k = cur.keys.getOrNull(selKey)
                    if (k != null) {
                        val lo = cur.keys.getOrNull(selKey - 1)?.timeMs ?: 0L
                        val hi = cur.keys.getOrNull(selKey + 1)?.timeMs ?: dur
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DecimalField(stringResource(R.string.fx_key_time), k.timeMs.toFloat(), Modifier.weight(1f)) { v ->
                                replace(cur.copy(keys = cur.keys.mapIndexed { i, x -> if (i == selKey) x.copy(timeMs = v.toLong().coerceIn(lo, hi)) else x }))
                            }
                            DecimalField(stringResource(R.string.fx_key_value), k.value, Modifier.weight(1f)) { v ->
                                replace(cur.copy(keys = cur.keys.mapIndexed { i, x -> if (i == selKey) x.copy(value = v.coerceIn(cur.prop.min, cur.prop.max)) else x }))
                            }
                            IconButton(
                                enabled = cur.keys.size > 1,
                                onClick = { replace(cur.copy(keys = cur.keys.filterIndexed { i, _ -> i != selKey })); selKey = 0 },
                            ) { Icon(Icons.Filled.Delete, stringResource(R.string.fx_key_delete)) }
                        }
                        Text(stringResource(R.string.fx_curve), style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Curve.entries.forEach { c ->
                                FilterChip(
                                    selected = k.curve == c,
                                    onClick = { replace(cur.copy(keys = cur.keys.mapIndexed { i, x -> if (i == selKey) x.copy(curve = c) else x })) },
                                    label = { Text(stringResource(curveLabel(c))) },
                                )
                            }
                        }
                    }
                    }
                }
            }
            Text(stringResource(R.string.fx_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Value-over-time graph. Time runs left to right (also in Arabic screens). Tap empty space to add a keyframe,
 * drag a point to move it; a point stays between its neighbours so keyframes never cross.
 */
@Composable
private fun KeyframeGraph(track: Track, durMs: Long, selected: Int, onSelect: (Int) -> Unit, onTrack: (Track) -> Unit) {
    val tr by rememberUpdatedState(track)
    val sel by rememberUpdatedState(selected)
    val colLine = MaterialTheme.colorScheme.primary
    val colGrid = MaterialTheme.colorScheme.outlineVariant
    val colDot = MaterialTheme.colorScheme.onSurface
    val pad = 24f
    ForceLtr {
        Canvas(
            Modifier.fillMaxWidth().height(170.dp)
                .pointerInput(track.prop, durMs) {
                    fun toT(x: Float) = (((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f) * durMs).toLong()
                    fun toV(y: Float): Float {
                        val u = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                        return tr.prop.min + u * (tr.prop.max - tr.prop.min)
                    }
                    fun px(k: Keyframe) = Offset(
                        pad + k.timeMs.toFloat() / durMs * (size.width - 2 * pad),
                        pad + (1f - (k.value - tr.prop.min) / (tr.prop.max - tr.prop.min)) * (size.height - 2 * pad),
                    )
                    fun nearest(o: Offset, r: Float): Int {
                        var best = -1
                        var bd = r
                        tr.keys.forEachIndexed { i, k -> val d = hypot(px(k).x - o.x, px(k).y - o.y); if (d <= bd) { bd = d; best = i } }
                        return best
                    }
                    detectTapGestures { o ->
                        val i = nearest(o, 44f)
                        if (i >= 0) onSelect(i) else {
                            val nk = Keyframe(toT(o.x), toV(o.y))
                            val list = (tr.keys + nk).sortedBy { it.timeMs }
                            onTrack(tr.copy(keys = list))
                            onSelect(list.indexOf(nk))
                        }
                    }
                }
                .pointerInput(track.prop, durMs) {
                    var drag = -1
                    fun toT(x: Float) = (((x - pad) / (size.width - 2 * pad)).coerceIn(0f, 1f) * durMs).toLong()
                    fun toV(y: Float): Float {
                        val u = 1f - ((y - pad) / (size.height - 2 * pad)).coerceIn(0f, 1f)
                        return tr.prop.min + u * (tr.prop.max - tr.prop.min)
                    }
                    detectDragGestures(
                        onDragStart = { o ->
                            var best = -1
                            var bd = 56f
                            tr.keys.forEachIndexed { i, k ->
                                val x = pad + k.timeMs.toFloat() / durMs * (size.width - 2 * pad)
                                val y = pad + (1f - (k.value - tr.prop.min) / (tr.prop.max - tr.prop.min)) * (size.height - 2 * pad)
                                val d = hypot(x - o.x, y - o.y)
                                if (d <= bd) { bd = d; best = i }
                            }
                            drag = best
                            if (best >= 0) onSelect(best)
                        },
                        onDragEnd = { drag = -1 },
                        onDragCancel = { drag = -1 },
                    ) { change, _ ->
                        val i = drag
                        if (i >= 0) {
                            change.consume()
                            val keys = tr.keys
                            val lo = keys.getOrNull(i - 1)?.timeMs ?: 0L
                            val hi = keys.getOrNull(i + 1)?.timeMs ?: durMs
                            val v = (toV(change.position.y) * 10f).roundToInt() / 10f
                            onTrack(tr.copy(keys = keys.mapIndexed { n, k -> if (n == i) k.copy(timeMs = toT(change.position.x).coerceIn(lo, hi), value = v) else k }))
                        }
                    }
                },
        ) {
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            for (n in 0..4) {
                val y = pad + h * n / 4f
                drawLine(colGrid, Offset(pad, y), Offset(pad + w, y), 1f)
            }
            for (n in 0..4) {
                val x = pad + w * n / 4f
                drawLine(colGrid, Offset(x, pad), Offset(x, pad + h), 1f)
            }
            val span = track.prop.max - track.prop.min
            fun yOf(v: Float) = pad + (1f - (v - track.prop.min) / span) * h
            val path = Path()
            val steps = 120
            for (n in 0..steps) {
                val t = (durMs * n / steps.toFloat()).toLong()
                val p = Offset(pad + w * n / steps, yOf(track.valueAt(t)))
                if (n == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, colLine, style = Stroke(width = 4f))
            track.keys.forEachIndexed { i, k ->
                val c = Offset(pad + k.timeMs.toFloat() / durMs * w, yOf(k.value))
                drawCircle(if (i == selected) colLine else colDot, if (i == selected) 14f else 9f, c)
                if (i == selected) drawCircle(colDot, 5f, c)
            }
        }
    }
}

@Composable
private fun DecimalField(label: String, value: Float, modifier: Modifier, onValue: (Float) -> Unit) {
    var t by remember { mutableStateOf(fmt(value)) }
    LaunchedEffect(value) { if (t.toFloatOrNull() != value) t = fmt(value) }
    ForceLtr {
        OutlinedTextField(
            value = t, onValueChange = { s ->
                t = s
                s.replace(',', '.').toFloatOrNull()?.let(onValue)
            },
            label = { Text(label) }, singleLine = true, modifier = modifier,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    }
}

private fun fmt(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(Locale.US, v)
