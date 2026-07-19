package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ExcludedAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedPackages by remember { mutableStateOf(AppSettings.getExcludedAppPackages(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember {
        mutableStateOf(
            AppListFilter.entries.firstOrNull {
                it.name == AppSettings.getExcludedAppsFilter(context)
            } ?: AppListFilter.USER
        )
    }
    var sort by remember { mutableStateOf(AppListSort.LABEL_ASC) }

    val appListAccess = rememberAppListAccessState { loadInstalledApps(context) }
    AppListDisclosureDialog(appListAccess)

    val loadedApps = appListAccess.apps
    if (loadedApps == null) {
        SettingsScaffold(title = "排除应用", onBack = onBack) { innerPadding ->
            AppListLoadingContent(Modifier.padding(innerPadding))
        }
        return
    }
    if (appListAccess.unavailable) {
        SettingsScaffold(title = "排除应用", onBack = onBack) { innerPadding ->
            AppListUnavailableContent(
                modifier = Modifier.padding(innerPadding),
                onRetry = appListAccess.retry
            )
        }
        return
    }

    var debouncedQuery by remember { mutableStateOf("") }
    var visibleApps by remember { mutableStateOf(emptyList<InstalledApp>()) }

    LaunchedEffect(query) {
        delay(250)
        debouncedQuery = query
    }

    LaunchedEffect(loadedApps, filter, sort, debouncedQuery, selectedPackages) {
        val normalizedQuery = debouncedQuery.trim().lowercase(Locale.ROOT)
        visibleApps = withContext(Dispatchers.Default) {
            loadedApps.filter { app ->
                (filter == AppListFilter.ALL ||
                    (filter == AppListFilter.USER && !app.isSystem) ||
                    (filter == AppListFilter.SYSTEM && app.isSystem) ||
                    (filter == AppListFilter.SELECTED && app.packageName in selectedPackages)) &&
                    (normalizedQuery.isEmpty() || app.normalizedLabel.contains(normalizedQuery) ||
                        app.normalizedPackageName.contains(normalizedQuery))
            }.sortedWith(sort.comparator)
        }
    }

    SettingsScaffold(
        title = "排除应用",
        onBack = onBack,
        actions = {
            val loadedPackageNames = loadedApps.mapTo(mutableSetOf()) { it.packageName }
            AppListOverflowMenu(
                filter = filter,
                sort = sort,
                onSelectAll = { selectedPackages = selectedPackages + loadedPackageNames },
                onClear = { selectedPackages = emptySet() },
                onInvert = { selectedPackages = selectedPackages - loadedPackageNames + (loadedPackageNames - selectedPackages) },
                onFilterChange = {
                    filter = it
                    AppSettings.setExcludedAppsFilter(context, it.name)
                },
                onSortChange = { sort = it }
            )
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
                        InstalledAppCheckboxItem(
                            app = app,
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
                    val inspectionPackages = AppSettings.getHttpInspectionAppPackages(context)
                    AppSettings.setHttpInspectionAppPackages(context, inspectionPackages - selectedPackages)
                    AppSettings.setBlockedAppPackages(context, AppSettings.getBlockedAppPackages(context) - selectedPackages)
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
