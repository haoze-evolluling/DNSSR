package com.haoze.dnssr.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.LogDailyStats
import com.haoze.dnssr.data.LogFilter
import com.haoze.dnssr.data.entity.DnsLogEntity
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.LogResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

private data class LogFilterOption(
    val filter: LogFilter,
    val label: String,
    val count: Int,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onBack: () -> Unit,
    onRuntimeDnsSettingsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: LogViewModel = viewModel()
    val logs = viewModel.logs.collectAsLazyPagingItems()
    val dailyStats by viewModel.dailyStats.collectAsStateWithLifecycle()
    val params by viewModel.params.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var pendingDomain by remember { mutableStateOf<String?>(null) }
    var isExporting by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isExporting = true
            val result = runCatching {
                val csv = viewModel.exportCurrentLogsCsv()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(csv)
                    } ?: error("无法打开导出文件")
                }
            }
            Toast.makeText(
                context,
                if (result.isSuccess) "日志已导出" else "导出失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
            isExporting = false
        }
    }

    LaunchedEffect(Unit) {
        delay(300) // 等待页面进入动画完成后再加载数据
        viewModel.activate()
        viewModel.refresh()
    }

    LogScreenContent(
        logs = logs,
        dailyStats = dailyStats,
        selectedFilter = params.filter,
        listState = listState,
        searchQuery = searchQuery,
        isSearchActive = isSearchActive,
        isExporting = isExporting,
        onBack = onBack,
        onSearchQueryChange = {
            searchQuery = it
            viewModel.onSearchQueryChange(it)
        },
        onSearchClick = { isSearchActive = true },
        onCloseSearchClick = {
            if (searchQuery.isNotEmpty()) {
                searchQuery = ""
                viewModel.onSearchQueryChange("")
            } else {
                isSearchActive = false
            }
        },
        onRefresh = {
            viewModel.refresh()
            logs.refresh()
        },
        onExport = {
            exportLauncher.launch(viewModel.exportFileName())
        },
        onFilterClick = { filter ->
            viewModel.onFilterChange(filter)
        },
        onLogLongClick = { pendingDomain = it }
    )

    pendingDomain?.let { domain ->
        AddRuleFromLogDialog(
            domain = domain,
            onDismiss = { pendingDomain = null },
            onCopyDomain = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("DNS domain", domain))
                Toast.makeText(context, "已复制域名", Toast.LENGTH_SHORT).show()
                pendingDomain = null
            },
            onAddBlockRule = {
                scope.launch(Dispatchers.IO) {
                    val manager = BlockListManager(AppDatabase.getInstance(context).blockRuleDao())
                    val success = manager.addRule(domain)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            onRuntimeDnsSettingsChanged()
                        }
                        Toast.makeText(
                            context,
                            if (success) "已添加规则" else "规则格式无效",
                            Toast.LENGTH_SHORT
                        ).show()
                        pendingDomain = null
                    }
                }
            },
            onAddAllowRule = {
                scope.launch(Dispatchers.IO) {
                    val manager = AllowListManager(AppDatabase.getInstance(context).allowRuleDao())
                    val success = manager.addRule(domain)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            onRuntimeDnsSettingsChanged()
                        }
                        Toast.makeText(
                            context,
                            if (success) "已添加白名单规则" else "规则格式无效",
                            Toast.LENGTH_SHORT
                        ).show()
                        pendingDomain = null
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogScreenContent(
    logs: LazyPagingItems<DnsLogEntity>,
    dailyStats: LogDailyStats,
    selectedFilter: LogFilter,
    listState: LazyListState,
    searchQuery: String,
    isSearchActive: Boolean,
    isExporting: Boolean,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onFilterClick: (LogFilter) -> Unit,
    onLogLongClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            LogTopBar(
                searchQuery = searchQuery,
                isSearchActive = isSearchActive,
                onBack = onBack,
                onSearchQueryChange = onSearchQueryChange,
                onSearchClick = onSearchClick,
                onCloseSearchClick = onCloseSearchClick,
                onRefresh = onRefresh,
                onExport = onExport,
                isExporting = isExporting
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LogFilterBar(
                stats = dailyStats,
                selectedFilter = selectedFilter,
                onFilterClick = onFilterClick
            )

            HorizontalDivider()

            LogList(
                logs = logs,
                listState = listState,
                onLogLongClick = onLogLongClick,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogTopBar(
    searchQuery: String,
    isSearchActive: Boolean,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onCloseSearchClick: () -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    isExporting: Boolean
) {
    TopAppBar(
        title = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                var keepSearchField by remember { mutableStateOf(isSearchActive) }
                LaunchedEffect(isSearchActive) {
                    if (isSearchActive) {
                        keepSearchField = true
                    }
                }
                val searchWidth by animateDpAsState(
                    targetValue = if (isSearchActive) maxWidth else 0.dp,
                    animationSpec = tween(200),
                    finishedListener = {
                        if (!isSearchActive) {
                            keepSearchField = false
                        }
                    },
                    label = "LogSearchWidth"
                )
                val titleAlpha by animateFloatAsState(
                    targetValue = if (isSearchActive) 0f else 1f,
                    animationSpec = tween(120),
                    label = "LogTitleAlpha"
                )
                val searchAlpha by animateFloatAsState(
                    targetValue = if (isSearchActive) 1f else 0f,
                    animationSpec = tween(120),
                    label = "LogSearchAlpha"
                )

                Text(
                    text = "DNS 日志",
                    modifier = Modifier.alpha(titleAlpha)
                )
                if (keepSearchField) {
                    Box(
                        modifier = Modifier
                            .width(searchWidth)
                            .clipToBounds()
                            .alpha(searchAlpha)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("搜索域名...") },
                            singleLine = true,
                            shape = SettingsCornerShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
        },
        actions = {
            if (isSearchActive) {
                IconButton(onClick = onCloseSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭搜索"
                    )
                }
            } else {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索"
                    )
                }
            }
            IconButton(
                onClick = onExport,
                enabled = !isExporting
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = "导出日志"
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新"
                )
            }
        }
    )
}

@Composable
private fun LogFilterBar(
    stats: LogDailyStats,
    selectedFilter: LogFilter,
    onFilterClick: (LogFilter) -> Unit
) {
    val totalCount = stats.passed + stats.blocked + stats.error
    val allColor = MaterialTheme.colorScheme.onSurface
    val options = remember(stats, allColor) {
        listOf(
            LogFilterOption(LogFilter.ALL, "全部", totalCount, allColor),
            LogFilterOption(LogFilter.PASSED, "通过", stats.passed, Color(0xFF2E7D32)),
            LogFilterOption(LogFilter.BLOCKED, "过滤", stats.blocked, Color(0xFFC62828)),
            LogFilterOption(LogFilter.ERROR, "失败", stats.error, Color(0xFF757575))
        )
    }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            FilterButton(
                label = option.label,
                count = option.count,
                selected = selectedFilter == option.filter,
                color = option.color,
                onClick = { onFilterClick(option.filter) }
            )
        }
    }
}

@Composable
private fun FilterButton(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) color.copy(alpha = 0.12f) else Color.Transparent,
            contentColor = if (selected) color else color.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = "$label · $count",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun LogList(
    logs: LazyPagingItems<DnsLogEntity>,
    listState: LazyListState,
    onLogLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                count = logs.itemCount,
                contentType = logs.itemContentType { "DnsLogEntity" }
            ) { index ->
                logs[index]?.let { item ->
                    LogItem(
                        log = item,
                        onLongClick = onLogLongClick
                    )
                }
            }

            if (logs.loadState.append is LoadState.Loading) {
                item(
                    key = "append_loading",
                    contentType = "append_loading"
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }

        when {
            logs.loadState.refresh is LoadState.Loading && logs.itemCount == 0 -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            logs.loadState.refresh is LoadState.NotLoading && logs.itemCount == 0 -> {
                EmptyLogMessage()
            }
        }
    }
}

@Composable
private fun EmptyLogMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "暂无日志",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogItem(
    log: DnsLogEntity,
    onLongClick: (String) -> Unit
) {
    val (label, color) = remember(log.result) {
        when (log.result) {
            LogResult.PASSED.value -> "通过" to Color(0xFF2E7D32)
            LogResult.BLOCKED.value -> "屏蔽" to Color(0xFFC62828)
            else -> "失败" to Color(0xFF757575)
        }
    }
    val typeName = remember(log.queryType) { dnsTypeName(log.queryType) }
    val formattedTime = remember(log.timestamp) { timeFormatter.format(Date(log.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongClick(log.queryName) }
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.queryName,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                if (log.cached) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1565C0).copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "命中缓存",
                            color = Color(0xFF1565C0),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = label,
                    color = color,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = " · $typeName",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            log.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AddRuleFromLogDialog(
    domain: String,
    onDismiss: () -> Unit,
    onCopyDomain: () -> Unit,
    onAddBlockRule: () -> Unit,
    onAddAllowRule: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("处理域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("选择要对 \"$domain\" 执行的操作。")
                OutlinedButton(
                    onClick = onCopyDomain,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制域名")
                }
                OutlinedButton(
                    onClick = onAddAllowRule,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("加入白名单规则")
                }
                OutlinedButton(
                    onClick = onAddBlockRule,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("加入屏蔽规则")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        dismissButton = {}
    )
}

private fun dnsTypeName(type: Int): String {
    return when (type) {
        1 -> "A"
        28 -> "AAAA"
        5 -> "CNAME"
        15 -> "MX"
        16 -> "TXT"
        2 -> "NS"
        12 -> "PTR"
        255 -> "ANY"
        else -> "TYPE$type"
    }
}
