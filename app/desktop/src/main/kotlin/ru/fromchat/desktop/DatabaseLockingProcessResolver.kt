package ru.fromchat.desktop

import java.io.File
import java.lang.ProcessHandle
import java.util.concurrent.TimeUnit

object DatabaseLockingProcessResolver {
    fun findLockingProcesses(files: List<File>): List<LockingProcessInfo> {
        val discovered = buildList {
            if (isWindowsOs()) {
                addAll(WindowsRestartManager.findLockingProcesses(files))
            }
            if (isMacOs()) {
                addAll(findWithLsof(files))
            }
            if (isLinuxOs()) {
                addAll(findWithFuser(files))
            }
            addAll(ProcessExecutableResolver.findFromChatProcesses())
        }
        return discovered
            .distinctBy { it.pid }
            .sortedBy { it.name.lowercase() }
    }

    fun killProcess(pid: Long): Boolean = ProcessExecutableResolver.killProcess(pid)

    private fun findWithLsof(files: List<File>): List<LockingProcessInfo> {
        val pids = linkedSetOf<Long>()
        files.forEach { file ->
            val process = runCatching {
                ProcessBuilder("lsof", "-t", file.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return@forEach
            process.waitFor(3, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readLines()
                .mapNotNull { it.trim().toLongOrNull() }
                .forEach { pids += it }
        }
        return pids.mapNotNull { pidToLockingProcess(it) }
    }

    private fun findWithFuser(files: List<File>): List<LockingProcessInfo> {
        val pids = linkedSetOf<Long>()
        files.forEach { file ->
            val process = runCatching {
                ProcessBuilder("fuser", file.absolutePath)
                    .redirectErrorStream(true)
                    .start()
            }.getOrNull() ?: return@forEach
            process.waitFor(3, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            output.split(Regex("\\s+"))
                .mapNotNull { token -> token.trim().toLongOrNull() }
                .forEach { pids += it }
        }
        return pids.mapNotNull { pidToLockingProcess(it) }
    }

    private fun pidToLockingProcess(pid: Long): LockingProcessInfo? {
        if (pid == ProcessHandle.current().pid()) return null
        val handle = ProcessHandle.of(pid)
        if (handle.isEmpty) return null
        val info = handle.get().info()
        val command = info.command().orElse("")
        val executable = ProcessExecutableResolver.executablePathForPid(pid)
        val name = executable?.let { File(it).name }
            ?: command.substringBefore(' ').takeIf { it.isNotBlank() }
            ?: "PID $pid"
        return LockingProcessInfo(
            pid = pid,
            name = name,
            description = command.ifBlank { null },
            executablePath = executable,
            icon = ProcessExecutableResolver.iconForExecutable(executable),
        )
    }
}
