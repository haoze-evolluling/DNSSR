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
import com.haoze.dnssr.ui.components.AppAlertDialog as AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.haoze.dnssr.ui.PermissionDisclosureSettings
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

private enum class PermissionDisclosure {
    NOTIFICATION,
    VPN
}

class MainActivity : ComponentActivity() {

    private var permissionDisclosure by mutableStateOf<PermissionDisclosure?>(null)

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            PermissionDisclosureSettings.updateVpnGrant(this, true)
            startVpnService()
        } else {
            PermissionDisclosureSettings.updateVpnGrant(this, false)
            ensureMonitorServiceState()
            mainViewModel?.refreshStatus()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionDisclosureSettings.updateNotificationGrant(this, granted)
        prepareVpn()
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                        permissionDisclosure?.let { disclosure ->
                            PermissionDisclosureDialog(
                                disclosure = disclosure,
                                onContinue = { continuePermissionRequest(disclosure) },
                                onDismiss = { dismissPermissionRequest(disclosure) }
                            )
                        }
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
        PermissionDisclosureSettings.updateNotificationGrant(this, hasNotificationPermission())
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
                PackageManager.PERMISSION_GRANTED -> {
                    PermissionDisclosureSettings.updateNotificationGrant(this, true)
                    prepareVpn()
                }
                else -> {
                    PermissionDisclosureSettings.updateNotificationGrant(this, false)
                    when {
                        !PermissionDisclosureSettings.isNotificationExplained(this) -> {
                            permissionDisclosure = PermissionDisclosure.NOTIFICATION
                        }
                        PermissionDisclosureSettings.wasNotificationRequested(this) -> prepareVpn()
                        else -> requestNotificationPermission()
                    }
                }
            }
        } else {
            prepareVpn()
        }
    }

    private fun prepareVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            PermissionDisclosureSettings.updateVpnGrant(this, false)
            if (PermissionDisclosureSettings.isVpnExplained(this)) {
                vpnPrepareLauncher.launch(intent)
            } else {
                permissionDisclosure = PermissionDisclosure.VPN
            }
        } else {
            PermissionDisclosureSettings.updateVpnGrant(this, true)
            startVpnService()
        }
    }

    private fun requestNotificationPermission() {
        PermissionDisclosureSettings.markNotificationRequested(this)
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun continuePermissionRequest(disclosure: PermissionDisclosure) {
        permissionDisclosure = null
        when (disclosure) {
            PermissionDisclosure.NOTIFICATION -> {
                PermissionDisclosureSettings.setNotificationExplained(this, true)
                requestNotificationPermission()
            }
            PermissionDisclosure.VPN -> {
                PermissionDisclosureSettings.setVpnExplained(this, true)
                prepareVpn()
            }
        }
    }

    private fun dismissPermissionRequest(disclosure: PermissionDisclosure) {
        permissionDisclosure = null
        when (disclosure) {
            PermissionDisclosure.NOTIFICATION -> {
                PermissionDisclosureSettings.setNotificationExplained(this, true)
                PermissionDisclosureSettings.markNotificationRequested(this)
                prepareVpn()
            }
            PermissionDisclosure.VPN -> {
                PermissionDisclosureSettings.setVpnExplained(this, true)
                ensureMonitorServiceState()
                mainViewModel.refreshStatus()
            }
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

@androidx.compose.runtime.Composable
private fun PermissionDisclosureDialog(
    disclosure: PermissionDisclosure,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val title: String
    val message: String
    when (disclosure) {
        PermissionDisclosure.NOTIFICATION -> {
            title = "通知权限"
            message = "通知用于显示 DNS VPN 的运行和停止状态。拒绝不会阻止核心功能运行，但你可能无法及时看到连接状态提醒。"
        }
        PermissionDisclosure.VPN -> {
            title = "VPN 连接权限"
            message = "DNSSR 需要建立本地 VPN 来处理和过滤 DNS 请求。此权限用于在设备上接管 DNS 流量，不会将全部网络流量发送到远程 VPN 服务器。"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onContinue) { Text("继续") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("暂不允许") } }
    )
}
