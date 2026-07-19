package com.haoze.dnssr.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.haoze.dnssr.data.entity.HttpRequestLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HttpRequestLogDao {
    @Insert
    suspend fun insertAll(entities: List<HttpRequestLogEntity>)

    @Query("SELECT * FROM http_request_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<HttpRequestLogEntity>>

    @Query("SELECT * FROM http_request_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HttpRequestLogEntity>

    @Query("SELECT outcome, COUNT(*) AS count FROM http_request_log WHERE timestamp >= :since GROUP BY outcome")
    suspend fun dailyStats(since: Long): List<HttpDailyStatRow>

    @Query("DELETE FROM http_request_log WHERE timestamp < :before")
    suspend fun deleteBefore(before: Long)

    @Query("DELETE FROM http_request_log")
    suspend fun clearAll()
}

data class HttpDailyStatRow(
    val outcome: String,
    val count: Int
)
