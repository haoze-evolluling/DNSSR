package com.haoze.dnssr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

data class SettingsGuide(
    val id: String,
    val title: String,
    val message: String
)

object SettingsGuides {
    val PROVIDER_MANAGEMENT = SettingsGuide("provider_management", "服务商管理", "服务商决定实际使用哪个 DNS 服务进行查询，可在这里添加、编辑、启用或排序 DoH、DoT 等服务。")
    val BOOTSTRAP = SettingsGuide("bootstrap", "Bootstrap 设置", "Bootstrap 用于先解析 DNS 服务商自身的域名，配置不当可能导致依赖域名的服务商无法连接。")
    val LATENCY_TEST = SettingsGuide("latency_test", "查询测速", "查询测速会使用指定域名比较各服务商的实际解析耗时，结果会随网络环境和目标域名变化。")
    val CACHE = SettingsGuide("cache", "缓存设置", "缓存会复用近期解析结果以减少重复查询；缓存策略会影响响应速度、数据新鲜度和断网时的容错表现。")
    val RESOLUTION_MODE = SettingsGuide("resolution_mode", "解析模式", "解析模式决定服务商的查询方式，在速度、耗电、网络请求量和故障容忍之间取舍。")
    val LOG_MODE = SettingsGuide("log_mode", "日志模式", "日志模式决定记录哪些 DNS 请求；记录范围越大，排查越方便，但会保留更多本机历史数据。")
    val CONFIG_TRANSFER = SettingsGuide("config_transfer", "导入与导出", "导入与导出可迁移设置、服务商和规则订阅。导入会覆盖或合并本机对应配置，请先确认文件来源。")
    val DOMAIN_RULES = SettingsGuide("domain_rules", "域名规则", "域名规则用于屏蔽或放行域名，并可管理规则订阅；规则会直接影响匹配域名能否解析。")
    val DATA_CLEANUP = SettingsGuide("data_cleanup", "数据清理", "数据清理会删除所选的本机数据。执行前请确认范围，删除后的缓存、日志或规则无法恢复。")
    val SERVICE_DISPLAY = SettingsGuide("service_display", "服务显示", "服务显示只控制首页解析服务列表中展示的协议和服务商，不会改变实际查询配置。")
    val APPEARANCE = SettingsGuide("appearance", "外观设置", "外观设置用于调整主题、颜色和首页显示效果，仅改变应用界面呈现。")
    val FOREGROUND_BACKGROUND = SettingsGuide("foreground_background", "前后台行为", "前后台行为控制通知常驻、最近任务显示等运行表现，部分选项可能影响系统对服务的管理方式。")
    val EXCLUDED_APPS = SettingsGuide("excluded_apps", "排除应用", "排除应用会改用系统 DNS，不再经过 DNSSR 的 DNS 解析和域名规则。")
    val HTTP_INSPECTION = SettingsGuide("http_inspection", "HTTP(S) 流量过滤", "")
    val EXPERIMENTAL_FEATURES = SettingsGuide("experimental_features", "实验功能", "实验功能仍在开发或验证中，行为和兼容性可能变化，不建议在依赖稳定性的场景中启用。")
    val ABOUT = SettingsGuide("about", "应用信息", "应用信息提供软件说明、作者信息和项目资料，便于了解项目与获取支持。")
    val SPONSOR = SettingsGuide("sponsor", "赞助", "赞助页面提供支持项目持续开发的方式。")
    val SPONSOR_LIST = SettingsGuide("sponsor_list", "赞助者名单", "赞助者名单用于感谢支持 DNSSR 项目的朋友。")
    val CO_BUILDER_LIST = SettingsGuide("co_builder_list", "共建者名单", "共建者名单用于感谢提出建议、反馈问题和参与测试的共建者。")
}

@Composable
fun SettingsGuideHost(
    guide: SettingsGuide,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var showGuide by remember(guide.id) {
        mutableStateOf(!AppSettings.isSettingsGuideAcknowledged(context, guide.id))
    }

    content()

    if (showGuide) {
        BackHandler(enabled = true) {}
        AlertDialog(
            onDismissRequest = {},
            title = { Text(guide.title) },
            text = { Text(guide.message) },
            confirmButton = {
                TextButton(onClick = {
                    AppSettings.acknowledgeSettingsGuide(context, guide.id)
                    showGuide = false
                }) {
                    Text("我知道了")
                }
            }
        )
    }
}
