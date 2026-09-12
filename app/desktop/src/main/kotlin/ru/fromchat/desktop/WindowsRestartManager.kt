package ru.fromchat.desktop

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.io.File

internal object WindowsRestartManager {
    private const val ERROR_MORE_DATA = 234
    private const val CCH_RM_MAX_APP_NAME = 255
    private const val CCH_RM_MAX_SVC_NAME = 63

    fun findLockingProcesses(files: List<File>): List<LockingProcessInfo> {
        if (!isWindowsOs()) return emptyList()
        val paths = files.map { it.absolutePath }.filter { it.isNotBlank() }
        if (paths.isEmpty()) return emptyList()

        val session = IntByReference()
        val sessionKey = CharArray(33)
        val start = RestartManager.INSTANCE.RmStartSession(session, 0, sessionKey)
        if (start != WinNT.ERROR_SUCCESS) return emptyList()
        val sessionHandle = session.value

        try {
            val register = RestartManager.INSTANCE.RmRegisterResources(
                sessionHandle,
                paths.size,
                paths.toTypedArray(),
                0,
                null,
                0,
                null,
            )
            if (register != WinNT.ERROR_SUCCESS) return emptyList()

            val needed = IntByReference()
            val count = IntByReference()
            var result = RestartManager.INSTANCE.RmGetList(
                sessionHandle,
                needed,
                count,
                null,
                null,
            )
            if (result != ERROR_MORE_DATA && result != WinNT.ERROR_SUCCESS) return emptyList()

            val processCount = needed.value.coerceAtLeast(count.value).coerceAtLeast(1)
            val processes = Array(processCount) { RmProcessInfo() }
            count.value = processCount
            result = RestartManager.INSTANCE.RmGetList(
                sessionHandle,
                needed,
                count,
                processes,
                null,
            )
            if (result != WinNT.ERROR_SUCCESS) return emptyList()

            return processes
                .take(count.value.coerceAtMost(processes.size))
                .mapNotNull { info ->
                    val pid = info.dwProcessId.toLong()
                    if (pid <= 0L) return@mapNotNull null
                    val appName = info.appName().trim()
                    val executable = ProcessExecutableResolver.executablePathForPid(pid)
                    LockingProcessInfo(
                        pid = pid,
                        name = appName.ifBlank { executable?.let { File(it).name } ?: "PID $pid" },
                        description = executable ?: ProcessExecutableResolver.commandLineForPid(pid),
                        executablePath = executable,
                        icon = ProcessExecutableResolver.iconForExecutable(executable),
                    )
                }
                .distinctBy { it.pid }
        } finally {
            RestartManager.INSTANCE.RmEndSession(sessionHandle)
        }
    }

    @Structure.FieldOrder(
        "dwProcessId",
        "processStartTime",
        "strAppName",
        "strServiceShortName",
        "applicationType",
        "appStatus",
        "tsSessionId",
        "restartable",
    )
    private class RmProcessInfo : Structure(), Structure.ByReference {
        @JvmField var dwProcessId = 0
        @JvmField var processStartTime = WinBase.FILETIME()
        @JvmField var strAppName = CharArray(CCH_RM_MAX_APP_NAME + 1)
        @JvmField var strServiceShortName = CharArray(CCH_RM_MAX_SVC_NAME + 1)
        @JvmField var applicationType = DWORD(0)
        @JvmField var appStatus = DWORD(0)
        @JvmField var tsSessionId = DWORD(0)
        @JvmField var restartable = 0

        fun appName(): String = Native.toString(strAppName)
    }

    private interface RestartManager : StdCallLibrary {
        fun RmStartSession(sessionHandle: IntByReference, sessionFlags: Int, sessionKey: CharArray): Int
        fun RmEndSession(sessionHandle: Int): Int
        fun RmRegisterResources(
            sessionHandle: Int,
            fileCount: Int,
            fileNames: Array<String>?,
            serviceCount: Int,
            serviceNames: Array<WString>?,
            processCount: Int,
            processIds: IntArray?,
        ): Int
        fun RmGetList(
            sessionHandle: Int,
            procInfoNeeded: IntByReference,
            procInfoCount: IntByReference,
            affectedApps: Array<RmProcessInfo>?,
            rebootReasons: IntByReference?,
        ): Int

        companion object {
            val INSTANCE: RestartManager = Native.load("Rstrtmgr", RestartManager::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
