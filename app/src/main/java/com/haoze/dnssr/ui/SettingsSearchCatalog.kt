package com.haoze.dnssr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector

internal data class SettingsSearchEntry(
    val title: String,
    val description: String,
    val breadcrumb: List<String>,
    val route: String,
    val icon: ImageVector,
    val keywords: List<String> = emptyList()
) {
    val resultSubtitle: String
        get() = (listOf("应用设置") + breadcrumb).joinToString(" · ") +
            description.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()

    fun matches(query: String): Boolean =
        (listOf(title, description) + breadcrumb + keywords).any { it.contains(query, ignoreCase = true) }
}

internal object SettingsSearchCatalog {
    private fun page(title: String, description: String, section: String, route: String, icon: ImageVector, vararg keywords: String) =
        SettingsSearchEntry(title, description, listOf(section, title), route, icon, keywords.toList())

    private fun option(title: String, description: String, section: String, page: String, route: String, icon: ImageVector, vararg keywords: String) =
        SettingsSearchEntry(title, description, listOf(section, page), route, icon, keywords.toList())

    private fun nestedPage(title: String, description: String, section: String, parent: String, route: String, icon: ImageVector, vararg keywords: String) =
        SettingsSearchEntry(title, description, listOf(section, parent, title), route, icon, keywords.toList())

    private fun nestedOption(title: String, description: String, section: String, parent: String, page: String, route: String, icon: ImageVector, vararg keywords: String) =
        SettingsSearchEntry(title, description, listOf(section, parent, page), route, icon, keywords.toList())

    val entries: List<SettingsSearchEntry> = listOf(
        page("服务商管理", "选择、添加或编辑 DoH/DoT 服务", "解析设置", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns, "DNS", "服务地址", "协议"),
        option("新增 DNS 服务商", "添加自定义解析服务", "解析设置", "服务商管理", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns, "添加", "DoH", "DoT"),
        option("服务商名称", "设置自定义 DNS 服务的名称", "解析设置", "服务商管理", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns),
        option("解析地址", "设置 DoH 请求地址", "解析设置", "服务商管理", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns, "URL"),
        option("服务器地址", "设置 DoT 服务器地址", "解析设置", "服务商管理", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns, "主机名"),
        option("端口", "设置 DoT 服务端口", "解析设置", "服务商管理", Routes.PROVIDER_MANAGEMENT, Icons.Filled.Dns),
        page("Bootstrap 设置", "配置全局 Bootstrap DNS 与智慧权重", "解析设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public, "IP", "递归 DNS"),
        option("启用 Bootstrap IP", "使用独立递归 DNS 解析服务商域名", "解析设置", "Bootstrap 设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public, "全局开关"),
        option("内置 Bootstrap IP", "管理内置解析 IP", "解析设置", "Bootstrap 设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public),
        option("自定义 Bootstrap IP", "添加和管理自定义解析 IP", "解析设置", "Bootstrap 设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public, "添加 IP"),
        option("名称（可选）", "设置自定义 Bootstrap IP 名称", "解析设置", "Bootstrap 设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public),
        option("IP 地址", "填写自定义 Bootstrap IP", "解析设置", "Bootstrap 设置", Routes.BOOTSTRAP_SETTINGS, Icons.Filled.Public),
        page("查询测速", "测试指定域名的实际解析耗时", "解析设置", Routes.RACE_MODE_LATENCY, Icons.Filled.Speed, "延迟", "测速域名", "测速服务商", "查询耗时结果"),

        page("缓存设置", "缓存已解析的域名，减少重复查询", "性能优化", Routes.CACHE_SETTINGS, Icons.Filled.Storage, "DNS 缓存"),
        option("本地 DNS 缓存", "启用或关闭本地响应缓存", "性能优化", "缓存设置", Routes.CACHE_SETTINGS, Icons.Filled.Storage, "缓存策略", "预设"),
        option("保守", "跟随上游 TTL", "性能优化", "缓存设置", Routes.CACHE_SETTINGS, Icons.Filled.Storage),
        option("均衡", "最长 1 小时，短 TTL 至少 1 分钟", "性能优化", "缓存设置", Routes.CACHE_SETTINGS, Icons.Filled.Storage),
        option("高命中", "最长 6 小时，短 TTL 至少 2 分钟", "性能优化", "缓存设置", Routes.CACHE_SETTINGS, Icons.Filled.Storage),
        page("解析模式", "选择省电、均衡、极速或主备解析策略", "性能优化", Routes.RACE_MODE_PROVIDERS, Icons.AutoMirrored.Filled.AltRoute, "DNS 策略"),
        option("预设 DNS 服务", "切换阿里云和腾讯 DNSPod 的预设服务协议", "性能优化", "解析模式", Routes.RACE_MODE_PROVIDERS, Icons.AutoMirrored.Filled.AltRoute, "阿里云", "DNSPod"),
        nestedPage("省电", "配置单一 DNS 服务商", "性能优化", "解析模式", Routes.RESOLUTION_SINGLE, Icons.AutoMirrored.Filled.AltRoute, "单一服务"),
        nestedPage("均衡", "配置参与智能预测的服务商", "性能优化", "解析模式", Routes.RESOLUTION_SMART, Icons.AutoMirrored.Filled.AltRoute, "智能预测"),
        nestedPage("极速", "配置参与并行查询的服务商", "性能优化", "解析模式", Routes.RESOLUTION_PARALLEL, Icons.AutoMirrored.Filled.AltRoute, "并行竞速"),
        nestedPage("主备（高级）", "配置服务商主备顺序", "性能优化", "解析模式", Routes.RESOLUTION_BACKUP, Icons.AutoMirrored.Filled.AltRoute, "主备模式", "故障转移", "主备顺序"),
        page("日志模式", "选择 DNS 请求日志的记录范围", "性能优化", Routes.LOG_RETENTION_SETTINGS, Icons.Filled.History, "保留时间", "自动清理"),
        option("DNS 请求日志", "设置日志记录范围", "性能优化", "日志模式", Routes.LOG_RETENTION_SETTINGS, Icons.Filled.History),
        option("自动清理时间", "设置日志保留天数", "性能优化", "日志模式", Routes.LOG_RETENTION_SETTINGS, Icons.Filled.History, "保留天数"),

        page("前后台行为", "后台隐藏、通知常驻和电池设置", "运行行为", Routes.FOREGROUND_BACKGROUND_SETTINGS, Icons.Filled.FlipToBack),
        option("后台隐藏", "隐藏最近任务卡片并禁用任务截图", "运行行为", "前后台行为", Routes.FOREGROUND_BACKGROUND_SETTINGS, Icons.Filled.FlipToBack, "最近任务"),
        option("通知常驻", "VPN 未运行时在通知栏常驻提醒", "运行行为", "前后台行为", Routes.FOREGROUND_BACKGROUND_SETTINGS, Icons.Filled.FlipToBack),
        option("忽略电池优化", "前往系统电池优化设置", "运行行为", "前后台行为", Routes.FOREGROUND_BACKGROUND_SETTINGS, Icons.Filled.FlipToBack, "后台运行"),
        page("排除应用", "指定使用系统 DNS 的应用", "运行行为", Routes.EXCLUDED_APPS, Icons.Filled.Apps, "应用列表", "系统 DNS"),
        page("HTTP(S) 流量过滤", "按应用检查请求并应用域名规则", "运行行为", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http, "HTTPS", "HTTP3", "QUIC"),
        option("启用所选应用的 HTTP(S) 检查", "打开所选应用的流量过滤", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http),
        option("选择过滤应用", "选择需要检查流量的应用", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_APPS, Icons.Filled.Http),
        option("尝试检查 HTTP/3", "阻断 QUIC 并尝试回退到 TCP", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http, "QUIC"),
        option("安装 HTTPS 根证书", "导出并安装流量检查证书", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http, "CA"),
        option("查看 HTTPS 根证书", "查看根证书 SHA-256 指纹", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http, "证书指纹"),
        option("重置 HTTPS 根证书", "废止并重新生成根证书", "运行行为", "HTTP(S) 流量过滤", Routes.HTTP_INSPECTION_SETTINGS, Icons.Filled.Http),

        page("服务显示", "配置首页显示的协议和服务商", "外观", Routes.HOME_PROVIDER_VISIBILITY, Icons.Filled.Visibility, "DoH", "DoT", "全部服务"),
        page("外观设置", "设置应用的显示外观", "外观", Routes.APPEARANCE_SETTINGS, Icons.Filled.Palette),
        nestedPage("日夜模式", "选择浅色、深色或跟随系统", "外观", "外观设置", Routes.DAY_NIGHT_MODE, Icons.Filled.Palette, "主题", "深色模式"),
        nestedPage("主题色配置", "选择应用界面的强调色", "外观", "外观设置", Routes.THEME_COLOR_SETTINGS, Icons.Filled.Palette, "配色", "强调色"),
        nestedPage("首页透明度", "调整首页按钮、选择框与文字透明度", "外观", "外观设置", Routes.HOME_COMPONENT_OPACITY, Icons.Filled.Palette, "交互组件", "文字"),
        nestedPage("首页句子", "设置 DNS 服务开启和关闭时的句子", "外观", "外观设置", Routes.HOME_SENTENCE_SETTINGS, Icons.Filled.Palette, "开启句子", "关闭句子"),
        nestedPage("通知栏文案", "设置 DNS 服务开启和关闭时的通知文案", "外观", "外观设置", Routes.NOTIFICATION_TEXT_SETTINGS, Icons.Filled.Palette, "通知文字"),
        nestedPage("软件背景", "选取手机图片作为应用背景", "外观", "外观设置", Routes.CUSTOM_BACKGROUND_SETTINGS, Icons.Filled.Palette, "壁纸", "背景图片"),
        nestedOption("启用软件背景", "显示已添加的背景图片", "外观", "外观设置", "软件背景", Routes.CUSTOM_BACKGROUND_SETTINGS, Icons.Filled.Palette),
        nestedOption("添加图片", "从手机选取背景图片", "外观", "外观设置", "软件背景", Routes.CUSTOM_BACKGROUND_SETTINGS, Icons.Filled.Palette, "壁纸"),
        nestedPage("服务动态光影", "设置服务启动和关闭时的动态效果", "外观", "外观设置", Routes.SERVICE_LIGHT_EFFECT_SETTINGS, Icons.Filled.Palette, "动效"),
        nestedOption("启用服务动态光影", "显示服务状态动态光影", "外观", "外观设置", "服务动态光影", Routes.SERVICE_LIGHT_EFFECT_SETTINGS, Icons.Filled.Palette),
        nestedPage("旧版图标", "切换桌面入口图标样式", "外观", "外观设置", Routes.LEGACY_ICON_SETTINGS, Icons.Filled.Palette, "桌面图标"),
        nestedOption("使用旧版图标", "将桌面入口图标切换为旧版样式", "外观", "外观设置", "旧版图标", Routes.LEGACY_ICON_SETTINGS, Icons.Filled.Palette),
        nestedPage("旧版日志页面", "选择首页日志按钮打开的页面", "外观", "外观设置", Routes.LEGACY_LOG_PAGE_SETTINGS, Icons.Filled.Palette, "日志仪表盘"),
        nestedOption("使用旧版日志页面", "让首页日志按钮打开日志分组页", "外观", "外观设置", "旧版日志页面", Routes.LEGACY_LOG_PAGE_SETTINGS, Icons.Filled.Palette),

        page("导入与导出", "备份或恢复自定义服务与规则订阅", "数据管理", Routes.CONFIG_TRANSFER, Icons.Filled.ImportExport, "备份", "恢复"),
        nestedPage("设置配置", "选择配置内容并导入或导出", "数据管理", "导入与导出", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport, "JSON"),
        option("自定义 DNS 服务商", "导入或导出名称、协议和解析地址", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        option("自定义 Bootstrap IP", "导入或导出名称、IP 和启用状态", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        option("网络规则订阅", "导入或导出订阅名称和链接", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        option("排除应用", "导入或导出使用系统 DNS 的应用包名", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        option("导出配置", "将勾选内容保存为 JSON 配置文件", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        option("导入配置", "合并配置并跳过本机已有项目", "数据管理", "设置配置", Routes.CONFIG_IMPORT_EXPORT, Icons.Filled.ImportExport),
        nestedPage("规则导出", "将当前生效规则导出为 TXT 文件", "数据管理", "导入与导出", Routes.RULE_EXPORT, Icons.Filled.ImportExport, "订阅文件"),
        page("域名规则", "添加屏蔽、放行、覆写规则和订阅", "数据管理", Routes.RULE_MANAGEMENT, Icons.AutoMirrored.Filled.Rule, "黑名单", "白名单", "hosts"),
        nestedPage("拦截响应", "配置固定响应和动态策略", "数据管理", "域名规则", Routes.BLOCK_RESPONSE_SETTINGS, Icons.AutoMirrored.Filled.Rule, "NXDOMAIN", "空响应"),
        option("启用动态策略", "按域名请求次数动态切换响应", "数据管理", "拦截响应", Routes.BLOCK_RESPONSE_SETTINGS, Icons.AutoMirrored.Filled.Rule),
        option("动态参数", "设置请求阈值、窗口和保持时间", "数据管理", "拦截响应", Routes.BLOCK_RESPONSE_SETTINGS, Icons.AutoMirrored.Filled.Rule, "阈值"),
        option("添加屏蔽域名", "输入要屏蔽的域名", "数据管理", "域名规则", Routes.RULE_MANAGEMENT, Icons.AutoMirrored.Filled.Rule),
        option("添加放行域名", "输入要放行的域名", "数据管理", "域名规则", Routes.RULE_MANAGEMENT, Icons.AutoMirrored.Filled.Rule),
        option("添加覆写域名", "将域名覆写为 IPv4、IPv6 或 CNAME", "数据管理", "域名规则", Routes.RULE_MANAGEMENT, Icons.AutoMirrored.Filled.Rule),
        nestedPage("规则订阅", "管理 DNS 过滤与 hosts 覆写订阅", "数据管理", "域名规则", Routes.SUBSCRIPTION_MANAGEMENT, Icons.AutoMirrored.Filled.Rule),
        nestedPage("自动更新设置", "设置规则订阅的自动更新开关和频率", "数据管理", "域名规则", Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL, Icons.AutoMirrored.Filled.Rule, "更新时间", "更新频率"),
        option("自动更新规则订阅", "在后台定期更新所有网络订阅", "数据管理", "自动更新设置", Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL, Icons.AutoMirrored.Filled.Rule),
        option("自定义更新时间", "输入 1 至 168 小时的更新间隔", "数据管理", "自动更新设置", Routes.SUBSCRIPTION_AUTO_UPDATE_INTERVAL, Icons.AutoMirrored.Filled.Rule),
        option("黑名单规则", "查看、启用、停用或删除屏蔽规则", "数据管理", "域名规则", Routes.RULE_LIST, Icons.AutoMirrored.Filled.Rule),
        option("白名单规则", "查看、启用、停用或删除放行规则", "数据管理", "域名规则", Routes.ALLOW_RULE_LIST, Icons.AutoMirrored.Filled.Rule),
        option("覆写域名规则", "查看、启用、停用或删除覆写规则", "数据管理", "域名规则", Routes.REWRITE_RULE_LIST, Icons.AutoMirrored.Filled.Rule),
        option("清理全部规则", "删除全部规则后重新导入", "数据管理", "域名规则", Routes.RULE_MANAGEMENT, Icons.AutoMirrored.Filled.Rule),
        page("数据清理", "删除缓存、日志或域名规则", "数据管理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep, "清空数据"),
        option("删除请求日志", "清除 DNS 和 HTTP 的历史请求记录", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),
        option("删除 DNS 缓存", "移除已缓存的解析结果", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),
        option("恢复 DNS 默认权重", "清除竞速模式的健康样本", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),
        option("恢复 Bootstrap 权重", "清除 Bootstrap DNS 解析健康样本", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),
        option("删除全部规则", "清除手动添加和订阅导入的所有域名规则", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),
        option("重置所有新手引导", "让所有首次进入说明再次显示", "数据管理", "数据清理", Routes.DATA_CLEANUP, Icons.Filled.DeleteSweep),

        page("应用信息", "查看软件说明、作者信息和项目仓库", "关于应用", Routes.ABOUT, Icons.Filled.Info),
        page("赞助", "支持项目持续开发", "关于应用", Routes.SPONSOR, Icons.Filled.Info),
        page("赞助者名单", "查看项目赞助者", "关于应用", Routes.SPONSOR_LIST, Icons.Filled.Info),
        page("共建者名单", "查看提出建议与帮助测试的共建者", "关于应用", Routes.CO_BUILDER_LIST, Icons.Filled.Info)
    )
}
