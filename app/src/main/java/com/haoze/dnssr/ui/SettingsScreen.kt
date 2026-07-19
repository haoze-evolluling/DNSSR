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
    onNavigateToRuleManagement: () -> Unit,
    onNavigateToExcludedApps: () -> Unit,
    onNavigateToDataCleanup: () -> Unit,
    onNavigateToConfigTransfer: () -> Unit,
    onNavigateToProviderManagement: () -> Unit,
    onNavigateToHomeProviderVisibility: () -> Unit,
    onNavigateToRaceModeLatency: () -> Unit,
    onNavigateToRaceModeProviders: () -> Unit,
    onNavigateToCacheSettings: () -> Unit,
    onNavigateToBootstrapSettings: () -> Unit,
    onNavigateToLogRetentionSettings: () -> Unit,
    onNavigateToForegroundBackgroundSettings: () -> Unit,
    onNavigateToHttpInspection: () -> Unit,
    onNavigateToAppearanceSettings: () -> Unit,
    onNavigateToExperimentalFeatures: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSponsor: () -> Unit,
    onNavigateToSponsorList: () -> Unit,
    onNavigateToCoBuilderList: () -> Unit
) {
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
                        title = ScreenDestinations.providerManagement.title,
                        subtitle = "选择、添加或编辑 DoH/DoT 服务",
                        leadingIcon = Icons.Filled.Dns,
                        onClick = onNavigateToProviderManagement
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.bootstrapSettings.title,
                        subtitle = "配置全局 Bootstrap DNS 与智慧权重",
                        leadingIcon = Icons.Filled.Public,
                        onClick = onNavigateToBootstrapSettings
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.raceModeLatency.title,
                        subtitle = "选择服务商并测试指定域名的实际解析耗时",
                        leadingIcon = Icons.Filled.Speed,
                        onClick = onNavigateToRaceModeLatency
                    )
                }
            }

            item { SettingsGroupTitle("性能优化") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.cacheSettings.title,
                        subtitle = "缓存已解析的域名，减少重复查询",
                        leadingIcon = Icons.Filled.Storage,
                        onClick = onNavigateToCacheSettings
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.raceModeProviders.title,
                        subtitle = "选择省电、均衡、极速或主备解析策略",
                        leadingIcon = Icons.AutoMirrored.Filled.AltRoute,
                        onClick = onNavigateToRaceModeProviders
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.logRetentionSettings.title,
                        subtitle = "选择 DNS 请求日志的记录范围",
                        leadingIcon = Icons.Filled.History,
                        onClick = onNavigateToLogRetentionSettings
                    )
                }
            }

            item { SettingsGroupTitle("数据管理") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.configTransfer.title,
                        subtitle = "备份或恢复自定义服务与规则订阅",
                        leadingIcon = Icons.Filled.ImportExport,
                        onClick = onNavigateToConfigTransfer
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.ruleManagement.title,
                        subtitle = "添加屏蔽或白名单规则，导入规则订阅",
                        leadingIcon = Icons.AutoMirrored.Filled.Rule,
                        onClick = onNavigateToRuleManagement
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.dataCleanup.title,
                        subtitle = "删除缓存、日志或域名规则",
                        leadingIcon = Icons.Filled.DeleteSweep,
                        onClick = onNavigateToDataCleanup
                    )
                }
            }

            item { SettingsGroupTitle("外观") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.homeProviderVisibility.title,
                        subtitle = "配置首页解析服务列表中显示的协议和服务商",
                        leadingIcon = Icons.Filled.Visibility,
                        onClick = onNavigateToHomeProviderVisibility
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.appearanceSettings.title,
                        subtitle = "设置应用的显示外观",
                        leadingIcon = Icons.Filled.Palette,
                        onClick = onNavigateToAppearanceSettings
                    )
                }
            }

            item { SettingsGroupTitle("运行行为") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.foregroundBackgroundSettings.title,
                        subtitle = "后台隐藏、通知常驻",
                        leadingIcon = Icons.Filled.FlipToBack,
                        onClick = onNavigateToForegroundBackgroundSettings
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "排除应用",
                        subtitle = "指定使用系统 DNS 的应用",
                        leadingIcon = Icons.Filled.Apps,
                        onClick = onNavigateToExcludedApps
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "HTTP(S) 流量过滤",
                        subtitle = "按应用检查 HTTP(S) 请求并应用现有域名规则",
                        leadingIcon = Icons.Filled.Http,
                        onClick = onNavigateToHttpInspection
                    )
                }
            }

            item { SettingsGroupTitle("实验功能") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.experimentalFeatures.title,
                        subtitle = "查看还在开发中的功能",
                        leadingIcon = Icons.Filled.Science,
                        onClick = onNavigateToExperimentalFeatures
                    )
                }
            }

            item { SettingsGroupTitle("关于应用") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = ScreenDestinations.about.title,
                        subtitle = "查看软件说明、作者信息和项目仓库",
                        leadingIcon = Icons.Filled.Info,
                        onClick = onNavigateToAbout
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.sponsor.title,
                        subtitle = "请作者喝杯蜜雪，支持项目持续开发",
                        leadingIcon = Icons.Filled.Favorite,
                        onClick = onNavigateToSponsor
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.sponsorList.title,
                        subtitle = "感谢每一位支持 DNSSR 项目的朋友",
                        leadingIcon = Icons.Filled.WorkspacePremium,
                        onClick = onNavigateToSponsorList
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = ScreenDestinations.coBuilderList.title,
                        subtitle = "感谢提出建议与帮助测试的共建者",
                        leadingIcon = Icons.Filled.Groups,
                        onClick = onNavigateToCoBuilderList
                    )
                }
            }
        }
    }
}
