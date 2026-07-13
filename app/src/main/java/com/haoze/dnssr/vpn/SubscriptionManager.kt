package com.haoze.dnssr.vpn

import android.util.Log
import com.haoze.dnssr.data.dao.SubscriptionDao
import com.haoze.dnssr.data.entity.SubscriptionEntity
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

    /**
     * 添加新订阅并下载导入规则。
     */
    suspend fun addSubscription(
        url: String,
        kind: String = SubscriptionKind.BLOCK,
        name: String? = null
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))
        val normalizedKind = normalizeKind(kind)

        _importing.value = true
        try {
            // 先创建订阅记录
            val displayName = name?.trim()?.takeIf { it.isNotEmpty() } ?: extractNameFromUrl(url)
            val subscription = SubscriptionEntity(
                url = url,
                name = displayName,
                sourceType = SubscriptionSourceType.REMOTE,
                kind = normalizedKind,
                enabled = true,
                ruleCount = 0,
                lastUpdated = 0,
                addedAt = System.currentTimeMillis()
            )
            val id = subscriptionDao.insert(subscription)
            val saved = subscription.copy(id = id)

            // 下载并导入
            val importResult = downloadAndImport(
                url = url,
                subscriptionId = id,
                kind = normalizedKind,
                enabled = true
            )
            if (importResult.isFailure) {
                // 导入失败则删除订阅记录
                subscriptionDao.deleteById(id)
                return@withContext Result.failure(importResult.exceptionOrNull() ?: Exception("导入失败"))
            }

            val ruleCount = importResult.getOrNull() ?: 0
            subscriptionDao.update(saved.copy(ruleCount = ruleCount, lastUpdated = System.currentTimeMillis()))
            Result.success(saved.copy(ruleCount = ruleCount, lastUpdated = System.currentTimeMillis()))
        } catch (e: Exception) {
            Log.e(TAG, "添加订阅失败", e)
            Result.failure(e)
        } finally {
            _importing.value = false
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
            if (subscriptionDao.byUrlAndKind(trimmedUrl, SubscriptionKind.BLOCK) != null) {
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
        content: String,
        kind: String = SubscriptionKind.BLOCK
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("订阅名称不能为空"))
        }
        val normalizedKind = normalizeKind(kind)
        if (subscriptionDao.byUrlAndKind(sourceRef, normalizedKind) != null) {
            return@withContext Result.failure(IllegalArgumentException("该文件已作为订阅导入"))
        }

        _importing.value = true
        var id: Long? = null
        try {
            val rules = when (normalizedKind) {
                SubscriptionKind.ALLOW -> AdGuardRuleParser.parseAllowAll(content)
                else -> AdGuardRuleParser.parseAll(content)
            }
            _importProgress.value = 0 to rules.size

            val subscription = SubscriptionEntity(
                url = sourceRef,
                name = trimmedName,
                sourceType = SubscriptionSourceType.LOCAL,
                kind = normalizedKind,
                enabled = true,
                ruleCount = 0,
                lastUpdated = 0,
                addedAt = System.currentTimeMillis()
            )
            id = subscriptionDao.insert(subscription)
            importPreparedRules(rules, id, normalizedKind, enabled = true)

            val importedAt = System.currentTimeMillis()
            val saved = subscription.copy(id = id, ruleCount = rules.size, lastUpdated = importedAt)
            subscriptionDao.update(saved)
            Result.success(saved)
        } catch (e: Exception) {
            id?.let { subscriptionId ->
                when (normalizedKind) {
                    SubscriptionKind.ALLOW -> allowListManager.removeRulesBySource(sourceTag(subscriptionId))
                    else -> blockListManager.removeRulesBySource(sourceTag(subscriptionId))
                }
                subscriptionDao.deleteById(subscriptionId)
            }
            Log.e(TAG, "本地文件订阅导入失败", e)
            Result.failure(e)
        } finally {
            _importing.value = false
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
            val duplicate = subscriptionDao.byUrlAndKind(trimmedUrl, subscription.kind)
            if (duplicate != null && duplicate.id != id) {
                return@withContext Result.failure(IllegalArgumentException("This subscription URL already exists"))
            }

            _importing.value = true
            try {
                val rules = downloadRules(trimmedUrl, subscription.kind).getOrElse {
                    return@withContext Result.failure(it)
                }
                _importProgress.value = 0 to rules.size
                val source = sourceTag(id)
                when (subscription.kind) {
                    SubscriptionKind.ALLOW -> allowListManager.removeRulesBySource(source)
                    else -> blockListManager.removeRulesBySource(source)
                }
                importPreparedRules(rules, id, subscription.kind, subscription.enabled)

                val updatedAt = System.currentTimeMillis()
                subscriptionDao.setDetails(id, trimmedName, trimmedUrl, rules.size, updatedAt)
                Result.success(
                    subscription.copy(
                        name = trimmedName,
                        url = trimmedUrl,
                        ruleCount = rules.size,
                        lastUpdated = updatedAt
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to edit subscription", e)
                Result.failure(e)
            } finally {
                _importing.value = false
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
        try {
            val source = sourceTag(id)
            val rules = downloadRules(subscription.url, subscription.kind).getOrElse {
                return@withContext Result.failure(it)
            }
            when (subscription.kind) {
                SubscriptionKind.ALLOW -> allowListManager.replaceRulesBySource(rules, source, subscription.enabled)
                else -> blockListManager.replaceRulesBySource(rules, source, subscription.enabled)
            }
            val ruleCount = rules.size
            subscriptionDao.update(subscription.copy(ruleCount = ruleCount, lastUpdated = System.currentTimeMillis()))
            Result.success(ruleCount)
        } catch (e: Exception) {
            Log.e(TAG, "更新订阅失败", e)
            Result.failure(e)
        } finally {
            _importing.value = false
            _importProgress.value = -1 to 0
        }
    }

    /**
     * 删除订阅及其关联的所有规则。
     */
    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) {
        val subscription = subscriptionDao.byId(id)
        val source = sourceTag(id)
        when (subscription?.kind) {
            SubscriptionKind.ALLOW -> allowListManager.removeRulesBySource(source)
            else -> blockListManager.removeRulesBySource(source)
        }
        subscriptionDao.deleteById(id)
    }

    suspend fun setSubscriptionEnabled(id: Long, enabled: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            val subscription = subscriptionDao.byId(id)
                ?: return@withContext Result.failure(IllegalArgumentException("订阅不存在"))

            try {
                val source = sourceTag(id)
                when (subscription.kind) {
                    SubscriptionKind.ALLOW -> allowListManager.setRulesEnabledBySource(source, enabled)
                    else -> blockListManager.setRulesEnabledBySource(source, enabled)
                }
                subscriptionDao.setEnabled(id, enabled)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "切换订阅状态失败", e)
                Result.failure(e)
            }
        }

    suspend fun allSubscriptions(): List<SubscriptionEntity> = subscriptionDao.all()

    suspend fun remoteSubscriptions(): List<SubscriptionEntity> = subscriptionDao.allRemote()

    suspend fun subscriptionsByKind(kind: String): List<SubscriptionEntity> {
        return subscriptionDao.allByKind(normalizeKind(kind))
    }

    fun sourceTag(subscriptionId: Long): String = "sub_$subscriptionId"

    private suspend fun downloadAndImport(
        url: String,
        subscriptionId: Long,
        kind: String,
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
                val rules = when (kind) {
                    SubscriptionKind.ALLOW -> AdGuardRuleParser.parseAllowAll(body)
                    else -> AdGuardRuleParser.parseAll(body)
                }
                val total = rules.size
                _importProgress.value = 0 to total

                if (rules.isEmpty()) {
                    return@withContext Result.success(0)
                }

                // 分块批量导入
                val source = sourceTag(subscriptionId)
                when (kind) {
                    SubscriptionKind.ALLOW -> {
                        allowListManager.addRulesBatch(rules, source, CHUNK_SIZE, enabled) { imported ->
                            _importProgress.value = imported to total
                        }
                    }
                    else -> {
                        blockListManager.addRulesBatch(rules, source, CHUNK_SIZE, enabled) { imported ->
                            _importProgress.value = imported to total
                        }
                    }
                }

                Result.success(rules.size)
            } catch (e: Exception) {
                Log.e(TAG, "下载导入失败: $url", e)
                Result.failure(e)
            }
        }

    private suspend fun downloadRules(
        url: String,
        kind: String
    ): Result<List<AdGuardRuleParser.ParsedRule>> {
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val body = response.body?.string() ?: return Result.failure(Exception("Response body is empty"))
                Result.success(
                    when (kind) {
                        SubscriptionKind.ALLOW -> AdGuardRuleParser.parseAllowAll(body)
                        else -> AdGuardRuleParser.parseAll(body)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download subscription: $url", e)
            Result.failure(e)
        }
    }

    private suspend fun importPreparedRules(
        rules: List<AdGuardRuleParser.ParsedRule>,
        subscriptionId: Long,
        kind: String,
        enabled: Boolean
    ) {
        if (rules.isEmpty()) return
        val total = rules.size
        val source = sourceTag(subscriptionId)
        when (kind) {
            SubscriptionKind.ALLOW -> {
                allowListManager.addRulesBatch(rules, source, CHUNK_SIZE, enabled) { imported ->
                    _importProgress.value = imported to total
                }
            }
            else -> {
                blockListManager.addRulesBatch(rules, source, CHUNK_SIZE, enabled) { imported ->
                    _importProgress.value = imported to total
                }
            }
        }
    }

    private fun normalizeKind(kind: String): String {
        return when (kind) {
            SubscriptionKind.ALLOW -> SubscriptionKind.ALLOW
            else -> SubscriptionKind.BLOCK
        }
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
