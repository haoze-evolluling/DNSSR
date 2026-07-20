package com.haoze.dnssr.vpn

import android.util.Log
import com.haoze.dnssr.data.dao.SubscriptionDao
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.data.entity.SubscriptionImportState
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.data.entity.SubscriptionSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class RuleImportSummary(
    val blockCount: Int,
    val allowCount: Int,
    val rewriteCount: Int = 0,
    val duplicateCount: Int,
    val invalidCount: Int,
    val unsupportedCount: Int
) {
    val importedCount: Int get() = blockCount + allowCount + rewriteCount
    val skippedCount: Int get() = duplicateCount + invalidCount + unsupportedCount

    fun displayMessage(prefix: String): String =
        "$prefix：黑名单 $blockCount 条，白名单 $allowCount 条，覆写 $rewriteCount 条，重复 $duplicateCount 条，" +
            "无效/不支持 ${invalidCount + unsupportedCount} 条"
}

sealed interface SubscriptionUpdateOutcome {
    data class Updated(val ruleCount: Int) : SubscriptionUpdateOutcome
    data class NotModified(val ruleCount: Int) : SubscriptionUpdateOutcome
    data class Failed(val error: String, val retryable: Boolean) : SubscriptionUpdateOutcome
}

private sealed interface DownloadResult {
    data class NotModified(val etag: String?, val lastModified: String?) : DownloadResult
    data class Content(
        val rules: AdGuardRuleParser.CategorizedRules,
        val ruleSetHash: String,
        val etag: String?,
        val lastModified: String?
    ) : DownloadResult
}

private class SubscriptionUpdateException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : Exception(message, cause)

private data class InitialImportResult(
    val ruleCount: Int,
    val ruleSetHash: String,
    val etag: String?,
    val lastModified: String?
)

/**
 * 规则订阅管理器。
 *
 * 负责下载、解析、导入和删除订阅规则。
 * 使用分块批量导入避免大规则列表导致卡顿。
 */
class SubscriptionManager(
    private val subscriptionDao: SubscriptionDao,
    private val blockListManager: BlockListManager,
    private val allowListManager: AllowListManager,
    private val rewriteRuleManager: RewriteRuleManager
) {
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val CHUNK_SIZE = 500
        private val MIRROR_PLACEHOLDERS = setOf(
            "{url}", "{urlEncoded}", "{scheme}", "{host}", "{path}", "{pathAndQuery}"
        )
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
        name: String? = null,
        kind: String = SubscriptionKind.BLOCK,
        mirrorTemplate: String? = null,
        mirrorFallback: Boolean = true
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))
        val trimmedUrl = url.trim()
        if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
            return@withContext Result.failure(IllegalArgumentException("订阅链接必须使用 HTTP 或 HTTPS"))
        }
        if (subscriptionDao.byUrl(trimmedUrl) != null) {
            return@withContext Result.failure(IllegalArgumentException("该订阅链接已存在"))
        }
        val normalizedKind = kind
        val normalizedMirror = normalizeMirrorTemplate(mirrorTemplate)

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
                importState = SubscriptionImportState.IMPORTING,
                mirrorTemplate = normalizedMirror,
                mirrorFallback = mirrorFallback
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

            val imported = importResult.getOrThrow()
            val completed = saved.copy(
                ruleCount = imported.ruleCount,
                lastUpdated = System.currentTimeMillis(),
                importState = SubscriptionImportState.READY,
                importError = null,
                httpEtag = imported.etag,
                httpLastModified = imported.lastModified,
                ruleSetHash = imported.ruleSetHash,
                lastAttemptAt = System.currentTimeMillis(),
                consecutiveFailureCount = 0
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
        kind: String = SubscriptionKind.BLOCK,
        contentLoader: () -> String
    ): Result<SubscriptionEntity> = withContext(Dispatchers.IO) {
        if (_importing.value) return@withContext Result.failure(IllegalStateException("正在导入中"))

        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("订阅名称不能为空"))
        }
        val normalizedKind = kind
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
            val rules = if (kind == SubscriptionKind.REWRITE) AdGuardRuleParser.parseHostsRewrite(content) else emptyList()
            val categorized = if (kind == SubscriptionKind.REWRITE) null else AdGuardRuleParser.parseCategorized(content)
            if (rules.isEmpty() && categorized?.isEmpty() != false) {
                throw IllegalArgumentException("文件中没有可导入的有效 DNS 规则")
            }
            _importProgress.value = 0 to (categorized?.size ?: rules.size)
            val summary = if (kind == SubscriptionKind.REWRITE) importRewriteRules(rules, id, true) else importPreparedRules(categorized!!, id, enabled = true)
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
    suspend fun editSubscription(
        id: Long,
        url: String,
        name: String,
        mirrorTemplate: String? = null,
        mirrorFallback: Boolean = true
    ): Result<SubscriptionEntity> =
        withContext(Dispatchers.IO) {
            val trimmedName = name.trim()
            val trimmedUrl = url.trim()
            val normalizedMirror = normalizeMirrorTemplate(mirrorTemplate)
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
            if (subscription.url == trimmedUrl &&
                subscription.mirrorTemplate == normalizedMirror &&
                subscription.mirrorFallback == mirrorFallback
            ) {
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
                val download = downloadRules(subscription.copy(
                    url = trimmedUrl,
                    mirrorTemplate = normalizedMirror,
                    mirrorFallback = mirrorFallback,
                    httpEtag = null,
                    httpLastModified = null,
                    ruleSetHash = null
                )) as DownloadResult.Content
                val rules = download.rules
                _importProgress.value = 0 to rules.size
                val summary = replaceDownloadedRules(rules, id, subscription.enabled, subscription.kind)
                lastImportSummary = summary

                val updatedAt = System.currentTimeMillis()
                val updated = subscription.copy(
                        name = trimmedName,
                        url = trimmedUrl,
                        mirrorTemplate = normalizedMirror,
                        mirrorFallback = mirrorFallback,
                        ruleCount = summary.importedCount,
                        lastUpdated = updatedAt,
                        importState = SubscriptionImportState.READY,
                        importError = null,
                        httpEtag = download.etag,
                        httpLastModified = download.lastModified,
                        ruleSetHash = download.ruleSetHash,
                        lastAttemptAt = updatedAt,
                        consecutiveFailureCount = 0
                    )
                subscriptionDao.update(updated)
                Result.success(updated)
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

    suspend fun updateSubscription(id: Long): SubscriptionUpdateOutcome = withContext(Dispatchers.IO) {
        if (_importing.value) {
            return@withContext SubscriptionUpdateOutcome.Failed("正在导入中", retryable = false)
        }

        val subscription = subscriptionDao.byId(id)
            ?: return@withContext SubscriptionUpdateOutcome.Failed("订阅不存在", retryable = false)
        if (subscription.sourceType == SubscriptionSourceType.LOCAL) {
            return@withContext SubscriptionUpdateOutcome.Failed("本地文件订阅无法更新", retryable = false)
        }

        _importing.value = true
        _importingSubscriptionId.value = id
        subscriptionDao.setImportState(id, SubscriptionImportState.IMPORTING, null)
        try {
            when (val download = downloadRules(subscription)) {
                is DownloadResult.NotModified -> {
                    subscriptionDao.markNotModified(
                        id,
                        SubscriptionImportState.READY,
                        System.currentTimeMillis(),
                        download.etag ?: subscription.httpEtag,
                        download.lastModified ?: subscription.httpLastModified
                    )
                    SubscriptionUpdateOutcome.NotModified(subscription.ruleCount)
                }
                is DownloadResult.Content -> {
                    if (subscription.ruleSetHash != null && subscription.ruleSetHash == download.ruleSetHash) {
                        subscriptionDao.markNotModified(
                            id,
                            SubscriptionImportState.READY,
                            System.currentTimeMillis(),
                            download.etag,
                            download.lastModified
                        )
                        return@withContext SubscriptionUpdateOutcome.NotModified(subscription.ruleCount)
                    }
                    val rules = download.rules
                    _importProgress.value = 0 to rules.size
                    val summary = replaceDownloadedRules(rules, id, subscription.enabled, subscription.kind)
                    val ruleCount = summary.importedCount
                    lastImportSummary = summary
                    val now = System.currentTimeMillis()
                    subscriptionDao.update(
                        subscription.copy(
                            ruleCount = ruleCount,
                            lastUpdated = now,
                            importState = SubscriptionImportState.READY,
                            importError = null,
                            httpEtag = download.etag,
                            httpLastModified = download.lastModified,
                            ruleSetHash = download.ruleSetHash,
                            lastAttemptAt = now,
                            consecutiveFailureCount = 0
                        )
                    )
                    SubscriptionUpdateOutcome.Updated(ruleCount)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SubscriptionUpdateException) {
            Log.e(TAG, "更新订阅失败", e)
            subscriptionDao.markUpdateFailed(
                id,
                SubscriptionImportState.FAILED,
                e.message ?: "更新失败",
                System.currentTimeMillis()
            )
            SubscriptionUpdateOutcome.Failed(e.message ?: "更新失败", e.retryable)
        } catch (e: Exception) {
            Log.e(TAG, "更新订阅失败", e)
            subscriptionDao.markUpdateFailed(
                id,
                SubscriptionImportState.FAILED,
                e.message ?: "更新失败",
                System.currentTimeMillis()
            )
            SubscriptionUpdateOutcome.Failed(e.message ?: "更新失败", retryable = true)
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
                rewriteRuleManager.setRulesEnabledBySource(source, enabled)
                subscriptionDao.setEnabled(id, enabled)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "切换订阅状态失败", e)
                Result.failure(e)
            }
        }

    suspend fun allSubscriptions(): List<SubscriptionEntity> = subscriptionDao.all()

    suspend fun remoteSubscriptions(): List<SubscriptionEntity> = subscriptionDao.allRemote()

    suspend fun enabledRemoteSubscriptions(): List<SubscriptionEntity> = subscriptionDao.allEnabledRemote()

    fun sourceTag(subscriptionId: Long): String = "sub_$subscriptionId"

    private suspend fun downloadAndImport(
        url: String,
        subscriptionId: Long,
        enabled: Boolean
    ): Result<InitialImportResult> =
        withContext(Dispatchers.IO) {
            try {
                val subscription = subscriptionDao.byId(subscriptionId) ?: SubscriptionEntity(url = url, name = url)
                val download = downloadRules(subscription) as DownloadResult.Content
                val rules = download.rules
                val total = rules.size
                _importProgress.value = 0 to total

                if (rules.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("订阅中没有可导入的有效 DNS 规则"))
                }

                // 分块批量导入
                val summary = if (subscription.kind == SubscriptionKind.REWRITE) importRewriteRules(rules.rewriteRules, subscriptionId, enabled) else importPreparedRules(rules, subscriptionId, enabled)
                lastImportSummary = summary

                Result.success(
                    InitialImportResult(
                        summary.importedCount,
                        download.ruleSetHash,
                        download.etag,
                        download.lastModified
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "下载导入失败: $url", e)
                Result.failure(e)
            }
        }

    private fun downloadRules(subscription: SubscriptionEntity): DownloadResult {
        return try {
            executeDownload(subscription, useValidators = true)
        } catch (e: SubscriptionUpdateException) {
            throw e
        } catch (e: IOException) {
            throw SubscriptionUpdateException(e.message ?: "网络请求失败", retryable = true, cause = e)
        }
    }

    private fun executeDownload(subscription: SubscriptionEntity, useValidators: Boolean): DownloadResult {
        val mirrorUrl = subscription.mirrorTemplate?.let { buildMirrorUrl(it, subscription.url) }
        if (mirrorUrl != null) {
            try {
                return executeDownloadAt(subscription, mirrorUrl, useValidators)
            } catch (e: Exception) {
                if (!subscription.mirrorFallback || e is CancellationException) throw e
                Log.w(TAG, "镜像下载失败，回退原始订阅地址: $mirrorUrl", e)
            }
        }
        return executeDownloadAt(subscription, subscription.url, useValidators)
    }

    private fun executeDownloadAt(
        subscription: SubscriptionEntity,
        requestUrl: String,
        useValidators: Boolean
    ): DownloadResult {
        val request = Request.Builder().url(requestUrl).apply {
            if (useValidators) {
                subscription.httpEtag?.let { header("If-None-Match", it) }
                subscription.httpLastModified?.let { header("If-Modified-Since", it) }
            }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (response.code == 304) {
                return@use DownloadResult.NotModified(
                    response.header("ETag"),
                    response.header("Last-Modified")
                )
            }
            if (response.code == 412 && useValidators) {
                return@use executeDownloadAt(subscription, requestUrl, useValidators = false)
            }
            if (!response.isSuccessful) throw httpFailure(response)
            val body = response.body?.string()
                ?: throw SubscriptionUpdateException("订阅响应为空", retryable = false)
            val rules = if (subscription.kind == SubscriptionKind.REWRITE) {
                val rewrites = AdGuardRuleParser.parseHostsRewrite(body)
                AdGuardRuleParser.CategorizedRules(rewriteRules = rewrites)
            } else AdGuardRuleParser.parseCategorized(body)
            if (rules.isEmpty()) {
                throw SubscriptionUpdateException(
                    "订阅中没有可导入的有效 DNS 规则，已保留原规则",
                    retryable = false
                )
            }
            DownloadResult.Content(
                rules = rules,
                ruleSetHash = calculateRuleSetHash(rules),
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified")
            )
        }
    }

    private fun normalizeMirrorTemplate(template: String?): String? {
        val normalized = template?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(MIRROR_PLACEHOLDERS.any { it in normalized }) {
            "镜像模板必须包含 {url}、{urlEncoded}、{scheme}、{host}、{path} 或 {pathAndQuery}"
        }
        buildMirrorUrl(normalized, "https://example.com/rules.txt")
        return normalized
    }

    private fun buildMirrorUrl(template: String, originalUrl: String): String {
        val encoded = URLEncoder.encode(originalUrl, Charsets.UTF_8.name()).replace("+", "%20")
        val uri = runCatching { URI(originalUrl) }.getOrElse {
            throw IllegalArgumentException("原始订阅地址格式无效", it)
        }
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val pathAndQuery = path + (uri.rawQuery?.let { "?$it" } ?: "")
        val result = template
            .replace("{urlEncoded}", encoded)
            .replace("{url}", originalUrl)
            .replace("{scheme}", uri.scheme.orEmpty())
            .replace("{host}", uri.host.orEmpty())
            .replace("{pathAndQuery}", pathAndQuery)
            .replace("{path}", path)
        require(result.startsWith("https://") || result.startsWith("http://")) {
            "镜像模板生成的地址必须使用 HTTP 或 HTTPS"
        }
        return result
    }

    private fun httpFailure(response: Response): SubscriptionUpdateException {
        val retryable = response.code in setOf(408, 425, 429) || response.code in 500..599
        return SubscriptionUpdateException("HTTP ${response.code}", retryable)
    }

    private fun calculateRuleSetHash(rules: AdGuardRuleParser.CategorizedRules): String {
        val digest = MessageDigest.getInstance("SHA-256")
        rules.blockRules.map { it.pattern }.sorted().forEach {
            digest.update("B\u0000$it\n".toByteArray(Charsets.UTF_8))
        }
        rules.allowRules.map { it.pattern }.sorted().forEach {
            digest.update("A\u0000$it\n".toByteArray(Charsets.UTF_8))
        }
        rules.rewriteRules.sortedBy { it.pattern + it.targetValue }.forEach { digest.update("R\u0000${it.pattern}\u0000${it.targetType}\u0000${it.targetValue}\n".toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
        val total = rules.size
        return try {
            blockListManager.replaceRulesBySource(
                rules.blockRules,
                source,
                enabled
            ) { imported ->
                _importProgress.value = imported to total
            }
            allowListManager.replaceRulesBySource(
                rules.allowRules,
                source,
                enabled
            ) { imported ->
                _importProgress.value = rules.blockRules.size + imported to total
            }
            rules.toSummary(rules.blockRules.size, rules.allowRules.size)
        } catch (e: Exception) {
            blockListManager.replaceRulesBySource(oldBlockRules, source, enabled)
            allowListManager.replaceRulesBySource(oldAllowRules, source, enabled)
            throw e
        }
    }

    private suspend fun importRewriteRules(rules: List<RewriteRule>, subscriptionId: Long, enabled: Boolean): RuleImportSummary {
        val inserted = rewriteRuleManager.addRules(rules, sourceTag(subscriptionId), enabled, CHUNK_SIZE) { imported ->
            _importProgress.value = imported to rules.size
        }
        return RuleImportSummary(0, 0, inserted, (rules.size - inserted).coerceAtLeast(0), 0, 0)
    }

    private suspend fun replaceDownloadedRules(rules: AdGuardRuleParser.CategorizedRules, subscriptionId: Long, enabled: Boolean, kind: String): RuleImportSummary {
        if (kind != SubscriptionKind.REWRITE) return replacePreparedRules(rules, subscriptionId, enabled)
        val source = sourceTag(subscriptionId)
        val old = rewriteRuleManager.rulesBySource(source)
        return try {
            rewriteRuleManager.replaceRulesBySource(rules.rewriteRules, source, enabled, CHUNK_SIZE) { imported ->
                _importProgress.value = imported to rules.rewriteRules.size
            }
            RuleImportSummary(0, 0, rules.rewriteRules.size, 0, rules.invalidCount, rules.unsupportedCount)
        } catch (e: Exception) { rewriteRuleManager.replaceRulesBySource(old, source, enabled); throw e }
    }

    private fun AdGuardRuleParser.CategorizedRules.toSummary(
        insertedBlock: Int,
        insertedAllow: Int
    ): RuleImportSummary = RuleImportSummary(
        blockCount = insertedBlock,
        allowCount = insertedAllow,
        rewriteCount = 0,
        duplicateCount = duplicateCount + (size - insertedBlock - insertedAllow).coerceAtLeast(0),
        invalidCount = invalidCount,
        unsupportedCount = unsupportedCount
    )

    private suspend fun removeRulesBySource(source: String) {
        blockListManager.removeRulesBySource(source)
        allowListManager.removeRulesBySource(source)
        rewriteRuleManager.removeRulesBySource(source)
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
