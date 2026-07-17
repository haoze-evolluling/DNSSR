package com.haoze.dnssr.ui

import android.content.pm.ApplicationInfo
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsCheckboxItem
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsLoadingContent
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem
import com.haoze.dnssr.ui.components.SettingsTextItem
import com.haoze.dnssr.vpn.HttpsInspectionCaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

private data class InspectionApp(
    val label: String,
    val packageName: String,
    val normalizedLabel: String,
    val normalizedPackageName: String
)

@Composable
fun HttpInspectionSettingsScreen(
    onBack: () -> Unit,
    onNavigateToRequestLogs: () -> Unit
) {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var enabled by remember { mutableStateOf(AppSettings.isHttpInspectionEnabled(context) && supported) }
    var selectedPackages by remember { mutableStateOf(AppSettings.getHttpInspectionAppPackages(context)) }
    var apps by remember { mutableStateOf<List<InspectionApp>?>(null) }
    var query by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var httpsReady by remember { mutableStateOf(AppSettings.isHttpsInspectionReady(context)) }
    var caFingerprint by remember { mutableStateOf<String?>(null) }
    var caBusy by remember { mutableStateOf(false) }
    var showInstallConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val securitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        caBusy = false
        showInstallConfirmation = true
    }

    LaunchedEffect(Unit) {
        caFingerprint = withContext(Dispatchers.IO) {
            runCatching { HttpsInspectionCaManager.fingerprintSha256() }.getOrNull()
        }
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { info ->
                    val label = info.loadLabel(context.packageManager).toString()
                    InspectionApp(
                        label = label,
                        packageName = info.packageName,
                        normalizedLabel = label.lowercase(Locale.ROOT),
                        normalizedPackageName = info.packageName.lowercase(Locale.ROOT)
                    )
                }
                .sortedWith(compareBy<InspectionApp> { it.normalizedLabel }.thenBy { it.packageName })
                .toList()
        }
    }
    LaunchedEffect(query) {
        delay(250)
        debouncedQuery = query.trim().lowercase(Locale.ROOT)
    }

    SettingsScaffold(title = "HTTP 流量过滤", onBack = onBack) { innerPadding ->
        val loadedApps = apps
        if (loadedApps == null) {
            SettingsLoadingContent(Modifier.padding(innerPadding))
            return@SettingsScaffold
        }
        val visibleApps = loadedApps.filter { app ->
            debouncedQuery.isEmpty() || app.normalizedLabel.contains(debouncedQuery) ||
                app.normalizedPackageName.contains(debouncedQuery)
        }
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsInfoText(
                if (supported) {
                    "实验功能默认关闭。明文 HTTP 已支持逐请求过滤；HTTPS 解密需要安装 DNSSR 用户 CA。"
                } else {
                    "HTTP 流量过滤需要 Android 10 或更高版本；当前设备继续使用 DNS-only 模式。"
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            SettingsSwitchItem(
                title = "启用 HTTP 流量过滤",
                subtitle = if (selectedPackages.isEmpty()) "尚未选择检查应用" else "已选择 ${selectedPackages.size} 个应用",
                checked = enabled,
                enabled = supported,
                onCheckedChange = { enabled = it }
            )
            SettingsNavigationItem(
                title = "安装 DNSSR 用户 CA",
                subtitle = caFingerprint?.let { "SHA-256：$it" } ?: "正在准备安装专用 CA",
                value = if (httpsReady) "已确认" else "未就绪",
                onClick = {
                    if (caBusy || !supported) return@SettingsNavigationItem
                    caBusy = true
                    scope.launch {
                        val exportedCertificate = withContext(Dispatchers.IO) {
                            runCatching { HttpsInspectionCaManager.exportCertificateToDownloads(context) }
                        }
                        exportedCertificate.fold(
                            onSuccess = {
                                Toast.makeText(
                                    context,
                                    "证书已保存到下载/${HttpsInspectionCaManager.EXPORTED_CERTIFICATE_NAME}",
                                    Toast.LENGTH_LONG
                                ).show()
                                val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                runCatching { securitySettingsLauncher.launch(intent) }
                                    .onFailure {
                                        caBusy = false
                                        showInstallConfirmation = true
                                    }
                            },
                            onFailure = { error ->
                                caBusy = false
                                Toast.makeText(
                                    context,
                                    "导出用户 CA 失败：${error.message ?: "未知错误"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            )
            SettingsTextItem(
                title = "重置 DNSSR 用户 CA",
                subtitle = "立即废止当前 CA 并生成新的安装专用证书",
                enabled = supported && !caBusy,
                textColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                onClick = { showResetConfirmation = true }
            )
            SettingsNavigationItem(
                title = "HTTP 请求记录",
                subtitle = "仅保存应用、authority、协议、结果、匹配规则和时间",
                onClick = onNavigateToRequestLogs
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索应用或包名") },
                singleLine = true,
                enabled = supported,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                shape = SettingsCornerShape,
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        SettingsCheckboxItem(
                            title = app.label,
                            subtitle = app.packageName,
                            checked = app.packageName in selectedPackages,
                            enabled = supported,
                            onCheckedChange = { checked ->
                                selectedPackages = if (checked) {
                                    selectedPackages + app.packageName
                                } else {
                                    selectedPackages - app.packageName
                                }
                            }
                        )
                        if (index != visibleApps.lastIndex) SettingsDivider()
                    }
                }
            }
            Button(
                onClick = {
                    AppSettings.setHttpInspectionEnabled(context, enabled)
                    AppSettings.setHttpInspectionAppPackages(context, selectedPackages)
                    val excludedPackages = AppSettings.getExcludedAppPackages(context)
                    AppSettings.setExcludedAppPackages(context, excludedPackages - selectedPackages)
                    RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                    Toast.makeText(context, "HTTP 流量过滤配置已保存", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                enabled = supported,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("保存")
            }
        }
    }

    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            title = { Text("确认 CA 安装状态") },
            text = {
                Text(
                    "请在系统设置中选择“从存储设备安装 CA 证书”，然后选择下载目录中的 " +
                        HttpsInspectionCaManager.EXPORTED_CERTIFICATE_NAME +
                        "。系统不会向 DNSSR 返回可信状态，请仅在安装完成后确认。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    httpsReady = true
                    AppSettings.setHttpsInspectionReady(context, true)
                    showInstallConfirmation = false
                }) { Text("已完成安装") }
            },
            dismissButton = {
                TextButton(onClick = { showInstallConfirmation = false }) { Text("尚未完成") }
            }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("重置 DNSSR 用户 CA") },
            text = { Text("已安装的旧 CA 将无法用于后续 HTTPS 解密。重置后需要重新安装新 CA。") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    caBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                HttpsInspectionCaManager.reset()
                                HttpsInspectionCaManager.fingerprintSha256()
                            }
                        }
                        caBusy = false
                        result.onSuccess { fingerprint ->
                            caFingerprint = fingerprint
                            httpsReady = false
                            AppSettings.setHttpsInspectionReady(context, false)
                        }
                        Toast.makeText(
                            context,
                            if (result.isSuccess) "用户 CA 已重置" else "重置用户 CA 失败",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) { Text("立即重置") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("取消") }
            }
        )
    }
}
