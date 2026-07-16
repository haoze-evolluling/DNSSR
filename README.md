# DNSSR

DNSSR 是一款 Android 本地 DNS 解析管理工具。应用通过 Android `VpnService` 建立只处理 DNS 的本地通道，将系统 DNS 请求按配置转发到上游 DNS 服务；它不是通用网络代理，不会转发网页、应用数据或文件传输流量。

项目仓库：https://github.com/haoze-evolluling/DNSSR

## 功能

- 支持普通 DNS、DNS-over-HTTPS（DoH）和 DNS-over-TLS（DoT）。
- 内置多个服务商，可添加、编辑、删除自定义服务商，并在首页控制服务商显示。
- 提供四种解析模式：单服务商、择优而行（按健康权重选择）、百舸争流（并发查询并采用最快成功响应）和有备无患（按优先级切换主备服务）。
- 支持 DNS 缓存，可选择跟随上游 TTL、限制最长 TTL 或固定 TTL，并可在上游失败时使用短暂过期缓存。
- 支持域名屏蔽规则、白名单规则和 AdGuard DNS 规则订阅；订阅可手动刷新或定期自动更新。
- 支持 NXDOMAIN、返回 `0.0.0.0` 或 `::` 等屏蔽响应方式。
- 支持 Bootstrap IP，减少解析 DoH 与 DoT 服务商域名时对系统 DNS 的依赖。
- 提供 DNS 请求日志、规则拦截率、缓存记录、竞速统计、服务商健康状态与 Bootstrap 统计。
- 支持导入、导出自定义服务商和规则订阅配置，以及 Android 快捷设置磁贴开关。

## 工作方式与边界

启动服务后，Android 会要求授予 VPN 权限。DNSSR 创建虚拟网卡并仅拦截 DNS 查询，将其交给已配置的上游服务处理。

DNS 缓存、日志、规则和服务商配置均保存在设备本机。上游服务商仍会收到为完成解析所必需的域名请求。DoH 和 DoT 会加密设备到上游之间的 DNS 传输；普通 DNS 不具备此保护。

## 使用

1. 安装并打开 DNSSR，在首页选择 DNS 服务商。
2. 根据需要在“设置”中管理服务商、选择解析模式、配置缓存和 Bootstrap IP。
3. 需要过滤域名时，在“规则管理”中添加屏蔽或白名单规则，或导入订阅地址。
4. 点击首页电源按钮并在系统弹窗中授权 VPN。
5. 在“日志”中查看 DNS 请求、缓存、竞速、规则拦截与 Bootstrap 统计。

## 构建

环境要求：Android Studio、Android SDK 和 JDK 11。项目最低支持 Android 7.0（API 24），当前版本为 `2.32`。

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

构建产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 技术栈

- Kotlin 与 Jetpack Compose
- Android `VpnService`
- Room
- OkHttp
- Navigation Compose、Paging 与 WorkManager

## 作者与许可证

- 作者：haoze-evolluling（集美大学人工智能系大三学生）
- 当前仓库未声明开源许可证；使用、分发或二次开发前，请先确认后续许可证信息或联系作者。
