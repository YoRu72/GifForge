package com.mediaforge.app.l10n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.text.TextUtils
import android.view.View
import androidx.compose.ui.unit.LayoutDirection
import com.mediaforge.app.Prefs
import java.util.Locale

/**
 * App language, kept apart from the device language so Arabic can be chosen in Settings.
 * Digits are always Western (1234567): the locale carries the -u-nu-latn extension, which also
 * makes every String.format / resource %d in the app use those digits.
 */
object AppLang {
    const val SYSTEM = 0
    const val EN = 1
    const val AR = 2

    private var device: Locale = Locale.getDefault()

    /** Call once, before anything changes Locale.setDefault. */
    fun init(ctx: Context) {
        device = ctx.resources.configuration.locales[0]
    }

    private fun code(pref: Int): String = when (pref) {
        EN -> "en"
        AR -> "ar"
        else -> device.language
    }

    fun locale(pref: Int = Prefs.lang.value): Locale {
        val b = Locale.Builder()
        if (pref == SYSTEM) b.setLocale(device) else b.setLanguage(code(pref))
        return b.setUnicodeLocaleKeyword("nu", "latn").build()
    }

    fun isArabic(pref: Int = Prefs.lang.value) = code(pref) == "ar"

    fun isRtl(pref: Int = Prefs.lang.value) =
        TextUtils.getLayoutDirectionFromLocale(locale(pref)) == View.LAYOUT_DIRECTION_RTL

    fun direction(pref: Int = Prefs.lang.value) = if (isRtl(pref)) LayoutDirection.Rtl else LayoutDirection.Ltr

    /** Context whose resources use the chosen language; still unwraps to the Activity. */
    fun wrap(base: Context, pref: Int = Prefs.lang.value): Context {
        val loc = locale(pref)
        Locale.setDefault(loc)
        val conf = Configuration(base.resources.configuration)
        conf.setLocale(loc)
        conf.setLayoutDirection(loc)
        return LocalizedContext(base, base.createConfigurationContext(conf).resources)
    }
}

private class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
}
