package com.haoze.dnssr.ui

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val NAV_ANIM_DURATION = 300
private const val SCREEN_TITLE_ARG = "screenTitle"
private val navigationSlideSpec = tween<IntOffset>(
    durationMillis = NAV_ANIM_DURATION,
    easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
)

private fun titledRoute(route: String) = "$route?$SCREEN_TITLE_ARG={$SCREEN_TITLE_ARG}"

private fun NavHostController.navigateToTitledRoute(route: String, title: String) {
    navigate("$route?$SCREEN_TITLE_ARG=${Uri.encode(title)}")
}

private fun screenTitleArgument(defaultTitle: String) = navArgument(SCREEN_TITLE_ARG) {
    type = NavType.StringType
    defaultValue = defaultTitle
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
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
    const val DATA_CLEANUP = "data_cleanup"
    const val CONFIG_TRANSFER = "config_transfer"
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
    const val EXPERIMENTAL_FEATURES = "experimental_features"
    const val SUBSCRIPTION_MANAGEMENT = "subscription_management"
    const val SUBSCRIPTION_AUTO_UPDATE_INTERVAL = "subscription_auto_update_interval"
    const val ABOUT = "about"
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
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.MAIN,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = navigationSlideSpec
            )
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = navigationSlideSpec
            )
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = navigationSlideSpec
            )
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = navigationSlideSpec
            )
        }
    ) {
        composable(Routes.MAIN) {
            mainScreen(
                { navController.navigate(Routes.SETTINGS) },
                { navController.navigate(Routes.LOGS) },
                { navController.navigate(Routes.PROVIDER_MANAGEMENT) },
                { navController.navigateToTitledRoute(Routes.HOME_PROVIDER_VISIBILITY, "服务显示") },
                { navController.navigate(Routes.RACE_MODE_PROVIDERS) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
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
                onNavigateToExperimentalFeatures = { title -> navController.navigateToTitledRoute(Routes.EXPERIMENTAL_FEATURES, title) },
                onNavigateToAbout = { title -> navController.navigateToTitledRoute(Routes.ABOUT, title) }
            )
        }
        composable(Routes.LOGS) {
            LogHomeScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDnsLogs = { navController.navigate(Routes.DNS_LOGS) },
                onNavigateToDnsCache = { navController.navigate(Routes.DNS_CACHE) },
                onNavigateToRaceStats = { navController.navigate(Routes.RACE_STATS) },
                onNavigateToBootstrapStats = { navController.navigate(Routes.BOOTSTRAP_STATS) },
                onNavigateToSubscriptionInterceptionStats = {
                    navController.navigate(Routes.SUBSCRIPTION_INTERCEPTION_STATS)
                }
            )
        }
        composable(Routes.DNS_LOGS) {
            LogScreen(
                onBack = { navController.popBackStack() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.DNS_CACHE) {
            DnsCacheScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.RACE_STATS) {
            RaceStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.BOOTSTRAP_STATS) {
            BootstrapStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SUBSCRIPTION_INTERCEPTION_STATS) {
            SubscriptionInterceptionStatsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PROVIDER_HEALTH) {
            ProviderHealthScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(titledRoute(Routes.RULE_MANAGEMENT), arguments = listOf(screenTitleArgument("域名规则"))) { entry ->
            RuleManagementScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "域名规则",
                onNavigateToRuleList = { navController.navigate(Routes.RULE_LIST) },
                onNavigateToAllowRuleList = { navController.navigate(Routes.ALLOW_RULE_LIST) },
                onNavigateToSubscription = { navController.navigate(Routes.SUBSCRIPTION_MANAGEMENT) },
                onNavigateToAutoUpdateInterval = {
                    navController.navigate(Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL)
                },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.RULE_LIST) {
            RuleListScreen(
                onBack = { navController.popBackStack() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.DATA_CLEANUP), arguments = listOf(screenTitleArgument("数据清理"))) { entry ->
            DataCleanupScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "数据清理",
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.CONFIG_TRANSFER), arguments = listOf(screenTitleArgument("导入与导出"))) { entry ->
            ConfigTransferScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "导入与导出"
            )
        }
        composable(titledRoute(Routes.PROVIDER_MANAGEMENT), arguments = listOf(screenTitleArgument("服务商管理"))) { entry ->
            ProviderManagementScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "服务商管理"
            )
        }
        composable(titledRoute(Routes.HOME_PROVIDER_VISIBILITY), arguments = listOf(screenTitleArgument(""))) { entry ->
            HomeProviderVisibilityScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG).orEmpty()
            )
        }
        composable(Routes.ALLOW_RULE_LIST) {
            RuleListScreen(
                onBack = { navController.popBackStack() },
                ruleKind = ManagedRuleKind.ALLOW,
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.BOOTSTRAP_SETTINGS), arguments = listOf(screenTitleArgument("Bootstrap 设置"))) { entry ->
            BootstrapSettingsScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "Bootstrap 设置"
            )
        }
        composable(titledRoute(Routes.RACE_MODE_LATENCY), arguments = listOf(screenTitleArgument("查询测速"))) { entry ->
            RaceModeLatencySettingsScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "查询测速"
            )
        }
        composable(titledRoute(Routes.RACE_MODE_PROVIDERS), arguments = listOf(screenTitleArgument("解析模式"))) { entry ->
            ResolutionModeHomeScreen(
                onBack = { navController.popBackStack() },
                onOpenMode = { mode -> navController.navigate(mode.route) }
            )
        }
        composable(Routes.RESOLUTION_SINGLE) { ResolutionModeConfigScreen(DnsResolutionMode.SINGLE, { navController.popBackStack() }) }
        composable(Routes.RESOLUTION_SMART) { ResolutionModeConfigScreen(DnsResolutionMode.SMART_PREDICTION, { navController.popBackStack() }) }
        composable(Routes.RESOLUTION_PARALLEL) { ResolutionModeConfigScreen(DnsResolutionMode.PARALLEL_RACE, { navController.popBackStack() }) }
        composable(Routes.RESOLUTION_BACKUP) { ResolutionModeConfigScreen(DnsResolutionMode.PRIMARY_BACKUP, { navController.popBackStack() }) }
        composable(titledRoute(Routes.CACHE_SETTINGS), arguments = listOf(screenTitleArgument("DNS 缓存设置"))) { entry ->
            CacheSettingsScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "DNS 缓存设置",
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(titledRoute(Routes.LOG_RETENTION_SETTINGS), arguments = listOf(screenTitleArgument("日志保留"))) { entry ->
            LogRetentionSettingsScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "日志保留"
            )
        }
        composable(titledRoute(Routes.FOREGROUND_BACKGROUND_SETTINGS), arguments = listOf(screenTitleArgument("前后台行为"))) { entry ->
            ForegroundBackgroundSettingsScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "前后台行为",
                onHideFromRecentsChanged = onHideFromRecentsChanged
            )
        }
        composable(titledRoute(Routes.EXPERIMENTAL_FEATURES), arguments = listOf(screenTitleArgument("实验功能"))) { entry ->
            ExperimentalFeaturesScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "实验功能"
            )
        }
        composable(Routes.SUBSCRIPTION_MANAGEMENT) {
            SubscriptionScreen(
                onBack = { navController.popBackStack() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL) {
            SubscriptionAutoUpdateIntervalScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(titledRoute(Routes.ABOUT), arguments = listOf(screenTitleArgument("DNSSR 应用信息"))) { entry ->
            AboutScreen(
                onBack = { navController.popBackStack() },
                title = entry.arguments?.getString(SCREEN_TITLE_ARG) ?: "DNSSR 应用信息"
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
