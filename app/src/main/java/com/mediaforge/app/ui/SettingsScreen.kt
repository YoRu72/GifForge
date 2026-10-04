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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mediaforge.app.Prefs
import com.mediaforge.app.R
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
            fontMsg = ctx.getString(R.string.set_importing)
            val r = withContext(Dispatchers.IO) {
                uris.map { FontStore.importUri(ctx, it) }.fold(ImportResult.EMPTY) { a, b -> a + b }
            }
            fonts = FontStore.list(ctx)
            fontMsg = fontSummary(ctx, r)
        }
    }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            fontMsg = ctx.getString(R.string.set_scanning)
            val r = FontStore.importTree(ctx, uri)
            fonts = FontStore.list(ctx)
            fontMsg = fontSummary(ctx, r)
        }
    }
    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.set_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Header(stringResource(R.string.set_language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.lang_system, R.string.lang_en, R.string.lang_ar).forEachIndexed { i, n ->
                    FilterChip(selected = Prefs.lang.value == i, onClick = { Prefs.set(Prefs.lang, i) }, label = { Text(stringResource(n)) })
                }
            }

            Divider2()
            Header(stringResource(R.string.set_appearance))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).map { stringResource(it) }.forEachIndexed { i, n ->
                    FilterChip(selected = Prefs.themeMode.value == i, onClick = { Prefs.set(Prefs.themeMode, i) }, label = { Text(n) })
                }
            }
            if (Build.VERSION.SDK_INT >= 31) {
                SwitchRow(stringResource(R.string.set_dynamic), Prefs.dynamicColor.value) { Prefs.set(Prefs.dynamicColor, it) }
            }

            Divider2()
            Header(stringResource(R.string.set_defaults))
            Text(stringResource(R.string.set_defaults_hint), style = MaterialTheme.typography.bodySmall)
            LabeledSlider(stringResource(R.string.set_fps), stringResource(R.string.unit_fps, Prefs.defFps.value), Prefs.defFps.value.toFloat(), 5f..30f, 24) {
                Prefs.set(Prefs.defFps, it.roundToInt())
            }
            LabeledSlider(stringResource(R.string.set_quality), "${Prefs.defQuality.value}", Prefs.defQuality.value.toFloat(), 10f..100f, 0) {
                Prefs.set(Prefs.defQuality, it.roundToInt())
            }
            LabeledSlider(stringResource(R.string.set_clip_len), stringResource(R.string.unit_sec, Prefs.defClipSec.value), Prefs.defClipSec.value.toFloat(), 2f..60f, 0) {
                Prefs.set(Prefs.defClipSec, it.roundToInt())
            }
            Text(stringResource(R.string.set_max_width), style = MaterialTheme.typography.labelLarge)
            MaxWidthChips(Prefs.defMaxWidth.value) { Prefs.set(Prefs.defMaxWidth, it) }
            SwitchRow(stringResource(R.string.set_loop), Prefs.defLoop.value) { Prefs.set(Prefs.defLoop, it) }
            SwitchRow(stringResource(R.string.set_fast), Prefs.defFast.value) { Prefs.set(Prefs.defFast, it) }

            Divider2()
            Header(stringResource(R.string.set_saving))
            SwitchRow(stringResource(R.string.set_autosave), Prefs.autoSave.value) { Prefs.set(Prefs.autoSave, it) }
            val appCtx = androidx.compose.ui.platform.LocalContext.current
            SwitchRow(stringResource(R.string.set_sub_autosave), Prefs.subAutosave.value) { on ->
                Prefs.set(Prefs.subAutosave, on)
                if (!on) com.mediaforge.app.media.Autosave.clearAll(appCtx) // off = nothing is kept, including old copies
            }
            Text(stringResource(R.string.set_sub_autosave_note), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = Prefs.namePrefix.value,
                onValueChange = { v -> Prefs.set(Prefs.namePrefix, v.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(24)) },
                label = { Text(stringResource(R.string.set_prefix)) },
                supportingText = { Text(stringResource(R.string.set_prefix_example, Prefs.safePrefix() + "1700000000000.gif")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider2()
            Header(stringResource(R.string.set_behavior))
            SwitchRow(stringResource(R.string.set_keep_awake), Prefs.keepAwake.value) { Prefs.set(Prefs.keepAwake, it) }

            Divider2()
            Header(stringResource(R.string.set_fonts))
            Text(ctx.resources.getQuantityString(R.plurals.font_files_installed, fonts.size, fonts.size, formatSize(fonts.sumOf { it.length() })))
            Text(stringResource(R.string.set_fonts_folder, FontStore.dir(ctx).absolutePath), style = MaterialTheme.typography.bodySmall)
            Button(onClick = onFonts) { Text(stringResource(R.string.set_open_fonts)) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filesLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.set_import_files)) }
                OutlinedButton(onClick = { treeLauncher.launch(null) }) { Text(stringResource(R.string.set_import_folder)) }
            }
            OutlinedTextField(
                value = Prefs.fontSample.value,
                onValueChange = { Prefs.set(Prefs.fontSample, it.take(40)) },
                label = { Text(stringResource(R.string.set_preview_text)) },
                supportingText = { Text(stringResource(R.string.set_preview_hint, "12345abcd")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { Prefs.set(Prefs.fontSample, "12345abcd") },
                enabled = Prefs.fontSample.value != "12345abcd",
            ) { Text(stringResource(R.string.set_preview_reset)) }
            if (fonts.isNotEmpty()) {
                OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.set_delete_fonts)) }
            }
            fontMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            Divider2()
            Header(stringResource(R.string.set_storage))
            Text(stringResource(R.string.set_temp_files, formatSize(cacheBytes)))
            OutlinedButton(onClick = {
                cacheFiles(ctx).forEach { it.delete() }
                cacheBytes = cacheFiles(ctx).sumOf { it.length() }
            }) { Text(stringResource(R.string.set_clear_temp)) }

            Divider2()
            Header(stringResource(R.string.set_about))
            Text("MediaForge $version")
            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text(stringResource(R.string.set_delete_fonts_title)) },
                    text = { Text(stringResource(R.string.set_delete_fonts_body)) },
                    confirmButton = {
                        TextButton(onClick = {
                            fonts.forEach { FontStore.delete(it) }
                            fonts = FontStore.list(ctx)
                            confirmDelete = false
                            fontMsg = ctx.getString(R.string.set_deleted_fonts)
                        }) { Text(stringResource(R.string.delete)) }
                    },
                    dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
                )
            }
        }
    }
}

private fun fontSummary(ctx: Context, r: ImportResult): String {
    val n = r.imported.size
    var m = ctx.resources.getQuantityString(R.plurals.fonts_imported, n, n)
    if (r.failed > 0) m += "\n" + ctx.getString(R.string.fonts_import_failed, r.failed)
    if (r.unsupported.isNotEmpty()) m += "\n" + ctx.getString(R.string.fonts_import_unsupported, r.unsupported.joinToString(", "))
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
