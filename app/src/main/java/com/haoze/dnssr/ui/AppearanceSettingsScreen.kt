package com.haoze.dnssr.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsItem
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsSwitchItem
import com.haoze.dnssr.ui.theme.ThemeColorStyle

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    title: String,
    onNavigateToDayNightMode: (String) -> Unit,
    onNavigateToThemeColorSettings: (String) -> Unit,
    onNavigateToHomeComponentOpacity: (String) -> Unit,
    onNavigateToHomeSentence: (String) -> Unit,
    onNavigateToCustomBackground: (String) -> Unit
) {
    val context = LocalContext.current
    val mode = AppSettings.getAppThemeMode(context)
    val dayNightTitle = "日夜模式"
    val colorSettingsTitle = "主题色配置"
    val colorStyle = AppSettings.getThemeColorStyle(context)
    val homeComponentOpacityTitle = "首页透明度"
    val homeSentenceTitle = "首页句子"
    val customBackgroundTitle = "软件背景"

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            item { SettingsGroupTitle("显示") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = dayNightTitle,
                        subtitle = "选择应用使用的浅色或深色外观",
                        value = mode.displayName,
                        onClick = { onNavigateToDayNightMode(dayNightTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = colorSettingsTitle,
                        subtitle = "选择应用界面的强调色",
                        value = colorStyle.displayName,
                        onClick = { onNavigateToThemeColorSettings(colorSettingsTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = homeComponentOpacityTitle,
                        subtitle = "分别调整首页按钮、选择框与文字的透明度",
                        onClick = { onNavigateToHomeComponentOpacity(homeComponentOpacityTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = homeSentenceTitle,
                        subtitle = "分别设置 DNS 服务开启和关闭时的句子",
                        onClick = { onNavigateToHomeSentence(homeSentenceTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = customBackgroundTitle,
                        subtitle = "选取手机图片作为应用背景",
                        onClick = { onNavigateToCustomBackground(customBackgroundTitle) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomBackgroundSettingsScreen(
    onBack: () -> Unit,
    title: String,
    onBackgroundChanged: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(AppSettings.isCustomBackgroundEnabled(context)) }
    var uri by remember { mutableStateOf(AppSettings.getCustomBackgroundUri(context)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(selected, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            uri = selected.toString()
            enabled = true
            AppSettings.setCustomBackground(context, true, uri)
            onBackgroundChanged()
        }
    }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { SettingsGroupTitle("自定义背景") }
            item {
                SettingsGroup {
                    SettingsSwitchItem(
                        title = "启用软件背景",
                        subtitle = if (uri == null) "请先选择一张图片" else "启用后服务动态光影将自动关闭",
                        checked = enabled,
                        enabled = uri != null,
                        onCheckedChange = {
                            enabled = it
                            AppSettings.setCustomBackground(context, it, uri)
                            onBackgroundChanged()
                        }
                    )
                    SettingsDivider()
                    SettingsItem(title = if (uri == null) "选择图片" else "更换图片") {
                        TextButton(onClick = { picker.launch(arrayOf("image/*")) }) { Text("选择") }
                    }
                    if (uri != null) {
                        SettingsDivider()
                        SettingsItem(title = "移除当前图片") {
                            TextButton(onClick = {
                                uri = null
                                enabled = false
                                AppSettings.setCustomBackground(context, false, null)
                                onBackgroundChanged()
                            }) { Text("移除") }
                        }
                    }
                }
            }
            item { SettingsInfoText("软件背景与服务动态光影不可同时启用。") }
        }
    }
}

@Composable
fun HomeSentenceSettingsScreen(onBack: () -> Unit, title: String) {
    val context = LocalContext.current
    var runningSentence by remember { mutableStateOf(AppSettings.getHomeSentenceRunning(context)) }
    var stoppedSentence by remember { mutableStateOf(AppSettings.getHomeSentenceStopped(context)) }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { SettingsGroupTitle("首页句子") }
            item {
                SettingsGroup {
                    OutlinedTextField(
                        value = runningSentence,
                        onValueChange = { runningSentence = it },
                        label = { Text("DNS 服务开启时") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                    OutlinedTextField(
                        value = stoppedSentence,
                        onValueChange = { stoppedSentence = it },
                        label = { Text("DNS 服务关闭时") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                    Button(
                        onClick = {
                            AppSettings.setHomeSentences(context, runningSentence, stoppedSentence)
                            onBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        Text("确定")
                    }
                }
            }
            item { SettingsInfoText("两项内容均可留空；留空后对应状态下首页不显示句子。") }
        }
    }
}

@Composable
fun HomeComponentOpacityScreen(onBack: () -> Unit, title: String) {
    val context = LocalContext.current
    var powerButton by remember { mutableStateOf(AppSettings.getHomePowerButtonOpacity(context)) }
    var providerSelector by remember { mutableStateOf(AppSettings.getHomeProviderSelectorOpacity(context)) }
    var modeButton by remember { mutableStateOf(AppSettings.getHomeModeButtonOpacity(context)) }
    var poem by remember { mutableStateOf(AppSettings.getHomePoemOpacity(context)) }
    var dnsDetail by remember { mutableStateOf(AppSettings.getHomeDnsDetailOpacity(context)) }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { SettingsGroupTitle("交互组件") }
            item {
                SettingsGroup {
                    OpacitySlider("启动按钮", powerButton, { powerButton = it }) {
                        AppSettings.setHomePowerButtonOpacity(context, powerButton)
                    }
                    SettingsDivider()
                    OpacitySlider("解析服务选择框", providerSelector, { providerSelector = it }) {
                        AppSettings.setHomeProviderSelectorOpacity(context, providerSelector)
                    }
                    SettingsDivider()
                    OpacitySlider("模式切换按钮", modeButton, { modeButton = it }) {
                        AppSettings.setHomeModeButtonOpacity(context, modeButton)
                    }
                }
            }
            item { SettingsGroupTitle("文字") }
            item {
                SettingsGroup {
                    OpacitySlider("首页古诗", poem, { poem = it }) {
                        AppSettings.setHomePoemOpacity(context, poem)
                    }
                    SettingsDivider()
                    OpacitySlider("DNS 服务详情", dnsDetail, { dnsDetail = it }) {
                        AppSettings.setHomeDnsDetailOpacity(context, dnsDetail)
                    }
                }
            }
        }
    }
}

@Composable
private fun OpacitySlider(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text("$title · ${(value * 100).roundToInt()}%")
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0.1f..1f,
            steps = 8,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ThemeColorSettingsScreen(
    onBack: () -> Unit,
    title: String,
    onThemeColorStyleChanged: (ThemeColorStyle) -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(AppSettings.getThemeColorStyle(context)) }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item { SettingsGroupTitle("主题色") }
            item {
                SettingsGroup {
                    ThemeColorStyle.entries.forEachIndexed { index, style ->
                        SettingsItem(
                            title = style.displayName,
                            subtitle = if (style == ThemeColorStyle.SYSTEM) "使用系统壁纸的动态取色" else null,
                            onClick = {
                                selectedStyle = style
                                AppSettings.setThemeColorStyle(context, style)
                                onThemeColorStyleChanged(style)
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(style.lightPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedStyle == style) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "已选中",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                        if (index < ThemeColorStyle.entries.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun DayNightModeScreen(
    onBack: () -> Unit,
    title: String,
    onThemeModeChanged: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(AppSettings.getAppThemeMode(context)) }

    SettingsScaffold(title = title, onBack = onBack) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            item { SettingsGroupTitle("主题") }
            item {
                SettingsGroup {
                    AppThemeMode.entries.forEachIndexed { index, mode ->
                        SettingsRadioItem(
                            title = mode.displayName,
                            selected = selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                AppSettings.setAppThemeMode(context, mode)
                                onThemeModeChanged(mode)
                            }
                        )
                        if (index < AppThemeMode.entries.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}
