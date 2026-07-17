package com.haoze.dnssr.vpn

import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class HttpsHttp1Relay(
    private val cleartextRelay: CleartextHttp1Relay,
    private val http2Relay: Http2FilteringRelay,
    private val inspector: Http1RequestInspector,
    private val onDecryptionFailure: (String) -> Unit
) {
    suspend fun relay(
        client: Socket,
        upstream: Socket,
        destination: InetSocketAddress,
        packageName: String
    ) {
        val defaultAuthority = destination.address.hostAddress ?: destination.hostString
        val keyManager = DynamicServerCertificateKeyManager(defaultAuthority)
        val serverContext = SSLContext.getInstance("TLS").apply {
            init(arrayOf(keyManager), null, SecureRandom())
        }
        val clientTls = serverContext.socketFactory
            .createSocket(client, defaultAuthority, destination.port, false) as SSLSocket
        clientTls.useClientMode = false
        clientTls.sslParameters = clientTls.sslParameters.apply {
            applicationProtocols = arrayOf(HTTP_2, HTTP_1_1)
        }
        val clientHandshake = runCatching { clientTls.startHandshake() }
        val authority = keyManager.selectedAuthority ?: defaultAuthority
        if (clientHandshake.isFailure) {
            inspector.logDecryptionFailed(packageName, authority)
            onDecryptionFailure(packageName)
            return
        }

        val negotiatedProtocol = clientTls.applicationProtocol.takeIf { it.isNotEmpty() } ?: HTTP_1_1
        val upstreamTls = (SSLSocketFactory.getDefault() as SSLSocketFactory)
            .createSocket(upstream, authority, destination.port, false) as SSLSocket
        upstreamTls.useClientMode = true
        upstreamTls.sslParameters = upstreamTls.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
            applicationProtocols = arrayOf(negotiatedProtocol)
        }
        val upstreamHandshake = runCatching { upstreamTls.startHandshake() }
        if (upstreamHandshake.isFailure) {
            inspector.logDecryptionFailed(packageName, authority)
            onDecryptionFailure(packageName)
            return
        }
        if (upstreamTls.applicationProtocol != negotiatedProtocol) {
            inspector.logDecryptionFailed(packageName, authority)
            onDecryptionFailure(packageName)
            return
        }
        if (negotiatedProtocol == HTTP_2) {
            http2Relay.relay(clientTls, upstreamTls, packageName)
        } else {
            cleartextRelay.relay(clientTls, upstreamTls, packageName)
        }
    }

    private companion object {
        const val HTTP_1_1 = "http/1.1"
        const val HTTP_2 = "h2"
    }
}
