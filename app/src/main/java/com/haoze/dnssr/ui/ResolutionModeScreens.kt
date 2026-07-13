package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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
    LaunchedEffect(Unit) { viewModel.activate() }
    LaunchedEffect(message) { message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearMessage() } }

    SettingsScaffold(title = "解析模式", onBack = onBack) { padding ->
        if (loading) return@SettingsScaffold SettingsLoadingContent(Modifier.padding(padding))
        LazyColumn(modifier = Modifier.padding(padding)) {
            item { SettingsGroupTitle("当前模式") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DnsResolutionMode.entries.forEach { candidate ->
                        FilterChip(
                            selected = mode == candidate,
                            onClick = { if (!viewModel.setResolutionMode(candidate) && mode != candidate) onOpenMode(candidate) },
                            label = { Text(candidate.displayName, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
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
                    SettingsGroup {
                        backupIds.mapNotNull { id -> providers.firstOrNull { it.id == id } }.forEachIndexed { index, provider ->
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (index == 0) "主 · ${provider.name}" else "备 $index · ${provider.name}", Modifier.weight(1f))
                                IconButton({ viewModel.movePrimaryBackupProvider(provider.id, -1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "提高优先级") }
                                IconButton({ viewModel.movePrimaryBackupProvider(provider.id, 1) }, enabled = index < backupIds.lastIndex) { Icon(Icons.Default.ArrowDownward, "降低优先级") }
                            }
                            if (index < backupIds.lastIndex) SettingsDivider()
                        }
                    }
                }
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
