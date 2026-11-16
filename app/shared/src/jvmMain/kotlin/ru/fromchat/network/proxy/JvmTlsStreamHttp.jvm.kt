package ru.fromchat.network.proxy

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.X509TrustManager

internal fun httpsGetOverTlsStreams(
    plainIn: InputStream,
    plainOut: OutputStream,
    host: String,
    port: Int,
    pathAndQuery: String,
    trustManager: X509TrustManager,
): String {
    val engine = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }.createSSLEngine(host, port).apply {
        useClientMode = true
        sslParameters = sslParameters.apply {
            serverNames = listOf(SNIHostName(host))
            endpointIdentificationAlgorithm = "HTTPS"
        }
    }
    val netSize = engine.session.packetBufferSize
    val appSize = engine.session.applicationBufferSize
    val peerBuffer = ByteBuffer.allocate(netSize * 4)
    runTlsHandshake(engine, plainIn, plainOut, netSize, appSize, host, peerBuffer)
    val request = buildString {
        append("GET ").append(pathAndQuery).append(" HTTP/1.1\r\n")
        append("Host: ").append(host).append("\r\n")
        append("Connection: close\r\n")
        append("Accept: application/json\r\n")
        append("\r\n")
    }
    writePlaintext(engine, plainOut, request.toByteArray(Charsets.US_ASCII), netSize)
    val body = readPlaintextUntilClosed(engine, plainIn, appSize, peerBuffer, netSize)
    return String(body, Charsets.UTF_8)
}

internal data class TlsEngineIoContext(
    val engine: SSLEngine,
    val plainIn: InputStream,
    val plainOut: OutputStream,
    val peerBuffer: ByteBuffer,
    val netSize: Int,
    val appSize: Int,
)

internal fun openTlsEngineClientOverSocket(
    plain: Socket,
    host: String,
    port: Int,
    trustManager: X509TrustManager,
): TlsEngineIoContext {
    val engine = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trustManager), SecureRandom())
    }.createSSLEngine(host, port).apply {
        useClientMode = true
        sslParameters = sslParameters.apply {
            serverNames = listOf(SNIHostName(host))
            endpointIdentificationAlgorithm = "HTTPS"
        }
    }
    val netSize = engine.session.packetBufferSize
    val appSize = engine.session.applicationBufferSize
    val peerBuffer = ByteBuffer.allocate(netSize * 4)
    val plainIn = plain.getInputStream()
    val plainOut = plain.getOutputStream()
    runTlsHandshake(engine, plainIn, plainOut, netSize, appSize, host, peerBuffer)
    return TlsEngineIoContext(engine, plainIn, plainOut, peerBuffer, netSize, appSize)
}

internal fun writeTlsApplicationData(
    ctx: TlsEngineIoContext,
    bytes: ByteArray,
    offset: Int = 0,
    length: Int = bytes.size,
) {
    writePlaintext(ctx.engine, ctx.plainOut, bytes.copyOfRange(offset, offset + length), ctx.netSize)
}

internal fun readTlsApplicationData(ctx: TlsEngineIoContext, buf: ByteArray, off: Int, len: Int): Int {
    var appBuffer = ByteBuffer.allocate(ctx.appSize)
    var inboundEof = false
    while (true) {
        if (ctx.peerBuffer.position() == 0) {
            if (inboundEof) return -1
            val added = appendInboundCiphertext(ctx.peerBuffer, ctx.plainIn, ctx.netSize, allowEof = true)
            if (!added) inboundEof = true
            if (ctx.peerBuffer.position() == 0) {
                if (inboundEof) return -1
                continue
            }
        }
        ctx.peerBuffer.flip()
        if (!ctx.peerBuffer.hasRemaining()) {
            ctx.peerBuffer.clear()
            continue
        }
        appBuffer.clear()
        var result = ctx.engine.unwrap(ctx.peerBuffer, appBuffer)
        while (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            appBuffer = ByteBuffer.allocate(appBuffer.capacity() * 2)
            result = ctx.engine.unwrap(ctx.peerBuffer, appBuffer)
        }
        if (ctx.peerBuffer.hasRemaining()) {
            ctx.peerBuffer.compact()
        } else {
            ctx.peerBuffer.clear()
        }
        if (result.bytesProduced() > 0) {
            appBuffer.flip()
            val toRead = minOf(len, appBuffer.remaining())
            appBuffer.get(buf, off, toRead)
            return toRead
        }
        when (result.status) {
            SSLEngineResult.Status.CLOSED -> return -1
            SSLEngineResult.Status.BUFFER_UNDERFLOW -> continue
            else -> {
                if (inboundEof && ctx.peerBuffer.position() == 0) return -1
            }
        }
    }
}

private fun runTlsHandshake(
    engine: SSLEngine,
    plainIn: InputStream,
    plainOut: OutputStream,
    netSize: Int,
    appSize: Int,
    tlsHost: String,
    peerBuffer: ByteBuffer,
) {
    engine.beginHandshake()
    loggedFirstTlsInbound = false
    peerBuffer.clear()
    var netBuffer = ByteBuffer.allocate(netSize * 2)
    var appBuffer = ByteBuffer.allocate(appSize)
    var hs = engine.handshakeStatus
    while (true) {
        hs = engine.handshakeStatus
        when (hs) {
            SSLEngineResult.HandshakeStatus.FINISHED -> break
            SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING -> {
                if (!engine.session.cipherSuite.contains("NULL", ignoreCase = true)) {
                    break
                }
                appendTlsInbound(peerBuffer, plainIn, netSize)
                peerBuffer.flip()
                if (!peerBuffer.hasRemaining()) continue
                appBuffer.clear()
                val unwrap = engine.unwrap(peerBuffer, appBuffer)
                peerBuffer.compact()
                // #region agent log
                sshProbeAgentLog(
                    "H14",
                    "JvmTlsStreamHttp.jvm.kt:null-cipher-recovery",
                    "NOT_HANDSHAKING with NULL cipher",
                    """{"unwrapStatus":"${unwrap.status}","bytesProduced":${unwrap.bytesProduced()}}""",
                )
                // #endregion
                continue
            }
            SSLEngineResult.HandshakeStatus.NEED_WRAP -> {
                netBuffer.clear()
                var result = engine.wrap(ByteBuffer.allocate(0), netBuffer)
                while (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                    netBuffer = ByteBuffer.allocate(netBuffer.capacity() * 2)
                    result = engine.wrap(ByteBuffer.allocate(0), netBuffer)
                }
                if (result.status == SSLEngineResult.Status.CLOSED) {
                    throw IOException("TLS closed during handshake (wrap)")
                }
                netBuffer.flip()
                writeBuffer(plainOut, netBuffer)
                hs = result.handshakeStatus
            }
            SSLEngineResult.HandshakeStatus.NEED_UNWRAP -> {
                do {
                    if (peerBuffer.position() == 0) {
                        appendTlsInbound(peerBuffer, plainIn, netSize)
                    }
                    peerBuffer.flip()
                    if (!peerBuffer.hasRemaining()) {
                        peerBuffer.clear()
                        appendTlsInbound(peerBuffer, plainIn, netSize)
                        peerBuffer.flip()
                    }
                    appBuffer.clear()
                    var unwrap = engine.unwrap(peerBuffer, appBuffer)
                    while (unwrap.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
                        appBuffer = ByteBuffer.allocate(appBuffer.capacity() * 2)
                        unwrap = engine.unwrap(peerBuffer, appBuffer)
                    }
                    if (peerBuffer.hasRemaining()) {
                        peerBuffer.compact()
                    } else {
                        peerBuffer.clear()
                    }
                    if (unwrap.status == SSLEngineResult.Status.CLOSED) {
                        throw IOException("TLS closed during handshake")
                    }
                    hs = unwrap.handshakeStatus
                    if (unwrap.status == SSLEngineResult.Status.BUFFER_UNDERFLOW &&
                        hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP
                    ) {
                        appendTlsInbound(peerBuffer, plainIn, netSize)
                    } else {
                        break
                    }
                } while (hs == SSLEngineResult.HandshakeStatus.NEED_UNWRAP)
                if (hs != SSLEngineResult.HandshakeStatus.FINISHED) {
                    hs = engine.handshakeStatus
                }
            }
            SSLEngineResult.HandshakeStatus.NEED_TASK -> {
                var task = engine.delegatedTask
                while (task != null) {
                    task.run()
                    task = engine.delegatedTask
                }
                hs = engine.handshakeStatus
            }
            else -> throw IOException("Unexpected TLS handshake status: $hs")
        }
    }
    val cipher = engine.session.cipherSuite
    // #region agent log
    sshProbeAgentLog(
        "H2",
        "JvmTlsStreamHttp.jvm.kt:tls-handshake",
        "TLS handshake finished",
        """{"cipherSuite":"$cipher","hs":"$hs","host":"$tlsHost","peerRemainder":${peerBuffer.position()},"runId":"post-fix-sslengine-v7"}""",
    )
    // #endregion
    if (cipher.contains("NULL", ignoreCase = true)) {
        throw IOException("TLS not negotiated ($cipher)")
    }
    drainPeerCiphertext(engine, plainIn, peerBuffer, appBuffer, netSize, untilHandshakeComplete = true)
}

private var loggedFirstTlsInbound = false

private fun appendTlsInbound(peerBuffer: ByteBuffer, plainIn: InputStream, maxChunk: Int) {
    if (!appendInboundCiphertext(peerBuffer, plainIn, maxChunk, allowEof = false)) {
        throw EOFException("EOF during TLS handshake")
    }
    if (!loggedFirstTlsInbound && peerBuffer.position() > 0) {
        loggedFirstTlsInbound = true
        val snap = ByteArray(peerBuffer.position().coerceAtMost(12))
        peerBuffer.duplicate().apply { flip(); get(snap) }
        // #region agent log
        sshProbeAgentLog(
            "H3",
            "JvmTlsStreamHttp.jvm.kt:first-tls-inbound",
            "first ciphertext from socket",
            """{"bytes":${peerBuffer.position()},"hex":"${hexPrefix(snap)}"}""",
        )
        // #endregion
    }
}

private fun appendInboundCiphertext(
    peerBuffer: ByteBuffer,
    plainIn: InputStream,
    maxChunk: Int,
    allowEof: Boolean,
): Boolean {
    if (peerBuffer.position() > 0) {
        peerBuffer.compact()
    } else {
        peerBuffer.clear()
    }
    if (!peerBuffer.hasRemaining()) return false
    val before = peerBuffer.position()
    val read = fillFromSocketBlocking(plainIn, peerBuffer, maxChunk)
    if (!read && peerBuffer.position() == before) {
        return allowEof
    }
    return peerBuffer.position() > before
}

private fun drainPeerCiphertext(
    engine: SSLEngine,
    plainIn: InputStream,
    peerBuffer: ByteBuffer,
    appBuffer: ByteBuffer,
    netSize: Int,
    untilHandshakeComplete: Boolean,
    onPlaintext: (ByteArray) -> Unit = {},
) {
    var guard = 0
    while (guard++ < 512) {
        if (peerBuffer.position() == 0) {
            val added = appendInboundCiphertext(
                peerBuffer,
                plainIn,
                netSize,
                allowEof = !untilHandshakeComplete,
            )
            if (!added && peerBuffer.position() == 0) break
        }
        peerBuffer.flip()
        if (!peerBuffer.hasRemaining()) {
            peerBuffer.clear()
            break
        }
        appBuffer.clear()
        val result = engine.unwrap(peerBuffer, appBuffer)
        if (peerBuffer.hasRemaining()) peerBuffer.compact() else peerBuffer.clear()
        if (result.bytesProduced() > 0) {
            appBuffer.flip()
            val chunk = ByteArray(appBuffer.remaining())
            appBuffer.get(chunk)
            onPlaintext(chunk)
        }
        when (result.status) {
            SSLEngineResult.Status.BUFFER_UNDERFLOW -> continue
            SSLEngineResult.Status.CLOSED -> break
            else -> {
                if (untilHandshakeComplete &&
                    !engine.session.cipherSuite.contains("NULL", ignoreCase = true) &&
                    engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
                    peerBuffer.position() == 0
                ) {
                    break
                }
                if (!untilHandshakeComplete) break
            }
        }
    }
    if (peerBuffer.position() > 0) peerBuffer.compact()
}

private fun writePlaintext(engine: SSLEngine, plainOut: OutputStream, bytes: ByteArray, netSize: Int) {
    val app = ByteBuffer.wrap(bytes)
    var net = ByteBuffer.allocate(netSize)
    while (app.hasRemaining()) {
        net.clear()
        var result = engine.wrap(app, net)
        while (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            net = ByteBuffer.allocate(net.capacity() * 2)
            result = engine.wrap(app, net)
        }
        net.flip()
        writeBuffer(plainOut, net)
        if (result.status == SSLEngineResult.Status.CLOSED) {
            throw IOException("TLS closed while sending request")
        }
    }
}

private fun readPlaintextUntilClosed(
    engine: SSLEngine,
    plainIn: InputStream,
    appSize: Int,
    peerBuffer: ByteBuffer,
    netSize: Int,
): ByteArray {
    var appBuffer = ByteBuffer.allocate(appSize)
    val collected = ArrayList<Byte>(8192)
    var inboundEof = false
    while (true) {
        if (peerBuffer.position() == 0) {
            if (inboundEof) break
            val added = appendInboundCiphertext(peerBuffer, plainIn, netSize, allowEof = true)
            if (!added) inboundEof = true
            if (peerBuffer.position() == 0) {
                if (inboundEof) break
                continue
            }
        }
        peerBuffer.flip()
        if (!peerBuffer.hasRemaining()) {
            peerBuffer.clear()
            continue
        }
        appBuffer.clear()
        var result = engine.unwrap(peerBuffer, appBuffer)
        while (result.status == SSLEngineResult.Status.BUFFER_OVERFLOW) {
            appBuffer = ByteBuffer.allocate(appBuffer.capacity() * 2)
            result = engine.unwrap(peerBuffer, appBuffer)
        }
        if (peerBuffer.hasRemaining()) {
            peerBuffer.compact()
        } else {
            peerBuffer.clear()
        }
        if (result.bytesProduced() > 0) {
            appBuffer.flip()
            while (appBuffer.hasRemaining()) collected.add(appBuffer.get())
        }
        when (result.status) {
            SSLEngineResult.Status.CLOSED -> break
            SSLEngineResult.Status.BUFFER_UNDERFLOW -> continue
            else -> {
                if (inboundEof && peerBuffer.position() == 0) break
            }
        }
    }
    if (collected.isEmpty()) {
        throw IOException("HTTP/1.1 header parser received no bytes")
    }
    return collected.toByteArray()
}

private fun fillFromSocketBlocking(input: InputStream, buffer: ByteBuffer, maxChunk: Int): Boolean {
    if (!buffer.hasRemaining()) {
        buffer.compact()
    }
    if (!buffer.hasRemaining()) {
        return false
    }
    val tmp = ByteArray(buffer.remaining().coerceAtMost(maxChunk).coerceAtMost(8 * 1024))
    val read = input.read(tmp)
    if (read < 0) return false
    if (read > 0) buffer.put(tmp, 0, read)
    return read > 0
}

private fun writeBuffer(output: OutputStream, buffer: ByteBuffer) {
    if (!buffer.hasRemaining()) return
    val chunk = ByteArray(buffer.remaining())
    buffer.get(chunk)
    output.write(chunk)
    output.flush()
}