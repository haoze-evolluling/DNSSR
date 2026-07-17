package com.haoze.dnssr.vpn

import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http2.DefaultHttp2HeadersDecoder
import io.netty.handler.codec.http2.DefaultHttp2HeadersEncoder
import io.netty.handler.codec.http2.Http2Headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Collections

class Http2FilteringRelay(
    private val scope: CoroutineScope,
    private val inspector: Http1RequestInspector
) {
    private val headersDecoder = DefaultHttp2HeadersDecoder(true)
    private val headersEncoder = DefaultHttp2HeadersEncoder()
    private val encoderLock = Any()
    @Volatile
    private var outboundMaxFrameSize = DEFAULT_MAX_FRAME_SIZE

    suspend fun relay(client: Socket, upstream: Socket, packageName: String) {
        val clientInput = client.getInputStream()
        val clientOutput = client.getOutputStream()
        val upstreamInput = upstream.getInputStream()
        val upstreamOutput = upstream.getOutputStream()
        val clientWriteLock = Any()
        val upstreamWriteLock = Any()
        val allowedStreams = Collections.synchronizedSet(mutableSetOf<Int>())
        val blockedStreams = Collections.synchronizedSet(mutableSetOf<Int>())

        val preface = clientInput.readExactly(CLIENT_PREFACE.size)
        if (!preface.contentEquals(CLIENT_PREFACE)) {
            inspector.logInvalid(packageName, null, HTTP_2)
            return
        }
        synchronized(upstreamWriteLock) {
            upstreamOutput.write(preface)
            upstreamOutput.flush()
        }

        val responseJob = scope.launch {
            try {
                while (true) {
                    val frame = readFrame(upstreamInput)
                    if (frame.type == TYPE_SETTINGS && frame.streamId == 0 && frame.flags and FLAG_ACK == 0) {
                        applyPeerSettings(frame.payload)
                    }
                    synchronized(clientWriteLock) {
                        writeFrame(clientOutput, frame)
                        clientOutput.flush()
                    }
                }
            } catch (_: Exception) {
                runCatching { client.shutdownOutput() }
            }
        }

        try {
            while (true) {
                val frame = readFrame(clientInput)
                when {
                    frame.type == TYPE_HEADERS -> handleHeaders(
                        firstFrame = frame,
                        input = clientInput,
                        clientOutput = clientOutput,
                        upstreamOutput = upstreamOutput,
                        packageName = packageName,
                        allowedStreams = allowedStreams,
                        blockedStreams = blockedStreams,
                        clientWriteLock = clientWriteLock,
                        upstreamWriteLock = upstreamWriteLock
                    )
                    frame.streamId != 0 && frame.streamId in blockedStreams -> Unit
                    else -> synchronized(upstreamWriteLock) {
                        writeFrame(upstreamOutput, frame)
                        upstreamOutput.flush()
                    }
                }
            }
        } catch (_: Exception) {
            runCatching { upstream.shutdownOutput() }
        } finally {
            responseJob.cancelAndJoin()
        }
    }

    private suspend fun handleHeaders(
        firstFrame: Frame,
        input: InputStream,
        clientOutput: OutputStream,
        upstreamOutput: OutputStream,
        packageName: String,
        allowedStreams: MutableSet<Int>,
        blockedStreams: MutableSet<Int>,
        clientWriteLock: Any,
        upstreamWriteLock: Any
    ) {
        if (firstFrame.streamId == 0) throw EOFException("HEADERS on stream zero")
        val headerBlock = ArrayList<Byte>()
        headerBlock.addAll(extractHeadersFragment(firstFrame).toList())
        var endHeaders = firstFrame.flags and FLAG_END_HEADERS != 0
        while (!endHeaders) {
            val continuation = readFrame(input)
            if (continuation.type != TYPE_CONTINUATION || continuation.streamId != firstFrame.streamId) {
                throw EOFException("invalid CONTINUATION sequence")
            }
            headerBlock.addAll(continuation.payload.toList())
            endHeaders = continuation.flags and FLAG_END_HEADERS != 0
        }

        val headers = headersDecoder.decodeHeaders(
            firstFrame.streamId,
            Unpooled.wrappedBuffer(headerBlock.toByteArray())
        )
        val initialHeaders = firstFrame.streamId !in allowedStreams && firstFrame.streamId !in blockedStreams
        if (initialHeaders) {
            val rawAuthority = headers.authority()?.toString()
            val authority = rawAuthority?.let(Http1RequestParser::normalizeAuthorityHost)
            if (authority == null) {
                inspector.logInvalid(packageName, rawAuthority, HTTP_2)
                blockedStreams += firstFrame.streamId
                sendReset(clientOutput, firstFrame.streamId, clientWriteLock)
                return
            }
            when (inspector.inspectAuthority(packageName, authority, HTTP_2)) {
                is HttpAuthorityInspectionResult.Forward -> allowedStreams += firstFrame.streamId
                is HttpAuthorityInspectionResult.Terminate -> {
                    blockedStreams += firstFrame.streamId
                    sendReset(clientOutput, firstFrame.streamId, clientWriteLock)
                    return
                }
            }
        }
        if (firstFrame.streamId in blockedStreams) return
        writeEncodedHeaders(
            upstreamOutput,
            firstFrame.streamId,
            headers,
            firstFrame.flags and FLAG_END_STREAM != 0,
            upstreamWriteLock
        )
    }

    private fun extractHeadersFragment(frame: Frame): ByteArray {
        var offset = 0
        var padding = 0
        if (frame.flags and FLAG_PADDED != 0) {
            if (frame.payload.isEmpty()) throw EOFException("missing HEADERS padding")
            padding = frame.payload[0].toInt() and 0xff
            offset++
        }
        if (frame.flags and FLAG_PRIORITY != 0) offset += 5
        val end = frame.payload.size - padding
        if (offset > end) throw EOFException("invalid HEADERS padding")
        return frame.payload.copyOfRange(offset, end)
    }

    private fun writeEncodedHeaders(
        output: OutputStream,
        streamId: Int,
        headers: Http2Headers,
        endStream: Boolean,
        writeLock: Any
    ) {
        val encoded = synchronized(encoderLock) {
            val buffer = ByteBufAllocator.DEFAULT.buffer()
            try {
                headersEncoder.encodeHeaders(streamId, headers, buffer)
                ByteArray(buffer.readableBytes()).also(buffer::readBytes)
            } finally {
                buffer.release()
            }
        }
        synchronized(writeLock) {
            var offset = 0
            var first = true
            do {
                val length = minOf(outboundMaxFrameSize, encoded.size - offset)
                val last = offset + length >= encoded.size
                val flags = (if (last) FLAG_END_HEADERS else 0) or
                    (if (first && endStream) FLAG_END_STREAM else 0)
                writeFrame(
                    output,
                    Frame(
                        type = if (first) TYPE_HEADERS else TYPE_CONTINUATION,
                        flags = flags,
                        streamId = streamId,
                        payload = encoded.copyOfRange(offset, offset + length)
                    )
                )
                offset += length
                first = false
            } while (offset < encoded.size || first)
            output.flush()
        }
    }

    private fun sendReset(output: OutputStream, streamId: Int, writeLock: Any) {
        val payload = byteArrayOf(0, 0, 0, ERROR_CANCEL.toByte())
        synchronized(writeLock) {
            writeFrame(output, Frame(TYPE_RST_STREAM, 0, streamId, payload))
            output.flush()
        }
    }

    private fun applyPeerSettings(payload: ByteArray) {
        if (payload.size % 6 != 0) return
        var offset = 0
        while (offset < payload.size) {
            val id = ((payload[offset].toInt() and 0xff) shl 8) or (payload[offset + 1].toInt() and 0xff)
            val value = readInt(payload, offset + 2).toLong() and 0xffffffffL
            when (id) {
                SETTINGS_HEADER_TABLE_SIZE -> synchronized(encoderLock) {
                    headersEncoder.configuration().maxHeaderTableSize(value)
                }
                SETTINGS_MAX_FRAME_SIZE -> if (value in MIN_MAX_FRAME_SIZE..MAX_MAX_FRAME_SIZE) {
                    outboundMaxFrameSize = value.toInt()
                }
            }
            offset += 6
        }
    }

    private fun readFrame(input: InputStream): Frame {
        val header = input.readExactly(FRAME_HEADER_SIZE)
        val length = ((header[0].toInt() and 0xff) shl 16) or
            ((header[1].toInt() and 0xff) shl 8) or
            (header[2].toInt() and 0xff)
        if (length > MAX_ACCEPTED_FRAME_SIZE) throw EOFException("HTTP/2 frame exceeds limit")
        return Frame(
            type = header[3].toInt() and 0xff,
            flags = header[4].toInt() and 0xff,
            streamId = readInt(header, 5) and 0x7fffffff,
            payload = input.readExactly(length)
        )
    }

    private fun writeFrame(output: OutputStream, frame: Frame) {
        val length = frame.payload.size
        output.write((length ushr 16) and 0xff)
        output.write((length ushr 8) and 0xff)
        output.write(length and 0xff)
        output.write(frame.type)
        output.write(frame.flags)
        output.write((frame.streamId ushr 24) and 0x7f)
        output.write((frame.streamId ushr 16) and 0xff)
        output.write((frame.streamId ushr 8) and 0xff)
        output.write(frame.streamId and 0xff)
        output.write(frame.payload)
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(bytes, offset, length - offset)
            if (read < 0) throw EOFException("unexpected HTTP/2 EOF")
            offset += read
        }
        return bytes
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private data class Frame(val type: Int, val flags: Int, val streamId: Int, val payload: ByteArray)

    private companion object {
        val CLIENT_PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        const val HTTP_2 = "HTTP/2"
        const val FRAME_HEADER_SIZE = 9
        const val DEFAULT_MAX_FRAME_SIZE = 16_384
        const val MAX_ACCEPTED_FRAME_SIZE = 16_777_215
        const val MIN_MAX_FRAME_SIZE = 16_384L
        const val MAX_MAX_FRAME_SIZE = 16_777_215L
        const val TYPE_HEADERS = 1
        const val TYPE_RST_STREAM = 3
        const val TYPE_SETTINGS = 4
        const val TYPE_CONTINUATION = 9
        const val FLAG_END_STREAM = 0x1
        const val FLAG_ACK = 0x1
        const val FLAG_END_HEADERS = 0x4
        const val FLAG_PADDED = 0x8
        const val FLAG_PRIORITY = 0x20
        const val ERROR_CANCEL = 0x8
        const val SETTINGS_HEADER_TABLE_SIZE = 0x1
        const val SETTINGS_MAX_FRAME_SIZE = 0x5
    }
}
