package ru.fromchat.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import ru.fromchat.Res
import ru.fromchat.action_save
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.db.store.ProfileCache
import ru.fromchat.api.schema.core.ErrorResponse
import ru.fromchat.api.schema.user.profile.UserProfile
import ru.fromchat.auth_char_count
import ru.fromchat.auth_username_taken
import ru.fromchat.back
import ru.fromchat.display_name
import ru.fromchat.display_name_error
import ru.fromchat.error_unexpected
import ru.fromchat.profile_edit_saved
import ru.fromchat.profile_edit_title
import ru.fromchat.profile_headline_bio
import ru.fromchat.server_config_action_reset
import ru.fromchat.ui.LocalNavController
import ru.fromchat.ui.chat.Avatar
import ru.fromchat.ui.components.DisabledBringIntoViewSpec
import ru.fromchat.ui.components.FromChatSnackbarHost
import ru.fromchat.ui.components.HazeActionButton
import ru.fromchat.ui.components.LazyListFocusScrollEffect
import ru.fromchat.ui.components.Text
import ru.fromchat.ui.components.expressiveStepFieldColors
import ru.fromchat.ui.components.rememberLazyListFocusScrollState
import ru.fromchat.ui.components.SettingsPasswordOutlineFieldShape
import ru.fromchat.ui.components.showReplacingSnackbar
import ru.fromchat.ui.components.scrollFocusedItemIntoView
import ru.fromchat.ui.components.trackLazyListFocus
import ru.fromchat.profile_bio_length_error
import ru.fromchat.ui.main.settings.SettingsStepHorizontalPadding
import ru.fromchat.username
import ru.fromchat.username_length_error

private const val DISPLAY_NAME_MAX = 64
private const val BIO_MAX = 500
private const val USERNAME_MIN = 3
private const val USERNAME_MAX = 20

private object EditProfileLazyListIndices {
    const val USERNAME_FIELD = 1
    const val DISPLAY_NAME_FIELD = 2
    const val BIO_FIELD = 3
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalHazeMaterialsApi::class,
)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    initialFocusField: EditProfileFocusField? = null,
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val snackbarHostState = remember { SnackbarHostState() }
    val fieldColors = expressiveStepFieldColors()
    val hazeState = rememberHazeState()
    val focusScrollState = rememberLazyListFocusScrollState()
    val listState = rememberLazyListState()
    val usernameFocusRequester = remember { FocusRequester() }
    val displayNameFocusRequester = remember { FocusRequester() }
    val bioFocusRequester = remember { FocusRequester() }

    var usernameField by remember { mutableStateOf(TextFieldValue()) }
    var displayNameField by remember { mutableStateOf(TextFieldValue()) }
    var bioField by remember { mutableStateOf(TextFieldValue()) }
    var savedUsername by remember { mutableStateOf("") }
    var savedDisplayName by remember { mutableStateOf("") }
    var savedBio by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var listViewportBounds by remember { mutableStateOf<Rect?>(null) }

    val savedMessage = stringResource(Res.string.profile_edit_saved)
    val unexpectedError = stringResource(Res.string.error_unexpected)
    val displayNameError = stringResource(Res.string.display_name_error)
    val usernameLengthError = stringResource(Res.string.username_length_error)
    val usernameTaken = stringResource(Res.string.auth_username_taken)

    LaunchedEffect(Unit) {
        val profile = withContext(Dispatchers.Default) {
            runCatching { ApiClient.getOwnProfile() }.getOrNull()
                ?: ApiClient.user?.id?.let { ProfileCache.get(it) }
        }
        profile?.let {
            usernameField = TextFieldValue(it.username)
            displayNameField = TextFieldValue(it.displayName.orEmpty())
            bioField = TextFieldValue(it.bio.orEmpty())
            savedUsername = it.username
            savedDisplayName = it.displayName.orEmpty()
            savedBio = it.bio.orEmpty()
        }
        loaded = true
    }

    LaunchedEffect(loaded, initialFocusField) {
        if (!loaded || initialFocusField == null) return@LaunchedEffect
        val itemIndex = when (initialFocusField) {
            EditProfileFocusField.Username -> EditProfileLazyListIndices.USERNAME_FIELD
            EditProfileFocusField.DisplayName -> EditProfileLazyListIndices.DISPLAY_NAME_FIELD
            EditProfileFocusField.Bio -> EditProfileLazyListIndices.BIO_FIELD
        }
        val focusRequester = when (initialFocusField) {
            EditProfileFocusField.Username -> usernameFocusRequester
            EditProfileFocusField.DisplayName -> displayNameFocusRequester
            EditProfileFocusField.Bio -> bioFocusRequester
        }
        delay(100)
        listState.scrollFocusedItemIntoView(
            itemIndex,
            viewportMarginPx = with(density) { 12.dp.toPx() },
        )
        when (initialFocusField) {
            EditProfileFocusField.Username -> {
                usernameField = usernameField.copy(
                    selection = TextRange(0, usernameField.text.length),
                )
            }
            EditProfileFocusField.DisplayName -> {
                displayNameField = displayNameField.copy(
                    selection = TextRange(0, displayNameField.text.length),
                )
            }
            EditProfileFocusField.Bio -> Unit
        }
        focusRequester.requestFocus()
    }

    val username = usernameField.text
    val displayName = displayNameField.text
    val bio = bioField.text
    val trimmedUsername = username.trim()
    val trimmedDisplayName = displayName.trim()
    val trimmedBio = bio.trim()

    val usernameError = trimmedUsername.isNotEmpty() &&
        trimmedUsername.length !in USERNAME_MIN..USERNAME_MAX
    val displayNameFieldError = trimmedDisplayName.isNotEmpty() &&
        (trimmedDisplayName.isBlank() || trimmedDisplayName.length > DISPLAY_NAME_MAX)
    val bioError = bio.length > BIO_MAX

    val hasChanges = loaded && (
        trimmedUsername != savedUsername.trim() ||
            trimmedDisplayName != savedDisplayName.trim() ||
            trimmedBio != savedBio.trim()
        )

    val hasValidationErrors = usernameError || displayNameFieldError || bioError ||
        (hasChanges && trimmedDisplayName.isBlank())

    val canSave = loaded && hasChanges && !hasValidationErrors && !busy

    val avatarDisplayName = trimmedDisplayName.ifBlank { "?" }

    fun showSnack(text: String) {
        scope.launch {
            snackbarHostState.showReplacingSnackbar(
                message = text,
                withDismissAction = false,
                duration = SnackbarDuration.Short,
            )
        }
    }

    fun resetToSaved() {
        usernameField = TextFieldValue(savedUsername)
        displayNameField = TextFieldValue(savedDisplayName)
        bioField = TextFieldValue(savedBio)
    }

    fun saveProfile() {
        if (!canSave) return

        if (trimmedUsername.length !in USERNAME_MIN..USERNAME_MAX) {
            showSnack(usernameLengthError)
            return
        }

        if (trimmedDisplayName.isBlank() || trimmedDisplayName.length > DISPLAY_NAME_MAX) {
            showSnack(displayNameError)
            return
        }

        if (trimmedBio.length > BIO_MAX) {
            showSnack(unexpectedError)
            return
        }

        scope.launch {
            busy = true
            try {
                if (
                    !trimmedUsername.equals(savedUsername, ignoreCase = true) &&
                    runCatching { ApiClient.checkUsername(trimmedUsername).exists }
                        .getOrDefault(false)
                ) {
                    showSnack(usernameTaken)
                    return@launch
                }

                val response = withContext(Dispatchers.Default) {
                    ApiClient.updateProfile(
                        username = trimmedUsername,
                        displayName = trimmedDisplayName,
                        bio = trimmedBio.ifEmpty { "" },
                    )
                }

                savedUsername = response.username
                savedDisplayName = response.displayName.orEmpty()
                savedBio = response.bio.orEmpty()
                usernameField = TextFieldValue(savedUsername)
                displayNameField = TextFieldValue(savedDisplayName)
                bioField = TextFieldValue(savedBio)

                val ownUserId = ApiClient.user?.id
                if (ownUserId != null) {
                    val existing = ProfileCache.get(ownUserId)
                    val updatedProfile = existing?.copy(
                        username = response.username,
                        displayName = response.displayName,
                        bio = response.bio,
                    ) ?: UserProfile(
                        id = ownUserId,
                        username = response.username,
                        displayName = response.displayName,
                        bio = response.bio,
                        profilePicture = ApiClient.user?.profile_picture,
                        online = ApiClient.user?.online ?: false,
                        lastSeen = ApiClient.user?.last_seen,
                        createdAt = existing?.createdAt,
                    )
                    ProfileCache.put(updatedProfile)
                }

                showSnack(savedMessage)
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(ProfileRoutes.REFRESH_KEY, true)
                onBack()
            } catch (error: ClientRequestException) {
                val detail = if (error.response.status.value in arrayOf(400, 401, 403, 429)) {
                    runCatching { error.response.body<ErrorResponse>().detail }.getOrDefault(unexpectedError)
                } else {
                    unexpectedError
                }
                showSnack(detail)
            } catch (_: Exception) {
                showSnack(unexpectedError)
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.navigationBars,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        snackbarHost = { FromChatSnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            HazeActionButton(
                hazeState = hazeState,
                onClick = ::saveProfile,
                enabled = canSave,
                loading = busy,
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.back),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = ::resetToSaved,
                        enabled = loaded && hasChanges && !busy,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RestartAlt,
                            contentDescription = stringResource(Res.string.server_config_action_reset),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin()) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f,
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyListFocusScrollEffect(
            listState = listState,
            focusState = focusScrollState,
            viewportBoundsInWindow = listViewportBounds,
            contentPaddingTop = innerPadding.calculateTopPadding(),
            contentPaddingBottom = innerPadding.calculateBottomPadding(),
        )

        DisabledBringIntoViewSpec {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .consumeWindowInsets(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .hazeSource(hazeState)
                    .onGloballyPositioned { listViewportBounds = it.boundsInWindow() },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Avatar(
                            profilePictureUrl = ApiClient.user?.profile_picture,
                            displayName = avatarDisplayName,
                            modifier = Modifier.size(96.dp),
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = usernameField,
                        onValueChange = { usernameField = it },
                        label = { Text(stringResource(Res.string.username)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(usernameFocusRequester)
                            .trackLazyListFocus(focusScrollState, EditProfileLazyListIndices.USERNAME_FIELD)
                            .padding(horizontal = SettingsStepHorizontalPadding, vertical = 8.dp),
                        enabled = loaded && !busy,
                        singleLine = true,
                        isError = usernameError,
                        supportingText = if (usernameError) {
                            { Text(stringResource(Res.string.username_length_error)) }
                        } else null,
                        colors = fieldColors,
                        shape = SettingsPasswordOutlineFieldShape,
                    )
                }

                item {
                    OutlinedTextField(
                        value = displayNameField,
                        onValueChange = { displayNameField = it },
                        label = { Text(stringResource(Res.string.display_name)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(displayNameFocusRequester)
                            .trackLazyListFocus(focusScrollState, EditProfileLazyListIndices.DISPLAY_NAME_FIELD)
                            .padding(horizontal = SettingsStepHorizontalPadding),
                        enabled = loaded && !busy,
                        singleLine = true,
                        isError = displayNameFieldError || (hasChanges && trimmedDisplayName.isBlank()),
                        supportingText = {
                            when {
                                displayNameFieldError || (hasChanges && trimmedDisplayName.isBlank()) ->
                                    Text(stringResource(Res.string.display_name_error))
                                else ->
                                    Text(
                                        stringResource(
                                            Res.string.auth_char_count,
                                            displayName.length,
                                            DISPLAY_NAME_MAX,
                                        ),
                                    )
                            }
                        },
                        colors = fieldColors,
                        shape = SettingsPasswordOutlineFieldShape,
                    )
                }

                item { Spacer(Modifier.height(12.dp)) }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsStepHorizontalPadding),
                    ) {
                        OutlinedTextField(
                            value = bioField,
                            onValueChange = { bioField = it },
                            label = { Text(stringResource(Res.string.profile_headline_bio)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(bioFocusRequester)
                                .trackLazyListFocus(focusScrollState, EditProfileLazyListIndices.BIO_FIELD),
                            enabled = loaded && !busy,
                            minLines = 3,
                            maxLines = 6,
                            isError = bioError,
                            supportingText = {
                                if (bioError) {
                                    Text(stringResource(Res.string.profile_bio_length_error, BIO_MAX))
                                } else if (bio.isNotEmpty()) {
                                    Text(stringResource(Res.string.auth_char_count, bio.length, BIO_MAX))
                                }
                            },
                            colors = fieldColors,
                            shape = SettingsPasswordOutlineFieldShape,
                        )
                    }
                }
            }
            }
        }
    }
}
