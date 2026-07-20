package com.haoze.dnssr.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import com.haoze.dnssr.ui.components.AppAlertDialog as AlertDialog
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
    val PROVIDER_MANAGEMENT = SettingsGuide("provider_management", "服务商管理", "服务商决定 DNSSR 实际通过哪些上游 DNS 完成域名查询。你可以在这里添加、编辑、启用、停用或调整 DoH、DoT 等服务的顺序，并为不同服务填写地址与连接参数。启用多个可靠服务通常能提高可用性，但错误的地址、协议或证书配置可能导致解析失败。修改后建议回到首页观察服务状态，并通过查询测速确认当前网络下的连接效果。")
    val BOOTSTRAP = SettingsGuide("bootstrap", "Bootstrap 设置", "Bootstrap DNS 用于在连接加密 DNS 服务商之前，先解析该服务商自身的域名，从而避免解析过程形成循环依赖。你可以配置全局 Bootstrap 地址以及相关权重策略。若填写的服务器不可访问、响应过慢或返回错误结果，依赖域名连接的 DoH、DoT 服务可能无法正常工作。没有明确需求时建议保留可靠的默认配置，修改后应结合服务状态和实际解析结果进行确认。")
    val LATENCY_TEST = SettingsGuide("latency_test", "查询测速", "查询测速会使用你指定的测试域名，向选中的 DNS 服务商发起真实查询，并比较各服务商在当前网络环境中的解析耗时。测试结果会受到网络波动、缓存状态、目标域名和服务商负载影响，因此单次结果不代表长期表现。建议使用经常访问且稳定的域名，多执行几次后综合判断。测速只用于辅助选择服务商，不会自动替你修改当前启用状态或解析策略。")
    val CACHE = SettingsGuide("cache", "缓存设置", "DNS 缓存会保存近期的解析结果，在有效期内复用响应，从而减少重复网络请求并提升访问速度。你可以在这里调整缓存容量、有效期以及相关容错行为。更大的缓存或更长的保留时间能够提高命中率，但也可能让地址变更较慢生效；过于激进的清理则会增加查询次数和耗电。修改配置后已有缓存可能仍然存在，需要时可前往数据清理页面手动清除。")
    val RESOLUTION_MODE = SettingsGuide("resolution_mode", "解析模式", "解析模式决定一次 DNS 请求如何选择和调度已启用的服务商。不同模式会在响应速度、网络请求数量、耗电量以及故障容忍能力之间作出不同取舍。你可以先进入各模式查看具体参数，再根据设备用途和网络质量选择。服务商数量、连接稳定性和历史测速结果都会影响最终表现；修改模式不会删除服务商配置，如遇解析异常，可先切回较简单的模式进行排查。")
    val LOG_MODE = SettingsGuide("log_mode", "日志模式", "日志模式决定 DNSSR 会记录哪些 DNS 请求以及保留多少排查信息。较完整的日志有助于确认请求来自哪个应用、命中了什么规则、由哪个服务商响应，但也会占用更多存储空间，并在本机留下更多访问记录。若只关注运行状态，可以选择较精简的记录范围；需要定位规则或解析问题时再临时提高详细程度。日志只保存在设备本地，可随时通过数据清理页面删除。")
    val CONFIG_TRANSFER = SettingsGuide("config_transfer", "导入与导出", "导入与导出用于在设备之间迁移 DNSSR 的自定义配置，包括服务商、应用设置以及规则订阅等内容。导出前请留意文件中可能包含的服务地址或其他个人配置，并妥善保存。导入时系统会按所选方式覆盖或合并本机对应数据，因此建议先导出现有配置作为备份，并确认文件来源可信、版本兼容。操作完成后请检查关键设置，必要时重新启动服务使配置生效。")
    val DOMAIN_RULES = SettingsGuide("domain_rules", "域名规则", "域名规则用于决定特定域名应被拦截、放行、重写，或交由订阅规则处理，会直接影响相关网站和应用能否正常联网。你可以管理手动规则、白名单、重写规则、订阅以及镜像模板。规则越多并不一定越好，冲突或范围过宽的条目可能造成误拦截。新增规则后建议立即验证目标域名，并通过 DNS 日志查看实际命中情况，以便快速发现优先级或格式问题。")
    val DATA_CLEANUP = SettingsGuide("data_cleanup", "数据清理", "数据清理可以集中删除设备上的 DNS 缓存、请求日志、统计记录、规则数据或其他本地内容。不同清理项的影响范围并不相同，有些数据删除后无法恢复，也可能让历史统计和排查线索永久丢失。执行前请逐项核对选择内容，重要配置应先通过导出功能备份。重置新手引导只会让首次进入说明重新显示，不会删除配置、规则、缓存、日志或证书。")
    val SERVICE_DISPLAY = SettingsGuide("service_display", "服务显示", "服务显示用于控制首页解析服务列表中展示哪些协议和具体服务商，方便你隐藏暂时不关心的项目，让首页状态更加简洁。这里的选项只影响界面呈现，不会启用、停用、删除服务商，也不会改变 DNS 请求实际采用的解析策略。若要调整真正参与查询的服务，请前往服务商管理或解析模式页面。隐藏项目后仍可随时返回本页恢复显示，不会丢失原有配置。")
    val APPEARANCE = SettingsGuide("appearance", "外观设置", "外观设置集中管理应用的主题模式、主题颜色、首页组件透明度、首页语句、通知文字、自定义背景和服务灯光效果等显示选项。这里的调整主要影响界面与通知的呈现方式，不会改变 DNS 服务、规则或网络行为。部分自定义背景和视觉效果可能增加资源占用，在低性能设备上可适当减少效果。若修改后内容辨识度下降，可分别进入对应子页面恢复默认值。")
    val FOREGROUND_BACKGROUND = SettingsGuide("foreground_background", "前后台行为", "前后台行为决定 DNSSR 在应用离开前台后如何展示和维持运行，包括常驻通知、最近任务显示以及相关系统交互。部分选项可能影响系统对后台服务的管理方式，尤其在启用了严格省电策略的设备上，隐藏界面并不等于服务已经停止。调整前请确认自己希望的是隐藏应用、减少通知，还是保持解析服务稳定运行；修改后建议锁屏或切换应用测试实际效果。")
    val EXCLUDED_APPS = SettingsGuide("excluded_apps", "排除应用", "排除应用用于指定哪些应用不经过 DNSSR 的 DNS 解析，而是继续使用系统提供的 DNS。被排除后，该应用的域名请求通常不会命中 DNSSR 中配置的屏蔽、放行或重写规则，其日志和统计也可能不再完整显示。此功能适合处理与本地 VPN、特殊网络或特定解析方式不兼容的应用。添加后若发现过滤失效属于预期行为，移出排除列表即可恢复。")
    val BLOCKED_APPS = SettingsGuide("blocked_apps", "禁止联网应用", "禁止联网应用用于阻止选中的应用通过 DNSSR 所建立的本地 VPN 连接网络，适合临时限制不希望联网的应用。该功能依赖系统版本和 VPN 工作状态，Android 版本过低时可能不可用。选中应用后，它们的网络请求可能直接失败，后台同步、消息通知和账号验证也会受到影响。若某个应用出现异常，请先检查它是否在禁止列表中；取消选择后即可恢复正常转发。")
    val HTTP_INSPECTION = SettingsGuide("http_inspection", "HTTPS 过滤 (Beta)", "HTTPS 过滤属于实验性高级功能，不适合缺少相关经验的用户。它会对明确选择的应用检查 HTTP(S) 请求，并需要安装和信任 DNSSR 根证书；证书固定、自定义校验或不兼容的连接可能无法解密并自动旁路。安装、卸载或重新安装证书均需谨慎操作，否则部分应用可能无法联网。HTTP/3 默认直连，启用尝试检查后会阻断所选应用的 UDP 443，以促使客户端回退到 TCP。")
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
            text = {
                Column {
                    Text(guide.message)
                }
            },
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
