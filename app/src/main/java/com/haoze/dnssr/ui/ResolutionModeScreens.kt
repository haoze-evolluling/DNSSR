package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.DnsProtocolBadge
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.DnsProtocol
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val presetDnsService by viewModel.presetDnsService.collectAsStateWithLifecycle()
    val loading by viewModel.initialLoading.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showModeDialog by remember { mutableStateOf(false) }
    var showPresetDnsServiceDialog by remember { mutableStateOf(false) }
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

    if (showPresetDnsServiceDialog) {
        PresetDnsServicePickerDialog(
            selectedService = presetDnsService,
            onSelect = { service ->
                showPresetDnsServiceDialog = false
                viewModel.setPresetDnsService(service)
            },
            onDismiss = { showPresetDnsServiceDialog = false }
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
                            DnsResolutionMode.PRIMARY_BACKUP -> "${backupIds.size} 个服务商"
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
            item { SettingsGroupTitle("预设 DNS 服务") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "预设 DNS 服务",
                        subtitle = "切换阿里云和腾讯 DNSPod 的预设服务协议",
                        value = presetDnsService.displayName,
                        onClick = { showPresetDnsServiceDialog = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetDnsServicePickerDialog(
    selectedService: PresetDnsService,
    onSelect: (PresetDnsService) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择预设 DNS 服务") },
        text = {
            Column {
                PresetDnsService.entries.forEachIndexed { index, service ->
                    SettingsRadioItem(
                        title = service.displayName,
                        selected = selectedService == service,
                        onClick = { onSelect(service) }
                    )
                    if (index < PresetDnsService.entries.lastIndex) SettingsDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
    val context = LocalContext.current
    val smartIds by viewModel.smartPredictionIds.collectAsStateWithLifecycle()
    val parallelIds by viewModel.parallelRaceIds.collectAsStateWithLifecycle()
    val backupIds by viewModel.primaryBackupIds.collectAsStateWithLifecycle()
    val singleId by viewModel.singleProviderId.collectAsStateWithLifecycle()
    val loading by viewModel.initialLoading.collectAsStateWithLifecycle()
    var protocol by remember { mutableStateOf(DnsProtocol.DNS) }
    val listState = rememberLazyListState()
    var listViewportBounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) { viewModel.activate() }
    val selected = when (mode) {
        DnsResolutionMode.SMART_PREDICTION -> smartIds
        DnsResolutionMode.PARALLEL_RACE -> parallelIds
        DnsResolutionMode.PRIMARY_BACKUP -> backupIds.toSet()
        DnsResolutionMode.SINGLE -> setOf(singleId)
    }

    fun applyProviderSelection(provider: DnsProvider) {
        if (mode == DnsResolutionMode.SINGLE) {
            viewModel.selectSingleProvider(provider.id)
        } else {
            viewModel.toggleModeProvider(mode, provider.id)
        }
    }

    fun handleProviderSelection(provider: DnsProvider) {
        if (mode == DnsResolutionMode.SINGLE && provider.id == singleId) return
        if (mode != DnsResolutionMode.SINGLE && provider.id in selected) {
            applyProviderSelection(provider)
        } else {
            applyProviderSelection(provider)
        }
    }

    SettingsScaffold(title = mode.displayName, onBack = onBack) { padding ->
        if (loading) return@SettingsScaffold SettingsLoadingContent(Modifier.padding(padding))
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .onGloballyPositioned { listViewportBounds = it.boundsInWindow() }
        ) {
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
                            SettingsRadioItem(provider.name, provider.id == singleId, { handleProviderSelection(provider) }, subtitle = provider.endpointLabel())
                        } else {
                            SettingsCheckboxItem(provider.name, provider.id in selected, { handleProviderSelection(provider) }, subtitle = provider.endpointLabel())
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
                        providersById = providers.associateBy { it.id },
                        listState = listState,
                        listViewportBounds = listViewportBounds,
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
    providersById: Map<String, DnsProvider>,
    listState: LazyListState,
    listViewportBounds: Rect?,
    onReorder: (String, Int) -> Unit
) {
    var orderedIds by remember(backupIds) { mutableStateOf(backupIds) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var settlingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(0) }
    var targetIndex by remember { mutableIntStateOf(0) }
    var draggedCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val latestBackupOrder = rememberUpdatedState(backupIds)
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val settleOffset = remember { Animatable(0f) }
    val reorderThresholdPx = with(density) { 40.dp.toPx() }
    val edgeOverscrollPx = with(density) { 20.dp.toPx() }
    val autoScrollEdgePx = with(density) { 72.dp.toPx() }
    val maxAutoScrollPxPerSecond = with(density) { 720.dp.toPx() }
    val rowHeight = 48.dp
    val dividerHeight = 1.dp
    val itemHeight = rowHeight + dividerHeight
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val liftedShape = RoundedCornerShape(6.dp)
    val rowColor = MaterialTheme.colorScheme.surfaceContainer

    fun updateDraggedPosition(providerId: String) {
        while (true) {
            val current = orderedIds
            val from = current.indexOf(providerId)
            if (from < 0) return

            if (from == 0 && dragOffsetY < -edgeOverscrollPx) {
                dragOffsetY = -edgeOverscrollPx
            }
            if (from == current.lastIndex && dragOffsetY > edgeOverscrollPx) {
                dragOffsetY = edgeOverscrollPx
            }

            val direction = when {
                dragOffsetY <= -reorderThresholdPx -> -1
                dragOffsetY >= reorderThresholdPx -> 1
                else -> return
            }
            val to = (from + direction).coerceIn(current.indices)
            if (from == to) return

            orderedIds = current.toMutableList().apply {
                add(to, removeAt(from))
            }
            targetIndex = to
            dragOffsetY -= direction * itemHeightPx
        }
    }

    fun settleDraggedItem(providerId: String) {
        settleJob?.cancel()
        settleJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            settleOffset.snapTo(dragOffsetY)
            settlingId = providerId
            draggedId = null
            draggedCoordinates = null
            settleOffset.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            if (settlingId == providerId) {
                dragOffsetY = 0f
                settlingId = null
            }
        }
    }

    LaunchedEffect(draggedId, listViewportBounds) {
        val providerId = draggedId ?: return@LaunchedEffect
        val viewport = listViewportBounds ?: return@LaunchedEffect
        var previousFrameNanos = withFrameNanos { it }

        while (draggedId == providerId) {
            val frameNanos = withFrameNanos { it }
            val frameSeconds = ((frameNanos - previousFrameNanos) / 1_000_000_000f)
                .coerceAtMost(0.05f)
            previousFrameNanos = frameNanos

            val coordinates = draggedCoordinates
            if (coordinates?.isAttached != true) continue
            val centerY = coordinates.boundsInWindow().center.y
            val topDistance = centerY - viewport.top
            val bottomDistance = viewport.bottom - centerY
            val scrollVelocity = when {
                topDistance < autoScrollEdgePx && listState.canScrollBackward -> {
                    -maxAutoScrollPxPerSecond *
                        (1f - topDistance / autoScrollEdgePx).coerceIn(0f, 1f)
                }
                bottomDistance < autoScrollEdgePx && listState.canScrollForward -> {
                    maxAutoScrollPxPerSecond *
                        (1f - bottomDistance / autoScrollEdgePx).coerceIn(0f, 1f)
                }
                else -> 0f
            }
            if (scrollVelocity == 0f) continue

            val consumedScroll = listState.scrollBy(scrollVelocity * frameSeconds)
            if (consumedScroll != 0f) {
                dragOffsetY += consumedScroll
                updateDraggedPosition(providerId)
            }
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(itemHeight * orderedIds.size - dividerHeight)
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            shape = SettingsCornerShape,
            colors = CardDefaults.cardColors(containerColor = rowColor)
        ) {}

        orderedIds.forEachIndexed { index, providerId ->
            val provider = providersById[providerId] ?: return@forEachIndexed
            key(providerId) {
                var rowCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                val isDragging = draggedId == providerId
                val isSettling = settlingId == providerId
                val isRaised = isDragging || isSettling
                val baseOffset by animateDpAsState(
                    targetValue = itemHeight * index,
                    animationSpec = if (isRaised) snap() else tween(durationMillis = 160),
                    label = "primaryBackupItemPlacement"
                )
                val liftedScale by animateFloatAsState(
                    targetValue = if (isRaised) 1.02f else 1f,
                    animationSpec = tween(durationMillis = 120),
                    label = "primaryBackupLiftScale"
                )
                val displayIndex = if (isRaised) dragStartIndex else index
                val accessibilityActions = buildList {
                    if (index > 0) {
                        add(CustomAccessibilityAction("提高优先级") {
                            onReorder(providerId, index - 1)
                            true
                        })
                    }
                    if (index < orderedIds.lastIndex) {
                        add(CustomAccessibilityAction("降低优先级") {
                            onReorder(providerId, index + 1)
                            true
                        })
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .offset(y = baseOffset)
                        .zIndex(if (isRaised) 1f else 0f)
                        .graphicsLayer {
                            translationY = when {
                                isDragging -> dragOffsetY
                                isSettling -> settleOffset.value
                                else -> 0f
                            }
                            scaleX = liftedScale
                            scaleY = liftedScale
                            shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                            shape = liftedShape
                        }
                        .onGloballyPositioned {
                            rowCoordinates = it
                            if (isDragging) draggedCoordinates = it
                        }
                        .background(
                            color = if (isRaised) rowColor else Color.Transparent,
                            shape = liftedShape
                        )
                        .semantics(mergeDescendants = true) {
                            customActions = accessibilityActions
                        }
                        .padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (displayIndex == 0) {
                            "主"
                        } else {
                            "备 $displayIndex"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DnsProtocolBadge(
                        protocol = provider.protocol,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .pointerInput(providerId, itemHeightPx, backupIds) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        settleJob?.cancel()
                                        settlingId = null
                                        draggedId = providerId
                                        draggedCoordinates = rowCoordinates
                                        dragStartIndex = orderedIds.indexOf(providerId)
                                        targetIndex = dragStartIndex
                                        dragOffsetY = 0f
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onDragCancel = {
                                        val currentIndex = orderedIds.indexOf(providerId)
                                        val backupOrder = latestBackupOrder.value
                                        val originalIndex = backupOrder.indexOf(providerId)
                                        if (currentIndex >= 0 && originalIndex >= 0) {
                                            dragOffsetY +=
                                                (currentIndex - originalIndex) * itemHeightPx
                                            targetIndex = originalIndex
                                        }
                                        orderedIds = backupOrder
                                        settleDraggedItem(providerId)
                                    },
                                    onDragEnd = {
                                        onReorder(providerId, targetIndex)
                                        settleDraggedItem(providerId)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        updateDraggedPosition(providerId)
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "长按并拖动调整顺序",
                            tint = if (isRaised) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
        repeat(orderedIds.lastIndex) { index ->
            SettingsDivider(Modifier.offset(y = itemHeight * index + rowHeight))
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
