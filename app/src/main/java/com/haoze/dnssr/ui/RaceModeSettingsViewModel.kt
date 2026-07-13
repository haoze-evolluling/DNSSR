package com.haoze.dnssr.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haoze.dnssr.data.AppDatabase
import com.haoze.dnssr.vpn.BootstrapHealthEngine
import com.haoze.dnssr.vpn.BootstrapLogger
import com.haoze.dnssr.vpn.BootstrapSelector
import com.haoze.dnssr.vpn.DnsLatencyTester
import com.haoze.dnssr.vpn.DnsProvider
import com.haoze.dnssr.vpn.ProviderHealthEngine
import com.haoze.dnssr.vpn.ProviderHealthSnapshot
import com.haoze.dnssr.vpn.ProviderHealthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class RaceModeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val bootstrapHealthEngine = BootstrapHealthEngine(application, viewModelScope)
    private val bootstrapLogger = BootstrapLogger(
        AppDatabase.getInstance(application).bootstrapLogDao(),
        AppSettings.logRetentionDays(application)
    )
    private val bootstrapSelector = BootstrapSelector(
        context = application,
        healthEngine = bootstrapHealthEngine,
        logger = bootstrapLogger
    )

    private val _providers = MutableStateFlow<List<DnsProvider>>(emptyList())
    val providers: StateFlow<List<DnsProvider>> = _providers.asStateFlow()

    private val _healthByProvider = MutableStateFlow<Map<String, ProviderHealthSnapshot>>(emptyMap())
    val healthByProvider: StateFlow<Map<String, ProviderHealthSnapshot>> = _healthByProvider.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _latencyTestSelectedIds = MutableStateFlow<Set<String>>(emptySet())
    val latencyTestSelectedIds: StateFlow<Set<String>> = _latencyTestSelectedIds.asStateFlow()

    private val _raceModeEnabled = MutableStateFlow(false)
    val raceModeEnabled: StateFlow<Boolean> = _raceModeEnabled.asStateFlow()

    private val _raceModeStrategy = MutableStateFlow(RaceModeStrategy.SMART_PREDICTION)
    val raceModeStrategy: StateFlow<RaceModeStrategy> = _raceModeStrategy.asStateFlow()

    private val _testDomain = MutableStateFlow("")
    val testDomain: StateFlow<String> = _testDomain.asStateFlow()

    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    private val _results = MutableStateFlow<List<DnsLatencyTester.Result>>(emptyList())
    val results: StateFlow<List<DnsLatencyTester.Result>> = _results.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _initialLoading = MutableStateFlow(true)
    val initialLoading: StateFlow<Boolean> = _initialLoading.asStateFlow()

    private var activated = false

    fun activate() {
        if (!activated) {
            activated = true
            load()
        }
    }

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            ProviderHealthEngine.flushActive(commit = true)
            val all = DnsProvider.loadRuntimeProviders(context)
            val health = ProviderHealthStore.loadAll(context)
            val ids = DnsProvider.loadRaceProviderIds(context)
            val latencyIds = DnsProvider.loadLatencyTestProviderIds(context)
            val domain = AppSettings.getRaceTestDomain(context)
            val raceModeEnabled = AppSettings.isRaceModeEnabled(context)
            val strategy = AppSettings.getRaceModeStrategy(context)
            withContext(Dispatchers.Main) {
                _providers.value = all
                _healthByProvider.value = health
                _selectedIds.value = ids
                _latencyTestSelectedIds.value = latencyIds
                _raceModeEnabled.value = raceModeEnabled
                _raceModeStrategy.value = strategy
                _testDomain.value = domain
                _results.value = emptyList()
                _initialLoading.value = false
            }
        }
    }

    fun toggleProvider(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val updated = _selectedIds.value.toMutableSet().apply {
                if (contains(id)) remove(id) else add(id)
            }
            DnsProvider.saveRaceProviderIds(context, updated)
            RuntimeDnsSettingsRefresher.refreshIfRunning(context, "race_providers_changed")
            withContext(Dispatchers.Main) {
                _selectedIds.value = updated
            }
        }
    }

    fun toggleLatencyTestProvider(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val updated = _latencyTestSelectedIds.value.toMutableSet().apply {
                if (contains(id)) remove(id) else add(id)
            }
            DnsProvider.saveLatencyTestProviderIds(context, updated)
            withContext(Dispatchers.Main) {
                _latencyTestSelectedIds.value = updated
            }
        }
    }

    fun setRaceModeStrategy(strategy: RaceModeStrategy): Boolean {
        if (_raceModeStrategy.value == strategy) return false
        val context = getApplication<Application>()
        AppSettings.setRaceModeStrategy(context, strategy)
        _raceModeStrategy.value = strategy
        return true
    }

    fun setTestDomain(domain: String) {
        _testDomain.value = domain
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            AppSettings.setRaceTestDomain(context, domain)
        }
    }

    fun setRaceModeEnabled(enabled: Boolean): Boolean {
        val context = getApplication<Application>()
        if (enabled && _selectedIds.value.size < 2) {
            _message.value = "至少选择 2 个服务商后才能启用竞速模式"
            return false
        }
        AppSettings.setRaceModeEnabled(context, enabled)
        _raceModeEnabled.value = enabled
        _message.value = if (enabled) "已启用竞速模式" else "已关闭竞速模式"
        return true
    }

    fun runLatencyTest() {
        val context = getApplication<Application>()
        val domain = _testDomain.value.trim().takeIf { it.isNotEmpty() }
            ?: AppSettings.getRaceTestDomain(context)
        val selected = _providers.value.filter { it.id in _latencyTestSelectedIds.value }
        if (selected.isEmpty()) {
            _message.value = "请先选择要测速的服务商"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isTesting.value = true
            _results.value = emptyList()
            val deferreds = selected.map { provider ->
                async {
                    DnsLatencyTester.testAverage(
                        context = context,
                        provider = provider,
                        domain = domain,
                        attempts = LATENCY_TEST_ATTEMPTS,
                        bootstrapSelector = bootstrapSelector
                    )
                }
            }
            val testResults = deferreds.map { it.await() }
                .sortedWith(
                    compareBy<DnsLatencyTester.Result> { if (it.success) 0 else 1 }
                        .thenBy { if (it.success) it.elapsedMs else Long.MAX_VALUE }
                        .thenByDescending { it.successCount }
                        .thenBy { it.providerName }
                )
            bootstrapLogger.flush()
            withContext(Dispatchers.Main) {
                _results.value = testResults
                _isTesting.value = false
            }
        }
    }

    fun resetSelectedProviderWeights() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val providerIds = _selectedIds.value.ifEmpty {
                _providers.value.map { it.id }.toSet()
            }
            ProviderHealthStore.reset(context, providerIds)
            ProviderHealthEngine.flushActive(commit = true)
            val health = ProviderHealthStore.loadAll(context)
            withContext(Dispatchers.Main) {
                _healthByProvider.value = health
                _message.value = "已恢复默认权重"
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        bootstrapHealthEngine.close()
        runBlocking {
            bootstrapLogger.flush()
        }
        super.onCleared()
    }

    private companion object {
        const val LATENCY_TEST_ATTEMPTS = 3
    }
}
