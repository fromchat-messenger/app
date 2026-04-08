package ru.fromchat

import android.app.Application
import android.util.Log
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient
import ru.fromchat.api.WebSocketManager
import ru.fromchat.fcm.ensureFcmTokenRegistered
import ru.fromchat.fcm.uploadPendingFcmTokenIfAvailable
import ru.fromchat.notifications.NotificationHelper

class App: Application() {
    @OptIn(DelicateCoroutinesApi::class)
    private fun fetchAndNotify(
        includeDmMessages: Boolean = false,
        dmMessageId: Int? = null
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                NotificationHelper.fetchAndNotify(
                    applicationContext,
                    includeDmMessages = includeDmMessages,
                    dmMessageId = dmMessageId
                )
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        UtilsLibrary.init(this)

        WebSocketManager.addGlobalMessageHandler { msg ->
            runCatching {
                if (msg.type == "newMessage") {
                    fetchAndNotify()
                } else if (msg.type == "dmNew") {
                    val dmMessageId = msg.data?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                    fetchAndNotify(
                        includeDmMessages = true,
                        dmMessageId = dmMessageId
                    )
                } else if (msg.type == "updates") {
                    msg.data?.jsonObject?.get("updates")?.jsonArray?.let { updates ->
                        var shouldFetchPublic = false
                        var shouldFetchDm = false
                        var latestDmMessageId: Int? = null

                        for (item in updates) {
                            val type = item
                                .jsonObject["type"]
                                ?.jsonPrimitive
                                ?.content

                            if (type == "newMessage") {
                                shouldFetchPublic = true
                                continue
                            }

                            if (type == "dmNew") {
                                shouldFetchDm = true
                                val envelopeId = item
                                    .jsonObject["data"]
                                    ?.jsonObject
                                    ?.get("id")
                                    ?.jsonPrimitive
                                    ?.content
                                    ?.toIntOrNull()
                                if (envelopeId != null) {
                                    latestDmMessageId = envelopeId.coerceAtLeast(latestDmMessageId ?: 0)
                                }
                            }
                        }

                        if (shouldFetchPublic || shouldFetchDm) {
                            fetchAndNotify(
                                includeDmMessages = shouldFetchDm,
                                dmMessageId = latestDmMessageId
                            )
                        }
                    }
                }
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching {
                ApiClient.loadPersistedData()
            }

            runCatching {
                uploadPendingFcmTokenIfAvailable()
            }

            // If we have an auth token, try to get current FCM token and register it immediately
            runCatching {
                val isRegistered = ensureFcmTokenRegistered()
                if (!isRegistered) {
                    Log.d("AppFCM", "FCM token registration skipped or deferred")
                }
            }
        }
    }
}