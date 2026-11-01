package ru.fromchat.utils.settings

import ru.fromchat.utils.files.ensurePortableLayoutInstanceId
import ru.fromchat.utils.files.isPortableDesktop

/** Java Preferences node path; portable builds use a per-folder instance suffix. */
fun jvmPreferencesNode(baseNode: String): String {
    if (!isPortableDesktop()) return baseNode
    val instance = ensurePortableLayoutInstanceId()
        ?: return "$baseNode.portable.unbound"
    return "$baseNode.portable.$instance"
}
