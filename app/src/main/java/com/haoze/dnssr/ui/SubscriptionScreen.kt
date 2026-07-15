package com.haoze.dnssr.ui

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.data.entity.SubscriptionImportState
import com.haoze.dnssr.data.entity.SubscriptionSourceType
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsScaffold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    onRuntimeDnsSettingsChanged: () -> Unit = {},
    viewModel: SubscriptionViewModel = viewModel()
) {
    val context = LocalContext.current
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importingSubscriptionId by viewModel.importingSubscriptionId.collectAsStateWithLifecycle()
    val operationMessage by viewModel.operationMessage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy = importing || operationMessage != null

    var showAddChoiceDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var localImportUri by remember { mutableStateOf<Uri?>(null) }
    var showActionDialog by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showDeleteDialog by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showUrlDialog by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showEditDialog by remember { mutableStateOf<SubscriptionEntity?>(null) }
    var showRenameDialog by remember { mutableStateOf<SubscriptionEntity?>(null) }
    val localImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        localImportUri = uri
    }

    NavigationSettledEffect {
        viewModel.activate()
    }

    // 消息自动清除
    message?.let { resultMessage ->
        androidx.compose.runtime.LaunchedEffect(resultMessage) {
            Toast.makeText(context, resultMessage, Toast.LENGTH_LONG).show()
            kotlinx.coroutines.delay(3000)
            viewModel.clearMessage()
        }
    }

    SettingsScaffold(
        title = "规则订阅",
        onBack = onBack,
        actions = {
            IconButton(onClick = viewModel::updateAllSubscriptions, enabled = subscriptions.isNotEmpty() && !busy) {
                Icon(Icons.Default.Refresh, contentDescription = "更新所有订阅")
            }
            IconButton(onClick = { showAddChoiceDialog = true }, enabled = !busy) {
                Icon(Icons.Default.Add, contentDescription = "添加规则订阅")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 操作结果消息
            message?.let {
                item {
                    SettingsInfoText(text = it, modifier = Modifier.padding(top = 8.dp))
                }
            }

            item {
                AnimatedContent(
                    targetState = subscriptions.isEmpty() && !busy,
                    transitionSpec = {
                        EnterTransition.None.togetherWith(ExitTransition.None)
                    },
                    label = "SubscriptionList"
                ) { empty ->
                    if (empty) {
                        SettingsGroupTitle("规则订阅")
                        SettingsGroup {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无规则订阅",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "点击右上角 + 添加 AdGuard DNS 规则地址",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column {
                            SettingsGroupTitle("规则订阅")
                            SettingsGroup {
                                subscriptions.forEachIndexed { index, sub ->
                                    SubscriptionItem(
                                        subscription = sub,
                                        onShowUrl = {
                                            if (sub.sourceType == SubscriptionSourceType.REMOTE) showUrlDialog = sub
                                        },
                                        onShowActions = { showActionDialog = sub },
                                        actionsEnabled = !busy,
                                        isUpdating = importingSubscriptionId == sub.id,
                                        importProgress = importProgress
                                    )
                                    if (index < subscriptions.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsInfoText(
                    text = "依据 AdGuard DNS 语法自动分类黑白名单。支持 BOM、行尾注释、hosts 多域名和 IDN 域名。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showAddChoiceDialog) {
        AddSubscriptionChoiceDialog(
            onDismiss = { showAddChoiceDialog = false },
            onAddRemote = {
                showAddChoiceDialog = false
                showAddDialog = true
            },
            onAddLocal = {
                showAddChoiceDialog = false
                localImportLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream"))
            }
        )
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, name ->
                viewModel.addSubscription(url, name)
                showAddDialog = false
            }
        )
    }

    localImportUri?.let { uri ->
        LocalSubscriptionImportDialog(
            initialName = remember(uri) { context.displayNameFor(uri) },
            onDismiss = { localImportUri = null },
            onConfirm = { name ->
                viewModel.addLocalSubscription(uri, name)
                localImportUri = null
            }
        )
    }

    showUrlDialog?.let { sub ->
        AlertDialog(
            onDismissRequest = { showUrlDialog = null },
            title = { Text("订阅地址") },
            text = {
                SelectionContainer {
                    Text(
                        text = sub.url,
                        style = MaterialTheme.typography.bodyMedium,
                        softWrap = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showUrlDialog = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 订阅操作对话框
    showActionDialog?.let { sub ->
        SubscriptionActionDialog(
            subscription = sub,
            onDismiss = { showActionDialog = null },
            onUpdate = {
                viewModel.updateSubscription(sub.id)
                showActionDialog = null
            },
            onDelete = {
                showActionDialog = null
                showDeleteDialog = sub
            },
            onEdit = {
                showActionDialog = null
                if (sub.sourceType == SubscriptionSourceType.LOCAL) {
                    showRenameDialog = sub
                } else {
                    showEditDialog = sub
                }
            },
            onToggleEnabled = {
                viewModel.toggleSubscriptionEnabled(sub.id, !sub.enabled)
                showActionDialog = null
            }
        )
    }

    showEditDialog?.let { sub ->
        EditSubscriptionDialog(
            subscription = sub,
            onDismiss = { showEditDialog = null },
            onConfirm = { url, name ->
                viewModel.editSubscription(sub.id, url, name)
                showEditDialog = null
            }
        )
    }

    showRenameDialog?.let { sub ->
        RenameSubscriptionDialog(
            subscription = sub,
            onDismiss = { showRenameDialog = null },
            onConfirm = { name ->
                viewModel.renameSubscription(sub.id, name)
                showRenameDialog = null
            }
        )
    }

    // 删除确认对话框
    showDeleteDialog?.let { sub ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除规则订阅") },
            text = { Text("确定删除「${sub.name}」及其导入的所有规则吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubscription(sub.id)
                    showDeleteDialog = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SubscriptionItem(
    subscription: SubscriptionEntity,
    onShowUrl: () -> Unit,
    onShowActions: () -> Unit,
    actionsEnabled: Boolean,
    isUpdating: Boolean,
    importProgress: Pair<Int, Int>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (subscription.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (subscription.sourceType == SubscriptionSourceType.LOCAL) "本地文件" else subscription.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (subscription.sourceType == SubscriptionSourceType.REMOTE) {
                        Modifier.clickable(onClick = onShowUrl)
                    } else {
                        Modifier
                    }
                )
            }
            IconButton(onClick = onShowActions, enabled = actionsEnabled) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "打开规则订阅操作"
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (subscription.enabled) "已启用" else "已禁用",
                style = MaterialTheme.typography.bodySmall,
                color = if (subscription.enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = "${subscription.ruleCount} 条规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (subscription.lastUpdated > 0) {
                val dateStr = remember(subscription.lastUpdated) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(subscription.lastUpdated))
                }
                Text(
                    text = if (subscription.sourceType == SubscriptionSourceType.LOCAL) {
                        "导入于 $dateStr"
                    } else {
                        "更新于 $dateStr"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isUpdating) {
            val (current, total) = importProgress
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (total > 0) "正在导入规则... $current / $total" else "正在下载并更新规则...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { current.toFloat() / total.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (subscription.importState == SubscriptionImportState.FAILED) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "导入失败：${subscription.importError ?: "未知错误"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun SubscriptionActionDialog(
    subscription: SubscriptionEntity,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(subscription.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (subscription.sourceType == SubscriptionSourceType.REMOTE) {
                    OutlinedButton(
                        onClick = onUpdate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "更新规则",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (subscription.sourceType == SubscriptionSourceType.LOCAL) "重命名订阅" else "编辑订阅",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(
                    onClick = onToggleEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (subscription.enabled) "禁用规则" else "启用规则",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "删除规则",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则订阅") },
        text = {
            Column {
                Text(
                    text = "输入 AdGuard DNS 规则订阅地址，导入时会自动区分黑名单和白名单规则。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅名称（可选）") },
                    placeholder = { Text("例如：EasyList China") },
                    shape = SettingsCornerShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅地址") },
                    placeholder = { Text("https://example.com/filter.txt") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = SettingsCornerShape,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), name.trim()) },
                enabled = url.trim().startsWith("http")
            ) {
                Text("导入规则")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun AddSubscriptionChoiceDialog(
    onDismiss: () -> Unit,
    onAddRemote: () -> Unit,
    onAddLocal: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加规则订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAddRemote, modifier = Modifier.fillMaxWidth()) {
                    Text("从网络地址导入", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                OutlinedButton(onClick = onAddLocal, modifier = Modifier.fillMaxWidth()) {
                    Text("从本地文件导入", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LocalSubscriptionImportDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入本地规则订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "本地文件导入后无法更新，但可在订阅列表中重命名、启用、禁用或删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅名称") },
                    singleLine = true,
                    shape = SettingsCornerShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }


        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("导入规则") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditSubscriptionDialog(
    subscription: SubscriptionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember(subscription.id) { mutableStateOf(subscription.name) }
    var url by remember(subscription.id) { mutableStateOf(subscription.url) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑规则订阅") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subscription.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("订阅地址") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    singleLine = true,
                    shape = SettingsCornerShape,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("订阅名称") },
                    singleLine = true,
                    shape = SettingsCornerShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), name.trim()) },
                enabled = url.trim().isNotEmpty() && name.trim().isNotEmpty()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun RenameSubscriptionDialog(
    subscription: SubscriptionEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(subscription.id) { mutableStateOf(subscription.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名规则订阅") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("订阅名称") },
                singleLine = true,
                shape = SettingsCornerShape,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun android.content.Context.displayNameFor(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return "本地规则"
}

