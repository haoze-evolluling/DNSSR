package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.HttpRequestLogEntity
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HttpRequestLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by remember(context) {
        AppDatabase.getInstance(context).httpRequestLogDao().observeRecent()
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val timeFormatter = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    SettingsScaffold(title = "HTTP 请求记录", onBack = onBack) { innerPadding ->
        if (logs.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                SettingsInfoText("暂无 HTTP 请求记录。启用数据面并处理所选应用的请求后，记录会显示在这里。")
            }
            return@SettingsScaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(logs, key = { it.id }) { log ->
                HttpRequestLogRow(log, timeFormatter.format(Date(log.timestamp)))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun HttpRequestLogRow(log: HttpRequestLogEntity, formattedTime: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = log.authority ?: "未取得 authority",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = outcomeLabel(log.outcome),
                style = MaterialTheme.typography.labelLarge,
                color = outcomeColor(log.outcome),
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Text(log.packageName, style = MaterialTheme.typography.bodySmall)
        Text(
            listOf(log.protocol, formattedTime, log.matchedRule?.let { "规则：$it" })
                .filterNotNull()
                .joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun outcomeLabel(outcome: String) = when (outcome) {
    "allowed" -> "已放行"
    "blocked" -> "已拦截"
    "invalid" -> "无效请求"
    "decryption_failed" -> "解密失败直连"
    "unsupported_protocol" -> "协议直连"
    "resource_bypass" -> "资源绕过"
    else -> outcome
}

@Composable
private fun outcomeColor(outcome: String) = when (outcome) {
    "blocked", "invalid" -> MaterialTheme.colorScheme.error
    "allowed" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
