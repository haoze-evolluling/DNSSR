package com.haoze.dnssr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.haoze.dnssr.data.entity.AllowRuleEntity
import com.haoze.dnssr.data.entity.AllowRuleSourceEntity
import com.haoze.dnssr.data.dao.EnabledBlockRule

@Dao
interface AllowRuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRule(entity: AllowRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(entity: AllowRuleSourceEntity): Long

    @Query("SELECT id FROM allow_rule WHERE pattern = :pattern")
    suspend fun idByPattern(pattern: String): Long

    @Transaction
    suspend fun insertForSource(entity: AllowRuleEntity, source: String, sourceEnabled: Boolean): Boolean {
        val insertedId = insertRule(entity)
        val ruleId = if (insertedId == -1L) idByPattern(entity.pattern) else insertedId
        return insertSource(AllowRuleSourceEntity(ruleId, source, sourceEnabled)) != -1L
    }

    @Transaction
    suspend fun insertAllForSource(
        entities: List<AllowRuleEntity>,
        source: String,
        sourceEnabled: Boolean
    ): Int = entities.count { insertForSource(it, source, sourceEnabled) }

    @Transaction
    suspend fun replaceBySource(
        source: String,
        entities: List<AllowRuleEntity>,
        sourceEnabled: Boolean,
        chunkSize: Int = 500,
        onProgress: ((Int) -> Unit)? = null
    ) {
        deleteSource(source)
        deleteOrphans()
        var imported = 0
        entities.chunked(chunkSize).forEach { chunk ->
            insertAllForSource(chunk, source, sourceEnabled)
            imported += chunk.size
            onProgress?.invoke(imported)
        }
    }

    @Query("SELECT * FROM allow_rule ORDER BY addedAt DESC")
    suspend fun all(): List<AllowRuleEntity>

    @Query(
        "SELECT DISTINCT r.* FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE r.enabled = 1 AND s.enabled = 1 ORDER BY r.addedAt DESC"
    )
    suspend fun enabledRules(): List<AllowRuleEntity>

    @Query(
        "SELECT r.pattern, r.pattern AS source FROM allow_rule r " +
            "JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE r.enabled = 1 AND s.enabled = 1 AND s.source LIKE 'sub_%' GROUP BY r.pattern"
    )
    suspend fun enabledSubscriptionRules(): List<EnabledBlockRule>

    @Query(
        "SELECT r.pattern, 'useradd' AS source FROM allow_rule r " +
            "JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE r.enabled = 1 AND s.enabled = 1 AND s.source = 'useradd' GROUP BY r.pattern"
    )
    suspend fun enabledCustomRules(): List<EnabledBlockRule>

    @Query(
        "SELECT DISTINCT r.pattern FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE r.enabled = 1 AND s.enabled = 1"
    )
    suspend fun enabledPatterns(): List<String>

    @Query("SELECT COUNT(*) FROM allow_rule")
    suspend fun count(): Int

    @Query("DELETE FROM allow_rule WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE allow_rule SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE allow_rule_source SET enabled = :enabled WHERE source = :source")
    suspend fun setEnabledBySource(source: String, enabled: Boolean)

    @Query("DELETE FROM allow_rule")
    suspend fun clearAll()

    @Query(
        "SELECT r.* FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE s.source = :source ORDER BY r.addedAt DESC"
    )
    suspend fun bySource(source: String): List<AllowRuleEntity>

    @Query("DELETE FROM allow_rule_source WHERE source = :source")
    suspend fun deleteSource(source: String)

    @Query("DELETE FROM allow_rule WHERE NOT EXISTS (SELECT 1 FROM allow_rule_source s WHERE s.ruleId = allow_rule.id)")
    suspend fun deleteOrphans()

    @Transaction
    suspend fun deleteBySource(source: String) {
        deleteSource(source)
        deleteOrphans()
    }

    @Query("SELECT COUNT(*) FROM allow_rule_source WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT * FROM allow_rule WHERE pattern LIKE :query OR rawLine LIKE :query ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun searchPaged(query: String, limit: Int, offset: Int): List<AllowRuleEntity>

    @Query("SELECT COUNT(*) FROM allow_rule WHERE pattern LIKE :query OR rawLine LIKE :query")
    suspend fun searchCount(query: String): Int

    @Query("SELECT * FROM allow_rule ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun paged(limit: Int, offset: Int): List<AllowRuleEntity>

    @Query(
        "SELECT DISTINCT r.* FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE s.source = :source ORDER BY r.addedAt DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun pagedBySource(source: String, limit: Int, offset: Int): List<AllowRuleEntity>

    @Query(
        "SELECT DISTINCT r.* FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE s.source = :source AND (r.pattern LIKE :query OR r.rawLine LIKE :query) " +
            "ORDER BY r.addedAt DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun searchPagedBySource(
        source: String,
        query: String,
        limit: Int,
        offset: Int
    ): List<AllowRuleEntity>

    @Query("SELECT COUNT(DISTINCT r.id) FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id WHERE s.source = :source")
    suspend fun countBySourceForList(source: String): Int

    @Query(
        "SELECT COUNT(DISTINCT r.id) FROM allow_rule r JOIN allow_rule_source s ON s.ruleId = r.id " +
            "WHERE s.source = :source AND (r.pattern LIKE :query OR r.rawLine LIKE :query)"
    )
    suspend fun searchCountBySource(source: String, query: String): Int
}
