package ru.fromchat.desktop.tray

import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import ru.fromchat.Logger

/**
 * The exported `com.canonical.dbusmenu` object: a fixed menu tree
 * `status | sep | Show | About | sep | Quit`, whose single mutable bit is the status label.
 *
 * The tree is static, so `revision` stays put; label changes go out as
 * [SniMenuIface.ItemsPropertiesUpdated] signals from [SniTrayController.updateStatus].
 * The object-level properties (`Version`, `TextDirection`, ...) are served by
 * [SniPropertiesProvider] like on the item, so value signatures stay explicit.
 */
internal class SniMenuImpl(
    statusLabel: String,
    showLabel: String,
    aboutLabel: String,
    quitLabel: String,
    private val onShow: () -> Unit,
    private val onAbout: () -> Unit,
    private val onQuit: () -> Unit,
) : SniMenuIface, SniPropertiesProvider() {
    private val lock = Any()
    private val nodes: MutableMap<Int, SniMenuItemType> = buildMap {
        put(STATUS_ID, SniMenuItemType.Item(statusLabel, enabled = false))
        put(SEPARATOR_FIRST_ID, SniMenuItemType.Separator)
        put(SHOW_ID, SniMenuItemType.Item(showLabel, enabled = true))
        put(ABOUT_ID, SniMenuItemType.Item(aboutLabel, enabled = true))
        put(SEPARATOR_LAST_ID, SniMenuItemType.Separator)
        put(QUIT_ID, SniMenuItemType.Item(quitLabel, enabled = true))
    }.toMutableMap()

    fun updateItemLabel(label: String) {
        synchronized(lock) {
            nodes[STATUS_ID] = SniMenuItemType.Item(label, enabled = false)
        }
    }

    /** Property diffs to publish for the status row — the only entry whose label can change. */
    fun itemPropertyUpdates(): List<SniItemProperties> = synchronized(lock) {
        val props = nodes[STATUS_ID]?.dbusmenuProps()
            ?: return@synchronized emptyList()
        listOf(SniItemProperties(STATUS_ID, props))
    }

    override fun GetLayout(parentId: Int, recursionDepth: Int, propertyNames: List<String>): SniGetLayoutResult =
        synchronized(lock) {
            val root = parentId.takeIf { nodes.containsKey(it) } ?: ROOT_ID
            SniGetLayoutResult(UInt32(REVISION.toLong()), layoutNode(root, recursionDepth))
        }

    override fun GetGroupProperties(ids: List<Int>, propertyNames: List<String>): List<SniItemProperties> =
        synchronized(lock) {
            val wanted = ids.takeIf { it.isNotEmpty() } ?: nodes.keys.sorted()
            wanted.mapNotNull { id ->
                nodes[id]?.let { type ->
                    SniItemProperties(id, filterProps(type.dbusmenuProps(), propertyNames))
                }
            }
        }

    override fun GetProperty(id: Int, name: String): Variant<*> = synchronized(lock) {
        nodes[id]?.dbusmenuProps()?.get(name) ?: Variant("")
    }

    override fun Event(id: Int, eventId: String, data: Variant<*>, timestamp: UInt32) {
        handleEvent(id, eventId)
    }

    override fun EventGroup(events: List<SniEventStruct>): List<UInt32> {
        events.forEach { event -> handleEvent(event.menuId(), event.menuEventId()) }
        return emptyList()
    }

    /**
     * The shell sends `AboutToShow(0)` right after the menu model is built. The tree never
     * changes shape, so `false` (no re-layout needed) is always right.
     */
    override fun AboutToShow(id: Int) = false

    override fun AboutToShowGroup(ids: List<Int>) =
        SniAboutToShowGroupResult(emptyList(), emptyList())

    override fun sniProperties(): Map<String, Variant<*>> = synchronized(lock) {
        mapOf(
            "Version" to Variant(UInt32(DBUSMENU_VERSION)),
            "TextDirection" to Variant("ltr"),
            "Status" to Variant("normal"),
            // Untyped theme path list; pin the wire signature since the runtime type is erased.
            "IconThemePath" to Variant(emptyList<String>(), ICON_THEME_PATH_WIRE_SIG),
        )
    }

    override fun isRemote() = false

    override fun getObjectPath() = SniTrayController.MENU_PATH

    private fun layoutNode(id: Int, depth: Int): SniLayoutNode =
        SniLayoutNode(
            id,
            nodes[id]?.dbusmenuProps() ?: emptyMap(),
            if (depth == 0) {
                emptyList()
            } else {
                childrenOf(id).map { childId -> Variant(layoutNode(childId, childDepth(depth))) }
            },
        )

    private fun childDepth(depth: Int) = if (depth < 0) depth else depth - 1

    private fun childrenOf(id: Int): List<Int> =
        if (id != ROOT_ID) emptyList() else nodes.keys
            .filter { it != ROOT_ID }
            .sorted()

    private fun handleEvent(id: Int, eventId: String) {
        // Only activation matters; 'opened'/'closed' carry no state the app needs.
        if (eventId != "clicked") return
        Logger.i(LOG_TAG, "menu item $id clicked")
        when (id) {
            SHOW_ID -> onShow()
            ABOUT_ID -> onAbout()
            QUIT_ID -> onQuit()
        }
    }

    companion object {
        private val LOG_TAG = "SniTray"
        private const val REVISION = 1
        private const val DBUSMENU_VERSION = 2L
        private const val ICON_THEME_PATH_WIRE_SIG = "as"

        /** Keys the client may not have asked for but needs to make sense of any item. */
        private val STRUCTURAL_PROPS = setOf("type", "children-display")

        const val ROOT_ID = 0
        const val STATUS_ID = 1
        const val SEPARATOR_FIRST_ID = 2
        const val SHOW_ID = 3
        const val ABOUT_ID = 4
        const val SEPARATOR_LAST_ID = 5
        const val QUIT_ID = 6

        private fun filterProps(
            props: Map<String, Variant<*>>,
            propertyNames: List<String>?,
        ): Map<String, Variant<*>> = when {
            propertyNames == null || propertyNames.isEmpty() -> props
            else -> props.filterKeys { key -> key in propertyNames || key in STRUCTURAL_PROPS }
        }
    }
}
