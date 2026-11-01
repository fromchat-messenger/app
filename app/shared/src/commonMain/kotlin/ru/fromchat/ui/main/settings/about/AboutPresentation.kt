package ru.fromchat.ui.main.settings.about

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.utils.WindowWidthSizeClass
import ru.fromchat.utils.currentWindowAdaptiveInfo
import ru.fromchat.utils.widthSizeClass

/** Opens About as a centered dialog (wide) or full-screen route (compact). */
val LocalOpenAbout = staticCompositionLocalOf<() -> Unit> {
    error("LocalOpenAbout not provided")
}

@Composable
fun aboutUsesDialogPresentation(): Boolean =
    currentWindowAdaptiveInfo().widthSizeClass != WindowWidthSizeClass.COMPACT

fun NavController.isShowingAboutRoute(): Boolean =
    currentBackStackEntry?.destination?.route == SettingsRoutes.About

fun NavController.popAboutRouteIfShowing() {
    if (isShowingAboutRoute()) {
        navigateUp()
    }
}

@Composable
fun AboutDialogHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    documentNavController: NavController,
) {
    if (!visible) return

    val outerPadding = 24.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(outerPadding)
                .widthIn(max = 440.dp)
                .heightIn(max = 720.dp),
        ) {
            CompositionLocalProvider(LocalNavController provides documentNavController) {
                AboutScreenLayout(
                    onNavigateUp = onDismiss,
                    useCloseButton = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
