package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.RewriteRuleDao
import com.haoze.dnssr.data.entity.RewriteRuleEntity
import com.haoze.dnssr.data.entity.RewriteTargetType
import java.net.InetAddress

data class RewriteRule(val pattern: String, val targetType: String, val targetValue: String, val rawLine: String)
data class RewriteAnswer(val targetType: String, val targetValue: String)

class RewriteRuleManager(private val dao: RewriteRuleDao) {
    @Volatile private var rules: Map<String, Set<RewriteAnswer>> = emptyMap()
    suspend fun refreshCache() { rules = dao.enabledRules().groupBy({ it.pattern }, { RewriteAnswer(it.targetType, it.targetValue) }).mapValues { it.value.toSet() } }
    fun findAnswers(qname: String): Set<RewriteAnswer> {
        val domain = qname.lowercase().trimEnd('.')
        var candidate = domain
        while (true) { rules[candidate]?.let { return it }; val dot = candidate.indexOf('.'); if (dot < 0) return emptySet(); candidate = candidate.substring(dot + 1) }
    }
    suspend fun addRule(domain: String, targetType: String, targetValue: String): Boolean {
        val normalized = AdGuardRuleParser.normalizeDomainForRewrite(domain) ?: return false
        val normalizedValue = normalizeTarget(targetType, targetValue) ?: return false
        val isCname = targetType == RewriteTargetType.CNAME
        if (isCname && dao.countOtherTypes(normalized, targetType) > 0) return false
        if (!isCname && dao.countType(normalized, RewriteTargetType.CNAME) > 0) return false
        val ok = dao.insertForSource(RewriteRuleEntity(pattern = normalized, targetType = targetType, targetValue = normalizedValue, rawLine = "$normalized -> $normalizedValue", addedAt = System.currentTimeMillis()), "useradd", true)
        if (ok) refreshCache(); return ok
    }
    suspend fun addRules(
        rules: List<RewriteRule>,
        source: String,
        enabled: Boolean,
        chunkSize: Int = 500,
        onProgress: ((Int) -> Unit)? = null
    ): Int {
        var inserted = 0
        val now = System.currentTimeMillis()
        rules.chunked(chunkSize).forEachIndexed { index, chunk ->
            inserted += dao.insertAllForSource(
                chunk.map { rule ->
                    RewriteRuleEntity(
                        pattern = rule.pattern,
                        targetType = rule.targetType,
                        targetValue = rule.targetValue,
                        rawLine = rule.rawLine,
                        addedAt = now
                    )
                },
                source,
                enabled
            )
            onProgress?.invoke(minOf((index + 1) * chunkSize, rules.size))
        }
        refreshCache()
        return inserted
    }
    suspend fun removeRulesBySource(source: String) { dao.deleteBySource(source); refreshCache() }
    suspend fun setRulesEnabledBySource(source: String, enabled: Boolean) { dao.setEnabledBySource(source, enabled); refreshCache() }
    suspend fun count() = dao.count()
    suspend fun deleteRule(id: Long) { dao.deleteById(id); refreshCache() }
    suspend fun toggleRule(id: Long, enabled: Boolean) { dao.setEnabled(id, enabled); refreshCache() }
    suspend fun rulesBySource(source: String) = dao.rulesBySource(source).map { RewriteRule(it.pattern, it.targetType, it.targetValue, it.rawLine) }
    suspend fun replaceRulesBySource(
        rules: List<RewriteRule>,
        source: String,
        enabled: Boolean,
        chunkSize: Int = 500,
        onProgress: ((Int) -> Unit)? = null
    ) {
        dao.replaceBySource(
            source,
            rules.map {
                RewriteRuleEntity(
                    pattern = it.pattern,
                    targetType = it.targetType,
                    targetValue = it.targetValue,
                    rawLine = it.rawLine,
                    addedAt = System.currentTimeMillis()
                )
            },
            enabled,
            chunkSize,
            onProgress
        )
        refreshCache()
    }
    suspend fun clearAll() { dao.clearAll(); rules = emptyMap() }
    private fun normalizeTarget(type: String, value: String): String? = when (type) {
        RewriteTargetType.CNAME -> AdGuardRuleParser.normalizeDomainForRewrite(value)
        RewriteTargetType.IPV4, RewriteTargetType.IPV6 -> runCatching { InetAddress.getByName(value.trim()) }.getOrNull()?.takeIf { (type == RewriteTargetType.IPV4 && it.address.size == 4) || (type == RewriteTargetType.IPV6 && it.address.size == 16) }?.hostAddress
        else -> null
    }
}
