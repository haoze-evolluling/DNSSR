package com.haoze.dnssr.vpn

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.haoze.dnssr.MainActivity
import com.haoze.dnssr.R
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.ui.RuntimeDnsSettingsRefresher
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class RuleOperationType {
    ADD_SUBSCRIPTION,
    ADD_LOCAL_SUBSCRIPTION,
    EDIT_SUBSCRIPTION,
    UPDATE_SUBSCRIPTION,
    UPDATE_ALL_SUBSCRIPTIONS,
    IMPORT_RULES,
    IMPORT_HOSTS_RULES,
    ADD_BLOCK_RULE,
    ADD_ALLOW_RULE
}

object RuleOperationScheduler {
    const val TAG = "manual_rule_operation"
    const val KEY_TYPE = "type"
    const val KEY_URL = "url"
    const val KEY_NAME = "name"
    const val KEY_URI = "uri"
    const val KEY_PATTERN = "pattern"
    const val KEY_SUBSCRIPTION_ID = "subscription_id"
    const val KEY_CURRENT = "current"
    const val KEY_TOTAL = "total"
    const val KEY_MESSAGE = "message"
    const val KEY_SUCCESS = "success"
    const val KEY_KIND = "kind"

    private const val UNIQUE_WORK_NAME = "manual_rule_operation_queue"

    fun enqueue(
        context: Context,
        type: RuleOperationType,
        subscriptionId: Long = -1,
        url: String? = null,
        name: String? = null,
        uri: Uri? = null,
        pattern: String? = null,
        kind: String? = null
    ): OneTimeWorkRequest {
        val input = Data.Builder()
            .putString(KEY_TYPE, type.name)
            .putLong(KEY_SUBSCRIPTION_ID, subscriptionId)
            .putString(KEY_URL, url)
            .putString(KEY_NAME, name)
            .putString(KEY_URI, uri?.toString())
            .putString(KEY_PATTERN, pattern)
            .putString(KEY_KIND, kind)
            .build()
        val builder = OneTimeWorkRequestBuilder<RuleOperationWorker>()
            .setInputData(input)
            .addTag(TAG)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (type in setOf(
                RuleOperationType.ADD_SUBSCRIPTION,
                RuleOperationType.EDIT_SUBSCRIPTION,
                RuleOperationType.UPDATE_SUBSCRIPTION,
                RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS
            )
        ) {
            builder.setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
        }
        return builder.build().also { request ->
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}

class RuleOperationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationId = id.hashCode()
    private val completionNotificationId = notificationId xor COMPLETION_ID_MASK

    override suspend fun doWork(): Result = coroutineScope {
        val type = runCatching {
            RuleOperationType.valueOf(inputData.getString(RuleOperationScheduler.KEY_TYPE).orEmpty())
        }.getOrNull() ?: return@coroutineScope failure("未知的规则操作")
        val subscriptionId = inputData.getLong(RuleOperationScheduler.KEY_SUBSCRIPTION_ID, -1)
        val title = titleFor(type)
        createNotificationChannel()
        setForeground(createForegroundInfo(title, -1, 0))
        setProgress(progressData(type, subscriptionId, -1, 0))

        val database = AppDatabase.getInstance(applicationContext)
        val blockManager = BlockListManager(database.blockRuleDao())
        val allowManager = AllowListManager(database.allowRuleDao())
        val subscriptionManager = SubscriptionManager(
            database.subscriptionDao(),
            blockManager,
            allowManager,
            RewriteRuleManager(database.rewriteRuleDao(), java.io.File(applicationContext.filesDir, "rule-index"))
        )
        val rewriteManager = RewriteRuleManager(database.rewriteRuleDao(), java.io.File(applicationContext.filesDir, "rule-index"))
        var activeSubscriptionId = subscriptionId
        var latestCurrent = -1
        var latestTotal = 0
        val subscriptionIdJob = launch {
            subscriptionManager.importingSubscriptionId.collect { id ->
                if (id != null) {
                    activeSubscriptionId = id
                    setProgress(progressData(type, activeSubscriptionId, latestCurrent, latestTotal))
                }
            }
        }
        val progressJob = launch {
            subscriptionManager.importProgress.collect { (current, total) ->
                if (current >= 0) {
                    latestCurrent = current
                    latestTotal = total
                    setProgress(progressData(type, activeSubscriptionId, current, total))
                    notificationManager.notify(
                        notificationId,
                        buildNotification(title, current, total, ongoing = true)
                    )
                }
            }
        }

        try {
            val message = SubscriptionUpdateCoordinator.runManual {
                execute(type, subscriptionId, blockManager, allowManager, rewriteManager, subscriptionManager)
            }
            RuntimeDnsSettingsRefresher.refreshIfRunning(applicationContext, "rule_operation_completed")
            showFinishedNotification("$title 已完成", message)
            Result.success(
                workDataOf(
                    RuleOperationScheduler.KEY_SUCCESS to true,
                    RuleOperationScheduler.KEY_MESSAGE to message
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: "操作失败"
            showFinishedNotification("$title 失败", message)
            Result.success(
                workDataOf(
                    RuleOperationScheduler.KEY_SUCCESS to false,
                    RuleOperationScheduler.KEY_MESSAGE to message
                )
            )
        } finally {
            progressJob.cancel()
            subscriptionIdJob.cancel()
        }
    }

    private suspend fun execute(
        type: RuleOperationType,
        subscriptionId: Long,
        blockManager: BlockListManager,
        allowManager: AllowListManager,
        rewriteManager: RewriteRuleManager,
        subscriptionManager: SubscriptionManager
    ): String = when (type) {
        RuleOperationType.ADD_SUBSCRIPTION -> {
            val result = subscriptionManager.addSubscription(
                inputData.getString(RuleOperationScheduler.KEY_URL).orEmpty(),
                inputData.getString(RuleOperationScheduler.KEY_NAME),
                inputData.getString(RuleOperationScheduler.KEY_KIND) ?: com.haoze.dnssr.data.entity.SubscriptionKind.BLOCK
            )
            result.getOrThrow()
            subscriptionManager.latestImportSummary()?.displayMessage("导入成功") ?: "导入成功"
        }
        RuleOperationType.ADD_LOCAL_SUBSCRIPTION -> {
            val uri = requiredUri()
            val result = subscriptionManager.addLocalSubscription(
                uri.toString(),
                inputData.getString(RuleOperationScheduler.KEY_NAME).orEmpty(),
                inputData.getString(RuleOperationScheduler.KEY_KIND) ?: com.haoze.dnssr.data.entity.SubscriptionKind.BLOCK
            ) { readUri(uri) }
            result.getOrThrow()
            subscriptionManager.latestImportSummary()?.displayMessage("导入成功") ?: "导入成功"
        }
        RuleOperationType.EDIT_SUBSCRIPTION -> {
            subscriptionManager.editSubscription(
                subscriptionId,
                inputData.getString(RuleOperationScheduler.KEY_URL).orEmpty(),
                inputData.getString(RuleOperationScheduler.KEY_NAME).orEmpty()
            ).getOrThrow()
            subscriptionManager.latestImportSummary()?.displayMessage("订阅已保存") ?: "订阅已保存"
        }
        RuleOperationType.UPDATE_SUBSCRIPTION -> when (
            val outcome = subscriptionManager.updateSubscription(subscriptionId)
        ) {
            is SubscriptionUpdateOutcome.Updated -> subscriptionManager.latestImportSummary()
                ?.displayMessage("更新成功") ?: "更新成功，共导入 ${outcome.ruleCount} 条规则"
            is SubscriptionUpdateOutcome.NotModified -> "订阅已是最新"
            is SubscriptionUpdateOutcome.Failed -> throw IOException(outcome.error)
        }
        RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS -> {
            var updated = 0
            var unchanged = 0
            var failed = 0
            var totalRules = 0
            subscriptionManager.remoteSubscriptions().forEach { subscription ->
                when (val outcome = subscriptionManager.updateSubscription(subscription.id)) {
                    is SubscriptionUpdateOutcome.Updated -> {
                        updated++
                        totalRules += outcome.ruleCount
                    }
                    is SubscriptionUpdateOutcome.NotModified -> unchanged++
                    is SubscriptionUpdateOutcome.Failed -> failed++
                }
            }
            "检查完成：更新 $updated 个，已是最新 $unchanged 个，失败 $failed 个，共导入 $totalRules 条规则"
        }
        RuleOperationType.IMPORT_RULES -> {
            val rules = AdGuardRuleParser.parseCategorized(readUri(requiredUri()))
            require(!rules.isEmpty()) { "文件中没有可导入的有效 DNS 规则" }
            val total = rules.size
            var completed = 0
            val insertedBlock = blockManager.addRulesBatch(rules.blockRules, LOCAL_IMPORT_SOURCE) { current ->
                setProgressAsync(progressData(type, -1, current, total))
                notifyProgress(titleFor(type), current, total)
            }
            completed += rules.blockRules.size
            val insertedAllow = allowManager.addRulesBatch(rules.allowRules, LOCAL_IMPORT_SOURCE) { current ->
                setProgressAsync(progressData(type, -1, completed + current, total))
                notifyProgress(titleFor(type), completed + current, total)
            }
            val duplicates = rules.duplicateCount + (rules.size - insertedBlock - insertedAllow).coerceAtLeast(0)
            "导入完成：黑名单 $insertedBlock 条，白名单 $insertedAllow 条，重复 $duplicates 条，" +
                "无效/不支持 ${rules.invalidCount + rules.unsupportedCount} 条"
        }
        RuleOperationType.IMPORT_HOSTS_RULES -> {
            val rules = AdGuardRuleParser.parseHostsRewrite(readUri(requiredUri()))
            require(rules.isNotEmpty()) { "文件中没有可导入的真实 IP hosts 规则" }
            val total = rules.size
            val inserted = rewriteManager.addRules(rules, LOCAL_HOSTS_SOURCE, true) { current ->
                setProgressAsync(progressData(type, -1, current, total))
                notifyProgress(titleFor(type), current, total)
            }
            "hosts 导入完成：新增 $inserted 条，跳过 ${total - inserted} 条"
        }
        RuleOperationType.ADD_BLOCK_RULE -> {
            check(blockManager.addRule(inputData.getString(RuleOperationScheduler.KEY_PATTERN).orEmpty())) {
                "规则格式无效或规则已存在"
            }
            "已添加到屏蔽规则"
        }
        RuleOperationType.ADD_ALLOW_RULE -> {
            check(allowManager.addRule(inputData.getString(RuleOperationScheduler.KEY_PATTERN).orEmpty())) {
                "规则格式无效或规则已存在"
            }
            "已添加到白名单规则"
        }
    }

    private fun requiredUri(): Uri = inputData.getString(RuleOperationScheduler.KEY_URI)
        ?.let(Uri::parse) ?: throw IllegalArgumentException("缺少规则文件")

    private fun readUri(uri: Uri): String = applicationContext.contentResolver.openInputStream(uri)
        ?.bufferedReader()?.use { it.readText() }
        ?: throw IOException("无法读取所选文件")

    private fun progressData(type: RuleOperationType, subscriptionId: Long, current: Int, total: Int) =
        workDataOf(
            RuleOperationScheduler.KEY_TYPE to type.name,
            RuleOperationScheduler.KEY_SUBSCRIPTION_ID to subscriptionId,
            RuleOperationScheduler.KEY_CURRENT to current,
            RuleOperationScheduler.KEY_TOTAL to total
        )

    private fun failure(message: String) = Result.failure(
        workDataOf(RuleOperationScheduler.KEY_SUCCESS to false, RuleOperationScheduler.KEY_MESSAGE to message)
    )

    private fun titleFor(type: RuleOperationType): String = when (type) {
        RuleOperationType.ADD_SUBSCRIPTION, RuleOperationType.ADD_LOCAL_SUBSCRIPTION -> "正在导入规则订阅"
        RuleOperationType.EDIT_SUBSCRIPTION -> "正在保存规则订阅"
        RuleOperationType.UPDATE_SUBSCRIPTION -> "正在更新规则订阅"
        RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS -> "正在更新所有规则订阅"
        RuleOperationType.IMPORT_RULES -> "正在导入规则"
        RuleOperationType.IMPORT_HOSTS_RULES -> "正在导入 hosts 规则"
        RuleOperationType.ADD_BLOCK_RULE -> "正在添加屏蔽规则"
        RuleOperationType.ADD_ALLOW_RULE -> "正在添加白名单规则"
    }

    private fun createForegroundInfo(title: String, current: Int, total: Int) = ForegroundInfo(
        notificationId,
        buildNotification(title, current, total, ongoing = true),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    )

    private fun notifyProgress(title: String, current: Int, total: Int) {
        notificationManager.notify(notificationId, buildNotification(title, current, total, ongoing = true))
    }

    private fun buildNotification(title: String, current: Int, total: Int, ongoing: Boolean) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.dns_svgrepo_com)
            .setContentTitle(title)
            .setContentText(if (total > 0) "$current / $total" else "正在准备...")
            .setProgress(total.coerceAtLeast(0), current.coerceAtLeast(0), total <= 0)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(mainPendingIntent())
            .build()

    private fun showFinishedNotification(title: String, message: String) {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.dns_svgrepo_com)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent())
            .build()
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(completionNotificationId, notification)
        }
    }

    private fun mainPendingIntent(): PendingIntent = PendingIntent.getActivity(
        applicationContext,
        notificationId,
        Intent(applicationContext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "规则导入与更新", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "rule_operations"
        const val LOCAL_IMPORT_SOURCE = "local_import"
        const val LOCAL_HOSTS_SOURCE = "local_hosts"
        const val COMPLETION_ID_MASK = 0x40000000
    }
}
