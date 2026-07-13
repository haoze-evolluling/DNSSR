package com.haoze.dnssr.ui

import android.content.Context
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.SubscriptionManager
import org.json.JSONArray
import org.json.JSONObject

data class ConfigExportSelection(
    val providers: Boolean,
    val bootstrapIps: Boolean,
    val subscriptions: Boolean
)

data class ConfigImportResult(
    val added: Int,
    val skipped: Int,
    val failed: Int
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
        AllowListManager(database.allowRuleDao())
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
        return root.toString(2)
    }

    suspend fun import(
        content: String,
        onProgress: (ConfigImportProgress) -> Unit = {}
    ): ConfigImportResult {
        val config = parseAndValidate(content)
        var added = 0
        var skipped = 0
        var failed = 0
        var processed = 0
        val total = config.providers.size + config.bootstrapIps.size + config.subscriptions.size

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
        return ConfigImportResult(added, skipped, failed)
    }

    private fun parseAndValidate(content: String): TransferConfig {
        val root = try {
            JSONObject(content)
        } catch (_: Exception) {
            throw IllegalArgumentException("配置文件不是有效的 JSON")
        }
        if (root.optInt("formatVersion", -1) != FORMAT_VERSION) {
            throw IllegalArgumentException("不支持的配置文件版本")
        }

        val providers = root.optionalArray("providers").mapObjects { obj ->
            val protocol = DnsProtocol.entries.firstOrNull { it.name == obj.requiredString("protocol") }
                ?: throw IllegalArgumentException("配置中包含不支持的 DNS 协议")
            val provider = ImportedProvider(
                name = obj.requiredString("name"),
                protocol = protocol,
                url = obj.optString("url", "").trim(),
                host = obj.optString("host", "").trim(),
                port = obj.optInt("port", if (protocol == DnsProtocol.DNS) 53 else 853)
            )
            val valid = when (protocol) {
                DnsProtocol.DOH, DnsProtocol.DOH3 -> DnsProvider.isValidDohUrl(provider.url)
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
        return TransferConfig(providers, bootstrapIps, subscriptions)
    }

    private fun providerKey(provider: DnsProvider): String = providerKey(
        ImportedProvider(provider.name, provider.protocol, provider.url, provider.host, provider.port)
    )

    private fun providerKey(provider: ImportedProvider): String = when (provider.protocol) {
        DnsProtocol.DOH, DnsProtocol.DOH3 -> "${provider.protocol.name}:${provider.url.lowercase()}"
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

    private data class TransferConfig(
        val providers: List<ImportedProvider>,
        val bootstrapIps: List<ImportedBootstrap>,
        val subscriptions: List<ImportedSubscription>
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
        private const val FORMAT_VERSION = 1
    }
}
