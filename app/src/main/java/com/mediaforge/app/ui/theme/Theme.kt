package com.mediaforge.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Typography
import com.mediaforge.app.Prefs
import com.mediaforge.app.l10n.AppLang

private val Purple = Color(0xFF6C4DFF)
private val Light = lightColorScheme(primary = Purple)
private val Dark = darkColorScheme(primary = Color(0xFFB9A8FF))

@Composable
fun MediaForgeTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.themeMode.value) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        Prefs.dynamicColor.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = if (AppLang.isArabic()) arabicTypography() else latinTypography(),
        content = content,
    )
}
