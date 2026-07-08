package ru.fromchat.api

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.fromchat.Logger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val PENDING_FCM_TOKEN_KEY = "pending_fcm_token"
private const val CURRENT_FCM_TOKEN_KEY = "current_fcm_token"

private suspend fun fetchCurrentFcmToken(): String? = suspendCancellableCoroutine { cont ->
    FirebaseMessaging.getInstance().token
        .addOnCompleteListener { task: Task<String> ->
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                cont.resumeWithException(
                    task.exception ?: IllegalStateException("Failed to fetch FCM token")
                )
            }
        }
}

private suspend fun postFcmToken(token: String): Boolean {
    return runCatching {
        ApiClient.registerFcmToken(token)
        Logger.i("FcmReg", "Uploaded FCM token to server: ...${token.takeLast(8)}")
        true
    }.getOrElse { e ->
        Logger.e("FcmReg", "Failed to upload FCM token: ${e.message}", e)
        false
    }
}

actual suspend fun uploadPendingFcmTokenIfAvailable() = withContext(Dispatchers.IO) {
    try {
        val pending = settings.getString(PENDING_FCM_TOKEN_KEY, "")

        if (ApiClient.token.isNullOrEmpty()) {
            Logger.d("FcmReg", "Auth token missing; deferring FCM token upload")
            return@withContext
        }
        if (pending.isBlank()) {
            return@withContext
        }

        if (postFcmToken(pending)) {
            settings.putString(CURRENT_FCM_TOKEN_KEY, pending)
            settings.remove(PENDING_FCM_TOKEN_KEY)
        } else {
            Logger.d("FcmReg", "Deferring pending FCM token upload")
        }
    } catch (e: Exception) {
        Logger.e("FcmReg", "uploadPendingFcmTokenIfAvailable error: ${e.message}")
    }
}

actual suspend fun ensureFcmTokenRegistered(): Boolean = withContext(Dispatchers.IO) {
    if (ApiClient.token.isNullOrEmpty()) {
        Logger.d("FcmReg", "Auth token missing; skip explicit FCM sync")
        return@withContext false
    }

    try {
        uploadPendingFcmTokenIfAvailable()
        val token = fetchCurrentFcmToken() ?: return@withContext false
        val prepared = token.trim()
        if (prepared.isBlank()) return@withContext false

        val current = settings.getString(CURRENT_FCM_TOKEN_KEY, "")
        if (prepared == current) return@withContext true

        val result = postFcmToken(prepared)
        if (result) {
            settings.putString(CURRENT_FCM_TOKEN_KEY, prepared)
            settings.remove(PENDING_FCM_TOKEN_KEY)
        }
        result
    } catch (e: Exception) {
        Logger.e("FcmReg", "ensureFcmTokenRegistered error: ${e.message}")
        false
    }
}

actual suspend fun unregisterFcmTokenFromServer(): Boolean = withContext(Dispatchers.IO) {
    if (ApiClient.token.isNullOrEmpty()) {
        Logger.d("FcmReg", "Auth token missing; cannot unregister FCM token")
        return@withContext false
    }

    val storedToken = settings.getString(CURRENT_FCM_TOKEN_KEY, "").trim()
    val token = storedToken.ifBlank {
        runCatching { fetchCurrentFcmToken()?.trim().orEmpty() }.getOrDefault("")
    }
    Logger.i("FcmReg", "unregisterFcmTokenFromServer requested with token=...${token.takeLast(8)}")
    return@withContext runCatching {
        ApiClient.unregisterFcmToken(token.takeIf { it.isNotBlank() })
        settings.remove(PENDING_FCM_TOKEN_KEY)
        settings.remove(CURRENT_FCM_TOKEN_KEY)
        true
    }.getOrElse { e ->
        Logger.e("FcmReg", "Failed to unregister FCM token: ${e.message}")
        false
    }
}

actual suspend fun isFcmPushRegisteredLocally(): Boolean =
    settings.getString(CURRENT_FCM_TOKEN_KEY, "").isNotBlank()
