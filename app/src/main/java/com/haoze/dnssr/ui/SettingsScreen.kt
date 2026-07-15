package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToRuleManagement: (String) -> Unit,
    onNavigateToDataCleanup: (String) -> Unit,
    onNavigateToConfigTransfer: (String) -> Unit,
    onNavigateToProviderManagement: (String) -> Unit,
    onNavigateToHomeProviderVisibility: (String) -> Unit,
    onNavigateToRaceModeLatency: (String) -> Unit,
    onNavigateToRaceModeProviders: (String) -> Unit,
    onNavigateToCacheSettings: (String) -> Unit,
    onNavigateToBootstrapSettings: (String) -> Unit,
    onNavigateToLogRetentionSettings: (String) -> Unit,
    onNavigateToForegroundBackgroundSettings: (String) -> Unit,
    onNavigateToExperimentalFeatures: (String) -> Unit,
    onNavigateToAbout: (String) -> Unit,
    onNavigateToSponsor: (String) -> Unit,
    onNavigateToSponsorList: (String) -> Unit
) {
    val providerManagementTitle = "服务商管理"
    val homeProviderVisibilityTitle = "服务显示"
    val bootstrapSettingsTitle = "Bootstrap 设置"
    val latencySettingsTitle = "查询测速"
    val cacheSettingsTitle = "缓存设置"
    val raceModeSettingsTitle = "解析模式"
    val ruleManagementTitle = "域名规则"
    val logRetentionTitle = "日志保留"
    val dataCleanupTitle = "数据清理"
    val configTransferTitle = "导入与导出"
    val foregroundBackgroundTitle = "前后台行为"
    val experimentalFeaturesTitle = "实验功能"
    val aboutTitle = "应用信息"
    val sponsorTitle = "赞助"
    val sponsorListTitle = "赞助者名单"

    SettingsScaffold(
        title = "应用设置",
        onBack = onBack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            item { SettingsGroupTitle("解析设置") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = providerManagementTitle,
                    subtitle = "选择、添加或编辑 DoH/DoT 服务",
                    onClick = { onNavigateToProviderManagement(providerManagementTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = homeProviderVisibilityTitle,
                    subtitle = "配置首页解析服务列表中显示的协议和服务商",
                    onClick = { onNavigateToHomeProviderVisibility(homeProviderVisibilityTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = bootstrapSettingsTitle,
                    subtitle = "配置全局 Bootstrap DNS 与智慧权重",
                    onClick = { onNavigateToBootstrapSettings(bootstrapSettingsTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = latencySettingsTitle,
                    subtitle = "选择服务商并测试指定域名的实际解析耗时",
                    onClick = { onNavigateToRaceModeLatency(latencySettingsTitle) }
                )
                }
            }

            item { SettingsGroupTitle("性能优化") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = cacheSettingsTitle,
                    subtitle = "缓存已解析的域名，减少重复查询",
                    onClick = { onNavigateToCacheSettings(cacheSettingsTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = raceModeSettingsTitle,
                    subtitle = "选择一脉直达、择优而行、百舸争流或有备无患",
                    onClick = { onNavigateToRaceModeProviders(raceModeSettingsTitle) }
                )
                }
            }

            item { SettingsGroupTitle("规则管理") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = ruleManagementTitle,
                    subtitle = "添加屏蔽或白名单规则，导入规则订阅",
                    onClick = { onNavigateToRuleManagement(ruleManagementTitle) }
                )
                }
            }

            item { SettingsGroupTitle("数据管理") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = configTransferTitle,
                    subtitle = "备份或恢复自定义服务与规则订阅",
                    onClick = { onNavigateToConfigTransfer(configTransferTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = logRetentionTitle,
                    subtitle = "设置请求日志自动清理时间",
                    onClick = { onNavigateToLogRetentionSettings(logRetentionTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = dataCleanupTitle,
                    subtitle = "删除缓存、日志或域名规则",
                    onClick = { onNavigateToDataCleanup(dataCleanupTitle) }
                )
                }
            }

            item { SettingsGroupTitle("隐私保护") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = foregroundBackgroundTitle,
                    subtitle = "后台隐藏、通知常驻",
                    onClick = { onNavigateToForegroundBackgroundSettings(foregroundBackgroundTitle) }
                )
                }
            }

            item { SettingsGroupTitle("实验功能") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = experimentalFeaturesTitle,
                    subtitle = "查看还在开发中的功能",
                    onClick = { onNavigateToExperimentalFeatures(experimentalFeaturesTitle) }
                )
                }
            }

            item { SettingsGroupTitle("关于应用") }
            item {
                SettingsGroup {
                SettingsNavigationItem(
                    title = aboutTitle,
                    subtitle = "查看软件说明、作者信息和项目仓库",
                    onClick = { onNavigateToAbout(aboutTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = sponsorTitle,
                    subtitle = "请作者喝杯蜜雪，支持项目持续开发",
                    onClick = { onNavigateToSponsor(sponsorTitle) }
                )
                SettingsDivider()
                SettingsNavigationItem(
                    title = sponsorListTitle,
                    subtitle = "感谢每一位支持 DNSSR 项目的朋友",
                    onClick = { onNavigateToSponsorList(sponsorListTitle) }
                )
                }
            }
        }
    }
}
