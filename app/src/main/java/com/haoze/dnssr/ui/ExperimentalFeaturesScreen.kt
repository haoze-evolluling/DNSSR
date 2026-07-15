package com.haoze.dnssr.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem

@Composable
fun ExperimentalFeaturesScreen(
    onBack: () -> Unit,
    title: String = "实验功能"
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var legacyIconEnabled by remember {
        mutableStateOf(AppSettings.isLegacyIconEnabled(context))
    }
    var legacyLogPageEnabled by remember {
        mutableStateOf(AppSettings.isLegacyLogPageEnabled(context))
    }
    val serviceLightEffectSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var serviceLightEffectEnabled by remember {
        mutableStateOf(AppSettings.isServiceLightEffectEnabled(context))
    }
    val customBackgroundEnabled = AppSettings.isCustomBackgroundEnabled(context)

    fun saveLegacyIconEnabled(enabled: Boolean) {
        legacyIconEnabled = enabled
        AppSettings.setLegacyIconEnabled(context, enabled)
        LauncherIconManager.setLegacyIconEnabled(context, enabled)
    }

    fun saveLegacyLogPageEnabled(enabled: Boolean) {
        legacyLogPageEnabled = enabled
        AppSettings.setLegacyLogPageEnabled(context, enabled)
    }

    fun saveServiceLightEffectEnabled(enabled: Boolean) {
        if (!serviceLightEffectSupported) return
        serviceLightEffectEnabled = enabled
        AppSettings.setServiceLightEffectEnabled(context, enabled)
    }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsInfoText(
                text = "这里会集中放置尚未成熟、仍在开发中的功能。实验性功能可能调整、移除或存在兼容性问题。",
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingsGroupTitle("外观")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "服务动态光影",
                    subtitle = if (customBackgroundEnabled) {
                        "软件背景已启用，服务动态光影不可同时使用"
                    } else if (serviceLightEffectSupported) {
                        "启动和关闭服务时，光影从电源按钮向整个页面展开或收回\n光影效果代码来源于开源项目:\nhttps://github.com/badnng/Hyper-pick-up-code/"
                    } else {
                        "需要 Android 13 或更高版本"
                    },
                    checked = serviceLightEffectEnabled,
                    enabled = serviceLightEffectSupported && !customBackgroundEnabled,
                    onCheckedChange = ::saveServiceLightEffectEnabled
                )
                SettingsSwitchItem(
                    title = "使用旧版图标",
                    subtitle = "开启后仅将桌面入口图标切换为旧版样式",
                    checked = legacyIconEnabled,
                    onCheckedChange = ::saveLegacyIconEnabled
                )
            }
            SettingsInfoText("桌面图标可能需要等待启动器刷新；如果未立即变化，可回到桌面稍等片刻。")

            SettingsGroupTitle("日志")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "旧版日志页面",
                    subtitle = "开启后首页日志按钮显示当前的日志分组页；关闭后显示新版日志仪表盘",
                    checked = legacyLogPageEnabled,
                    onCheckedChange = ::saveLegacyLogPageEnabled
                )
            }
        }
    }
}
