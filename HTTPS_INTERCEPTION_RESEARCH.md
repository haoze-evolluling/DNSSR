# Android 无 root HTTP/HTTPS 流量拦截调研

调研日期：2026-07-17

## 结论摘要

1. **无 root 可以捕获和转发设备流量，但不能保证解密任意 App 的 HTTPS。** Android `VpnService` 可以创建 TUN 虚拟网卡，把选定应用的 IP 包交给本地进程；应用仍需实现 TCP/UDP 转发、连接状态、回包注入和上游 socket 防回环。Android 官方说明 `VpnService` 返回虚拟网络接口的文件描述符，且同一用户/配置文件同时只能有一个活动 VPN。[Android `VpnService`](https://developer.android.com/reference/android/net/VpnService)
2. **用户 CA 只对主动信任它的客户端有效。** Android 官方网络安全配置说明：应用默认信任预装系统 CA；仅 target API 23 及以下的应用还会默认信任用户添加的 CA。target API 24+ 的普通第三方应用默认不信任用户 CA，但应用作者可通过 Network Security Configuration 显式加入 `src="user"` 或仅在调试构建中加入 CA。[Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
3. **证书固定、私有信任库和应用层加密是独立障碍。** 即使客户端信任用户 CA，证书固定或自带信任库仍可拒绝中间人证书；成功解开 TLS 后，业务载荷也可能再次加密。PCAPdroid 的官方 TLS 解密文档记录了这些限制，并明确指出普通 App 通常会因 Android 安全机制而解密失败。[PCAPdroid TLS decryption](https://emanuele-f.github.io/PCAPdroid/tls_decryption)
4. **`Host` 不是通用的 HTTPS 域名入口。** 明文 HTTP/1.1 请求带 `Host`；HTTP/2、HTTP/3 使用 `:authority` 表示目标权威信息。HTTPS 中这些 HTTP 字段位于 TLS 内，解密前看不到。普通 TLS 可尝试从 ClientHello 的 SNI 获得主机名；显式 HTTP 代理可从 `CONNECT host:port` 获得目标；只有解密成功后才能读取 HTTP `Host`/`:authority`。[RFC 9110 Host and `:authority`](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2)、[RFC 9110 CONNECT](https://www.rfc-editor.org/rfc/rfc9110.html#section-9.3.6)、[RFC 6066 SNI](https://www.rfc-editor.org/rfc/rfc6066.html#section-3)
5. **建议先交付不依赖 CA 的域名拦截，再把 HTTPS MITM 做成明确标注兼容范围的可选能力。** DNS、普通 TLS SNI、明文 HTTP Host 和已知 IP 可以组成域名判定链；MITM 只针对用户选择、且实际信任本地 CA 的应用/浏览器启用。

## 类似开源项目

### PCAPdroid 与 PCAPdroid-mitm

[PCAPdroid](https://github.com/emanuele-f/PCAPdroid) 是最接近本需求的参考实现。其 README 明确说明：无需 root，利用本地 VPN 捕获流量；可提取 DNS、SNI、HTTP URL 和远端 IP；TLS 解密由本地代理完成。它组合了：

- [`zdtun`](https://github.com/emanuele-f/zdtun)：无 root TUN 数据面的最小 TCP/IP 栈，跟踪会话、TCP 序列号和窗口，并用真实 socket 承担重传；支持 TCP、UDP、ICMP、IPv4 和 IPv6。
- [`nDPI`](https://github.com/ntop/nDPI)：协议识别和连接元数据。
- [`mitmproxy`](https://github.com/mitmproxy/mitmproxy)：选中连接的 TLS 中间人代理。
- [`PCAPdroid-mitm`](https://github.com/emanuele-f/PCAPdroid-mitm)：把 mitmproxy/Python 打包进独立 Android addon，在本机执行解密。

可借鉴的核心设计是**先用连接元数据规则选择是否解密，再把选中的 TCP 流送入 MITM**，而不是让所有 TCP 都进入昂贵且容易破坏连接的 TLS 代理。其官方文档还记录：QUIC 和 STARTTLS 尚不支持，阻断 QUIC 只能促使部分客户端回退到 TLS；这意味着只实现 TCP MITM 不能覆盖 HTTP/3。[PCAPdroid README](https://github.com/emanuele-f/PCAPdroid#readme)、[PCAPdroid TLS decryption](https://emanuele-f.github.io/PCAPdroid/tls_decryption)

### HTTP Toolkit Android

[HTTP Toolkit Android](https://github.com/httptoolkit/httptoolkit-android) 的 README 对架构描述很清楚：外层 Kotlin 应用负责代理配置、CA 信任和 VPN 生命周期；VPN 接收设备发出的每个 IP 包，解析并重写部分流量到桌面 HTTP Toolkit，再把响应送回设备。它证明了无 root VPN + 流量重写到显式代理的路线可行，同时也明确指出 root 能覆盖更多真实 App 流量。

该项目更适合参考以下部分：VPN 与代理控制面的分层、目标流量选择、原目的地址保留，以及代理不可用时的失败处理。其桌面依赖模式不宜直接照搬到希望完全本地运行的 DNSSR。

### NetGuard

[NetGuard](https://github.com/M66B/NetGuard) 是成熟的无 root Android 本地 VPN 防火墙。官方 README 明确支持 IPv4/IPv6 TCP/UDP，并说明无 root 防火墙依赖 Android VPN 服务；Android 不允许 VPN 链式叠加。它适合作为 VPN 生命周期、按应用包含/排除、IPv6、网络切换和防回环处理的工程参考，但其目标是允许/阻断连接，不是 TLS 解密。

### zdtun 与 tun2socks 类方案

[`zdtun`](https://github.com/emanuele-f/zdtun) 已被 PCAPdroid 验证用于 Android 无 root 抓包，且提供完整的本地 gateway 示例。另一类方案是 [`hev-socks5-tunnel`](https://github.com/heiher/hev-socks5-tunnel)，把 TUN 中的流量转换到 SOCKS5 代理。二者都说明 TUN 文件描述符之后仍需要一个用户态 TCP/IP/转发层；`VpnService` 本身不会把 IP 包自动变成可供 HTTP 代理读取的 TCP 字节流。

mitmproxy 官方的[代理模式文档](https://docs.mitmproxy.org/stable/concepts/modes/)也区分了显式代理、透明代理、TUN、WireGuard 等模式。对 DNSSR 而言，Android `VpnService` 提供的是流量入口，仍需由数据面把原始连接透明地桥接到本地 MITM 或直连上游。

## Android 与 TLS 的能力边界

### 用户 CA 信任

| 客户端情况 | 用户 CA MITM 结果 |
| --- | --- |
| target API 23 及以下，使用平台默认信任配置 | 默认可信任用户 CA |
| target API 24+，未显式配置用户 CA | 默认拒绝 |
| 应用通过 Network Security Configuration 加入 `src="user"` | 可信任，但仍可能受证书固定/自定义校验影响 |
| 应用调试构建通过 `debug-overrides` 加入调试 CA | 调试环境可用；Android 官方文档说明该配置可绕过该配置内信任锚相关的 pinning |
| 自带 CA 库、实现自定义校验或证书固定 | 通常拒绝，单靠安装用户 CA 无法解决 |
| 浏览器或应用明确支持用户证书 | 可以尝试，仍取决于该客户端的具体策略 |

因此产品文案应使用“对信任用户 CA 的应用解密 HTTPS”，不能写成“无 root 解密所有 HTTPS”。对于不信任 CA 的流量，应默认直连或只做 SNI/IP 元数据阻断；若强行 MITM，客户端只会看到 TLS 证书错误。

Android 11 起，应用也不能再直接发起通用 CA 安装流程，用户需从系统设置手动安装。Android 官方的企业变更说明指出 `KeyChain.createInstallIntent()` 对 target Android 11 的调用者不再用于安装 CA，CA 安装需经设置完成。[Android 11 enterprise changes](https://developer.android.com/work/versions/android-11#certificate-management)

### 域名信号的含义

| 信号 | 出现位置 | 是否需要 TLS 解密 | 主要缺口 |
| --- | --- | --- | --- |
| DNS 查询名 | DNS/DoH/DoT 解析阶段 | 否，但客户端自带 DoH/DoT 时 DNSSR 未必可见 | 一个 IP 可对应多个域名；缓存、硬编码 IP、加密 DNS 会丢失映射 |
| HTTP/1.1 `Host` | 明文 HTTP 请求头 | 明文 HTTP 不需要；HTTPS 需要 | 需先完成 TCP 重组；字段可能跨多个包；代理请求形式与源站形式不同 |
| HTTP/2/3 `:authority` | HTTP 头块 | HTTPS 通常需要 | HPACK/QPACK 与多路复用要求协议级解析，不应按字节搜索 |
| TLS SNI | 普通 TLS ClientHello 扩展 | 否 | SNI 可缺失；ECH 会加密真实 ClientHello；域前置等场景中不等同于最终 HTTP authority |
| HTTP `CONNECT host:port` | 客户端到显式 HTTP 代理的请求 | CONNECT 本身通常明文 | 只有客户端确实配置并遵循显式代理时才存在；透明 TUN 流量不会自动产生 CONNECT |
| 目的 IP/端口 | IP/TCP/UDP 包头 | 否 | 共享 CDN/IP 下域名不唯一，只适合兜底或 IP 规则 |

普通 HTTP 的域名拦截必须在**完成 TCP 流重组后**解析 HTTP 消息。RFC 9110 要求 HTTP/1.1 客户端发送 `Host`，并要求服务端拒绝缺失或重复 `Host` 的请求；实现时应使用 HTTP 解析器并拒绝歧义输入，不能在单个 IP 包中搜索字符串。[RFC 9110 section 7.2](https://www.rfc-editor.org/rfc/rfc9110.html#section-7.2)

TLS SNI 由 RFC 6066 定义在扩展后的 ClientHello 中。它是解密前域名策略的重要输入，但不等于 HTTP Host。启用 Encrypted ClientHello 的客户端会隐藏真实 ClientHello；ECH 已由 [RFC 9849](https://www.rfc-editor.org/rfc/rfc9849.html) 标准化，因此长期方案必须允许“SNI 不可用”，回退到 DNS 关联、IP 规则或仅在解密成功后判断。

### HTTP 明文流量

Android 官方网络安全配置允许应用按域名控制 cleartext，但 target API 28+ 的默认基线禁止明文流量；这意味着现实中的 HTTP 明文量可能有限，不过遗留 App、局域网设备和显式 `http://` 仍可能产生。[Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)

明文拦截仍需覆盖：IPv4/IPv6、TCP 分片与乱序、keep-alive 上的多个请求、绝对形式 URI（显式代理）、升级到 WebSocket、非标准端口，以及无法识别时的直连策略。若只需按域名阻断，可在解析出 Host 后复位/关闭流；若要查看或修改内容，则应把该连接交给成熟 HTTP 代理库。

## 适合 DNSSR 的建议架构

当前 DNSSR README 明确说明 VPN 只路由 DNS，不转发通用应用流量。要增加本需求，建议保持 DNS 控制面与新数据面边界清晰：

```text
Android apps
    |
VpnService TUN (IPv4 + IPv6, DNS + TCP + UDP)
    |
connection tracker / user-space TCP-IP forwarder
    |
    +-- DNS ----------> existing DNSSR resolver/rules/cache
    +-- TCP metadata -> HTTP Host parser / TLS ClientHello SNI parser
    |                     |
    |                     +-- block / allow / select MITM
    +-- selected TLS --> local MITM proxy --> protected upstream socket
    +-- other TCP -----> direct protected upstream socket
    +-- UDP -----------> direct forwarder (optionally block UDP/443)
```

关键约束：

- VPN 路由必须从当前 DNS 地址/路由扩展到目标流量范围；否则看不到 HTTP/HTTPS 数据包。
- 所有上游 socket 必须调用 `VpnService.protect()` 或等效排除机制，避免重新进入 TUN 形成死循环。[Android `VpnService.protect`](https://developer.android.com/reference/android/net/VpnService#protect(java.net.Socket))
- 不建议自行实现完整 TCP 栈。优先评估 zdtun 或成熟 tun2socks/用户态网络栈，并核对许可证、ABI、IPv6、维护活跃度和 Android NDK 集成成本。
- HTTP、TLS ClientHello、HTTP/2、QUIC 都应使用结构化解析器；包级字符串扫描会在分片、重传、乱序和头压缩时失效。
- MITM CA 私钥应仅保存在应用私有存储或 Android Keystore 可保护的范围内，按安装生成，支持撤销/重置，禁止随 APK 分发固定私钥。
- 解密日志和正文属于高敏感数据，应默认关闭持久化、提供明确授权与删除入口，并避免把 Authorization、Cookie、表单和正文写入普通日志。
- 需要明确与其他 VPN 互斥；Android 平台同一用户不能同时运行两个 `VpnService` VPN。

## 推荐实施顺序

### 第一阶段：全流量数据面与无解密域名阻断

将 TUN 扩展到 TCP/UDP，集成成熟用户态转发层，先保证 IPv4/IPv6、网络切换、按应用包含/排除、socket 防回环和失败直连/失败关闭策略。建立连接表，把现有 DNS 请求与后续目的 IP 关联；增加普通 TLS ClientHello SNI 和明文 HTTP/1.1 Host 解析。此阶段不需要用户 CA，已经能覆盖大部分“按域名阻断”诉求。

### 第二阶段：HTTP 明文检查

在 TCP 重组之后用成熟 HTTP/1.x 解析器输出 method、scheme、authority/Host、path 和必要头字段。先实现查看与阻断，不急于支持正文修改。HTTP/2 cleartext (`h2c`) 和 WebSocket 可先识别并透传，避免范围失控。

### 第三阶段：选择性 HTTPS MITM

按应用、域名和端口建立解密规则；动态生成本地 CA 和叶子证书；只把规则命中的 TCP TLS 流交给本地 MITM。提供 CA 导出与 Android 设置安装引导，并在 UI 中明确显示“客户端不信任 CA”“证书固定/自定义信任”“非 TLS/不支持协议”等状态。失败时的直连还是阻断应由用户可配置，默认直连更不易破坏应用。

优先验证目标应是明确支持用户 CA 的浏览器、用户自有 App，或其 Network Security Configuration 可修改的调试构建。第三方 target 24+ App 不应列为可保证支持的对象。

### 第四阶段：协议覆盖与性能

根据真实流量再决定是否解析 HTTP/2、WebSocket 和 STARTTLS。HTTP/3/QUIC 是独立的大工程；可先提供 UDP/443 阻断以尝试促使客户端回退，但必须标注回退不保证成功。增加连接/字节/内存上限、背压、空闲超时和按连接采样，避免全流量接管后显著增加耗电与内存。

## 需要提前决定的产品语义

1. “拦截”是指阻断、记录、查看正文，还是修改请求/响应。四者的数据面、隐私风险和 UI 都不同。
2. 域名规则命中顺序：解密后的 authority/Host、SNI、DNS 关联和 IP 规则冲突时以谁为准。
3. 域名未知或解析失败时默认直连还是阻断。
4. MITM 失败时直连还是阻断；若先失败再直连，客户端会经历额外延迟。
5. 是否允许按应用选择。全设备解密既昂贵，也更容易误收集敏感数据。
6. 是否必须保留现有“只处理 DNS”的轻量模式。建议保留，因为全流量 TUN 的性能、兼容性和隐私成本显著更高。

## 一手资料索引

- Android Developers: [`VpnService`](https://developer.android.com/reference/android/net/VpnService)
- Android Developers: [Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
- Android Developers: [Android 11 enterprise certificate-management changes](https://developer.android.com/work/versions/android-11#certificate-management)
- PCAPdroid: [repository and README](https://github.com/emanuele-f/PCAPdroid)
- PCAPdroid: [TLS decryption user guide](https://emanuele-f.github.io/PCAPdroid/tls_decryption)
- PCAPdroid-mitm: [repository and README](https://github.com/emanuele-f/PCAPdroid-mitm)
- zdtun: [repository and README](https://github.com/emanuele-f/zdtun)
- HTTP Toolkit Android: [repository and architecture README](https://github.com/httptoolkit/httptoolkit-android)
- NetGuard: [repository and README](https://github.com/M66B/NetGuard)
- hev-socks5-tunnel: [repository and README](https://github.com/heiher/hev-socks5-tunnel)
- mitmproxy: [Proxy Modes](https://docs.mitmproxy.org/stable/concepts/modes/)
- IETF: [RFC 9110 HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- IETF: [RFC 6066 TLS Server Name Indication](https://www.rfc-editor.org/rfc/rfc6066.html#section-3)
- IETF: [RFC 9849 TLS Encrypted Client Hello](https://www.rfc-editor.org/rfc/rfc9849.html)
