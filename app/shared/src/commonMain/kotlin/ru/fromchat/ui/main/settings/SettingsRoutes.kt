@file:Suppress("ConstPropertyName")

package ru.fromchat.ui.main.settings

object SettingsRoutes {
    const val Appearance = "settings/appearance"
    const val Notifications = "settings/notifications"
    const val Devices = "settings/devices"
    /** Hub with a single action to start the password flow. */
    const val Security = "settings/security"
    /** Single destination: in-screen steps + morphing hero (no nested nav routes per step). */
    const val SecurityPasswordFlow = "settings/security/password"
    const val Account = "settings/account"
    const val AccountDeleteFlow = "settings/account/delete"
    const val ServerConfig = "serverConfig"
    const val About = "about"
}
