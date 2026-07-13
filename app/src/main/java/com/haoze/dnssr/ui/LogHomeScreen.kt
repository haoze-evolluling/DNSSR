package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun LogHomeScreen(
    onBack: () -> Unit,
    onNavigateToDnsLogs: () -> Unit,
    onNavigateToDnsCache: () -> Unit,
    onNavigateToRaceStats: () -> Unit,
    onNavigateToBootstrapStats: () -> Unit,
    onNavigateToSubscriptionInterceptionStats: () -> Unit
) {
    val scrollState = rememberScrollState()

    SettingsScaffold(
        title = "日志",
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsGroupTitle("记录与统计")
            SettingsGroup {
                SettingsNavigationItem(
                    title = "请求记录",
                    subtitle = "查看 DNS 请求和处理结果",
                    onClick = onNavigateToDnsLogs
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = "缓存记录",
                    subtitle = "查看、搜索或清理已缓存的 DNS 结果",
                    onClick = onNavigateToDnsCache
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = "竞速解析",
                    subtitle = "查看服务商的响应速度和成功情况",
                    onClick = onNavigateToRaceStats
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = "Bootstrap 解析",
                    subtitle = "查看 DNS 服务地址的解析情况",
                    onClick = onNavigateToBootstrapStats
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = "规则拦截",
                    subtitle = "查看各订阅拦截请求的次数和占比",
                    onClick = onNavigateToSubscriptionInterceptionStats
                )
            }
        }
    }
}
