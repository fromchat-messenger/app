package ru.fromchat

import ru.fromchat.logging.AppLogLevel
import ru.fromchat.logging.AppLogStore

actual object Logger {
    actual fun d(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Debug, tag, message, throwable)
        println("DEBUG: [$tag] $message ${throwable?.message ?: ""}")
    }

    actual fun i(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Info, tag, message, throwable)
        println("INFO: [$tag] $message ${throwable?.message ?: ""}")
    }

    actual fun w(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Warn, tag, message, throwable)
        System.err.println("WARN: [$tag] $message ${throwable?.message ?: ""}")
    }

    actual fun e(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Error, tag, message, throwable)
        System.err.println("ERROR: [$tag] $message ${throwable?.message ?: ""}")
        throwable?.printStackTrace()
    }

    actual fun f(tag: String, message: String, throwable: Throwable?) {
        AppLogStore.record(AppLogLevel.Fatal, tag, message, throwable)
        System.err.println("FATAL: [$tag] $message ${throwable?.message ?: ""}")
        throwable?.printStackTrace()
    }
}
