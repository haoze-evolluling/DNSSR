package com.haoze.dnssr.vpn

sealed interface Http1InspectionResult {
    data object NeedMoreData : Http1InspectionResult
    data class Forward(
        val request: Http1RequestHead,
        val matchedAllowRule: String?
    ) : Http1InspectionResult
    data class Terminate(
        val request: Http1RequestHead?,
        val reason: String,
        val matchedBlockRule: String? = null
    ) : Http1InspectionResult
}

sealed interface HttpAuthorityInspectionResult {
    data class Forward(val matchedAllowRule: String?) : HttpAuthorityInspectionResult
    data class Terminate(val matchedBlockRule: String) : HttpAuthorityInspectionResult
}

class Http1RequestInspector(
    private val domainPolicy: HttpDomainPolicy,
    private val logger: HttpRequestLogger
) {
    suspend fun logInvalid(packageName: String, authority: String? = null) {
        logInvalid(packageName, authority, "HTTP/1.1")
    }

    suspend fun logInvalid(packageName: String, authority: String?, protocol: String) {
        logger.log(
            packageName = packageName,
            authority = authority,
            protocol = protocol,
            outcome = HttpRequestOutcome.INVALID
        )
    }

    suspend fun inspectAuthority(
        packageName: String,
        authority: String,
        protocol: String
    ): HttpAuthorityInspectionResult {
        return when (val decision = domainPolicy.evaluate(authority)) {
            is HttpDomainDecision.Allow -> {
                logger.log(
                    packageName,
                    authority,
                    protocol,
                    HttpRequestOutcome.ALLOWED,
                    decision.matchedRule
                )
                HttpAuthorityInspectionResult.Forward(decision.matchedRule)
            }
            is HttpDomainDecision.Block -> {
                logger.log(
                    packageName,
                    authority,
                    protocol,
                    HttpRequestOutcome.BLOCKED,
                    decision.matchedRule
                )
                HttpAuthorityInspectionResult.Terminate(decision.matchedRule)
            }
        }
    }

    suspend fun logDecryptionFailed(packageName: String, authority: String?) {
        logger.log(
            packageName = packageName,
            authority = authority,
            protocol = "HTTPS",
            outcome = HttpRequestOutcome.DECRYPTION_FAILED
        )
    }

    suspend fun logResourceBypass(packageName: String, protocol: String) {
        logger.log(
            packageName = packageName,
            authority = null,
            protocol = protocol,
            outcome = HttpRequestOutcome.RESOURCE_BYPASS
        )
    }

    suspend fun inspect(
        packageName: String,
        buffer: ByteArray,
        length: Int = buffer.size
    ): Http1InspectionResult {
        return when (val parsed = Http1RequestParser.parse(buffer, length)) {
            Http1ParseResult.NeedMoreData -> Http1InspectionResult.NeedMoreData
            is Http1ParseResult.Invalid -> {
                logInvalid(packageName)
                Http1InspectionResult.Terminate(request = null, reason = parsed.reason)
            }
            is Http1ParseResult.Parsed -> inspectParsed(packageName, parsed.request)
        }
    }

    private suspend fun inspectParsed(
        packageName: String,
        request: Http1RequestHead
    ): Http1InspectionResult {
        return when (val decision = inspectAuthority(packageName, request.authority, "HTTP/1.1")) {
            is HttpAuthorityInspectionResult.Forward ->
                Http1InspectionResult.Forward(request, decision.matchedAllowRule)
            is HttpAuthorityInspectionResult.Terminate ->
                Http1InspectionResult.Terminate(
                    request = request,
                    reason = "authority blocked by domain policy",
                    matchedBlockRule = decision.matchedBlockRule
                )
        }
    }
}
