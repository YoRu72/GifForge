package com.mediaforge.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mediaforge.app.media.CropRect

val CROP_PRESETS = listOf("Free", "Original", "1:1", "16:9", "9:16", "4:3", "3:4")

class CropState {
    var rect by mutableStateOf(CropRect())
    var aspect by mutableStateOf<Float?>(null) // locked pixel aspect (w/h), null = free
    var preset by mutableStateOf("Free")

    fun reset() { rect = CropRect(); aspect = null; preset = "Free" }

    fun applyPreset(name: String, cw: Int, ch: Int) {
        val a: Float? = when (name) {
            "Original" -> cw.toFloat() / ch
            "1:1" -> 1f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:3" -> 4f / 3f
            "3:4" -> 3f / 4f
            else -> null
        }
        preset = name
        aspect = a
        if (a == null) return
        val ca = cw.toFloat() / ch
        var wF: Float
        var hF: Float
        if (a >= ca) { wF = 1f; hF = ca / a } else { hF = 1f; wF = a / ca }
        if (name != "Original") { wF *= 0.9f; hF *= 0.9f }
        val cx = ((rect.l + rect.r) / 2f).coerceIn(wF / 2f, 1f - wF / 2f)
        val cy = ((rect.t + rect.b) / 2f).coerceIn(hF / 2f, 1f - hF / 2f)
        rect = CropRect(cx - wF / 2f, cy - hF / 2f, cx + wF / 2f, cy + hF / 2f)
    }
}
