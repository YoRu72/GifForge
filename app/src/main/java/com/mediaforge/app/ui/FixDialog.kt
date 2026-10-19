package com.mediaforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.FixOps
import com.mediaforge.app.subs.FixRule
import com.mediaforge.app.subs.ReadOps
import com.mediaforge.app.subs.formatAssTime

private fun ruleLabel(r: FixRule): Int = when (r) {
    FixRule.EMPTY -> R.string.fx_empty
    FixRule.SPACES -> R.string.fx_spaces
    FixRule.AR_PUNCT -> R.string.fx_ar_punct
    FixRule.AR_DIGITS -> R.string.fx_ar_digits
    FixRule.LONE_I -> R.string.fx_lone_i
    FixRule.OVERLAP -> R.string.fx_overlap
    FixRule.SHORT_GAP -> R.string.fx_short_gap
    FixRule.SHORT -> R.string.fx_short
    FixRule.SPEED -> R.string.fx_speed
    FixRule.LONG -> R.string.fx_long
}

/**
 * SE2: full-screen preview: rules on top (each with how many lines it changes), the options, then every single fix with
 * its own tick box. Apply writes exactly what the list shows.
 */
@Composable
fun FixDialog(events: List<AssEvent>, onDismiss: () -> Unit, onApply: (List<AssEvent>, Int) -> Unit) {
    // long lines are often intended (songs, signs): that rule starts unticked, like Subtitle Edit
    var rules by remember { mutableStateOf(FixRule.entries.toSet() - FixRule.LONG) }
    var skip by remember { mutableStateOf(setOf<String>()) }
    var minDur by rememberSaveable { mutableStateOf("500") }
    var maxDur by rememberSaveable { mutableStateOf("7000") }
    var minGap by rememberSaveable { mutableStateOf("24") }
    val opt = FixOps.Options(
        minMs = minDur.toLongOrNull()?.coerceAtLeast(1L) ?: 500L,
        maxMs = maxDur.toLongOrNull()?.coerceAtLeast(1L) ?: 7000L,
        gapMs = minGap.toLongOrNull()?.coerceAtLeast(0L) ?: 24L,
        maxCps = ReadOps.max(),
    )
    val result = remember(events, rules, opt, skip) { FixOps.run(events, rules, opt, skip) }
    val counts = remember(result) { result.fixes.groupingBy { it.rule }.eachCount() }
    val applied = result.fixes.count { it.key !in skip }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(stringResource(R.string.fx_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 6.dp))
                Column(Modifier.weight(0.45f).verticalScroll(rememberScrollState())) {
                    FixRule.entries.forEach { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable { rules = if (r in rules) rules - r else rules + r },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(r in rules, { on -> rules = if (on) rules + r else rules - r })
                            Text(stringResource(ruleLabel(r)), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text((counts[r] ?: 0).toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        MsField(stringResource(R.string.fx_min_dur), minDur, Modifier.weight(1f)) { minDur = it }
                        MsField(stringResource(R.string.fx_max_dur), maxDur, Modifier.weight(1f)) { maxDur = it }
                        MsField(stringResource(R.string.fx_min_gap), minGap, Modifier.weight(1f)) { minGap = it }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (result.fixes.isEmpty()) {
                    Text(stringResource(R.string.fx_none), Modifier.weight(0.55f).padding(16.dp))
                } else {
                    LazyColumn(Modifier.weight(0.55f)) {
                        items(result.fixes, key = { it.key }) { fx ->
                            FixRow(fx, fx.key !in skip) { on -> skip = if (on) skip - fx.key else skip + fx.key }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(enabled = applied > 0, onClick = { onApply(result.events, applied) }) { Text(stringResource(R.string.fx_apply_n, applied)) }
                }
            }
        }
    }
}

@Composable
private fun MsField(label: String, value: String, modifier: Modifier, onValue: (String) -> Unit) {
    ForceLtr {
        OutlinedTextField(
            value, { s -> onValue(s.filter { it.isDigit() }.take(6)) }, modifier, singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

private fun plainLine(s: String) = s.replace(Regex("\\{[^}]*\\}"), "").replace("\\N", " / ").replace("\\h", " ")

@Composable
private fun FixRow(fx: FixOps.Fix, on: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!on) }.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Checkbox(on, onToggle)
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Text(
                stringResource(R.string.fx_line, fx.line + 1) + " - " + stringResource(ruleLabel(fx.rule)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            )
            val b = fx.before
            val a = fx.after
            val dir = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)
            when {
                a == null -> {
                    Text(plainLine(b.text).ifBlank { "∅" }, style = dir.copy(textDecoration = TextDecoration.LineThrough), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.fx_removed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                a.text != b.text -> {
                    Text(plainLine(b.text), style = dir.copy(textDecoration = TextDecoration.LineThrough), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        plainLine(a.text), Modifier.background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 2.dp),
                        style = dir, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> ForceLtr {
                    Text(
                        formatAssTime(b.startMs) + " - " + formatAssTime(b.endMs) + "  →  " + formatAssTime(a.startMs) + " - " + formatAssTime(a.endMs),
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    )
                }
            }
        }
    }
    HorizontalDivider()
}
