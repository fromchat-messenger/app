/*
 * Material Symbol "exclamation" (rounded), Apache 2.0 — Google Material Design Icons.
 * Not shipped in compose material-icons-extended 1.7.3; vendored for [Icons.Rounded.Exclamation].
 */
package androidx.compose.material.icons.rounded

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _exclamation: ImageVector? = null

/** Rounded Material Symbol exclamation mark (!). */
@Suppress("UnusedReceiverParameter")
val Icons.Rounded.Exclamation: ImageVector
    get() {
        if (_exclamation != null) return _exclamation!!
        _exclamation = ImageVector.Builder(
            name = "Rounded.Exclamation",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(11.288f, 13.713f)
                quadTo(11f, 13.425f, 11f, 13f)
                verticalLineTo(6f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(13f, 6f)
                quadToRelative(0.425f, 0f, 0.713f, 0.288f)
                reflectiveQuadTo(13f, 6f)
                verticalLineToRelative(7f)
                quadToRelative(0f, 0.425f, -0.288f, 0.713f)
                reflectiveQuadTo(12f, 14f)
                quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
                moveToRelative(0f, 5f)
                quadTo(11f, 18.426f, 11f, 18f)
                quadToRelative(0f, -0.425f, 0.288f, -0.712f)
                reflectiveQuadTo(12f, 17f)
                quadToRelative(0.425f, 0f, 0.713f, 0.288f)
                reflectiveQuadTo(13f, 18f)
                quadToRelative(0f, 0.425f, -0.288f, 0.713f)
                reflectiveQuadTo(12f, 19f)
                quadToRelative(-0.425f, 0f, -0.712f, -0.288f)
            }
        }.build()
        return _exclamation!!
    }
