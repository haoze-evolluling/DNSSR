package com.haoze.dnssr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import com.haoze.dnssr.ui.components.AppAlertDialog as AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.data.entity.MirrorTemplateEntity
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsItem
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold

private val mirrorTemplatePlaceholders = listOf(
    "{url}",
    "{scheme}",
    "{urlEncoded}",
    "{host}",
    "{path}",
    "{pathAndQuery}"
)

@Composable
fun MirrorTemplateScreen(
    onBack: () -> Unit,
    onNavigateToFormatGuide: () -> Unit,
    viewModel: RuleManagementViewModel = viewModel()
) {
    val templates by viewModel.mirrorTemplates.collectAsStateWithLifecycle(initialValue = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTemplate by remember { mutableStateOf<MirrorTemplateEntity?>(null) }
    var pendingDeletion by remember { mutableStateOf<MirrorTemplateEntity?>(null) }

    SettingsScaffold(title = "镜像站模板", onBack = onBack) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                SettingsInfoText(
                    "保存常用的订阅下载镜像。添加规则订阅时，可以直接选择这里的模板。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item { SettingsGroupTitle("操作") }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "添加模板",
                        subtitle = "填写镜像站名称和地址格式",
                        leadingIcon = Icons.Default.Add,
                        onClick = { showAddDialog = true }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "镜像站格式示例",
                        subtitle = "了解占位符如何组成镜像地址",
                        leadingIcon = Icons.Default.Info,
                        onClick = onNavigateToFormatGuide
                    )
                }
            }
            item { SettingsGroupTitle("已保存模板（${templates.size}）") }
            if (templates.isEmpty()) {
                item { SettingsInfoText("暂无模板。点击上方“添加模板”开始使用。") }
            } else {
                item {
                    SettingsGroup {
                        templates.forEachIndexed { index, template ->
                            if (index > 0) SettingsDivider()
                            SettingsItem(
                                title = template.name,
                                subtitle = template.template,
                                trailing = {
                                    Row {
                                        IconButton(onClick = { editingTemplate = template }) {
                                            Icon(Icons.Default.Edit, contentDescription = "编辑 ${template.name}")
                                        }
                                        IconButton(onClick = { pendingDeletion = template }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除 ${template.name}", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MirrorTemplateDialog(
            onDismiss = { showAddDialog = false },
            onSubmit = { name, template, onResult -> viewModel.addMirrorTemplate(name, template, onResult) },
            onSaved = { showAddDialog = false }
        )
    }

    editingTemplate?.let { template ->
        MirrorTemplateDialog(
            template = template,
            onDismiss = { editingTemplate = null },
            onSubmit = { name, address, onResult -> viewModel.editMirrorTemplate(template, name, address, onResult) },
            onSaved = { editingTemplate = null }
        )
    }

    pendingDeletion?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("删除镜像站模板") },
            text = { Text("确定要删除“${template.name}”吗？已使用此模板的订阅不会被修改。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMirrorTemplate(template)
                    pendingDeletion = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MirrorTemplateDialog(
    template: MirrorTemplateEntity? = null,
    onDismiss: () -> Unit,
    onSubmit: (String, String, (String) -> Unit) -> Unit,
    onSaved: () -> Unit
) {
    val isEditing = template != null
    var name by remember(template?.id) { mutableStateOf(template?.name.orEmpty()) }
    var address by remember(template?.id) { mutableStateOf(template?.template.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(if (isEditing) "编辑镜像站模板" else "添加镜像站模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (isEditing) "修改后，已使用此模板的订阅不会被自动更新。" else "保存后，可在添加规则订阅时直接选择。", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("模板名称") },
                    placeholder = { Text("例如：GitHub 镜像") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; error = null },
                    label = { Text("模板地址") },
                    placeholder = { Text("https://mirror.example.com/{url}") },
                    supportingText = { Text(error ?: "必须使用 HTTP(S) 并包含至少一个占位符") },
                    isError = error != null,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mirrorTemplatePlaceholders.chunked(3).forEach { rowPlaceholders ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPlaceholders.forEachIndexed { index, placeholder ->
                                OutlinedButton(
                                    onClick = {
                                        address += placeholder
                                        error = null
                                    },
                                    modifier = Modifier.weight(if (index == 2) 0.4f else 0.3f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(placeholder, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting && name.isNotBlank() && address.isNotBlank(),
                onClick = {
                    submitting = true
                    onSubmit(name, address) { message ->
                        submitting = false
                        if (message == "已添加镜像站模板" || message == "已更新镜像站模板") onSaved() else error = message
                    }
                }
            ) { Text(if (submitting) if (isEditing) "保存中..." else "添加中..." else if (isEditing) "保存" else "添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text("取消") } }
    )
}
