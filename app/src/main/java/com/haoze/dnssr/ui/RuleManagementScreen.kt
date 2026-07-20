package com.haoze.dnssr.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
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
import com.haoze.dnssr.data.entity.RewriteTargetType
import com.haoze.dnssr.data.entity.MirrorTemplateEntity

@Composable
fun RuleManagementScreen(
    onBack: () -> Unit,
    title: String = "域名规则",
    onNavigateToRuleList: () -> Unit,
    onNavigateToAllowRuleList: () -> Unit,
    onNavigateToRewriteRuleList: () -> Unit,
    onNavigateToSubscription: () -> Unit,
    onNavigateToMirrorTemplates: () -> Unit,
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
    val rewriteRuleCount by viewModel.rewriteRuleCount.collectAsStateWithLifecycle()
    val mirrorTemplates by viewModel.mirrorTemplates.collectAsStateWithLifecycle(initialValue = emptyList())

    var newRule by remember { mutableStateOf("") }
    var newAllowRule by remember { mutableStateOf("") }
    var rewriteDomain by remember { mutableStateOf("") }
    var rewriteAddress by remember { mutableStateOf("") }
    var rewriteTargetType by remember { mutableStateOf(RewriteTargetType.IPV4) }
    var addResult by remember { mutableStateOf<String?>(null) }
    var addAllowResult by remember { mutableStateOf<String?>(null) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var showAddAllowRuleDialog by remember { mutableStateOf(false) }
    var showAddRewriteRuleDialog by remember { mutableStateOf(false) }
    var showClearAllRulesDialog by remember { mutableStateOf(false) }
    var addRuleError by remember { mutableStateOf<String?>(null) }
    var addAllowRuleError by remember { mutableStateOf<String?>(null) }
    var addRewriteRuleError by remember { mutableStateOf<String?>(null) }

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

    fun openAddRewriteRuleDialog() {
        rewriteDomain = ""
        rewriteAddress = ""
        rewriteTargetType = RewriteTargetType.IPV4
        addRewriteRuleError = null
        showAddRewriteRuleDialog = true
    }

    fun closeAddRewriteRuleDialog() {
        showAddRewriteRuleDialog = false
        addRewriteRuleError = null
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
                    text = "当前共有 $ruleCount 条屏蔽规则，$allowRuleCount 条白名单规则，$rewriteRuleCount 条覆写规则。覆写规则优先于黑白名单。",
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                SettingsGroupTitle("拦截策略")
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
                SettingsGroupTitle("快速添加")
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
                        title = "添加放行域名",
                        subtitle = "输入要放行的域名，如 example.com",
                        onClick = ::openAddAllowRuleDialog
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "添加覆写域名",
                        subtitle = "将域名覆写为 IPv4、IPv6 或 CNAME",
                        onClick = ::openAddRewriteRuleDialog
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
                SettingsGroupTitle("订阅与更新")
            }
            item {
                SettingsGroup {
                    SettingsNavigationItem(
                        title = "镜像站模板",
                        subtitle = "维护订阅下载镜像，可在添加订阅时直接选择",
                        value = "${mirrorTemplates.size} 个",
                        onClick = {
                            onNavigateToMirrorTemplates()
                        }
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "规则订阅",
                        subtitle = "管理 DNS 过滤与 hosts 覆写订阅",
                        onClick = onNavigateToSubscription
                    )
                    SettingsDivider()
                    SettingsNavigationItem(
                        title = "自动更新设置",
                        subtitle = "设置规则订阅的自动更新开关和频率",
                        onClick = onNavigateToAutoUpdateInterval
                    )
                }
            }

            item {
                SettingsGroupTitle("规则管理")
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
                    SettingsNavigationItem(title = "覆写域名规则", subtitle = "查看、启用、停用或删除覆写规则", value = "$rewriteRuleCount 条", onClick = onNavigateToRewriteRuleList)
                }
            }

            item {
                SettingsGroupTitle("维护工具")
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
            text = "确定要删除全部域名规则吗？屏蔽、白名单和覆写规则都会被移除。",
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
            title = { Text("添加放行域名") },
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

    if (showAddRewriteRuleDialog) {
        AlertDialog(
            onDismissRequest = ::closeAddRewriteRuleDialog,
            title = { Text("添加覆写域名") },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = rewriteDomain, onValueChange = { rewriteDomain = it; addRewriteRuleError = null }, label = { Text("域名，如 example.com") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(RewriteTargetType.IPV4, RewriteTargetType.IPV6, RewriteTargetType.CNAME).forEach { type -> FilterChip(selected = rewriteTargetType == type, onClick = { rewriteTargetType = type; rewriteAddress = ""; addRewriteRuleError = null }, label = { Text(type) }, modifier = Modifier.weight(1f)) }
                    }
                    OutlinedTextField(
                        value = rewriteAddress,
                        onValueChange = { rewriteAddress = it; addRewriteRuleError = null },
                        label = { Text(if (rewriteTargetType == RewriteTargetType.CNAME) "目标域名" else "$rewriteTargetType 地址") },
                        supportingText = addRewriteRuleError?.let { error -> { Text(error) } },
                        isError = addRewriteRuleError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.addRewriteRule(rewriteDomain, rewriteTargetType, rewriteAddress) { message ->
                if (message == "已添加覆写域名") {
                    addResult = message
                    closeAddRewriteRuleDialog()
                } else {
                    addRewriteRuleError = message
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } }) { Text("确定") } },
            dismissButton = { TextButton(onClick = ::closeAddRewriteRuleDialog) { Text("取消") } }
        )
    }
}
