package ru.fromchat.desktop.tray

import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBus
import org.freedesktop.dbus.interfaces.Properties
import ru.fromchat.Logger
import ru.fromchat.desktop.TrayConnectionStatus
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

/**
 * SNI (StatusNotifierItem) tray over D-Bus: the shell hosts the icon and renders our menu, so
 * nothing about the tray touches AWT's XEmbed land or a separate Compose popup scene.
 *
 * Design, click matrix and wire contract: `app/desktop/tray-sni/DESIGN.md`.
 *
 * Threading: [tryInstall] and [updateStatus] are meant to be called off the EDT (they do blocking
 * D-Bus I/O); method callbacks from the shell arrive on dbus-java threads and are hopped to the
 * EDT before being handed to the app — same contract the AWT tray clicks follow.
 */
internal class SniTrayController private constructor(
    private val connection: DBusConnection,
    private val itemImpl: SniItemImpl,
    private val menuImpl: SniMenuImpl,
    private val ownerChangedToken: AutoCloseable?,
) {
    private val closed = AtomicBoolean(false)

    /**
     * Pushes status changes: the disabled status row label, the tooltip text, and the
     * `ItemsPropertiesUpdated` signal the shell's menu client listens for. Shapes are static,
     * so no `LayoutUpdated` is needed (and `revision` never moves).
     */
    fun updateStatus(statusLabel: String) {
        if (closed.get() || !connection.isConnected) return
        runCatching {
            itemImpl.setToolTip(statusLabel)
            menuImpl.updateItemLabel(statusLabel)
            connection.sendMessage(
                SniMenuIface.ItemsPropertiesUpdated(
                    MENU_PATH,
                    menuImpl.itemPropertyUpdates(),
                    emptyList(),
                ),
            )
            connection.sendMessage(
                SniItemIface.NewToolTip(ITEM_PATH, itemImpl.liveToolTip()),
            )
        }.onFailure {
            Logger.w(LOG_TAG, "updateStatus failed: ${it.message}")
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching {
            ownerChangedToken?.close()
            connection.close()
        }.onFailure {
            Logger.w(LOG_TAG, "SNI connection close failed: ${it.message}")
        }
    }

    companion object {
        private val LOG_TAG = "SniTray"
        const val ITEM_PATH = "/org/ayatana/NotificationItem/FromChat"
        const val MENU_PATH = "$ITEM_PATH/Menu"
        private const val WATCHER_SERVICE = "org.kde.StatusNotifierWatcher"
        private const val WATCHER_PATH = "/StatusNotifierWatcher"
        private const val HOST_REGISTERED_PROP = "IsStatusNotifierHostRegistered"

        /** Pixmaps to ship: smallest-first matters on the shell side (`getBestPixmap`). */
        private val TRAY_PX_SIZES = listOf(32, 48)

        /**
         * All-or-nothing install: connect, export item + menu, register with the watcher. Any
         * failure closes the connection and yields `null`; the caller keeps the AWT/XEmbed
         * fallback.
         */
        fun tryInstall(
            trayImage: BufferedImage,
            appName: String,
            statusLabel: String,
            showLabel: String,
            aboutLabel: String,
            quitLabel: String,
            onShow: () -> Unit,
            onAbout: () -> Unit,
            onQuit: () -> Unit,
        ): SniTrayController? {
            val connection = try {
                DBusConnectionBuilder.forSessionBus()
                    .withShared(false)
                    .build()
            } catch (failure: Exception) {
                Logger.w(LOG_TAG, "No session bus; SNI tray unavailable: ${failure.message}")
                return null
            }

            try {
                val itemImpl = SniItemImpl(
                    pixmaps = sniPixmaps(trayImage, TRAY_PX_SIZES),
                    appName = appName,
                    statusLabel = statusLabel,
                    onShow = { runOnEdt(onShow, "onShow") },
                    onAbout = { runOnEdt(onAbout, "onAbout") },
                    onQuit = { runOnEdt(onQuit, "onQuit") },
                )
                val menuImpl = SniMenuImpl(
                    statusLabel = statusLabel,
                    showLabel = showLabel,
                    aboutLabel = aboutLabel,
                    quitLabel = quitLabel,
                    onShow = { runOnEdt(onShow, "menu onShow") },
                    onAbout = { runOnEdt(onAbout, "menu onAbout") },
                    onQuit = { runOnEdt(onQuit, "menu onQuit") },
                )

                connection.exportObject(ITEM_PATH, itemImpl)
                connection.exportObject(MENU_PATH, menuImpl)

                val watchProps = connection.getRemoteObject(
                    WATCHER_SERVICE,
                    WATCHER_PATH,
                    Properties::class.java,
                )
                val hostRegistered = remoteWatcherHostRegistered(watchProps)
                if (!hostRegistered) {
                    Logger.w(LOG_TAG, "No SNI host registered on the watcher")
                    return null
                }

                watcherProxy(connection).RegisterStatusNotifierItem(ITEM_PATH)
                Logger.i(LOG_TAG, "SNI item registered at $ITEM_PATH (${connection.uniqueName})")

                connection.sendMessage(SniItemIface.NewIcon(ITEM_PATH))

                val ownerChangedToken = subscribeToWatcherOwnerChanges(connection)
                return SniTrayController(connection, itemImpl, menuImpl, ownerChangedToken)
            } catch (failure: DBusExecutionException) {
                Logger.w(LOG_TAG, "SNI registration failed: ${failure.message}")
            } catch (failure: Exception) {
                Logger.w(LOG_TAG, "SNI setup failed: ${failure.javaClass.simpleName}: ${failure.message}")
            }
            runCatching { connection.close() }
            return null
        }

        /** A shell restart re-renders the extension; re-register on owner changes of the watcher. */
        private fun subscribeToWatcherOwnerChanges(connection: DBusConnection) = runCatching {
            connection.addSigHandler(DBus.NameOwnerChanged::class.java) { signal ->
                if (signal.name == WATCHER_SERVICE && signal.newOwner.isNullOrBlank()) {
                    Logger.i(LOG_TAG, SNI_WATCHER_LOST)
                    reRegisterLater(connection)
                }
            }
        }.onFailure {
            Logger.w(LOG_TAG, "watcher NameOwnerChanged subscription failed: ${it.message}")
        }.getOrNull()

        private fun reRegisterLater(connection: DBusConnection) {
            Thread {
                runCatching {
                    val watchProps = connection.getRemoteObject(
                        WATCHER_SERVICE,
                        WATCHER_PATH,
                        Properties::class.java,
                    )
                    if (remoteWatcherHostRegistered(watchProps)) {
                        watcherProxy(connection).RegisterStatusNotifierItem(ITEM_PATH)
                        Logger.i(LOG_TAG, "SNI re-registered after watcher owner change")
                    }
                }.onFailure {
                    Logger.w(LOG_TAG, "SNI re-registration failed: ${it.message}")
                }
            }.apply { isDaemon = true }.start()
        }

        private fun watcherProxy(connection: DBusConnection): SniWatcherIface =
            connection.getRemoteObject(WATCHER_SERVICE, WATCHER_PATH, SniWatcherIface::class.java)

        /**
         * Reads `IsStatusNotifierHostRegistered` through the standard Properties proxy. The
         * interface has no annotation-constrained property getters (see [SniDBus.kt]), so the
         * generic `<A> Get` reply may arrive wrapped depending on how dbus-java coerces it.
         */
        private fun remoteWatcherHostRegistered(properties: Properties): Boolean = runCatching {
            val requested = properties.Get<Any?>(WATCHER_IFACE_NAME, HOST_REGISTERED_PROP)
            val value = (requested as? org.freedesktop.dbus.types.Variant<*>)?.value ?: requested
            value == true
        }.getOrElse {
            Logger.w(LOG_TAG, "Watcher host check failed: ${it.message}")
            false
        }

        /** dbus callbacks arrive on library threads; the app expects EDT like its AWT clicks. */
        private fun runOnEdt(block: () -> Unit, what: String) {
            SwingUtilities.invokeLater {
                runCatching { block() }.onFailure {
                    Logger.w(LOG_TAG, "$what dispatch failed: ${it.message}")
                }
            }
        }

        private const val SNI_WATCHER_LOST = "SNI watcher owner lost; queued re-registration"
    }
}
