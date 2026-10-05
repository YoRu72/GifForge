package com.mediaforge.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.FixOps
import com.mediaforge.app.subs.FixRule

private fun FixRule.label() = when (this) {
    FixRule.EMPTY -> R.string.fx_empty; FixRule.SPACES -> R.string.fx_spaces; FixRule.OVERLAP -> R.string.fx_overlap
    FixRule.SHORT -> R.string.fx_short; FixRule.SPEED -> R.string.fx_speed; FixRule.LONG -> R.string.fx_long; FixRule.AR_PUNCT -> R.string.fx_ar_punct; FixRule.AR_DIGITS -> R.string.fx_ar_digits
}

/** Fix Common Errors: each rule shows how many lines it would change; only the ticked rules are applied. */
@Composable
fun FixDialog(events: List<AssEvent>, onDismiss: () -> Unit, onApply: (Set<FixRule>) -> Unit) {
    val counts = remember(events) { FixRule.values().associateWith { FixOps.count(events, it) } }
    var on by remember { mutableStateOf(counts.filter { it.value > 0 && it.key != FixRule.LONG }.keys) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.fx_title)) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                FixRule.values().forEach { r ->
                    val n = counts[r] ?: 0
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(r in on, { on = if (r in on) on - r else on + r }, enabled = n > 0)
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(stringResource(r.label()))
                            Text(if (n > 0) stringResource(R.string.fx_count, n) else stringResource(R.string.fx_none), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = on.isNotEmpty(), onClick = { onApply(on) }) { Text(stringResource(R.string.sh_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
