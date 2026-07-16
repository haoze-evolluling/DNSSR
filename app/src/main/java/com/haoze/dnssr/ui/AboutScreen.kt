package com.haoze.dnssr.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import java.util.Locale

private const val PROJECT_REPOSITORY_URL = "https://github.com/haoze-evolluling/DNSSR"
private const val ABOUT_ASSET_URL = "file:///android_asset/app_info.html"

private object AboutWebViewCache {
    var webView: WebView? = null
    var pageReady: Boolean = false
}

@SuppressLint("SetJavaScriptEnabled")
internal fun preloadAboutPage(context: Context) {
    if (AboutWebViewCache.webView != null) return
    AboutWebViewCache.webView = WebView(context.applicationContext).apply {
        configureAboutPageSettings()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                AboutWebViewCache.pageReady = true
            }
        }
        loadUrl(ABOUT_ASSET_URL)
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    title: String = "应用信息"
) {
    val context = LocalContext.current
    val themeColors = rememberAboutThemeColors()
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")
    }
    val pageData = remember(title, versionName) {
        JSONObject()
            .put("title", title)
            .put("version", versionName)
            .put("repositoryUrl", PROJECT_REPOSITORY_URL)
            .toString()
    }
    var webView by remember { mutableStateOf(AboutWebViewCache.webView) }
    var pageReady by remember { mutableStateOf(AboutWebViewCache.pageReady) }
    var webViewAttached by rememberSaveable { mutableStateOf(false) }
    val webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean = handleAboutUri(request.url, onBack) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_REPOSITORY_URL))
            runCatching { context.startActivity(intent) }
                .onFailure {
                    Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
                }
        }

        override fun onPageFinished(view: WebView, url: String?) {
            AboutWebViewCache.pageReady = true
            pageReady = true
        }
    }

    LaunchedEffect(pageReady, pageData, themeColors) {
        val view = webView
        if (pageReady && view != null) {
            view.evaluateJavascript(
                "window.DNSSR && window.DNSSR.setTheme(${themeColors.toJson()});",
                null
            )
            view.evaluateJavascript(
                "window.DNSSR && window.DNSSR.render($pageData);",
                null
            )
        }
    }
    NavigationSettledEffect {
        webViewAttached = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.background)
    ) {
        if (webViewAttached) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val cachedWebView = AboutWebViewCache.webView
                val view = cachedWebView ?: WebView(viewContext.applicationContext).apply {
                    configureAboutPageSettings()
                }
                view.webViewClient = webViewClient
                view.setBackgroundColor(themeColors.background.toArgb())
                if (cachedWebView == null) {
                    AboutWebViewCache.webView = view
                    view.loadUrl(ABOUT_ASSET_URL)
                }
                webView = view
                view
            },
            update = { view ->
                webView = view
                view.webViewClient = webViewClient
                view.setBackgroundColor(themeColors.background.toArgb())
            }
        )
        if (!webViewAttached || !pageReady) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColors.background),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureAboutPageSettings() {
    isVerticalScrollBarEnabled = false
    isHorizontalScrollBarEnabled = false
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
private fun rememberAboutThemeColors(): AboutThemeColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(colorScheme) {
        AboutThemeColors(
            background = colorScheme.background,
            surface = colorScheme.surface,
            surfaceVariant = colorScheme.surfaceVariant,
            primary = colorScheme.primary,
            secondary = colorScheme.secondary,
            tertiary = colorScheme.tertiary,
            outline = colorScheme.outline,
            onBackground = colorScheme.onBackground,
            onSurface = colorScheme.onSurface,
            onSurfaceVariant = colorScheme.onSurfaceVariant
        )
    }
}

private data class AboutThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val outline: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color
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
            .put("primaryRgb", primary.toCssRgb())
            .put("secondaryRgb", secondary.toCssRgb())
            .put("tertiaryRgb", tertiary.toCssRgb())
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

private fun handleAboutUri(uri: Uri, onBack: () -> Unit, onOpenRepository: () -> Unit): Boolean {
    if (uri.scheme != "dnssr") return true
    val target = uri.host
        ?: uri.lastPathSegment
        ?: uri.schemeSpecificPart.removePrefix("//")
    when (target) {
        "back" -> onBack()
        "repository" -> onOpenRepository()
    }
    return true
}
