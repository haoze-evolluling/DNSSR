package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun ConfigTransferScreen(
    onBack: () -> Unit,
    title: String = "导入与导出",
    onNavigateToConfigImportExport: () -> Unit,
    onNavigateToRuleExport: () -> Unit
) {
    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroup {
                SettingsNavigationItem(
                    title = "设置配置",
                    subtitle = "备份或恢复自定义服务与规则订阅",
                    onClick = onNavigateToConfigImportExport
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = "规则导出",
                    subtitle = "将当前生效规则导出为可订阅的 TXT 文件",
                    onClick = onNavigateToRuleExport
                )
            }
        }
    }
}
