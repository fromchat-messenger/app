package ru.fromchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.about
import ru.fromchat.auth_get_started
import ru.fromchat.auth_welcome_tagline
import ru.fromchat.auth_welcome_title
import ru.fromchat.logs_title
import ru.fromchat.more
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsRoutes
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

private val AUTH_CONTENT_MAX_WIDTH = 600.dp
private val AUTH_CONTENT_MAX_HEIGHT = 800.dp

@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    onAlreadyLoggedIn: () -> Unit = {},
) {
    LaunchedEffect(Unit) {
        if (!ApiClient.token.isNullOrBlank()) {
            onAlreadyLoggedIn()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.union(WindowInsets.extraStatusBars),
    ) { innerPadding ->
        val navController = LocalNavController.current
        var menuExpanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.about)) },
                        leadingIcon = {
                            Icon(Icons.Default.Info, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            navController.navigate(SettingsRoutes.About)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.logs_title)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.BugReport, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            navController.navigate(SettingsRoutes.Logs)
                        },
                    )
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val constrainWide = maxWidth > AUTH_CONTENT_MAX_WIDTH
                Box(
                    modifier =
                        if (constrainWide) {
                            Modifier
                                .align(Alignment.Center)
                                .widthIn(max = AUTH_CONTENT_MAX_WIDTH)
                                .heightIn(max = AUTH_CONTENT_MAX_HEIGHT)
                        } else {
                            Modifier.fillMaxSize()
                        },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = SettingsStepHorizontalPadding)
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = Res.getUri("drawable/logo_square.png"),
                            contentDescription = null,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(MaterialTheme.shapes.extraLarge),
                            contentScale = ContentScale.Fit,
                        )

                        Spacer(Modifier.height(24.dp))

                        Text(
                            text = stringResource(Res.string.auth_welcome_title),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(Res.string.auth_welcome_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(40.dp))

                        ActionButton(onClick = onGetStarted) {
                            Text(stringResource(Res.string.auth_get_started))
                        }
                    }
                }
            }
        }
    }
}
