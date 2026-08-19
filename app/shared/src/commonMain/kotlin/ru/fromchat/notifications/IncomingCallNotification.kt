package ru.fromchat.notifications

/** Platform hook when an incoming call arrives while the app may be in the background. */
internal expect fun notifyIncomingCallIfBackground(callerDisplayName: String)
