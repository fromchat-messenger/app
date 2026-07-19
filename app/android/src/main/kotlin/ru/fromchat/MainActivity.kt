package ru.fromchat
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.schema.messages.MessagesResponse
import ru.fromchat.config.ServerConfig
import ru.fromchat.notifications.NotificationLaunchCoordinator
import ru.fromchat.notifications.NotificationLaunchTarget
import ru.fromchat.ui.App
import ru.fromchat.ui.chat.panels.publicchat.isPublicChatVisible

private const val EXTRA_NOTIFICATION_CHAT_TYPE = "notification_chat_type"
private const val EXTRA_OPEN_DM_USER_ID = "open_dm_user_id"
private const val EXTRA_MARK_MESSAGE_READ = "mark_message_read"
private const val EXTRA_MESSAGE_ID = "scroll_to_message_id"
private const val CHAT_TYPE_PUBLIC = "public"
private const val CHAT_TYPE_DM = "dm"
private const val INVALID_PROFILE_DEEP_LINK_MESSAGE = "Could not open this profile link. Use fromchat://u/<idOrUsername>."
private data class ProfileDeepLinkResolution(
    val scrollToMessageId: Int? = null,
    val startAtPublicChat: Boolean = false,
    val startAtDmConversationUserId: Int? = null,
    val startAtProfileUserId: Int? = null,
    val startAtProfileUsername: String? = null,
    val profileLookupErrorMessage: String? = null
)

private data class ProfileDeepLinkTarget(
    val userId: Int? = null,
    val username: String? = null,
    val parseError: String? = null
)

class MainActivity : ComponentActivity() {
    private var scrollToMessageId by mutableStateOf<Int?>(null)
    private var startAtPublicChat by mutableStateOf(false)
    private var startAtDmConversationUserId by mutableStateOf<Int?>(null)
    private var startAtProfileUserId by mutableStateOf<Int?>(null)
    private var startAtProfileUsername by mutableStateOf<String?>(null)
    private var profileLookupErrorMessage by mutableStateOf<String?>(null)
    private var prevIsPublicChatVisible: Boolean? = null
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private fun parseLaunchStateFromIntent(intent: Intent?): ProfileDeepLinkResolution {
        Logger.d(
            "ProfileDeepLink",
            "handleIntent: action=${intent?.action}, data=${intent?.dataString}, messageId=${intent?.getIntExtra(EXTRA_MESSAGE_ID, -1)}, " +
                "chatType=${intent?.getStringExtra(EXTRA_NOTIFICATION_CHAT_TYPE)}, openDmUserId=${intent?.getIntExtra(EXTRA_OPEN_DM_USER_ID, -1)}"
        )
        val messageId = intent?.getIntExtra(EXTRA_MESSAGE_ID, -1) ?: -1
        val chatType = intent?.getStringExtra(EXTRA_NOTIFICATION_CHAT_TYPE) ?: CHAT_TYPE_PUBLIC
        val dmConversationUserId = intent?.getIntExtra(EXTRA_OPEN_DM_USER_ID, -1) ?: -1
        val profileTarget = parseProfileDeepLink(intent)
        Logger.d(
            "ProfileDeepLink",
            "handleIntent parsedProfileTarget: userId=${profileTarget?.userId}, username=${profileTarget?.username}, parseError=${profileTarget?.parseError}"
        )

        if (intent?.getBooleanExtra(EXTRA_MARK_MESSAGE_READ, false) == true) {
            markMessagesAsRead()
        }

        val baseState = ProfileDeepLinkResolution(
            scrollToMessageId = if (messageId != -1) messageId else null,
            startAtPublicChat = messageId != -1 && chatType != CHAT_TYPE_DM,
            startAtDmConversationUserId = if (chatType == CHAT_TYPE_DM && dmConversationUserId > 0) {
                dmConversationUserId
            } else {
                null
            }
        )

        if (profileTarget?.parseError != null) {
            return baseState.copy(profileLookupErrorMessage = profileTarget.parseError)
        }

        if (profileTarget == null) {
            return baseState
        }

        val profileUserId = profileTarget.userId
        val profileUsername = profileTarget.username?.trim().orEmpty()
        if (profileUserId != null && profileUserId > 0) {
            return baseState.copy(
                startAtProfileUserId = profileUserId,
                startAtProfileUsername = null,
                startAtDmConversationUserId = null,
                startAtPublicChat = false,
            )
        }
        if (profileUsername.isNotEmpty()) {
            return baseState.copy(
                startAtProfileUserId = null,
                startAtProfileUsername = profileUsername,
                startAtDmConversationUserId = null,
                startAtPublicChat = false,
            )
        }

        return baseState
    }

    private fun applyLaunchState(launchState: ProfileDeepLinkResolution) {
        scrollToMessageId = launchState.scrollToMessageId
        startAtPublicChat = launchState.startAtPublicChat
        startAtDmConversationUserId = launchState.startAtDmConversationUserId
        startAtProfileUserId = launchState.startAtProfileUserId
        startAtProfileUsername = launchState.startAtProfileUsername
        profileLookupErrorMessage = launchState.profileLookupErrorMessage
    }

    private fun deliverLaunchIntent(intent: Intent?) {
        val launchState = parseLaunchStateFromIntent(intent)
        applyLaunchState(launchState)

        val messageId = intent?.getIntExtra(EXTRA_MESSAGE_ID, -1) ?: -1
        if (messageId == -1 || intent?.hasExtra(EXTRA_NOTIFICATION_CHAT_TYPE) != true) {
            return
        }

        NotificationLaunchCoordinator.publish(
            NotificationLaunchTarget(
                dmConversationUserId = launchState.startAtDmConversationUserId,
                scrollToMessageId = launchState.scrollToMessageId,
                startAtPublicChat = launchState.startAtPublicChat,
            )
        )
    }

    private fun parseProfileDeepLink(intent: Intent?): ProfileDeepLinkTarget? {
        val data: Uri = intent?.data ?: return null
        Logger.d("ProfileDeepLink", "parseProfileDeepLink intentData=${data.toString()}")
        if (!data.scheme.equals("fromchat", ignoreCase = true)) return null
        if (data.host != "u") return null

        val segments = data.pathSegments.filter { it.isNotBlank() }
        Logger.d("ProfileDeepLink", "parseProfileDeepLink segments=${segments.joinToString(",")}")
        if (segments.size != 1) {
            return ProfileDeepLinkTarget(parseError = INVALID_PROFILE_DEEP_LINK_MESSAGE)
        }
        val segment = segments[0].let { Uri.decode(it) }
        val trimmed = segment.trim()
        if (trimmed.isBlank()) {
            return ProfileDeepLinkTarget(parseError = INVALID_PROFILE_DEEP_LINK_MESSAGE)
        }

        return trimmed.toLongOrNull()?.let { idLong ->
            if (idLong in 1L..Int.MAX_VALUE.toLong()) {
                Logger.d("ProfileDeepLink", "parseProfileDeepLink resolved as userId=$idLong")
                ProfileDeepLinkTarget(userId = idLong.toInt())
            } else {
                Logger.d("ProfileDeepLink", "parseProfileDeepLink large numeric treated as username=$trimmed")
                ProfileDeepLinkTarget(username = trimmed)
            }
        } ?: run {
            Logger.d("ProfileDeepLink", "parseProfileDeepLink resolved as username=$trimmed")
            ProfileDeepLinkTarget(username = trimmed)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun markMessagesAsRead() {
        GlobalScope.launch {
            try {
                // Get all unread messages and mark them as read
                val messageIds = ApiClient.http
                    .get("${ServerConfig.apiBaseUrl}/messages/new")
                    .body<MessagesResponse>()
                    .messages
                    .map { it.id }

                if (messageIds.isNotEmpty()) {
                    ApiClient.http.post("${ServerConfig.apiBaseUrl}/messages/read") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("messageIds" to messageIds))
                    }
                    Logger.i("MainActivity", "Marked ${messageIds.size} messages as read")
                }
            } catch (e: Exception) {
                Logger.e("MainActivity", "Failed to mark messages as read", e)
            }
        }
    }

    private fun checkGooglePlayServices(): Boolean {
        with (GoogleApiAvailability.getInstance()) {
            val resultCode = isGooglePlayServicesAvailable(this@MainActivity)

            if (resultCode == ConnectionResult.SUCCESS) {
                return true
            }

            if (isUserResolvableError(resultCode)) {
                getErrorDialog(
                    this@MainActivity,
                    resultCode,
                    9000
                )?.show()
            }

            return false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.i("MainActivity", "onCreate savedInstanceStateNull=${savedInstanceState == null}")
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        deliverLaunchIntent(intent)
        setContent {
            App(
                scrollToMessageId = scrollToMessageId,
                startAtPublicChat = startAtPublicChat,
                startAtDmConversationUserId = startAtDmConversationUserId,
                startAtProfileUserId = startAtProfileUserId,
                startAtProfileUsername = startAtProfileUsername,
                profileLookupErrorMessage = profileLookupErrorMessage,
                onProfileLookupErrorMessageConsumed = {
                    profileLookupErrorMessage = null
                }
            )
        }

        lifecycleScope.launch {
            checkGooglePlayServices()
        }

        // Request POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deliverLaunchIntent(intent)
    }

    override fun onPause() {
        Logger.i("MainActivity", "onPause")
        super.onPause()
        prevIsPublicChatVisible = isPublicChatVisible
        isPublicChatVisible = false
    }

    override fun onResume() {
        Logger.i("MainActivity", "onResume")
        super.onResume()
        isPublicChatVisible = prevIsPublicChatVisible ?: false
    }

    override fun onDestroy() {
        Logger.i("MainActivity", "onDestroy isFinishing=$isFinishing")
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Logger.i("MainActivity", "onSaveInstanceState")
        super.onSaveInstanceState(outState)
    }
}
