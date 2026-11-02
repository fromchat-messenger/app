package ru.fromchat.incognito

import kotlin.random.Random

/** On-disk name only; payload stays [IncognitoVault] `FCIN` + [packWorkDir] `FCFS`. */
internal object IncognitoContainerOnDisk {
    private val popularExtensions = listOf(
        "dat",
        "db",
        "bin",
        "tmp",
        "log",
        "cache",
        "json",
        "xml",
        "txt",
        "jpg",
        "png",
        "mp4",
        "pdf",
        "doc",
        "zip",
        "bak",
        "csv",
        "wav",
        "mp3",
        "sqlite",
    )

    fun newRandomFileName(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        val baseLength = 8 + Random.nextInt(5)
        val base = buildString(baseLength) {
            repeat(baseLength) {
                append(alphabet[Random.nextInt(alphabet.length)])
            }
        }
        return "$base.${popularExtensions.random()}"
    }
}
