package ru.fromchat.plugins.host.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

data class PluginBulletin(val message: String, val id: Long)

object PluginBulletinStore {
    val bulletins = mutableStateListOf<PluginBulletin>()
    private var nextId = 0L

    fun show(message: String) {
        val id = ++nextId
        bulletins += PluginBulletin(message, id)
    }
}

@Composable
fun PluginUiHost(content: @Composable () -> Unit) {
    val bulletins = remember { PluginBulletinStore.bulletins }
    Box(Modifier.fillMaxSize()) {
        content()
        bulletins.firstOrNull()?.let { bulletin ->
            LaunchedEffect(bulletin.id) {
                delay(4_000)
                if (bulletins.firstOrNull()?.id == bulletin.id) {
                    bulletins.removeAt(0)
                }
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = bulletin.message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
