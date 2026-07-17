package com.haoze.dnssr.ui

import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

private enum class InspectionAppFilter(val label: String) {
    USER("用户应用"), SYSTEM("系统应用"), ALL("全部应用"), SELECTED("已选中")
}

private data class InspectionInstalledApp(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
    val normalizedLabel: String,
    val normalizedPackageName: String
)

@Composable
fun HttpInspectionAppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InspectionInstalledApp>?>(null) }
    var selectedPackages by remember { mutableStateOf(AppSettings.getHttpInspectionAppPackages(context)) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InspectionAppFilter.USER) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            context.packageManager.getInstalledApplications(0).asSequence()
                .filter { it.packageName != context.packageName }
                .map { info ->
                    val label = info.loadLabel(context.packageManager).toString()
                    InspectionInstalledApp(label, info.packageName, info.flags and ApplicationInfo.FLAG_SYSTEM != 0, label.lowercase(Locale.ROOT), info.packageName.lowercase(Locale.ROOT))
                }
                .sortedWith(compareBy<InspectionInstalledApp> { it.normalizedLabel }.thenBy { it.packageName })
                .toList()
        }
    }
    val loadedApps = apps
    if (loadedApps == null) {
        SettingsScaffold(title = "选择过滤应用", onBack = onBack) { SettingsLoadingContent(Modifier.padding(it)) }
        return
    }
    var debouncedQuery by remember { mutableStateOf("") }
    var visibleApps by remember { mutableStateOf(emptyList<InspectionInstalledApp>()) }
    var showFilterMenu by remember { mutableStateOf(false) }
    LaunchedEffect(query) { delay(250); debouncedQuery = query }
    LaunchedEffect(loadedApps, filter, debouncedQuery, selectedPackages) {
        val normalizedQuery = debouncedQuery.trim().lowercase(Locale.ROOT)
        visibleApps = withContext(Dispatchers.Default) {
            loadedApps.filter { app ->
                (filter == InspectionAppFilter.ALL ||
                    filter == InspectionAppFilter.USER && !app.isSystem ||
                    filter == InspectionAppFilter.SYSTEM && app.isSystem ||
                    filter == InspectionAppFilter.SELECTED && app.packageName in selectedPackages) &&
                    (normalizedQuery.isEmpty() || app.normalizedLabel.contains(normalizedQuery) || app.normalizedPackageName.contains(normalizedQuery))
            }
        }
    }
    SettingsScaffold(title = "选择过滤应用", onBack = onBack, actions = {
        Box {
            IconButton(onClick = { showFilterMenu = true }) { Icon(Icons.Default.FilterList, "筛选应用") }
            DropdownMenu(expanded = showFilterMenu, onDismissRequest = { showFilterMenu = false }) {
                InspectionAppFilter.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(option.label) }, onClick = { filter = option; showFilterMenu = false }, leadingIcon = {
                        if (filter == option) Icon(Icons.Default.Check, null)
                    })
                }
            }
        }
    }) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsInfoText("仅所选应用的 HTTP/HTTPS 流量会按现有域名规则过滤。选择应用会取消其“排除应用”状态。", Modifier.padding(top = 8.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("搜索应用或包名") }, singleLine = true)
            SettingsInfoText("已显示 ${visibleApps.size} 个应用，已选择 ${selectedPackages.size} 个")
            Card(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp), SettingsCornerShape, CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(visibleApps, key = { _, app -> app.packageName }) { index, app ->
                        SettingsCheckboxItem(
                            title = app.label,
                            subtitle = app.packageName,
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
                RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
                Toast.makeText(context, "已保存过滤应用", Toast.LENGTH_SHORT).show()
                onBack()
            }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) { Text("保存") }
        }
    }
}
