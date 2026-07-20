package com.haoze.dnssr.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.MirrorTemplateEntity
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.RuleOperationScheduler
import com.haoze.dnssr.vpn.RuleOperationType
import com.haoze.dnssr.vpn.RewriteRuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private val rewriteRuleManager: RewriteRuleManager by lazy {
        RewriteRuleManager(AppDatabase.getInstance(application).rewriteRuleDao(), java.io.File(application.filesDir, "rule-index"))
    }

    private val _ruleCount = MutableStateFlow(0)
    val ruleCount: StateFlow<Int> = _ruleCount.asStateFlow()

    private val _allowRuleCount = MutableStateFlow(0)
    val allowRuleCount: StateFlow<Int> = _allowRuleCount.asStateFlow()
    private val _rewriteRuleCount = MutableStateFlow(0)
    val rewriteRuleCount: StateFlow<Int> = _rewriteRuleCount.asStateFlow()
    val mirrorTemplates = AppDatabase.getInstance(application).mirrorTemplateDao().observeAll()

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
            val rewriteCount = rewriteRuleManager.count()
            withContext(Dispatchers.Main) {
                _ruleCount.value = count
                _allowRuleCount.value = allowCount
                _rewriteRuleCount.value = rewriteCount
            }
        }
    }

    fun addRule(pattern: String, onResult: (String) -> Unit) {
        observeResult(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.ADD_BLOCK_RULE, pattern = pattern
            ).id,
            onResult
        )
    }

    fun addAllowRule(pattern: String, onResult: (String) -> Unit) {
        observeResult(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.ADD_ALLOW_RULE, pattern = pattern
            ).id,
            onResult
        )
    }

    fun importRules(uri: Uri, onResult: (String) -> Unit) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        observeResult(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.IMPORT_RULES, uri = uri
            ).id,
            onResult
        )
    }

    fun addRewriteRule(domain: String, targetType: String, targetValue: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = rewriteRuleManager.addRule(domain, targetType, targetValue)
            if (success) RuntimeDnsSettingsRefresher.refreshIfRunning(getApplication(), "rewrite_rule_added")
            withContext(Dispatchers.Main) {
                onResult(if (success) "已添加覆写域名" else "域名、目标格式无效、规则冲突或已存在")
                loadRuleCount()
            }
        }
    }

    fun addMirrorTemplate(name: String, template: String, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                require(name.trim().isNotEmpty()) { "镜像站名称不能为空" }
                require(template.trim().startsWith("http://") || template.trim().startsWith("https://")) { "模板必须使用 HTTP 或 HTTPS" }
                require(listOf("{url}", "{urlEncoded}", "{scheme}", "{host}", "{path}", "{pathAndQuery}").any { it in template }) { "模板缺少 URL 占位符" }
                AppDatabase.getInstance(getApplication<Application>()).mirrorTemplateDao().insert(
                    MirrorTemplateEntity(name = name.trim(), template = template.trim())
                )
            }
            withContext(Dispatchers.Main) { onResult(if (result.isSuccess) "已添加镜像站模板" else result.exceptionOrNull()?.message ?: "添加失败") }
        }
    }

    fun deleteMirrorTemplate(template: MirrorTemplateEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.getInstance(getApplication<Application>()).mirrorTemplateDao().delete(template)
        }
    }

    fun importHostsRules(uri: Uri, onResult: (String) -> Unit) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        observeResult(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.IMPORT_HOSTS_RULES, uri = uri
            ).id,
            onResult
        )
    }

    private fun observeResult(workId: java.util.UUID, onResult: (String) -> Unit) {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfoByIdFlow(workId)
                .collect { info ->
                    if (info?.state?.isFinished == true) {
                        val success = info.outputData.getBoolean(RuleOperationScheduler.KEY_SUCCESS, false)
                        val message = info.outputData.getString(RuleOperationScheduler.KEY_MESSAGE)
                            ?: "操作失败"
                        onResult(if (success) message else "操作失败：$message")
                        loadRuleCount()
                        return@collect
                    }
                }
        }
    }

}
