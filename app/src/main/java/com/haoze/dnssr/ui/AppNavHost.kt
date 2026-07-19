package com.haoze.dnssr.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.Lifecycle
import com.haoze.dnssr.ui.theme.ThemeColorStyle

private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun NavHostController.navigateWhenResumed(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.popWhenResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val LOG_DASHBOARD = "log_dashboard"
    const val LOGS = "logs"
    const val DNS_LOGS = "dns_logs"
    const val DNS_CACHE = "dns_cache"
    const val RACE_STATS = "race_stats"
    const val BOOTSTRAP_STATS = "bootstrap_stats"
    const val SUBSCRIPTION_INTERCEPTION_STATS = "subscription_interception_stats"
    const val PROVIDER_HEALTH = "provider_health"
    const val RULE_MANAGEMENT = "rule_management"
    const val RULE_LIST = "rule_list"
    const val ALLOW_RULE_LIST = "allow_rule_list"
    const val REWRITE_RULE_LIST = "rewrite_rule_list"
    const val BLOCK_RESPONSE_SETTINGS = "block_response_settings"
    const val EXCLUDED_APPS = "excluded_apps"
    const val DATA_CLEANUP = "data_cleanup"
    const val CONFIG_TRANSFER = "config_transfer"
    const val CONFIG_IMPORT_EXPORT = "config_import_export"
    const val RULE_EXPORT = "rule_export"
    const val PROVIDER_MANAGEMENT = "provider_management"
    const val HOME_PROVIDER_VISIBILITY = "home_provider_visibility"
    const val BOOTSTRAP_SETTINGS = "bootstrap_settings"
    const val RACE_MODE_LATENCY = "race_mode_latency"
    const val RACE_MODE_PROVIDERS = "race_mode_providers"
    const val RESOLUTION_SINGLE = "resolution_single"
    const val RESOLUTION_SMART = "resolution_smart"
    const val RESOLUTION_PARALLEL = "resolution_parallel"
    const val RESOLUTION_BACKUP = "resolution_backup"
    const val CACHE_SETTINGS = "cache_settings"
    const val LOG_RETENTION_SETTINGS = "log_retention_settings"
    const val FOREGROUND_BACKGROUND_SETTINGS = "foreground_background_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val DAY_NIGHT_MODE = "day_night_mode"
    const val THEME_COLOR_SETTINGS = "theme_color_settings"
    const val HOME_COMPONENT_OPACITY = "home_component_opacity"
    const val HOME_SENTENCE_SETTINGS = "home_sentence_settings"
    const val NOTIFICATION_TEXT_SETTINGS = "notification_text_settings"
    const val CUSTOM_BACKGROUND_SETTINGS = "custom_background_settings"
    const val SERVICE_LIGHT_EFFECT_SETTINGS = "service_light_effect_settings"
    const val LEGACY_ICON_SETTINGS = "legacy_icon_settings"
    const val LEGACY_LOG_PAGE_SETTINGS = "legacy_log_page_settings"
    const val HTTP_INSPECTION_SETTINGS = "http_inspection_settings"
    const val HTTP_INSPECTION_APPS = "http_inspection_apps"
    const val HTTP_REQUEST_LOGS = "http_request_logs"
    const val SUBSCRIPTION_MANAGEMENT = "subscription_management"
    const val SUBSCRIPTION_AUTO_UPDATE_INTERVAL = "subscription_auto_update_interval"
    const val ABOUT = "about"
    const val SPONSOR = "sponsor"
    const val SPONSOR_LIST = "sponsor_list"
    const val CO_BUILDER_LIST = "co_builder_list"
}

data class ScreenDestination(val route: String, val title: String)

object ScreenDestinations {
    val ruleManagement = ScreenDestination(Routes.RULE_MANAGEMENT, "域名规则")
    val dataCleanup = ScreenDestination(Routes.DATA_CLEANUP, "数据清理")
    val configTransfer = ScreenDestination(Routes.CONFIG_TRANSFER, "导入与导出")
    val configImportExport = ScreenDestination(Routes.CONFIG_IMPORT_EXPORT, "设置配置")
    val ruleExport = ScreenDestination(Routes.RULE_EXPORT, "规则导出")
    val providerManagement = ScreenDestination(Routes.PROVIDER_MANAGEMENT, "服务商管理")
    val homeProviderVisibility = ScreenDestination(Routes.HOME_PROVIDER_VISIBILITY, "服务显示")
    val bootstrapSettings = ScreenDestination(Routes.BOOTSTRAP_SETTINGS, "Bootstrap 设置")
    val raceModeLatency = ScreenDestination(Routes.RACE_MODE_LATENCY, "查询测速")
    val raceModeProviders = ScreenDestination(Routes.RACE_MODE_PROVIDERS, "解析模式")
    val cacheSettings = ScreenDestination(Routes.CACHE_SETTINGS, "缓存设置")
    val logRetentionSettings = ScreenDestination(Routes.LOG_RETENTION_SETTINGS, "日志模式")
    val foregroundBackgroundSettings = ScreenDestination(Routes.FOREGROUND_BACKGROUND_SETTINGS, "前后台行为")
    val appearanceSettings = ScreenDestination(Routes.APPEARANCE_SETTINGS, "外观设置")
    val about = ScreenDestination(Routes.ABOUT, "应用信息")
    val sponsor = ScreenDestination(Routes.SPONSOR, "赞助")
    val sponsorList = ScreenDestination(Routes.SPONSOR_LIST, "赞助者名单")
    val coBuilderList = ScreenDestination(Routes.CO_BUILDER_LIST, "共建者名单")
    val dayNightMode = ScreenDestination(Routes.DAY_NIGHT_MODE, "日夜模式")
    val themeColorSettings = ScreenDestination(Routes.THEME_COLOR_SETTINGS, "主题色配置")
    val homeComponentOpacity = ScreenDestination(Routes.HOME_COMPONENT_OPACITY, "首页透明度")
    val homeSentenceSettings = ScreenDestination(Routes.HOME_SENTENCE_SETTINGS, "首页句子")
    val notificationTextSettings = ScreenDestination(Routes.NOTIFICATION_TEXT_SETTINGS, "通知栏文案")
    val customBackgroundSettings = ScreenDestination(Routes.CUSTOM_BACKGROUND_SETTINGS, "软件背景")
    val serviceLightEffectSettings = ScreenDestination(Routes.SERVICE_LIGHT_EFFECT_SETTINGS, "服务动态光影")
    val legacyIconSettings = ScreenDestination(Routes.LEGACY_ICON_SETTINGS, "旧版图标")
    val legacyLogPageSettings = ScreenDestination(Routes.LEGACY_LOG_PAGE_SETTINGS, "旧版日志页面")
}

@Composable
fun AppNavHost(
    mainScreen: @Composable (
        onNavigateToSettings: () -> Unit,
        onNavigateToLogs: () -> Unit,
        onNavigateToProviderManagement: () -> Unit,
        onNavigateToHomeProviderVisibility: () -> Unit,
        onNavigateToRaceModeSettings: () -> Unit
    ) -> Unit,
    onRuntimeDnsSettingsChanged: () -> Unit,
    onHideFromRecentsChanged: (Boolean) -> Unit = {},
    onThemeModeChanged: (AppThemeMode) -> Unit = {},
    onThemeColorStyleChanged: (ThemeColorStyle) -> Unit = {},
    onCustomBackgroundChanged: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it / 8 },
                animationSpec = tween(NAVIGATION_ENTER_DURATION_MS, easing = emphasizedDecelerate)
            ) + fadeIn(animationSpec = tween(NAVIGATION_ENTER_DURATION_MS))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 24 },
                animationSpec = tween(NAVIGATION_EXIT_DURATION_MS, easing = emphasizedAccelerate)
            ) + scaleOut(
                targetScale = 0.98f,
                animationSpec = tween(NAVIGATION_EXIT_DURATION_MS, easing = emphasizedAccelerate)
            ) + fadeOut(animationSpec = tween(NAVIGATION_EXIT_DURATION_MS))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 24 },
                animationSpec = tween(NAVIGATION_ENTER_DURATION_MS, easing = emphasizedDecelerate)
            ) + scaleIn(
                initialScale = 0.98f,
                animationSpec = tween(NAVIGATION_ENTER_DURATION_MS, easing = emphasizedDecelerate)
            ) + fadeIn(animationSpec = tween(NAVIGATION_ENTER_DURATION_MS))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it / 8 },
                animationSpec = tween(NAVIGATION_EXIT_DURATION_MS, easing = emphasizedAccelerate)
            ) + fadeOut(animationSpec = tween(NAVIGATION_EXIT_DURATION_MS))
        }
    ) {
        composable(Routes.MAIN) {
            val context = LocalContext.current
            mainScreen(
                { navController.navigateWhenResumed(Routes.SETTINGS) },
                {
                    navController.navigateWhenResumed(
                        if (AppSettings.isLegacyLogPageEnabled(context)) {
                            Routes.LOGS
                        } else {
                            Routes.LOG_DASHBOARD
                        }
                    )
                },
                { navController.navigateWhenResumed(ScreenDestinations.providerManagement.route) },
                { navController.navigateWhenResumed(ScreenDestinations.homeProviderVisibility.route) },
                { navController.navigateWhenResumed(ScreenDestinations.raceModeProviders.route) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToRoute = { navController.navigateWhenResumed(it) }
            )
        }
        composable(Routes.LOG_DASHBOARD) {
            ModernLogDashboardScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToDnsLogs = { navController.navigateWhenResumed(Routes.DNS_LOGS) },
                onNavigateToDnsCache = { navController.navigateWhenResumed(Routes.DNS_CACHE) },
                onNavigateToRaceStats = { navController.navigateWhenResumed(Routes.RACE_STATS) },
                onNavigateToBootstrapStats = { navController.navigateWhenResumed(Routes.BOOTSTRAP_STATS) },
                onNavigateToSubscriptionInterceptionStats = {
                    navController.navigateWhenResumed(Routes.SUBSCRIPTION_INTERCEPTION_STATS)
                }
            )
        }
        composable(Routes.LOGS) {
            LogHomeScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToDnsLogs = { navController.navigateWhenResumed(Routes.DNS_LOGS) },
                onNavigateToDnsCache = { navController.navigateWhenResumed(Routes.DNS_CACHE) },
                onNavigateToRaceStats = { navController.navigateWhenResumed(Routes.RACE_STATS) },
                onNavigateToBootstrapStats = { navController.navigateWhenResumed(Routes.BOOTSTRAP_STATS) },
                onNavigateToSubscriptionInterceptionStats = {
                    navController.navigateWhenResumed(Routes.SUBSCRIPTION_INTERCEPTION_STATS)
                }
            )
        }
        composable(Routes.DNS_LOGS) {
            RequestLogScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.DNS_CACHE) {
            DnsCacheScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(Routes.RACE_STATS) {
            RaceStatsScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(Routes.BOOTSTRAP_STATS) {
            BootstrapStatsScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(Routes.SUBSCRIPTION_INTERCEPTION_STATS) {
            SubscriptionInterceptionStatsScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(Routes.PROVIDER_HEALTH) {
            ProviderHealthScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(ScreenDestinations.ruleManagement.route) {
            SettingsGuideHost(SettingsGuides.DOMAIN_RULES) {
                RuleManagementScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.ruleManagement.title,
                onNavigateToRuleList = { navController.navigateWhenResumed(Routes.RULE_LIST) },
                onNavigateToAllowRuleList = { navController.navigateWhenResumed(Routes.ALLOW_RULE_LIST) },
                onNavigateToRewriteRuleList = { navController.navigateWhenResumed(Routes.REWRITE_RULE_LIST) },
                onNavigateToSubscription = { navController.navigateWhenResumed(Routes.SUBSCRIPTION_MANAGEMENT) },
                onNavigateToAutoUpdateInterval = {
                    navController.navigateWhenResumed(Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL)
                },
                onNavigateToBlockResponseSettings = {
                    navController.navigateWhenResumed(Routes.BLOCK_RESPONSE_SETTINGS)
                },
                    onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
                )
            }
        }
        composable(Routes.EXCLUDED_APPS) {
            SettingsGuideHost(SettingsGuides.EXCLUDED_APPS) {
                ExcludedAppsScreen(onBack = { navController.popWhenResumed() })
            }
        }
        composable(Routes.BLOCK_RESPONSE_SETTINGS) {
            BlockResponseSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.RULE_LIST) {
            RuleListScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(ScreenDestinations.dataCleanup.route) {
            SettingsGuideHost(SettingsGuides.DATA_CLEANUP) {
                DataCleanupScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.dataCleanup.title,
                    onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
                )
            }
        }
        composable(ScreenDestinations.configTransfer.route) {
            SettingsGuideHost(SettingsGuides.CONFIG_TRANSFER) {
                ConfigTransferScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.configTransfer.title,
                onNavigateToConfigImportExport = {
                    navController.navigateWhenResumed(ScreenDestinations.configImportExport.route)
                },
                    onNavigateToRuleExport = {
                        navController.navigateWhenResumed(ScreenDestinations.ruleExport.route)
                    }
                )
            }
        }
        composable(ScreenDestinations.configImportExport.route) {
            ConfigImportExportScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.configImportExport.title
            )
        }
        composable(ScreenDestinations.ruleExport.route) {
            RuleExportScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.ruleExport.title
            )
        }
        composable(ScreenDestinations.providerManagement.route) {
            SettingsGuideHost(SettingsGuides.PROVIDER_MANAGEMENT) {
                ProviderManagementScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.providerManagement.title
                )
            }
        }
        composable(ScreenDestinations.homeProviderVisibility.route) {
            SettingsGuideHost(SettingsGuides.SERVICE_DISPLAY) {
                HomeProviderVisibilityScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.homeProviderVisibility.title
                )
            }
        }
        composable(Routes.ALLOW_RULE_LIST) {
            RuleListScreen(
                onBack = { navController.popWhenResumed() },
                ruleKind = ManagedRuleKind.ALLOW,
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.REWRITE_RULE_LIST) {
            RuleListScreen(onBack = { navController.popWhenResumed() }, ruleKind = ManagedRuleKind.REWRITE, onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged)
        }
        composable(ScreenDestinations.bootstrapSettings.route) {
            SettingsGuideHost(SettingsGuides.BOOTSTRAP) {
                BootstrapSettingsScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.bootstrapSettings.title
                )
            }
        }
        composable(ScreenDestinations.raceModeLatency.route) {
            SettingsGuideHost(SettingsGuides.LATENCY_TEST) {
                RaceModeLatencySettingsScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.raceModeLatency.title
                )
            }
        }
        composable(ScreenDestinations.raceModeProviders.route) {
            SettingsGuideHost(SettingsGuides.RESOLUTION_MODE) {
                ResolutionModeHomeScreen(
                onBack = { navController.popWhenResumed() },
                    onOpenMode = { mode -> navController.navigateWhenResumed(mode.route) }
                )
            }
        }
        composable(Routes.RESOLUTION_SINGLE) { ResolutionModeConfigScreen(DnsResolutionMode.SINGLE, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_SMART) { ResolutionModeConfigScreen(DnsResolutionMode.SMART_PREDICTION, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_PARALLEL) { ResolutionModeConfigScreen(DnsResolutionMode.PARALLEL_RACE, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_BACKUP) { ResolutionModeConfigScreen(DnsResolutionMode.PRIMARY_BACKUP, { navController.popWhenResumed() }) }
        composable(ScreenDestinations.cacheSettings.route) {
            SettingsGuideHost(SettingsGuides.CACHE) {
                CacheSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.cacheSettings.title,
                    onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
                )
            }
        }
        composable(ScreenDestinations.logRetentionSettings.route) {
            SettingsGuideHost(SettingsGuides.LOG_MODE) {
                LogRetentionSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged,
                    title = ScreenDestinations.logRetentionSettings.title
                )
            }
        }
        composable(ScreenDestinations.foregroundBackgroundSettings.route) {
            SettingsGuideHost(SettingsGuides.FOREGROUND_BACKGROUND) {
                ForegroundBackgroundSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.foregroundBackgroundSettings.title,
                    onHideFromRecentsChanged = onHideFromRecentsChanged
                )
            }
        }
        composable(Routes.HTTP_INSPECTION_SETTINGS) {
            HttpInspectionSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToRequestLogs = { navController.navigateWhenResumed(Routes.HTTP_REQUEST_LOGS) },
                onNavigateToApps = { navController.navigateWhenResumed(Routes.HTTP_INSPECTION_APPS) }
            )
        }
        composable(Routes.HTTP_INSPECTION_APPS) {
            HttpInspectionAppsScreen(onBack = { navController.popWhenResumed() })
        }
        composable(Routes.HTTP_REQUEST_LOGS) {
            HttpRequestLogScreen(onBack = { navController.popWhenResumed() })
        }
        composable(Routes.SUBSCRIPTION_MANAGEMENT) {
            SubscriptionScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL) {
            SubscriptionAutoUpdateIntervalScreen(
                onBack = { navController.popWhenResumed() }
            )
        }
        composable(ScreenDestinations.about.route) {
            SettingsGuideHost(SettingsGuides.ABOUT) {
                AboutScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.about.title
                )
            }
        }
        composable(ScreenDestinations.sponsor.route) {
            SettingsGuideHost(SettingsGuides.SPONSOR) {
                SponsorScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.sponsor.title
                )
            }
        }
        composable(ScreenDestinations.appearanceSettings.route) {
            SettingsGuideHost(SettingsGuides.APPEARANCE) {
                AppearanceSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.appearanceSettings.title,
                onNavigateToDayNightMode = { navController.navigateWhenResumed(ScreenDestinations.dayNightMode.route) },
                onNavigateToThemeColorSettings = { navController.navigateWhenResumed(ScreenDestinations.themeColorSettings.route) },
                onNavigateToHomeComponentOpacity = { navController.navigateWhenResumed(ScreenDestinations.homeComponentOpacity.route) },
                onNavigateToHomeSentence = { navController.navigateWhenResumed(ScreenDestinations.homeSentenceSettings.route) },
                onNavigateToNotificationText = { navController.navigateWhenResumed(ScreenDestinations.notificationTextSettings.route) },
                onNavigateToCustomBackground = { navController.navigateWhenResumed(ScreenDestinations.customBackgroundSettings.route) },
                onNavigateToServiceLightEffect = { navController.navigateWhenResumed(ScreenDestinations.serviceLightEffectSettings.route) },
                onNavigateToLegacyIcon = { navController.navigateWhenResumed(ScreenDestinations.legacyIconSettings.route) },
                onNavigateToLegacyLogPage = { navController.navigateWhenResumed(ScreenDestinations.legacyLogPageSettings.route) }
                )
            }
        }
        composable(ScreenDestinations.dayNightMode.route) {
            DayNightModeScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.dayNightMode.title,
                onThemeModeChanged = onThemeModeChanged
            )
        }
        composable(ScreenDestinations.themeColorSettings.route) {
            ThemeColorSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.themeColorSettings.title,
                onThemeColorStyleChanged = onThemeColorStyleChanged
            )
        }
        composable(ScreenDestinations.homeComponentOpacity.route) {
            HomeComponentOpacityScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.homeComponentOpacity.title
            )
        }
        composable(ScreenDestinations.homeSentenceSettings.route) {
            HomeSentenceSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.homeSentenceSettings.title
            )
        }
        composable(ScreenDestinations.notificationTextSettings.route) {
            NotificationTextSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.notificationTextSettings.title
            )
        }
        composable(ScreenDestinations.customBackgroundSettings.route) {
            CustomBackgroundSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.customBackgroundSettings.title,
                onBackgroundChanged = onCustomBackgroundChanged
            )
        }
        composable(ScreenDestinations.serviceLightEffectSettings.route) {
            ServiceLightEffectSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.serviceLightEffectSettings.title
            )
        }
        composable(ScreenDestinations.legacyIconSettings.route) {
            LegacyIconSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.legacyIconSettings.title
            )
        }
        composable(ScreenDestinations.legacyLogPageSettings.route) {
            LegacyLogPageSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = ScreenDestinations.legacyLogPageSettings.title
            )
        }
        composable(ScreenDestinations.sponsorList.route) {
            SettingsGuideHost(SettingsGuides.SPONSOR_LIST) {
                SponsorListScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.sponsorList.title
                )
            }
        }
        composable(ScreenDestinations.coBuilderList.route) {
            SettingsGuideHost(SettingsGuides.CO_BUILDER_LIST) {
                CoBuilderListScreen(
                onBack = { navController.popWhenResumed() },
                    title = ScreenDestinations.coBuilderList.title
                )
            }
        }
    }
}

private val DnsResolutionMode.route: String
    get() = when (this) {
        DnsResolutionMode.SINGLE -> Routes.RESOLUTION_SINGLE
        DnsResolutionMode.SMART_PREDICTION -> Routes.RESOLUTION_SMART
        DnsResolutionMode.PARALLEL_RACE -> Routes.RESOLUTION_PARALLEL
        DnsResolutionMode.PRIMARY_BACKUP -> Routes.RESOLUTION_BACKUP
    }
