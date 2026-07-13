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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import com.haoze.dnssr.vpn.DnsLatencyTester
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.ProviderHealthSnapshot
import com.haoze.dnssr.vpn.ProviderHealthStore
import java.util.Locale

@Composable
fun RaceModeProviderSettingsScreen(
    onBack: () -> Unit,
    title: String = "竞速模式",
    onRuntimeDnsSettingsChanged: () -> Unit,
    viewModel: RaceModeSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val healthByProvider by viewModel.healthByProvider.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val raceModeEnabled by viewModel.raceModeEnabled.collectAsStateWithLifecycle()
    val raceModeStrategy by viewModel.raceModeStrategy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val initialLoading by viewModel.initialLoading.collectAsStateWithLifecycle()
    var selectedProtocol by remember { mutableStateOf(DnsProtocol.DOH) }
    var initializedProtocolFilter by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        delay(300) // 等待页面进入动画完成后再加载数据
        viewModel.activate()
    }

    LaunchedEffect(initialLoading, providers, selectedIds) {
        if (!initialLoading && providers.isNotEmpty() && !initializedProtocolFilter) {
            providers.firstOrNull { it.id in selectedIds }?.protocol?.let {
                selectedProtocol = it
            }
            initializedProtocolFilter = true
        }
    }

    LaunchedEffect(providers) {
        val protocols = availableProtocols(providers)
        if (selectedProtocol !in protocols) {
            selectedProtocol = protocols.firstOrNull() ?: DnsProtocol.DOH
        }
    }

    SettingsScaffold(
        title = title,
        onBack = onBack,
        titleTrailing = {
            val actionColor = if (raceModeEnabled) {
                MaterialTheme.colorScheme.error
            } else {
                Color(0xFF2E7D32)
            }
            Button(
                onClick = {
                    if (viewModel.setRaceModeEnabled(!raceModeEnabled)) {
                        onRuntimeDnsSettingsChanged()
                    }
                },
                enabled = raceModeEnabled || selectedIds.size >= 2,
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionColor,
                    contentColor = Color.White,
                    disabledContainerColor = actionColor.copy(alpha = 0.38f),
                    disabledContentColor = Color.White.copy(alpha = 0.74f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(text = if (raceModeEnabled) "关闭" else "开启")
            }
        }
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
                    val protocols = availableProtocols(providers)
                    ProtocolToggleRow(
                        selectedProtocol = selectedProtocol,
                        onSelect = { selectedProtocol = it },
                        protocols = protocols,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
                    )
                }
                item {
                    SettingsGroupTitle("参与竞速的 DNS 服务商")
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
                        selectedIds.size < 2 -> "至少选择 2 个服务商后，才能启用竞速模式。"
                        raceModeStrategy == RaceModeStrategy.BRUTE_FORCE_PARALLEL -> {
                            "暴力并行会并发请求已选择的 ${selectedIds.size} 个服务商，并使用最快成功结果。"
                        }
                        else -> "智慧预测会使用下方健康权重，在已选择的 ${selectedIds.size} 个服务商之间分配查询流量。"
                    }
                    SettingsInfoText(hint)
                }

                item {
                    SettingsGroupTitle("竞速模式")
                }
                item {
                    SettingsGroup {
                        RaceModeStrategy.values().forEachIndexed { index, strategy ->
                            SettingsRadioItem(
                                title = strategy.displayName,
                                subtitle = when (strategy) {
                                    RaceModeStrategy.BRUTE_FORCE_PARALLEL -> "原有模式：同时查询全部服务商，采用最快成功响应"
                                    RaceModeStrategy.SMART_PREDICTION -> "使用服务商健康权重，优先查询更稳定、更快的服务商"
                                },
                                selected = strategy == raceModeStrategy,
                                onClick = {
                                    if (viewModel.setRaceModeStrategy(strategy)) {
                                        onRuntimeDnsSettingsChanged()
                                    }
                                }
                            )
                            if (index < RaceModeStrategy.values().lastIndex) {
                                SettingsDivider()
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
    title: String = "DNS 查询测速",
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
    var selectedProtocol by remember { mutableStateOf(DnsProtocol.DOH) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        delay(300) // 等待页面进入动画完成后再加载数据
        viewModel.activate()
    }

    LaunchedEffect(providers) {
        val protocols = availableProtocols(providers)
        if (selectedProtocol !in protocols) {
            selectedProtocol = protocols.firstOrNull() ?: DnsProtocol.DOH
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
        Text(
            text = if (result.success) {
                buildString {
                    append("平均 ${result.elapsedMs} ms")
                    append(" · ${result.successCount}/${result.attempts} 次成功")
                    if (result.elapsedSamplesMs.size > 1) {
                        append(" · 样本 ")
                        append(result.elapsedSamplesMs.joinToString(" / ") { "$it ms" })
                    }
                }
            } else {
                "全部失败（${result.attempts} 次）${result.message?.let { " · $it" } ?: ""}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
