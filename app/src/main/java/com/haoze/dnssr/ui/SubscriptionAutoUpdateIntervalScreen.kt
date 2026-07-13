package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateScheduler
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateSettings

@Composable
fun SubscriptionAutoUpdateIntervalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var intervalHours by remember {
        mutableIntStateOf(SubscriptionAutoUpdateSettings.intervalHours(context))
    }
    var customHours by remember { mutableStateOf(intervalHours.toString()) }
    var customError by remember { mutableStateOf<String?>(null) }

    fun saveInterval(hours: Int) {
        intervalHours = hours
        customHours = hours.toString()
        SubscriptionAutoUpdateSettings.save(
            context,
            SubscriptionAutoUpdateSettings.isEnabled(context),
            hours
        )
        SubscriptionAutoUpdateScheduler.sync(context)
    }

    SettingsScaffold(title = "更新间隔", onBack = onBack) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
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
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = customHours,
                        onValueChange = {
                            customHours = it.filter(Char::isDigit)
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
                    Button(
                        onClick = {
                            val hours = customHours.toIntOrNull()
                            if (hours == null || hours !in SubscriptionAutoUpdateSettings.MIN_INTERVAL_HOURS..SubscriptionAutoUpdateSettings.MAX_INTERVAL_HOURS) {
                                customError = "请输入 1 至 168 之间的小时数"
                            } else {
                                customError = null
                                saveInterval(hours)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("保存自定义间隔")
                    }
                }
            }
        }
    }
}
