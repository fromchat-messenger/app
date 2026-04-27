package ru.fromchat.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pr0gramm3r101.utils.crypto.deriveAuthSecret
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.api.ApiClient
import ru.fromchat.api.RegisterRequest
import ru.fromchat.api.apiRequest
import ru.fromchat.crypto.IdentityKeyManager
import ru.fromchat.back
import ru.fromchat.confirm_password
import ru.fromchat.display_name
import ru.fromchat.display_name_error
import ru.fromchat.error_unexpected
import ru.fromchat.fill_all_fields
import ru.fromchat.password
import ru.fromchat.password_length_error
import ru.fromchat.passwords_dont_match
import ru.fromchat.register
import ru.fromchat.register_button
import ru.fromchat.register_d
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.RowHeader
import ru.fromchat.username
import ru.fromchat.username_length_error

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit
) {
    val errorUnexpected = stringResource(Res.string.error_unexpected)
    
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        var username by remember { mutableStateOf("") }
        var displayName by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var confirmPassword by remember { mutableStateOf("") }
        var alert by remember { mutableStateOf<String?>(null) }

        val scope = rememberCoroutineScope()
        val navController = LocalNavController.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RowHeader(
                    icon = Icons.Filled.PersonAdd,
                    title = stringResource(Res.string.register),
                    subtitle = stringResource(Res.string.register_d)
                )

                if (alert != null) {
                    Text(text = alert!!, color = MaterialTheme.colorScheme.error)
                }

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(Res.string.username)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text(stringResource(Res.string.display_name)) },
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(Res.string.password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(Res.string.confirm_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val alertErrorFilling = stringResource(Res.string.fill_all_fields)
                    val alertErrorPasswordLittle = stringResource(Res.string.password_length_error)
                    val alertErrorNameLittle = stringResource(Res.string.username_length_error)
                    val alertErrorPasswordConfrim = stringResource(Res.string.passwords_dont_match)
                    val alertErrorDisplayName = stringResource(Res.string.display_name_error)

                    IconButton(
                        onClick = { navController.navigateUp() }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back)
                        )
                    }

                    Button(
                        onClick = {
                            // Checks
                            if (username.isBlank() || displayName.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                                alert = alertErrorFilling
                                return@Button
                            }
                            if (password != confirmPassword) {
                                alert = alertErrorPasswordConfrim
                                return@Button
                            }
                            if (username.length !in 3..20) {
                                alert = alertErrorNameLittle
                                return@Button
                            }
                            if (displayName.isBlank() || displayName.length > 64) {
                                alert = alertErrorDisplayName
                                return@Button
                            }
                            if (password.length !in 5..50) {
                                alert = alertErrorPasswordLittle
                                return@Button
                            }

                            // Derive auth secret before sending (matches frontend implementation)
                            scope.launch {
                                val u = username.trim()
                                val display = displayName.trim()
                                val derived = deriveAuthSecret(u, password)

                                apiRequest(
                                    unexpectedError = errorUnexpected,
                                    onError = { message, _ ->
                                        alert = message
                                    },
                                    onSuccess = {
                                        onRegistered()
                                    }
                                ) {
                                    val response = ApiClient.registerRequest(
                                        RegisterRequest(
                                            u,
                                            display,
                                            derived,
                                            derived
                                        )
                                    )
                                    ApiClient.bindSession(response)
                                    try {
                                        IdentityKeyManager.ensureKeysOnLogin(
                                            username = u,
                                            password = password,
                                            token = response.token
                                        )
                                    } catch (e: Exception) {
                                        ApiClient.clearMemorySession()
                                        throw e
                                    }
                                    ApiClient.persistSessionToStorage(response)
                                    runCatching { ApiClient.refreshServerInstanceFingerprint() }
                                    response
                                }
                            }
                        }
                    ) {
                        Text(stringResource(Res.string.register_button))
                    }
                }
            }
        }
    }
}