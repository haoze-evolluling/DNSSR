package com.haoze.dnssr.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.MainActivity
import com.haoze.dnssr.R
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
        var succeededCount = 0
        var importedRuleCount = 0
        var failed = false
        manager.remoteSubscriptions().forEach { subscription ->
            manager.updateSubscription(subscription.id).fold(
                onSuccess = { ruleCount ->
                    succeeded = true
                    succeededCount++
                    importedRuleCount += ruleCount
                },
                onFailure = { failed = true }
            )
        }
        if (succeeded) {
            RuntimeDnsSettingsRefresher.refreshIfRunning(applicationContext, "subscriptions_auto_updated")
            SubscriptionAutoUpdateNotifier.showSuccess(
                applicationContext,
                succeededCount,
                importedRuleCount
            )
        }
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

private object SubscriptionAutoUpdateNotifier {
    private const val CHANNEL_ID = "subscription_auto_update"
    private const val NOTIFICATION_ID = 4102

    fun showSuccess(context: Context, subscriptionCount: Int, ruleCount: Int) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "规则订阅自动更新",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_dnssr)
            .setContentTitle("规则订阅已自动更新")
            .setContentText("已更新 $subscriptionCount 个订阅，共导入 $ruleCount 条规则")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
