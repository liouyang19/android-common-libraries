package com.taisau.android.common.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class ThemeState(
    initialType: ThemeType = ThemeType.LIGHT,
    initialCustomColors: CustomThemeColors = CustomThemeColors.DEFAULT,
) {
    var themeType: ThemeType by mutableStateOf(initialType)
        private set

    var customThemeColors: CustomThemeColors by mutableStateOf(initialCustomColors)
        private set

    fun switch(type: ThemeType) {
        themeType = type
    }

    fun updateCustomColors(colors: CustomThemeColors) {
        customThemeColors = colors
        themeType = ThemeType.CUSTOM
    }
}

@Composable
fun rememberThemeState(
    initialType: ThemeType = ThemeType.LIGHT,
    initialCustomColors: CustomThemeColors = CustomThemeColors.DEFAULT,
): ThemeState {
    return remember { ThemeState(initialType, initialCustomColors) }
}

val LocalThemeState = staticCompositionLocalOf { ThemeState() }
