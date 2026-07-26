package ru.fromchat.ui.main.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import com.pr0gramm3r101.utils.WindowWidthSizeClass
import com.pr0gramm3r101.utils.currentWindowAdaptiveInfo
import com.pr0gramm3r101.utils.widthSizeClass
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.back
import ru.fromchat.ui.LocalNavController

/**
 * Back is shown on compact (full-screen stack), and on large screens only when the
 * previous destination is not the list–detail shell (`chat`) — i.e. when nested
 * deeper than a settings detail opened as the right pane.
 */
@Composable
fun settingsDetailShowBackButton(): Boolean {
    if (currentWindowAdaptiveInfo().widthSizeClass == WindowWidthSizeClass.COMPACT) {
        return true
    }
    val previousRoute = LocalNavController.current.previousBackStackEntry?.destination?.route
    return previousRoute != null && previousRoute != "chat"
}

@Composable
fun settingsDetailUseCollapsingTopBar(): Boolean =
    currentWindowAdaptiveInfo().widthSizeClass == WindowWidthSizeClass.COMPACT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDetailTopBar(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val navigationIcon: @Composable () -> Unit = {
        if (settingsDetailShowBackButton()) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.back),
                )
            }
        }
    }
    if (settingsDetailUseCollapsingTopBar()) {
        MediumTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            scrollBehavior = scrollBehavior,
        )
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
        )
    }
}
