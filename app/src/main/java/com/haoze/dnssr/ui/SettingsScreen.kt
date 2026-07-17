package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
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
    onNavigateToExcludedApps: (String) -> Unit,
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
    onNavigateToHttpInspection: (String) -> Unit,
    onNavigateToAppearanceSettings: (String) -> Unit,
    onNavigateToExperimentalFeatures: (String) -> Unit,
    onNavigateToAbout: (String) -> Unit,
    onNavigateToSponsor: (String) -> Unit,
    onNavigateToSponsorList: (String) -> Unit,
    onNavigateToCoBuilderList: (String) -> Unit
) {
    val providerManagementTitle = "服务商管理"
    val homeProviderVisibilityTitle = "服务显示"
    val bootstrapSettingsTitle = "Bootstrap 设置"
    val latencySettingsTitle = "查询测速"
    val cacheSettingsTitle = "缓存设置"
    val raceModeSettingsTitle = "解析模式"
    val ruleManagementTitle = "域名规则"
    val excludedAppsTitle = "排除应用"
    val logRetentionTitle = "日志模式"
    val dataCleanupTitle = "数据清理"
    val configTransferTitle = "导入与导出"
    val foregroundBackgroundTitle = "前后台行为"
    val httpInspectionTitle = "HTTP(S) 流量过滤"
    val appearanceSettingsTitle = "外观设置"
    val experimentalFeaturesTitle = "实验功能"
    val aboutTitle = "应用信息"
    val sponsorTitle = "赞助"
    val sponsorListTitle = "赞助者名单"
    val coBuilderListTitle = "共建者名单"

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
                        leadingIcon = Icons.Filled.Dns,
                        onClick = { onNavigateToProviderManagement(providerManagementTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = bootstrapSettingsTitle,
                        subtitle = "配置全局 Bootstrap DNS 与智慧权重",
                        leadingIcon = Icons.Filled.Public,
                        onClick = { onNavigateToBootstrapSettings(bootstrapSettingsTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = latencySettingsTitle,
                        subtitle = "选择服务商并测试指定域名的实际解析耗时",
                        leadingIcon = Icons.Filled.Speed,
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
                        leadingIcon = Icons.Filled.Storage,
                        onClick = { onNavigateToCacheSettings(cacheSettingsTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = raceModeSettingsTitle,
                        subtitle = "选择省电、均衡、极速或主备解析策略",
                        leadingIcon = Icons.AutoMirrored.Filled.AltRoute,
                        onClick = { onNavigateToRaceModeProviders(raceModeSettingsTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = logRetentionTitle,
                        subtitle = "选择 DNS 请求日志的记录范围",
                        leadingIcon = Icons.Filled.History,
                        onClick = { onNavigateToLogRetentionSettings(logRetentionTitle) }
                    )
                }
            }

            item { SettingsGroupTitle("数据管理") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = configTransferTitle,
                        subtitle = "备份或恢复自定义服务与规则订阅",
                        leadingIcon = Icons.Filled.ImportExport,
                        onClick = { onNavigateToConfigTransfer(configTransferTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ruleManagementTitle,
                        subtitle = "添加屏蔽或白名单规则，导入规则订阅",
                        leadingIcon = Icons.AutoMirrored.Filled.Rule,
                        onClick = { onNavigateToRuleManagement(ruleManagementTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = dataCleanupTitle,
                        subtitle = "删除缓存、日志或域名规则",
                        leadingIcon = Icons.Filled.DeleteSweep,
                        onClick = { onNavigateToDataCleanup(dataCleanupTitle) }
                    )
                }
            }

            item { SettingsGroupTitle("外观") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = homeProviderVisibilityTitle,
                        subtitle = "配置首页解析服务列表中显示的协议和服务商",
                        leadingIcon = Icons.Filled.Visibility,
                        onClick = { onNavigateToHomeProviderVisibility(homeProviderVisibilityTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = appearanceSettingsTitle,
                        subtitle = "设置应用的显示外观",
                        leadingIcon = Icons.Filled.Palette,
                        onClick = { onNavigateToAppearanceSettings(appearanceSettingsTitle) }
                    )
                }
            }

            item { SettingsGroupTitle("运行行为") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = foregroundBackgroundTitle,
                        subtitle = "后台隐藏、通知常驻",
                        leadingIcon = Icons.Filled.FlipToBack,
                        onClick = { onNavigateToForegroundBackgroundSettings(foregroundBackgroundTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = excludedAppsTitle,
                        subtitle = "指定使用系统 DNS 的应用",
                        leadingIcon = Icons.Filled.Apps,
                        onClick = { onNavigateToExcludedApps(excludedAppsTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = httpInspectionTitle,
                        subtitle = "按应用检查 HTTP(S) 请求并应用现有域名规则",
                        leadingIcon = Icons.Filled.Http,
                        onClick = { onNavigateToHttpInspection(httpInspectionTitle) }
                    )
                }
            }

            item { SettingsGroupTitle("实验功能") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = experimentalFeaturesTitle,
                        subtitle = "查看还在开发中的功能",
                        leadingIcon = Icons.Filled.Science,
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
                        leadingIcon = Icons.Filled.Info,
                        onClick = { onNavigateToAbout(aboutTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = sponsorTitle,
                        subtitle = "请作者喝杯蜜雪，支持项目持续开发",
                        leadingIcon = Icons.Filled.Favorite,
                        onClick = { onNavigateToSponsor(sponsorTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = sponsorListTitle,
                        subtitle = "感谢每一位支持 DNSSR 项目的朋友",
                        leadingIcon = Icons.Filled.WorkspacePremium,
                        onClick = { onNavigateToSponsorList(sponsorListTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = coBuilderListTitle,
                        subtitle = "感谢提出建议与帮助测试的共建者",
                        leadingIcon = Icons.Filled.Groups,
                        onClick = { onNavigateToCoBuilderList(coBuilderListTitle) }
                    )
                }
            }
        }
    }
}
