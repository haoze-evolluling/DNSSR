package com.haoze.dnssr.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsTextItem
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.RewriteRuleManager
import com.haoze.dnssr.vpn.BootstrapHealthEngine
import com.haoze.dnssr.vpn.BootstrapHealthStore
import com.haoze.dnssr.vpn.BootstrapLogger
import com.haoze.dnssr.vpn.DnsLogger
import com.haoze.dnssr.vpn.HttpRequestLogger
import com.haoze.dnssr.vpn.ProviderHealthEngine
import com.haoze.dnssr.vpn.ProviderHealthStore
import com.haoze.dnssr.vpn.RaceLogger
import com.haoze.dnssr.vpn.cache.DnsCacheController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataCleanupScreen(
    onBack: () -> Unit,
    title: String = "数据清理",
    onRuntimeDnsSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var pendingAction by remember { mutableStateOf<CleanupAction?>(null) }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            SettingsInfoText(
                text = "以下操作会立即删除本机数据，删除后无法恢复。",
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsGroupTitle("运行数据")
            SettingsGroup {
                SettingsTextItem(
                    title = "删除请求日志",
                    subtitle = "清除 DNS 和 HTTP 的历史请求记录",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.LOG }
                )
                SettingsDivider()
                SettingsTextItem(
                    title = "删除 DNS 缓存",
                    subtitle = "移除已缓存的解析结果，下次访问会重新查询",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.CACHE }
                )
            }

            SettingsGroupTitle("权重数据")
            SettingsGroup {
                SettingsTextItem(
                    title = "恢复 DNS 默认权重",
                    subtitle = "清除竞速模式的健康样本，让择优而行重新按默认权重分配流量",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.PROVIDER_WEIGHT }
                )
                SettingsDivider()
                SettingsTextItem(
                    title = "恢复 Bootstrap 权重",
                    subtitle = "清除 Bootstrap DNS 解析健康样本，重新按默认权重选择",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.BOOTSTRAP_WEIGHT }
                )
            }

            SettingsGroupTitle("用户数据")
            SettingsGroup {
                SettingsTextItem(
                    title = "删除全部规则",
                    subtitle = "清除手动添加和订阅导入的所有域名规则",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.RULE }
                )
                SettingsDivider()
                SettingsTextItem(
                    title = "重置所有新手引导",
                    subtitle = "让所有首次进入说明再次显示",
                    textColor = MaterialTheme.colorScheme.error,
                    onClick = { pendingAction = CleanupAction.SETTINGS_GUIDES }
                )
            }
        }
    }

    pendingAction?.let { action ->
        ConfirmDialog(
            title = action.title,
            text = action.message,
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getInstance(context)
                    when (action) {
                        CleanupAction.CACHE -> {
                            DnsCacheController.clearAll(db.dnsCacheDao())
                        }
                        CleanupAction.LOG -> {
                            DnsLogger(db.dnsLogDao(), AppSettings.logRetentionDays(context)).clearAll()
                            HttpRequestLogger(db.httpRequestLogDao(), AppSettings.logRetentionDays(context)).clearAll()
                            RaceLogger(db.raceLogDao(), AppSettings.logRetentionDays(context)).clearAll()
                            BootstrapLogger(db.bootstrapLogDao(), AppSettings.logRetentionDays(context)).clearAll()
                        }
                        CleanupAction.PROVIDER_WEIGHT -> {
                            ProviderHealthEngine.flushActive(commit = true)
                            val providerIds = ProviderHealthStore.loadAll(context).keys
                            ProviderHealthStore.reset(context, providerIds)
                        }
                        CleanupAction.BOOTSTRAP_WEIGHT -> {
                            BootstrapHealthEngine.flushActive(commit = true)
                            val bootstrapIpIds = BootstrapHealthStore.loadAll(context).keys
                                .plus(AppSettings.loadBootstrapIpEntries(context).map { it.id })
                            BootstrapHealthStore.reset(context, bootstrapIpIds)
                        }
                        CleanupAction.RULE -> {
                            clearAllDomainRules(context)
                        }
                        CleanupAction.SETTINGS_GUIDES -> {
                            AppSettings.resetAllSettingsGuides(context)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (action == CleanupAction.RULE) {
                            onRuntimeDnsSettingsChanged()
                        }
                        pendingAction = null
                        Toast.makeText(context, "已${action.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { pendingAction = null }
        )
    }
}

suspend fun clearAllDomainRules(context: Context) {
    val database = AppDatabase.getInstance(context)
    BlockListManager(database.blockRuleDao()).clearAll()
    AllowListManager(database.allowRuleDao()).clearAll()
    RewriteRuleManager(database.rewriteRuleDao()).clearAll()
    database.subscriptionDao().resetAfterRuleCleanup()
}

private enum class CleanupAction(
    val title: String,
    val message: String
) {
    CACHE("删除 DNS 缓存", "确定要删除所有本地 DNS 缓存吗？下次访问域名时会重新查询。"),
    LOG("删除请求日志", "确定要删除所有 DNS、HTTP 请求日志、竞速统计和 Bootstrap DNS 解析统计吗？"),
    PROVIDER_WEIGHT("恢复竞速模式默认权重", "确定要清除所有服务商健康样本并恢复竞速模式默认权重吗？"),
    BOOTSTRAP_WEIGHT("恢复 Bootstrap IP 默认权重", "确定要清除 Bootstrap DNS 解析健康样本并恢复默认权重吗？"),
    RULE("删除全部规则", "确定要删除全部域名规则吗？屏蔽和白名单规则都会被移除。"),
    SETTINGS_GUIDES("重置所有新手引导", "确定要重置所有应用设置新手引导吗？这不会删除任何配置、规则、缓存、日志或证书。重置后，所有新手引导将会再次显示")
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
