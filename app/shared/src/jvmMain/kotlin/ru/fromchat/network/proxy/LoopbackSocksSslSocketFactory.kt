package ru.fromchat.network.proxy

import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * OkHttp/JDK [SSLSocket] on loopback SSH-SOCKS can negotiate NULL ciphers; use [SSLEngine] instead.
 */
internal class LoopbackSocksSslSocketFactory(
    private val delegate: SSLSocketFactory,
    private val trustManager: X509TrustManager,
) : SSLSocketFactory() {

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        if (s.connectedViaLoopbackSocks()) {
            return EngineTlsSocket(s, host, port, trustManager, autoClose)
        }
        return delegate.createSocket(s, host, port, autoClose)
    }

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(host: String?, port: Int): Socket = delegate.createSocket(host, port)

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: java.net.InetAddress?,
        localPort: Int,
    ): Socket = delegate.createSocket(host, port, localHost, localPort)

    override fun createSocket(address: java.net.InetAddress?, port: Int): Socket =
        delegate.createSocket(address, port)

    override fun createSocket(
        address: java.net.InetAddress?,
        port: Int,
        localAddress: java.net.InetAddress?,
        localPort: Int,
    ): Socket = delegate.createSocket(address, port, localAddress, localPort)
}

internal fun Socket.connectedViaLoopbackSocks(): Boolean {
    if (!isConnected) return false
    val remote = remoteSocketAddress as? InetSocketAddress ?: return false
    return remote.address.isLoopbackAddress
}

private class EngineTlsSocket(
    private val plain: Socket,
    private val peerHost: String,
    private val peerPort: Int,
    private val trustManager: X509TrustManager,
    private val autoClose: Boolean,
) : SSLSocket() {
    private var tls: TlsEngineIoContext? = null

    @Synchronized
    private fun context(): TlsEngineIoContext {
        tls?.let { return it }
        val ctx = openTlsEngineClientOverSocket(plain, peerHost, peerPort, trustManager)
        tls = ctx
        return ctx
    }

    @Synchronized
    override fun startHandshake() {
        context()
    }

    override fun getInputStream(): InputStream = TlsEngineInputStream(context())

    override fun getOutputStream(): OutputStream = TlsEngineOutputStream(context())

    override fun getSession() = context().engine.session

    override fun getEnabledCipherSuites(): Array<String> = context().engine.enabledCipherSuites

    override fun setEnabledCipherSuites(suites: Array<String>) {
        context().engine.enabledCipherSuites = suites
    }

    override fun getSupportedCipherSuites(): Array<String> = context().engine.supportedCipherSuites

    override fun getSupportedProtocols(): Array<String> = context().engine.supportedProtocols

    override fun getEnabledProtocols(): Array<String> = context().engine.enabledProtocols

    override fun setEnabledProtocols(protocols: Array<String>) {
        context().engine.enabledProtocols = protocols
    }

    override fun getNeedClientAuth(): Boolean = false

    override fun setNeedClientAuth(need: Boolean) = Unit

    override fun getWantClientAuth(): Boolean = false

    override fun setWantClientAuth(want: Boolean) = Unit

    override fun getUseClientMode(): Boolean = true

    override fun setUseClientMode(mode: Boolean) = Unit

    override fun getEnableSessionCreation(): Boolean = true

    override fun setEnableSessionCreation(flag: Boolean) = Unit

    override fun addHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener) = Unit

    override fun removeHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener) = Unit

    override fun close() {
        try {
            plain.close()
        } finally {
            if (autoClose) {
                runCatching { super.close() }
            }
        }
    }

    override fun isClosed(): Boolean = plain.isClosed

    override fun isConnected(): Boolean = plain.isConnected

    override fun getInetAddress(): java.net.InetAddress? = plain.inetAddress

    override fun getLocalAddress(): java.net.InetAddress = plain.localAddress

    override fun getPort(): Int = peerPort

    override fun getLocalPort(): Int = plain.localPort

    override fun getRemoteSocketAddress(): java.net.SocketAddress? = plain.remoteSocketAddress

    override fun getLocalSocketAddress(): java.net.SocketAddress? = plain.localSocketAddress

    override fun setSoTimeout(timeout: Int) {
        plain.soTimeout = timeout
    }

    override fun getSoTimeout(): Int = plain.soTimeout

    override fun setTcpNoDelay(on: Boolean) {
        plain.tcpNoDelay = on
    }
}

private class TlsEngineInputStream(
    private val ctx: TlsEngineIoContext,
) : InputStream() {
    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        readTlsApplicationData(ctx, b, off, len)
}

private class TlsEngineOutputStream(
    private val ctx: TlsEngineIoContext,
) : OutputStream() {
    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        writeTlsApplicationData(ctx, b, off, len)
    }

    override fun flush() {
        ctx.plainOut.flush()
    }
}