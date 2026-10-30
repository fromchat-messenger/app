package ru.fromchat.desktop

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class DesktopMenuCommand {
    NewChat,
    SearchConversations,
    EnterChatListSelection,
    /** Open the in-app Settings → About screen (not a separate window). */
    OpenAbout,
}

/**
 * App menu / keyboard actions → UI ([ru.fromchat.ui.main.MainScreen], [ru.fromchat.ui.App]).
 * Emitted from desktop menu bar, window shortcuts, and hardware keyboards (DeX, iPad, etc.).
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
