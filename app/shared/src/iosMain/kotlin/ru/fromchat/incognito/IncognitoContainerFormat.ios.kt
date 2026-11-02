package ru.fromchat.incognito

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

private const val PACK_MAGIC = "FCFS"

internal actual fun packWorkDir(
    workRoot: String,
    extraRelativeFiles: List<Pair<String, ByteArray>>,
): ByteArray {
    val root = File(workRoot)
    val files = root.walkTopDown().filter { it.isFile }.toList()
    val buffer = ByteArrayOutputStream()
    DataOutputStream(buffer).use { out ->
        out.writeUTF(PACK_MAGIC)
        out.writeInt(files.size + extraRelativeFiles.size)
        for (file in files) {
            val relative = file.relativeTo(root).path.replace('\\', '/')
            writePackedFile(out, relative, file.readBytes())
        }
        for ((relative, bytes) in extraRelativeFiles) {
            writePackedFile(out, relative, bytes)
        }
    }
    return buffer.toByteArray()
}

private fun writePackedFile(out: DataOutputStream, relative: String, bytes: ByteArray) {
    out.writeUTF(relative)
    out.writeInt(bytes.size)
    out.write(bytes)
}

internal actual fun unpackWorkDir(workRoot: String, packed: ByteArray) {
    val root = File(workRoot)
    DataInputStream(packed.inputStream()).use { input ->
        val magic = input.readUTF()
        require(magic == PACK_MAGIC) { "Bad incognito container payload" }
        val count = input.readInt()
        repeat(count) {
            val relative = input.readUTF()
            val size = input.readInt()
            val bytes = ByteArray(size)
            input.readFully(bytes)
            val target = File(root, relative)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
    }
}
