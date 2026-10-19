package ru.fromchat.desktop.tray

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.messages.DBusSignal
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant

/**
 * D-Bus interfaces for the StatusNotifierItem (SNI) tray, bound to what the hosts we target
 * actually implement. The authoritative references for the wire contract lived — at design time —
 * in the shipped `interfaces-xml` of `ubuntu-appindicators` (see `tray-sni/DESIGN.md`).
 *
 * Deliberately method-only: dbus-java derives the wire type of `@DBusBoundProperty` getters from
 * the *raw* Java type, which cannot express typed arrays (`a(iiay)`) from Kotlin — and raw `List`
 * exports as the wrong type (`av`) or fails the export outright. All properties are therefore
 * served by our own `org.freedesktop.DBus.Properties` implementation in [SniItemImpl] and
 * [SniMenuImpl], where each value is a [Variant] built with an explicit signature.
 */
internal const val ITEM_IFACE_NAME = "org.kde.StatusNotifierItem"
internal const val MENU_IFACE_NAME = "com.canonical.dbusmenu"
internal const val WATCHER_IFACE_NAME = "org.kde.StatusNotifierWatcher"

@DBusInterfaceName(ITEM_IFACE_NAME)
interface SniItemIface : DBusInterface {
    /** The shell opens the DbusMenu popup itself; nothing to do when asked. */
    fun ContextMenu(x: Int, y: Int)

    /** Primary activation → raise the app window (double click on GNOME, single click on KDE). */
    fun Activate(x: Int, y: Int)

    /** Middle click on GNOME; treated as a window-show like the secondary activation path. */
    fun SecondaryActivate(x: Int, y: Int)

    fun ProvideXdgActivationToken(token: String)

    fun Scroll(delta: Int, orientation: String)

    class NewIcon(objectPath: String) : DBusSignal(objectPath)

    class NewToolTip(objectPath: String, tooltip: SniToolTipStruct) : DBusSignal(objectPath, tooltip)
}

@DBusInterfaceName(MENU_IFACE_NAME)
interface SniMenuIface : DBusInterface {
    /** Wire contract: `(u revision, node)` with `node = (ia{sv}av)`, children as nested variants. */
    fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): SniGetLayoutResult

    fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<SniItemProperties>

    fun GetProperty(id: Int, name: String): Variant<*>

    fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32)

    fun EventGroup(events: List<SniEventStruct>): List<UInt32>

    fun AboutToShow(id: Int): Boolean

    fun AboutToShowGroup(ids: List<Int>): SniAboutToShowGroupResult

    class ItemsPropertiesUpdated(
        objectPath: String,
        updatedProperties: List<SniItemProperties>,
        removedProperties: List<SniRemovedProperties>,
    ) : DBusSignal(objectPath, updatedProperties, removedProperties)

    class LayoutUpdated(objectPath: String, revision: UInt32, parent: Int) : DBusSignal(objectPath, revision, parent)
}

/**
 * org.kde.StatusNotifierWatcher — the *remote* stub. Properties (host registration etc.) are read
 * through the standard `org.freedesktop.DBus.Properties` proxy instead of `@DBusBoundProperty`
 * getters, so this stub holds nothing but the register method (the only call we make).
 */
@DBusInterfaceName(WATCHER_IFACE_NAME)
interface SniWatcherIface : DBusInterface {
    fun RegisterStatusNotifierItem(service: String)

    fun RegisterStatusNotifierItemHost(service: String)
}
