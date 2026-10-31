@file:Suppress("NOTHING_TO_INLINE")

package ru.fromchat.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual inline fun currentWindowSize() = LocalWindowInfo.current.containerSize