package ru.fromchat.network.proxy

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import javax.net.SocketFactory
import okhttp3.Dns

/**
 * Opens a TCP stream to [targetHost]:[targetPort] via SOCKS5, sending the hostname (ATYP 0x03)
 * so the proxy resolves and dials from its side — required for SSH dynamic forward (-D).
 */
internal fun socks5TcpConnect(
    proxy: InetSocketAddress,
    targetHost: String,
    targetPort: Int,
    connectTimeoutMs: Int = 12_000,
    readTimeoutMs: Int = 12_000,
): Socket {
    val host = targetHost.trim()
    require(host.isNotEmpty()) { "empty target host" }
    require(targetPort in 1..65535) { "bad target port" }
    val proxySocket = Socket()
    proxySocket.connect(proxy, connectTimeoutMs)
    proxySocket.soTimeout = readTimeoutMs
    val input = proxySocket.getInputStream()
    val output = proxySocket.getOutputStream()
    output.write(byteArrayOf(0x05, 0x01, 0x00))
    output.flush()
    val methodReply = ByteArray(2)
    readFully(input, methodReply)
    if (methodReply[0] != 0x05.toByte() || methodReply[1] != 0x00.toByte()) {
        proxySocket.close()
        throw IOException("SOCKS5 auth rejected")
    }
    val domain = host.toByteArray(Charsets.US_ASCII)
    if (domain.size > 255) {
        proxySocket.close()
        throw IOException("SOCKS5 domain too long")
    }
    val portHi = targetPort shr 8
    val portLo = targetPort and 0xFF
    val request = ByteArray(7 + domain.size)
    request[0] = 0x05
    request[1] = 0x01
    request[2] = 0x00
    request[3] = 0x03
    request[4] = domain.size.toByte()
    domain.copyInto(request, destinationOffset = 5)
    request[5 + domain.size] = portHi.toByte()
    request[6 + domain.size] = portLo.toByte()
    output.write(request)
    output.flush()
    val header = ByteArray(4)
    readFully(input, header)
    if (header[0] != 0x05.toByte()) {
        proxySocket.close()
        throw IOException("Bad SOCKS5 reply")
    }
    val status = header[1].toInt() and 0xFF
    if (status != 0) {
        proxySocket.close()
        throw IOException(socks5StatusMessage(status))
    }
    when (header[3].toInt() and 0xFF) {
        0x01 -> readFully(input, ByteArray(4 + 2))
        0x03 -> {
            val len = input.read()
            if (len < 0) throw IOException("short SOCKS5 bind addr")
            readFully(input, ByteArray(len + 2))
        }
        0x04 -> readFully(input, ByteArray(16 + 2))
        else -> throw IOException("Bad SOCKS5 bind ATYP")
    }
    return proxySocket
}

/** Keeps logical hostnames on [InetAddress] so SOCKS + TLS SNI use the API host, not only A-record bytes. */
internal object SocksPreservingDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        return Dns.SYSTEM.lookup(hostname).map { resolved ->
            InetAddress.getByAddress(hostname, resolved.address)
        }
    }
}

/**
 * Returns an unconnected [Socket] so OkHttp can call [Socket.connect]; the dial uses SOCKS5 remote DNS.
 */
internal class RemoteDnsSocksSocketFactory(
    private val proxy: InetSocketAddress,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : SocketFactory() {
    override fun createSocket(): Socket =
        LazyRemoteDnsSocksSocket(proxy, null, -1, connectTimeoutMs, readTimeoutMs)

    override fun createSocket(host: String, port: Int): Socket =
        LazyRemoteDnsSocksSocket(proxy, host, port, connectTimeoutMs, readTimeoutMs)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket(host, port)

    override fun createSocket(address: InetAddress, port: Int): Socket =
        createSocket(address.hostName ?: address.hostAddress.orEmpty(), port)

    override fun createSocket(address: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        createSocket(address.hostName ?: address.hostAddress.orEmpty(), port)
}

/**
 * Unconnected [Socket] for OkHttp: [connect] opens the SOCKS5 tunnel with remote DNS (ATYP 0x03).
 */
private class LazyRemoteDnsSocksSocket(
    private val proxy: InetSocketAddress,
    private val presetHost: String?,
    private val presetPort: Int,
    private val connectTimeoutMs: Int,
    private val readTimeoutMs: Int,
) : Socket() {
    private var delegate: Socket? = null
    private var pendingSoTimeout = readTimeoutMs

    private fun requireDelegate(): Socket =
        delegate ?: throw SocketException("Socket is not connected")

    @Synchronized
    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        if (delegate != null) return
        val inet = endpoint as? InetSocketAddress ?: throw SocketException("unsupported socket address")
        val dialHost = listOfNotNull(
            inet.hostString.takeIf { it.isNotBlank() },
            presetHost?.takeIf { it.isNotBlank() },
        ).firstOrNull { !isLiteralIpv4(it) }
            ?: presetHost?.takeIf { it.isNotBlank() }
            ?: inet.hostString
        require(dialHost.isNotBlank()) { "missing dial host" }
        val dialPort = if (presetPort > 0) presetPort else inet.port
        require(dialPort in 1..65535) { "bad dial port" }
        val effective = if (timeout > 0) timeout else connectTimeoutMs
        delegate = socks5TcpConnect(proxy, dialHost, dialPort, effective, pendingSoTimeout)
    }

    @Synchronized
    override fun connect(endpoint: SocketAddress?) {
        connect(endpoint, connectTimeoutMs)
    }

    override fun isConnected(): Boolean = delegate?.isConnected == true

    override fun isClosed(): Boolean = delegate?.isClosed == true

    override fun isBound(): Boolean = delegate?.isBound == true

    override fun getInputStream(): InputStream = requireDelegate().inputStream

    override fun getOutputStream(): OutputStream = requireDelegate().outputStream

    override fun close() {
        delegate?.close()
        delegate = null
    }

    override fun setSoTimeout(timeout: Int) {
        pendingSoTimeout = timeout
        delegate?.soTimeout = timeout
    }

    override fun getSoTimeout(): Int = delegate?.soTimeout ?: pendingSoTimeout

    override fun setTcpNoDelay(on: Boolean) {
        delegate?.tcpNoDelay = on
    }

    override fun shutdownInput() {
        requireDelegate().shutdownInput()
    }

    override fun shutdownOutput() {
        requireDelegate().shutdownOutput()
    }

    override fun getInetAddress(): InetAddress? = delegate?.inetAddress

    override fun getLocalAddress(): InetAddress = delegate?.localAddress ?: super.getLocalAddress()

    override fun getPort(): Int = if (presetPort > 0) presetPort else delegate?.port ?: -1

    override fun getLocalPort(): Int = delegate?.localPort ?: -1

    override fun getRemoteSocketAddress(): SocketAddress? = delegate?.remoteSocketAddress

    override fun getLocalSocketAddress(): SocketAddress? = delegate?.localSocketAddress
}

private fun readFully(input: java.io.InputStream, buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val read = input.read(buffer, offset, buffer.size - offset)
        if (read < 0) throw IOException("Unexpected end of SOCKS stream")
        offset += read
    }
}

private fun isLiteralIpv4(host: String): Boolean =
    host.matches(Regex("""^\d{1,3}(\.\d{1,3}){3}$"""))

private fun socks5StatusMessage(status: Int): String = when (status) {
    0x01 -> "SOCKS: general failure"
    0x02 -> "SOCKS: connection not allowed"
    0x03 -> "SOCKS: network unreachable"
    0x04 -> "SOCKS: host unreachable"
    0x05 -> "SOCKS: connection refused"
    0x06 -> "SOCKS: TTL expired"
    0x07 -> "SOCKS: command not supported"
    0x08 -> "SOCKS: address type not supported"
    else -> "SOCKS: error $status"
}