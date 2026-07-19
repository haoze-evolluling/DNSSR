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
fun HttpInspectionAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedPackages by remember { mutableStateOf(AppSettings.getHttpInspectionAppPackages(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppListFilter.USER) }
    var sort by remember { mutableStateOf(AppListSort.LABEL_ASC) }

    val appListAccess = rememberAppListAccessState { loadInstalledApps(context) }
    AppListDisclosureDialog(appListAccess)
    val loadedApps = appListAccess.apps
    if (loadedApps == null) {
        SettingsScaffold(title = "选择过滤应用", onBack = onBack) { AppListLoadingContent(Modifier.padding(it)) }
        return
    }
    if (appListAccess.unavailable) {
        SettingsScaffold(title = "选择过滤应用", onBack = onBack) {
            AppListUnavailableContent(Modifier.padding(it), appListAccess.retry)
        }
        return
    }
    var debouncedQuery by remember { mutableStateOf("") }
    var visibleApps by remember { mutableStateOf(emptyList<InstalledApp>()) }
    LaunchedEffect(query) { delay(250); debouncedQuery = query }
    LaunchedEffect(loadedApps, filter, sort, debouncedQuery, selectedPackages) {
        val normalizedQuery = debouncedQuery.trim().lowercase(Locale.ROOT)
        visibleApps = withContext(Dispatchers.Default) {
            loadedApps.filter { app ->
                (filter == AppListFilter.ALL ||
                    filter == AppListFilter.USER && !app.isSystem ||
                    filter == AppListFilter.SYSTEM && app.isSystem ||
                    filter == AppListFilter.SELECTED && app.packageName in selectedPackages) &&
                    (normalizedQuery.isEmpty() || app.normalizedLabel.contains(normalizedQuery) || app.normalizedPackageName.contains(normalizedQuery))
            }.sortedWith(sort.comparator)
        }
    }
    SettingsScaffold(title = "选择过滤应用", onBack = onBack, actions = {
        val loadedPackageNames = loadedApps.mapTo(mutableSetOf()) { it.packageName }
        AppListOverflowMenu(
            filter = filter,
            sort = sort,
            onSelectAll = { selectedPackages = selectedPackages + loadedPackageNames },
            onClear = { selectedPackages = emptySet() },
            onInvert = { selectedPackages = selectedPackages - loadedPackageNames + (loadedPackageNames - selectedPackages) },
            onFilterChange = { filter = it },
            onSortChange = { sort = it }
        )
    }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsInfoText("Go 全隧道仅检查所选应用的 HTTP(S) 请求，其他应用透明转发。选择应用会取消其“排除应用”状态。", Modifier.padding(top = 8.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("搜索应用或包名") }, singleLine = true)
            SettingsInfoText("已显示 ${visibleApps.size} 个应用，已选择 ${selectedPackages.size} 个")
            Card(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), SettingsCornerShape, CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        InstalledAppCheckboxItem(
                            app = app,
                            checked = app.packageName in selectedPackages,
                            onCheckedChange = { checked ->
                            selectedPackages = if (checked) selectedPackages + app.packageName else selectedPackages - app.packageName
                            }
                        )
                        if (index != visibleApps.lastIndex) SettingsDivider()
                    }
                }
            }
            Button(onClick = {
                AppSettings.setHttpInspectionAppPackages(context, selectedPackages)
                AppSettings.setExcludedAppPackages(context, AppSettings.getExcludedAppPackages(context) - selectedPackages)
                AppSettings.setBlockedAppPackages(context, AppSettings.getBlockedAppPackages(context) - selectedPackages)
                RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                Toast.makeText(context, "已保存过滤应用", Toast.LENGTH_SHORT).show()
                onBack()
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("保存") }
        }
    }
}
