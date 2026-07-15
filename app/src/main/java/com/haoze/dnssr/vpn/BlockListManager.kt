package com.haoze.dnssr.vpn

import com.haoze.dnssr.data.dao.BlockRuleDao
import com.haoze.dnssr.data.entity.BlockRuleEntity

/**
 * AdGuard 风格屏蔽规则管理器。
 *
 * 支持添加的格式（通过 AdGuardRuleParser 解析）：
 * - ||example.com^ 或 ||example.com
 * - example.com
 * - 0.0.0.0 example.com / 127.0.0.1 example.com
 *
 * 性能优化：
 * - 使用 BlockRuleCache 内存 HashSet 缓存，isBlocked() 从 O(N) 降至 O(domain标签数)
 * - VPN 启动时全量加载缓存，规则变更时增量更新
 */
class BlockListManager(private val dao: BlockRuleDao) {

    private val cache = BlockRuleCache()

    /**
     * 从数据库全量重载缓存。VPN 启动时或大批量操作后调用。
     */
    suspend fun refreshCache() {
        cache.reload(dao)
    }

    /**
     * O(domain标签数) 的屏蔽匹配。使用内存缓存，不查数据库。
     */
    fun isBlocked(qname: String): Boolean {
        return cache.findMatch(qname) != null
    }

    fun findMatch(qname: String): BlockRuleMatch? {
        return cache.findMatch(qname)
    }

    suspend fun allRules(): List<BlockRuleEntity> = dao.all()

    /**
     * 添加单条规则（支持 AdGuard 格式自动解析）。
     */
    suspend fun addRule(pattern: String): Boolean {
        val parsed = AdGuardRuleParser.parseLine(pattern) ?: return false
        val inserted = dao.insertForSource(
            BlockRuleEntity(
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

    /**
     * 批量导入规则（用于订阅导入）。
     * @param rules 已解析的规则列表
     * @param source 来源标识（如 "sub_1"）
     * @param chunkSize 分块大小
     * @param onProgress 进度回调 (已导入数)
     */
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
                BlockRuleEntity(
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
        // 批量导入后全量刷新缓存
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
            BlockRuleEntity(pattern = rule.pattern, rawLine = rule.rawLine, addedAt = now)
        }, enabled, onProgress = onProgress)
        cache.reload(dao)
    }

    suspend fun userRules(): List<BlockRuleEntity> = dao.all()

    suspend fun deleteRule(id: Long) {
        val rules = dao.all()
        val rule = rules.find { it.id == id }
        if (rule != null) {
            dao.deleteById(id)
            cache.reload(dao)
        }
    }

    suspend fun toggleRule(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        // 切换启用状态后需全量刷新（禁用需移除，启用需添加）
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

    /**
     * 按 source 删除规则（用于删除订阅的所有规则）。
     */
    suspend fun removeRulesBySource(source: String) {
        dao.deleteBySource(source)
        cache.reload(dao)
    }

    suspend fun countBySource(source: String): Int = dao.countBySource(source)

    suspend fun parsedRulesBySource(source: String): List<AdGuardRuleParser.ParsedRule> =
        dao.bySource(source).map { AdGuardRuleParser.ParsedRule(it.pattern, it.rawLine) }
}
