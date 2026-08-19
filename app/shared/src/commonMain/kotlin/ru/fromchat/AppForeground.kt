package ru.fromchat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppForeground {
    private val _isInForeground = MutableStateFlow(true)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    private val _isWindowFocused = MutableStateFlow(true)
    val isWindowFocused: StateFlow<Boolean> = _isWindowFocused.asStateFlow()

    fun setForeground(inForeground: Boolean) {
        _isInForeground.value = inForeground
        if (!inForeground) _isWindowFocused.value = false
    }

    fun setWindowFocused(focused: Boolean) {
        _isWindowFocused.value = focused
        if (focused) _isInForeground.value = true
    }
}
