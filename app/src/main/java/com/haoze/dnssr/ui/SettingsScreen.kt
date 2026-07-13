package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.vpn.DnsProvider

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToRuleManagement: (String) -> Unit,
    onNavigateToDataCleanup: (String) -> Unit,
    onNavigateToProviderManagement: (String) -> Unit,
    onNavigateToHomeProviderVisibility: (String) -> Unit,
    onNavigateToRaceModeLatency: (String) -> Unit,
    onNavigateToRaceModeProviders: (String) -> Unit,
    onNavigateToCacheSettings: (String) -> Unit,
    onNavigateToBootstrapSettings: (String) -> Unit,
    onNavigateToLogRetentionSettings: (String) -> Unit,
    onNavigateToForegroundBackgroundSettings: (String) -> Unit,
    onNavigateToExperimentalFeatures: (String) -> Unit,
    onNavigateToAbout: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val providerManagementTitle = "DNS 服务商管理"
    val homeProviderVisibilityTitle = "服务显示"
    val bootstrapSettingsTitle = "Bootstrap DNS 解析设置"
    val latencySettingsTitle = "DNS 查询测速"
    val cacheSettingsTitle = "DNS 缓存设置"
    val raceModeSettingsTitle = "解析模式"
    val ruleManagementTitle = "域名规则"
    val logRetentionTitle = "日志保留"
    val dataCleanupTitle = "数据清理"
    val foregroundBackgroundTitle = "前后台行为"
    val experimentalFeaturesTitle = "实验功能"
    val aboutTitle = "DNSSR 应用信息"

    val currentProviderName = remember {
        DnsProvider.loadSelected(context).name
    }
    val homeProviderVisibilitySummary = if (AppSettings.getHomeProviderVisibility(context).isDefault()) {
        "全部服务"
    } else {
        "已自定义"
    }

    val raceModeSummary = AppSettings.getDnsResolutionMode(context).displayName

    val cachePolicy = remember { AppSettings.getDnsCachePolicy(context) }
    val cachePreset = remember { AppSettings.getDnsCachePreset(context) }
    val cacheSummary = if (cachePolicy.enabled) {
        cachePreset.displayName
    } else {
        "未启用"
    }

    val logRetentionDays = remember { AppSettings.logRetentionDays(context) }
    val logRetentionSummary = "$logRetentionDays 天"
    val raceTestDomain = remember { AppSettings.getRaceTestDomain(context) }
    val bootstrapSummary = remember {
        if (AppSettings.isBootstrapEnabled(context)) {
            "${AppSettings.loadEnabledBootstrapIpEntries(context).size} 个已启用"
        } else {
            "未启用"
        }
    }
    SettingsScaffold(
        title = "应用设置",
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsGroupTitle("解析设置")
            SettingsGroup {
                SettingsNavigationItem(
                    title = providerManagementTitle,
                    subtitle = "选择、添加或编辑 DoH/DoT 服务",
                    value = currentProviderName,
                    valueMaxScreenFraction = 0.5f,
                    onClick = { onNavigateToProviderManagement(providerManagementTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = homeProviderVisibilityTitle,
                    subtitle = "配置首页解析服务列表中显示的协议和服务商",
                    value = homeProviderVisibilitySummary,
                    onClick = { onNavigateToHomeProviderVisibility(homeProviderVisibilityTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = bootstrapSettingsTitle,
                    subtitle = "配置全局 Bootstrap DNS 与智慧权重",
                    value = bootstrapSummary,
                    onClick = { onNavigateToBootstrapSettings(bootstrapSettingsTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = latencySettingsTitle,
                    subtitle = "选择服务商并测试指定域名的实际解析耗时",
                    value = raceTestDomain,
                    onClick = { onNavigateToRaceModeLatency(latencySettingsTitle) }
                )
            }

            SettingsGroupTitle("性能优化")
            SettingsGroup {
                SettingsNavigationItem(
                    title = cacheSettingsTitle,
                    subtitle = "缓存已解析的域名，减少重复查询",
                    value = cacheSummary,
                    onClick = { onNavigateToCacheSettings(cacheSettingsTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = raceModeSettingsTitle,
                    subtitle = "选择一脉直达、择优而行、百舸争流或有备无患",
                    value = raceModeSummary,
                    onClick = { onNavigateToRaceModeProviders(raceModeSettingsTitle) }
                )
            }

            SettingsGroupTitle("规则管理")
            SettingsGroup {
                SettingsNavigationItem(
                    title = ruleManagementTitle,
                    subtitle = "添加屏蔽或白名单规则，导入规则订阅",
                    onClick = { onNavigateToRuleManagement(ruleManagementTitle) }
                )
            }

            SettingsGroupTitle("数据管理")
            SettingsGroup {
                SettingsNavigationItem(
                    title = logRetentionTitle,
                    subtitle = "设置请求日志自动清理时间",
                    value = logRetentionSummary,
                    onClick = { onNavigateToLogRetentionSettings(logRetentionTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = dataCleanupTitle,
                    subtitle = "删除缓存、日志或域名规则",
                    onClick = { onNavigateToDataCleanup(dataCleanupTitle) }
                )
            }

            SettingsGroupTitle("隐私保护")
            SettingsGroup {
                SettingsNavigationItem(
                    title = foregroundBackgroundTitle,
                    subtitle = "后台隐藏、通知常驻",
                    onClick = { onNavigateToForegroundBackgroundSettings(foregroundBackgroundTitle) }
                )
            }

            SettingsGroupTitle("实验功能")
            SettingsGroup {
                SettingsNavigationItem(
                    title = experimentalFeaturesTitle,
                    subtitle = "查看还在开发中的功能",
                    onClick = { onNavigateToExperimentalFeatures(experimentalFeaturesTitle) }
                )
            }

            SettingsGroupTitle("关于应用")
            SettingsGroup {
                SettingsNavigationItem(
                    title = aboutTitle,
                    subtitle = "查看软件说明、作者信息和项目仓库",
                    onClick = { onNavigateToAbout(aboutTitle) }
                )
            }
        }
    }
}
