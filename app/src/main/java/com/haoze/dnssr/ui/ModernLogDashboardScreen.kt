package com.haoze.dnssr.ui

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.json.JSONObject
import java.util.Locale

private object DashboardWebViewCache {
    var webView: WebView? = null
    var pageReady: Boolean = false
}

@SuppressLint("SetJavaScriptEnabled")
internal fun preloadLogDashboard(context: Context) {
    if (DashboardWebViewCache.webView != null) return
    DashboardWebViewCache.webView = WebView(context.applicationContext).apply {
        configureLocalPageSettings()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                DashboardWebViewCache.pageReady = true
            }
        }
        loadUrl(DASHBOARD_ASSET_URL)
    }
}

@Composable
fun ModernLogDashboardScreen(
    onBack: () -> Unit,
    onNavigateToDnsLogs: () -> Unit,
    onNavigateToDnsCache: () -> Unit,
    onNavigateToRaceStats: () -> Unit,
    onNavigateToBootstrapStats: () -> Unit,
    onNavigateToSubscriptionInterceptionStats: () -> Unit,
    viewModel: ModernLogDashboardViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboardTheme = rememberDashboardThemeColors()
    val cachedWebView = DashboardWebViewCache.webView
    var webView by remember { mutableStateOf(cachedWebView) }
    var pageReady by remember { mutableStateOf(DashboardWebViewCache.pageReady) }
    var webViewAttached by rememberSaveable { mutableStateOf(false) }
    val hasDashboardData = uiState.dashboardJson != EMPTY_DASHBOARD_JSON
    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }
    NavigationSettledEffect {
        webViewAttached = true
    }

    LaunchedEffect(pageReady, uiState.dashboardJson, dashboardTheme) {
        val view = webView
        if (pageReady && view != null) {
            view.post {
                view.evaluateJavascript(
                    "window.DNSSR && window.DNSSR.setTheme(${dashboardTheme.toJson()});",
                    null
                )
                if (hasDashboardData) {
                    view.evaluateJavascript(
                        "window.DNSSR && window.DNSSR.render(${uiState.dashboardJson});",
                        null
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dashboardTheme.background)
    ) {
        if (webViewAttached) DashboardWebView(
            cachedWebView = cachedWebView,
            themeColors = dashboardTheme,
            onPageReady = {
                DashboardWebViewCache.pageReady = true
                pageReady = true
            },
            onWebViewCreated = {
                webView = it
                DashboardWebViewCache.webView = it
            },
            onBack = onBack,
            onRefresh = viewModel::refresh,
            onNavigateToDnsLogs = onNavigateToDnsLogs,
            onNavigateToDnsCache = onNavigateToDnsCache,
            onNavigateToRaceStats = onNavigateToRaceStats,
            onNavigateToBootstrapStats = onNavigateToBootstrapStats,
            onNavigateToSubscriptionInterceptionStats = onNavigateToSubscriptionInterceptionStats,
            modifier = Modifier.fillMaxSize()
        )
        if (!webViewAttached || !pageReady || (uiState.loading && !hasDashboardData)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dashboardTheme.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DashboardWebView(
    cachedWebView: WebView?,
    themeColors: DashboardThemeColors,
    onPageReady: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToDnsLogs: () -> Unit,
    onNavigateToDnsCache: () -> Unit,
    onNavigateToRaceStats: () -> Unit,
    onNavigateToBootstrapStats: () -> Unit,
    onNavigateToSubscriptionInterceptionStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            return handleDashboardUri(
                request.url,
                onBack,
                onRefresh,
                onNavigateToDnsLogs,
                onNavigateToDnsCache,
                onNavigateToRaceStats,
                onNavigateToBootstrapStats,
                onNavigateToSubscriptionInterceptionStats
            )
        }

        override fun onPageFinished(view: WebView, url: String?) {
            onPageReady()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val view = cachedWebView ?: WebView(context.applicationContext).apply {
                configureLocalPageSettings()
            }
            view.webViewClient = webViewClient
            view.setBackgroundColor(themeColors.background.toArgb())
            if (cachedWebView == null) {
                view.loadUrl(DASHBOARD_ASSET_URL)
            }
            onWebViewCreated(view)
            view
        },
        update = { view ->
            view.webViewClient = webViewClient
            view.setBackgroundColor(themeColors.background.toArgb())
            onWebViewCreated(view)
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureLocalPageSettings() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = true
        allowContentAccess = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            forceDark = WebSettings.FORCE_DARK_OFF
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isAlgorithmicDarkeningAllowed = false
        }
    }
}

@Composable
private fun rememberDashboardThemeColors(): DashboardThemeColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        DashboardThemeColors(
            background = colorScheme.background,
            surface = colorScheme.surface,
            surfaceVariant = colorScheme.surfaceVariant,
            primary = colorScheme.primary,
            secondary = colorScheme.secondary,
            tertiary = colorScheme.tertiary,
            outline = colorScheme.outline,
            onBackground = colorScheme.onBackground,
            onSurface = colorScheme.onSurface,
            onSurfaceVariant = colorScheme.onSurfaceVariant,
            error = colorScheme.error
        )
    }
}

private data class DashboardThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val outline: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val error: Color
) {
    fun toJson(): String {
        return JSONObject()
            .put("background", background.toCssColor())
            .put("surface", surface.toCssColor())
            .put("surfaceVariant", surfaceVariant.toCssColor())
            .put("primary", primary.toCssColor())
            .put("secondary", secondary.toCssColor())
            .put("tertiary", tertiary.toCssColor())
            .put("outline", outline.toCssColor())
            .put("onBackground", onBackground.toCssColor())
            .put("onSurface", onSurface.toCssColor())
            .put("onSurfaceVariant", onSurfaceVariant.toCssColor())
            .put("error", error.toCssColor())
            .put("primaryRgb", primary.toCssRgb())
            .put("secondaryRgb", secondary.toCssRgb())
            .put("tertiaryRgb", tertiary.toCssRgb())
            .put("errorRgb", error.toCssRgb())
            .toString()
    }
}

private fun Color.toCssColor(): String {
    val argb = toArgb()
    return String.format(
        Locale.US,
        "#%02X%02X%02X",
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb)
    )
}

private fun Color.toCssRgb(): String {
    val argb = toArgb()
    return String.format(
        Locale.US,
        "%d, %d, %d",
        android.graphics.Color.red(argb),
        android.graphics.Color.green(argb),
        android.graphics.Color.blue(argb)
    )
}

private fun handleDashboardUri(
    uri: Uri,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToDnsLogs: () -> Unit,
    onNavigateToDnsCache: () -> Unit,
    onNavigateToRaceStats: () -> Unit,
    onNavigateToBootstrapStats: () -> Unit,
    onNavigateToSubscriptionInterceptionStats: () -> Unit
): Boolean {
    if (uri.scheme != "dnssr") return false
    val target = uri.host
        ?: uri.lastPathSegment
        ?: uri.schemeSpecificPart.removePrefix("//")
    when (target) {
        "back" -> onBack()
        "refresh" -> onRefresh()
        "dns_logs" -> onNavigateToDnsLogs()
        "dns_cache" -> onNavigateToDnsCache()
        "race_stats" -> onNavigateToRaceStats()
        "bootstrap_stats" -> onNavigateToBootstrapStats()
        "subscription_interception_stats" -> onNavigateToSubscriptionInterceptionStats()
    }
    return true
}

private const val DASHBOARD_ASSET_URL = "file:///android_asset/log_dashboard.html"
private const val EMPTY_DASHBOARD_JSON = "{}"
