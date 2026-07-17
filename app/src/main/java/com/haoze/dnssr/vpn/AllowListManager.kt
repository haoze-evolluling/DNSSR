package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.AllowRuleDao
import com.haoze.dnssr.data.entity.AllowRuleEntity
import java.io.File

/**
 * DNS 白名单规则管理器。
 *
 * 白名单命中时会绕过本应用的屏蔽规则，但仍继续走所选加密 DNS 上游解析。
 */
class AllowListManager(
    private val dao: AllowRuleDao,
    indexDirectory: File? = null
) {

    private val cache = AllowRuleCache(indexDirectory?.let { File(it, "subscription-allow.trie") })

    suspend fun refreshCache() {
        cache.reload(dao)
    }

    fun isAllowed(qname: String): Boolean {
        return cache.isAllowed(qname)
    }

    fun findMatch(qname: String): String? = cache.findMatch(qname)

    suspend fun allRules(): List<AllowRuleEntity> = dao.all()

    suspend fun addRule(pattern: String): Boolean {
        val parsed = AdGuardRuleParser.parseAllowLine(pattern) ?: return false
        val inserted = dao.insertForSource(
            AllowRuleEntity(
                pattern = parsed.pattern,
                rawLine = parsed.rawLine,
                addedAt = System.currentTimeMillis(),
                enabled = true,
                groupName = null
            ),
            source = "useradd",
            sourceEnabled = true
        )
        if (!inserted) return false
        cache.reload(dao)
        return true
    }

    suspend fun addRulesBatch(
        rules: List<AdGuardRuleParser.ParsedRule>,
        source: String,
        chunkSize: Int = 500,
        enabled: Boolean = true,
        onProgress: ((Int) -> Unit)? = null
    ): Int {
        val now = System.currentTimeMillis()
        var imported = 0
        var inserted = 0
        rules.chunked(chunkSize).forEach { chunk ->
            val entities = chunk.map { rule ->
                AllowRuleEntity(
                    pattern = rule.pattern,
                    rawLine = rule.rawLine,
                    addedAt = now,
                    enabled = true,
                    groupName = null
                )
            }
            inserted += dao.insertAllForSource(entities, source, enabled)
            imported += chunk.size
            onProgress?.invoke(imported)
        }
        cache.reload(dao)
        return inserted
    }

    suspend fun replaceRulesBySource(
        rules: List<AdGuardRuleParser.ParsedRule>,
        source: String,
        enabled: Boolean,
        onProgress: ((Int) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        dao.replaceBySource(source, rules.map { rule ->
            AllowRuleEntity(pattern = rule.pattern, rawLine = rule.rawLine, addedAt = now)
        }, enabled, onProgress = onProgress)
        cache.reload(dao)
    }

    suspend fun deleteRule(id: Long) {
        val rule = dao.all().find { it.id == id }
        if (rule != null) {
            dao.deleteById(id)
            cache.reload(dao)
        }
    }

    suspend fun toggleRule(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        cache.reload(dao)
    }

    suspend fun setRulesEnabledBySource(source: String, enabled: Boolean) {
        dao.setEnabledBySource(source, enabled)
        cache.reload(dao)
    }

    suspend fun count(): Int = dao.count()

    suspend fun clearAll() {
        dao.clearAll()
        cache.clear()
    }

    suspend fun removeRulesBySource(source: String) {
        dao.deleteBySource(source)
        cache.reload(dao)
    }

    suspend fun countBySource(source: String): Int = dao.countBySource(source)

    suspend fun parsedRulesBySource(source: String): List<AdGuardRuleParser.ParsedRule> =
        dao.bySource(source).map { AdGuardRuleParser.ParsedRule(it.pattern, it.rawLine) }
}
