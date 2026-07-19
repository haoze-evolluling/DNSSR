package com.haoze.dnssr.ui

import android.os.Build

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigateToRoute: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val normalizedQuery = searchQuery.trim()
    val filteredItems = if (normalizedQuery.isEmpty()) emptyList() else
        ScreenDestinations.searchEntries.filter { it.matches(normalizedQuery) }

    SettingsScaffold(title = "应用设置", onBack = onBack) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    placeholder = { Text("搜索设置") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "搜索") },
                    trailingIcon = if (searchQuery.isNotEmpty()) {{
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清除")
                        }
                    }} else null
                )
            }
            if (normalizedQuery.isNotEmpty()) {
                if (filteredItems.isEmpty()) {
                    item { Text("没有找到匹配的设置", modifier = Modifier.padding(32.dp)) }
                } else {
                    items(filteredItems.size) { index ->
                        val setting = filteredItems[index]
                        SettingsGroup(modifier = Modifier.padding(vertical = 4.dp)) {
                            SettingsNavigationItem(
                                title = setting.title,
                                subtitle = setting.resultSubtitle,
                                leadingIcon = setting.icon,
                                enabled = setting.route != Routes.BLOCKED_APPS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                                onClick = { onNavigateToRoute(setting.route) }
                            )
                        }
                    }
                }
                return@LazyColumn
            }
            SettingsSection.values().sortedBy { it.order }.forEach { section ->
                val entries = ScreenDestinations.mainEntries.filter { it.mainSection == section }
                item { SettingsGroupTitle(section.title) }
                item {
                    SettingsGroup {
                        entries.forEachIndexed { index, destination ->
                            if (index > 0) SettingsDivider()
                            SettingsNavigationItem(
                                title = destination.title,
                                subtitle = destination.description,
                                leadingIcon = destination.icon,
                                enabled = destination.route != Routes.BLOCKED_APPS || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                                onClick = { onNavigateToRoute(destination.route) }
                            )
                        }
                    }
                }
            }
        }
    }
}
