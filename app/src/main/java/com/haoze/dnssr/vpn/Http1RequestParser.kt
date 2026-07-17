package com.haoze.dnssr.vpn

import java.net.IDN
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale

sealed interface Http1ParseResult {
    data object NeedMoreData : Http1ParseResult
    data class Invalid(val reason: String) : Http1ParseResult
    data class Parsed(val request: Http1RequestHead) : Http1ParseResult
}

data class Http1RequestHead(
    val method: String,
    val authority: String,
    val headerByteCount: Int,
    val bodyFraming: Http1BodyFraming,
    val connectionClose: Boolean
)

sealed interface Http1BodyFraming {
    data object None : Http1BodyFraming
    data object Chunked : Http1BodyFraming
    data class ContentLength(val byteCount: Long) : Http1BodyFraming
}

object Http1RequestParser {
    const val MAX_HEADER_BYTES = 64 * 1024
    private val METHOD = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
    private val HEADER_NAME = METHOD
    private val DOMAIN_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun parse(buffer: ByteArray, length: Int = buffer.size): Http1ParseResult {
        if (length < 0 || length > buffer.size) return Http1ParseResult.Invalid("invalid buffer length")
        val headerEnd = findHeaderEnd(buffer, length)
        if (headerEnd < 0) {
            return if (length > MAX_HEADER_BYTES) {
                Http1ParseResult.Invalid("request headers exceed limit")
            } else {
                Http1ParseResult.NeedMoreData
            }
        }
        if (headerEnd > MAX_HEADER_BYTES) return Http1ParseResult.Invalid("request headers exceed limit")

        val text = String(buffer, 0, headerEnd - 4, StandardCharsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val requestLine = lines.firstOrNull() ?: return Http1ParseResult.Invalid("missing request line")
        val requestParts = requestLine.split(' ')
        if (requestParts.size != 3 || requestParts.any(String::isEmpty)) {
            return Http1ParseResult.Invalid("ambiguous request line")
        }
        val (method, target, version) = requestParts
        if (!METHOD.matches(method) || version != "HTTP/1.1") {
            return Http1ParseResult.Invalid("unsupported request line")
        }

        val headers = LinkedHashMap<String, MutableList<String>>()
        for (line in lines.drop(1)) {
            if (line.isEmpty() || line.startsWith(' ') || line.startsWith('\t')) {
                return Http1ParseResult.Invalid("invalid folded header")
            }
            val separator = line.indexOf(':')
            if (separator <= 0) return Http1ParseResult.Invalid("invalid header")
            val name = line.substring(0, separator)
            val value = line.substring(separator + 1).trim(' ', '\t')
            if (!HEADER_NAME.matches(name) || value.any(::isInvalidHeaderValueCharacter)) {
                return Http1ParseResult.Invalid("invalid header")
            }
            headers.getOrPut(name.lowercase(Locale.ROOT)) { mutableListOf() }.add(value)
        }

        val hostValues = headers["host"] ?: return Http1ParseResult.Invalid("missing Host")
        if (hostValues.size != 1) return Http1ParseResult.Invalid("multiple Host headers")
        val hostAuthority = parseAuthority(hostValues.single())
            ?: return Http1ParseResult.Invalid("invalid Host authority")
        val targetAuthority = authorityFromTarget(method, target)
            ?: return Http1ParseResult.Invalid("invalid request target")
        if (targetAuthority.authority != null && targetAuthority.authority != hostAuthority) {
            return Http1ParseResult.Invalid("request target conflicts with Host")
        }

        val transferEncoding = headers["transfer-encoding"]
        val contentLength = headers["content-length"]
        if (transferEncoding != null && contentLength != null) {
            return Http1ParseResult.Invalid("conflicting message framing")
        }
        val bodyFraming = when {
            transferEncoding != null -> parseTransferEncoding(transferEncoding)
                ?: return Http1ParseResult.Invalid("unsupported Transfer-Encoding")
            contentLength != null -> parseContentLength(contentLength)
                ?: return Http1ParseResult.Invalid("invalid Content-Length")
            else -> Http1BodyFraming.None
        }
        val connectionTokens = headers["connection"].orEmpty()
            .flatMap { it.split(',') }
            .map { it.trim().lowercase(Locale.ROOT) }

        return Http1ParseResult.Parsed(
            Http1RequestHead(
                method = method,
                authority = hostAuthority.host,
                headerByteCount = headerEnd,
                bodyFraming = bodyFraming,
                connectionClose = "close" in connectionTokens
            )
        )
    }

    fun normalizeAuthorityHost(value: String): String? = parseAuthority(value)?.host

    private fun findHeaderEnd(buffer: ByteArray, length: Int): Int {
        val limit = minOf(length, MAX_HEADER_BYTES + 4)
        for (index in 3 until limit) {
            if (buffer[index - 3] == CR && buffer[index - 2] == LF &&
                buffer[index - 1] == CR && buffer[index] == LF
            ) return index + 1
        }
        return -1
    }

    private fun authorityFromTarget(method: String, target: String): TargetAuthority? {
        if (target.isEmpty() || target.any { it <= ' ' || it == '\u007f' }) return null
        if (method.equals("CONNECT", ignoreCase = true)) return parseAuthority(target)?.let(::TargetAuthority)
        if (target.startsWith('/') || target == "*") return TargetAuthority(null)
        val uri = runCatching { URI(target) }.getOrNull() ?: return null
        if (!uri.isAbsolute || uri.rawUserInfo != null || uri.rawFragment != null) return null
        val rawAuthority = uri.rawAuthority ?: return null
        return parseAuthority(rawAuthority)?.let(::TargetAuthority)
    }

    private fun parseAuthority(value: String): NormalizedAuthority? {
        if (value.isEmpty() || value != value.trim() || value.contains('@') || value.contains(',')) return null
        val host: String
        val port: String?
        if (value.startsWith('[')) {
            val closing = value.indexOf(']')
            if (closing <= 1) return null
            host = value.substring(1, closing)
            port = when {
                closing == value.lastIndex -> null
                value.getOrNull(closing + 1) == ':' -> value.substring(closing + 2)
                else -> return null
            }
            val address = runCatching { InetAddress.getByName(host) }.getOrNull()
            if (address !is Inet6Address) return null
        } else {
            if (value.count { it == ':' } > 1) return null
            val separator = value.lastIndexOf(':')
            host = if (separator >= 0) value.substring(0, separator) else value
            port = if (separator >= 0) value.substring(separator + 1) else null
        }
        if (host.isEmpty()) return null
        val parsedPort = port?.toIntOrNull()
        if (port != null && (parsedPort == null || parsedPort < 1 || parsedPort > 65535)) return null
        val normalizedHost = normalizeHost(host) ?: return null
        return NormalizedAuthority(normalizedHost, parsedPort)
    }

    private fun normalizeHost(host: String): String? {
        val ipLiteral = host.all { it.isDigit() || it == '.' } || host.contains(':')
        if (ipLiteral) {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return null
            return address.hostAddress?.lowercase(Locale.ROOT) ?: return null
        }
        val ascii = runCatching { IDN.toASCII(host.trimEnd('.'), IDN.USE_STD3_ASCII_RULES) }
            .getOrNull()?.lowercase(Locale.ROOT) ?: return null
        if (ascii.isEmpty() || ascii.length > 253) return null
        if (ascii.split('.').any { it.length !in 1..63 || !DOMAIN_LABEL.matches(it) }) return null
        return ascii
    }

    private fun parseTransferEncoding(values: List<String>): Http1BodyFraming? {
        if (values.size != 1) return null
        val codings = values.single().split(',').map { it.trim().lowercase(Locale.ROOT) }
        return if (codings == listOf("chunked")) Http1BodyFraming.Chunked else null
    }

    private fun parseContentLength(values: List<String>): Http1BodyFraming? {
        if (values.size != 1 || !values.single().all(Char::isDigit)) return null
        val length = values.single().toLongOrNull() ?: return null
        return if (length == 0L) Http1BodyFraming.None else Http1BodyFraming.ContentLength(length)
    }

    private fun isInvalidHeaderValueCharacter(char: Char): Boolean =
        char == '\u007f' || char < ' ' && char != '\t'

    private data class NormalizedAuthority(val host: String, val port: Int?)
    private data class TargetAuthority(val authority: NormalizedAuthority?)

    private const val CR: Byte = 13
    private const val LF: Byte = 10
}
