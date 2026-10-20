package com.mediaforge.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Video frames, timelines and position pads are physical: left is always left, time runs left to right.
 * Inside an Arabic (RTL) screen they must not mirror, or text and crop boxes land on the wrong side.
 */
@Composable
fun ForceLtr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/** B3: Arabic-Indic and Persian digits typed by the keyboard become 1234567 in numeric fields; the Arabic decimal sign too. */
fun westernDigits(s: String): String = buildString(s.length) {
    for (c in s) append(
        when (c) {
            in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
            in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
            '\u066B' -> '.'
            else -> c
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LtrRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) = ForceLtr {
    RangeSlider(
        value = value, onValueChange = onValueChange, modifier = modifier,
        valueRange = valueRange, steps = steps, onValueChangeFinished = onValueChangeFinished,
    )
}
