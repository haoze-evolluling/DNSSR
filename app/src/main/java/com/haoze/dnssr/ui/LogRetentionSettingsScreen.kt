package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold

private val logRetentionOptions = listOf(1, 7, 30)

@Composable
fun LogRetentionSettingsScreen(
    onBack: () -> Unit,
    onRuntimeDnsSettingsChanged: () -> Unit,
    title: String = "日志模式"
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var logRetention by remember { mutableIntStateOf(AppSettings.logRetentionDays(context)) }
    var logMode by remember { mutableStateOf(AppSettings.getDnsLogMode(context)) }

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
            SettingsGroupTitle("DNS 请求日志")
            SettingsGroup {
                DnsLogMode.entries.forEachIndexed { index, mode ->
                    SettingsRadioItem(
                        title = mode.displayName,
                        subtitle = when (mode) {
                            DnsLogMode.ALL -> "保存通过、拦截和错误请求"
                            DnsLogMode.BLOCKED_AND_ERRORS -> "减少数据库写入，同时保留关键记录"
                            DnsLogMode.OFF -> "不创建或写入 DNS 请求日志"
                        },
                        selected = logMode == mode,
                        onClick = {
                            logMode = mode
                            AppSettings.setDnsLogMode(context, mode)
                            onRuntimeDnsSettingsChanged()
                        }
                    )
                    if (index < DnsLogMode.entries.lastIndex) SettingsDivider()
                }
            }
            SettingsInfoText("关闭后不会删除已有日志，重新开启即可继续查看。")

            if (logMode != DnsLogMode.OFF) {
            SettingsGroupTitle("自动清理时间")
            SettingsGroup {
                logRetentionOptions.forEachIndexed { index, days ->
                    SettingsRadioItem(
                        title = "保留 $days 天",
                        selected = logRetention == days,
                        onClick = {
                            logRetention = days
                            AppSettings.setLogRetentionDays(context, days)
                        }
                    )
                    if (index < logRetentionOptions.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
            SettingsInfoText("超过所选时间的 DNS 请求日志会自动删除，用于控制本地日志占用。")
            }
        }
    }
}
