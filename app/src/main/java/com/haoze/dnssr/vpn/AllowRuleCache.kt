package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.AllowRuleDao

/**
 * 白名单规则内存缓存。
 *
 * 匹配逻辑与屏蔽规则一致：精确匹配或父域后缀匹配。
 */
class AllowRuleCache {

    private val ruleSet = HashSet<String>()

    suspend fun reload(dao: AllowRuleDao) {
        val rules = dao.enabledRules()
        synchronized(this) {
            ruleSet.clear()
            ruleSet.addAll(rules.map { it.pattern })
        }
    }

    fun isAllowed(qname: String): Boolean {
        val domain = qname.lowercase().trimEnd('.')
        synchronized(this) {
            if (ruleSet.contains(domain)) return true
            var pos = domain.indexOf('.')
            while (pos >= 0 && pos < domain.length - 1) {
                val suffix = domain.substring(pos + 1)
                if (ruleSet.contains(suffix)) return true
                pos = domain.indexOf('.', pos + 1)
            }
            return false
        }
    }

    fun addPattern(pattern: String) {
        synchronized(this) { ruleSet.add(pattern) }
    }

    fun removePattern(pattern: String) {
        synchronized(this) { ruleSet.remove(pattern) }
    }

    fun clear() {
        synchronized(this) { ruleSet.clear() }
    }
}
