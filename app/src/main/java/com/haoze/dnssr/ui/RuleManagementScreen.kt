package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.haoze.dnssr.ui.components.SettingsCornerShape
import com.haoze.dnssr.ui.components.SettingsDivider
import com.haoze.dnssr.ui.components.SettingsGroup
import com.haoze.dnssr.ui.components.SettingsGroupTitle
import com.haoze.dnssr.ui.components.SettingsInfoText
import com.haoze.dnssr.ui.components.SettingsNavigationItem
import com.haoze.dnssr.ui.components.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RuleManagementScreen(
    onBack: () -> Unit,
    title: String = "域名规则",
    onNavigateToRuleList: () -> Unit,
    onNavigateToAllowRuleList: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToAutoUpdateInterval: () -> Unit,
    onNavigateToBlockResponseSettings: () -> Unit,
    onRuntimeDnsSettingsChanged: () -> Unit = {},
    viewModel: RuleManagementViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val ruleCount by viewModel.ruleCount.collectAsStateWithLifecycle()
    val allowRuleCount by viewModel.allowRuleCount.collectAsStateWithLifecycle()

    var newRule by remember { mutableStateOf("") }
    var newAllowRule by remember { mutableStateOf("") }
    var addResult by remember { mutableStateOf<String?>(null) }
    var addAllowResult by remember { mutableStateOf<String?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddAllowRuleDialog by remember { mutableStateOf(false) }
    var showClearAllRulesDialog by remember { mutableStateOf(false) }
    var addRuleError by remember { mutableStateOf<String?>(null) }
    var addAllowRuleError by remember { mutableStateOf<String?>(null) }

    fun openAddRuleDialog() {
        newRule = ""
        addRuleError = null
        showAddRuleDialog = true
    }

    fun closeAddRuleDialog() {
        showAddRuleDialog = false
        addRuleError = null
    }

    fun openAddAllowRuleDialog() {
        newAllowRule = ""
        addAllowRuleError = null
        showAddAllowRuleDialog = true
    }

    fun closeAddAllowRuleDialog() {
        showAddAllowRuleDialog = false
        addAllowRuleError = null
    }

    NavigationSettledEffect {
        viewModel.activate()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadRuleCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    SettingsScaffold(
        title = title,
        onBack = onBack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                SettingsInfoText(
                    text = "当前共有 $ruleCount 条屏蔽规则，$allowRuleCount 条白名单规则。白名单命中时会绕过本应用屏蔽规则。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                SettingsGroupTitle("拦截响应")
            }
            item {
                SettingsGroup {
                    val dynamicConfig = AppSettings.getDynamicBlockResponseConfig(context)
                    SettingsNavigationItem(
                        title = "拦截响应",
                        subtitle = if (dynamicConfig.enabled) {
                            "动态策略：先 NODATA，高频请求后 NXDOMAIN"
                        } else {
                            "当前：${AppSettings.getBlockResponseMode(context).displayName}"
                        },
                        onClick = onNavigateToBlockResponseSettings
                    )
                }
            }

            item {
                SettingsGroupTitle("手动添加域名")
            }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "添加屏蔽域名",
                        subtitle = "输入要屏蔽的域名，如 example.com",
                        onClick = ::openAddRuleDialog
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "添加白名单域名",
                        subtitle = "输入要放行的域名，如 example.com",
                        onClick = ::openAddAllowRuleDialog
                    )
                }
            }
            addResult?.let { message ->
                item {
                    SettingsInfoText(message)
                }
            }
            addAllowResult?.let { message ->
                item {
                    SettingsInfoText(message)
                }
            }

            item {
                SettingsGroupTitle("自动更新")
            }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "自动更新设置",
                        subtitle = "设置规则订阅的自动更新开关和频率",
                        onClick = onNavigateToAutoUpdateInterval
                    )
                }
            }

            item {
                SettingsGroupTitle("规则列表与订阅")
            }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "黑名单规则",
                        subtitle = "查看、启用、停用或删除屏蔽规则",
                        value = "$ruleCount 条",
                        onClick = onNavigateToRuleList
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "白名单规则",
                        subtitle = "查看、启用、停用或删除放行规则",
                        value = "$allowRuleCount 条",
                        onClick = onNavigateToAllowRuleList
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "规则订阅",
                        subtitle = "从 AdGuard DNS 过滤规则订阅导入屏蔽/放行域名",
                        onClick = onNavigateToSubscription
                    )
                }
            }

            item {
                SettingsGroupTitle("规则修复")
            }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "清理全部规则",
                        subtitle = "如出现规则冲突、重复或导入异常，建议先删除全部规则，再重新导入",
                        onClick = { showClearAllRulesDialog = true }
                    )
                }
            }
        }
    }

    if (showClearAllRulesDialog) {
        ConfirmDialog(
            title = "删除全部规则",
            text = "确定要删除全部域名规则吗？屏蔽和白名单规则都会被移除。",
            onConfirm = {
                showClearAllRulesDialog = false
                scope.launch(Dispatchers.IO) {
                    clearAllDomainRules(context)
                    withContext(Dispatchers.Main) {
                        viewModel.loadRuleCount()
                        onRuntimeDnsSettingsChanged()
                        Toast.makeText(context, "已删除全部规则", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showClearAllRulesDialog = false }
        )
    }

    if (showAddRuleDialog) {
        AlertDialog(
            onDismissRequest = ::closeAddRuleDialog,
            title = { Text("添加屏蔽域名") },
            text = {
                OutlinedTextField(
                    value = newRule,
                    onValueChange = {
                        newRule = it
                        addRuleError = null
                    },
                    label = { Text("要屏蔽的域名，如 example.com") },
                    supportingText = {
                        Text(addRuleError ?: "请输入域名或支持的过滤规则")
                    },
                    isError = addRuleError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = SettingsCornerShape,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rule = newRule.trim()
                        if (rule.isBlank()) {
                            addRuleError = "请输入要屏蔽的域名"
                            return@TextButton
                        }
                        viewModel.addRule(rule) { message ->
                            if (message == "已添加到屏蔽规则") {
                                addResult = message
                                newRule = ""
                                closeAddRuleDialog()
                            } else {
                                addRuleError = message
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = ::closeAddRuleDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddAllowRuleDialog) {
        AlertDialog(
            onDismissRequest = ::closeAddAllowRuleDialog,
            title = { Text("添加白名单域名") },
            text = {
                OutlinedTextField(
                    value = newAllowRule,
                    onValueChange = {
                        newAllowRule = it
                        addAllowRuleError = null
                    },
                    label = { Text("要放行的域名，如 example.com") },
                    supportingText = {
                        Text(addAllowRuleError ?: "请输入域名或支持的白名单规则")
                    },
                    isError = addAllowRuleError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = SettingsCornerShape,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val rule = newAllowRule.trim()
                        if (rule.isBlank()) {
                            addAllowRuleError = "请输入要放行的域名"
                            return@TextButton
                        }
                        viewModel.addAllowRule(rule) { message ->
                            if (message == "已添加到白名单规则") {
                                addAllowResult = message
                                newAllowRule = ""
                                closeAddAllowRuleDialog()
                            } else {
                                addAllowRuleError = message
                            }
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = ::closeAddAllowRuleDialog) {
                    Text("取消")
                }
            }
        )
    }
}
