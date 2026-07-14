package com.haoze.dnssr.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.data.entity.SubscriptionKind
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _selectedKind = MutableStateFlow(SubscriptionKind.BLOCK)
    val selectedKind: StateFlow<String> = _selectedKind.asStateFlow()

    val importProgress: StateFlow<Pair<Int, Int>> = subscriptionManager.importProgress
    val importing: StateFlow<Boolean> = subscriptionManager.importing

    private val _updatingSubscriptionId = MutableStateFlow<Long?>(null)
    val updatingSubscriptionId: StateFlow<Long?> = _updatingSubscriptionId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage: StateFlow<String?> = _operationMessage.asStateFlow()

    private var activated = false

    fun activate() {
        if (!activated) {
            activated = true
            loadSubscriptions()
        }
    }

    fun loadSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            loadSubscriptionsIntoState()
        }
    }

    fun setSelectedKind(kind: String) {
        val normalized = if (kind == SubscriptionKind.ALLOW) SubscriptionKind.ALLOW else SubscriptionKind.BLOCK
        if (_selectedKind.value == normalized) return
        _selectedKind.value = normalized
        loadSubscriptions()
    }

    fun addSubscription(url: String, name: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val kind = _selectedKind.value
            val result = subscriptionManager.addSubscription(url, kind, name)
            if (result.isSuccess) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(
                    getApplication<Application>(),
                    "subscription_added"
                )
            }
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val sub = result.getOrNull()
                    _message.value = "导入成功，共添加 ${sub?.ruleCount ?: 0} 条${kind.ruleKindLabel()}规则"
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
            val kind = _selectedKind.value
            val result = try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    reader?.readText()
                } ?: throw IllegalArgumentException("无法读取所选文件")
                subscriptionManager.addLocalSubscription(uri.toString(), name, content, kind)
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) {
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "local_subscription_added")
            }
            withContext(Dispatchers.Main) {
                _message.value = if (result.isSuccess) {
                    "导入成功，共添加 ${result.getOrNull()?.ruleCount ?: 0} 条${kind.ruleKindLabel()}规则"
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
                val result = subscriptionManager.updateSubscription(id)
                if (result.isSuccess) {
                    RuntimeDnsSettingsRefresher.refreshIfRunning(
                        getApplication<Application>(),
                        "subscription_updated"
                    )
                }
                withContext(Dispatchers.Main) {
                    if (result.isSuccess) {
                        _message.value = "更新成功，共导入 ${result.getOrNull() ?: 0} 条规则"
                    } else {
                        _message.value = "更新失败：${result.exceptionOrNull()?.message}"
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
                var failed = 0
                var totalRules = 0
                subscriptionManager.remoteSubscriptions().forEach { subscription ->
                    _updatingSubscriptionId.value = subscription.id
                    try {
                        subscriptionManager.updateSubscription(subscription.id).fold(
                            onSuccess = { ruleCount ->
                                updated++
                                totalRules += ruleCount
                            },
                            onFailure = { failed++ }
                        )
                    } finally {
                        _updatingSubscriptionId.value = null
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
                        failed == 0 -> "已更新 $updated 个订阅，共导入 $totalRules 条规则"
                        updated == 0 -> "更新所有订阅失败"
                        else -> "已更新 $updated 个订阅，$failed 个更新失败"
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

    private fun String.ruleKindLabel(): String {
        return if (this == SubscriptionKind.ALLOW) "白名单" else "屏蔽"
    }

    private suspend fun loadSubscriptionsIntoState() {
        val list = subscriptionManager.subscriptionsByKind(_selectedKind.value)
        withContext(Dispatchers.Main) {
            _subscriptions.value = list
        }
    }
}
