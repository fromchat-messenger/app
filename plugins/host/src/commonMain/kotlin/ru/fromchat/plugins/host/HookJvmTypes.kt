package ru.fromchat.plugins.host

internal fun resolveJvmParameterType(typeName: String): Class<*> =
    when (typeName) {
        "boolean" -> java.lang.Boolean.TYPE
        "byte" -> java.lang.Byte.TYPE
        "char" -> java.lang.Character.TYPE
        "short" -> java.lang.Short.TYPE
        "int" -> Integer.TYPE
        "long" -> java.lang.Long.TYPE
        "float" -> java.lang.Float.TYPE
        "double" -> java.lang.Double.TYPE
        "void" -> java.lang.Void.TYPE
        else -> Class.forName(typeName)
    }
