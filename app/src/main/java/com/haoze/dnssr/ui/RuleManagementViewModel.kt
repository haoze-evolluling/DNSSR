package com.haoze.dnssr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class RuleManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val blockListManager: BlockListManager by lazy {
        BlockListManager(AppDatabase.getInstance(application).blockRuleDao())
    }
    private val allowListManager: AllowListManager by lazy {
        AllowListManager(AppDatabase.getInstance(application).allowRuleDao())
    }

    private val _ruleCount = MutableStateFlow(0)
    val ruleCount: StateFlow<Int> = _ruleCount.asStateFlow()

    private val _allowRuleCount = MutableStateFlow(0)
    val allowRuleCount: StateFlow<Int> = _allowRuleCount.asStateFlow()

    private var activated = false

    fun activate() {
        if (!activated) {
            activated = true
            loadRuleCount()
        }
    }

    fun loadRuleCount() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = blockListManager.count()
            val allowCount = allowListManager.count()
            withContext(Dispatchers.Main) {
                _ruleCount.value = count
                _allowRuleCount.value = allowCount
            }
        }
    }

    fun addRule(pattern: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = blockListManager.addRule(pattern)
            val message = if (success) "已添加到屏蔽规则" else "规则格式无效，请输入域名或支持的过滤规则"
            if (success) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "block_rule_added"
                )
            }
            withContext(Dispatchers.Main) {
                onResult(message)
            }
            if (success) {
                loadRuleCount()
            }
        }
    }

    fun addAllowRule(pattern: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = allowListManager.addRule(pattern)
            val message = if (success) "已添加到白名单规则" else "规则格式无效，请输入域名或支持的白名单规则"
            if (success) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "allow_rule_added"
                )
            }
            withContext(Dispatchers.Main) {
                onResult(message)
            }
            if (success) {
                loadRuleCount()
            }
        }
    }

    fun importRules(uri: Uri, allowRules: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    reader?.readText()
                } ?: throw IOException("无法读取所选文件")
                val rules = if (allowRules) {
                    com.haoze.dnssr.vpn.AdGuardRuleParser.parseAllowAll(text)
                } else {
                    com.haoze.dnssr.vpn.AdGuardRuleParser.parseAll(text)
                }
                val inserted = if (allowRules) {
                    allowListManager.addRulesBatch(rules, LOCAL_IMPORT_SOURCE)
                } else {
                    blockListManager.addRulesBatch(rules, LOCAL_IMPORT_SOURCE)
                }
                if (inserted > 0) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        context,
                        if (allowRules) "allow_rules_imported" else "block_rules_imported"
                    )
                }
                withContext(Dispatchers.Main) {
                    val kind = if (allowRules) "白名单" else "屏蔽"
                    onResult("已导入 $inserted 条${kind}规则；${rules.size - inserted} 条重复或无效")
                }
                loadRuleCount()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult("导入失败：${e.message ?: "无法读取规则文件"}")
                }
            }
        }
    }

    private companion object {
        const val LOCAL_IMPORT_SOURCE = "local_import"
    }
}
