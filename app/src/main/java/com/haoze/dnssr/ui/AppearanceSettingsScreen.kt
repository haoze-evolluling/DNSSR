package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    title: String,
    onNavigateToDayNightMode: (String) -> Unit
) {
    val context = LocalContext.current
    val mode = AppSettings.getAppThemeMode(context)
    val dayNightTitle = "日夜模式"

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
