package com.haoze.dnssr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
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
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsItem
import com.haoze.dnssr.ui.theme.ThemeColorStyle

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    title: String,
    onNavigateToDayNightMode: (String) -> Unit,
    onNavigateToThemeColorSettings: (String) -> Unit
) {
    val context = LocalContext.current
    val mode = AppSettings.getAppThemeMode(context)
    val dayNightTitle = "日夜模式"
    val colorSettingsTitle = "主题色配置"
    val colorStyle = AppSettings.getThemeColorStyle(context)

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
                        leadingIcon = Icons.Filled.DarkMode,
                        value = mode.displayName,
                        onClick = { onNavigateToDayNightMode(dayNightTitle) }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = colorSettingsTitle,
                        subtitle = "选择应用界面的强调色",
                        leadingIcon = Icons.Filled.Palette,
                        value = colorStyle.displayName,
                        onClick = { onNavigateToThemeColorSettings(colorSettingsTitle) }
                    )
                }
            }
        }
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
