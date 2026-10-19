package com.mediaforge.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.subs.ArabicTools

// ---------------------------------------------------------------- AR3: Arabic tools under the line editor

/**
 * Direction marks, isolates, Arabic punctuation and the per-line direction switch. Works on the caret/selection of
 * the line editor ([tf]); every action returns the new field value through [onChange].
 */
@Composable
fun ArabicToolsRow(tf: TextFieldValue, onChange: (TextFieldValue) -> Unit) {
    fun put(s: String) {
        val (t, c) = ArabicTools.insert(tf.text, tf.selection.start, tf.selection.end, s)
        onChange(TextFieldValue(t, TextRange(c)))
    }
    fun whole(f: (String) -> String) {
        val t = f(tf.text)
        if (t != tf.text) onChange(TextFieldValue(t, TextRange(minOf(tf.selection.end, t.length))))
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = ArabicTools.isForcedRtl(tf.text), onClick = { whole { ArabicTools.toggleRtl(it) } },
            label = { Text(stringResource(R.string.ar_rtl_line)) },
        )
        AssistChip(onClick = { whole { ArabicTools.arabicPunctuation(it) } }, label = { Text(stringResource(R.string.ar_fix_punct)) })
        for (p in listOf("،", "؛", "؟", "«", "»")) AssistChip(onClick = { put(p) }, label = { Text(p) })
        // technical names of the Unicode marks stay as they are (RLM, LRM)
        AssistChip(onClick = { put(ArabicTools.RLM) }, label = { Text("RLM") })
        AssistChip(onClick = { put(ArabicTools.LRM) }, label = { Text("LRM") })
        AssistChip(onClick = {
            val (t, c) = ArabicTools.isolate(tf.text, tf.selection.start, tf.selection.end, rtl = false)
            onChange(TextFieldValue(t, TextRange(c)))
        }, label = { Text(stringResource(R.string.ar_iso_ltr)) })
        AssistChip(onClick = {
            val (t, c) = ArabicTools.isolate(tf.text, tf.selection.start, tf.selection.end, rtl = true)
            onChange(TextFieldValue(t, TextRange(c)))
        }, label = { Text(stringResource(R.string.ar_iso_rtl)) })
        AssistChip(onClick = { whole { ArabicTools.stripControls(it) } }, label = { Text(stringResource(R.string.ar_clear_marks)) })
    }
}
