package com.haoze.dnssr.vpn

import java.net.IDN
import java.net.InetAddress

/** Parses the DNS subset of AdGuard, hosts, and domains-only rule lists. */
object AdGuardRuleParser {

    data class ParsedRule(val pattern: String, val rawLine: String)

    data class CategorizedRules(
        val blockRules: List<ParsedRule> = emptyList(),
        val allowRules: List<ParsedRule> = emptyList(),
        val rewriteRules: List<RewriteRule> = emptyList(),
        val duplicateCount: Int = 0,
        val invalidCount: Int = 0,
        val unsupportedCount: Int = 0,
        val ignoredCount: Int = 0,
        val totalLines: Int = 0
    ) {
        val size: Int get() = blockRules.size + allowRules.size + rewriteRules.size
        val skippedCount: Int get() = invalidCount + unsupportedCount
        fun isEmpty(): Boolean = blockRules.isEmpty() && allowRules.isEmpty() && rewriteRules.isEmpty()
    }

    private val SINKHOLE_ADDRESSES = setOf("0", "0.0.0.0", "127.0.0.1", "::", "::1")
    private val DOMAIN_LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

    fun parseLine(line: String): ParsedRule? = parseSingle(line, allowRule = false)

    /** Manual allow entry accepts either an exception rule or a plain domain. */
    fun parseAllowLine(line: String): ParsedRule? = parseSingle(line, allowRule = true)

    fun parseAll(text: String): List<ParsedRule> = parseCategorized(text).blockRules

    fun parseAllowAll(text: String): List<ParsedRule> = text.lineSequence()
        .mapNotNull(::parseAllowLine)
        .distinctBy { it.pattern }
        .toList()

    fun parseCategorized(text: String): CategorizedRules {
        val blockRules = LinkedHashMap<String, ParsedRule>()
        val allowRules = LinkedHashMap<String, ParsedRule>()
        var duplicates = 0
        var invalid = 0
        var unsupported = 0
        var ignored = 0
        var total = 0

        text.lineSequence().forEach { originalLine ->
            total++
            val line = originalLine.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith("!") || line.startsWith("#") ||
                (line.startsWith("[") && line.endsWith("]"))
            ) {
                ignored++
                return@forEach
            }

            val hosts = parseHostsLine(line)
            if (hosts != null) {
                if (hosts.isEmpty()) {
                    unsupported++
                } else {
                    hosts.forEach { rule ->
                        if (blockRules.putIfAbsent(rule.pattern, rule) != null) duplicates++
                    }
                }
                return@forEach
            }

            val allow = line.startsWith("@@")
            when (val result = parseAdblockOrDomain(line, allow)) {
                is LineResult.Valid -> {
                    val target = if (allow) allowRules else blockRules
                    if (target.putIfAbsent(result.rule.pattern, result.rule) != null) duplicates++
                }
                LineResult.Invalid -> invalid++
                LineResult.Unsupported -> unsupported++
            }
        }

        return CategorizedRules(
            blockRules = blockRules.values.toList(),
            allowRules = allowRules.values.toList(),
            duplicateCount = duplicates,
            invalidCount = invalid,
            unsupportedCount = unsupported,
            ignoredCount = ignored,
            totalLines = total
        )
    }

    private fun parseSingle(line: String, allowRule: Boolean): ParsedRule? {
        val trimmed = line.trim().trimStart('\uFEFF')
        if (!allowRule && trimmed.startsWith("@@")) return null
        if (!allowRule) parseHostsLine(trimmed)?.firstOrNull()?.let { return it }
        val result = parseAdblockOrDomain(trimmed, trimmed.startsWith("@@"))
        return (result as? LineResult.Valid)?.rule
    }

    private fun parseHostsLine(line: String): List<ParsedRule>? {
        val content = line.substringBefore('#').trim()
        val fields = content.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (fields.size < 2 || !looksLikeIp(fields.first())) return null
        if (fields.first().lowercase() !in SINKHOLE_ADDRESSES) return emptyList()
        return fields.drop(1).mapNotNull { host ->
            normalizeDomain(host)?.let { ParsedRule(it, line) }
        }
    }

    private fun looksLikeIp(value: String): Boolean {
        if (value == "0") return true
        if (!value.contains(':') && !value.matches(Regex("^[0-9.]+$"))) return false
        return try {
            InetAddress.getByName(value)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseAdblockOrDomain(line: String, allow: Boolean): LineResult {
        var value = line.substringBefore('#').trim()
        if (value.isEmpty()) return LineResult.Invalid
        if (allow) value = value.removePrefix("@@")
        if (value.startsWith("/") || value.contains("*") || value.contains("##") || value.contains("#@#")) {
            return LineResult.Unsupported
        }

        val modifierIndex = value.indexOf('$')
        if (modifierIndex >= 0) {
            val modifiers = value.substring(modifierIndex + 1)
                .split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (modifiers.any { it != "important" }) return LineResult.Unsupported
            value = value.substring(0, modifierIndex)
        }

        value = when {
            value.startsWith("||") -> value.removePrefix("||").trimEnd('^')
            value.startsWith("|") || value.endsWith("|") -> return LineResult.Unsupported
            else -> value.trimEnd('^')
        }
        val domain = normalizeDomain(value) ?: return LineResult.Invalid
        return LineResult.Valid(ParsedRule(domain, line))
    }

    private fun normalizeDomain(value: String): String? {
        val candidate = value.trim().trimEnd('.')
        if (candidate.isEmpty() || candidate.contains('/') || candidate.contains(':') || candidate.contains(' ')) {
            return null
        }
        val ascii = try {
            IDN.toASCII(candidate, IDN.USE_STD3_ASCII_RULES).lowercase()
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (ascii.length > 253 || !ascii.contains('.') || looksLikeIp(ascii)) return null
        val labels = ascii.split('.')
        if (labels.any { it.length !in 1..63 || !DOMAIN_LABEL.matches(it) }) return null
        return ascii
    }

    fun normalizeDomainForRewrite(value: String): String? {
        val candidate = value.trim().trimEnd('.')
        if (candidate.isEmpty()) return null

        // Rewrite sources may be literal IPv4/IPv6 addresses as well as host names.
        // Keep the broader domain-rule validator unchanged so IPs are not accepted
        // accidentally by block/allow list parsing.
        if (looksLikeIp(candidate)) {
            return runCatching { InetAddress.getByName(candidate).hostAddress?.lowercase() }.getOrNull()
        }
        return normalizeDomain(candidate)
    }

    fun parseHostsRewrite(text: String): List<RewriteRule> {
        val rules = LinkedHashMap<String, RewriteRule>()
        text.lineSequence().forEach { line ->
            val fields = line.substringBefore('#').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (fields.size < 2) return@forEach
            val address = runCatching { InetAddress.getByName(fields.first()) }.getOrNull()
            if (address == null || fields.first().lowercase() in SINKHOLE_ADDRESSES) return@forEach
            val targetType = if (address.address.size == 4) {
                com.haoze.dnssr.data.entity.RewriteTargetType.IPV4
            } else {
                com.haoze.dnssr.data.entity.RewriteTargetType.IPV6
            }
            val targetValue = address.hostAddress ?: return@forEach
            fields.drop(1).forEach { host ->
                normalizeDomain(host)?.let { domain ->
                    rules.putIfAbsent(domain, RewriteRule(domain, targetType, targetValue, line))
                }
            }
        }
        return rules.values.toList()
    }

    private sealed interface LineResult {
        data class Valid(val rule: ParsedRule) : LineResult
        data object Invalid : LineResult
        data object Unsupported : LineResult
    }
}
