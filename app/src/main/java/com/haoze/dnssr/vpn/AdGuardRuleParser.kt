package com.haoze.dnssr.vpn

/**
 * AdGuard DNS 过滤规则解析器。
 *
 * 支持的格式：
 * - ||example.com^ 或 ||example.com → example.com
 * - 0.0.0.0 example.com / 127.0.0.1 example.com → example.com
 * - example.com（纯域名）→ example.com
 *
 * 跳过的格式（静默忽略）：
 * - @@||domain^ 白名单规则（屏蔽规则导入时）
 * - ! 或 # 开头的注释
 * - /regex/ 正则规则
 * - 含 $ 修饰符的规则
 * - 含 * 通配符的规则
 * - ## / #$# 等 cosmetic 规则
 * - 空行
 */
object AdGuardRuleParser {

    data class ParsedRule(val pattern: String, val rawLine: String)

    private val HOSTS_PATTERN = Regex("^(0\\.0\\.0\\.0|127\\.0\\.0\\.1)\\s+(.+)$")

    /**
     * 解析单行规则文本，返回规范化后的域名，或 null 表示跳过。
     */
    fun parseLine(line: String): ParsedRule? {
        return parseLine(line, allowRule = false)
    }

    /**
     * 解析单行白名单规则。支持 @@||example.com^，也支持直接输入 example.com。
     */
    fun parseAllowLine(line: String): ParsedRule? {
        return parseLine(line, allowRule = true)
    }

    private fun parseLine(line: String, allowRule: Boolean): ParsedRule? {
        val trimmed = line.trim()

        // 跳过空行和注释
        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("#")) {
            return null
        }

        val withoutAllowPrefix = if (trimmed.startsWith("@@")) {
            trimmed.removePrefix("@@")
        } else {
            if (!allowRule) trimmed else trimmed
        }

        // 屏蔽规则导入时跳过白名单规则。
        if (!allowRule && trimmed.startsWith("@@")) return null

        // 跳过正则规则
        if (withoutAllowPrefix.startsWith("/")) return null

        // 跳过含 $ 修饰符的规则
        if (withoutAllowPrefix.contains("$")) return null

        // 跳过含 * 通配符的规则
        if (withoutAllowPrefix.contains("*")) return null

        var domain = withoutAllowPrefix

        // 剥离 || 前缀
        if (domain.startsWith("||")) {
            domain = domain.removePrefix("||")
        }

        // 剥离 ^ 后缀（可能紧跟其他内容如 ^$important，但前面已过滤含 $ 的行）
        domain = domain.trimEnd('^')

        // hosts 格式匹配: 0.0.0.0 domain 或 127.0.0.1 domain
        val hostsMatch = HOSTS_PATTERN.matchEntire(domain)
        if (hostsMatch != null) {
            domain = hostsMatch.groupValues[2].trim()
        }

        // 规范化
        domain = domain.lowercase().trim().trimEnd('.')
        if (domain.isEmpty()) return null

        // 跳过含 / 或 : 的异常模式（如含路径或端口号）
        if (domain.contains("/") || domain.contains(":")) return null

        return ParsedRule(pattern = domain, rawLine = trimmed)
    }

    /**
     * 解析完整的规则文本，返回所有有效规则列表。
     */
    fun parseAll(text: String): List<ParsedRule> {
        return text.lineSequence().mapNotNull(::parseLine).toList()
    }

    fun parseAllowAll(text: String): List<ParsedRule> {
        return text.lineSequence().mapNotNull(::parseAllowLine).toList()
    }
}
