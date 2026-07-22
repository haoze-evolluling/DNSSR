package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsTextItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RuleExportScreen(
    onBack: () -> Unit,
    title: String = "规则导出",
    viewModel: ConfigTransferViewModel = viewModel()
) {
    val context = LocalContext.current
    val operation by viewModel.operation.collectAsState()
    val exportProgress by viewModel.ruleExportProgress.collectAsState()
    val exportProgressText by viewModel.ruleExportProgressText.collectAsState()
    val message by viewModel.message.collectAsState()
    var exportType by remember { mutableStateOf(RuleExportType.ALL) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportRules(it, exportType) } }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())
        ) {
            SettingsInfoText(
                text = "仅导出当前生效的规则。导出的 TXT 文件可在规则订阅中作为本地文件导入。",
                modifier = Modifier.padding(top = 8.dp)
            )
            SettingsGroup {
                RuleExportItem(
                    title = "导出全部订阅规则",
                    subtitle = "导出所有订阅导入的当前生效规则",
                    type = RuleExportType.SUBSCRIPTIONS,
                    operation = operation,
                    isSelected = exportType == RuleExportType.SUBSCRIPTIONS,
                    exportProgress = exportProgress,
                    exportProgressText = exportProgressText,
                    onExport = { type ->
                        exportType = type
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("DNSSR-rules-${type.fileNameSuffix}-$date.txt")
                    }
                )
                SettingsDivider()
                RuleExportItem(
                    title = "导出全部手动添加规则",
                    subtitle = "导出所有手动添加的当前生效规则",
                    type = RuleExportType.MANUAL,
                    operation = operation,
                    isSelected = exportType == RuleExportType.MANUAL,
                    exportProgress = exportProgress,
                    exportProgressText = exportProgressText,
                    onExport = { type ->
                        exportType = type
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("DNSSR-rules-${type.fileNameSuffix}-$date.txt")
                    }
                )
                SettingsDivider()
                RuleExportItem(
                    title = "导出全部规则",
                    subtitle = "合并订阅导入与手动添加的当前生效规则",
                    type = RuleExportType.ALL,
                    operation = operation,
                    isSelected = exportType == RuleExportType.ALL,
                    exportProgress = exportProgress,
                    exportProgressText = exportProgressText,
                    onExport = { type ->
                        exportType = type
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("DNSSR-rules-${type.fileNameSuffix}-$date.txt")
                    }
                )
            }
        }
    }
}

@Composable
private fun RuleExportItem(
    title: String,
    subtitle: String,
    type: RuleExportType,
    operation: ConfigTransferOperation,
    isSelected: Boolean,
    exportProgress: Float,
    exportProgressText: String,
    onExport: (RuleExportType) -> Unit
) {
    SettingsTextItem(
        title = title,
        subtitle = subtitle,
        subtitleContent = {
            if (operation == ConfigTransferOperation.EXPORTING && isSelected) {
                LinearProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Text(
                    text = exportProgressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        enabled = operation == ConfigTransferOperation.IDLE,
        onClick = { onExport(type) }
    )
}
