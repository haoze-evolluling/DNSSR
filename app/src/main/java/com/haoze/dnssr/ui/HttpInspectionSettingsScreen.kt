package com.haoze.dnssr.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem
import com.haoze.dnssr.ui.components.SettingsTextItem
import com.haoze.dnssr.vpn.HttpsInspectionCaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun HttpInspectionSettingsScreen(
    onBack: () -> Unit,
    onNavigateToRequestLogs: () -> Unit,
    onNavigateToApps: () -> Unit
) {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var enabled by remember { mutableStateOf(AppSettings.isHttpInspectionEnabled(context) && supported) }
    var httpsReady by remember { mutableStateOf(AppSettings.isHttpsInspectionReady(context)) }
    var caFingerprint by remember { mutableStateOf<String?>(null) }
    var caBusy by remember { mutableStateOf(false) }
    var showInstallConfirmation by remember { mutableStateOf(false) }
    var showOverwriteConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showCaDetails by remember { mutableStateOf(false) }
    var noticeRequiresDecision by remember {
        mutableStateOf(!AppSettings.isHttpInspectionNoticeAcknowledged(context))
    }
    var showUsageNotice by remember { mutableStateOf(noticeRequiresDecision) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val securitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        caBusy = false
        showInstallConfirmation = true
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        caFingerprint = withContext(Dispatchers.IO) {
            runCatching { HttpsInspectionCaManager.fingerprintSha256() }.getOrNull()
        }
    }

    fun exportCertificate(replaceExisting: Boolean) {
        caBusy = true
        scope.launch {
            val exportedCertificate = withContext(Dispatchers.IO) {
                runCatching {
                    if (replaceExisting) {
                        HttpsInspectionCaManager.deleteExportedCertificatesInDownloads(context)
                    }
                    HttpsInspectionCaManager.exportCertificateToDownloads(context)
                }
            }
            exportedCertificate.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        "CA 已保存到下载/${HttpsInspectionCaManager.EXPORTED_CERTIFICATE_NAME}，请在系统设置中手动安装",
                        Toast.LENGTH_LONG
                    ).show()
                    runCatching { securitySettingsLauncher.launch(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
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

    SettingsScaffold(
        title = "HTTP(S) 流量过滤",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
                noticeRequiresDecision = false
                showUsageNotice = true
            }) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = "使用前说明")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
        ) {
            SettingsGroupTitle("流量过滤")
            SettingsGroup {
                val selectedCount = AppSettings.getHttpInspectionAppPackages(context).size
                SettingsSwitchItem(
                    title = "启用所选应用的流量过滤",
                    subtitle = if (selectedCount == 0) "尚未选择应用；开启后也不会接管任何应用流量" else "已选择 $selectedCount 个应用；其他应用不受影响",
                    checked = enabled,
                    enabled = supported,
                    onCheckedChange = { checked ->
                        enabled = checked
                        AppSettings.setHttpInspectionEnabled(context, checked)
                        RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                    }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "选择过滤应用",
                    subtitle = if (selectedCount == 0) "选择需要进行 HTTP(S) 过滤的应用" else "已选择 $selectedCount 个应用",
                    onClick = { if (supported) onNavigateToApps() }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "HTTP(S) 请求记录",
                    subtitle = "查看逐请求过滤结果和 HTTPS 解密失败直连记录；不保存路径、请求头或正文",
                    onClick = onNavigateToRequestLogs
                )
            }
            SettingsGroupTitle("HTTPS 解密")
            SettingsGroup {
                SettingsNavigationItem(
                    title = "安装用户 CA",
                    subtitle = "导出 CA 到下载目录，并前往系统设置完成安装",
                    value = if (httpsReady) "已安装" else "未安装",
                    onClick = {
                        if (!supported || caBusy) return@SettingsNavigationItem
                        caBusy = true
                        scope.launch {
                            val existingFile = withContext(Dispatchers.IO) {
                                runCatching { HttpsInspectionCaManager.hasExportedCertificateInDownloads(context) }
                            }
                            caBusy = false
                            existingFile.fold(
                                onSuccess = { exists ->
                                    if (exists) showOverwriteConfirmation = true else exportCertificate(false)
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "检查下载目录失败：${error.message ?: "未知错误"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "查看用户 CA",
                    subtitle = "查看当前 DNSSR 用户 CA 的 SHA-256 指纹",
                    onClick = { showCaDetails = true }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsTextItem(
                    title = "重置 DNSSR 用户 CA",
                    subtitle = "废止当前 CA 并生成新 CA；之后必须移除旧证书并重新安装",
                    enabled = supported && !caBusy,
                    textColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    onClick = { showResetConfirmation = true }
                )
            }
        }
    }

    if (showUsageNotice) {
        AlertDialog(
            onDismissRequest = {
                if (!noticeRequiresDecision) showUsageNotice = false
            },
            title = { Text("HTTP(S) 流量过滤说明") },
            text = {
                Text(
                    if (supported) {
                        "这是实验功能，默认关闭且仅处理明确选择的应用。明文 HTTP 会按每个请求的 Host 精确匹配现有黑白名单和订阅规则。HTTPS 只有在目标应用信任 DNSSR 用户 CA、且未使用证书固定或自定义证书校验时，才能解密并按 Host/:authority 逐请求过滤。\n\n" +
                            "Android 7 及以上版本的现代应用通常默认不信任用户安装的 CA；采用证书固定、私有信任库或自定义校验的应用也会拒绝解密。系统中能看到 DNSSR CA，只代表证书已安装，不代表所选应用一定信任它。解密失败时 DNSSR 默认直连以避免应用断网，该 HTTPS 请求将无法按解密后的 authority 过滤。HTTP/3（QUIC）等不支持的协议也会直连。"
                    } else {
                        "HTTP(S) 流量过滤需要 Android 10 或更高版本。当前设备不支持此实验功能，将继续使用 DNS-only 模式。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.setHttpInspectionNoticeAcknowledged(context, true)
                    noticeRequiresDecision = false
                    showUsageNotice = false
                }) { Text("我已知晓") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (noticeRequiresDecision) {
                        showUsageNotice = false
                        onBack()
                    } else {
                        showUsageNotice = false
                    }
                }) { Text(if (noticeRequiresDecision) "暂不使用" else "关闭") }
            }
        )
    }

    if (showCaDetails) {
        AlertDialog(
            onDismissRequest = { showCaDetails = false },
            title = { Text("用户 CA") },
            text = {
                Text(
                    caFingerprint?.let { "SHA-256 指纹：\n$it" }
                        ?: "正在读取当前用户 CA 的 SHA-256 指纹…"
                )
            },
            confirmButton = {
                TextButton(onClick = { showCaDetails = false }) { Text("关闭") }
            }
        )
    }

    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            title = { Text("确认用户 CA 安装") },
            text = {
                Text(
                    "请在系统设置中选择“从存储设备安装 CA 证书”，然后选择下载目录中的 " +
                        HttpsInspectionCaManager.EXPORTED_CERTIFICATE_NAME +
                        "。系统不会向 DNSSR 返回真实安装状态，请仅在系统证书页面确认看到 DNSSR User CA 后点击“已完成安装”。\n\n" +
                        "请注意：用户 CA 不会被所有应用信任。Android 7 及以上版本的现代应用通常默认只信任系统 CA，使用证书固定或自定义证书校验的应用也可能拒绝。安装成功不代表所有 HTTPS 都能解密；不兼容的连接将记录为“HTTPS 解密失败，已直连”。"
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

    if (showOverwriteConfirmation) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirmation = false },
            title = { Text("覆盖现有 CA 文件？") },
            text = {
                Text(
                    "下载目录中已有 ${HttpsInspectionCaManager.EXPORTED_CERTIFICATE_NAME}。继续将删除现有同名文件并导出新的 CA 文件。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirmation = false
                    exportCertificate(true)
                }) { Text("覆盖") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirmation = false }) { Text("取消") }
            }
        )
    }

    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("重置 DNSSR 用户 CA") },
            text = {
                Text(
                    "重置会立即废止 DNSSR 当前使用的 CA，并生成新的私钥和证书。已安装在系统中的旧 CA 不会被自动删除，请稍后到系统凭据设置中手动移除；新 CA 必须重新导出并安装后，兼容应用才能继续进行 HTTPS 解密。"
                )
            },
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
