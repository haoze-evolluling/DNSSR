package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.SettingsGroup
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
    val message by viewModel.message.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let(viewModel::exportRules) }

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
                SettingsTextItem(
                    title = "导出规则",
                    subtitle = "去重、过滤无效规则，并由白名单解决同域名冲突",
                    enabled = operation == ConfigTransferOperation.IDLE,
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                        exportLauncher.launch("DNSSR-rules-$date.txt")
                    }
                )
            }
        }
    }
}
