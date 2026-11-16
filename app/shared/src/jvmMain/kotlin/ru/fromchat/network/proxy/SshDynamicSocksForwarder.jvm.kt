package ru.fromchat.network.proxy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import ru.fromchat.logging.Logger
import ru.fromchat.network.proxy.ssh.keyProviderFromPem
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local SOCKS5 listener that forwards flows through SSH direct-tcpip channels (ssh -D semantics).
 */
actual class SshDynamicSocksForwarder actual constructor(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val auth: Auth,
    private val password: String?,
    private val privateKeyPem: String?,
    private val privateKeyPassphrase: String?,
) {
    private var sshClient: SSHClient? = null
    private var serverSocket: ServerSocket? = null
    private var workers: ExecutorService? = null
    private val running = AtomicBoolean(false)
    private val channelLock = Any()
    actual fun start(): Int {
        if (!running.compareAndSet(false, true)) {
            return serverSocket?.localPort ?: error("SSH forwarder already running")
        }
        val ssh = SSHClient()
        ssh.addHostKeyVerifier(PromiscuousVerifier())
        ssh.connect(host, port)
        when (auth) {
            is PasswordAuth -> ssh.authPassword(username, password.orEmpty())
            is SshKeyAuth -> {
                val key = keyProviderFromPem(privateKeyPem.orEmpty(), privateKeyPassphrase)
                ssh.authPublickey(username, key)
            }
            else -> error("Unsupported SSH auth")
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
                    Logger.w("SshDynamicSocksForwarder", "Accept failed: ${it.message}")
                }
            }
        }
    }

    private fun handleSocksClient(ssh: SSHClient, client: Socket) {
        client.soTimeout = 30_000
        var direct: net.schmizz.sshj.connection.channel.direct.DirectConnection? = null
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
                else -> {
                    writeSocksReply(output, 0x08)
                    return
                }
            }
            direct = try {
                openDirectConnection(ssh, remoteHost, remotePort)
            } catch (_: Exception) {
                writeSocksReply(output, 0x05)
                return
            }
            direct.setAutoExpand(true)
            client.tcpNoDelay = true
            writeSocksReply(output, 0x00)
            relayBidirectional(client, direct, isLoopbackSocksClient(client))
        } catch (_: IOException) {
            // Client closed or handshake failed.
        } finally {
            runCatching { direct?.close() }
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

    private fun writeSocksReply(output: java.io.OutputStream, status: Int) {
        output.write(byteArrayOf(0x05, status.toByte(), 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
    }

    private fun openDirectConnection(
        ssh: SSHClient,
        remoteHost: String,
        remotePort: Int,
    ): net.schmizz.sshj.connection.channel.direct.DirectConnection =
        synchronized(channelLock) {
            try {
                ssh.newDirectConnection(remoteHost, remotePort)
            } catch (e: Exception) {
                Logger.w(
                    "SshDynamicSocksForwarder",
                    "direct-tcpip failed for $remoteHost:$remotePort: ${e.message}",
                )
                throw e
            }
        }

    private fun isLoopbackSocksClient(client: Socket): Boolean {
        client.inetAddress?.takeIf { it.isLoopbackAddress }?.let { return true }
        val remote = client.remoteSocketAddress as? InetSocketAddress ?: return false
        return remote.address.isLoopbackAddress
    }

    private fun relayBidirectional(
        client: Socket,
        direct: net.schmizz.sshj.connection.channel.direct.DirectConnection,
        loopback: Boolean,
    ) {
        if (loopback) {
            val clientSentLatch = CountDownLatch(1)
            val gatedClientIn = GatedOnFirstReadInputStream(client.getInputStream()) {
                clientSentLatch.countDown()
            }
            val clientOut = FlushingOutputStream(client.getOutputStream())
            val executor = workers ?: error("forwarder workers missing")
            val soc2chan = executor.submit {
                copyStreamFlush(gatedClientIn, direct.outputStream)
            }
            val chan2soc = executor.submit {
                clientSentLatch.await(25, TimeUnit.SECONDS)
                copyStreamFlush(direct.inputStream, clientOut)
            }
            runCatching { soc2chan.get() }
            runCatching { chan2soc.get() }
        } else {
            val soc2chan = StreamCopier(client.getInputStream(), direct.outputStream, direct.loggerFactory)
                .bufSize(direct.remoteMaxPacketSize.toInt())
                .spawnDaemon("soc2chan")
            val chan2soc = StreamCopier(
                direct.inputStream,
                client.getOutputStream(),
                direct.loggerFactory,
            )
                .bufSize(direct.localMaxPacketSize)
                .spawnDaemon("chan2soc")
            runCatching { soc2chan.await() }
            runCatching { chan2soc.await() }
        }
    }
}

private fun copyStreamFlush(input: java.io.InputStream, output: java.io.OutputStream) {
    val buf = ByteArray(8192)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        if (n > 0) {
            output.write(buf, 0, n)
            output.flush()
        }
    }
}

private class FlushingOutputStream(
    private val delegate: java.io.OutputStream,
) : java.io.OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        delegate.write(b, off, len)
        delegate.flush()
    }

    override fun flush() {
        delegate.flush()
    }

    override fun close() {
        delegate.close()
    }
}

private class GatedOnFirstReadInputStream(
    private val delegate: java.io.InputStream,
    private val onFirstBytes: () -> Unit,
) : java.io.InputStream() {
    private var signaledFirst = false

    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) signalOnce()
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) signalOnce()
        return n
    }

    private fun signalOnce() {
        if (!signaledFirst) {
            signaledFirst = true
            onFirstBytes()
        }
    }
}
