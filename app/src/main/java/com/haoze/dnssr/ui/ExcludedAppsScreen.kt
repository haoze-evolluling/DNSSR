package com.haoze.dnssr.ui

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsCheckboxItem
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsLoadingContent
import com.haoze.dnssr.ui.components.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class AppFilter(val label: String) {
    USER("用户应用"),
    SYSTEM("系统应用"),
    ALL("全部应用"),
    SELECTED("已选中")
}

private data class InstalledApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val normalizedLabel: String,
    val normalizedPackageName: String
)

@Composable
fun ExcludedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var selectedPackages by remember { mutableStateOf(AppSettings.getExcludedAppPackages(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.USER) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(0)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .map { info ->
                    val label = info.loadLabel(context.packageManager).toString()
                    InstalledApp(
                        label = label,
                        packageName = info.packageName,
                        isSystem = info.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                        normalizedLabel = label.lowercase(Locale.ROOT),
                        normalizedPackageName = info.packageName.lowercase(Locale.ROOT)
                    )
                }
                .toList()
                .sortedWith(compareBy<InstalledApp> { it.normalizedLabel }.thenBy { it.packageName })
        }
    }

    val loadedApps = apps
    if (loadedApps == null) {
        SettingsScaffold(title = "排除应用", onBack = onBack) { innerPadding ->
            SettingsLoadingContent(Modifier.padding(innerPadding))
        }
        return
    }

    var debouncedQuery by remember { mutableStateOf("") }
    var visibleApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        delay(250)
        debouncedQuery = query
    }

    LaunchedEffect(loadedApps, filter, debouncedQuery, selectedPackages) {
        val normalizedQuery = debouncedQuery.trim().lowercase(Locale.ROOT)
        visibleApps = withContext(Dispatchers.Default) {
            loadedApps.filter { app ->
                (filter == AppFilter.ALL ||
                    (filter == AppFilter.USER && !app.isSystem) ||
                    (filter == AppFilter.SYSTEM && app.isSystem) ||
                    (filter == AppFilter.SELECTED && app.packageName in selectedPackages)) &&
                    (normalizedQuery.isEmpty() || app.normalizedLabel.contains(normalizedQuery) ||
                        app.normalizedPackageName.contains(normalizedQuery))
            }
        }
    }

    SettingsScaffold(
        title = "排除应用",
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { showFilterMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "筛选应用"
                    )
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    AppFilter.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                filter = option
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                if (filter == option) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsInfoText(
                text = "排除后，应用将使用系统 DNS，不参与本应用的过滤、缓存、日志和统计。",
                modifier = Modifier.padding(top = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索应用或包名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            SettingsInfoText("已显示 ${visibleApps.size} 个应用，已选择 ${selectedPackages.size} 个")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                shape = SettingsCornerShape,
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        SettingsCheckboxItem(
                            title = app.label,
                            subtitle = app.packageName,
                            checked = app.packageName in selectedPackages,
                            onCheckedChange = { checked ->
                                selectedPackages = if (checked) {
                                    selectedPackages + app.packageName
                                } else {
                                    selectedPackages - app.packageName
                                }
                            }
                        )
                        if (index != visibleApps.lastIndex) SettingsDivider()
                    }
                }
            }
            Button(
                onClick = {
                    AppSettings.setExcludedAppPackages(context, selectedPackages)
                    RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                    val vpnRunning = com.haoze.dnssr.vpn.DnsVpnService.isRunning(context)
                    Toast.makeText(
                        context,
                        if (vpnRunning) "已保存，DNS VPN 正在重连" else "已保存，下次启动 DNS VPN 时生效",
                        Toast.LENGTH_SHORT
                    ).show()
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("保存")
            }
        }
    }
}
