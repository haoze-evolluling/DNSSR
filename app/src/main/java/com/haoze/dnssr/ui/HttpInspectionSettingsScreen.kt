package com.haoze.dnssr.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem
import com.haoze.dnssr.ui.components.SettingsTextItem
import com.haoze.dnssr.vpn.GoInspectionCaManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@Composable
fun HttpInspectionSettingsScreen(
    onBack: () -> Unit,
    onNavigateToRequestLogs: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToCaGuide: () -> Unit
) {
    val context = LocalContext.current
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    var enabled by remember { mutableStateOf(AppSettings.isHttpInspectionEnabled(context) && supported) }
    var httpsReady by remember { mutableStateOf(AppSettings.isHttpsInspectionReady(context)) }
    var filterHttp3 by remember { mutableStateOf(AppSettings.isHttp3InspectionEnabled(context)) }
    var blockEncryptedDns by remember { mutableStateOf(AppSettings.isEncryptedDnsBlockingEnabled(context)) }
    var caFingerprint by remember { mutableStateOf<String?>(null) }
    var caBusy by remember { mutableStateOf(false) }
    var showInstallConfirmation by remember { mutableStateOf(false) }
    var showOverwriteConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showCaDetails by remember { mutableStateOf(false) }
    var showUsageNotice by remember {
        mutableStateOf(!AppSettings.isSettingsGuideAcknowledged(context, SettingsGuides.HTTP_INSPECTION.id))
    }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val securitySettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        scope.launch {
            httpsReady = withContext(Dispatchers.IO) {
                runCatching { GoInspectionCaManager.isInstalled(context) }.getOrDefault(false)
            }
            AppSettings.setHttpsInspectionReady(context, httpsReady)
            caBusy = false
            showInstallConfirmation = !httpsReady
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        caFingerprint = withContext(Dispatchers.IO) {
            runCatching { GoInspectionCaManager.fingerprintSha256(context) }.getOrNull()
        }
    }

    fun exportCertificate(replaceExisting: Boolean) {
        caBusy = true
        scope.launch {
            val exportedCertificate = withContext(Dispatchers.IO) {
                runCatching {
                    if (replaceExisting) {
                        GoInspectionCaManager.deleteExportedCertificatesInDownloads(context)
                    }
                    GoInspectionCaManager.exportCertificateToDownloads(context)
                }
            }
            exportedCertificate.fold(
                onSuccess = {
                    Toast.makeText(
                        context,
                        "CA 已保存到下载/${GoInspectionCaManager.EXPORTED_CERTIFICATE_NAME}，请在系统设置中手动安装",
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
                        "导出 HTTPS 根证书失败：${error.message ?: "未知错误"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    SettingsScaffold(
        title = "HTTPS 过滤 (Beta)",
        onBack = onBack,
        actions = {
            IconButton(onClick = {
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
            SettingsGroupTitle("过滤范围")
            SettingsGroup {
                val selectedCount = AppSettings.getHttpInspectionAppPackages(context).size
                SettingsSwitchItem(
                    title = "启用所选应用的 HTTP(S) 检查",
                    subtitle = if (selectedCount == 0) "尚未选择应用；开启后只保留 DNS 过滤" else "已选择 $selectedCount 个应用；其他应用透明转发",
                    checked = enabled,
                    enabled = supported,
                    onCheckedChange = { checked ->
                        if (checked && AppSettings.getDnsResolutionMode(context) in setOf(
                                DnsResolutionMode.SMART_PREDICTION,
                                DnsResolutionMode.PARALLEL_RACE
                            )) {
                            AppSettings.setDnsResolutionMode(context, DnsResolutionMode.SINGLE)
                        }
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
            }
            SettingsGroupTitle("协议与兼容性")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "尝试检查 HTTP/3",
                    subtitle = "阻断所选应用的 QUIC，促使其回退到可检查的 TCP；部分站点可能加载失败",
                    checked = filterHttp3,
                    enabled = supported,
                    onCheckedChange = { checked ->
                        filterHttp3 = checked
                        AppSettings.setHttp3InspectionEnabled(context, checked)
                        RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                    }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsSwitchItem(
                    title = "阻止过滤应用使用加密 DNS",
                    subtitle = "仅阻断所选应用的 DNS-over-TLS（TCP 853），防止其绕过域名规则",
                    checked = blockEncryptedDns,
                    enabled = supported,
                    onCheckedChange = { checked ->
                        blockEncryptedDns = checked
                        AppSettings.setEncryptedDnsBlockingEnabled(context, checked)
                        RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                    }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "HTTP(S) 请求记录",
                    subtitle = "查看 Go 隧道产生的逐请求结果和 HTTPS 自动旁路记录",
                    onClick = onNavigateToRequestLogs
                )
            }
            SettingsGroupTitle("CA 证书")
            SettingsGroup {
                SettingsNavigationItem(
                    title = "安装和卸载CA证书方法",
                    subtitle = "查看 Android 系统 CA 证书的安装、卸载和安全说明",
                    onClick = onNavigateToCaGuide
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "安装 HTTPS 根证书",
                    subtitle = "导出 DNSSR 根证书，并前往系统设置完成安装",
                    value = if (httpsReady) "已安装" else "未安装",
                    onClick = {
                        if (!supported || caBusy) return@SettingsNavigationItem
                        caBusy = true
                        scope.launch {
                            val existingFile = withContext(Dispatchers.IO) {
                                runCatching { GoInspectionCaManager.hasExportedCertificateInDownloads(context) }
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
                    title = "查看 HTTPS 根证书",
                    subtitle = "查看 Go 隧道当前根证书的 SHA-256 指纹",
                    onClick = { showCaDetails = true }
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsTextItem(
                    title = "重置 HTTPS 根证书",
                    subtitle = "废止 Go 隧道当前证书并生成新证书；之后需要重新安装",
                    enabled = supported && !caBusy,
                    textColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    onClick = { showResetConfirmation = true }
                )
            }
        }
    }

    if (showUsageNotice) {
        BackHandler(enabled = true) {}
        AlertDialog(
            onDismissRequest = {},
            title = { Text(SettingsGuides.HTTP_INSPECTION.title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (supported) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.error)) {
                                    append(
                                        "此功能不适合没有相关经验的用户。安装、卸载或重新安装 CA 证书需要一定操作能力，操作不当可能导致部分应用无法联网；仅在你能自行处理这些问题时使用。"
                                    )
                                }
                                append("\n\n")
                                append(
                                    "开启后，仅对明确选择的应用进行 HTTP(S) 流量检查，其他应用透明转发。HTTPS 仅在应用信任 DNSSR 根证书且未使用证书固定或自定义校验时才能解密。"
                                )
                                append("\n\n")
                                append(
                                    "不兼容的连接会自动旁路并直接转发。HTTP/3（QUIC）默认直连；开启“尝试检查 HTTP/3”后，会阻断所选应用的 UDP 443，促使支持回退的客户端改用 TCP。"
                                )
                            }
                        )
                    } else {
                        Text(
                            "HTTP(S) 流量检查需要 Android 10 或更高版本，当前设备不满足运行条件，因此本页功能无法启用，DNSSR 将继续使用 DNS-only 模式。DNS 解析、域名规则和其他基础功能不会受到影响，也不需要安装根证书。若以后升级到受支持的系统，请在启用前了解证书安装、应用信任和 HTTPS 解密的限制；配置不当可能导致部分应用无法联网，证书固定或自定义校验的连接也可能无法被检查。"
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.acknowledgeSettingsGuide(context, SettingsGuides.HTTP_INSPECTION.id)
                    showUsageNotice = false
                }) { Text("我知道了") }
            }
        )
    }

    if (showCaDetails) {
        AlertDialog(
            onDismissRequest = { showCaDetails = false },
            title = { Text("HTTPS 根证书") },
            text = {
                Text(
                    caFingerprint?.let { "SHA-256 指纹：\n$it" }
                        ?: "正在读取 Go 隧道根证书的 SHA-256 指纹…"
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
            title = { Text("验证 HTTPS 根证书") },
            text = {
                Text(
                    "请在系统设置中选择“从存储设备安装 CA 证书”，然后选择下载目录中的 " +
                        GoInspectionCaManager.EXPORTED_CERTIFICATE_NAME +
                        "。返回后 DNSSR 会通过系统 CA 存储按 SHA-256 指纹验证安装状态；若系统限制读取证书库，状态会保持未验证。\n\n" +
                        "安装成功只表示证书已进入系统用户凭据库，不代表所有应用都会信任它。证书固定、自定义校验、双向 TLS 等不兼容连接会由 Go 隧道自动旁路，并记录为“HTTPS 自动旁路”。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        httpsReady = withContext(Dispatchers.IO) {
                            runCatching { GoInspectionCaManager.isInstalled(context) }.getOrDefault(false)
                        }
                        AppSettings.setHttpsInspectionReady(context, httpsReady)
                        showInstallConfirmation = !httpsReady
                    }
                }) { Text("重新验证") }
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
                    "下载目录中已有 ${GoInspectionCaManager.EXPORTED_CERTIFICATE_NAME}。继续将删除现有同名文件并导出新的 CA 文件。"
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
            title = { Text("重置 HTTPS 根证书") },
            text = {
                Text(
                    "重置会删除 Go 隧道当前私有 CA 并生成新的私钥和根证书。系统中已安装的旧证书不会自动删除，请到系统凭据设置中手动移除；新证书重新安装并验证后，兼容应用才能继续进行 HTTPS 检查。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    caBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                GoInspectionCaManager.reset(context)
                                GoInspectionCaManager.fingerprintSha256(context)
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
                            if (result.isSuccess) "HTTPS 根证书已重置" else "重置 HTTPS 根证书失败",
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
