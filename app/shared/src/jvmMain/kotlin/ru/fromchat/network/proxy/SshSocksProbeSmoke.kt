package ru.fromchat.network.proxy

import kotlinx.coroutines.runBlocking
import ru.fromchat.config.ServerConfig
import java.io.File

/**
 * Debug-only SSH dynamic SOCKS + in-app loopback TLS probe. Run via `./gradlew :app:shared:sshSocksProbeSmoke`.
 *
 * Env: `FROMCHAT_SSH_HOST` (default fromchat.ru), `FROMCHAT_SSH_PORT` (2222),
 * `FROMCHAT_SSH_USER` (denis0001-dev), `FROMCHAT_SSH_KEY` (path to PEM), `FROMCHAT_SSH_KEY_PASSPHRASE` (optional).
 */
fun main() {
    val host = System.getenv("FROMCHAT_SSH_HOST")?.trim().orEmpty().ifEmpty { "fromchat.ru" }
    val port = System.getenv("FROMCHAT_SSH_PORT")?.trim()?.toIntOrNull() ?: 2222
    val user = System.getenv("FROMCHAT_SSH_USER")?.trim().orEmpty().ifEmpty { "denis0001-dev" }
    val keyPath = System.getenv("FROMCHAT_SSH_KEY")?.trim().orEmpty()
        .ifEmpty { "${System.getenv("HOME")}/.ssh/id_ed25519" }
    val keyFile = File(keyPath)
    if (!keyFile.isFile) {
        System.err.println("SSH key not found: $keyPath (set FROMCHAT_SSH_KEY)")
        kotlin.system.exitProcess(2)
    }
    val passphrase = System.getenv("FROMCHAT_SSH_KEY_PASSPHRASE")?.takeIf { it.isNotEmpty() }
    ServerConfig.initialize()
    runBlocking {
        val forwarder = SshDynamicSocksForwarder(
            host = host,
            port = port,
            username = user,
            auth = SshKeyAuth(),
            password = null,
            privateKeyPem = keyFile.readText(),
            privateKeyPassphrase = passphrase,
        )
        // #region agent log
        sshProbeAgentLog(
            "H0",
            "SshSocksProbeSmoke.kt:start",
            "ssh smoke starting",
            """{"host":"$host","port":$port,"user":"$user","runId":"ssh-smoke-v1"}""",
        )
        // #endregion
        val localPort = try {
            forwarder.start()
        } catch (error: Throwable) {
            // #region agent log
            sshProbeAgentLog(
                "H2",
                "SshSocksProbeSmoke.kt:ssh-start-failed",
                error.message ?: error.javaClass.name,
                """{"exception":"${error.javaClass.name}"}""",
            )
            // #endregion
            System.err.println("SSH_START_FAIL: ${error.message}")
            error.printStackTrace()
            kotlin.system.exitProcess(1)
        }
        val result = try {
            ProxyRuntime.withProbeIsolation {
                probeSshLocalSocksCheck(forwarder, localPort)
            }
        } finally {
            forwarder.stop()
        }
        when (result) {
            is ProxyCheckResult.Success -> {
                // #region agent log
                sshProbeAgentLog(
                    "H0",
                    "SshSocksProbeSmoke.kt:success",
                    "ssh smoke ok",
                    """{"pingMs":${result.pingMs},"instanceLen":${result.instanceId.length}}""",
                )
                // #endregion
                println("SSH_SMOKE_OK pingMs=${result.pingMs} instanceId=${result.instanceId}")
            }
            else -> {
                // #region agent log
                sshProbeAgentLog(
                    "H1",
                    "SshSocksProbeSmoke.kt:failed",
                    result.toString(),
                    """{"result":"${result::class.simpleName}"}""",
                )
                // #endregion
                System.err.println("SSH_SMOKE_FAIL: $result")
                kotlin.system.exitProcess(1)
            }
        }
    }
}