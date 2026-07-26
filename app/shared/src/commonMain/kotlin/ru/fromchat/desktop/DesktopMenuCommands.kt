package ru.fromchat.desktop

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DesktopMenuCommand {
    NewChat,
    SearchConversations,
    EnterChatListSelection,
}

/**
 * Desktop menu / hotkey actions → UI (MainScreen, ChatsTab).
 * No-ops on mobile when nothing emits.
 */
object DesktopMenuCommands {
    private val commandsFlow = MutableSharedFlow<DesktopMenuCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<DesktopMenuCommand> = commandsFlow.asSharedFlow()

    fun emit(command: DesktopMenuCommand) {
        commandsFlow.tryEmit(command)
    }
}
