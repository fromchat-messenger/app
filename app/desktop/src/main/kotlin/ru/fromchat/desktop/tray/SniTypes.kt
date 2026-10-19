package ru.fromchat.desktop.tray

import org.freedesktop.dbus.Struct
import org.freedesktop.dbus.annotations.Position
import org.freedesktop.dbus.exceptions.DBusException
import org.freedesktop.dbus.interfaces.DBusSerializable
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * One "pixmap" of an SNI icon: an RGBA32-size struct as `a(iiay)` per the StatusNotifierItem spec.
 *
 * The pixel data is what the shell extension expects (`pixmapsUtils.js` flips ARGB→RGBA, assumes
 * `rowStride = width * 4`): four bytes per pixel in **ARGB byte order, big-endian** — exactly what
 * Java's `(alpha shl 24) or (red shl 16) or (green shl 8) or blue` reads as bytes most-significant
 * first, so the packing here is a straight shift-per-channel, no `ImageIO`.
 */
class SniPixmapStruct(
    @Position(0) private val width: Int,
    @Position(1) private val height: Int,
    @Position(2) private val data: ByteArray,
) : Struct()

/**
 * SNI `ToolTip` struct `(sa(iiay)ss)`: theme icon name, pixmaps, title, description.
 * GNOME never reads it (shipped XML comments ToolTip out), KDE does.
 */
class SniToolTipStruct(
    @Position(0) private val iconName: String,
    @Position(1) private val pixmaps: List<SniPixmapStruct>,
    @Position(2) private val title: String,
    @Position(3) private val description: String,
) : Struct()

/**
 * One node of the DbusMenu layout tree. The shell's client binds `GetLayout` to
 * `(u (ia{sv}av))`, i.e. children are *Variants* of recursive nodes — not the flattened form the
 * protocol spec describes. Getting this shape right is what keeps the popup from silently not
 * building.
 */
class SniLayoutNode(
    @Position(0) private val id: Int,
    @Position(1) private val properties: Map<String, Variant<*>>,
    /** Each element is a [Variant] wrapping another [SniLayoutNode]. */
    @Position(2) private val children: List<Variant<*>>,
) : Struct()

/**
 * `GetLayout` return tuple `(u revision, node)`.
 *
 * Deliberately a [DBusSerializable] (not a `Struct`): dbus-java's exported-method return-XML
 * generator (`ExportedObject.createReturnArguments`) walks *outer* struct fields by their erased
 * `Field#getType`, so a struct with `List` fields dies on export; the serializable branch instead
 * re-derives the wire shape from the `deserialize` signature with full generic info, and the
 * runtime expands `serialize()` to exactly these reply args. `SniLayoutNode` stays a Struct — the
 * deep struct recursion is generic-aware.
 */
class SniGetLayoutResult(
    private var revision: UInt32,
    private var root: SniLayoutNode,
) : DBusSerializable {
    override fun serialize(): Array<Any?> = arrayOf(revision, root)

    fun deserialize(revision: UInt32, root: SniLayoutNode) {
        // Inbound GetLayout replies never happen (we are the server); contract requires the method.
        this.revision = revision
        this.root = root
    }
}

/** `GetGroupProperties` response element: one item's full property map. */
class SniItemProperties(
    @Position(0) private val id: Int,
    @Position(1) private val properties: Map<String, Variant<*>>,
) : Struct()

/** `ItemsPropertiesUpdated` second argument element `a(ias)`: id + removed property names. */
class SniRemovedProperties(
    @Position(0) private val id: Int,
    @Position(1) private val names: List<String>,
) : Struct()

/** `EventGroup` element `a(isvu)`. */
class SniEventStruct(
    @Position(0) private val id: Int,
    @Position(1) private val eventId: String,
    @Position(2) private val data: Variant<*>,
    @Position(3) private val timestamp: UInt32,
) : Struct() {
    fun menuId() = id

    fun menuEventId() = eventId
}

/** `AboutToShowGroup` return tuple `(ai updatesNeeded, ai idErrors)`; see `SniGetLayoutResult`. */
class SniAboutToShowGroupResult(
    private var updatesNeeded: List<Int>,
    private var idErrors: List<Int>,
) : DBusSerializable {
    override fun serialize(): Array<Any?> = arrayOf(updatesNeeded, idErrors)

    fun deserialize(updatesNeeded: List<Int>, idErrors: List<Int>) {
        // Inbound only; we always send false / empty lists (static menu tree).
        this.updatesNeeded = updatesNeeded
        this.idErrors = idErrors
    }
}

/** Progressively downsizes [image] to each requested square size, keeping ARGB. */
internal fun sniPixmaps(image: BufferedImage, sizes: List<Int>): List<SniPixmapStruct> =
    sizes.map { size ->
        val scaled = resizeSquare(image, size)
        SniPixmapStruct(scaled.width, scaled.height, argb32Bytes(scaled))
    }

/**
 * ARGB32 big-endian byte stream of [image] (see [SniPixmapStruct]): per pixel the four bytes
 * `alpha, red, green, blue`.
 */
private fun argb32Bytes(image: BufferedImage): ByteArray {
    val buffer = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    val bytes = ByteArray(buffer.size * 4)
    var i = 0
    for (pixel in buffer) {
        bytes[i++] = (pixel ushr 24).toByte()
        bytes[i++] = (pixel ushr 16).toByte()
        bytes[i++] = (pixel ushr 8).toByte()
        bytes[i++] = pixel.toByte()
    }
    return bytes
}

private fun resizeSquare(source: BufferedImage, size: Int): BufferedImage {
    if (source.width == size && source.height == size) return source
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics: Graphics2D = out.createGraphics()
    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    graphics.drawImage(source, 0, 0, size, size, null)
    graphics.dispose()
    return out
}

private fun variantOf(value: Any?) = Variant(value)

/** Menu node property maps (string keys per the DbusMenu property catalogue). */
internal sealed interface SniMenuItemType {
    data object Separator : SniMenuItemType
    data class Item(val label: String, val enabled: Boolean) : SniMenuItemType
}

internal fun SniMenuItemType.dbusmenuProps(): Map<String, Variant<*>> =
    when (this) {
        is SniMenuItemType.Separator -> buildMap {
            put("type", variantOf("separator"))
            put("visible", variantOf(true))
            put("enabled", variantOf(true))
        }
        is SniMenuItemType.Item -> buildMap {
            put("type", variantOf("standard"))
            put("label", variantOf(label))
            put("visible", variantOf(true))
            put("enabled", variantOf(enabled))
        }
    }
