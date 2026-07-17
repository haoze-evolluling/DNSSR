package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.AllowRuleDao

/**
 * 白名单规则内存缓存。
 *
 * 匹配逻辑与屏蔽规则一致：精确匹配或父域后缀匹配。
 */
class AllowRuleCache {

    @Volatile
    private var ruleSet: Set<String> = emptySet()

    suspend fun reload(dao: AllowRuleDao) {
        val rules = dao.enabledRules()
        val updatedRules = HashSet<String>(rules.size).apply {
            rules.forEach { rule -> add(rule.pattern) }
        }
        synchronized(this) { ruleSet = updatedRules }
    }

    fun isAllowed(qname: String): Boolean {
        return findMatch(qname) != null
    }

    fun findMatch(qname: String): String? {
        val domain = qname.lowercase().trimEnd('.')
        val rules = ruleSet
        if (rules.contains(domain)) return domain
        var pos = domain.indexOf('.')
        while (pos >= 0 && pos < domain.length - 1) {
            val suffix = domain.substring(pos + 1)
            if (rules.contains(suffix)) return suffix
            pos = domain.indexOf('.', pos + 1)
        }
        return null
    }

    fun addPattern(pattern: String) {
        synchronized(this) {
            ruleSet = HashSet(ruleSet).apply { add(pattern) }
        }
    }

    fun removePattern(pattern: String) {
        synchronized(this) {
            if (pattern !in ruleSet) return
            ruleSet = HashSet(ruleSet).apply { remove(pattern) }
        }
    }

    fun clear() {
        synchronized(this) { ruleSet = emptySet() }
    }
}
