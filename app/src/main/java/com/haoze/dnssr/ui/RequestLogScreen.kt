package com.haoze.dnssr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.DnsLogEntity
import com.haoze.dnssr.data.entity.HttpRequestLogEntity
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.LogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RequestSource(val label: String) { ALL("全部"), DNS("DNS"), HTTPS("HTTPS") }
private enum class RequestStatus(val label: String) { ALL("全部"), PASSED("通过"), BLOCKED("过滤"), ERROR("失败"), BYPASSED("旁路") }

private data class RequestLogItem(
    val key: String,
    val timestamp: Long,
    val source: RequestSource,
    val status: RequestStatus,
    val title: String,
    val subtitle: String,
    val detail: String?,
    val domain: String?,
    val cached: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLogScreen(onBack: () -> Unit, onRuntimeDnsSettingsChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val database = remember(context) { AppDatabase.getInstance(context) }
    val dnsLogs by remember(database) { database.dnsLogDao().observeRecentForRequests() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val httpLogs by remember(database) { database.httpRequestLogDao().observeRecent() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var source by remember { mutableStateOf(RequestSource.ALL) }
    var status by remember { mutableStateOf(RequestStatus.ALL) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var pendingDomain by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val allItems = remember(dnsLogs, httpLogs) {
        (dnsLogs.map(::dnsRequestItem) + httpLogs.map(::httpRequestItem)).sortedByDescending { it.timestamp }
    }
    val visibleItems = remember(allItems, source, status, query) {
        val needle = query.trim()
        allItems.filter { item ->
            (source == RequestSource.ALL || item.source == source) &&
                (status == RequestStatus.ALL || item.status == status) &&
                (needle.isEmpty() || listOf(item.title, item.subtitle, item.detail.orEmpty()).any { it.contains(needle, true) })
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(requestCsv(visibleItems)) } ?: error("无法打开导出文件") } }
            Toast.makeText(context, if (result.isSuccess) "日志已导出" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = {
                if (searching) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, placeholder = { Text("搜索请求") }, modifier = Modifier.fillMaxWidth())
                else Text("请求日志")
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            actions = {
                IconButton(onClick = { if (searching && query.isNotEmpty()) query = "" else searching = !searching }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, if (searching) "关闭搜索" else "搜索") }
                IconButton(onClick = { exportLauncher.launch("dnssr-request-logs-${System.currentTimeMillis()}.csv") }) { Icon(Icons.Default.FileDownload, "导出 CSV") }
                RequestLogOverflowMenu(status = status, onStatusChange = { status = it })
            }
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            RequestFilterRow(RequestSource.entries, source, { it.label }, { source = it })
            HorizontalDivider()
            if (visibleItems.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("当前筛选下暂无请求日志", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visibleItems, key = { it.key }) { item -> RequestLogCard(item) { item.domain?.let { pendingDomain = it } } }
            }
        }
    }
    pendingDomain?.let { domain ->
        RequestDomainDialog(domain, { pendingDomain = null }, {
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("domain", domain)); pendingDomain = null
        }, { allow ->
            scope.launch(Dispatchers.IO) {
                val success = if (allow) AllowListManager(database.allowRuleDao()).addRule(domain) else BlockListManager(database.blockRuleDao()).addRule(domain)
                withContext(Dispatchers.Main) { if (success) onRuntimeDnsSettingsChanged(); Toast.makeText(context, if (success) "已添加规则" else "规则格式无效", Toast.LENGTH_SHORT).show(); pendingDomain = null }
            }
        })
    }
}

@Composable
private fun RequestLogOverflowMenu(
    status: RequestStatus,
    onStatusChange: (RequestStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.FilterList, contentDescription = "选择状态")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(100.dp)
        ) {
            RequestStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = if (status == option) "已选中" else null,
                            tint = if (status == option) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                    },
                    onClick = {
                        onStatusChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> RequestFilterRow(values: List<T>, selected: T, label: (T) -> String, select: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value -> TextButton(onClick = { select(value) }, colors = ButtonDefaults.textButtonColors(containerColor = if (value == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)) { Text(label(value)) } }
    }
}

@Composable
private fun RequestLogCard(item: RequestLogItem, onLongClick: () -> Unit) {
    val color = when (item.status) { RequestStatus.PASSED -> Color(0xFF2E7D32); RequestStatus.BLOCKED -> Color(0xFFC62828); RequestStatus.ERROR -> Color(0xFF757575); RequestStatus.BYPASSED -> Color(0xFF616161); else -> MaterialTheme.colorScheme.primary }
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLongClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = .12f)) { Text(item.status.label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            }
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
            item.detail?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun RequestDomainDialog(domain: String, dismiss: () -> Unit, copy: () -> Unit, add: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("处理域名") }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(domain); OutlinedButton(copy, Modifier.fillMaxWidth()) { Text("复制域名") }; OutlinedButton({ add(true) }, Modifier.fillMaxWidth()) { Text("加入白名单规则") }; OutlinedButton({ add(false) }, Modifier.fillMaxWidth()) { Text("加入屏蔽规则") }
    } }, confirmButton = { TextButton(dismiss) { Text("取消") } })
}

private val requestTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
private fun dnsRequestItem(log: DnsLogEntity) = RequestLogItem("dns-${log.id}", log.timestamp, RequestSource.DNS, when (log.result) { LogResult.PASSED.value -> RequestStatus.PASSED; LogResult.BLOCKED.value -> RequestStatus.BLOCKED; else -> RequestStatus.ERROR }, log.queryName, "${requestTime.format(Date(log.timestamp))} · DNS · ${dnsRequestType(log.queryType)}${if (log.cached) " · 命中缓存" else ""}", log.message, log.queryName, log.cached)
private fun httpRequestItem(log: HttpRequestLogEntity) = RequestLogItem("https-${log.id}", log.timestamp, RequestSource.HTTPS, when (log.outcome) { "allowed", "rewritten" -> RequestStatus.PASSED; "blocked", "invalid" -> RequestStatus.BLOCKED; "decryption_failed", "unsupported_protocol", "resource_bypass" -> RequestStatus.BYPASSED; else -> RequestStatus.ERROR }, log.authority ?: "未取得 authority", "${requestTime.format(Date(log.timestamp))} · HTTPS · ${log.protocol} · ${log.packageName}", log.matchedRule?.let { "匹配规则 · $it" }, log.authority)
private fun dnsRequestType(type: Int) = when (type) { 1 -> "A"; 28 -> "AAAA"; 5 -> "CNAME"; 15 -> "MX"; 16 -> "TXT"; 2 -> "NS"; 12 -> "PTR"; 255 -> "ANY"; else -> "TYPE$type" }
private fun requestCsv(items: List<RequestLogItem>) = buildString { append('\uFEFF'); appendLine("timestamp,time,source,status,request,details"); items.forEach { appendLine(listOf(it.timestamp, requestTime.format(Date(it.timestamp)), it.source.label, it.status.label, it.title, it.subtitle + (it.detail?.let { d -> " · $d" } ?: "")).joinToString(",") { v -> "\"${v.toString().replace("\"", "\"\"")}\"" }) } }
