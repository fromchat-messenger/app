package ru.fromchat.desktop.tray

import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

/**
 * The `org.freedesktop.DBus.Properties` surface of one exported SNI object.
 *
 * Hand-rolled (not `@DBusBoundProperty`) so each value can be a [Variant] built with an explicit
 * D-Bus signature: dbus-java binds annotated getters by their *raw* Java type, which cannot carry
 * `a(iiay)` from Kotlin. Each object serves exactly one interface, so the interface argument is
 * ignored here.
 */
internal abstract class SniPropertiesProvider : Properties {
    abstract fun sniProperties(): Map<String, Variant<*>>

    override fun <A> Get(interfaceName: String, propertyName: String): A {
        val variant = sniProperties()[propertyName]
            ?: throw DBusException("Unknown property: $propertyName")
        @Suppress("UNCHECKED_CAST")
        return variant as A
    }

    override fun <A> Set(interfaceName: String, propertyName: String, value: A) = Unit

    override fun GetAll(interfaceName: String): Map<String, Variant<*>> = sniProperties()
}
