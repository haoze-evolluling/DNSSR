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

    fun importRules(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    reader?.readText()
                } ?: throw IOException("无法读取所选文件")
                val rules = com.haoze.dnssr.vpn.AdGuardRuleParser.parseCategorized(text)
                if (rules.isEmpty()) throw IllegalArgumentException("文件中没有可导入的有效 DNS 规则")
                val insertedBlock = blockListManager.addRulesBatch(rules.blockRules, LOCAL_IMPORT_SOURCE)
                val insertedAllow = allowListManager.addRulesBatch(rules.allowRules, LOCAL_IMPORT_SOURCE)
                if (insertedBlock + insertedAllow > 0) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        context,
                        "rules_imported"
                    )
                }
                withContext(Dispatchers.Main) {
                    val databaseDuplicates = rules.size - insertedBlock - insertedAllow
                    onResult(
                        "导入完成：黑名单 $insertedBlock 条，白名单 $insertedAllow 条，" +
                            "重复 ${rules.duplicateCount + databaseDuplicates.coerceAtLeast(0)} 条，" +
                            "无效/不支持 ${rules.invalidCount + rules.unsupportedCount} 条"
                    )
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
