package com.mediaforge.app.ui

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.AssEvent
import com.mediaforge.app.subs.AssLook
import com.mediaforge.app.subs.Kara
import com.mediaforge.app.subs.Karaoke

/**
 * FX9 / 22.d: karaoke timing of one line (Aegisub's karaoke mode on a phone). Syllables are chips; the selected one can
 * be cut (only where Arabic joining allows), merged with the one before, lengthened or shortened. Tap timing: play the
 * line and press "Syllable" in the video controls at each new syllable ([tapNext] is the syllable the next tap starts).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KaraokePanel(
    e: AssEvent, hasVideo: Boolean, tapNext: Int, onTapNext: (Int) -> Unit, onText: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val k = Karaoke.parse(e.text)
    if (!k.isKaraoke) {
        Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ka_none), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { onText(Karaoke.fromPlain(e)) }) { Text(stringResource(R.string.ka_make)) }
        }
        return
    }
    val syls = Karaoke.normalize(k.syls, e.durationMs)
    var sel by remember(e.startMs) { mutableStateOf(0) }
    val i = sel.coerceIn(0, syls.size - 1)
    fun put(list: List<com.mediaforge.app.subs.Syl>) = onText(Karaoke.build(Kara(k.prefix, list)))

    Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("\\k" to R.string.ka_k, "\\kf" to R.string.ka_kf, "\\ko" to R.string.ka_ko).forEach { (t, l) ->
                FilterChip(syls.all { it.tag == t }, { put(Karaoke.setTagType(syls, t)) }, { Text(stringResource(l)) })
            }
        }
        // syllables in reading order (an Arabic line reads right to left, the row follows the screen direction)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            syls.forEachIndexed { j, s ->
                FilterChip(
                    j == i, { sel = j },
                    { Text(AssLook.plainText(s.text).ifBlank { "∅" } + "  " + (s.durMs + 5) / 10, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)) },
                )
            }
        }
        val s = syls[i]
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(enabled = i + 1 < syls.size, onClick = { put(Karaoke.nudge(syls, i, -10)) }) { Text("−10 ms") }
            OutlinedButton(enabled = i + 1 < syls.size, onClick = { put(Karaoke.nudge(syls, i, 10)) }) { Text("+10 ms") }
            OutlinedButton(enabled = i > 0, onClick = { put(Karaoke.merge(syls, i)); sel = i - 1 }) { Text(stringResource(R.string.ka_merge)) }
            OutlinedButton(onClick = { onText(k.prefix + syls.joinToString("") { it.text }) }) { Text(stringResource(R.string.ka_remove)) }
        }
        // where the selected syllable may be cut: never inside a joined Arabic word, never between a letter and its mark
        val cuts = (1 until s.text.length).filter { Karaoke.canSplit(s.text, it) && AssLook.plainText(s.text.substring(0, it)).isNotBlank() }
        if (cuts.isNotEmpty()) {
            Text(stringResource(R.string.ka_cut), style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cuts.take(40).forEach { p ->
                    AssistChip(onClick = { put(Karaoke.split(syls, i, p)) }, label = {
                        Text(AssLook.plainText(s.text.substring(0, p)) + " | " + AssLook.plainText(s.text.substring(p)), style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
                    })
                }
            }
        } else if (AssLook.plainText(s.text).trim().length > 1) {
            Text(stringResource(R.string.ka_no_cut), style = MaterialTheme.typography.bodySmall)
        }
        if (hasVideo) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (tapNext in 1 until syls.size) stringResource(R.string.ka_tap_next, tapNext + 1, syls.size) else stringResource(R.string.ka_tap_hint),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = { onTapNext(1); Toast.makeText(ctx, R.string.ka_tap_hint, Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.ka_tap_start)) }
            }
        }
    }
}
