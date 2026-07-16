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
