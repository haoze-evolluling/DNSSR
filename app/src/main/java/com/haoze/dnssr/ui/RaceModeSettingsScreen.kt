package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.SettingsCheckboxItem
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsLoadingContent
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import com.haoze.dnssr.ui.components.DnsProtocolBadge
import com.haoze.dnssr.vpn.DnsLatencyTester
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.ProviderHealthSnapshot
import com.haoze.dnssr.vpn.ProviderHealthStore
import java.util.Locale

@Composable
private fun LegacyRaceModeProviderSettingsScreen(
    onBack: () -> Unit,
    title: String = "解析模式",
    onRuntimeDnsSettingsChanged: () -> Unit,
    viewModel: RaceModeSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val healthByProvider by viewModel.healthByProvider.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val raceModeEnabled by viewModel.raceModeEnabled.collectAsStateWithLifecycle()
    val raceModeStrategy by viewModel.raceModeStrategy.collectAsStateWithLifecycle()
    val resolutionMode by viewModel.resolutionMode.collectAsStateWithLifecycle()
    val primaryBackupIds by viewModel.primaryBackupIds.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val initialLoading by viewModel.initialLoading.collectAsStateWithLifecycle()
    var selectedProtocol by remember { mutableStateOf(DnsProtocol.DNS) }
    val normalizedWeights = ProviderHealthStore.normalizeWeightsToPercent(
        providers
            .filter { it.id in selectedIds }
            .map { provider ->
                provider.id to (healthByProvider[provider.id]?.predictionWeight ?: 1.0)
            }
    )

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    NavigationSettledEffect {
        viewModel.activate()
    }

    LaunchedEffect(providers) {
        val protocols = availableProtocols(providers)
        if (selectedProtocol !in protocols) {
            selectedProtocol = protocols.firstOrNull() ?: DnsProtocol.DNS
        }
    }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        if (initialLoading) {
            SettingsLoadingContent(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    SettingsGroupTitle("解析模式")
                }
                item {
                    SettingsGroup {
                        DnsResolutionMode.entries.forEachIndexed { index, mode ->
                            SettingsRadioItem(
                                title = mode.displayName,
                                subtitle = when (mode) {
                                    DnsResolutionMode.SINGLE -> "仅使用首页当前选择的 DNS 服务商"
                                    DnsResolutionMode.SMART_PREDICTION -> "按健康权重优先选择稳定、快速的服务商"
                                    DnsResolutionMode.PARALLEL_RACE -> "同时查询全部服务商，采用最快成功响应"
                                    DnsResolutionMode.PRIMARY_BACKUP -> "主服务失败后，按优先级依次尝试备用服务"
                                },
                                selected = resolutionMode == mode,
                                onClick = {
                                    if (viewModel.setResolutionMode(mode)) onRuntimeDnsSettingsChanged()
                                }
                            )
                            if (index < DnsResolutionMode.entries.lastIndex) SettingsDivider()
                        }
                    }
                }
                if (resolutionMode == DnsResolutionMode.SINGLE) {
                    item { SettingsInfoText("一脉直达模式使用首页“解析服务”下拉框中的当前选择。") }
                }
                item {
                    val protocols = availableProtocols(providers)
                    ProtocolToggleRow(
                        selectedProtocol = selectedProtocol,
                        onSelect = { selectedProtocol = it },
                        protocols = protocols,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                }
                item {
                    SettingsGroupTitle("参与当前模式的 DNS 服务商")
                }
                item {
                    val visibleProviders = providers.filter { it.protocol == selectedProtocol }
                    if (visibleProviders.isEmpty()) {
                        SettingsInfoText("暂无 ${selectedProtocol.label} DNS 服务商。")
                    } else {
                        SettingsGroup {
                            visibleProviders.forEachIndexed { index, provider ->
                                SettingsCheckboxItem(
                                    title = provider.name,
                                    subtitle = providerRaceSubtitle(
                                        provider = provider,
                                        health = healthByProvider[provider.id],
                                        normalizedWeightPercent = normalizedWeights[provider.id],
                                        selected = provider.id in selectedIds
                                    ),
                                    checked = provider.id in selectedIds,
                                    onCheckedChange = { viewModel.toggleProvider(provider.id) }
                                )
                                if (index < visibleProviders.lastIndex) {
                                    SettingsDivider()
                                }
                            }
                        }
                    }
                }
                item {
                    val hint = when {
                        selectedIds.size < 2 -> "多服务商模式至少需要选择 2 个服务商。"
                        resolutionMode == DnsResolutionMode.PARALLEL_RACE -> "并行查询已选择的 ${selectedIds.size} 个服务商。"
                        resolutionMode == DnsResolutionMode.SMART_PREDICTION -> "按健康权重在 ${selectedIds.size} 个服务商之间分配查询。"
                        resolutionMode == DnsResolutionMode.PRIMARY_BACKUP -> "按下方优先级依次查询，成功后停止。"
                        else -> "这些服务商用于切换到多服务商模式。"
                    }
                    SettingsInfoText(hint)
                }
                if (resolutionMode == DnsResolutionMode.PRIMARY_BACKUP) {
                    item { SettingsGroupTitle("主备优先级") }
                    item {
                        SettingsGroup {
                            primaryBackupIds.mapNotNull { id -> providers.firstOrNull { it.id == id } }
                                .forEachIndexed { index, provider ->
                                    var dragDistance by remember(provider.id) { mutableFloatStateOf(0f) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .draggable(
                                                orientation = Orientation.Vertical,
                                                state = rememberDraggableState { delta ->
                                                    dragDistance += delta
                                                    when {
                                                        dragDistance <= -40f -> {
                                                            viewModel.movePrimaryBackupProvider(provider.id, -1)
                                                            dragDistance = 0f
                                                        }
                                                        dragDistance >= 40f -> {
                                                            viewModel.movePrimaryBackupProvider(provider.id, 1)
                                                            dragDistance = 0f
                                                        }
                                                    }
                                                }
                                            )
                                            .padding(start = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DragHandle, contentDescription = "拖动调整优先级")
                                        Text(
                                            text = if (index == 0) "主 · ${provider.name}" else "备 $index · ${provider.name}",
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.movePrimaryBackupProvider(provider.id, -1) },
                                            enabled = index > 0
                                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "提高优先级") }
                                        IconButton(
                                            onClick = { viewModel.movePrimaryBackupProvider(provider.id, 1) },
                                            enabled = index < primaryBackupIds.lastIndex
                                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "降低优先级") }
                                    }
                                    if (index < primaryBackupIds.lastIndex) SettingsDivider()
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolToggleRow(
    selectedProtocol: DnsProtocol,
    onSelect: (DnsProtocol) -> Unit,
    protocols: List<DnsProtocol>,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        protocols.chunked(2).forEach { protocolRow ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                protocolRow.forEach { option ->
                    FilterChip(
                        selected = selectedProtocol == option,
                        onClick = { onSelect(option) },
                        label = {
                            Text(
                                text = option.label,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun availableProtocols(providers: List<DnsProvider>): List<DnsProtocol> {
    val present = providers.map { it.protocol }.toSet()
    return DnsProtocol.MANAGED_PROTOCOLS.filter { it in present }
        .ifEmpty { DnsProtocol.MANAGED_PROTOCOLS }
}

private fun providerRaceSubtitle(
    provider: DnsProvider,
    health: ProviderHealthSnapshot?,
    normalizedWeightPercent: Int?,
    selected: Boolean
): String {
    val healthText = if (health == null || health.attempts == 0) {
        "暂无运行健康数据"
    } else {
        "准确率 ${formatHealthPercent(health.accuracy)} · 延迟 ${formatHealthMs(health.ewmaMs)} · 样本 ${health.attempts}"
    }
    val weightText = when {
        selected && normalizedWeightPercent != null -> "健康权重 $normalizedWeightPercent%"
        selected -> "健康权重待计算"
        else -> "未参与竞速"
    }
    return "${provider.endpointLabel()}\n$healthText · $weightText"
}

private fun formatHealthPercent(value: Double): String {
    return String.format(Locale.getDefault(), "%.1f%%", value * 100.0)
}

private fun formatHealthMs(value: Double): String {
    return "${value.toInt()} ms"
}

@Composable
fun RaceModeLatencySettingsScreen(
    onBack: () -> Unit,
    title: String = "查询测速",
    viewModel: RaceModeSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedIds by viewModel.latencyTestSelectedIds.collectAsStateWithLifecycle()
    val testDomain by viewModel.testDomain.collectAsStateWithLifecycle()
    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val initialLoading by viewModel.initialLoading.collectAsStateWithLifecycle()
    var selectedProtocol by remember { mutableStateOf(DnsProtocol.DNS) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    NavigationSettledEffect {
        viewModel.activate()
    }

    LaunchedEffect(providers) {
        val protocols = availableProtocols(providers)
        if (selectedProtocol !in protocols) {
            selectedProtocol = protocols.firstOrNull() ?: DnsProtocol.DNS
        }
    }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        if (initialLoading) {
            SettingsLoadingContent(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    SettingsGroupTitle("测速域名")
                }
                item {
                    SettingsGroup {
                        OutlinedTextField(
                            value = testDomain,
                            onValueChange = { value ->
                                viewModel.setTestDomain(value.filter { !it.isWhitespace() })
                            },
                            label = { Text("用于测速的域名") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Done
                            ),
                            shape = SettingsCornerShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
                item {
                    SettingsInfoText("会向已选择的测速服务商查询这个域名，每个服务商连续测 3 次并按成功样本取平均值。")
                }

                item {
                    val protocols = availableProtocols(providers)
                    ProtocolToggleRow(
                        selectedProtocol = selectedProtocol,
                        onSelect = { selectedProtocol = it },
                        protocols = protocols,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                }
                item {
                    SettingsGroupTitle("测速服务商")
                }
                item {
                    val visibleProviders = providers.filter { it.protocol == selectedProtocol }
                    if (visibleProviders.isEmpty()) {
                        SettingsInfoText("暂无 ${selectedProtocol.label} DNS 服务商。")
                    } else {
                        SettingsGroup {
                            visibleProviders.forEachIndexed { index, provider ->
                                SettingsCheckboxItem(
                                    title = provider.name,
                                    subtitle = provider.endpointLabel(),
                                    checked = provider.id in selectedIds,
                                    onCheckedChange = { viewModel.toggleLatencyTestProvider(provider.id) }
                                )
                                if (index < visibleProviders.lastIndex) {
                                    SettingsDivider()
                                }
                            }
                        }
                    }
                }
                item {
                    SettingsInfoText("可选择 1 个或多个服务商；这里的选择只用于 DNS 查询测速，不影响竞速模式。")
                }

                item {
                    SettingsGroupTitle("查询耗时结果")
                }
                item {
                    SettingsGroup {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.runLatencyTest() },
                                enabled = !isTesting && selectedIds.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = if (isTesting) "测速中..." else "测试查询耗时")
                            }

                            results.forEach { result ->
                                LatencyResultItem(result = result)
                            }
                        }
                    }
                }
                item {
                    SettingsInfoText("测速结果按成功优先、平均耗时从低到高排序；结果只反映当前网络状态。")
                }
            }
        }
    }
}

@Composable
private fun LatencyResultItem(result: DnsLatencyTester.Result) {
    val color = if (result.success) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.providerName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            DnsProtocolBadge(protocol = result.protocol)
        }
        if (result.success) {
            Text(
                text = buildString {
                    append("平均 ${result.elapsedMs} ms")
                    append(" · ${result.successCount}/${result.attempts} 次成功")
                    if (result.elapsedSamplesMs.size > 1) {
                        append(" · 样本 ")
                        append(result.elapsedSamplesMs.joinToString(" / ") { "$it ms" })
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        } else {
            Text(
                text = "全部失败（${result.attempts} 次）",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
            result.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = color
                )
            }
        }
    }
}
