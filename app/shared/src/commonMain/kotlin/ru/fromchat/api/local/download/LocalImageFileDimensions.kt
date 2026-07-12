package ru.fromchat.api.local.download

/** EXIF-oriented width/height from a local file (bounds read only; no full decode). */
internal expect fun readLocalImageDimensions(absolutePath: String): Pair<Int, Int>?

/** Bounds-only width/height from encoded image bytes (JPEG EXIF when supported). */
internal expect fun readImageDimensionsFromBytes(data: ByteArray): Pair<Int, Int>?
