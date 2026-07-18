package com.haoze.dnssr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.haoze.dnssr.data.entity.RewriteRuleEntity
import com.haoze.dnssr.data.entity.RewriteRuleSourceEntity

data class EnabledRewriteRule(val pattern: String, val targetType: String, val targetValue: String)

@Dao
interface RewriteRuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRule(rule: RewriteRuleEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSource(source: RewriteRuleSourceEntity): Long
    @Query("SELECT id FROM rewrite_rule WHERE pattern=:pattern AND targetType=:targetType AND targetValue=:targetValue") suspend fun idByKey(pattern: String, targetType: String, targetValue: String): Long
    @Transaction suspend fun insertForSource(rule: RewriteRuleEntity, source: String, enabled: Boolean): Boolean {
        val id = insertRule(rule).let { if (it == -1L) idByKey(rule.pattern, rule.targetType, rule.targetValue) else it }
        return insertSource(RewriteRuleSourceEntity(id, source, enabled)) != -1L
    }
    @Query("SELECT r.pattern, r.targetType, r.targetValue FROM rewrite_rule r JOIN rewrite_rule_source s ON s.ruleId=r.id WHERE r.enabled=1 AND s.enabled=1 GROUP BY r.id") suspend fun enabledRules(): List<EnabledRewriteRule>
    @Query("SELECT * FROM rewrite_rule ORDER BY id DESC LIMIT :limit OFFSET :offset") suspend fun paged(limit: Int, offset: Int): List<RewriteRuleEntity>
    @Query("SELECT * FROM rewrite_rule WHERE pattern LIKE :query OR targetValue LIKE :query OR rawLine LIKE :query ORDER BY id DESC LIMIT :limit OFFSET :offset") suspend fun searchPaged(query: String, limit: Int, offset: Int): List<RewriteRuleEntity>
    @Query("SELECT r.* FROM rewrite_rule r JOIN rewrite_rule_source s ON s.ruleId=r.id WHERE s.source=:source AND (r.pattern LIKE :query OR r.targetValue LIKE :query OR r.rawLine LIKE :query) GROUP BY r.id ORDER BY r.id DESC LIMIT :limit OFFSET :offset") suspend fun searchPagedBySource(source: String, query: String, limit: Int, offset: Int): List<RewriteRuleEntity>
    @Query("SELECT r.* FROM rewrite_rule r JOIN rewrite_rule_source s ON s.ruleId=r.id WHERE s.source=:source GROUP BY r.id ORDER BY r.id DESC LIMIT :limit OFFSET :offset") suspend fun pagedBySource(source: String, limit: Int, offset: Int): List<RewriteRuleEntity>
    @Query("SELECT COUNT(*) FROM rewrite_rule") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM rewrite_rule WHERE pattern LIKE :query OR targetValue LIKE :query OR rawLine LIKE :query") suspend fun searchCount(query: String): Int
    @Query("SELECT COUNT(DISTINCT r.id) FROM rewrite_rule r JOIN rewrite_rule_source s ON s.ruleId=r.id WHERE s.source=:source") suspend fun countBySourceForList(source: String): Int
    @Query("SELECT COUNT(DISTINCT r.id) FROM rewrite_rule r JOIN rewrite_rule_source s ON s.ruleId=r.id WHERE s.source=:source AND (r.pattern LIKE :query OR r.targetValue LIKE :query OR r.rawLine LIKE :query)") suspend fun searchCountBySource(source: String, query: String): Int
    @Query("SELECT * FROM rewrite_rule r WHERE EXISTS (SELECT 1 FROM rewrite_rule_source s WHERE s.ruleId=r.id AND s.source=:source)") suspend fun rulesBySource(source: String): List<RewriteRuleEntity>
    @Query("SELECT COUNT(*) FROM rewrite_rule WHERE pattern=:pattern AND targetType=:targetType") suspend fun countType(pattern: String, targetType: String): Int
    @Query("SELECT COUNT(*) FROM rewrite_rule WHERE pattern=:pattern AND targetType!=:targetType") suspend fun countOtherTypes(pattern: String, targetType: String): Int
    @Query("UPDATE rewrite_rule SET enabled=:enabled WHERE id=:id") suspend fun setEnabled(id: Long, enabled: Boolean)
    @Query("DELETE FROM rewrite_rule WHERE id=:id") suspend fun deleteById(id: Long)
    @Query("DELETE FROM rewrite_rule") suspend fun clearAll()
    @Query("DELETE FROM rewrite_rule_source WHERE source=:source") suspend fun deleteSource(source: String)
    @Query("DELETE FROM rewrite_rule WHERE NOT EXISTS (SELECT 1 FROM rewrite_rule_source s WHERE s.ruleId=rewrite_rule.id)") suspend fun deleteOrphans()
    @Transaction suspend fun deleteBySource(source: String) { deleteSource(source); deleteOrphans() }
    @Transaction suspend fun replaceBySource(source: String, rules: List<RewriteRuleEntity>, enabled: Boolean) { deleteBySource(source); rules.forEach { insertForSource(it, source, enabled) } }
    @Query("UPDATE rewrite_rule_source SET enabled=:enabled WHERE source=:source") suspend fun setEnabledBySource(source: String, enabled: Boolean)
}
