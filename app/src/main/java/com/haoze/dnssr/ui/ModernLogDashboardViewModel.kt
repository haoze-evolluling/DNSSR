package com.haoze.dnssr.ui

import android.app.Application
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.BootstrapStatsRange
import com.haoze.dnssr.data.RaceStatsRange
import com.haoze.dnssr.data.SubscriptionInterceptionStatsRange
import com.haoze.dnssr.data.entity.DnsCacheEntity
import com.haoze.dnssr.data.entity.DnsLogEntity
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.data.repository.BootstrapLogRepository
import com.haoze.dnssr.data.repository.DnsCacheRepository
import com.haoze.dnssr.data.repository.DnsLogRepository
import com.haoze.dnssr.data.repository.RaceLogRepository
import com.haoze.dnssr.util.dayStartMillis
import com.haoze.dnssr.vpn.LogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class ModernLogDashboardUiState(
    val loading: Boolean = true,
    val dashboardJson: String = "{}",
    val error: String? = null
)

class ModernLogDashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val dnsLogRepository = DnsLogRepository(database.dnsLogDao())
    private val dnsCacheRepository = DnsCacheRepository(database.dnsCacheDao())
    private val raceLogRepository = RaceLogRepository(database.raceLogDao())
    private val bootstrapLogRepository = BootstrapLogRepository(application, database.bootstrapLogDao())

    private val _uiState = MutableStateFlow(ModernLogDashboardUiState())
    val uiState: StateFlow<ModernLogDashboardUiState> = _uiState.asStateFlow()
    var hasLoadedDashboard = false
        private set
    var dashboardWebView: WebView? = null

    fun markDashboardLoaded() {
        hasLoadedDashboard = true
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            val result = runCatching { buildDashboardJson() }
            _uiState.value = result.fold(
                onSuccess = { ModernLogDashboardUiState(loading = false, dashboardJson = it) },
                onFailure = { error ->
                    ModernLogDashboardUiState(
                        loading = false,
                        dashboardJson = errorJson(error),
                        error = error.message ?: "加载失败"
                    )
                }
            )
        }
    }

    override fun onCleared() {
        dashboardWebView?.destroy()
        dashboardWebView = null
        super.onCleared()
    }

    private suspend fun buildDashboardJson(): String {
        val now = System.currentTimeMillis()
        val dailyStats = dnsLogRepository.dailyStats(dayStartMillis())
        val recentLogs = dnsLogRepository.recentLogs(RECENT_LOG_LIMIT)
        val recentCacheEntries = dnsCacheRepository.recentEntries(now, RECENT_CACHE_LIMIT)
        val raceStats = raceLogRepository.stats(RaceStatsRange.TODAY)
        val bootstrapStats = bootstrapLogRepository.stats(BootstrapStatsRange.TODAY)
        val subscriptionStats = dnsLogRepository.subscriptionInterceptionStats(SubscriptionInterceptionStatsRange.TODAY)
        val subscriptions = database.subscriptionDao().allByKind(SubscriptionKind.BLOCK)
        val subscriptionsById = subscriptions.associateBy { it.id }
        val subscriptionItems = (subscriptionsById.keys + subscriptionStats.hitsBySubscriptionId.keys)
            .map { id ->
                val subscription = subscriptionsById[id]
                val hits = subscriptionStats.hitsBySubscriptionId[id] ?: 0
                JSONObject()
                    .put("name", subscription?.name ?: "已删除订阅 #$id")
                    .put("enabled", subscription?.enabled ?: false)
                    .put("deleted", subscription == null)
                    .put("hits", hits)
                    .put(
                        "rate",
                        if (subscriptionStats.totalRequests == 0) 0.0 else hits.toDouble() / subscriptionStats.totalRequests
                    )
            }
            .sortedByDescending { it.optInt("hits") }
            .take(TOP_LIST_LIMIT)

        val totalLogs = dailyStats.passed + dailyStats.blocked + dailyStats.error
        return JSONObject()
            .put("generatedAt", now)
            .put(
                "dailyStats",
                JSONObject()
                    .put("total", totalLogs)
                    .put("passed", dailyStats.passed)
                    .put("blocked", dailyStats.blocked)
                    .put("error", dailyStats.error)
                    .put("cached", dailyStats.cached)
            )
            .put("recentLogs", recentLogs.toLogArray())
            .put("cacheEntries", recentCacheEntries.toCacheArray(now))
            .put(
                "race",
                JSONObject()
                    .put("requests", raceStats.strategyStats.sumOf { it.requests })
                    .put("successes", raceStats.strategyStats.sumOf { it.successes })
                    .put("avgElapsedMs", raceStats.strategyStats.weightedAverage { it.avgElapsedMs to it.requests })
                    .put("strategies", JSONArray().also { array ->
                        raceStats.strategyStats.take(TOP_LIST_LIMIT).forEach { item ->
                            array.put(
                                JSONObject()
                                    .put("name", item.displayName)
                                    .put("requests", item.requests)
                                    .put("successRate", item.successRate)
                                    .put("avgElapsedMs", item.avgElapsedMs)
                            )
                        }
                    })
                    .put("winners", JSONArray().also { array ->
                        raceStats.winnerStats.take(TOP_LIST_LIMIT).forEach { item ->
                            array.put(
                                JSONObject()
                                    .put("name", item.providerName)
                                    .put("wins", item.wins)
                                    .put("avgElapsedMs", item.avgWinnerElapsedMs)
                            )
                        }
                    })
            )
            .put(
                "bootstrap",
                JSONObject()
                    .put("attempts", bootstrapStats.overall.attempts)
                    .put("successes", bootstrapStats.overall.successes)
                    .put("successRate", bootstrapStats.overall.successRate)
                    .put("avgElapsedMs", bootstrapStats.overall.avgElapsedMs)
                    .put("fallbackUses", bootstrapStats.overall.fallbackUses)
                    .put("ips", JSONArray().also { array ->
                        bootstrapStats.ipStats.take(TOP_LIST_LIMIT).forEach { item ->
                            array.put(
                                JSONObject()
                                    .put("name", item.ipName)
                                    .put("ip", item.ip)
                                    .put("attempts", item.attempts)
                                    .put("successRate", item.successRate)
                                    .put("avgElapsedMs", item.avgElapsedMs)
                                    .put("weight", item.predictionWeight)
                            )
                        }
                    })
            )
            .put(
                "subscriptions",
                JSONObject()
                    .put("totalRequests", subscriptionStats.totalRequests)
                    .put("items", JSONArray(subscriptionItems))
            )
            .toString()
    }

    private fun List<DnsLogEntity>.toLogArray(): JSONArray {
        return JSONArray().also { array ->
            forEach { log ->
                array.put(
                    JSONObject()
                        .put("timestamp", log.timestamp)
                        .put("queryName", log.queryName)
                        .put("queryType", dnsTypeName(log.queryType))
                        .put("result", log.result)
                        .put("resultLabel", resultLabel(log.result))
                        .put("cached", log.cached)
                        .put("message", log.message.orEmpty())
                )
            }
        }
    }

    private fun List<DnsCacheEntity>.toCacheArray(now: Long): JSONArray {
        return JSONArray().also { array ->
            forEach { entry ->
                array.put(
                    JSONObject()
                        .put("queryName", entry.queryName)
                        .put("queryType", dnsTypeName(entry.queryType))
                        .put("expiresAt", entry.expiresAt)
                        .put("remainingSeconds", ((entry.expiresAt - now).coerceAtLeast(0L) + 999L) / 1000L)
                        .put("hitCount", entry.hitCount)
                        .put("responseSize", entry.responseSize)
                        .put("originalTtlSeconds", entry.originalTtlSeconds)
                        .put("lastHitAt", entry.lastHitAt ?: 0L)
                )
            }
        }
    }

    private fun errorJson(error: Throwable): String {
        return JSONObject()
            .put("generatedAt", System.currentTimeMillis())
            .put("error", error.message ?: "加载失败")
            .toString()
    }

    private fun resultLabel(result: String): String {
        return when (result) {
            LogResult.PASSED.value -> "通过"
            LogResult.BLOCKED.value -> "过滤"
            LogResult.ERROR.value -> "失败"
            else -> result
        }
    }

    private fun dnsTypeName(type: Int): String {
        return when (type) {
            1 -> "A"
            28 -> "AAAA"
            5 -> "CNAME"
            15 -> "MX"
            16 -> "TXT"
            2 -> "NS"
            12 -> "PTR"
            255 -> "ANY"
            else -> "TYPE$type"
        }
    }

    private fun <T> List<T>.weightedAverage(valueAndWeight: (T) -> Pair<Double, Int>): Double {
        var weightedTotal = 0.0
        var totalWeight = 0
        forEach { item ->
            val (value, weight) = valueAndWeight(item)
            weightedTotal += value * weight
            totalWeight += weight
        }
        return if (totalWeight == 0) 0.0 else weightedTotal / totalWeight
    }

    private companion object {
        private const val RECENT_LOG_LIMIT = 12
        private const val RECENT_CACHE_LIMIT = 8
        private const val TOP_LIST_LIMIT = 8
    }
}
