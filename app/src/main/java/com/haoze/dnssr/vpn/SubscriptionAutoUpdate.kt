package com.haoze.dnssr.vpn

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.ui.RuntimeDnsSettingsRefresher
import java.util.concurrent.TimeUnit

object SubscriptionAutoUpdateSettings {
    private const val PREFS = "dns_vpn_prefs"
    private const val KEY_ENABLED = "subscription_auto_update_enabled"
    private const val KEY_INTERVAL = "subscription_auto_update_interval_hours"
    const val DEFAULT_INTERVAL_HOURS = 24
    val intervals = listOf(6, 12, 24, 48)
    const val MIN_INTERVAL_HOURS = 1
    const val MAX_INTERVAL_HOURS = 168

    fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun intervalHours(context: Context): Int {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_INTERVAL, DEFAULT_INTERVAL_HOURS)
        return value.takeIf { it in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS } ?: DEFAULT_INTERVAL_HOURS
    }

    fun save(context: Context, enabled: Boolean, intervalHours: Int) {
        require(intervalHours in MIN_INTERVAL_HOURS..MAX_INTERVAL_HOURS)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_INTERVAL, intervalHours)
            .apply()
    }
}

object SubscriptionAutoUpdateScheduler {
    private const val WORK_NAME = "subscription_auto_update"

    fun sync(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!SubscriptionAutoUpdateSettings.isEnabled(context)) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val hours = SubscriptionAutoUpdateSettings.intervalHours(context)
        val request = PeriodicWorkRequestBuilder<SubscriptionAutoUpdateWorker>(hours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class SubscriptionAutoUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val manager = SubscriptionManager(
            database.subscriptionDao(),
            BlockListManager(database.blockRuleDao()),
            AllowListManager(database.allowRuleDao())
        )
        var succeeded = false
        var failed = false
        manager.remoteSubscriptions().forEach { subscription ->
            manager.updateSubscription(subscription.id).fold(
                onSuccess = { succeeded = true },
                onFailure = { failed = true }
            )
        }
        if (succeeded) {
            RuntimeDnsSettingsRefresher.refreshIfRunning(applicationContext, "subscriptions_auto_updated")
        }
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
