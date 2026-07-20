package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.haoze.dnssr.ui.components.AppAlertDialog as AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.SettingsSwitchItem
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateScheduler
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateSettings

@Composable
fun SubscriptionAutoUpdateIntervalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var intervalHours by remember {
        mutableIntStateOf(SubscriptionAutoUpdateSettings.intervalHours(context))
    }
    var autoUpdateEnabled by remember {
        mutableStateOf(SubscriptionAutoUpdateSettings.isEnabled(context))
    }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customHours by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    fun saveInterval(hours: Int) {
        intervalHours = hours
        SubscriptionAutoUpdateSettings.save(
            context,
            SubscriptionAutoUpdateSettings.isEnabled(context),
            hours
        )
        SubscriptionAutoUpdateScheduler.sync(context)
    }

    fun openCustomDialog() {
        customHours = intervalHours.toString()
        customError = null
        showCustomDialog = true
    }

    fun closeCustomDialog() {
        showCustomDialog = false
        customError = null
    }

    SettingsScaffold(title = "自动更新设置", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            SettingsGroupTitle("自动更新")
            SettingsGroup {
                SettingsSwitchItem(
                    title = "自动更新规则订阅",
                    subtitle = "在后台定期更新所有网络订阅，实际执行时间可能受系统调度影响",
                    checked = autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        autoUpdateEnabled = enabled
                        SubscriptionAutoUpdateSettings.save(context, enabled, intervalHours)
                        SubscriptionAutoUpdateScheduler.sync(context)
                    }
                )
            }

            SettingsGroupTitle("自动更新时间")
            SettingsGroup {
                SubscriptionAutoUpdateSettings.intervals.forEachIndexed { index, hours ->
                    SettingsRadioItem(
                        title = "每 $hours 小时",
                        selected = intervalHours == hours,
                        onClick = {
                            customError = null
                            saveInterval(hours)
                        }
                    )
                    if (index < SubscriptionAutoUpdateSettings.intervals.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
            SettingsInfoText("系统会在后台按此频率检查网络规则订阅，实际执行时间可能受系统调度影响。")

            SettingsGroupTitle("自定义间隔")
            SettingsGroup {
                SettingsNavigationItem(
                    title = "自定义更新时间",
                    subtitle = "输入 1 至 168 小时之间的更新时间",
                    value = if (intervalHours !in SubscriptionAutoUpdateSettings.intervals) {
                        "每 $intervalHours 小时"
                    } else {
                        null
                    },
                    onClick = ::openCustomDialog
                )
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = ::closeCustomDialog,
            title = { Text("自定义更新时间") },
            text = {
                OutlinedTextField(
                    value = customHours,
                    onValueChange = {
                        customHours = it
                        customError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("小时") },
                    supportingText = {
                        Text(customError ?: "可设置 1 至 168 小时")
                    },
                    isError = customError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val hours = customHours.trim().toIntOrNull()
                        if (
                            hours == null ||
                            hours !in SubscriptionAutoUpdateSettings.MIN_INTERVAL_HOURS..SubscriptionAutoUpdateSettings.MAX_INTERVAL_HOURS
                        ) {
                            customError = "请输入 1 至 168 之间的小时数"
                        } else {
                            saveInterval(hours)
                            closeCustomDialog()
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = ::closeCustomDialog) {
                    Text("取消")
                }
            }
        )
    }
}
