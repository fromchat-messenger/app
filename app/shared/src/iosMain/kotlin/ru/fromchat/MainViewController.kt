package ru.fromchat

import androidx.compose.ui.window.ComposeUIViewController
import ru.fromchat.ui.App

fun MainViewController(
    startAtProfileUserId: Int?,
    startAtProfileUsername: String?
) = ComposeUIViewController {
    App(
        startAtProfileUserId = startAtProfileUserId,
        startAtProfileUsername = startAtProfileUsername
    )
}

fun MainViewController() = ComposeUIViewController {
    App()
}