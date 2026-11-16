package ru.fromchat.network.proxy

import net.schmizz.sshj.connection.channel.direct.DirectConnection
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress

/**
 * Connected [Socket] facade over an SSH `direct-tcpip` channel (remote DNS already resolved by caller).
 */
internal class SshDirectTcpSocket(
    private val direct: DirectConnection,
) : Socket() {
    private var closed = false

    init {
        direct.setAutoExpand(true)
    }

    override fun getInputStream() = direct.inputStream

    override fun getOutputStream() = direct.outputStream

    override fun close() {
        if (closed) return
        closed = true
        runCatching { direct.close() }
    }

    override fun isConnected(): Boolean = !closed

    override fun isClosed(): Boolean = closed

    override fun isBound(): Boolean = true

    override fun connect(endpoint: SocketAddress?) = Unit

    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit

    override fun getInetAddress(): InetAddress = InetAddress.getLoopbackAddress()
}