package com.haoze.dnssr

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.ui.AppNavHost
import com.haoze.dnssr.ui.AppSettings
import com.haoze.dnssr.ui.AppThemeMode
import com.haoze.dnssr.ui.LauncherIconManager
import com.haoze.dnssr.ui.MainViewModel
import com.haoze.dnssr.ui.preloadAboutPage
import com.haoze.dnssr.ui.preloadLogDashboard
import com.haoze.dnssr.ui.theme.DNSSRTheme
import com.haoze.dnssr.ui.theme.ThemeColorStyle
import com.haoze.dnssr.vpn.DnsVpnService
import com.haoze.dnssr.vpn.VpnMonitorService
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DATABASE_WARMUP_DELAY_MS = 500L
private const val LOG_DASHBOARD_WARMUP_DELAY_MS = 900L
private const val ABOUT_PAGE_WARMUP_DELAY_MS = 700L
private const val PREFERRED_REFRESH_RATE_HZ = 120f

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            ensureMonitorServiceState()
            mainViewModel?.refreshStatus()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        prepareVpn()
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply {
            preferredRefreshRate = PREFERRED_REFRESH_RATE_HZ
        }
        enableEdgeToEdge()
        applyRecentsPrivacySetting()
        LauncherIconManager.applyPreferredIcon(this)
        SubscriptionAutoUpdateScheduler.sync(this)
        setContent {
            var themeMode by remember { mutableStateOf(AppSettings.getAppThemeMode(this)) }
            var colorStyle by remember { mutableStateOf(AppSettings.getThemeColorStyle(this)) }
            var backgroundEnabled by remember { mutableStateOf(AppSettings.isCustomBackgroundEnabled(this)) }
            var backgroundUri by remember { mutableStateOf(AppSettings.getCustomBackgroundUri(this)) }
            var backgroundBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(backgroundEnabled, backgroundUri) {
                backgroundBitmap = if (backgroundEnabled && backgroundUri != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            contentResolver.openInputStream(Uri.parse(backgroundUri)).use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        }.getOrNull()
                    }
                } else null
            }
            val darkTheme = when (themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            DNSSRTheme(
                darkTheme = darkTheme,
                colorStyle = colorStyle,
                transparentBackground = backgroundBitmap != null
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = if (backgroundBitmap != null) Color.Transparent else MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize()) {
                        backgroundBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (darkTheme) 0.34f else 0.16f))
                            )
                        }
                        AppNavHost(
                        mainScreen = { onSettings, onLogs, onProviderManagement, onHomeProviderVisibility, onRaceModeSettings ->
                            com.haoze.dnssr.ui.MainScreen(
                                onToggle = { isRunning -> onToggleVpn(isRunning) },
                                onNavigateToSettings = onSettings,
                                onNavigateToLogs = onLogs,
                                onNavigateToProviderManagement = onProviderManagement,
                                onNavigateToHomeProviderVisibility = onHomeProviderVisibility,
                                onNavigateToRaceModeSettings = onRaceModeSettings
                            )
                        },
                        onRuntimeDnsSettingsChanged = { refreshRuntimeConfigIfRunning() },
                        onHideFromRecentsChanged = { applyRecentsPrivacy(it) },
                        onThemeModeChanged = { themeMode = it },
                        onThemeColorStyleChanged = { colorStyle = it },
                        onCustomBackgroundChanged = {
                            backgroundEnabled = AppSettings.isCustomBackgroundEnabled(this@MainActivity)
                            backgroundUri = AppSettings.getCustomBackgroundUri(this@MainActivity)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    }
                }
            }
        }
        lifecycleScope.launch {
            delay(DATABASE_WARMUP_DELAY_MS)
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(applicationContext).openHelper.writableDatabase
            }
        }
        lifecycleScope.launch {
            delay(LOG_DASHBOARD_WARMUP_DELAY_MS)
            preloadLogDashboard(applicationContext)
            delay(ABOUT_PAGE_WARMUP_DELAY_MS)
            preloadAboutPage(applicationContext)
        }
        handleAutoStartIfNeeded(intent)
        ensureMonitorServiceState()
    }

    private fun ensureMonitorServiceState() {
        if (!AppSettings.isPersistentNotificationEnabled(this)) {
            stopService(VpnMonitorService.stopIntent(this))
            return
        }
        if (DnsVpnService.isRunning(this)) return
        if (!hasNotificationPermission()) return
        ContextCompat.startForegroundService(this, VpnMonitorService.startIntent(this))
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoStartIfNeeded(intent)
    }

    override fun onResume() {
        super.onResume()
        applyRecentsPrivacySetting()
        mainViewModel.refreshStatus()
    }

    private fun applyRecentsPrivacySetting() {
        applyRecentsPrivacy(AppSettings.isHideFromRecentsEnabled(this))
    }

    private fun applyRecentsPrivacy(hideFromRecents: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!hideFromRecents)
        }

        val activityManager = getSystemService(ActivityManager::class.java)
        activityManager.appTasks.forEach { task ->
            runCatching { task.setExcludeFromRecents(hideFromRecents) }
        }
    }

    private fun handleAutoStartIfNeeded(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_AUTO_START_VPN, false) != true) return
        // 消费 extra，防止重复触发
        setIntent(intent.replaceExtras(null))
        mainViewModel.refreshStatus { isRunning ->
            if (!isRunning) {
                requestNotificationPermissionThenPrepare()
            }
        }
    }

    private fun onToggleVpn(isRunning: Boolean) {
        if (isRunning) {
            stopVpnService()
        } else {
            requestNotificationPermissionThenPrepare()
        }
    }

    private fun refreshRuntimeConfigIfRunning() {
        mainViewModel.loadProviders()
        mainViewModel.refreshRuntimeConfigIfRunning()
    }

    private fun requestNotificationPermissionThenPrepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)) {
                PackageManager.PERMISSION_GRANTED -> prepareVpn()
                else -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            prepareVpn()
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        // 由 DnsVpnService 自行读取选中的单个 provider 或竞速列表
        ContextCompat.startForegroundService(this, DnsVpnService.startIntent(this))
    }

    private fun stopVpnService() {
        startService(DnsVpnService.stopIntent(this))
    }

    companion object {
        const val EXTRA_AUTO_START_VPN = "auto_start_vpn"
    }
}
