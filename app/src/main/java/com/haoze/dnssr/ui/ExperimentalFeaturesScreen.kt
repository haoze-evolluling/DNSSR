package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Http
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem

@Composable
fun ExperimentalFeaturesScreen(
    onBack: () -> Unit,
    onNavigateToDoh3Service: () -> Unit,
    onNavigateToHttpInspection: () -> Unit,
    title: String = "实验功能"
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var legacyIconEnabled by remember {
        mutableStateOf(AppSettings.isLegacyIconEnabled(context))
    }
    var legacyLogPageEnabled by remember {
        mutableStateOf(AppSettings.isLegacyLogPageEnabled(context))
    }
    fun saveLegacyIconEnabled(enabled: Boolean) {
        legacyIconEnabled = enabled
        AppSettings.setLegacyIconEnabled(context, enabled)
        LauncherIconManager.setLegacyIconEnabled(context, enabled)
    }

    fun saveLegacyLogPageEnabled(enabled: Boolean) {
        legacyLogPageEnabled = enabled
        AppSettings.setLegacyLogPageEnabled(context, enabled)
    }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsInfoText(
                text = "这里会集中放置尚未成熟、仍在开发中的功能。实验性功能可能调整、移除或存在兼容性问题。",
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsGroupTitle("服务")
            SettingsGroup {
                SettingsNavigationItem(
                    title = "HTTP 流量过滤",
                    subtitle = "按应用配置明文 HTTP 与 HTTPS 域名过滤",
                    leadingIcon = Icons.Filled.Http,
                    onClick = onNavigateToHttpInspection
                )
                com.haoze.dnssr.ui.components.SettingsDivider()
                SettingsNavigationItem(
                    title = "DOH3 服务",
                    subtitle = "查看开发进度",
                    leadingIcon = Icons.Filled.Construction,
                    onClick = onNavigateToDoh3Service
                )
            }

            SettingsGroupTitle("外观")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "使用旧版图标",
                    subtitle = "开启后仅将桌面入口图标切换为旧版样式",
                    checked = legacyIconEnabled,
                    onCheckedChange = ::saveLegacyIconEnabled
                )
            }
            SettingsInfoText("桌面图标可能需要等待启动器刷新；如果未立即变化，可回到桌面稍等片刻。")

            SettingsGroupTitle("日志")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "旧版日志页面",
                    subtitle = "开启后首页日志按钮显示当前的日志分组页；关闭后显示新版日志仪表盘",
                    checked = legacyLogPageEnabled,
                    onCheckedChange = ::saveLegacyLogPageEnabled
                )
            }
        }
    }
}
