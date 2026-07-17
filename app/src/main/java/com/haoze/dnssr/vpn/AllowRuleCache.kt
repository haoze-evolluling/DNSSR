package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.AllowRuleDao
import java.io.File

/**
 * 白名单规则内存缓存。
 *
 * 匹配逻辑与屏蔽规则一致：精确匹配或父域后缀匹配。
 */
class AllowRuleCache(private val indexFile: File? = null) {

    @Volatile
    private var customRules: Set<String> = emptySet()
    @Volatile
    private var subscriptionFallback: Set<String> = emptySet()
    @Volatile
    private var subscriptionIndex: MappedSubscriptionRuleIndex? = null

    suspend fun reload(dao: AllowRuleDao) {
        val custom = dao.enabledCustomRules().mapTo(HashSet()) { it.pattern }
        val subscriptions = dao.enabledSubscriptionRules()
        val mapped = indexFile?.let { file ->
            runCatching { MappedSubscriptionRuleIndex.compileAndLoad(file, subscriptions) }.getOrNull()
        }
        val fallback = if (mapped == null) subscriptions.mapTo(HashSet()) { it.pattern } else emptySet()
        synchronized(this) {
            subscriptionIndex?.close()
            customRules = custom
            subscriptionFallback = fallback
            subscriptionIndex = mapped
        }
    }

    fun isAllowed(qname: String): Boolean {
        return findMatch(qname) != null
    }

    fun findMatch(qname: String): String? {
        val domain = qname.lowercase().trimEnd('.')
        val rules = customRules
        if (rules.contains(domain)) return domain
        var pos = domain.indexOf('.')
        while (pos >= 0 && pos < domain.length - 1) {
            val suffix = domain.substring(pos + 1)
            if (rules.contains(suffix)) return suffix
            pos = domain.indexOf('.', pos + 1)
        }
        subscriptionIndex?.find(domain)?.let { return it }
        val subscriptions = subscriptionFallback
        if (subscriptions.contains(domain)) return domain
        var subscriptionPos = domain.indexOf('.')
        while (subscriptionPos >= 0 && subscriptionPos < domain.length - 1) {
            val suffix = domain.substring(subscriptionPos + 1)
            if (subscriptions.contains(suffix)) return suffix
            subscriptionPos = domain.indexOf('.', subscriptionPos + 1)
        }
        return null
    }

    fun addPattern(pattern: String) {
        synchronized(this) {
            customRules = HashSet(customRules).apply { add(pattern) }
        }
    }

    fun removePattern(pattern: String) {
        synchronized(this) {
            if (pattern !in customRules) return
            customRules = HashSet(customRules).apply { remove(pattern) }
        }
    }

    fun clear() {
        synchronized(this) {
            customRules = emptySet()
            subscriptionFallback = emptySet()
            subscriptionIndex?.close()
            subscriptionIndex = null
        }
    }
}
