package com.haoze.dnssr.vpn

import android.content.Context
import android.util.Log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.NetworkException
import org.chromium.net.UploadDataProvider
import org.chromium.net.UploadDataSink
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/**
 * 使用 HTTP/3 over QUIC 的 DNS-over-HTTPS 解析器。
 *
 * 应用 UID 已在 VPN Builder 中被排除，Cronet 发起的上游连接不会被本应用再次拦截。
 */
class Doh3Resolver(
    context: Context,
    dohUrl: String
) : DnsResolver {
    private val dohHttpUrl = dohUrl.toHttpUrl()
    private val callbackExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val uploadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engine: CronetEngine = CronetEngine.Builder(context.applicationContext)
        .enableHttp2(true)
        .enableQuic(true)
        .setUserAgent("DNSSR")
        .apply {
            if (dohHttpUrl.scheme == "https") {
                addQuicHint(dohHttpUrl.host, dohHttpUrl.port, dohHttpUrl.port)
            }
        }
        .build()

    override suspend fun resolve(query: ByteArray): ByteArray {
        return try {
            withTimeout(DNS_UPSTREAM_TIMEOUT_MS.toLong()) {
                resolveOnce(query)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException(
                "DoH3 请求超时（${DNS_UPSTREAM_TIMEOUT_MS}ms）；QUIC 使用 UDP/443，请检查该 UDP 端口是否被拦截",
                e
            )
        }
    }

    private suspend fun resolveOnce(query: ByteArray): ByteArray = suspendCancellableCoroutine { continuation ->
        val responseBytes = ByteArrayOutputStream()
        lateinit var request: UrlRequest

        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(
                request: UrlRequest,
                info: UrlResponseInfo,
                newLocationUrl: String
            ) {
                request.followRedirect()
            }

            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                request.read(ByteBuffer.allocateDirect(BUFFER_SIZE))
            }

            override fun onReadCompleted(
                request: UrlRequest,
                info: UrlResponseInfo,
                byteBuffer: ByteBuffer
            ) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                responseBytes.write(chunk)
                byteBuffer.clear()
                request.read(byteBuffer)
            }

            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                if (!continuation.isActive) return
                val protocol = info.negotiatedProtocol.orEmpty()
                when {
                    info.httpStatusCode !in 200..299 -> {
                        runCatching {
                            continuation.resumeWithException(IOException("DNS upstream HTTP ${info.httpStatusCode}"))
                        }
                    }
                    !protocol.isHttp3Protocol() -> {
                        val altSvc = info.headerValue("alt-svc") ?: "未提供"
                        Log.w(
                            TAG,
                            "DoH3 QUIC was not negotiated for ${dohHttpUrl.host}: " +
                                "protocol=${protocol.ifBlank { "unknown" }}, " +
                                "alt-svc=$altSvc"
                        )
                        runCatching {
                            continuation.resumeWithException(
                                IOException(
                                    "DoH3 协商失败：实际协议 ${protocol.ifBlank { "unknown" }}；" +
                                        "Alt-Svc：$altSvc。服务端未协商 HTTP/3，" +
                                        "或 UDP/443 被网络拦截"
                                )
                            )
                        }
                    }
                    else -> {
                        runCatching { continuation.resume(responseBytes.toByteArray()) }
                    }
                }
            }

            override fun onFailed(
                request: UrlRequest,
                info: UrlResponseInfo?,
                error: CronetException
            ) {
                val details = when (error) {
                    is NetworkException -> {
                        "errorCode=${error.errorCode}, internalErrorCode=${error.cronetInternalErrorCode}, " +
                            "immediatelyRetryable=${error.immediatelyRetryable()}"
                    }
                    else -> "message=${error.message.orEmpty()}"
                }
                Log.w(TAG, "DoH3 request failed for ${dohHttpUrl.host}: $details", error)
                if (continuation.isActive) {
                    runCatching {
                        continuation.resumeWithException(
                            IOException("DoH3 网络请求失败：$details", error)
                        )
                    }
                }
            }

            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                Log.w(
                    TAG,
                    "DoH3 request canceled for ${dohHttpUrl.host}: " +
                        "protocol=${info?.negotiatedProtocol.orEmpty().ifBlank { "unknown" }}"
                )
                if (continuation.isActive) {
                    runCatching { continuation.resumeWithException(IOException("DoH3 request canceled")) }
                }
            }
        }

        request = engine.newUrlRequestBuilder(dohHttpUrl.toString(), callback, callbackExecutor)
            .setHttpMethod("POST")
            .addHeader("Accept", DNS_MEDIA_TYPE)
            .addHeader("Content-Type", DNS_MEDIA_TYPE)
            .setUploadDataProvider(ByteArrayUploadDataProvider(query), uploadExecutor)
            .build()

        continuation.invokeOnCancellation { request.cancel() }
        request.start()
    }

    override fun close() {
        runCatching { engine.shutdown() }
        callbackExecutor.shutdownNow()
        uploadExecutor.shutdownNow()
    }

    private fun String.isHttp3Protocol(): Boolean {
        val normalized = lowercase()
        return normalized.startsWith("h3") || normalized.contains("quic")
    }

    private fun UrlResponseInfo.headerValue(name: String): String? {
        return allHeaders.entries.firstOrNull { (headerName, _) ->
            headerName.equals(name, ignoreCase = true)
        }?.value?.joinToString(", ")
    }

    private class ByteArrayUploadDataProvider(
        private val bytes: ByteArray
    ) : UploadDataProvider() {
        private var offset = 0

        override fun getLength(): Long = bytes.size.toLong()

        override fun read(uploadDataSink: UploadDataSink, byteBuffer: ByteBuffer) {
            val count = minOf(byteBuffer.remaining(), bytes.size - offset)
            byteBuffer.put(bytes, offset, count)
            offset += count
            uploadDataSink.onReadSucceeded(false)
        }

        override fun rewind(uploadDataSink: UploadDataSink) {
            offset = 0
            uploadDataSink.onRewindSucceeded()
        }
    }

    companion object {
        private const val TAG = "Doh3Resolver"
        private const val DNS_MEDIA_TYPE = "application/dns-message"
        private const val BUFFER_SIZE = 32 * 1024
    }
}
