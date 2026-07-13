package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.SettingsCheckboxItem
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsLoadingContent
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.vpn.DnsProtocol

@Composable
fun ResolutionModeHomeScreen(
    onBack: () -> Unit,
    onOpenMode: (DnsResolutionMode) -> Unit,
    viewModel: RaceModeSettingsViewModel = viewModel()
) {
    val mode by viewModel.resolutionMode.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val smartIds by viewModel.smartPredictionIds.collectAsStateWithLifecycle()
    val parallelIds by viewModel.parallelRaceIds.collectAsStateWithLifecycle()
    val backupIds by viewModel.primaryBackupIds.collectAsStateWithLifecycle()
    val singleId by viewModel.singleProviderId.collectAsStateWithLifecycle()
    val loading by viewModel.initialLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showModeDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.activate() }
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessage() } }

    if (showModeDialog) {
        ResolutionModePickerDialog(
            selectedMode = mode,
            onSelect = { selectedMode ->
                showModeDialog = false
                if (!viewModel.setResolutionMode(selectedMode) && mode != selectedMode) {
                    onOpenMode(selectedMode)
                }
            },
            onDismiss = { showModeDialog = false }
        )
    }

    SettingsScaffold(title = "解析模式", onBack = onBack) { padding ->
        if (loading) return@SettingsScaffold SettingsLoadingContent(Modifier.padding(padding))
        LazyColumn(modifier = Modifier.padding(padding)) {
            item { SettingsGroupTitle("当前模式") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "解析模式",
                        subtitle = subtitleFor(mode),
                        value = mode.displayName,
                        onClick = { showModeDialog = true }
                    )
                }
            }
            item { SettingsGroupTitle("模式配置") }
            item {
                SettingsGroup {
                    DnsResolutionMode.entries.forEachIndexed { index, itemMode ->
                        val summary = when (itemMode) {
                            DnsResolutionMode.SINGLE -> providers.firstOrNull { it.id == singleId }?.name ?: "未配置"
                            DnsResolutionMode.SMART_PREDICTION -> "${smartIds.size} 个服务商"
                            DnsResolutionMode.PARALLEL_RACE -> "${parallelIds.size} 个服务商"
                            DnsResolutionMode.PRIMARY_BACKUP -> "${backupIds.size} 个主备服务"
                        }
                        SettingsNavigationItem(
                            title = itemMode.displayName,
                            subtitle = subtitleFor(itemMode),
                            value = summary,
                            onClick = { onOpenMode(itemMode) }
                        )
                        if (index < DnsResolutionMode.entries.lastIndex) SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ResolutionModePickerDialog(
    selectedMode: DnsResolutionMode,
    onSelect: (DnsResolutionMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择解析模式") },
        text = {
            Column {
                DnsResolutionMode.entries.forEachIndexed { index, mode ->
                    SettingsRadioItem(
                        title = mode.displayName,
                        subtitle = subtitleFor(mode),
                        selected = selectedMode == mode,
                        onClick = { onSelect(mode) }
                    )
                    if (index < DnsResolutionMode.entries.lastIndex) SettingsDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun ResolutionModeConfigScreen(
    mode: DnsResolutionMode,
    onBack: () -> Unit,
    viewModel: RaceModeSettingsViewModel = viewModel()
) {
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val smartIds by viewModel.smartPredictionIds.collectAsStateWithLifecycle()
    val parallelIds by viewModel.parallelRaceIds.collectAsStateWithLifecycle()
    val backupIds by viewModel.primaryBackupIds.collectAsStateWithLifecycle()
    val singleId by viewModel.singleProviderId.collectAsStateWithLifecycle()
    val loading by viewModel.initialLoading.collectAsStateWithLifecycle()
    var protocol by remember { mutableStateOf(DnsProtocol.DOH) }
    LaunchedEffect(Unit) { viewModel.activate() }
    val selected = when (mode) {
        DnsResolutionMode.SMART_PREDICTION -> smartIds
        DnsResolutionMode.PARALLEL_RACE -> parallelIds
        DnsResolutionMode.PRIMARY_BACKUP -> backupIds.toSet()
        DnsResolutionMode.SINGLE -> setOf(singleId)
    }
    SettingsScaffold(title = mode.displayName, onBack = onBack) { padding ->
        if (loading) return@SettingsScaffold SettingsLoadingContent(Modifier.padding(padding))
        LazyColumn(modifier = Modifier.padding(padding)) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DnsProtocol.MANAGED_PROTOCOLS.filter { p -> providers.any { it.protocol == p } }.forEach { p ->
                        FilterChip(selected = protocol == p, onClick = { protocol = p }, label = { Text(p.label) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            item { SettingsGroupTitle(if (mode == DnsResolutionMode.SINGLE) "DNS 服务商" else "参与服务商") }
            item {
                SettingsGroup {
                    providers.filter { it.protocol == protocol }.forEachIndexed { index, provider ->
                        if (mode == DnsResolutionMode.SINGLE) {
                            SettingsRadioItem(provider.name, provider.id == singleId, { viewModel.selectSingleProvider(provider.id) }, subtitle = provider.endpointLabel())
                        } else {
                            SettingsCheckboxItem(provider.name, provider.id in selected, { viewModel.toggleModeProvider(mode, provider.id) }, subtitle = provider.endpointLabel())
                        }
                        if (index < providers.count { it.protocol == protocol } - 1) SettingsDivider()
                    }
                }
            }
            item { SettingsInfoText(descriptionFor(mode, selected.size)) }
            if (mode == DnsResolutionMode.PRIMARY_BACKUP && backupIds.isNotEmpty()) {
                item { SettingsGroupTitle("主备顺序") }
                item {
                    PrimaryBackupOrderGroup(
                        backupIds = backupIds,
                        providerNames = providers.associate { it.id to it.name },
                        onReorder = viewModel::reorderPrimaryBackupProvider
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimaryBackupOrderGroup(
    backupIds: List<String>,
    providerNames: Map<String, String>,
    onReorder: (String, Int) -> Unit
) {
    var orderedIds by remember(backupIds) { mutableStateOf(backupIds) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val latestOrder = rememberUpdatedState(orderedIds)
    val reorderThresholdPx = with(LocalDensity.current) { 40.dp.toPx() }

    SettingsGroup {
        orderedIds.forEachIndexed { index, providerId ->
            val providerName = providerNames[providerId] ?: return@forEachIndexed
            key(providerId) {
                var dragDistance by remember { mutableFloatStateOf(0f) }
                var targetIndex by remember { mutableIntStateOf(index) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (index == 0) "主 · $providerName" else "备 $index · $providerName",
                        Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(providerId) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedId = providerId
                                        targetIndex = latestOrder.value.indexOf(providerId)
                                        dragDistance = 0f
                                    },
                                    onDragCancel = {
                                        orderedIds = backupIds
                                        draggedId = null
                                        dragDistance = 0f
                                    },
                                    onDragEnd = {
                                        onReorder(providerId, targetIndex)
                                        draggedId = null
                                        dragDistance = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragDistance += dragAmount.y
                                        val direction = when {
                                            dragDistance <= -reorderThresholdPx -> -1
                                            dragDistance >= reorderThresholdPx -> 1
                                            else -> 0
                                        }
                                        if (direction != 0) {
                                            val current = latestOrder.value
                                            val from = current.indexOf(providerId)
                                            val to = (from + direction).coerceIn(current.indices)
                                            if (from >= 0 && from != to) {
                                                orderedIds = current.toMutableList().apply {
                                                    add(to, removeAt(from))
                                                }
                                                targetIndex = to
                                            }
                                            dragDistance = 0f
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "长按并拖动调整顺序",
                            tint = if (draggedId == providerId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                if (index < orderedIds.lastIndex) SettingsDivider()
            }
        }
    }
}

private fun subtitleFor(mode: DnsResolutionMode) = when (mode) {
    DnsResolutionMode.SINGLE -> "固定使用一个 DNS 服务商"
    DnsResolutionMode.SMART_PREDICTION -> "根据健康权重选择服务商"
    DnsResolutionMode.PARALLEL_RACE -> "并发查询并采用最快响应"
    DnsResolutionMode.PRIMARY_BACKUP -> "失败时按优先级切换备用服务"
}

private fun descriptionFor(mode: DnsResolutionMode, count: Int) = when (mode) {
    DnsResolutionMode.SINGLE -> "此选择与首页当前 DNS 服务商保持一致。"
    DnsResolutionMode.SMART_PREDICTION -> "已选择 $count 个服务商。至少选择 2 个；运行时会根据成功率和延迟形成健康权重。"
    DnsResolutionMode.PARALLEL_RACE -> "已选择 $count 个服务商。至少选择 2 个；查询会并发发送并采用最快成功响应。"
    DnsResolutionMode.PRIMARY_BACKUP -> "已选择 $count 个服务商。至少需要 1 个主服务和 1 个备用服务。"
}
