package com.haoze.dnssr.vpn

sealed interface HttpDomainDecision {
    val authority: String
    val matchedRule: String?

    data class Allow(
        override val authority: String,
        override val matchedRule: String? = null
    ) : HttpDomainDecision

    data class Block(
        override val authority: String,
        override val matchedRule: String
    ) : HttpDomainDecision
}

class HttpDomainPolicy(
    private val allowListManager: AllowListManager,
    private val blockListManager: BlockListManager
) {
    fun evaluate(authority: String): HttpDomainDecision {
        allowListManager.findMatch(authority)?.let { rule ->
            return HttpDomainDecision.Allow(authority, rule)
        }
        blockListManager.findMatch(authority)?.let { match ->
            return HttpDomainDecision.Block(authority, match.pattern)
        }
        return HttpDomainDecision.Allow(authority)
    }
}
