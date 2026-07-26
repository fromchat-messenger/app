package ru.fromchat.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
actual fun getColorScheme(darkTheme: Boolean, dynamicColor: Boolean) =
    if (darkTheme) darkColorScheme() else lightColorScheme()

@Composable
actual fun ApplySystemBarTheme(darkTheme: Boolean, surfaceColor: Color) = Unit
