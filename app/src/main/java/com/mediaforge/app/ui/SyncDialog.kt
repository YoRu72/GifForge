package com.mediaforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.formatAssTime
import com.mediaforge.app.subs.parseAssTime

/** Two-point sync: pick two lines and say when each should start; everything in between is stretched to fit. */
@Composable
fun SyncDialog(events: List<AssEvent>, active: Int, onDismiss: () -> Unit, onApply: (Int, Long, Int, Long) -> Unit) {
    val first0 = if (active in events.indices && active < events.size - 1) active else 0
    var a by rememberSaveable { mutableStateOf((first0 + 1).toString()) }
    var b by rememberSaveable { mutableStateOf(events.size.toString()) }
    var ta by rememberSaveable { mutableStateOf(formatAssTime(events.getOrNull(first0)?.startMs ?: 0L)) }
    var tb by rememberSaveable { mutableStateOf(formatAssTime(events.lastOrNull()?.startMs ?: 0L)) }
    val ia = a.toIntOrNull()?.minus(1); val ib = b.toIntOrNull()?.minus(1)
    val pa = parseAssTime(ta); val pb = parseAssTime(tb)
    val ok = ia != null && ib != null && ia in events.indices && ib in events.indices && ia != ib && pa != null && pb != null
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sy_title)) },
        text = {
            ForceLtr {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sy_hint), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(a, { a = it }, Modifier.weight(0.4f), singleLine = true, label = { Text(stringResource(R.string.sy_line_a)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(ta, { ta = it }, Modifier.weight(0.6f), singleLine = true, isError = pa == null, label = { Text(stringResource(R.string.sy_new_start)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(b, { b = it }, Modifier.weight(0.4f), singleLine = true, label = { Text(stringResource(R.string.sy_line_b)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(tb, { tb = it }, Modifier.weight(0.6f), singleLine = true, isError = pb == null, label = { Text(stringResource(R.string.sy_new_start)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onApply(ia!!, pa!!, ib!!, pb!!) }) { Text(stringResource(R.string.sh_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
