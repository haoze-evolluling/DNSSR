package com.haoze.dnssr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.data.entity.SubscriptionEntity
import com.haoze.dnssr.vpn.AllowListManager
import com.haoze.dnssr.vpn.BlockListManager
import com.haoze.dnssr.vpn.RewriteRuleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RuleListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val blockRuleDao = db.blockRuleDao()
    private val allowRuleDao = db.allowRuleDao()
    private val subscriptionDao = db.subscriptionDao()
    private val rewriteRuleDao = db.rewriteRuleDao()
    private val rewriteRuleManager by lazy { RewriteRuleManager(rewriteRuleDao, java.io.File(application.filesDir, "rule-index")) }
    private val blockListManager: BlockListManager by lazy {
        BlockListManager(blockRuleDao)
    }
    private val allowListManager: AllowListManager by lazy {
        AllowListManager(allowRuleDao)
    }

    private val pageSize = 100

    private val _rules = MutableStateFlow<List<RuleListItem>>(emptyList())
    val rules: StateFlow<List<RuleListItem>> = _rules.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sourceFilter = MutableStateFlow<RuleSourceFilter>(RuleSourceFilter.All)
    val sourceFilter: StateFlow<RuleSourceFilter> = _sourceFilter.asStateFlow()

    private val _sourceSubscriptions = MutableStateFlow<List<SubscriptionEntity>>(emptyList())
    val sourceSubscriptions: StateFlow<List<SubscriptionEntity>> = _sourceSubscriptions.asStateFlow()

    private var activated = false
    private var ruleKind = ManagedRuleKind.BLOCK

    fun setRuleKind(kind: ManagedRuleKind) {
        if (ruleKind == kind) return
        ruleKind = kind
        _searchQuery.value = ""
        _sourceFilter.value = RuleSourceFilter.All
        if (activated) {
            loadSourceSubscriptions()
            loadPage(1)
        }
    }

    fun activate() {
        if (!activated) {
            activated = true
        }
        loadSourceSubscriptions()
        loadPage(1)
    }

    fun loadPage(page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val query = _searchQuery.value.trim()
            val offset = (page - 1) * pageSize
            val rules = loadRules(query, pageSize, offset)
            val total = countRules(query)
            val pages = if (total == 0) 1 else (total + pageSize - 1) / pageSize
            withContext(Dispatchers.Main) {
                _rules.value = rules
                _currentPage.value = page.coerceAtMost(pages)
                _totalPages.value = pages
                _totalCount.value = total
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        loadPage(1)
    }

    fun selectSourceFilter(filter: RuleSourceFilter) {
        if (_sourceFilter.value == filter) return
        _sourceFilter.value = filter
        loadPage(1)
    }

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            if (ruleKind == ManagedRuleKind.REWRITE) {
                rewriteRuleManager.deleteRule(id)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "rewrite_rule_deleted")
            } else if (ruleKind == ManagedRuleKind.ALLOW) {
                allowListManager.deleteRule(id)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "allow_rule_deleted")
            } else {
                blockListManager.deleteRule(id)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "block_rule_deleted")
            }
            loadPage(_currentPage.value)
        }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            if (ruleKind == ManagedRuleKind.REWRITE) {
                rewriteRuleManager.toggleRule(id, enabled)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "rewrite_rule_toggled")
            } else if (ruleKind == ManagedRuleKind.ALLOW) {
                allowListManager.toggleRule(id, enabled)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "allow_rule_toggled")
            } else {
                blockListManager.toggleRule(id, enabled)
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "block_rule_toggled")
            }
            loadPage(_currentPage.value)
        }
    }

    private suspend fun loadRules(query: String, limit: Int, offset: Int): List<RuleListItem> {
        val source = _sourceFilter.value.source
        return if (ruleKind == ManagedRuleKind.REWRITE) {
            val rules = if (source == null) {
                if (query.isEmpty()) rewriteRuleDao.paged(limit, offset) else rewriteRuleDao.searchPaged("%$query%", limit, offset)
            } else {
                if (query.isEmpty()) rewriteRuleDao.pagedBySource(source, limit, offset) else rewriteRuleDao.searchPagedBySource(source, "%$query%", limit, offset)
            }
            rules.map { RuleListItem(it.id, it.pattern, "${it.pattern} -> ${it.targetValue}", it.enabled, it.targetType) }
        } else if (ruleKind == ManagedRuleKind.ALLOW) {
            val rules = if (source == null) {
                if (query.isEmpty()) allowRuleDao.paged(limit, offset)
                else allowRuleDao.searchPaged("%$query%", limit, offset)
            } else {
                if (query.isEmpty()) allowRuleDao.pagedBySource(source, limit, offset)
                else allowRuleDao.searchPagedBySource(source, "%$query%", limit, offset)
            }
            rules.map { RuleListItem(it.id, it.pattern, it.rawLine, it.enabled) }
        } else {
            val rules = if (source == null) {
                if (query.isEmpty()) blockRuleDao.paged(limit, offset)
                else blockRuleDao.searchPaged("%$query%", limit, offset)
            } else {
                if (query.isEmpty()) blockRuleDao.pagedBySource(source, limit, offset)
                else blockRuleDao.searchPagedBySource(source, "%$query%", limit, offset)
            }
            rules.map { RuleListItem(it.id, it.pattern, it.rawLine, it.enabled) }
        }
    }

    private suspend fun countRules(query: String): Int {
        val source = _sourceFilter.value.source
        return if (ruleKind == ManagedRuleKind.REWRITE) {
            if (source == null) { if (query.isEmpty()) rewriteRuleDao.count() else rewriteRuleDao.searchCount("%$query%") }
            else { if (query.isEmpty()) rewriteRuleDao.countBySourceForList(source) else rewriteRuleDao.searchCountBySource(source, "%$query%") }
        } else if (ruleKind == ManagedRuleKind.ALLOW) {
            if (source == null) {
                if (query.isEmpty()) allowRuleDao.count() else allowRuleDao.searchCount("%$query%")
            } else {
                if (query.isEmpty()) allowRuleDao.countBySourceForList(source)
                else allowRuleDao.searchCountBySource(source, "%$query%")
            }
        } else {
            if (source == null) {
                if (query.isEmpty()) blockRuleDao.count() else blockRuleDao.searchCount("%$query%")
            } else {
                if (query.isEmpty()) blockRuleDao.countBySourceForList(source)
                else blockRuleDao.searchCountBySource(source, "%$query%")
            }
        }
    }

    private fun loadSourceSubscriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val subscriptions = if (ruleKind == ManagedRuleKind.REWRITE) {
                subscriptionDao.allByKind(com.haoze.dnssr.data.entity.SubscriptionKind.REWRITE)
            } else if (ruleKind == ManagedRuleKind.ALLOW) {
                subscriptionDao.withAllowRules()
            } else {
                subscriptionDao.withBlockRules()
            }
            withContext(Dispatchers.Main) {
                _sourceSubscriptions.value = subscriptions
            }
        }
    }
}

sealed interface RuleSourceFilter {
    val source: String?

    data object All : RuleSourceFilter {
        override val source: String? = null
    }

    data object Manual : RuleSourceFilter {
        override val source = "useradd"
    }

    data class Subscription(val id: Long) : RuleSourceFilter {
        override val source = "sub_$id"
    }
}

enum class ManagedRuleKind(
    val title: String,
    val countLabel: String
) {
    BLOCK("屏蔽规则", "屏蔽规则"),
    ALLOW("放行规则", "白名单规则"),
    REWRITE("覆写域名规则", "覆写规则")
}

data class RuleListItem(
    val id: Long,
    val pattern: String,
    val rawLine: String,
    val enabled: Boolean,
    val targetType: String? = null
)
