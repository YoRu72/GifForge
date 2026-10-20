package com.mediaforge.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.R
import com.mediaforge.app.media.FontCatalog
import com.mediaforge.app.media.FontFace
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.subs.Attachments
import com.mediaforge.app.subs.SubFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 19.c + 19.d: fonts collector and attachment manager. Lists every font family the script uses with its state
 * (in the library / embedded in the file / missing), embeds library fonts into the .ass ([Fonts] section, so the
 * script shows the same on another machine and Aegisub reads it), and extracts embedded fonts into the library.
 */
@Composable
fun FontsCollectorDialog(file: SubFile, onDismiss: () -> Unit, onFile: (SubFile) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val faces by produceState<List<FontFace>?>(null) { value = withContext(Dispatchers.IO) { FontCatalog.scan(FontStore.list(ctx)) } }
    val used = Attachments.fontsUsed(file)
    val embedded = file.attachments.filter { it.section.equals("Fonts", true) }
    fun libraryFace(family: String): FontFace? = faces?.firstOrNull { it.family.trim().equals(family, true) }
    fun embeddedOf(family: String) = embedded.filter { it.name.substringBeforeLast('.').replace(Regex("_(0|B|I|BI)$"), "").equals(family, true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fc_title)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.fc_note), style = MaterialTheme.typography.bodySmall)
                for (fam in used) {
                    val lib = libraryFace(fam)
                    val emb = embeddedOf(fam)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(fam, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                when {
                                    emb.isNotEmpty() -> stringResource(R.string.fc_embedded)
                                    lib != null -> stringResource(R.string.fc_in_library)
                                    fam.equals("Arial", true) -> stringResource(R.string.fc_system)
                                    else -> stringResource(R.string.fc_missing)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (lib == null && emb.isEmpty() && !fam.equals("Arial", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (emb.isEmpty() && lib != null) OutlinedButton(onClick = {
                            scope.launch {
                                val bytes = withContext(Dispatchers.IO) { runCatching { lib.file.readBytes() }.getOrNull() }
                                if (bytes == null || bytes.size > 12_000_000) { Toast.makeText(ctx, R.string.fc_too_big, Toast.LENGTH_SHORT).show(); return@launch }
                                val flags = (if (lib.weight >= 600) "B" else "") + (if (lib.italic) "I" else "")
                                val name = fam.replace(Regex("[^A-Za-z0-9 _-]"), "_") + "_" + flags.ifEmpty { "0" } + "." + lib.file.extension.lowercase(Locale.ROOT).ifEmpty { "ttf" }
                                onFile(Attachments.attachFont(file, name, bytes))
                            }
                        }) { Text(stringResource(R.string.fc_embed)) }
                        if (emb.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    val n = withContext(Dispatchers.IO) { emb.count { a -> FontStore.importBytes(ctx, a.name, Attachments.decode(a.lines)) != null } }
                                    Toast.makeText(ctx, ctx.getString(R.string.fc_extracted, n), Toast.LENGTH_SHORT).show()
                                }
                            }) { Text(stringResource(R.string.fc_extract)) }
                            TextButton(onClick = { onFile(emb.fold(file) { f, a -> Attachments.remove(f, a.name) }) }) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
                if (used.isEmpty()) Text(stringResource(R.string.fc_none))
                val total = embedded.sumOf { a -> a.lines.sumOf { it.length } } * 3 / 4
                if (total > 0) Text(stringResource(R.string.fc_size, total / 1024), style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
