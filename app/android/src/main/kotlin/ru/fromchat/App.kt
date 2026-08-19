package ru.fromchat

import android.app.Application
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import ru.fromchat.api.ApiClient
import ru.fromchat.api.local.workers.AttachmentTransferBootstrap
import ru.fromchat.notifications.MessageNotificationCoordinator

class App : Application() {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        UtilsLibrary.init(this)
        MessageNotificationCoordinator.install()

        GlobalScope.launch(Dispatchers.IO) {
            runCatching { ApiClient.loadPersistedData() }
            AttachmentTransferBootstrap.launchOnApplicationStart()
        }
    }
}
