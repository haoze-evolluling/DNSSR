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
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun ConfigTransferScreen(
    onBack: () -> Unit,
    title: String = "导入与导出",
    onNavigateToConfigImportExport: () -> Unit
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
                    title = "配置导入与导出",
                    subtitle = "备份或恢复自定义服务与规则订阅",
                    onClick = onNavigateToConfigImportExport
                )
            }
        }
    }
}
