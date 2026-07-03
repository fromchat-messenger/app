package ru.fromchat.ui.profile

enum class EditProfileFocusField(val arg: String) {
    Username("username"),
    DisplayName("display_name"),
    Bio("bio"),
    ;

    companion object {
        fun fromArg(value: String?) =
            value?.takeIf { it.isNotBlank() }?.let { arg -> entries.firstOrNull { it.arg == arg } }
    }
}

object ProfileRoutes {
    const val ARG_FOCUS = "focus"
    const val Edit = "profile/edit?focus={focus}"
    const val REFRESH_KEY = "profile_refresh"

    fun editRoute(focus: EditProfileFocusField? = null): String =
        if (focus == null) "profile/edit"
        else "profile/edit?focus=${focus.arg}"
}
