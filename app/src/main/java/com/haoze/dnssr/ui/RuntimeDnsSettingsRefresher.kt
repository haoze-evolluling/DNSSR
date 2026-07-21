package com.haoze.dnssr.ui

import android.content.Context
import android.util.Log
import com.haoze.dnssr.vpn.DnsVpnService

object RuntimeDnsSettingsRefresher {
    private const val TAG = "RuntimeDnsRefresh"

    fun refreshIfRunning(context: Context, reason: String = "settings_changed") {
        val appContext = context.applicationContext
        if (!DnsVpnService.isRunning(appContext)) return

        runCatching {
            appContext.startService(DnsVpnService.refreshRuntimeConfigIntent(appContext, reason))
        }.onFailure { error ->
            Log.w(TAG, "Failed to request DNS runtime config refresh", error)
        }
    }

    fun syncRuleIfRunning(context: Context, ruleType: String, pattern: String) {
        val appContext = context.applicationContext
        if (!DnsVpnService.isRunning(appContext)) return
        runCatching {
            appContext.startService(DnsVpnService.syncRuleIntent(appContext, ruleType, pattern))
        }.onFailure { error ->
            Log.w(TAG, "Failed to request incremental rule cache sync", error)
        }
    }

    fun refreshRuleIndexesIfRunning(context: Context, refreshBlock: Boolean, refreshAllow: Boolean, refreshRewrite: Boolean) {
        val appContext = context.applicationContext
        if (!DnsVpnService.isRunning(appContext)) return
        runCatching {
            appContext.startService(
                DnsVpnService.refreshRuleIndexesIntent(appContext, refreshBlock, refreshAllow, refreshRewrite)
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to request rule index refresh", error)
        }
    }

    fun refreshAppExclusionsIfRunning(context: Context) {
        val appContext = context.applicationContext
        if (!DnsVpnService.isRunning(appContext)) return

        runCatching {
            appContext.startService(DnsVpnService.refreshAppExclusionsIntent(appContext))
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh application exclusions", error)
        }
    }
}
