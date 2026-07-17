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

    @Volatile
    private var rulesByPattern: Map<String, String> = emptyMap()

    /**
     * 从数据库全量重载已启用规则到内存。
     */
    suspend fun reload(dao: BlockRuleDao) {
        val rules = dao.enabledRules()
        val updatedRules = HashMap<String, String>(rules.size)
        rules.forEach { rule -> updatedRules[rule.pattern] = rule.source }
        synchronized(this) { rulesByPattern = updatedRules }
    }

    /**
     * O(domain标签数) 匹配。
     * 例如 "ad.example.com" 依次检查：
     * "ad.example.com" → "example.com" → "com"
     */
    fun findMatch(qname: String): BlockRuleMatch? {
        val domain = qname.lowercase().trimEnd('.')
        val rules = rulesByPattern
        rules[domain]?.let { source ->
            return BlockRuleMatch(pattern = domain, source = source)
        }
        var pos = domain.indexOf('.')
        while (pos >= 0 && pos < domain.length - 1) {
            val suffix = domain.substring(pos + 1)
            rules[suffix]?.let { source ->
                return BlockRuleMatch(pattern = suffix, source = source)
            }
            pos = domain.indexOf('.', pos + 1)
        }
        return null
    }

    fun addPattern(pattern: String, source: String) {
        synchronized(this) {
            rulesByPattern = HashMap(rulesByPattern).apply { put(pattern, source) }
        }
    }

    fun removePattern(pattern: String) {
        synchronized(this) {
            if (pattern !in rulesByPattern) return
            rulesByPattern = HashMap(rulesByPattern).apply { remove(pattern) }
        }
    }

    fun clear() {
        synchronized(this) { rulesByPattern = emptyMap() }
    }

    fun size(): Int = rulesByPattern.size
}
