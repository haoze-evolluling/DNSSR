package com.haoze.dnssr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.haoze.dnssr.data.entity.SubscriptionEntity

@Dao
interface SubscriptionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(subscription: SubscriptionEntity): Long

    @Update
    suspend fun update(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscription ORDER BY addedAt DESC")
    suspend fun all(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscription WHERE sourceType = 'remote' ORDER BY addedAt DESC")
    suspend fun allRemote(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscription WHERE kind = :kind ORDER BY addedAt DESC")
    suspend fun allByKind(kind: String): List<SubscriptionEntity>

    @Query("SELECT * FROM subscription WHERE id = :id")
    suspend fun byId(id: Long): SubscriptionEntity?

    @Query("UPDATE subscription SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE subscription SET name = :name WHERE id = :id")
    suspend fun setName(id: Long, name: String)

    @Query("UPDATE subscription SET name = :name, url = :url, ruleCount = :ruleCount, lastUpdated = :lastUpdated WHERE id = :id")
    suspend fun setDetails(id: Long, name: String, url: String, ruleCount: Int, lastUpdated: Long)

    @Query("SELECT * FROM subscription WHERE url = :url AND kind = :kind")
    suspend fun byUrlAndKind(url: String, kind: String): SubscriptionEntity?

    @Query("SELECT * FROM subscription WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): SubscriptionEntity?

    @Query("DELETE FROM subscription WHERE id = :id")
    suspend fun deleteById(id: Long)
}
