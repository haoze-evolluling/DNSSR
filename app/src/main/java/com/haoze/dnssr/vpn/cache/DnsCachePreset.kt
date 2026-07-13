package com.haoze.dnssr.vpn.cache

enum class DnsCachePreset(
    val storageValue: String,
    val displayName: String,
    val summary: String,
    val description: String,
    val policy: DnsCachePolicy
) {
    CONSERVATIVE(
        storageValue = "conservative",
        displayName = "保守",
        summary = "跟随上游 TTL",
        description = "完全尊重 DNS 返回的 TTL，不主动延长缓存时间，适合经常切换网络或对实时性敏感的场景。",
        policy = DnsCachePolicy(
            enabled = true,
            mode = DnsCacheMode.FOLLOW_DNS_TTL,
            maxTtlSeconds = 3600L,
            fixedTtlSeconds = 3600L,
            minTtlEnabled = false,
            minTtlSeconds = 60L,
            staleFallbackEnabled = false,
            staleFallbackSeconds = 300L
        )
    ),
    BALANCED(
        storageValue = "balanced",
        displayName = "均衡",
        summary = "最长 1 小时，短 TTL 至少 1 分钟",
        description = "减少重复查询，同时避免缓存时间过长。解析失败时可短暂使用 5 分钟内的过期缓存。",
        policy = DnsCachePolicy(
            enabled = true,
            mode = DnsCacheMode.LIMIT_MAX_TTL,
            maxTtlSeconds = 3600L,
            fixedTtlSeconds = 3600L,
            minTtlEnabled = true,
            minTtlSeconds = 60L,
            staleFallbackEnabled = true,
            staleFallbackSeconds = 300L
        )
    ),
    HIGH_HIT_RATE(
        storageValue = "high_hit_rate",
        displayName = "高命中",
        summary = "最长 6 小时，短 TTL 至少 2 分钟",
        description = "更偏向降低 DNS 请求次数和提升弱网可用性。动态域名更新可能会稍慢一些。",
        policy = DnsCachePolicy(
            enabled = true,
            mode = DnsCacheMode.LIMIT_MAX_TTL,
            maxTtlSeconds = 21_600L,
            fixedTtlSeconds = 3600L,
            minTtlEnabled = true,
            minTtlSeconds = 120L,
            staleFallbackEnabled = true,
            staleFallbackSeconds = 900L
        )
    );

    fun toPolicy(enabled: Boolean = true): DnsCachePolicy {
        return policy.copy(enabled = enabled)
    }

    companion object {
        fun fromStorageValue(value: String?): DnsCachePreset? {
            return values().firstOrNull { it.storageValue == value }
        }

        fun fromPolicy(policy: DnsCachePolicy): DnsCachePreset {
            return when {
                policy.mode == DnsCacheMode.FOLLOW_DNS_TTL &&
                    !policy.minTtlEnabled &&
                    !policy.staleFallbackEnabled -> CONSERVATIVE

                policy.mode == DnsCacheMode.LIMIT_MAX_TTL &&
                    policy.maxTtlSeconds <= BALANCED.policy.maxTtlSeconds &&
                    (!policy.minTtlEnabled || policy.minTtlSeconds <= BALANCED.policy.minTtlSeconds) &&
                    (!policy.staleFallbackEnabled || policy.staleFallbackSeconds <= BALANCED.policy.staleFallbackSeconds) -> BALANCED

                else -> HIGH_HIT_RATE
            }
        }
    }
}
