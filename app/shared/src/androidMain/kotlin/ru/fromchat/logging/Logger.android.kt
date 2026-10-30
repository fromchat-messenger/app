package ru.fromchat.logging

import android.util.Log

actual object Logger {
    actual fun d(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Debug, tag, message, throwable)
        Log.d(tag, message, throwable)
    }

    actual fun i(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Info, tag, message, throwable)
        Log.i(tag, message, throwable)
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Warn, tag, message, throwable)
        Log.w(tag, message, throwable)
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Error, tag, message, throwable)
        Log.e(tag, message, throwable)
    }

    actual fun f(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Fatal, tag, message, throwable)
        Log.wtf(tag, message, throwable)
    }
}

