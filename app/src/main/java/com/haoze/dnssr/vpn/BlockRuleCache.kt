package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.BlockRuleDao

/**
 * 屏蔽规则内存缓存。
 *
 * 将所有已启用规则加载到 HashSet 中，实现 O(domain标签数) 的快速匹配，
 * 替代每次 DNS 查询时的 O(N) 数据库全表扫描。
 *
 * 匹配逻辑：
 * - 精确匹配：domain == pattern
 * - 子域后缀匹配：domain.endsWith(".$pattern")
 *   通过分解 domain 的所有后缀逐级查找实现
 */
data class BlockRuleMatch(
    val pattern: String,
    val source: String
)

class BlockRuleCache {

    private val rulesByPattern = HashMap<String, String>()

    /**
     * 从数据库全量重载已启用规则到内存。
     */
    suspend fun reload(dao: BlockRuleDao) {
        val rules = dao.enabledRules()
        synchronized(this) {
            rulesByPattern.clear()
            rules.forEach { rule -> rulesByPattern[rule.pattern] = rule.source }
        }
    }

    /**
     * O(domain标签数) 匹配。
     * 例如 "ad.example.com" 依次检查：
     * "ad.example.com" → "example.com" → "com"
     */
    fun findMatch(qname: String): BlockRuleMatch? {
        val domain = qname.lowercase().trimEnd('.')
        synchronized(this) {
            rulesByPattern[domain]?.let { source ->
                return BlockRuleMatch(pattern = domain, source = source)
            }
            var pos = domain.indexOf('.')
            while (pos >= 0 && pos < domain.length - 1) {
                val suffix = domain.substring(pos + 1)
                rulesByPattern[suffix]?.let { source ->
                    return BlockRuleMatch(pattern = suffix, source = source)
                }
                pos = domain.indexOf('.', pos + 1)
            }
            return null
        }
    }

    fun addPattern(pattern: String, source: String) {
        synchronized(this) { rulesByPattern[pattern] = source }
    }

    fun removePattern(pattern: String) {
        synchronized(this) { rulesByPattern.remove(pattern) }
    }

    fun clear() {
        synchronized(this) { rulesByPattern.clear() }
    }

    fun size(): Int {
        synchronized(this) { return rulesByPattern.size }
    }
}
