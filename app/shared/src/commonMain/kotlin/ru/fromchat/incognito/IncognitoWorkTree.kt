package ru.fromchat.incognito

/** Schedules a sealed container sync after any mutation under the incognito work tree. */
internal fun notifyIncognitoWorkTreeMutated(path: String) {
    IncognitoMode.afterWorkTreeWrite(path)
}
