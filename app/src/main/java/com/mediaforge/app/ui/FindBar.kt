package com.mediaforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.Find
import com.mediaforge.app.subs.SearchField
import com.mediaforge.app.subs.SearchSpec
import com.mediaforge.app.subs.SubFile

/** A18.d: find / replace strip docked above the grid. Selecting a match makes it the active line. */
@Composable
fun FindBar(
    file: SubFile, active: Int, spec: SearchSpec, replacement: String,
    onSpec: (SearchSpec) -> Unit, onReplacement: (String) -> Unit,
    onGo: (Int) -> Unit, onReplaceOne: () -> Unit, onReplaceAll: () -> Unit, onClose: () -> Unit,
) {
    val bad = spec.regex && Find.error(spec)
    val hits = if (bad) emptyList() else Find.matches(file, spec)
    val dir = TextDirection.Content
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(
                spec.query, { onSpec(spec.copy(query = it)) }, Modifier.weight(1f), singleLine = true, isError = bad,
                label = { Text(stringResource(R.string.fd_find)) },
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = dir),
            )
            IconButton(onClick = { onGo(-1) }, enabled = hits.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.sub_prev)) }
            IconButton(onClick = { onGo(1) }, enabled = hits.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.sub_next)) }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.fd_close)) }
        }
        OutlinedTextField(
            replacement, onReplacement, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.fd_replace)) },
            textStyle = androidx.compose.material3.LocalTextStyle.current.copy(textDirection = dir),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(spec.regex, { onSpec(spec.copy(regex = !spec.regex)) }, { Text(stringResource(R.string.fd_regex)) })
            FilterChip(spec.matchCase, { onSpec(spec.copy(matchCase = !spec.matchCase)) }, { Text(stringResource(R.string.fd_case)) })
            listOf(SearchField.TEXT to R.string.sub_text, SearchField.STYLE to R.string.sub_style, SearchField.ACTOR to R.string.sub_actor, SearchField.EFFECT to R.string.sub_effect)
                .forEach { (f, r) -> FilterChip(spec.field == f, { onSpec(spec.copy(field = f)) }, { Text(stringResource(r)) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    bad -> stringResource(R.string.fd_bad_regex)
                    spec.query.isEmpty() -> ""
                    hits.isEmpty() -> stringResource(R.string.fd_none)
                    else -> stringResource(R.string.fd_count, hits.size)
                },
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                color = if (bad) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onReplaceOne, enabled = active in hits) { Text(stringResource(R.string.fd_replace_one)) }
            OutlinedButton(onClick = onReplaceAll, enabled = hits.isNotEmpty()) { Text(stringResource(R.string.fd_replace_all)) }
        }
    }
}
