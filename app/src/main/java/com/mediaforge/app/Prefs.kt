package com.mediaforge.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class BoolPref(val key: String, val def: Boolean) { var value by mutableStateOf(def); internal set }
class IntPref(val key: String, val def: Int) { var value by mutableStateOf(def); internal set }
class StrPref(val key: String, val def: String) { var value by mutableStateOf(def); internal set }

/** App settings. Values are Compose state, so screens update live; changes persist immediately. */
object Prefs {
    private var sp: SharedPreferences? = null

    val lang = IntPref("lang", 0)                  // 0 device, 1 English, 2 Arabic
    val themeMode = IntPref("theme", 0)            // 0 system, 1 light, 2 dark
    val dynamicColor = BoolPref("dynamic", true)
    val defFps = IntPref("def_fps", 15)
    val defQuality = IntPref("def_quality", 90)
    val defClipSec = IntPref("def_clip_sec", 10)
    val defMaxWidth = IntPref("def_max_width", 0)  // 0 = original size
    val defLoop = BoolPref("def_loop", true)
    val defFast = BoolPref("def_fast", false)
    val autoSave = BoolPref("auto_save", true)
    val keepAwake = BoolPref("keep_awake", true)
    val subAutosave = BoolPref("sub_autosave", true)   // recovery copy of the open subtitle script
    val namePrefix = StrPref("name_prefix", "MediaForge_")
    val fontSample = StrPref("font_sample", "12345abcd")   // preview text in the font directory
    val recentSubs = StrPref("recent_subs", "")            // recent subtitle uris, one per line

    private val ints = listOf(lang, themeMode, defFps, defQuality, defClipSec, defMaxWidth)
    private val bools = listOf(dynamicColor, defLoop, defFast, autoSave, keepAwake, subAutosave)
    private val strs = listOf(namePrefix, fontSample, recentSubs)

    fun init(ctx: Context) {
        if (sp != null) return
        val p = ctx.applicationContext.getSharedPreferences("mediaforge_prefs", Context.MODE_PRIVATE)
        sp = p
        ints.forEach { it.value = p.getInt(it.key, it.def) }
        bools.forEach { it.value = p.getBoolean(it.key, it.def) }
        strs.forEach { it.value = p.getString(it.key, it.def) ?: it.def }
    }

    fun set(p: IntPref, v: Int) { p.value = v; sp?.edit()?.putInt(p.key, v)?.apply() }
    fun set(p: BoolPref, v: Boolean) { p.value = v; sp?.edit()?.putBoolean(p.key, v)?.apply() }
    fun set(p: StrPref, v: String) { p.value = v; sp?.edit()?.putString(p.key, v)?.apply() }

    /** Filename prefix that is always safe to use. */
    fun safePrefix(): String = namePrefix.value.ifBlank { "MediaForge_" }
}
