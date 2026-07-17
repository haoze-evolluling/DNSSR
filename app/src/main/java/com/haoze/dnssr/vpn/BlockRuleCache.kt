package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.BlockRuleDao
import java.io.File

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

class BlockRuleCache(private val indexFile: File? = null) {

    @Volatile
    private var customRules: Map<String, String> = emptyMap()
    @Volatile
    private var subscriptionFallback: Map<String, String> = emptyMap()
    @Volatile
    private var subscriptionIndex: MappedSubscriptionRuleIndex? = null

    /**
     * 从数据库全量重载已启用规则到内存。
     */
    suspend fun reload(dao: BlockRuleDao) {
        val custom = dao.enabledCustomRules().associate { it.pattern to it.source }
        val subscriptions = dao.enabledSubscriptionRules()
        val mapped = indexFile?.let { file ->
            runCatching { MappedSubscriptionRuleIndex.compileAndLoad(file, subscriptions) }.getOrNull()
        }
        val fallback = if (mapped == null) subscriptions.associate { it.pattern to it.source } else emptyMap()
        synchronized(this) {
            subscriptionIndex?.close()
            customRules = custom
            subscriptionFallback = fallback
            subscriptionIndex = mapped
        }
    }

    /**
     * O(domain标签数) 匹配。
     * 例如 "ad.example.com" 依次检查：
     * "ad.example.com" → "example.com" → "com"
     */
    fun findMatch(qname: String): BlockRuleMatch? {
        val domain = qname.lowercase().trimEnd('.')
        val custom = customRules
        custom[domain]?.let { source ->
            return BlockRuleMatch(pattern = domain, source = source)
        }
        var pos = domain.indexOf('.')
        while (pos >= 0 && pos < domain.length - 1) {
            val suffix = domain.substring(pos + 1)
            custom[suffix]?.let { source ->
                return BlockRuleMatch(pattern = suffix, source = source)
            }
            pos = domain.indexOf('.', pos + 1)
        }
        subscriptionIndex?.find(domain)?.let { source -> return BlockRuleMatch(domain, source) }
        val subscriptions = subscriptionFallback
        subscriptions[domain]?.let { source -> return BlockRuleMatch(domain, source) }
        var subscriptionPos = domain.indexOf('.')
        while (subscriptionPos >= 0 && subscriptionPos < domain.length - 1) {
            val suffix = domain.substring(subscriptionPos + 1)
            subscriptions[suffix]?.let { source -> return BlockRuleMatch(suffix, source) }
            subscriptionPos = domain.indexOf('.', subscriptionPos + 1)
        }
        return null
    }

    fun addPattern(pattern: String, source: String) {
        synchronized(this) {
            customRules = HashMap(customRules).apply { put(pattern, source) }
        }
    }

    fun removePattern(pattern: String) {
        synchronized(this) {
            if (pattern !in customRules) return
            customRules = HashMap(customRules).apply { remove(pattern) }
        }
    }

    fun clear() {
        synchronized(this) {
            customRules = emptyMap()
            subscriptionFallback = emptyMap()
            subscriptionIndex?.close()
            subscriptionIndex = null
        }
    }

    fun size(): Int = customRules.size + subscriptionFallback.size
}
