package ru.fromchat.ui.calls

/**
 * When true, [CallMediaLayer] owns in-call mic/camera/end chrome (Android native +
 * WebView-based iOS/desktop). Call overlays still pass [showInCallControls].
 */
actual val UseInlineInCallChrome: Boolean = true
