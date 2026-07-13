package com.haoze.dnssr.ui

import android.content.Context
import com.haoze.dnssr.vpn.BlockResponseMode
import com.haoze.dnssr.vpn.BootstrapIpDefaults
import com.haoze.dnssr.vpn.BootstrapIpEntry
import com.haoze.dnssr.vpn.BootstrapIpValidator
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.cache.DnsCacheMode
import com.haoze.dnssr.vpn.cache.DnsCachePolicy
import com.haoze.dnssr.vpn.cache.DnsCachePreset
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class RaceModeStrategy(
    val storageValue: String,
    val displayName: String
) {
    BRUTE_FORCE_PARALLEL("brute_force_parallel", "暴力并行"),
    SMART_PREDICTION("smart_prediction", "智慧预测"),
    PRIMARY_BACKUP("primary_backup", "主备容灾");

    companion object {
        fun fromStorageValue(value: String?): RaceModeStrategy {
            return values().firstOrNull { it.storageValue == value } ?: SMART_PREDICTION
        }
    }
}

enum class DnsResolutionMode(
    val storageValue: String,
    val displayName: String
) {
    SINGLE("single", "单服务商"),
    SMART_PREDICTION("smart_prediction", "智慧预测"),
    PARALLEL_RACE("parallel_race", "并行竞速"),
    PRIMARY_BACKUP("primary_backup", "主备容灾");

    companion object {
        fun fromStorageValue(value: String?): DnsResolutionMode? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class HomeProviderVisibility(
    val visibleProtocols: Set<DnsProtocol> = DnsProtocol.MANAGED_PROTOCOLS.toSet(),
    val hiddenProviderIds: Set<String> = emptySet(),
    val visibleProviderIds: Set<String> = emptySet()
) {
    fun isVisible(provider: DnsProvider): Boolean {
        return if (provider.protocol in visibleProtocols) {
            provider.id !in hiddenProviderIds
        } else {
            provider.id in visibleProviderIds
        }
    }

    fun isDefault(): Boolean {
        return visibleProtocols == DnsProtocol.MANAGED_PROTOCOLS.toSet() &&
            hiddenProviderIds.isEmpty() && visibleProviderIds.isEmpty()
    }
}

/**
 * 应用设置封装，基于 SharedPreferences。
 */
object AppSettings {
    private const val PREFS_NAME = "dns_vpn_prefs"

    private const val KEY_DNS_CACHE_ENABLED = "dns_cache_enabled_v2"
    private const val KEY_DNS_CACHE_MODE = "dns_cache_mode_v2"
    private const val KEY_DNS_CACHE_MAX_TTL_SECONDS = "dns_cache_max_ttl_seconds_v2"
    private const val KEY_DNS_CACHE_FIXED_TTL_SECONDS = "dns_cache_fixed_ttl_seconds_v2"
    private const val KEY_DNS_CACHE_MIN_TTL_ENABLED = "dns_cache_min_ttl_enabled_v2"
    private const val KEY_DNS_CACHE_MIN_TTL_SECONDS = "dns_cache_min_ttl_seconds_v2"
    private const val KEY_DNS_CACHE_STALE_FALLBACK_ENABLED = "dns_cache_stale_fallback_enabled_v2"
    private const val KEY_DNS_CACHE_STALE_FALLBACK_SECONDS = "dns_cache_stale_fallback_seconds_v2"
    private const val KEY_DNS_CACHE_PRESET = "dns_cache_preset_v3"
    private const val KEY_BLOCK_RESPONSE_MODE = "block_response_mode"
    const val KEY_LOG_RETENTION_DAYS = "log_retention_days"
    const val KEY_RACE_MODE_ENABLED = "race_mode_enabled"
    const val KEY_RACE_PROVIDER_IDS = "race_provider_ids"
    const val KEY_RACE_TEST_DOMAIN = "race_test_domain"
    const val KEY_LATENCY_TEST_PROVIDER_IDS = "latency_test_provider_ids"
    const val KEY_RACE_MODE_STRATEGY = "race_mode_strategy"
    private const val KEY_DNS_RESOLUTION_MODE = "dns_resolution_mode"
    private const val KEY_SMART_PREDICTION_PROVIDER_IDS = "smart_prediction_provider_ids"
    private const val KEY_PARALLEL_RACE_PROVIDER_IDS = "parallel_race_provider_ids"
    private const val KEY_PRIMARY_BACKUP_PROVIDER_IDS = "primary_backup_provider_ids"
    const val KEY_BOOTSTRAP_ENABLED = "bootstrap_enabled"
    const val KEY_BOOTSTRAP_PRESET_IDS = "bootstrap_preset_ids"
    const val KEY_BOOTSTRAP_CUSTOM_JSON = "bootstrap_custom_json"
    const val KEY_HIDE_FROM_RECENTS_ENABLED = "hide_from_recents_enabled"
    const val KEY_PERSISTENT_NOTIFICATION_ENABLED = "persistent_notification_enabled"
    const val KEY_LEGACY_ICON_ENABLED = "legacy_icon_enabled"
    const val KEY_SERVICE_LIGHT_EFFECT_ENABLED = "service_light_effect_enabled"
    private const val KEY_HOME_VISIBLE_PROTOCOLS = "home_visible_protocols"
    private const val KEY_HOME_HIDDEN_PROVIDER_IDS = "home_hidden_provider_ids"
    private const val KEY_HOME_VISIBLE_PROVIDER_IDS = "home_visible_provider_ids"

    private const val MIN_CACHE_SECONDS = 30L
    private const val MAX_CACHE_SECONDS = 86_400L
    private const val DEFAULT_CACHE_ENABLED = true
    private const val DEFAULT_CACHE_MAX_TTL_SECONDS = 3600L
    private const val DEFAULT_CACHE_FIXED_TTL_SECONDS = 3600L
    private const val DEFAULT_CACHE_MIN_TTL_SECONDS = 60L
    private const val DEFAULT_CACHE_STALE_FALLBACK_SECONDS = 300L
    private const val DEFAULT_LOG_RETENTION_DAYS = 7
    private const val DEFAULT_RACE_MODE_ENABLED = false
    private val DEFAULT_RACE_PROVIDER_IDS = setOf(
        "preset_alidns_dot",
        "preset_dnspod_dot",
    )
    private val DEFAULT_LATENCY_TEST_PROVIDER_IDS = emptySet<String>()
    private const val DEFAULT_RACE_TEST_DOMAIN = "mihoyo.com"
    private val DEFAULT_RACE_MODE_STRATEGY = RaceModeStrategy.SMART_PREDICTION
    private val DEFAULT_BLOCK_RESPONSE_MODE = BlockResponseMode.NXDOMAIN
    private const val DEFAULT_BOOTSTRAP_ENABLED = true
    private const val DEFAULT_HIDE_FROM_RECENTS_ENABLED = false
    const val DEFAULT_PERSISTENT_NOTIFICATION_ENABLED = true
    private const val DEFAULT_LEGACY_ICON_ENABLED = false
    private const val DEFAULT_SERVICE_LIGHT_EFFECT_ENABLED = true
    private val DEFAULT_BOOTSTRAP_PRESET_IDS = setOf(
        "preset_volcengine",
        "preset_dnspod",
        "preset_alidns"
    )

    fun isCacheEnabled(context: Context): Boolean {
        return getDnsCachePolicy(context).enabled
    }

    fun setCacheEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DNS_CACHE_ENABLED, enabled)
            .apply()
    }

    fun getDnsCachePolicy(context: Context): DnsCachePolicy {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DnsCachePolicy(
            enabled = prefs.getBoolean(KEY_DNS_CACHE_ENABLED, DEFAULT_CACHE_ENABLED),
            mode = DnsCacheMode.fromStorageValue(
                prefs.getString(KEY_DNS_CACHE_MODE, DnsCacheMode.LIMIT_MAX_TTL.storageValue)
            ),
            maxTtlSeconds = prefs.getLong(KEY_DNS_CACHE_MAX_TTL_SECONDS, DEFAULT_CACHE_MAX_TTL_SECONDS)
                .coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS),
            fixedTtlSeconds = prefs.getLong(KEY_DNS_CACHE_FIXED_TTL_SECONDS, DEFAULT_CACHE_FIXED_TTL_SECONDS)
                .coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS),
            minTtlEnabled = prefs.getBoolean(KEY_DNS_CACHE_MIN_TTL_ENABLED, false),
            minTtlSeconds = prefs.getLong(KEY_DNS_CACHE_MIN_TTL_SECONDS, DEFAULT_CACHE_MIN_TTL_SECONDS)
                .coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS),
            staleFallbackEnabled = prefs.getBoolean(KEY_DNS_CACHE_STALE_FALLBACK_ENABLED, false),
            staleFallbackSeconds = prefs.getLong(
                KEY_DNS_CACHE_STALE_FALLBACK_SECONDS,
                DEFAULT_CACHE_STALE_FALLBACK_SECONDS
            ).coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS)
        )
    }

    fun getDnsCachePreset(context: Context): DnsCachePreset {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return DnsCachePreset.fromStorageValue(prefs.getString(KEY_DNS_CACHE_PRESET, null))
            ?: DnsCachePreset.fromPolicy(getDnsCachePolicy(context))
    }

    fun setDnsCachePreset(context: Context, preset: DnsCachePreset) {
        val enabled = getDnsCachePolicy(context).enabled
        setDnsCachePolicy(context, preset.toPolicy(enabled = enabled), preset)
    }

    fun setDnsCachePolicy(context: Context, policy: DnsCachePolicy) {
        setDnsCachePolicy(context, policy, DnsCachePreset.fromPolicy(policy))
    }

    private fun setDnsCachePolicy(context: Context, policy: DnsCachePolicy, preset: DnsCachePreset) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DNS_CACHE_PRESET, preset.storageValue)
            .putBoolean(KEY_DNS_CACHE_ENABLED, policy.enabled)
            .putString(KEY_DNS_CACHE_MODE, policy.mode.storageValue)
            .putLong(KEY_DNS_CACHE_MAX_TTL_SECONDS, policy.maxTtlSeconds.coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS))
            .putLong(KEY_DNS_CACHE_FIXED_TTL_SECONDS, policy.fixedTtlSeconds.coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS))
            .putBoolean(KEY_DNS_CACHE_MIN_TTL_ENABLED, policy.minTtlEnabled)
            .putLong(KEY_DNS_CACHE_MIN_TTL_SECONDS, policy.minTtlSeconds.coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS))
            .putBoolean(KEY_DNS_CACHE_STALE_FALLBACK_ENABLED, policy.staleFallbackEnabled)
            .putLong(
                KEY_DNS_CACHE_STALE_FALLBACK_SECONDS,
                policy.staleFallbackSeconds.coerceIn(MIN_CACHE_SECONDS, MAX_CACHE_SECONDS)
            )
            .apply()
    }

    fun logRetentionDays(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LOG_RETENTION_DAYS, DEFAULT_LOG_RETENTION_DAYS)
    }

    fun setLogRetentionDays(context: Context, days: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_LOG_RETENTION_DAYS, days.coerceIn(1, 30))
            .apply()
    }

    fun isRaceModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_RACE_MODE_ENABLED, DEFAULT_RACE_MODE_ENABLED)
    }

    fun setRaceModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_RACE_MODE_ENABLED, enabled)
            .apply()
    }

    fun getRaceProviderIds(context: Context): Set<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RACE_PROVIDER_IDS, null) ?: return DEFAULT_RACE_PROVIDER_IDS
        return try {
            val array = org.json.JSONArray(json)
            val ids = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                ids.add(array.getString(i))
            }
            ids
        } catch (_: Exception) {
            DEFAULT_RACE_PROVIDER_IDS
        }
    }

    fun hasRaceProviderIds(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_RACE_PROVIDER_IDS)
    }

    fun setRaceProviderIds(context: Context, ids: Set<String>) {
        val array = org.json.JSONArray()
        ids.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RACE_PROVIDER_IDS, array.toString())
            .apply()
    }

    fun getLatencyTestProviderIds(context: Context): Set<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LATENCY_TEST_PROVIDER_IDS, null) ?: return DEFAULT_LATENCY_TEST_PROVIDER_IDS
        return try {
            val array = org.json.JSONArray(json)
            val ids = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                ids.add(array.getString(i))
            }
            ids
        } catch (_: Exception) {
            DEFAULT_LATENCY_TEST_PROVIDER_IDS
        }
    }

    fun hasLatencyTestProviderIds(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .contains(KEY_LATENCY_TEST_PROVIDER_IDS)
    }

    fun setLatencyTestProviderIds(context: Context, ids: Set<String>) {
        val array = org.json.JSONArray()
        ids.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LATENCY_TEST_PROVIDER_IDS, array.toString())
            .apply()
    }

    fun getRaceTestDomain(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RACE_TEST_DOMAIN, DEFAULT_RACE_TEST_DOMAIN)
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_RACE_TEST_DOMAIN
    }

    fun setRaceTestDomain(context: Context, domain: String) {
        val trimmed = domain.trim()
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RACE_TEST_DOMAIN, trimmed.takeIf { it.isNotBlank() } ?: DEFAULT_RACE_TEST_DOMAIN)
            .apply()
    }

    fun getRaceModeStrategy(context: Context): RaceModeStrategy {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RACE_MODE_STRATEGY, DEFAULT_RACE_MODE_STRATEGY.storageValue)
        return RaceModeStrategy.fromStorageValue(value)
    }

    fun setRaceModeStrategy(context: Context, strategy: RaceModeStrategy) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RACE_MODE_STRATEGY, strategy.storageValue)
            .apply()
    }

    fun getDnsResolutionMode(context: Context): DnsResolutionMode {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        DnsResolutionMode.fromStorageValue(prefs.getString(KEY_DNS_RESOLUTION_MODE, null))?.let { return it }

        val migrated = if (!prefs.getBoolean(KEY_RACE_MODE_ENABLED, DEFAULT_RACE_MODE_ENABLED)) {
            DnsResolutionMode.SINGLE
        } else {
            when (RaceModeStrategy.fromStorageValue(prefs.getString(KEY_RACE_MODE_STRATEGY, null))) {
                RaceModeStrategy.BRUTE_FORCE_PARALLEL -> DnsResolutionMode.PARALLEL_RACE
                RaceModeStrategy.SMART_PREDICTION -> DnsResolutionMode.SMART_PREDICTION
                RaceModeStrategy.PRIMARY_BACKUP -> DnsResolutionMode.PRIMARY_BACKUP
            }
        }
        prefs.edit().putString(KEY_DNS_RESOLUTION_MODE, migrated.storageValue).apply()
        return migrated
    }

    fun setDnsResolutionMode(context: Context, mode: DnsResolutionMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DNS_RESOLUTION_MODE, mode.storageValue)
            .putBoolean(KEY_RACE_MODE_ENABLED, mode != DnsResolutionMode.SINGLE)
            .putString(
                KEY_RACE_MODE_STRATEGY,
                when (mode) {
                    DnsResolutionMode.PARALLEL_RACE -> RaceModeStrategy.BRUTE_FORCE_PARALLEL
                    DnsResolutionMode.PRIMARY_BACKUP -> RaceModeStrategy.PRIMARY_BACKUP
                    else -> RaceModeStrategy.SMART_PREDICTION
                }.storageValue
            )
            .apply()
    }

    private fun getModeProviderIds(context: Context, key: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, null)
        if (json == null) {
            val migrated = getRaceProviderIds(context)
            setModeProviderIds(context, key, migrated)
            return migrated
        }
        return try {
            val array = JSONArray(json)
            buildSet { for (index in 0 until array.length()) add(array.getString(index)) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun setModeProviderIds(context: Context, key: String, ids: Set<String>) {
        val array = JSONArray()
        ids.forEach(array::put)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(key, array.toString()).apply()
    }

    fun getSmartPredictionProviderIds(context: Context) =
        getModeProviderIds(context, KEY_SMART_PREDICTION_PROVIDER_IDS)

    fun setSmartPredictionProviderIds(context: Context, ids: Set<String>) =
        setModeProviderIds(context, KEY_SMART_PREDICTION_PROVIDER_IDS, ids)

    fun getParallelRaceProviderIds(context: Context) =
        getModeProviderIds(context, KEY_PARALLEL_RACE_PROVIDER_IDS)

    fun setParallelRaceProviderIds(context: Context, ids: Set<String>) =
        setModeProviderIds(context, KEY_PARALLEL_RACE_PROVIDER_IDS, ids)

    fun getPrimaryBackupProviderIds(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PRIMARY_BACKUP_PROVIDER_IDS, null)
            ?: return getRaceProviderIds(context).toList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) add(array.getString(index))
            }.distinct()
        } catch (_: Exception) {
            getRaceProviderIds(context).toList()
        }
    }

    fun setPrimaryBackupProviderIds(context: Context, ids: List<String>) {
        val array = JSONArray()
        ids.distinct().forEach(array::put)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PRIMARY_BACKUP_PROVIDER_IDS, array.toString())
            .apply()
    }

    fun removeProviderFromResolutionModes(context: Context, id: String) {
        setSmartPredictionProviderIds(context, getSmartPredictionProviderIds(context) - id)
        setParallelRaceProviderIds(context, getParallelRaceProviderIds(context) - id)
        setPrimaryBackupProviderIds(context, getPrimaryBackupProviderIds(context) - id)
    }

    fun getHomeProviderVisibility(context: Context): HomeProviderVisibility {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return HomeProviderVisibility(
            visibleProtocols = readStringSet(prefs.getString(KEY_HOME_VISIBLE_PROTOCOLS, null))
                ?.mapNotNull { value -> DnsProtocol.entries.firstOrNull { it.name == value } }
                ?.toSet()
                ?: DnsProtocol.MANAGED_PROTOCOLS.toSet(),
            hiddenProviderIds = readStringSet(prefs.getString(KEY_HOME_HIDDEN_PROVIDER_IDS, null)) ?: emptySet(),
            visibleProviderIds = readStringSet(prefs.getString(KEY_HOME_VISIBLE_PROVIDER_IDS, null)) ?: emptySet()
        )
    }

    fun setHomeProviderVisibility(context: Context, visibility: HomeProviderVisibility) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME_VISIBLE_PROTOCOLS, writeStringSet(visibility.visibleProtocols.map { it.name }.toSet()))
            .putString(KEY_HOME_HIDDEN_PROVIDER_IDS, writeStringSet(visibility.hiddenProviderIds))
            .putString(KEY_HOME_VISIBLE_PROVIDER_IDS, writeStringSet(visibility.visibleProviderIds))
            .apply()
    }

    private fun readStringSet(json: String?): Set<String>? {
        if (json == null) return null
        return try {
            val array = JSONArray(json)
            buildSet {
                for (index in 0 until array.length()) add(array.getString(index))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeStringSet(values: Set<String>): String {
        return JSONArray().apply { values.sorted().forEach(::put) }.toString()
    }

    fun getBlockResponseMode(context: Context): BlockResponseMode {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BLOCK_RESPONSE_MODE, DEFAULT_BLOCK_RESPONSE_MODE.storageValue)
        return BlockResponseMode.fromStorageValue(value)
    }

    fun setBlockResponseMode(context: Context, mode: BlockResponseMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BLOCK_RESPONSE_MODE, mode.storageValue)
            .apply()
    }

    fun isBootstrapEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BOOTSTRAP_ENABLED, DEFAULT_BOOTSTRAP_ENABLED)
    }

    fun setBootstrapEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BOOTSTRAP_ENABLED, enabled)
            .apply()
    }

    fun isHideFromRecentsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_FROM_RECENTS_ENABLED, DEFAULT_HIDE_FROM_RECENTS_ENABLED)
    }

    fun setHideFromRecentsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIDE_FROM_RECENTS_ENABLED, enabled)
            .apply()
    }

    fun isPersistentNotificationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, DEFAULT_PERSISTENT_NOTIFICATION_ENABLED)
    }

    fun setPersistentNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PERSISTENT_NOTIFICATION_ENABLED, enabled)
            .apply()
    }

    fun isLegacyIconEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LEGACY_ICON_ENABLED, DEFAULT_LEGACY_ICON_ENABLED)
    }

    fun setLegacyIconEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LEGACY_ICON_ENABLED, enabled)
            .apply()
    }

    fun isServiceLightEffectEnabled(context: Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            return false
        }
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SERVICE_LIGHT_EFFECT_ENABLED, DEFAULT_SERVICE_LIGHT_EFFECT_ENABLED)
    }

    fun setServiceLightEffectEnabled(context: Context, enabled: Boolean) {
        val supportedEnabled = enabled &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SERVICE_LIGHT_EFFECT_ENABLED, supportedEnabled)
            .apply()
    }

    fun loadBootstrapIpEntries(context: Context): List<BootstrapIpEntry> {
        val selectedPresetIds = getBootstrapPresetIds(context)
        val presets = BootstrapIpDefaults.PRESETS.map { entry ->
            entry.copy(enabled = entry.id in selectedPresetIds)
        }
        return presets + getCustomBootstrapIpEntries(context)
    }

    fun loadEnabledBootstrapIpEntries(context: Context): List<BootstrapIpEntry> {
        if (!isBootstrapEnabled(context)) return emptyList()
        return loadBootstrapIpEntries(context).filter { it.enabled }
    }

    fun setBootstrapIpEnabled(context: Context, id: String, enabled: Boolean) {
        val presetIds = BootstrapIpDefaults.PRESETS.map { it.id }.toSet()
        if (id in presetIds) {
            val selected = getBootstrapPresetIds(context).toMutableSet()
            if (enabled) selected.add(id) else selected.remove(id)
            setBootstrapPresetIds(context, selected)
            return
        }
        val updated = getCustomBootstrapIpEntries(context).map { entry ->
            if (entry.id == id) entry.copy(enabled = enabled) else entry
        }
        saveCustomBootstrapIpEntries(context, updated)
    }

    fun addCustomBootstrapIp(context: Context, name: String, ip: String): BootstrapIpEntry? {
        val trimmedIp = ip.trim()
        if (!BootstrapIpValidator.isValidIp(trimmedIp)) return null
        val entry = BootstrapIpEntry(
            id = "custom_${UUID.randomUUID()}",
            name = name.trim().ifBlank { trimmedIp },
            ip = trimmedIp,
            isPreset = false,
            enabled = true
        )
        saveCustomBootstrapIpEntries(context, getCustomBootstrapIpEntries(context) + entry)
        return entry
    }

    fun deleteCustomBootstrapIp(context: Context, id: String) {
        saveCustomBootstrapIpEntries(
            context,
            getCustomBootstrapIpEntries(context).filter { it.id != id }
        )
    }

    fun isValidBootstrapIp(ip: String?): Boolean {
        return BootstrapIpValidator.isValidIp(ip)
    }

    private fun getBootstrapPresetIds(context: Context): Set<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOOTSTRAP_PRESET_IDS, null)
            ?: return DEFAULT_BOOTSTRAP_PRESET_IDS
        return try {
            val array = JSONArray(json)
            buildSet {
                for (i in 0 until array.length()) {
                    add(array.getString(i))
                }
            }
        } catch (_: Exception) {
            DEFAULT_BOOTSTRAP_PRESET_IDS
        }
    }

    private fun setBootstrapPresetIds(context: Context, ids: Set<String>) {
        val validIds = BootstrapIpDefaults.PRESETS.map { it.id }.toSet()
        val array = JSONArray()
        ids.filter { it in validIds }.forEach { array.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOTSTRAP_PRESET_IDS, array.toString())
            .apply()
    }

    private fun getCustomBootstrapIpEntries(context: Context): List<BootstrapIpEntry> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BOOTSTRAP_CUSTOM_JSON, null)
            ?: return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val ip = obj.optString("ip", "").trim()
                    if (!BootstrapIpValidator.isValidIp(ip)) continue
                    add(
                        BootstrapIpEntry(
                            id = obj.optString("id", "").takeIf { it.isNotBlank() } ?: "custom_${UUID.randomUUID()}",
                            name = obj.optString("name", ip).takeIf { it.isNotBlank() } ?: ip,
                            ip = ip,
                            isPreset = false,
                            enabled = obj.optBoolean("enabled", true)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveCustomBootstrapIpEntries(context: Context, entries: List<BootstrapIpEntry>) {
        val array = JSONArray()
        entries.filter { !it.isPreset && BootstrapIpValidator.isValidIp(it.ip) }.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("ip", entry.ip)
                    put("enabled", entry.enabled)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOTSTRAP_CUSTOM_JSON, array.toString())
            .apply()
    }
}
