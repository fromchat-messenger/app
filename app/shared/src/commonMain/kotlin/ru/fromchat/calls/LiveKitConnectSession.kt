package ru.fromchat.calls

data class LiveKitConnectSession(
    val serverUrl: String,
    val token: String,
    val peerUserId: Int,
    val peerDisplayName: String,
    val roomName: String,
)
