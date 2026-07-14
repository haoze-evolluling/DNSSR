package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsRadioItem
import com.haoze.dnssr.vpn.DnsProtocol
import com.haoze.dnssr.vpn.DnsProvider

@Composable
fun Doh3FirstUseDialog(
    provider: DnsProvider,
    providers: List<DnsProvider>,
    onContinue: () -> Unit,
    onReplacementSelected: (DnsProvider) -> Unit,
    onDismiss: () -> Unit
) {
    var showReplacementPicker by remember(provider.id) { mutableStateOf(false) }

    if (showReplacementPicker) {
        DohReplacementPickerDialog(
            providers = providers.filter { it.protocol == DnsProtocol.DOH || it.protocol == DnsProtocol.DOT },
            onSelected = onReplacementSelected,
            onDismiss = onDismiss
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("DoH3 连接提示") },
            text = {
                Text(
                    "您选择的服务“${provider.name}”使用 DoH3。部分地区或网络会限制 UDP/443，" +
                        "该服务可能无法使用。"
                )
            },
            confirmButton = {
                TextButton(onClick = onContinue) { Text("仍然使用") }
            },
            dismissButton = {
                TextButton(onClick = { showReplacementPicker = true }) { Text("切换到 DoH/DoT") }
            }
        )
    }
}

@Composable
private fun DohReplacementPickerDialog(
    providers: List<DnsProvider>,
    onSelected: (DnsProvider) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择 DoH 或 DoT 服务") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                items(providers, key = DnsProvider::id) { provider ->
                    SettingsRadioItem(
                        title = "${provider.name} (${provider.protocol.label})",
                        subtitle = provider.endpointLabel(),
                        selected = false,
                        onClick = { onSelected(provider) }
                    )
                    SettingsDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
