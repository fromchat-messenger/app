package ru.fromchat.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.pr0gramm3r101.utils.UtilsLibrary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.fromchat.api.WebSocketManager

actual object NetworkConnectivity {
    private val _isOnline = MutableStateFlow(true)
    actual val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var callback: ConnectivityManager.NetworkCallback? = null

    @Suppress("DEPRECATION")
    private fun computeOnline(cm: ConnectivityManager): Boolean {
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    actual fun ensureStarted() {
        if (callback != null) return
        val context = UtilsLibrary.context
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _isOnline.value = computeOnline(cm)

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOnline.value = true
                WebSocketManager.onNetworkAvailable()
            }

            override fun onLost(network: Network) {
                _isOnline.value = false
                WebSocketManager.onNetworkLost()
            }
        }
        callback = cb
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), cb)
    }
}
