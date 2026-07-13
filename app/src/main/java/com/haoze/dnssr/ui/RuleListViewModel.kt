package com.haoze.dnssr.ui

import android.app.Application
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

class RuleListViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val blockRuleDao = db.blockRuleDao()
    private val allowRuleDao = db.allowRuleDao()
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

    private var activated = false
    private var ruleKind = ManagedRuleKind.BLOCK

    fun setRuleKind(kind: ManagedRuleKind) {
        if (ruleKind == kind) return
        ruleKind = kind
        _searchQuery.value = ""
        if (activated) {
            loadPage(1)
        }
    }

    fun activate() {
        if (!activated) {
            activated = true
            loadPage(1)
        }
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

    fun deleteRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            if (ruleKind == ManagedRuleKind.ALLOW) {
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
            if (ruleKind == ManagedRuleKind.ALLOW) {
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
        return if (ruleKind == ManagedRuleKind.ALLOW) {
            val rules = if (query.isEmpty()) {
                allowRuleDao.paged(limit, offset)
            } else {
                allowRuleDao.searchPaged("%$query%", limit, offset)
            }
            rules.map { RuleListItem(it.id, it.pattern, it.rawLine, it.enabled) }
        } else {
            val rules = if (query.isEmpty()) {
                blockRuleDao.paged(limit, offset)
            } else {
                blockRuleDao.searchPaged("%$query%", limit, offset)
            }
            rules.map { RuleListItem(it.id, it.pattern, it.rawLine, it.enabled) }
        }
    }

    private suspend fun countRules(query: String): Int {
        return if (ruleKind == ManagedRuleKind.ALLOW) {
            if (query.isEmpty()) allowRuleDao.count() else allowRuleDao.searchCount("%$query%")
        } else {
            if (query.isEmpty()) blockRuleDao.count() else blockRuleDao.searchCount("%$query%")
        }
    }
}

enum class ManagedRuleKind(
    val title: String,
    val countLabel: String
) {
    BLOCK("已添加的屏蔽规则", "屏蔽规则"),
    ALLOW("已添加的白名单规则", "白名单规则")
}

data class RuleListItem(
    val id: Long,
    val pattern: String,
    val rawLine: String,
    val enabled: Boolean
)
