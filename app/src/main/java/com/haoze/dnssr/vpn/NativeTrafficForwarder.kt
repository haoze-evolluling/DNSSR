package com.haoze.dnssr.vpn

import android.util.Log

object NativeTrafficForwarder {
    private const val TAG = "NativeTrafficForwarder"
    private val libraryLoaded = runCatching {
        System.loadLibrary("dnssr_traffic_forwarder")
    }.onFailure { Log.e(TAG, "Unable to load native traffic forwarder", it) }.isSuccess

    fun start(
        tunFileDescriptor: Int,
        vpnService: DnsVpnService,
        localProxyPort: Int
    ): Boolean = libraryLoaded && nativeStart(tunFileDescriptor, vpnService, localProxyPort)

    fun forward(packet: ByteArray, length: Int): ForwardResult {
        if (!libraryLoaded) return ForwardResult.FAILED
        return when (nativeForward(packet, length)) {
            1 -> ForwardResult.FORWARDED
            0 -> ForwardResult.REJECTED
            else -> ForwardResult.FAILED
        }
    }

    fun stop() {
        if (libraryLoaded) nativeStop()
    }

    private external fun nativeStart(
        tunFileDescriptor: Int,
        vpnService: DnsVpnService,
        localProxyPort: Int
    ): Boolean
    private external fun nativeForward(packet: ByteArray, length: Int): Int
    private external fun nativeStop()
}

enum class ForwardResult {
    FORWARDED,
    REJECTED,
    FAILED
}
