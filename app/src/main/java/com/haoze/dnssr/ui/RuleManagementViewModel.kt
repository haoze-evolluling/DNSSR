package com.haoze.dnssr.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.RuleOperationScheduler
import com.haoze.dnssr.vpn.RuleOperationType
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
