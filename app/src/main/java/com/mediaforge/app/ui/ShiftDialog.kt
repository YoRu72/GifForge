package com.mediaforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.TimeOps

enum class ShiftScope { ALL, FROM_ACTIVE, SELECTED }

/** Shift Times (Aegisub) + frame-rate change (Subtitle Edit) in one sheet. Numbers stay left-to-right. */
@Composable
fun ShiftDialog(
    hasSelection: Boolean, hasActive: Boolean, onDismiss: () -> Unit,
    onShift: (ShiftScope, Long) -> Unit, onRescale: (ShiftScope, Double, Double) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("0") }
    var scope by rememberSaveable { mutableStateOf(if (hasSelection) ShiftScope.SELECTED else ShiftScope.ALL) }
    var fps by rememberSaveable { mutableStateOf(-1) }
    val presets = listOf(23.976 to 25.0, 25.0 to 23.976, 24.0 to 25.0, 25.0 to 24.0, 29.97 to 25.0, 25.0 to 29.97)
    val delta = TimeOps.parseDelta(amount)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sh_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(scope == ShiftScope.ALL, { scope = ShiftScope.ALL }, { Text(stringResource(R.string.sh_all)) })
                    FilterChip(scope == ShiftScope.FROM_ACTIVE, { scope = ShiftScope.FROM_ACTIVE }, { Text(stringResource(R.string.sh_from)) }, enabled = hasActive)
                    FilterChip(scope == ShiftScope.SELECTED, { scope = ShiftScope.SELECTED }, { Text(stringResource(R.string.sh_selected)) }, enabled = hasSelection)
                }
                ForceLtr {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it }, singleLine = true, isError = delta == null,
                        label = { Text(stringResource(R.string.sh_amount)) }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                }
                Text(stringResource(R.string.sh_hint), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.sh_fps), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                ForceLtr {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        presets.forEachIndexed { i, (a, b) -> FilterChip(fps == i, { fps = if (fps == i) -1 else i }, { Text("$a > $b") }) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = delta != null || fps >= 0, onClick = {
                if (fps >= 0) onRescale(scope, presets[fps].first, presets[fps].second)
                if (delta != null && delta != 0L) onShift(scope, delta)
            }) { Text(stringResource(R.string.sh_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
