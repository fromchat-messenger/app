package ru.fromchat.network.proxy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import java.io.IOException
import java.io.StringReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local SOCKS5 listener that forwards flows through SSH direct-tcpip channels (ssh -D semantics).
 */
actual class SshDynamicSocksForwarder actual constructor(
    private val host: String,
    private val sshPort: Int,
    private val username: String,
    private val authType: SshAuthType,
    private val password: String?,
    private val privateKeyPem: String?,
) {
    private var sshClient: SSHClient? = null
    private var serverSocket: ServerSocket? = null
    private var workers: ExecutorService? = null
    private val running = AtomicBoolean(false)

    actual fun start(): Int {
        if (!running.compareAndSet(false, true)) {
            return serverSocket?.localPort ?: error("SSH forwarder already running")
        }
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(host, sshPort)
        when (authType) {
            SshAuthType.Password -> ssh.authPassword(username, password.orEmpty())
            SshAuthType.PrivateKey -> {
                val key = OpenSSHKeyFile()
                key.init(StringReader(privateKeyPem.orEmpty()), null, null)
                ssh.authPublickey(username, key)
            }
        }
        val server = ServerSocket()
        server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        val localPort = server.localPort
        sshClient = ssh
        serverSocket = server
        val executor = Executors.newCachedThreadPool()
        workers = executor
        executor.execute { acceptLoop(ssh, server) }
        return localPort
    }

    actual fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { serverSocket?.close() }
        runCatching { workers?.shutdownNow() }
        runCatching { sshClient?.disconnect() }
        serverSocket = null
        workers = null
        sshClient = null
    }

    private fun acceptLoop(ssh: SSHClient, server: ServerSocket) {
        while (running.get() && !server.isClosed) {
            runCatching {
                val client = server.accept()
                workers?.execute { handleSocksClient(ssh, client) }
            }.onFailure {
                if (running.get() && !server.isClosed) {
                    ru.fromchat.logging.Logger.w("SshDynamicSocksForwarder", "Accept failed: ${it.message}")
                }
            }
        }
    }

    private fun handleSocksClient(ssh: SSHClient, client: Socket) {
        client.soTimeout = 30_000
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()
            val version = input.read()
            if (version != 0x05) return
            val methodCount = input.read()
            if (methodCount <= 0) return
            val methods = ByteArray(methodCount)
            if (input.read(methods) != methodCount) return
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()
            val header = ByteArray(4)
            if (input.read(header) < 4) return
            if (header[0] != 0x05.toByte() || header[1] != 0x01.toByte()) return
            val addressType = header[3].toInt()
            val (remoteHost, remotePort) = when (addressType) {
                0x01 -> readIpv4Target(input)
                0x03 -> readDomainTarget(input)
                0x04 -> readIpv6Target(input)
                else -> return
            }
            val direct = ssh.newDirectConnection(remoteHost, remotePort)
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()
            relayBidirectional(client, direct.inputStream, direct.outputStream)
        } catch (_: IOException) {
            // Client closed or handshake failed.
        } finally {
            runCatching { client.close() }
        }
    }

    private fun readIpv4Target(input: java.io.InputStream): Pair<String, Int> {
        val addr = ByteArray(4)
        if (input.read(addr) != 4) throw IOException("short ipv4")
        val portHi = input.read()
        val portLo = input.read()
        val port = (portHi shl 8) or portLo
        val host = InetAddress.getByAddress(addr).hostAddress ?: throw IOException("bad ipv4")
        return host to port
    }

    private fun readDomainTarget(input: java.io.InputStream): Pair<String, Int> {
        val len = input.read()
        if (len <= 0) throw IOException("short domain")
        val domain = ByteArray(len)
        if (input.read(domain) != len) throw IOException("short domain name")
        val portHi = input.read()
        val portLo = input.read()
        val port = (portHi shl 8) or portLo
        return String(domain, Charsets.US_ASCII) to port
    }

    private fun readIpv6Target(input: java.io.InputStream): Pair<String, Int> {
        val addr = ByteArray(16)
        if (input.read(addr) != 16) throw IOException("short ipv6")
        val portHi = input.read()
        val portLo = input.read()
        val port = (portHi shl 8) or portLo
        val host = InetAddress.getByAddress(addr).hostAddress ?: throw IOException("bad ipv6")
        return host to port
    }

    private fun relayBidirectional(
        client: Socket,
        remoteIn: java.io.InputStream,
        remoteOut: java.io.OutputStream,
    ) {
        val clientIn = client.getInputStream()
        val clientOut = client.getOutputStream()
        val t1 = Thread {
            runCatching { clientIn.copyTo(remoteOut) }
            runCatching { remoteOut.flush() }
            runCatching { client.shutdownOutput() }
        }
        val t2 = Thread {
            runCatching { remoteIn.copyTo(clientOut) }
            runCatching { clientOut.flush() }
            runCatching { client.close() }
        }
        t1.start()
        t2.start()
        t1.join()
        t2.join()
    }
}
