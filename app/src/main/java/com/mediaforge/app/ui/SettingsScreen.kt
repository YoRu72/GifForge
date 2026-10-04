package com.mediaforge.app.ui

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.mediaforge.app.media.FontStore
import com.mediaforge.app.media.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mediaforge.app.Prefs
import java.io.File
import kotlin.math.roundToInt

private fun cacheFiles(ctx: Context): List<File> =
    ctx.cacheDir.listFiles { f -> f.isFile && (f.name.startsWith("mediaforge_") || f.name.startsWith("gifsrc_") || f.name.startsWith("mfproxy_")) }?.toList()
        ?: emptyList()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onFonts: () -> Unit) {
    val ctx = LocalContext.current
    var cacheBytes by remember { mutableLongStateOf(cacheFiles(ctx).sumOf { it.length() }) }
    var fonts by remember { mutableStateOf(FontStore.list(ctx)) }
    var fontMsg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val filesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            fontMsg = "Importing..."
            val r = withContext(Dispatchers.IO) {
                uris.map { FontStore.importUri(ctx, it) }.fold(ImportResult.EMPTY) { a, b -> a + b }
            }
            fonts = FontStore.list(ctx)
            fontMsg = fontSummary(r)
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            fontMsg = "Scanning folder..."
            val r = FontStore.importTree(ctx, uri)
            fonts = FontStore.list(ctx)
            fontMsg = fontSummary(r)
        }
    }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Header("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, n ->
                    FilterChip(selected = Prefs.themeMode.value == i, onClick = { Prefs.set(Prefs.themeMode, i) }, label = { Text(n) })
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow("Dynamic colors (from wallpaper)", Prefs.dynamicColor.value) { Prefs.set(Prefs.dynamicColor, it) }
            }

            Divider2()
            Header("New project defaults")
            Text("Used when you open a video; you can still change them per project.", style = MaterialTheme.typography.bodySmall)
            LabeledSlider("Frame rate", "${Prefs.defFps.value} fps", Prefs.defFps.value.toFloat(), 5f..30f, 24) {
                Prefs.set(Prefs.defFps, it.roundToInt())
            }
            LabeledSlider("Quality", "${Prefs.defQuality.value}", Prefs.defQuality.value.toFloat(), 10f..100f, 0) {
                Prefs.set(Prefs.defQuality, it.roundToInt())
            }
            LabeledSlider("Default clip length", "${Prefs.defClipSec.value} s", Prefs.defClipSec.value.toFloat(), 2f..60f, 0) {
                Prefs.set(Prefs.defClipSec, it.roundToInt())
            }
            Text("Maximum width", style = MaterialTheme.typography.labelLarge)
            MaxWidthChips(Prefs.defMaxWidth.value) { Prefs.set(Prefs.defMaxWidth, it) }
            SwitchRow("Loop forever", Prefs.defLoop.value) { Prefs.set(Prefs.defLoop, it) }
            SwitchRow("Fast encode (lower quality)", Prefs.defFast.value) { Prefs.set(Prefs.defFast, it) }

            Divider2()
            Header("Saving")
            SwitchRow("Save to gallery automatically", Prefs.autoSave.value) { Prefs.set(Prefs.autoSave, it) }
            OutlinedTextField(
                value = Prefs.namePrefix.value,
                onValueChange = { v -> Prefs.set(Prefs.namePrefix, v.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(24)) },
                label = { Text("File name prefix") },
                supportingText = { Text("Example: ${Prefs.safePrefix()}1700000000000.gif") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider2()
            Header("Behavior")
            SwitchRow("Keep screen on while encoding", Prefs.keepAwake.value) { Prefs.set(Prefs.keepAwake, it) }

            Divider2()
            Header("Fonts")
            Text("${fonts.size} font file${if (fonts.size == 1) "" else "s"} installed - ${formatSize(fonts.sumOf { it.length() })}")
            Text("Stored in the app's private folder: ${FontStore.dir(ctx).absolutePath}", style = MaterialTheme.typography.bodySmall)
            Button(onClick = onFonts) { Text("Open font directory") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filesLauncher.launch(arrayOf("*/*")) }) { Text("Import files") }
                OutlinedButton(onClick = { treeLauncher.launch(null) }) { Text("Import a folder") }
            }
            OutlinedTextField(
                value = Prefs.fontSample.value,
                onValueChange = { Prefs.set(Prefs.fontSample, it.take(40)) },
                label = { Text("Font preview text") },
                supportingText = { Text("Shown in the font directory. Default: 12345abcd") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { Prefs.set(Prefs.fontSample, "12345abcd") },
                enabled = Prefs.fontSample.value != "12345abcd",
            ) { Text("Reset preview text") }
            if (fonts.isNotEmpty()) {
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete all imported fonts") }
            }
            fontMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Divider2()
            Header("Storage")
            Text("Temporary files: ${formatSize(cacheBytes)}")
            OutlinedButton(onClick = {
                cacheFiles(ctx).forEach { it.delete() }
                cacheBytes = cacheFiles(ctx).sumOf { it.length() }
            }) { Text("Clear temporary files") }

            Divider2()
            Header("About")
            Text("MediaForge $version")
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete all imported fonts?") },
                    text = { Text("Text using these fonts falls back to the default font. System fonts are not touched.") },
                    confirmButton = {
                        TextButton(onClick = {
                            fonts.forEach { FontStore.delete(it) }
                            fonts = FontStore.list(ctx)
                            confirmDelete = false
                            fontMsg = "Deleted all imported fonts"
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                )
            }
        }
    }
}

private fun fontSummary(r: ImportResult): String {
    val n = r.imported.size
    var m = "Imported $n font file" + (if (n == 1) "" else "s")
    if (r.failed > 0) m += ", ${r.failed} couldn't be read"
    if (r.unsupported.isNotEmpty()) m += ". Not supported by Android: ${r.unsupported.joinToString(", ")}"
    return m
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Divider2() {
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
}
