package com.haoze.dnssr.ui

import android.content.Context
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.AdGuardRuleParser
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.SubscriptionManager
import com.haoze.dnssr.vpn.RewriteRuleManager
import org.json.JSONArray
import org.json.JSONObject

data class ConfigExportSelection(
    val providers: Boolean,
    val bootstrapIps: Boolean,
    val subscriptions: Boolean,
    val excludedApps: Boolean
)

data class ConfigImportResult(
    val added: Int,
    val skipped: Int,
    val failed: Int,
    val excludedAppsUpdated: Boolean
) {
    fun message(): String = "导入完成：新增 $added 项，跳过 $skipped 项，失败 $failed 项"
}

data class ConfigImportProgress(
    val processed: Int,
    val total: Int,
    val currentItem: String
)

class ConfigTransferManager(private val context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val subscriptionManager = SubscriptionManager(
        database.subscriptionDao(),
        BlockListManager(database.blockRuleDao()),
        AllowListManager(database.allowRuleDao()),
        RewriteRuleManager(database.rewriteRuleDao(), java.io.File(context.filesDir, "rule-index"))
    )

    suspend fun export(selection: ConfigExportSelection): String {
        val root = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())

        if (selection.providers) {
            root.put("providers", JSONArray().apply {
                DnsProvider.loadUserProviders(context).forEach { provider ->
                    put(JSONObject()
                        .put("name", provider.name)
                        .put("protocol", provider.protocol.name)
                        .put("url", provider.url)
                        .put("host", provider.host)
                        .put("port", provider.port))
                }
            })
        }
        if (selection.bootstrapIps) {
            root.put("bootstrapIps", JSONArray().apply {
                AppSettings.loadBootstrapIpEntries(context).filterNot { it.isPreset }.forEach { entry ->
                    put(JSONObject()
                        .put("name", entry.name)
                        .put("ip", entry.ip)
                        .put("enabled", entry.enabled))
                }
            })
        }
        if (selection.subscriptions) {
            root.put("subscriptions", JSONArray().apply {
                subscriptionManager.remoteSubscriptions().forEach { subscription ->
                    put(JSONObject()
                        .put("name", subscription.name)
                        .put("url", subscription.url))
                }
            })
        }
        if (selection.excludedApps) {
            root.put("excludedApps", JSONArray().apply {
                AppSettings.getExcludedAppPackages(context).forEach(::put)
            })
        }
        return root.toString(2)
    }

    suspend fun exportRules(onProgress: (Float, String) -> Unit = { _, _ -> }): RuleExportResult {
        onProgress(0f, "正在读取白名单规则")
        val allowPatterns = database.allowRuleDao().enabledPatterns()
            .mapNotNull(AdGuardRuleParser::parseAllowLine)
            .mapTo(sortedSetOf()) { it.pattern }
        onProgress(0.2f, "正在读取拦截规则")
        val blockPatterns = database.blockRuleDao().enabledPatterns()
            .mapNotNull(AdGuardRuleParser::parseLine)
            .mapTo(sortedSetOf()) { it.pattern }
            .apply { removeAll(allowPatterns) }
        onProgress(0.4f, "正在生成导出文件")
        val exportedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val totalRules = blockPatterns.size + allowPatterns.size
        var generatedRules = 0
        val content = buildString {
            appendLine("! DNSSR rules export")
            appendLine("! Exported at: $exportedAt")
            appendLine("! Block rules: ${blockPatterns.size}; allow rules: ${allowPatterns.size}")
            appendLine()
            blockPatterns.forEach {
                appendLine("||$it^")
                generatedRules++
                onProgress(0.4f + 0.2f * generatedRules / totalRules.coerceAtLeast(1), "正在生成导出文件")
            }
            allowPatterns.forEach {
                appendLine("@@||$it^")
                generatedRules++
                onProgress(0.4f + 0.2f * generatedRules / totalRules.coerceAtLeast(1), "正在生成导出文件")
            }
        }
        onProgress(0.6f, "正在写入文件")
        return RuleExportResult(content, blockPatterns.size, allowPatterns.size)
    }

    suspend fun import(
        content: String,
        onProgress: (ConfigImportProgress) -> Unit = {}
    ): ConfigImportResult {
        val config = parseAndValidate(content)
        var added = 0
        var skipped = 0
        var failed = 0
        var excludedAppsUpdated = false
        var processed = 0
        val total = config.providers.size + config.bootstrapIps.size + config.subscriptions.size + config.excludedApps.size

        fun report(item: String) {
            onProgress(ConfigImportProgress(processed, total, item))
        }

        fun complete(item: String) {
            processed++
            onProgress(ConfigImportProgress(processed, total, item))
        }

        val existingProviderKeys = DnsProvider.loadUserProviders(context)
            .map(::providerKey).toMutableSet()
        config.providers.forEach { provider ->
            val item = "DNS 服务商：${provider.name}"
            report(item)
            val key = providerKey(provider)
            if (!existingProviderKeys.add(key)) {
                skipped++
            } else {
                DnsProvider.addUserProvider(
                    context, provider.name, provider.protocol, provider.url, provider.host, provider.port
                )
                added++
            }
            complete(item)
        }

        val existingIps = AppSettings.loadBootstrapIpEntries(context)
            .filterNot { it.isPreset }.map { it.ip.lowercase() }.toMutableSet()
        config.bootstrapIps.forEach { entry ->
            val item = "Bootstrap IP：${entry.name}"
            report(item)
            if (!existingIps.add(entry.ip.lowercase())) {
                skipped++
            } else {
                val saved = AppSettings.addCustomBootstrapIp(context, entry.name, entry.ip)
                if (saved == null) {
                    failed++
                } else {
                    AppSettings.setBootstrapIpEnabled(context, saved.id, entry.enabled)
                    added++
                }
            }
            complete(item)
        }

        val existingSubscriptionKeys = subscriptionManager.remoteSubscriptions()
            .map { subscriptionKey(it.url) }.toMutableSet()
        config.subscriptions.forEach { entry ->
            val item = "规则订阅：${entry.name}"
            report(item)
            val key = subscriptionKey(entry.url)
            if (!existingSubscriptionKeys.add(key)) {
                skipped++
            } else {
                val result = subscriptionManager.addRemoteSubscription(entry.url, entry.name)
                if (result.isFailure) {
                    failed++
                } else {
                    added++
                }
            }
            complete(item)
        }

        if (config.excludedApps.isNotEmpty()) {
            val installedPackages = context.packageManager.getInstalledApplications(0)
                .mapTo(mutableSetOf()) { it.packageName }
            val validPackages = config.excludedApps.filter { it in installedPackages }.toSet()
            val invalidCount = config.excludedApps.size - validPackages.size
            val existingPackages = AppSettings.getExcludedAppPackages(context)
            val newPackages = validPackages - existingPackages
            AppSettings.setExcludedAppPackages(context, existingPackages + validPackages)
            excludedAppsUpdated = newPackages.isNotEmpty()
            added += newPackages.size
            skipped += validPackages.size - newPackages.size + invalidCount
            config.excludedApps.forEach { packageName -> complete("排除应用：$packageName") }
        }
        return ConfigImportResult(added, skipped, failed, excludedAppsUpdated)
    }

    private fun parseAndValidate(content: String): TransferConfig {
        val root = try {
            JSONObject(content)
        } catch (_: Exception) {
            throw IllegalArgumentException("配置文件不是有效的 JSON")
        }
        if (root.optInt("formatVersion", -1) !in SUPPORTED_FORMAT_VERSIONS) {
            throw IllegalArgumentException("不支持的配置文件版本")
        }

        val providers = root.optionalArray("providers").mapObjects { obj ->
            val protocolName = obj.requiredString("protocol")
            val protocol = if (protocolName.equals("DOH3", ignoreCase = true)) {
                DnsProtocol.DOH
            } else {
                DnsProtocol.entries.firstOrNull { it.name == protocolName }
            } ?: throw IllegalArgumentException("配置中包含不支持的 DNS 协议")
            val provider = ImportedProvider(
                name = obj.requiredString("name"),
                protocol = protocol,
                url = obj.optString("url", "").trim(),
                host = obj.optString("host", "").trim(),
                port = obj.optInt("port", if (protocol == DnsProtocol.DNS) 53 else 853)
            )
            val valid = when (protocol) {
                DnsProtocol.DOH -> DnsProvider.isValidDohUrl(provider.url)
                DnsProtocol.DOT -> DnsProvider.isValidDotHost(provider.host) && DnsProvider.isValidDotPort(provider.port)
                DnsProtocol.DNS -> DnsProvider.isValidDnsHost(provider.host) && DnsProvider.isValidDotPort(provider.port)
            }
            if (!valid) throw IllegalArgumentException("配置中包含无效的 DNS 服务商")
            provider
        }
        val bootstrapIps = root.optionalArray("bootstrapIps").mapObjects { obj ->
            val ip = obj.requiredString("ip")
            if (!AppSettings.isValidBootstrapIp(ip)) throw IllegalArgumentException("配置中包含无效的 Bootstrap IP")
            ImportedBootstrap(obj.requiredString("name"), ip, obj.optBoolean("enabled", true))
        }
        val subscriptions = root.optionalArray("subscriptions").mapObjects { obj ->
            val url = obj.requiredString("url")
            if (!url.startsWith("https://") && !url.startsWith("http://")) {
                throw IllegalArgumentException("配置中包含无效的订阅链接")
            }
            ImportedSubscription(obj.requiredString("name"), url)
        }
        val excludedApps = root.optionalArray("excludedApps").mapStrings()
            .filter { it.isNotBlank() }
            .toSet()
        return TransferConfig(providers, bootstrapIps, subscriptions, excludedApps)
    }

    private fun providerKey(provider: DnsProvider): String = providerKey(
        ImportedProvider(provider.name, provider.protocol, provider.url, provider.host, provider.port)
    )

    private fun providerKey(provider: ImportedProvider): String = when (provider.protocol) {
        DnsProtocol.DOH -> "${provider.protocol.name}:${provider.url.lowercase()}"
        else -> "${provider.protocol.name}:${provider.host.lowercase()}:${provider.port}"
    }

    private fun subscriptionKey(url: String) = url.trim().lowercase()

    private fun JSONObject.requiredString(key: String): String = optString(key, "").trim()
        .takeIf { it.isNotEmpty() } ?: throw IllegalArgumentException("配置缺少 $key")

    private fun JSONObject.optionalArray(key: String): JSONArray = when {
        !has(key) -> JSONArray()
        optJSONArray(key) != null -> getJSONArray(key)
        else -> throw IllegalArgumentException("配置字段 $key 格式错误")
    }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
        for (index in 0 until length()) {
            val item = optJSONObject(index) ?: throw IllegalArgumentException("配置列表格式错误")
            add(transform(item))
        }
    }

    private fun JSONArray.mapStrings(): List<String> = buildList {
        for (index in 0 until length()) {
            val item = optString(index, "").trim()
            if (item.isEmpty()) throw IllegalArgumentException("配置列表格式错误")
            add(item)
        }
    }

    private data class TransferConfig(
        val providers: List<ImportedProvider>,
        val bootstrapIps: List<ImportedBootstrap>,
        val subscriptions: List<ImportedSubscription>,
        val excludedApps: Set<String>
    )

    private data class ImportedProvider(
        val name: String,
        val protocol: DnsProtocol,
        val url: String,
        val host: String,
        val port: Int
    )

    private data class ImportedBootstrap(val name: String, val ip: String, val enabled: Boolean)
    private data class ImportedSubscription(val name: String, val url: String)

    companion object {
        private const val FORMAT_VERSION = 2
        private val SUPPORTED_FORMAT_VERSIONS = setOf(1, FORMAT_VERSION)
    }
}

data class RuleExportResult(
    val content: String,
    val blockRuleCount: Int,
    val allowRuleCount: Int
)
