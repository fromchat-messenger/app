package ru.fromchat.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController

@Composable
actual fun PlatformInteractiveKeyboardDismissEffect() {
    IosInteractiveKeyboardDismissEffect()
}

actual fun Modifier.imeScrollWithKeyboard(): Modifier = composed {
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val connection = remember(imeBottomPx, keyboardController, focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                dismissKeyboardIfScrollingWithIme(
                    available = available,
                    source = source,
                    imeBottomPx = imeBottomPx,
                    keyboardController = keyboardController,
                    focusManager = focusManager,
                )
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                dismissKeyboardIfScrollingWithIme(
                    available = available,
                    source = source,
                    imeBottomPx = imeBottomPx,
                    keyboardController = keyboardController,
                    focusManager = focusManager,
                )
                return Offset.Zero
            }
        }
    }
    nestedScroll(connection)
}

private fun dismissKeyboardIfScrollingWithIme(
    available: Offset,
    source: NestedScrollSource,
    imeBottomPx: Int,
    keyboardController: SoftwareKeyboardController?,
    focusManager: FocusManager,
) {
    if (source != NestedScrollSource.UserInput) return
    if (imeBottomPx <= 0) return
    if (available.y <= 0f) return
    keyboardController?.hide()
    focusManager.clearFocus(true)
}
