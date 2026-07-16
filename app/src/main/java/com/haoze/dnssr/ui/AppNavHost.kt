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
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.Lifecycle
import com.haoze.dnssr.ui.theme.ThemeColorStyle

private const val SCREEN_TITLE_ARG = "screenTitle"
private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private fun titledRoute(route: String) = "$route?$SCREEN_TITLE_ARG={$SCREEN_TITLE_ARG}"

private fun NavHostController.navigateToTitledRoute(route: String, title: String) {
    navigateWhenResumed("$route?$SCREEN_TITLE_ARG=${Uri.encode(title)}")
}

private fun NavHostController.navigateWhenResumed(route: String) {
    if (currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.popWhenResumed() {
    if (currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) popBackStack()
}

private fun screenTitleArgument(defaultTitle: String) = navArgument(SCREEN_TITLE_ARG) {
    type = NavType.StringType
    defaultValue = defaultTitle
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
    const val BLOCK_RESPONSE_SETTINGS = "block_response_settings"
    const val DATA_CLEANUP = "data_cleanup"
    const val CONFIG_TRANSFER = "config_transfer"
    const val CONFIG_IMPORT_EXPORT = "config_import_export"
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
    const val CUSTOM_BACKGROUND_SETTINGS = "custom_background_settings"
    const val SERVICE_LIGHT_EFFECT_SETTINGS = "service_light_effect_settings"
    const val EXPERIMENTAL_FEATURES = "experimental_features"
    const val DOH3_SERVICE = "doh3_service"
    const val SUBSCRIPTION_MANAGEMENT = "subscription_management"
    const val SUBSCRIPTION_AUTO_UPDATE_INTERVAL = "subscription_auto_update_interval"
    const val ABOUT = "about"
    const val SPONSOR = "sponsor"
    const val SPONSOR_LIST = "sponsor_list"
    const val CO_BUILDER_LIST = "co_builder_list"
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
                { navController.navigateWhenResumed(Routes.PROVIDER_MANAGEMENT) },
                { navController.navigateToTitledRoute(Routes.HOME_PROVIDER_VISIBILITY, "服务显示") },
                { navController.navigateWhenResumed(Routes.RACE_MODE_PROVIDERS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToRuleManagement = { title -> navController.navigateToTitledRoute(Routes.RULE_MANAGEMENT, title) },
                onNavigateToDataCleanup = { title -> navController.navigateToTitledRoute(Routes.DATA_CLEANUP, title) },
                onNavigateToConfigTransfer = { title -> navController.navigateToTitledRoute(Routes.CONFIG_TRANSFER, title) },
                onNavigateToProviderManagement = { title -> navController.navigateToTitledRoute(Routes.PROVIDER_MANAGEMENT, title) },
                onNavigateToHomeProviderVisibility = { title -> navController.navigateToTitledRoute(Routes.HOME_PROVIDER_VISIBILITY, title) },
                onNavigateToRaceModeLatency = { title -> navController.navigateToTitledRoute(Routes.RACE_MODE_LATENCY, title) },
                onNavigateToRaceModeProviders = { title -> navController.navigateToTitledRoute(Routes.RACE_MODE_PROVIDERS, title) },
                onNavigateToCacheSettings = { title -> navController.navigateToTitledRoute(Routes.CACHE_SETTINGS, title) },
                onNavigateToBootstrapSettings = { title -> navController.navigateToTitledRoute(Routes.BOOTSTRAP_SETTINGS, title) },
                onNavigateToLogRetentionSettings = { title -> navController.navigateToTitledRoute(Routes.LOG_RETENTION_SETTINGS, title) },
                onNavigateToForegroundBackgroundSettings = { title ->
                    navController.navigateToTitledRoute(Routes.FOREGROUND_BACKGROUND_SETTINGS, title)
                },
                onNavigateToAppearanceSettings = { title ->
                    navController.navigateToTitledRoute(Routes.APPEARANCE_SETTINGS, title)
                },
                onNavigateToExperimentalFeatures = { title -> navController.navigateToTitledRoute(Routes.EXPERIMENTAL_FEATURES, title) },
                onNavigateToAbout = { title -> navController.navigateToTitledRoute(Routes.ABOUT, title) },
                onNavigateToSponsor = { title -> navController.navigateToTitledRoute(Routes.SPONSOR, title) },
                onNavigateToSponsorList = { title -> navController.navigateToTitledRoute(Routes.SPONSOR_LIST, title) },
                onNavigateToCoBuilderList = { title -> navController.navigateToTitledRoute(Routes.CO_BUILDER_LIST, title) }
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
            LogScreen(
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
        composable(titledRoute(Routes.RULE_MANAGEMENT), arguments = listOf(screenTitleArgument("域名规则"))) { entry ->
            RuleManagementScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "域名规则",
                onNavigateToRuleList = { navController.navigateWhenResumed(Routes.RULE_LIST) },
                onNavigateToAllowRuleList = { navController.navigateWhenResumed(Routes.ALLOW_RULE_LIST) },
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
        composable(titledRoute(Routes.DATA_CLEANUP), arguments = listOf(screenTitleArgument("数据清理"))) { entry ->
            DataCleanupScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "数据清理",
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.CONFIG_TRANSFER), arguments = listOf(screenTitleArgument("导入与导出"))) { entry ->
            ConfigTransferScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "导入与导出",
                onNavigateToConfigImportExport = {
                    navController.navigateToTitledRoute(Routes.CONFIG_IMPORT_EXPORT, "配置导入与导出")
                }
            )
        }
        composable(titledRoute(Routes.CONFIG_IMPORT_EXPORT), arguments = listOf(screenTitleArgument("配置导入与导出"))) { entry ->
            ConfigImportExportScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "配置导入与导出"
            )
        }
        composable(titledRoute(Routes.PROVIDER_MANAGEMENT), arguments = listOf(screenTitleArgument("服务商管理"))) { entry ->
            ProviderManagementScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "服务商管理"
            )
        }
        composable(titledRoute(Routes.HOME_PROVIDER_VISIBILITY), arguments = listOf(screenTitleArgument(""))) { entry ->
            HomeProviderVisibilityScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG).orEmpty()
            )
        }
        composable(Routes.ALLOW_RULE_LIST) {
            RuleListScreen(
                onBack = { navController.popWhenResumed() },
                ruleKind = ManagedRuleKind.ALLOW,
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.BOOTSTRAP_SETTINGS), arguments = listOf(screenTitleArgument("Bootstrap 设置"))) { entry ->
            BootstrapSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "Bootstrap 设置"
            )
        }
        composable(titledRoute(Routes.RACE_MODE_LATENCY), arguments = listOf(screenTitleArgument("查询测速"))) { entry ->
            RaceModeLatencySettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "查询测速"
            )
        }
        composable(titledRoute(Routes.RACE_MODE_PROVIDERS), arguments = listOf(screenTitleArgument("解析模式"))) { entry ->
            ResolutionModeHomeScreen(
                onBack = { navController.popWhenResumed() },
                onOpenMode = { mode -> navController.navigateWhenResumed(mode.route) }
            )
        }
        composable(Routes.RESOLUTION_SINGLE) { ResolutionModeConfigScreen(DnsResolutionMode.SINGLE, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_SMART) { ResolutionModeConfigScreen(DnsResolutionMode.SMART_PREDICTION, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_PARALLEL) { ResolutionModeConfigScreen(DnsResolutionMode.PARALLEL_RACE, { navController.popWhenResumed() }) }
        composable(Routes.RESOLUTION_BACKUP) { ResolutionModeConfigScreen(DnsResolutionMode.PRIMARY_BACKUP, { navController.popWhenResumed() }) }
        composable(titledRoute(Routes.CACHE_SETTINGS), arguments = listOf(screenTitleArgument("缓存设置"))) { entry ->
            CacheSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "缓存设置",
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.LOG_RETENTION_SETTINGS), arguments = listOf(screenTitleArgument("日志保留"))) { entry ->
            LogRetentionSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "日志保留"
            )
        }
        composable(titledRoute(Routes.FOREGROUND_BACKGROUND_SETTINGS), arguments = listOf(screenTitleArgument("前后台行为"))) { entry ->
            ForegroundBackgroundSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "前后台行为",
                onHideFromRecentsChanged = onHideFromRecentsChanged
            )
        }
        composable(titledRoute(Routes.EXPERIMENTAL_FEATURES), arguments = listOf(screenTitleArgument("实验功能"))) { entry ->
            ExperimentalFeaturesScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToDoh3Service = { navController.navigateWhenResumed(Routes.DOH3_SERVICE) },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "实验功能"
            )
        }
        composable(Routes.DOH3_SERVICE) {
            Doh3ServiceScreen(onBack = { navController.popWhenResumed() })
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
        composable(titledRoute(Routes.ABOUT), arguments = listOf(screenTitleArgument("应用信息"))) { entry ->
            AboutScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "应用信息"
            )
        }
        composable(titledRoute(Routes.SPONSOR), arguments = listOf(screenTitleArgument("赞助"))) { entry ->
            SponsorScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "赞助"
            )
        }
        composable(titledRoute(Routes.APPEARANCE_SETTINGS), arguments = listOf(screenTitleArgument("外观设置"))) { entry ->
            AppearanceSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "外观设置",
                onNavigateToDayNightMode = { title ->
                    navController.navigateToTitledRoute(Routes.DAY_NIGHT_MODE, title)
                },
                onNavigateToThemeColorSettings = { title ->
                    navController.navigateToTitledRoute(Routes.THEME_COLOR_SETTINGS, title)
                },
                onNavigateToHomeComponentOpacity = { title ->
                    navController.navigateToTitledRoute(Routes.HOME_COMPONENT_OPACITY, title)
                },
                onNavigateToHomeSentence = { title ->
                    navController.navigateToTitledRoute(Routes.HOME_SENTENCE_SETTINGS, title)
                },
                onNavigateToCustomBackground = { title ->
                    navController.navigateToTitledRoute(Routes.CUSTOM_BACKGROUND_SETTINGS, title)
                },
                onNavigateToServiceLightEffect = { title ->
                    navController.navigateToTitledRoute(Routes.SERVICE_LIGHT_EFFECT_SETTINGS, title)
                }
            )
        }
        composable(titledRoute(Routes.DAY_NIGHT_MODE), arguments = listOf(screenTitleArgument("日夜模式"))) { entry ->
            DayNightModeScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "日夜模式",
                onThemeModeChanged = onThemeModeChanged
            )
        }
        composable(titledRoute(Routes.THEME_COLOR_SETTINGS), arguments = listOf(screenTitleArgument("主题色配置"))) { entry ->
            ThemeColorSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "主题色配置",
                onThemeColorStyleChanged = onThemeColorStyleChanged
            )
        }
        composable(titledRoute(Routes.HOME_COMPONENT_OPACITY), arguments = listOf(screenTitleArgument("首页透明度"))) { entry ->
            HomeComponentOpacityScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "首页透明度"
            )
        }
        composable(titledRoute(Routes.HOME_SENTENCE_SETTINGS), arguments = listOf(screenTitleArgument("首页句子"))) { entry ->
            HomeSentenceSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "首页句子"
            )
        }
        composable(titledRoute(Routes.CUSTOM_BACKGROUND_SETTINGS), arguments = listOf(screenTitleArgument("软件背景"))) { entry ->
            CustomBackgroundSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "软件背景",
                onBackgroundChanged = onCustomBackgroundChanged
            )
        }
        composable(titledRoute(Routes.SERVICE_LIGHT_EFFECT_SETTINGS), arguments = listOf(screenTitleArgument("服务动态光影"))) { entry ->
            ServiceLightEffectSettingsScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "服务动态光影"
            )
        }
        composable(titledRoute(Routes.SPONSOR_LIST), arguments = listOf(screenTitleArgument("赞助者名单"))) { entry ->
            SponsorListScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "赞助者名单"
            )
        }
        composable(titledRoute(Routes.CO_BUILDER_LIST), arguments = listOf(screenTitleArgument("共建者名单"))) { entry ->
            CoBuilderListScreen(
                onBack = { navController.popWhenResumed() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "共建者名单"
            )
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
