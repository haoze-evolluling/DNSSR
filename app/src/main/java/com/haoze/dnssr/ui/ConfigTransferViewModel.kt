package com.haoze.dnssr.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ConfigTransferOperation {
    IDLE,
    EXPORTING,
    IMPORTING
}

class ConfigTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val manager by lazy { ConfigTransferManager(application) }

    private val _operation = MutableStateFlow(ConfigTransferOperation.IDLE)
    val operation: StateFlow<ConfigTransferOperation> = _operation.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importProgress = MutableStateFlow(ConfigImportProgress(0, 0, "正在读取配置文件"))
    val importProgress: StateFlow<ConfigImportProgress> = _importProgress.asStateFlow()

    fun export(uri: Uri, selection: ConfigExportSelection) {
        runOperation(ConfigTransferOperation.EXPORTING) {
            val context = getApplication<Application>()
            val content = manager.export(selection)
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter().use { writer ->
                requireNotNull(writer) { "无法打开导出文件" }
                writer.write(content)
            }
            "配置已导出"
        }
    }

    fun exportRules(uri: Uri) {
        runOperation(ConfigTransferOperation.EXPORTING) {
            val context = getApplication<Application>()
            val result = manager.exportRules()
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter().use { writer ->
                requireNotNull(writer) { "无法打开导出文件" }
                writer.write(result.content)
            }
            "规则已导出：拦截 ${result.blockRuleCount} 条，白名单 ${result.allowRuleCount} 条"
        }
    }

    fun import(uri: Uri) {
        _importProgress.value = ConfigImportProgress(0, 0, "正在读取配置文件")
        runOperation(ConfigTransferOperation.IMPORTING, MIN_IMPORT_DURATION_MILLIS) {
            val context = getApplication<Application>()
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                requireNotNull(reader) { "无法读取配置文件" }
                reader.readText()
            }
            val result = manager.import(content) { progress ->
                _importProgress.value = progress
            }
            if (result.excludedAppsUpdated) {
                RuntimeDnsSettingsRefresher.refreshAppExclusionsIfRunning(context)
            } else {
                RuntimeDnsSettingsRefresher.refreshIfRunning(context, "configuration_imported")
            }
            result.message()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun runOperation(
        operation: ConfigTransferOperation,
        minimumDurationMillis: Long = 0,
        block: suspend () -> String
    ) {
        if (_operation.value != ConfigTransferOperation.IDLE) return
        _operation.value = operation
        viewModelScope.launch(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            val message = try {
                block()
            } catch (e: Exception) {
                "操作失败：${e.message ?: "未知错误"}"
            }
            delay((minimumDurationMillis - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0))
            withContext(Dispatchers.Main) {
                _message.value = message
                _operation.value = ConfigTransferOperation.IDLE
            }
        }
    }

    private companion object {
        const val MIN_IMPORT_DURATION_MILLIS = 3_000L
    }
}
