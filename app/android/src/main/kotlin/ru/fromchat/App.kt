package ru.fromchat

import android.app.Application
import com.pr0gramm3r101.utils.UtilsLibrary
import com.pr0gramm3r101.utils.settings.settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.WebSocketManager
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap
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
            GlobalScope.launch(Dispatchers.IO) {
                runCatching {
                    val currentUserId = settings.getInt("current_user_id", -1)

                    fun isOwnPublicMessage(data: JsonObject?) =
                        data?.get("user_id")?.jsonPrimitive?.content?.toIntOrNull() == currentUserId

                    fun isOwnDmMessage(data: JsonObject?) =
                        data?.get("senderId")?.jsonPrimitive?.content?.toIntOrNull() == currentUserId

                    when (msg.type) {
                        "newMessage" -> {
                            if (!isOwnPublicMessage(msg.data?.jsonObject)) {
                                fetchAndNotify()
                            }
                        }

                        "dmNew" -> {
                            if (!isOwnDmMessage(msg.data?.jsonObject)) {
                                fetchAndNotify(
                                    includeDmMessages = true,
                                    dmMessageId = msg
                                        .data
                                        ?.jsonObject
                                        ?.get("id")
                                        ?.jsonPrimitive
                                        ?.content
                                        ?.toIntOrNull()
                                )
                            }
                        }

                        "updates" -> {
                            msg.data?.jsonObject?.get("updates")?.jsonArray?.let { updates ->
                                var shouldFetchPublic = false
                                var shouldFetchDm = false
                                var latestDmMessageId: Int? = null

                                for (item in updates) {
                                    val (type, data) = item.jsonObject.let {
                                        Pair(
                                            it["type"]?.jsonPrimitive?.content,
                                            it["data"]?.jsonObject
                                        )
                                    }

                                    when (type) {
                                        "newMessage" -> {
                                            if (!isOwnPublicMessage(data)) {
                                                shouldFetchPublic = true
                                            }
                                        }

                                        "dmNew" -> {
                                            if (!isOwnDmMessage(data)) {
                                                shouldFetchDm = true

                                                data
                                                    ?.get("id")
                                                    ?.jsonPrimitive
                                                    ?.content
                                                    ?.toIntOrNull()
                                                    ?.let {
                                                        latestDmMessageId = it.coerceAtLeast(
                                                            latestDmMessageId ?: 0
                                                        )
                                                    }
                                            }
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
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { ApiClient.loadPersistedData() }
            AttachmentTransferBootstrap.launchOnApplicationStart()
        }
    }
}