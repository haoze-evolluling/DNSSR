package com.haoze.dnssr.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

class CleartextHttp1Relay(
    private val scope: CoroutineScope,
    private val inspector: Http1RequestInspector
) {
    suspend fun relay(client: Socket, upstream: Socket, packageName: String) {
        val responseJob = scope.launch {
            runCatching { upstream.getInputStream().copyTo(client.getOutputStream(), COPY_BUFFER_SIZE) }
            runCatching { client.shutdownOutput() }
        }
        var terminate = false
        try {
            val input = BufferedInputStream(client.getInputStream(), COPY_BUFFER_SIZE)
            val output = BufferedOutputStream(upstream.getOutputStream(), COPY_BUFFER_SIZE)
            while (!terminate) {
                val header = try {
                    readRequestHeader(input) ?: break
                } catch (_: Exception) {
                    inspector.logInvalid(packageName)
                    terminate = true
                    break
                }
                when (val result = inspector.inspect(packageName, header)) {
                    Http1InspectionResult.NeedMoreData -> {
                        inspector.logInvalid(packageName)
                        terminate = true
                    }
                    is Http1InspectionResult.Terminate -> terminate = true
                    is Http1InspectionResult.Forward -> {
                        output.write(header)
                        val bodyValid = runCatching {
                            forwardBody(input, output, result.request.bodyFraming)
                        }.isSuccess
                        if (!bodyValid) {
                            inspector.logInvalid(packageName, result.request.authority)
                            terminate = true
                        } else {
                            output.flush()
                        }
                    }
                }
            }
            if (terminate) {
                runCatching { client.close() }
                runCatching { upstream.close() }
            } else {
                runCatching { upstream.shutdownOutput() }
            }
        } finally {
            if (terminate) responseJob.cancelAndJoin() else responseJob.join()
        }
    }

    private fun readRequestHeader(input: InputStream): ByteArray? {
        val header = ArrayList<Byte>(1024)
        var matched = 0
        while (header.size <= Http1RequestParser.MAX_HEADER_BYTES) {
            val value = input.read()
            if (value < 0) {
                if (header.isEmpty()) return null
                throw EOFException("partial HTTP request header")
            }
            val byte = value.toByte()
            header += byte
            matched = when {
                matched == 0 && byte == CR -> 1
                matched == 1 && byte == LF -> 2
                matched == 2 && byte == CR -> 3
                matched == 3 && byte == LF -> return header.toByteArray()
                byte == CR -> 1
                else -> 0
            }
        }
        return header.toByteArray()
    }

    private fun forwardBody(input: InputStream, output: OutputStream, framing: Http1BodyFraming) {
        when (framing) {
            Http1BodyFraming.None -> Unit
            is Http1BodyFraming.ContentLength -> copyExactly(input, output, framing.byteCount)
            Http1BodyFraming.Chunked -> forwardChunkedBody(input, output)
        }
    }

    private fun forwardChunkedBody(input: InputStream, output: OutputStream) {
        while (true) {
            val sizeLine = readCrlfLine(input, MAX_CHUNK_LINE_BYTES)
            val sizeText = String(sizeLine, 0, sizeLine.size - 2, StandardCharsets.US_ASCII)
                .substringBefore(';')
                .trim()
            if (sizeText.isEmpty() || !sizeText.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                throw EOFException("invalid chunk size")
            }
            val size = sizeText.toLongOrNull(16) ?: throw EOFException("chunk size overflow")
            output.write(sizeLine)
            if (size == 0L) {
                while (true) {
                    val trailer = readCrlfLine(input, MAX_TRAILER_LINE_BYTES)
                    output.write(trailer)
                    if (trailer.size == 2) return
                    if (trailer.indexOf(':'.code.toByte()) <= 0) throw EOFException("invalid trailer")
                }
            }
            copyExactly(input, output, size)
            val ending = input.readExactly(2)
            if (ending[0] != CR || ending[1] != LF) throw EOFException("invalid chunk ending")
            output.write(ending)
        }
    }

    private fun readCrlfLine(input: InputStream, limit: Int): ByteArray {
        val line = ArrayList<Byte>()
        var previous = 0.toByte()
        while (line.size <= limit) {
            val value = input.read()
            if (value < 0) throw EOFException("partial HTTP line")
            val byte = value.toByte()
            line += byte
            if (previous == CR && byte == LF) return line.toByteArray()
            previous = byte
        }
        throw EOFException("HTTP line exceeds limit")
    }

    private fun copyExactly(input: InputStream, output: OutputStream, byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("partial HTTP request body")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.readExactly(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(bytes, offset, length - offset)
            if (read < 0) throw EOFException("partial HTTP framing")
            offset += read
        }
        return bytes
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 16 * 1024
        const val MAX_CHUNK_LINE_BYTES = 8 * 1024
        const val MAX_TRAILER_LINE_BYTES = 16 * 1024
        const val CR: Byte = 13
        const val LF: Byte = 10
    }
}
