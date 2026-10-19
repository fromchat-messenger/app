package ru.fromchat.desktop.tray

import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.types.Variant

/**
 * The exported `org.kde.StatusNotifierItem`. Properties are served by the hand-rolled
 * [SniPropertiesProvider] (`org.freedesktop.DBus.Properties`), so every [Variant] carries an
 * explicit signature and `Get`/`GetAll` always hand back the live values.
 */
internal class SniItemImpl(
    pixmaps: List<SniPixmapStruct>,
    private val appName: String,
    statusLabel: String,
    private val onShow: () -> Unit,
    private val onAbout: () -> Unit,
    private val onQuit: () -> Unit,
) : SniItemIface, SniPropertiesProvider() {
    private val lock = Any()

    // Fixed content; the pixmap Variant pins its wire signature (a(iiay)) explicitly, since the
    // runtime type of a List is erased and dbus-java could not infer the element type itself.
    private val pixmapVariant =
        Variant(pixmaps.toList(), PIXMAP_WIRE_SIG)

    private var tooltip =
        SniToolTipStruct("", emptyList(), appName, statusLabel)

    fun setToolTip(statusLabel: String) {
        synchronized(lock) {
            tooltip = SniToolTipStruct("", emptyList(), appName, statusLabel)
        }
    }

    fun liveToolTip(): SniToolTipStruct = synchronized(lock) { tooltip }

    override fun sniProperties(): Map<String, Variant<*>> = synchronized(lock) {
        buildMap {
            put("Category", Variant("ApplicationStatus"))
            put("Id", Variant(appName))
            put("Title", Variant(appName))
            put("Status", Variant("Active"))
            put("WindowId", Variant(0))
            put("IconThemePath", Variant(""))
            put("Menu", Variant(DBusPath(SniTrayController.MENU_PATH)))
            put("ItemIsMenu", Variant(false))
            // Empty on purpose: a non-empty IconName makes hosts prefer the (missing) theme icon.
            put("IconName", Variant(""))
            put("IconPixmap", pixmapVariant)
            put("ToolTip", Variant(tooltip))
        }
    }

    /** The shell opens the DbusMenu popup itself; nothing to do when asked. */
    override fun ContextMenu(x: Int, y: Int) = Unit

    /** Primary activation → raise the app window (double click on GNOME, single click on KDE). */
    override fun Activate(x: Int, y: Int) {
        onShow()
    }

    /** Middle click on GNOME; a secondary activation can also raise the window. */
    override fun SecondaryActivate(x: Int, y: Int) {
        onShow()
    }

    /** v1: the shell's Wayland activation token is not needed for our (existing) window. */
    override fun ProvideXdgActivationToken(token: String) = Unit

    override fun Scroll(delta: Int, orientation: String) = Unit

    override fun isRemote() = false

    override fun getObjectPath() = SniTrayController.ITEM_PATH

    companion object {
        const val PIXMAP_WIRE_SIG = "a(iiay)"
    }
}
