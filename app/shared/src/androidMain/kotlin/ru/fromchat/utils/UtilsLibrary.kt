package ru.fromchat.utils

import android.content.Context
import java.lang.ref.WeakReference

actual object UtilsLibrary {
    private var init = false
    private lateinit var _context: WeakReference<Context>
    val context get() = _context.get()!!

    actual fun init(vararg args: Any) {
        if (!init) {
            _context = WeakReference(args[0] as Context)
            init = true
        }
    }
}