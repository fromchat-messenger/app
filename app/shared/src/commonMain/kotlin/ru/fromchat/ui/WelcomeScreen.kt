package ru.fromchat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ru.fromchat.auth_get_started
import ru.fromchat.auth_welcome_tagline
import ru.fromchat.auth_welcome_title
import ru.fromchat.ui.components.ActionButton
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding

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
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
                .padding(horizontal = SettingsStepHorizontalPadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = Res.getUri("drawable/logo_square.svg"),
                contentDescription = null,
                modifier = Modifier
                    .size(112.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
                contentScale = ContentScale.Crop,
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
