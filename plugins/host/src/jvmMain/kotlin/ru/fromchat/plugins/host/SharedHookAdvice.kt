package ru.fromchat.plugins.host

import net.bytebuddy.asm.Advice
import ru.fromchat.plugins.HookStrategy
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal object HookAdviceBindings {
    private val byMethodKey = ConcurrentHashMap<String, SharedHookRegistration>()

    fun bind(method: Method, registration: SharedHookRegistration) {
        byMethodKey[key(method)] = registration
    }

    fun unbind(registration: SharedHookRegistration) {
        byMethodKey.entries.removeIf { it.value == registration }
    }

    fun registration(method: Method): SharedHookRegistration? = byMethodKey[key(method)]

    private fun key(method: Method): String =
        buildString {
            append(method.declaringClass.name)
            append('#')
            append(method.name)
            append('(')
            method.parameterTypes.forEachIndexed { index, type ->
                if (index > 0) append(',')
                append(type.name)
            }
            append(')')
        }
}

internal class SharedHookAdvice {
    companion object {
        @JvmStatic
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue::class)
        fun onEnter(
            @Advice.AllArguments arguments: Array<Any?>,
            @Advice.Origin method: Method,
        ): Boolean {
            val registration = HookAdviceBindings.registration(method) ?: return false
            registration.before?.let { before ->
                when (before(arguments).strategy) {
                    HookStrategy.CANCEL -> return true
                    HookStrategy.MODIFY, HookStrategy.MODIFY_FINAL, HookStrategy.DEFAULT -> Unit
                }
            }
            return false
        }

        @JvmStatic
        @Advice.OnMethodExit(onThrowable = Throwable::class)
        fun onExit(
            @Advice.AllArguments arguments: Array<Any?>,
            @Advice.Origin method: Method,
            @Advice.Enter skipOriginal: Boolean,
        ) {
            if (skipOriginal) return
            HookAdviceBindings.registration(method)?.after?.invoke(arguments, null)
        }
    }
}
