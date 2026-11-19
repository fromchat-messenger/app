package ru.fromchat.incognito

import ru.fromchat.network.proxy.ProxyStore
import ru.fromchat.utils.settings.incognitoProfileSettings
import ru.fromchat.utils.settings.normalProfileSettings
import ru.fromchat.utils.settings.serverProfileBooleanKeys
import ru.fromchat.utils.settings.serverProfileIntKeys
import ru.fromchat.utils.settings.serverProfileSettingsKeys

internal suspend fun seedIncognitoSettingsFromNormalProfile() {
    val normal = normalProfileSettings()
    val incognito = incognitoProfileSettings()
    for (key in serverProfileSettingsKeys) {
        when {
            key in serverProfileIntKeys -> {
                if (normal.contains(key)) {
                    incognito.putInt(key, normal.getInt(key, 0))
                }
            }
            key in serverProfileBooleanKeys -> {
                if (normal.contains(key)) {
                    incognito.putBoolean(key, normal.getBoolean(key, false))
                }
            }
            else -> {
                if (normal.contains(key)) {
                    incognito.putString(key, normal.getString(key, ""))
                }
            }
        }
    }
    ProxyStore.seedIncognitoProxyStateFromNormal(normal, incognito)
    incognito.remove(ProxyStore.INCOGNITO_TRUSTED_CERTS_KEY)
}