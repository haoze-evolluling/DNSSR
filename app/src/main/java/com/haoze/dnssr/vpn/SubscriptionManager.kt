package com.haoze.dnssr.vpn

import android.util.Log
import com.haoze.dnssr.data.dao.SubscriptionDao
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.data.entity.SubscriptionImportState
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.data.entity.SubscriptionSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

data class RuleImportSummary(
    val blockCount: Int,
    val allowCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
    val unsupportedCount: Int
) {
    val importedCount: Int get() = blockCount + allowCount
    val skippedCount: Int get() = duplicateCount + invalidCount + unsupportedCount

    fun displayMessage(prefix: String): String =
        "$prefix：黑名单 $blockCount 条，白名单 $allowCount 条，重复 $duplicateCount 条，" +
            "无效/不支持 ${invalidCount + unsupportedCount} 条"
}

/**
 * 规则订阅管理器。
 *
 * 负责下载、解析、导入和删除订阅规则。
 * 使用分块批量导入避免大规则列表导致卡顿。
 */
class SubscriptionManager(
    private val subscriptionDao: SubscriptionDao,
    private val blockListManager: BlockListManager,
    private val allowListManager: AllowListManager
) {
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val CHUNK_SIZE = 500
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _importProgress = MutableStateFlow(-1 to 0) // (current, total) -1 表示未在导入
    val importProgress: StateFlow<Pair<Int, Int>> = _importProgress.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importingSubscriptionId = MutableStateFlow<Long?>(null)
    val importingSubscriptionId: StateFlow<Long?> = _importingSubscriptionId.asStateFlow()

    @Volatile
    private var lastImportSummary: RuleImportSummary? = null

    fun latestImportSummary(): RuleImportSummary? = lastImportSummary

    /**
     * 添加新订阅并下载导入规则。
     */
    suspend fun addSubscription(
        url: String,
        name: String? = null
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
            return@withContext Result.failure(IllegalArgumentException("订阅链接必须使用 HTTP 或 HTTPS"))
        }
        if (subscriptionDao.byUrl(trimmedUrl) != null) {
            return@withContext Result.failure(IllegalArgumentException("该订阅链接已存在"))
        }
        val normalizedKind = SubscriptionKind.BLOCK

        _importing.value = true
        var saved: SubscriptionEntity? = null
        try {
            val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: extractNameFromUrl(trimmedUrl)
            val subscription = SubscriptionEntity(
                url = trimmedUrl,
                name = displayName,
                sourceType = SubscriptionSourceType.REMOTE,
                kind = normalizedKind,
                enabled = true,
                ruleCount = 0,
                lastUpdated = 0,
                addedAt = System.currentTimeMillis(),
                importState = SubscriptionImportState.IMPORTING
            )
            val id = subscriptionDao.insert(subscription)
            saved = subscription.copy(id = id)
            _importingSubscriptionId.value = id

            val importResult = downloadAndImport(
                url = trimmedUrl,
                subscriptionId = id,
                enabled = true
            )
            if (importResult.isFailure) {
                removeRulesBySource(sourceTag(id))
                val error = importResult.exceptionOrNull() ?: Exception("导入失败")
                subscriptionDao.setImportState(id, SubscriptionImportState.FAILED, error.message)
                return@withContext Result.failure(error)
            }

            val ruleCount = importResult.getOrNull() ?: 0
            val completed = saved.copy(
                ruleCount = ruleCount,
                lastUpdated = System.currentTimeMillis(),
                importState = SubscriptionImportState.READY,
                importError = null
            )
            subscriptionDao.update(completed)
            Result.success(completed)
        } catch (e: Exception) {
            Log.e(TAG, "添加订阅失败", e)
            saved?.let { subscription ->
                removeRulesBySource(sourceTag(subscription.id))
                subscriptionDao.setImportState(
                    subscription.id,
                    SubscriptionImportState.FAILED,
                    e.message ?: "导入失败"
                )
            }
            Result.failure(e)
        } finally {
            _importing.value = false
            _importingSubscriptionId.value = null
            _importProgress.value = -1 to 0
        }
    }

    /** Saves a remote subscription without downloading its rules. */
    suspend fun addRemoteSubscription(url: String, name: String): Result<SubscriptionEntity> =
        withContext(Dispatchers.IO) {
            val trimmedUrl = url.trim()
            val trimmedName = name.trim()
            if (trimmedName.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("订阅名称不能为空"))
            }
            if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
                return@withContext Result.failure(IllegalArgumentException("订阅链接必须使用 HTTP 或 HTTPS"))
            }
            if (subscriptionDao.byUrl(trimmedUrl) != null) {
                return@withContext Result.failure(IllegalArgumentException("该订阅链接已存在"))
            }

            try {
                val subscription = SubscriptionEntity(
                    url = trimmedUrl,
                    name = trimmedName,
                    sourceType = SubscriptionSourceType.REMOTE,
                    kind = SubscriptionKind.BLOCK,
                    enabled = true
                )
                Result.success(subscription.copy(id = subscriptionDao.insert(subscription)))
            } catch (e: Exception) {
                Log.e(TAG, "保存订阅失败", e)
                Result.failure(e)
            }
        }

    suspend fun addLocalSubscription(
        sourceRef: String,
        name: String,
        contentLoader: () -> String
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("订阅名称不能为空"))
        }
        val normalizedKind = SubscriptionKind.BLOCK
        if (subscriptionDao.byUrl(sourceRef) != null) {
            return@withContext Result.failure(IllegalArgumentException("该文件已作为订阅导入"))
        }

        _importing.value = true
        var saved: SubscriptionEntity? = null
        try {
            val subscription = SubscriptionEntity(
                url = sourceRef,
                name = trimmedName,
                sourceType = SubscriptionSourceType.LOCAL,
                kind = normalizedKind,
                enabled = true,
                ruleCount = 0,
                lastUpdated = 0,
                addedAt = System.currentTimeMillis(),
                importState = SubscriptionImportState.IMPORTING
            )
            val id = subscriptionDao.insert(subscription)
            saved = subscription.copy(id = id)
            _importingSubscriptionId.value = id

            val content = contentLoader()
            val rules = AdGuardRuleParser.parseCategorized(content)
            if (rules.isEmpty()) {
                throw IllegalArgumentException("文件中没有可导入的有效 DNS 规则")
            }
            _importProgress.value = 0 to rules.size
            val summary = importPreparedRules(rules, id, enabled = true)
            lastImportSummary = summary

            val importedAt = System.currentTimeMillis()
            val completed = saved.copy(
                ruleCount = summary.importedCount,
                lastUpdated = importedAt,
                importState = SubscriptionImportState.READY,
                importError = null
            )
            subscriptionDao.update(completed)
            Result.success(completed)
        } catch (e: Exception) {
            saved?.let { subscription ->
                removeRulesBySource(sourceTag(subscription.id))
                subscriptionDao.setImportState(
                    subscription.id,
                    SubscriptionImportState.FAILED,
                    e.message ?: "导入失败"
                )
            }
            Log.e(TAG, "本地文件订阅导入失败", e)
            Result.failure(e)
        } finally {
            _importing.value = false
            _importingSubscriptionId.value = null
            _importProgress.value = -1 to 0
        }
    }

    suspend fun renameSubscription(id: Long, name: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("订阅名称不能为空"))
            }
            val subscription = subscriptionDao.byId(id)
                ?: return@withContext Result.failure(IllegalArgumentException("订阅不存在"))
            try {
                subscriptionDao.setName(subscription.id, trimmed)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "重命名订阅失败", e)
                Result.failure(e)
            }
        }

    /**
     * 更新订阅（删除旧规则后重新下载导入）。
     */
    suspend fun editSubscription(id: Long, url: String, name: String): Result<SubscriptionEntity> =
        withContext(Dispatchers.IO) {
            val trimmedName = name.trim()
            val trimmedUrl = url.trim()
            if (trimmedName.isEmpty() || trimmedUrl.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Subscription name and URL are required"))
            }
            if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
                return@withContext Result.failure(IllegalArgumentException("Subscription URL must use HTTP or HTTPS"))
            }
            if (_importing.value) {
                return@withContext Result.failure(IllegalStateException("A subscription import is already running"))
            }

            val subscription = subscriptionDao.byId(id)
                ?: return@withContext Result.failure(IllegalArgumentException("Subscription does not exist"))
            if (subscription.sourceType == SubscriptionSourceType.LOCAL) {
                return@withContext Result.failure(IllegalStateException("本地文件订阅仅支持重命名"))
            }
            if (subscription.url == trimmedUrl) {
                subscriptionDao.setName(id, trimmedName)
                return@withContext Result.success(subscription.copy(name = trimmedName))
            }
            val duplicate = subscriptionDao.byUrl(trimmedUrl)
            if (duplicate != null && duplicate.id != id) {
                return@withContext Result.failure(IllegalArgumentException("This subscription URL already exists"))
            }

            _importing.value = true
            _importingSubscriptionId.value = id
            subscriptionDao.setImportState(id, SubscriptionImportState.IMPORTING, null)
            try {
                val rules = downloadRules(trimmedUrl).getOrElse { throw it }
                if (rules.isEmpty()) {
                    throw IllegalArgumentException("订阅中没有可导入的有效 DNS 规则，已保留原规则")
                }
                _importProgress.value = 0 to rules.size
                val summary = replacePreparedRules(rules, id, subscription.enabled)
                lastImportSummary = summary

                val updatedAt = System.currentTimeMillis()
                subscriptionDao.setDetails(id, trimmedName, trimmedUrl, summary.importedCount, updatedAt)
                subscriptionDao.setImportState(id, SubscriptionImportState.READY, null)
                Result.success(
                    subscription.copy(
                        name = trimmedName,
                        url = trimmedUrl,
                        ruleCount = summary.importedCount,
                        lastUpdated = updatedAt
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit subscription", e)
                subscriptionDao.setImportState(
                    id,
                    SubscriptionImportState.FAILED,
                    e.message ?: "更新失败"
                )
                Result.failure(e)
            } finally {
                _importing.value = false
                _importingSubscriptionId.value = null
                _importProgress.value = -1 to 0
            }
        }

    suspend fun updateSubscription(id: Long): Result<Int> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))

        val subscription = subscriptionDao.byId(id)
            ?: return@withContext Result.failure(IllegalArgumentException("订阅不存在"))
        if (subscription.sourceType == SubscriptionSourceType.LOCAL) {
            return@withContext Result.failure(IllegalStateException("本地文件订阅无法更新"))
        }

        _importing.value = true
        _importingSubscriptionId.value = id
        subscriptionDao.setImportState(id, SubscriptionImportState.IMPORTING, null)
        try {
            val rules = downloadRules(subscription.url).getOrElse { throw it }
            if (rules.isEmpty()) {
                throw IllegalArgumentException("订阅中没有可导入的有效 DNS 规则，已保留原规则")
            }
            _importProgress.value = 0 to rules.size
            replacePreparedRules(rules, id, subscription.enabled)
            val ruleCount = rules.size
            lastImportSummary = rules.toSummary(rules.blockRules.size, rules.allowRules.size)
            subscriptionDao.update(
                subscription.copy(
                    ruleCount = ruleCount,
                    lastUpdated = System.currentTimeMillis(),
                    importState = SubscriptionImportState.READY,
                    importError = null
                )
            )
            Result.success(ruleCount)
        } catch (e: Exception) {
            Log.e(TAG, "更新订阅失败", e)
            subscriptionDao.setImportState(
                id,
                SubscriptionImportState.FAILED,
                e.message ?: "更新失败"
            )
            Result.failure(e)
        } finally {
            _importing.value = false
            _importingSubscriptionId.value = null
            _importProgress.value = -1 to 0
        }
    }

    /**
     * 删除订阅及其关联的所有规则。
     */
    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) {
        val source = sourceTag(id)
        removeRulesBySource(source)
        subscriptionDao.deleteById(id)
    }

    suspend fun setSubscriptionEnabled(id: Long, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val subscription = subscriptionDao.byId(id)
                ?: return@withContext Result.failure(IllegalArgumentException("订阅不存在"))

            try {
                val source = sourceTag(id)
                blockListManager.setRulesEnabledBySource(source, enabled)
                allowListManager.setRulesEnabledBySource(source, enabled)
                subscriptionDao.setEnabled(id, enabled)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "切换订阅状态失败", e)
                Result.failure(e)
            }
        }

    suspend fun allSubscriptions(): List<SubscriptionEntity> = subscriptionDao.all()

    suspend fun remoteSubscriptions(): List<SubscriptionEntity> = subscriptionDao.allRemote()

    fun sourceTag(subscriptionId: Long): String = "sub_$subscriptionId"

    private suspend fun downloadAndImport(
        url: String,
        subscriptionId: Long,
        enabled: Boolean
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code}"))
                }

                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("响应体为空"))

                // 解析规则
                val rules = AdGuardRuleParser.parseCategorized(body)
                val total = rules.size
                _importProgress.value = 0 to total

                if (rules.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("订阅中没有可导入的有效 DNS 规则"))
                }

                // 分块批量导入
                val summary = importPreparedRules(rules, subscriptionId, enabled)
                lastImportSummary = summary

                Result.success(summary.importedCount)
            } catch (e: Exception) {
                Log.e(TAG, "下载导入失败: $url", e)
                Result.failure(e)
            }
        }

    private suspend fun downloadRules(
        url: String
    ): Result<AdGuardRuleParser.CategorizedRules> {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body?.string() ?: return Result.failure(Exception("Response body is empty"))
                Result.success(AdGuardRuleParser.parseCategorized(body))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download subscription: $url", e)
            Result.failure(e)
        }
    }

    private suspend fun importPreparedRules(
        rules: AdGuardRuleParser.CategorizedRules,
        subscriptionId: Long,
        enabled: Boolean
    ): RuleImportSummary {
        if (rules.isEmpty()) return rules.toSummary(0, 0)
        val total = rules.size
        val source = sourceTag(subscriptionId)
        var importedBefore = 0
        val insertedBlock = blockListManager.addRulesBatch(rules.blockRules, source, CHUNK_SIZE, enabled) { imported ->
            _importProgress.value = imported to total
        }
        importedBefore += rules.blockRules.size
        val insertedAllow = allowListManager.addRulesBatch(rules.allowRules, source, CHUNK_SIZE, enabled) { imported ->
            _importProgress.value = importedBefore + imported to total
        }
        return rules.toSummary(insertedBlock, insertedAllow)
    }

    private suspend fun replacePreparedRules(
        rules: AdGuardRuleParser.CategorizedRules,
        subscriptionId: Long,
        enabled: Boolean
    ): RuleImportSummary {
        val source = sourceTag(subscriptionId)
        val oldBlockRules = blockListManager.parsedRulesBySource(source)
        val oldAllowRules = allowListManager.parsedRulesBySource(source)
        return try {
            blockListManager.replaceRulesBySource(rules.blockRules, source, enabled)
            allowListManager.replaceRulesBySource(rules.allowRules, source, enabled)
            rules.toSummary(rules.blockRules.size, rules.allowRules.size)
        } catch (e: Exception) {
            blockListManager.replaceRulesBySource(oldBlockRules, source, enabled)
            allowListManager.replaceRulesBySource(oldAllowRules, source, enabled)
            throw e
        }
    }

    private fun AdGuardRuleParser.CategorizedRules.toSummary(
        insertedBlock: Int,
        insertedAllow: Int
    ): RuleImportSummary = RuleImportSummary(
        blockCount = insertedBlock,
        allowCount = insertedAllow,
        duplicateCount = duplicateCount + (size - insertedBlock - insertedAllow).coerceAtLeast(0),
        invalidCount = invalidCount,
        unsupportedCount = unsupportedCount
    )

    private suspend fun removeRulesBySource(source: String) {
        blockListManager.removeRulesBySource(source)
        allowListManager.removeRulesBySource(source)
    }

    private fun extractNameFromUrl(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path ?: ""
            val fileName = path.substringAfterLast('/')
            if (fileName.isNotBlank()) fileName else uri.host ?: url
        } catch (_: Exception) {
            url
        }
    }
}
