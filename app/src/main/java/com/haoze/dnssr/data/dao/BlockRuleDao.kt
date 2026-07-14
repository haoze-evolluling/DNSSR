package com.haoze.dnssr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.haoze.dnssr.data.entity.BlockRuleEntity

@Dao
interface BlockRuleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: BlockRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<BlockRuleEntity>): List<Long>

    @Transaction
    suspend fun replaceBySource(source: String, entities: List<BlockRuleEntity>) {
        deleteBySource(source)
        insertAll(entities)
    }

    @Query("SELECT * FROM block_rule ORDER BY addedAt DESC")
    suspend fun all(): List<BlockRuleEntity>

    @Query("SELECT * FROM block_rule WHERE enabled = 1 ORDER BY addedAt DESC")
    suspend fun enabledRules(): List<BlockRuleEntity>

    @Query("SELECT COUNT(*) FROM block_rule")
    suspend fun count(): Int

    @Query("DELETE FROM block_rule WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM block_rule WHERE pattern = :pattern")
    suspend fun delete(pattern: String)

    @Query("UPDATE block_rule SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE block_rule SET enabled = :enabled WHERE source = :source")
    suspend fun setEnabledBySource(source: String, enabled: Boolean)

    @Query("DELETE FROM block_rule")
    suspend fun clearAll()

    @Query("SELECT * FROM block_rule WHERE source = :source")
    suspend fun bySource(source: String): List<BlockRuleEntity>

    @Query("DELETE FROM block_rule WHERE source = :source")
    suspend fun deleteBySource(source: String)

    @Query("SELECT COUNT(*) FROM block_rule WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("SELECT * FROM block_rule WHERE pattern LIKE :query OR rawLine LIKE :query ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun searchPaged(query: String, limit: Int, offset: Int): List<BlockRuleEntity>

    @Query("SELECT COUNT(*) FROM block_rule WHERE pattern LIKE :query OR rawLine LIKE :query")
    suspend fun searchCount(query: String): Int

    @Query("SELECT * FROM block_rule ORDER BY addedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun paged(limit: Int, offset: Int): List<BlockRuleEntity>
}
