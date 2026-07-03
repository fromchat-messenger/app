package ru.fromchat.legal

sealed class DocumentLoadResult {
    data class Success(
        val markdown: String,
        val isCached: Boolean,
    ) : DocumentLoadResult()

    data object Failure : DocumentLoadResult()
}
