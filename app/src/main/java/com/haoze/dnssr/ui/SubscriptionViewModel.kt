package com.haoze.dnssr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.SubscriptionManager
import com.haoze.dnssr.vpn.SubscriptionUpdateCoordinator
import com.haoze.dnssr.vpn.SubscriptionUpdateOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
            allowListManager
        )
    }

    private val _subscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val subscriptions: StateFlow<List<SubscriptionEntity>> = _subscriptions.asStateFlow()

    val importProgress: StateFlow<Pair<Int, Int>> = subscriptionManager.importProgress
    val importing: StateFlow<Boolean> = subscriptionManager.importing
    val importingSubscriptionId: StateFlow<Long?> = subscriptionManager.importingSubscriptionId

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
    }

    fun activate() {
        loadSubscriptions()
    }

    fun loadSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSubscriptionsIntoState()
        }
    }

    fun addSubscription(url: String, name: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subscriptionManager.addSubscription(url, name = name)
            if (result.isSuccess) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "subscription_added"
                )
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    _message.value = subscriptionManager.latestImportSummary()
                        ?.displayMessage("导入成功")
                        ?: "导入成功"
                } else {
                    _message.value = "导入失败：${result.exceptionOrNull()?.message}"
                }
            }
            loadSubscriptions()
        }
    }

    fun addLocalSubscription(uri: Uri, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val result = subscriptionManager.addLocalSubscription(uri.toString(), name) {
                context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    reader?.readText()
                } ?: throw IllegalArgumentException("无法读取所选文件")
            }
            if (result.isSuccess) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "local_subscription_added")
            }
            withContext(Dispatchers.Main) {
                _message.value = if (result.isSuccess) {
                    subscriptionManager.latestImportSummary()?.displayMessage("导入成功")
                        ?: "导入成功"
                } else {
                    "导入失败：${result.exceptionOrNull()?.message}"
                }
            }
            loadSubscriptions()
        }
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

    fun editSubscription(id: Long, url: String, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = subscriptionManager.editSubscription(id, url, name)
            if (result.isSuccess) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "subscription_edited"
                )
            }
            loadSubscriptionsIntoState()
            withContext(Dispatchers.Main) {
                _message.value = if (result.isSuccess) {
                    "订阅已保存"
                } else {
                    "保存订阅失败：${result.exceptionOrNull()?.message}"
                }
            }
        }
    }

    fun updateSubscription(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _updatingSubscriptionId.value = id
            try {
                val outcome = SubscriptionUpdateCoordinator.runManual {
                    subscriptionManager.updateSubscription(id)
                }
                if (outcome is SubscriptionUpdateOutcome.Updated) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        getApplication<Application>(),
                        "subscription_updated"
                    )
                }
                withContext(Dispatchers.Main) {
                    _message.value = when (outcome) {
                        is SubscriptionUpdateOutcome.Updated -> subscriptionManager.latestImportSummary()
                            ?.displayMessage("更新成功")
                            ?: "更新成功，共导入 ${outcome.ruleCount} 条规则"
                        is SubscriptionUpdateOutcome.NotModified -> "订阅已是最新"
                        is SubscriptionUpdateOutcome.Failed -> "更新失败：${outcome.error}"
                    }
                }
                loadSubscriptions()
            } finally {
                _updatingSubscriptionId.value = null
            }
        }
    }

    fun updateAllSubscriptions() {
        _operationMessage.value = "正在更新所有规则订阅..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var updated = 0
                var unchanged = 0
                var failed = 0
                var totalRules = 0
                SubscriptionUpdateCoordinator.runManual {
                    subscriptionManager.remoteSubscriptions().forEach { subscription ->
                        _updatingSubscriptionId.value = subscription.id
                        try {
                            when (val outcome = subscriptionManager.updateSubscription(subscription.id)) {
                                is SubscriptionUpdateOutcome.Updated -> {
                                    updated++
                                    totalRules += outcome.ruleCount
                                }
                                is SubscriptionUpdateOutcome.NotModified -> unchanged++
                                is SubscriptionUpdateOutcome.Failed -> failed++
                            }
                        } finally {
                            _updatingSubscriptionId.value = null
                        }
                    }
                }
                if (updated > 0) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        getApplication<Application>(),
                        "subscriptions_updated"
                    )
                }
                loadSubscriptionsIntoState()
                withContext(Dispatchers.Main) {
                    _message.value = when {
                        failed == 0 -> "检查完成：更新 $updated 个，已是最新 $unchanged 个，共导入 $totalRules 条规则"
                        updated + unchanged == 0 -> "更新所有订阅失败"
                        else -> "检查完成：更新 $updated 个，已是最新 $unchanged 个，失败 $failed 个"
                    }
                }
            } finally {
                _operationMessage.value = null
            }
        }
    }

    fun deleteSubscription(id: Long) {
        _operationMessage.value = "正在删除规则订阅..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                subscriptionManager.deleteSubscription(id)
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "subscription_deleted"
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
                val result = subscriptionManager.setSubscriptionEnabled(id, enabled)
                if (result.isSuccess) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        getApplication<Application>(),
                        "subscription_toggled"
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

    private suspend fun loadSubscriptionsIntoState() {
        val list = subscriptionManager.allSubscriptions()
        withContext(Dispatchers.Main) {
            _subscriptions.value = list
        }
    }
}
