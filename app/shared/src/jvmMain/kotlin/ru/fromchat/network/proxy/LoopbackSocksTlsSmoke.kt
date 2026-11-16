package ru.fromchat.network.proxy

import ru.fromchat.security.tls.createFromChatTrustManager
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Debug-only in-process loopback SOCKS + TLS smoke (no SSH). Run via `./gradlew :app:shared:loopbackSocksTlsSmoke`.
 */
fun main() {
    val remoteHost = "api.fromchat.ru"
    val remotePort = 443
    val server = ServerSocket()
    server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
    val localPort = server.localPort
    val pool = Executors.newCachedThreadPool()
    pool.execute {
        runCatching {
            while (!server.isClosed) {
                val client = server.accept()
                client.tcpNoDelay = true
                pool.execute { handleLoopbackSocksClient(client, remoteHost, remotePort, pool) }
            }
        }
    }
    val result = runCatching {
        val proxy = InetSocketAddress("127.0.0.1", localPort)
        socks5TcpConnect(proxy, remoteHost, remotePort, connectTimeoutMs = 20_000, readTimeoutMs = 30_000)
            .use { plain ->
                plain.soTimeout = 30_000
                plain.tcpNoDelay = true
                httpsGetOverTlsStreams(
                    plainIn = plain.getInputStream(),
                    plainOut = plain.getOutputStream(),
                    host = remoteHost,
                    port = remotePort,
                    pathAndQuery = "/instance_id",
                    trustManager = createFromChatTrustManager(),
                )
            }
    }
    server.close()
    pool.shutdownNow()
    result.fold(
        onSuccess = { body ->
            sshProbeAgentLog(
                "H0",
                "LoopbackSocksTlsSmoke.kt:success",
                "smoke ok",
                """{"bodyLen":${body.length},"runId":"smoke-v1"}""",
            )
            println("SMOKE_OK bodyLen=${body.length}")
        },
        onFailure = { error ->
            sshProbeAgentLog(
                "H1",
                "LoopbackSocksTlsSmoke.kt:failed",
                error.message ?: error.javaClass.name,
                """{"exception":"${error.javaClass.name}"}""",
            )
            System.err.println("SMOKE_FAIL: ${error.message}")
            error.printStackTrace()
            kotlin.system.exitProcess(1)
        },
    )
}

private fun handleLoopbackSocksClient(
    client: Socket,
    remoteHost: String,
    remotePort: Int,
    pool: java.util.concurrent.ExecutorService,
) {
    var remote: Socket? = null
    try {
        val input = client.getInputStream()
        val output = client.getOutputStream()
        if (input.read() != 0x05) return
        val methodCount = input.read()
        if (methodCount <= 0) return
        input.read(ByteArray(methodCount))
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()
        val header = ByteArray(4)
        if (input.read(header) < 4) return
        if (header[1] != 0x01.toByte()) return
        val atyp = header[3].toInt()
        val (targetHost, targetPort) = when (atyp) {
            0x01 -> {
                val addr = ByteArray(4).also { if (input.read(it) < 4) return }
                val portHi = input.read()
                val portLo = input.read()
                val port = (portHi shl 8) or portLo
                val host = InetAddress.getByAddress(addr).hostAddress ?: return
                host to port
            }
            0x03 -> {
                val len = input.read()
                if (len <= 0) return
                val domain = ByteArray(len).also { if (input.read(it) < len) return }
                val portHi = input.read()
                val portLo = input.read()
                val port = (portHi shl 8) or portLo
                String(domain, Charsets.US_ASCII) to port
            }
            else -> return
        }
        remote = Socket().apply { connect(InetSocketAddress(targetHost, targetPort), 15_000) }
        output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        output.flush()
        relayLoopbackFlush(client, remote, pool)
    } catch (_: IOException) {
    } finally {
        runCatching { remote?.close() }
        runCatching { client.close() }
    }
}

private fun relayLoopbackFlush(
    client: Socket,
    remote: Socket,
    pool: java.util.concurrent.ExecutorService,
) {
    val clientSent = CountDownLatch(1)
    val clientIn = CountingInputStreamForSmoke(client.getInputStream()) { clientSent.countDown() }
    val clientOut = FlushingOutputStreamForSmoke(client.getOutputStream())
    val c2r = pool.submit { pumpFlush(clientIn, remote.getOutputStream()) }
    val r2c = pool.submit {
        clientSent.await(25, TimeUnit.SECONDS)
        pumpFlush(remote.getInputStream(), clientOut)
    }
    runCatching { c2r.get() }
    runCatching { r2c.get() }
}

private fun pumpFlush(input: java.io.InputStream, output: java.io.OutputStream) {
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

private class CountingInputStreamForSmoke(
    private val delegate: java.io.InputStream,
    private val onFirstBytes: () -> Unit,
) : java.io.InputStream() {
    private var signaled = false
    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) signal()
        return b
    }
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) signal()
        return n
    }
    private fun signal() {
        if (!signaled) {
            signaled = true
            onFirstBytes()
        }
    }
}

private class FlushingOutputStreamForSmoke(
    private val delegate: java.io.OutputStream,
) : java.io.OutputStream() {
    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        delegate.write(b, off, len)
        delegate.flush()
    }
    override fun flush() = delegate.flush()
    override fun close() = delegate.close()
}