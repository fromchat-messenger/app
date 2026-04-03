package ru.fromchat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirrors process visibility: [setForeground] from [androidx.lifecycle.Lifecycle.Event.ON_START] /
 * [androidx.lifecycle.Lifecycle.Event.ON_STOP] (or initial [syncFromLifecycle]).
 */
object AppForeground {
    private val _isInForeground = MutableStateFlow(true)
    val isInForeground: StateFlow<Boolean> = _isInForeground.asStateFlow()

    fun setForeground(inForeground: Boolean) {
        _isInForeground.value = inForeground
    }
}
