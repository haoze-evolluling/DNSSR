package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.MagSafeLoadingIndicator
import com.haoze.dnssr.ui.components.SettingsCheckboxItem
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsTextItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConfigImportExportScreen(
    onBack: () -> Unit,
    title: String = "配置导入与导出",
    viewModel: ConfigTransferViewModel = viewModel()
) {
    val context = LocalContext.current
    val operation by viewModel.operation.collectAsState()
    val busy = operation != ConfigTransferOperation.IDLE
    val message by viewModel.message.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()
    var providers by remember { mutableStateOf(true) }
    var bootstrapIps by remember { mutableStateOf(true) }
    var subscriptions by remember { mutableStateOf(true) }
    var excludedApps by remember { mutableStateOf(true) }
    var showImportOverlay by remember { mutableStateOf(false) }
    val selection = ConfigExportSelection(providers, bootstrapIps, subscriptions, excludedApps)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.export(it, selection) } }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(operation) {
        if (operation == ConfigTransferOperation.IMPORTING) showImportOverlay = true
    }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
        ) {
            SettingsInfoText(
                text = "配置文件仅包含所选的自定义配置，不包含日志、缓存和已下载的域名规则。",
                modifier = Modifier.padding(top = 8.dp)
            )
            SettingsGroupTitle("导出内容")
            SettingsGroup {
                SettingsCheckboxItem("自定义 DNS 服务商", providers, { providers = it }, subtitle = "名称、协议和解析地址", enabled = !busy)
                SettingsDivider()
                SettingsCheckboxItem("自定义 Bootstrap IP", bootstrapIps, { bootstrapIps = it }, subtitle = "名称、IP 和启用状态", enabled = !busy)
                SettingsDivider()
                SettingsCheckboxItem("网络规则订阅", subscriptions, { subscriptions = it }, subtitle = "订阅名称和链接", enabled = !busy)
                SettingsDivider()
                SettingsCheckboxItem("排除应用", excludedApps, { excludedApps = it }, subtitle = "使用系统 DNS 的应用包名", enabled = !busy)
            }
            SettingsGroupTitle("配置文件")
            SettingsGroup {
                SettingsTextItem(
                    title = "导出配置",
                    subtitle = "将勾选内容保存为 JSON 配置文件",
                    enabled = !busy && (providers || bootstrapIps || subscriptions || excludedApps),
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("DNSSR-config-$date.json")
                    }
                )
                SettingsDivider()
                SettingsTextItem(
                    title = "导入配置",
                    subtitle = "合并配置并跳过本机已有项目；网络订阅不会立即下载",
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                )
            }
        }
    }

    if (showImportOverlay) {
        ImportingOverlay(
            visible = operation == ConfigTransferOperation.IMPORTING,
            progress = importProgress,
            onExitFinished = { showImportOverlay = false }
        )
    }
}

@Composable
private fun ImportingOverlay(visible: Boolean, progress: ConfigImportProgress, onExitFinished: () -> Unit) {
    val backgroundColor = MaterialTheme.colorScheme.surface
    val contentColor = MaterialTheme.colorScheme.onSurface
    val visibilityState = remember { MutableTransitionState(false).apply { targetState = visible } }
    var hasEntered by remember { mutableStateOf(false) }

    LaunchedEffect(visible) { visibilityState.targetState = visible }
    LaunchedEffect(visibilityState.currentState) { if (visibilityState.currentState) hasEntered = true }
    LaunchedEffect(visibilityState.isIdle, visibilityState.currentState, hasEntered) {
        if (hasEntered && visibilityState.isIdle && !visibilityState.currentState) onExitFinished()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(backgroundColor.copy(alpha = 0.96f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    MagSafeLoadingIndicator(trackColor = contentColor)
                    Spacer(Modifier.height(28.dp))
                    Text("正在导入", style = MaterialTheme.typography.titleMedium, color = contentColor)
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { if (progress.total > 0) progress.processed.toFloat() / progress.total else 0f },
                        modifier = Modifier.width(220.dp),
                        color = Color(0xFF31E8C3),
                        trackColor = contentColor.copy(alpha = 0.12f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (progress.total > 0) "已处理 ${progress.processed} / ${progress.total}" else "正在准备",
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(progress.currentItem, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.72f))
                }
            }
        }
    }
}
