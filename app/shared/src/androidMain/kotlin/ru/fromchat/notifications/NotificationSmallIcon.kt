package ru.fromchat.notifications

import android.content.Context

/** White silhouette drawable for status-bar / notification small icons. */
object NotificationSmallIcon {
    private const val DRAWABLE_NAME = "ic_stat_fromchat"

    fun resId(context: Context): Int {
        val id = context.resources.getIdentifier(DRAWABLE_NAME, "drawable", context.packageName)
        check(id != 0) { "Missing drawable/$DRAWABLE_NAME in application resources" }
        return id
    }
}
