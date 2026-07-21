package com.haoze.dnssr.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.data.entity.MirrorTemplateEntity
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.SubscriptionManager
import com.haoze.dnssr.vpn.SubscriptionAutoUpdateScheduler
import com.haoze.dnssr.vpn.RuleOperationScheduler
import com.haoze.dnssr.vpn.RuleOperationType
import com.haoze.dnssr.vpn.SubscriptionUpdateCoordinator
import com.haoze.dnssr.vpn.SubscriptionUpdateOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val blockListManager: BlockListManager by lazy {
        BlockListManager(AppDatabase.getInstance(application).blockRuleDao())
    }
    private val allowListManager: AllowListManager by lazy {
        AllowListManager(AppDatabase.getInstance(application).allowRuleDao())
    }

    private val subscriptionManager: SubscriptionManager by lazy {
        SubscriptionManager(
            AppDatabase.getInstance(application).subscriptionDao(),
            blockListManager,
            allowListManager,
            com.haoze.dnssr.vpn.RewriteRuleManager(AppDatabase.getInstance(application).rewriteRuleDao(), java.io.File(application.filesDir, "rule-index"))
        )
    }

    private val _subscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionEntity>> = _subscriptions.asStateFlow()
    val mirrorTemplates = AppDatabase.getInstance(application).mirrorTemplateDao().observeAll()

    private val _importProgress = MutableStateFlow(-1 to 0)
    val importProgress: StateFlow<Pair<Int, Int>> = _importProgress.asStateFlow()
    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()
    private val _importingSubscriptionId = MutableStateFlow<Long?>(null)
    val importingSubscriptionId: StateFlow<Long?> = _importingSubscriptionId.asStateFlow()

    private val _updatingSubscriptionId = MutableStateFlow<Long?>(null)
    val updatingSubscriptionId: StateFlow<Long?> = _updatingSubscriptionId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    init {
        viewModelScope.launch {
            AppDatabase.getInstance(application).subscriptionDao().observeAll().collectLatest { list ->
                _subscriptions.value = list
            }
        }
        viewModelScope.launch {
            val workManager = WorkManager.getInstance(application)
            combine(
                workManager.getWorkInfosByTagFlow(RuleOperationScheduler.TAG),
                workManager.getWorkInfosByTagFlow(SubscriptionAutoUpdateScheduler.WORK_TAG)
            ) { manual, automatic -> manual + automatic }
                .collectLatest(::applyBackgroundWorkState)
        }
    }

    fun activate() {
        loadSubscriptions()
    }

    fun loadSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSubscriptionsIntoState()
        }
    }

    fun addSubscription(
        url: String,
        name: String? = null,
        kind: String = com.haoze.dnssr.data.entity.SubscriptionKind.BLOCK,
        mirrorTemplate: String? = null,
        mirrorFallback: Boolean = true
    ) {
        enqueueAndObserve(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.ADD_SUBSCRIPTION, url = url, name = name, kind = kind,
                mirrorTemplate = mirrorTemplate, mirrorFallback = mirrorFallback
            ).id
        )
    }

    fun addLocalSubscription(uri: Uri, name: String, kind: String = com.haoze.dnssr.data.entity.SubscriptionKind.BLOCK) {
        runCatching {
            getApplication<Application>().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        enqueueAndObserve(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.ADD_LOCAL_SUBSCRIPTION, name = name, uri = uri, kind = kind
            ).id
        )
    }

    fun renameSubscription(id: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subscriptionManager.renameSubscription(id, name)
            loadSubscriptionsIntoState()
            withContext(Dispatchers.Main) {
                _message.value = if (result.isSuccess) {
                    "已重命名规则订阅"
                } else {
                    "重命名失败：${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun editSubscription(
        id: Long,
        url: String,
        name: String,
        mirrorTemplate: String?,
        mirrorFallback: Boolean
    ) {
        enqueueAndObserve(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.EDIT_SUBSCRIPTION,
                subscriptionId = id, url = url, name = name,
                mirrorTemplate = mirrorTemplate, mirrorFallback = mirrorFallback
            ).id
        )
    }

    fun updateSubscription(id: Long) {
        enqueueAndObserve(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.UPDATE_SUBSCRIPTION, subscriptionId = id
            ).id
        )
    }

    fun updateAllSubscriptions() {
        enqueueAndObserve(
            RuleOperationScheduler.enqueue(
                getApplication(), RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS
            ).id
        )
    }

    fun deleteSubscription(id: Long) {
        _operationMessage.value = "正在删除规则订阅..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val subscription = AppDatabase.getInstance(getApplication<Application>()).subscriptionDao().byId(id)
                subscriptionManager.deleteSubscription(id)
                val isRewrite = subscription?.kind == com.haoze.dnssr.data.entity.SubscriptionKind.REWRITE
                RuntimeDnsSettingsRefresher.refreshRuleIndexesIfRunning(
                    getApplication<Application>(), !isRewrite, !isRewrite, isRewrite
                )
                loadSubscriptionsIntoState()
                withContext(Dispatchers.Main) {
                    _message.value = "已删除规则订阅"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _message.value = "删除失败：${e.message}"
                }
            } finally {
                _operationMessage.value = null
            }
        }
    }

    fun toggleSubscriptionEnabled(id: Long, enabled: Boolean) {
        _operationMessage.value = if (enabled) {
            "正在启用规则订阅..."
        } else {
            "正在禁用规则订阅..."
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val subscription = AppDatabase.getInstance(getApplication<Application>()).subscriptionDao().byId(id)
                val result = subscriptionManager.setSubscriptionEnabled(id, enabled)
                if (result.isSuccess) {
                    val isRewrite = subscription?.kind == com.haoze.dnssr.data.entity.SubscriptionKind.REWRITE
                    RuntimeDnsSettingsRefresher.refreshRuleIndexesIfRunning(
                        getApplication<Application>(), !isRewrite, !isRewrite, isRewrite
                    )
                    loadSubscriptionsIntoState()
                }
                withContext(Dispatchers.Main) {
                    _message.value = if (result.isSuccess) {
                        if (enabled) "已启用规则订阅" else "已禁用规则订阅"
                    } else {
                        "切换失败：${result.exceptionOrNull()?.message}"
                    }
                }
            } finally {
                _operationMessage.value = null
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun enqueueAndObserve(workId: java.util.UUID) {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication<Application>())
                .getWorkInfoByIdFlow(workId)
                .collectLatest { info ->
                    if (info?.state?.isFinished == true) {
                        val message = info.outputData.getString(RuleOperationScheduler.KEY_MESSAGE)
                        val success = info.outputData.getBoolean(RuleOperationScheduler.KEY_SUCCESS, false)
                        _message.value = if (success) message else "操作失败：$message"
                        loadSubscriptions()
                        return@collectLatest
                    }
                }
        }
    }

    private fun applyBackgroundWorkState(infos: List<WorkInfo>) {
        val active = infos.firstOrNull {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED
        }
        val type = active?.progress?.getString(RuleOperationScheduler.KEY_TYPE)
            ?.let { runCatching { RuleOperationType.valueOf(it) }.getOrNull() }
        val subscriptionOperation = type in setOf(
            RuleOperationType.ADD_SUBSCRIPTION,
            RuleOperationType.ADD_LOCAL_SUBSCRIPTION,
            RuleOperationType.EDIT_SUBSCRIPTION,
            RuleOperationType.UPDATE_SUBSCRIPTION,
            RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS
        )
        _importing.value = active != null && subscriptionOperation
        val id = active?.progress?.getLong(RuleOperationScheduler.KEY_SUBSCRIPTION_ID, -1) ?: -1
        _importingSubscriptionId.value = id.takeIf { it >= 0 }
        _updatingSubscriptionId.value = _importingSubscriptionId.value
        _importProgress.value = if (active != null) {
            active.progress.getInt(RuleOperationScheduler.KEY_CURRENT, -1) to
                active.progress.getInt(RuleOperationScheduler.KEY_TOTAL, 0)
        } else {
            -1 to 0
        }
        _operationMessage.value = if (type == RuleOperationType.UPDATE_ALL_SUBSCRIPTIONS) {
            "正在更新所有规则订阅..."
        } else null
    }

    private suspend fun loadSubscriptionsIntoState() {
        val list = subscriptionManager.allSubscriptions()
        withContext(Dispatchers.Main) {
            _subscriptions.value = list
        }
    }
}
