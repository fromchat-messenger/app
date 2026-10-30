package ru.fromchat.logging

import platform.Foundation.NSLog

actual object Logger {
    actual fun d(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Debug, tag, message, throwable)
        NSLog("DEBUG: [%s] %s %s", tag, message, throwable?.message ?: "")
    }

    actual fun i(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Info, tag, message, throwable)
        NSLog("INFO: [%s] %s %s", tag, message, throwable?.message ?: "")
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Warn, tag, message, throwable)
        NSLog("WARN: [%s] %s %s", tag, message, throwable?.message ?: "")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Error, tag, message, throwable)
        NSLog("ERROR: [%s] %s %s", tag, message, throwable?.message ?: "")
    }

    actual fun f(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Fatal, tag, message, throwable)
        NSLog("FATAL: [%s] %s %s", tag, message, throwable?.message ?: "")
    }
}

