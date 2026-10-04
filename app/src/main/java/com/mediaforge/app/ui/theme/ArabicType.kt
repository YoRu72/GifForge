package com.mediaforge.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mediaforge.app.R

/** The font supplied for the app (KFGQPC Uthman Taha Naskh). It has no Latin letters; Android draws those from the system font. */
val UthmanFamily = FontFamily(Font(R.font.uthman_tn1, FontWeight.Normal))

private fun TextStyle.ar(): TextStyle =
    copy(fontFamily = UthmanFamily, lineHeight = if (lineHeight.isSp) lineHeight * 1.3f else lineHeight)

/** Arabic typography: the app font everywhere, and taller lines so marks above and below never clip. */
fun arabicTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.ar(), displayMedium = t.displayMedium.ar(), displaySmall = t.displaySmall.ar(),
        headlineLarge = t.headlineLarge.ar(), headlineMedium = t.headlineMedium.ar(), headlineSmall = t.headlineSmall.ar(),
        titleLarge = t.titleLarge.ar(), titleMedium = t.titleMedium.ar(), titleSmall = t.titleSmall.ar(),
        bodyLarge = t.bodyLarge.ar(), bodyMedium = t.bodyMedium.ar(), bodySmall = t.bodySmall.ar(),
        labelLarge = t.labelLarge.ar(), labelMedium = t.labelMedium.ar(), labelSmall = t.labelSmall.ar(),
    )
}
