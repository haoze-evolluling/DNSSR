package com.haoze.dnssr.vpn

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class LocalInspectionProxy(
    private val vpnService: DnsVpnService,
    private val scope: CoroutineScope,
    private val http1RequestInspector: Http1RequestInspector,
    onHttpsDecryptionFailure: (String) -> Unit,
    private val onSustainedResourceExhaustion: () -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val activeClients = mutableSetOf<Socket>()
    private val connectionOwners = mutableMapOf<Int, String>()
    private val cleartextRelay = CleartextHttp1Relay(scope, http1RequestInspector)
    private val http2Relay = Http2FilteringRelay(scope, http1RequestInspector)
    private val httpsRelay = HttpsHttp1Relay(
        cleartextRelay,
        http2Relay,
        http1RequestInspector,
        onHttpsDecryptionFailure
    )
    private val resourceExhaustionTimes = ArrayDeque<Long>()

    val port: Int get() = serverSocket?.localPort ?: 0

    fun start(): Boolean {
        if (serverSocket != null) return true
        val server = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName(IPV4_LOOPBACK), 0), BACKLOG)
            }
        }.onFailure { Log.e(TAG, "Unable to bind local inspection proxy", it) }
            .getOrNull() ?: return false
        serverSocket = server
        acceptJob = scope.launch {
            while (isActive) {
                val client = runCatching { server.accept() }.getOrElse { error ->
                    if (!server.isClosed) Log.w(TAG, "Inspection proxy accept failed", error)
                    break
                }
                synchronized(activeClients) { activeClients += client }
                val resourceBypass = synchronized(activeClients) {
                    activeClients.size > MAX_INSPECTED_CONNECTIONS
                }
                if (resourceBypass) recordResourceExhaustion()
                launch {
                    try {
                        handleClient(client, resourceBypass)
                    } finally {
                        synchronized(activeClients) { activeClients -= client }
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        synchronized(activeClients) {
            activeClients.forEach { runCatching { it.close() } }
            activeClients.clear()
        }
        synchronized(connectionOwners) { connectionOwners.clear() }
        acceptJob?.cancel()
        acceptJob = null
    }

    private suspend fun handleClient(client: Socket, resourceBypass: Boolean) {
        client.use {
            val packageName = claimConnectionOwner(client.port)
            val destination = runCatching {
                readSocksDestination(client.getInputStream(), client.getOutputStream())
            }
                .onFailure { Log.d(TAG, "Invalid local SOCKS5 request", it) }
                .getOrNull() ?: return
            val upstream = Socket()
            upstream.use {
                val anyLocalAddress = if (destination.address is Inet6Address) IPV6_ANY else IPV4_ANY
                upstream.bind(InetSocketAddress(InetAddress.getByName(anyLocalAddress), 0))
                if (!vpnService.protect(upstream)) {
                    writeSocksReply(client.getOutputStream(), SOCKS_GENERAL_FAILURE)
                    return
                }
                val connectResult = runCatching {
                    upstream.connect(destination, CONNECT_TIMEOUT_MS)
                }
                if (connectResult.isFailure) {
                    writeSocksReply(client.getOutputStream(), SOCKS_HOST_UNREACHABLE)
                    return
                }
                writeSocksReply(client.getOutputStream(), SOCKS_SUCCESS)
                if (resourceBypass) {
                    packageName?.let {
                        http1RequestInspector.logResourceBypass(
                            it,
                            if (destination.port == HTTPS_PORT) "HTTPS" else "HTTP/1.1"
                        )
                    }
                    relayBidirectionally(client, upstream)
                } else if (destination.port == HTTP_PORT && packageName != null) {
                    cleartextRelay.relay(client, upstream, packageName)
                } else if (destination.port == HTTPS_PORT && packageName != null) {
                    httpsRelay.relay(client, upstream, destination, packageName)
                } else {
                    relayBidirectionally(client, upstream)
                }
            }
        }
    }

    fun registerConnectionOwner(proxySourcePort: Int, packageName: String) {
        synchronized(connectionOwners) { connectionOwners[proxySourcePort] = packageName }
    }

    private suspend fun claimConnectionOwner(proxySourcePort: Int): String? {
        repeat(OWNER_LOOKUP_ATTEMPTS) {
            synchronized(connectionOwners) { connectionOwners.remove(proxySourcePort) }?.let { return it }
            delay(OWNER_LOOKUP_DELAY_MS)
        }
        return null
    }

    private fun recordResourceExhaustion() {
        val now = System.currentTimeMillis()
        val sustained = synchronized(resourceExhaustionTimes) {
            resourceExhaustionTimes.addLast(now)
            while (resourceExhaustionTimes.firstOrNull()?.let { now - it > RESOURCE_WINDOW_MS } == true) {
                resourceExhaustionTimes.removeFirst()
            }
            resourceExhaustionTimes.size >= RESOURCE_FAILURE_THRESHOLD
        }
        if (sustained) onSustainedResourceExhaustion()
    }

    private suspend fun relayBidirectionally(client: Socket, upstream: Socket) {
        val upstreamToClient = scope.launch {
            runCatching { upstream.getInputStream().copyTo(client.getOutputStream(), COPY_BUFFER_SIZE) }
            runCatching { client.shutdownOutput() }
        }
        runCatching { client.getInputStream().copyTo(upstream.getOutputStream(), COPY_BUFFER_SIZE) }
        runCatching { upstream.shutdownOutput() }
        listOf(upstreamToClient).joinAll()
    }

    private fun readSocksDestination(input: InputStream, output: OutputStream): InetSocketAddress {
        val greeting = input.readExactly(2)
        if (greeting[0].toInt() != SOCKS_VERSION) throw EOFException("unsupported SOCKS version")
        val methods = input.readExactly(greeting[1].toInt() and 0xff)
        if (SOCKS_NO_AUTH.toByte() !in methods) throw EOFException("SOCKS authentication required")
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), SOCKS_NO_AUTH.toByte()))
        output.flush()

        val request = input.readExactly(4)
        if (request[0].toInt() != SOCKS_VERSION || request[1].toInt() != SOCKS_CONNECT) {
            throw EOFException("unsupported SOCKS command")
        }
        val address = when (request[3].toInt() and 0xff) {
            SOCKS_IPV4 -> InetAddress.getByAddress(input.readExactly(4))
            SOCKS_IPV6 -> InetAddress.getByAddress(input.readExactly(16))
            else -> throw EOFException("unsupported SOCKS address type")
        }
        val portBytes = input.readExactly(2)
        val port = ((portBytes[0].toInt() and 0xff) shl 8) or
            (portBytes[1].toInt() and 0xff)
        if (port == 0) throw EOFException("invalid destination port")
        return InetSocketAddress(address, port)
    }

    private fun writeSocksReply(output: OutputStream, status: Int) {
        output.write(
            byteArrayOf(
                SOCKS_VERSION.toByte(), status.toByte(), 0, SOCKS_IPV4.toByte(),
                0, 0, 0, 0, 0, 0
            )
        )
        output.flush()
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        val result = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(result, offset, length - offset)
            if (read < 0) throw EOFException("unexpected end of SOCKS request")
            offset += read
        }
        return result
    }

    private companion object {
        const val TAG = "LocalInspectionProxy"
        const val IPV4_LOOPBACK = "127.0.0.1"
        const val IPV4_ANY = "0.0.0.0"
        const val IPV6_ANY = "::"
        const val BACKLOG = 128
        const val CONNECT_TIMEOUT_MS = 10_000
        const val COPY_BUFFER_SIZE = 16 * 1024
        const val HTTP_PORT = 80
        const val HTTPS_PORT = 443
        const val OWNER_LOOKUP_ATTEMPTS = 100
        const val OWNER_LOOKUP_DELAY_MS = 10L
        const val MAX_INSPECTED_CONNECTIONS = 256
        const val RESOURCE_FAILURE_THRESHOLD = 20
        const val RESOURCE_WINDOW_MS = 60_000L
        const val SOCKS_VERSION = 5
        const val SOCKS_CONNECT = 1
        const val SOCKS_NO_AUTH = 0
        const val SOCKS_IPV4 = 1
        const val SOCKS_IPV6 = 4
        const val SOCKS_SUCCESS = 0
        const val SOCKS_GENERAL_FAILURE = 1
        const val SOCKS_HOST_UNREACHABLE = 4
    }
}
