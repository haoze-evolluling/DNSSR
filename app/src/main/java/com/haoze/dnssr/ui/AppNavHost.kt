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
    const val BLOCKED_APPS = "blocked_apps"
    const val BLOCKED_APPS_SELECTION = "blocked_apps_selection"
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
    const val CA_CERTIFICATE_GUIDE = "ca_certificate_guide"
    const val HTTP_REQUEST_LOGS = "http_request_logs"
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
                onNavigateToRuleList = { navController.navigateWhenResumed(ScreenDestinations.ruleList.route) },
                onNavigateToAllowRuleList = { navController.navigateWhenResumed(ScreenDestinations.allowRuleList.route) },
                onNavigateToRewriteRuleList = { navController.navigateWhenResumed(ScreenDestinations.rewriteRuleList.route) },
                onNavigateToSubscription = { navController.navigateWhenResumed(ScreenDestinations.subscriptionManagement.route) },
                onNavigateToAutoUpdateInterval = {
                    navController.navigateWhenResumed(ScreenDestinations.subscriptionAutoUpdate.route)
                },
                onNavigateToBlockResponseSettings = {
                    navController.navigateWhenResumed(ScreenDestinations.blockResponseSettings.route)
                },
                    onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
                )
            }
        }
        composable(ScreenDestinations.excludedApps.route) {
            SettingsGuideHost(SettingsGuides.EXCLUDED_APPS) {
                ExcludedAppsScreen(onBack = { navController.popWhenResumed() })
            }
        }
        composable(ScreenDestinations.blockResponseSettings.route) {
            BlockResponseSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(ScreenDestinations.ruleList.route) {
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
        composable(ScreenDestinations.allowRuleList.route) {
            RuleListScreen(
                onBack = { navController.popWhenResumed() },
                ruleKind = ManagedRuleKind.ALLOW,
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(ScreenDestinations.blockedApps.route) {
            BlockedAppsSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onSelectApps = { navController.navigateWhenResumed(Routes.BLOCKED_APPS_SELECTION) }
            )
        }
        composable(Routes.BLOCKED_APPS_SELECTION) {
            BlockedAppsScreen(onBack = { navController.popWhenResumed() })
        }
        composable(ScreenDestinations.rewriteRuleList.route) {
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
        composable(ScreenDestinations.resolutionSingle.route) { ResolutionModeConfigScreen(DnsResolutionMode.SINGLE, { navController.popWhenResumed() }) }
        composable(ScreenDestinations.resolutionSmart.route) { ResolutionModeConfigScreen(DnsResolutionMode.SMART_PREDICTION, { navController.popWhenResumed() }) }
        composable(ScreenDestinations.resolutionParallel.route) { ResolutionModeConfigScreen(DnsResolutionMode.PARALLEL_RACE, { navController.popWhenResumed() }) }
        composable(ScreenDestinations.resolutionBackup.route) { ResolutionModeConfigScreen(DnsResolutionMode.PRIMARY_BACKUP, { navController.popWhenResumed() }) }
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
        composable(ScreenDestinations.httpInspectionSettings.route) {
            HttpInspectionSettingsScreen(
                onBack = { navController.popWhenResumed() },
                onNavigateToRequestLogs = { navController.navigateWhenResumed(ScreenDestinations.httpRequestLogs.route) },
                onNavigateToApps = { navController.navigateWhenResumed(ScreenDestinations.httpInspectionApps.route) },
                onNavigateToCaGuide = { navController.navigateWhenResumed(ScreenDestinations.caCertificateGuide.route) }
            )
        }
        composable(ScreenDestinations.caCertificateGuide.route) {
            CaCertificateGuideScreen(onBack = { navController.popWhenResumed() })
        }
        composable(ScreenDestinations.httpInspectionApps.route) {
            HttpInspectionAppsScreen(onBack = { navController.popWhenResumed() })
        }
        composable(ScreenDestinations.httpRequestLogs.route) {
            HttpRequestLogScreen(onBack = { navController.popWhenResumed() })
        }
        composable(ScreenDestinations.subscriptionManagement.route) {
            SubscriptionScreen(
                onBack = { navController.popWhenResumed() },
                onRuntimeDnsSettingsChanged = onRuntimeDnsSettingsChanged
            )
        }
        composable(ScreenDestinations.subscriptionAutoUpdate.route) {
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
