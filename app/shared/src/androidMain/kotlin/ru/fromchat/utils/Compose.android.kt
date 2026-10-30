package ru.fromchat.utils

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeNestedScroll
import androidx.compose.ui.Modifier

@OptIn(ExperimentalLayoutApi::class)
actual fun Modifier.imeScrollWithKeyboard(): Modifier = this.imeNestedScroll()