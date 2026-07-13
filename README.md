# DNSSR

DNSSR 是一款 Android 本地 DNS 安全解析工具。它通过 Android VpnService 接管设备上的 DNS 查询，并将请求转发到用户配置的加密 DNS 服务，适合希望管理 DNS 服务商、查看解析状态、使用本地缓存或按规则屏蔽域名的用户。

项目仓库：https://github.com/haoze-evolluling/DNSSR

## 功能特性

- 支持 DNS-over-HTTPS 和 DNS-over-TLS 服务商。
- 支持添加、编辑和选择自定义 DNS 服务商。
- 支持本地 DNS 缓存，减少重复查询。
- 支持 DNS 请求日志、缓存记录、竞速统计和 Bootstrap 解析统计。
- 支持域名屏蔽规则和订阅导入。
- 支持竞速模式，在多个服务商之间按策略选择解析结果。
- 支持 Bootstrap IP，用于解析 DoH/DoT 服务商域名。
- 支持快速设置磁贴，便于从系统快捷开关启动或停止服务。

## 适用场景

- 希望在 Android 设备上统一使用加密 DNS。
- 需要查看 DNS 查询日志、缓存命中或解析失败原因。
- 需要屏蔽指定域名或维护域名规则订阅。
- 想比较多个 DNS 服务商在当前网络环境下的解析表现。
- 需要避免 DoH/DoT 服务商域名解析依赖系统 DNS。

## 技术栈

- Kotlin
- Jetpack Compose
- Android VpnService
- Room
- OkHttp
- Navigation Compose
- Paging

## 构建方式

请先安装 Android Studio 和 Android SDK，并确保本机 JDK 配置可用。

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

构建成功后，调试 APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 使用说明

1. 安装并打开 DNSSR。
2. 在主界面选择 DNS 服务商，或进入设置页管理服务商。
3. 按需配置 DNS 缓存、竞速模式、Bootstrap IP、日志保留时间和域名屏蔽规则。
4. 启动 DNS 服务后，系统会弹出 VPN 授权确认。授权后应用开始处理 DNS 查询。
5. 在日志页查看 DNS 请求、缓存、竞速统计和 Bootstrap 解析统计。

DNSSR 只处理 DNS 查询，不作为通用网络代理。DNS 缓存、日志、规则和配置保存在设备本机；使用 DoH/DoT 时，所选 DNS 服务商仍会收到必要的域名解析请求。

## 作者

- GitHub：haoze-evolluling
- 身份：集美大学人工智能系大二学生

## 许可证

当前仓库未声明开源许可证。使用、分发或二次开发前，请先确认仓库后续补充的许可证信息或联系作者。
