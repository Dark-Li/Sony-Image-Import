# SonyEdge Phase 1 协议现状与差异报告

## 1. 目的与边界

本文档记录 SonyEdge 在进入协议兼容性增强前的实现现状，并与 Imaging Edge Mobile 7.8.5 的可观察协议行为进行对照。

- 本阶段只进行源码审计、APK 静态协议证据分析和 ADB 基线验证。
- 未复制或修改 Imaging Edge Mobile 的代码，也未绕过认证、加密或访问控制。
- APK 分析只用于确认公开网络协议的请求形态、字段和调用顺序。
- 本报告完成前不修改 SonyEdge 的协议、下载或网络路由实现。

## 2. 分析输入

### SonyEdge

- 分支：`feature/protocol-compatibility`
- 基线提交：`e14cd75 Implement responsive SonyEdge Compose redesign`
- 当前版本：`versionCode 16`，`versionName 0.2.0`
- 当前启动入口：Compose `ComposeMainActivity`

### Imaging Edge Mobile

- 文件：`imaging_edge_mobile_ver7.8.5.apk`
- 包名：`com.sony.playmemories.mobile`
- 版本：`7.8.5`，`versionCode 785260216`
- SHA-256：`7E9C23DBF420E0BA8827ECAF3E2B8A72BA6E3865388FEE1F5980A906D7EECEDE`
- 工具：JADX 1.5.3、Android SDK `aapt`
- 临时分析输出：`build/protocol-analysis/imaging-edge-7.8.5/`

### 实机基线

- ADB 设备：OPPO CPH2025，序列号 `909e29e1`
- 相机：Sony ILCE-7RM3，通过相机 Wi-Fi AP 连接
- SonyEdge 基线结果：连接成功，并浏览到 `Camera / PhotoRoot / Date`
- 实机返回：6 个日期目录；可见目录包括 2026-07-08、07-10、07-12、07-15、07-18
- 证据：`build/protocol-analysis/baseline/connected.png` 和 `ui-connected.xml`

## 3. 当前架构与协议路径

| 模块 | 当前职责 | 主要限制 |
| --- | --- | --- |
| `DiscoveryClient` | SSDP 探测 Scalar；固定端口/路径探测 DMS | DMS 未使用标准 SSDP `LOCATION`；未保留完整设备描述 |
| `DmsServiceInfo` | 保存描述 URL、服务类型、控制 URL | 缺少 UDN、型号、friendly name、SCPD/event URL 和多服务表 |
| `DmsContentClient` | ContentDirectory `BrowseDirectChildren` 和 DIDL 解析 | 没有 `GetSortCapabilities`；资源选择主要依赖 URL 命名启发式 |
| `SonyRpcClient` | Scalar JSON-RPC 内容浏览 | 当前 Compose 主路径没有自动回退到 Scalar；单页最多 100 项 |
| `DownloadService` | 串行下载并写入 MediaStore | 没有自动重试、完整性强校验、事务化临时文件和可靠网络绑定 |
| `DownloadValidator` | 根据文件头推断 JPEG/ARW | 只分类不拒绝损坏文件；JPEG 未检查 EOI |
| `SonyEdgeViewModel` | 连接、浏览、选择和下载状态 | 全局绑定 Wi-Fi 后没有明确解绑；异常信息不完整 |

当前 Compose 主流程为：

1. 绑定首个 Wi-Fi 网络。
2. 从网关和硬编码地址组成候选主机。
3. 在固定 `64321` 端口和若干固定 XML 路径上寻找 ContentDirectory。
4. 通过 SOAP `BrowseDirectChildren` 分页浏览目录。
5. 从 DIDL `<res>` 中用文件名、URL、分辨率和大小启发式选择预览及原图。
6. 直接下载到缓存文件，再写入 Android MediaStore。

## 4. 能力矩阵

| 能力 | SonyEdge 基线 | Imaging Edge 7.8.5 证据 | 差异结论 |
| --- | --- | --- | --- |
| SSDP M-SEARCH | 仅 ScalarWebAPI；一次发送 | 设备发现库支持 `ssdp:all`、MediaServer、Scalar 等目标 | 需要多目标、重发、完整响应解析 |
| SSDP 响应字段 | 主要取 `LOCATION` | 解析 `LOCATION`、`USN`、`ST/NT`、`SERVER`、`CACHE-CONTROL` | SonyEdge 数据模型不足 |
| 设备描述 | 只取 Scalar action list 和 ContentDirectory control URL | 区分 ContentDirectory、XPushList、Scalar 等功能并保留设备身份 | 需要统一 `SonyDeviceDescription` |
| ContentDirectory | 支持分页 `BrowseDirectChildren` | 先调用 `GetSortCapabilities`，再按能力浏览 root/push root | 缺排序能力协商和 PushRoot |
| DIDL 元数据 | 解析少量 container/item/res 字段 | 解析日期、类型、多个资源 profile、HEIF/RAW/XAVC | 需要完整模型 |
| 原始资源选择 | URL/文件名启发式 | 明确按 `PN_ORIGINAL`、`JPEG_LRG`、`JPEG_SM`、`JPEG_TN`、HEIF profile 取 URL | 应以 protocolInfo profile 为主 |
| 手机主动浏览 | A7R III 实机可用 | ContentDirectory pull 流程 | 已具备基础能力，需增强兼容性 |
| 相机端选片推送 | 未实现 | 实现 XPushList 四阶段 SOAP 流程 | 关键缺失 |
| Scalar 控制/内容 | 有客户端，但 Compose 主路径未使用 | 根据设备能力使用 Camera/avContent 等服务 | 需要接入统一发现与能力路由 |
| 下载重试 | 仅用户手动重试失败列表 | 原版存在 SOAP/传输重试执行器 | SonyEdge 需按瞬时错误自动退避重试 |
| 下载完整性 | 只做弱文件头分类 | APK 静态证据不足以证明完整校验策略 | SonyEdge 必须独立实现严格校验 |
| 网络绑定 | 进程绑定首个 Wi-Fi，不解绑 | 原版具有完整 Wi-Fi 连接状态管理 | SonyEdge 需要任务作用域绑定/解绑 |

## 5. 关键协议差异

### 5.1 SSDP 与设备发现

当前 `DiscoveryClient` 的 SSDP 搜索目标固定为：

`urn:schemas-sony-com:service:ScalarWebAPI:1`

而 DMS 发现不是 SSDP 驱动，而是对候选 IP 的 `64321` 端口和固定路径进行 HTTP 探测。这能覆盖当前 A7R III，但会遗漏不同地址、端口、描述路径或只广播 MediaServer/ContentDirectory 的旧机型。

Imaging Edge APK 中的设备发现实现证明以下字段会参与设备身份和生命周期判断：

- `LOCATION`
- `USN` 及其中的 UUID
- `ST` 或 `NT`
- `SERVER`
- `CACHE-CONTROL/max-age`

目标实现应发送并汇总以下搜索目标：

- `ssdp:all`
- `upnp:rootdevice`
- `urn:schemas-upnp-org:device:MediaServer:1`
- `urn:schemas-upnp-org:service:ContentDirectory:1`
- `urn:schemas-sony-com:service:XPushList:1`
- `urn:schemas-sony-com:service:ScalarWebAPI:1`

不能再把 `192.168.122.1`、`64321` 或任一描述路径当成协议前提；固定候选只能保留为有明确日志的兼容性末级回退。

### 5.2 设备描述

当前模型只容纳单个 DMS 服务，无法描述一台同时提供 ContentDirectory、XPushList、ScalarWebAPI 和其他 UPnP 服务的相机。

目标模型 `SonyDeviceDescription` 至少应保留：

- `location`、`urlBase`
- `udn`、`friendlyName`、`manufacturer`、`modelName`、`modelNumber`、`serialNumber`
- 每个服务的 `serviceType`、`serviceId`、`controlURL`、`SCPDURL`、`eventSubURL`
- Scalar action list URL 及其服务类型关联

所有相对 URL 必须相对于描述文档 URL 或 `URLBase` 解析，不能手工拼接主机和固定路径。

### 5.3 ContentDirectory 与排序

当前实现直接发送 `BrowseDirectChildren`，`SortCriteria` 为空。Imaging Edge 7.8.5 会先执行 `GetSortCapabilities`，再把解析结果交给 root 或 PushRoot 浏览流程。

目标行为：

1. 连接后按服务实例缓存 `GetSortCapabilities`。
2. 支持日期时优先 `-dc:date`。
3. 不支持日期但支持标题时使用 `-dc:title`。
4. 都不支持时使用空排序条件。
5. 对 root、日期容器、普通容器和 PushRoot 使用同一分页 Browse 内核。

现有分页 Browse 是可复用基础，但应保留 `NumberReturned`、`TotalMatches`、`UpdateID`，并解析 SOAP Fault/UPnPError，而不是只抛原始 HTTP 文本。

### 5.4 DIDL-Lite 与资源 profile

当前实现从 `<res>` 保留 URL、`protocolInfo`、大小和分辨率，但随后主要根据 URL 中的 `ORG/LRG/SM/TN`、扩展名和尺寸打分。

Imaging Edge APK 的 `ResTag` 和 `BrowseResponseItem` 证明原版以 profile 为核心：

- `PN_ORIGINAL`
- `JPEG_TN`
- `JPEG_SM`
- `JPEG_LRG`
- `HEIF_TN`
- `HEIF_LRG`
- `SONY.COM_PN` 中的 AVC/XAVC 参数

并按文件扩展名区分 JPG、ARW、MP4、MTS、HIF 等内容类型。

目标实现需要 `SonyResourceProfile`，解析 `protocolInfo` 的 MIME、`DLNA.ORG_PN`、`SONY.COM_PN` 及资源属性。选择优先级为：

- 原始下载：`PN_ORIGINAL` > 明确的 RAW/JPEG 原始资源 > `JPEG_LRG`
- 缩略图：`JPEG_TN` 或 `HEIF_TN` > `JPEG_SM` > 可用预览资源
- 大图预览：`JPEG_LRG` 或 `HEIF_LRG` > `JPEG_SM`

URL 字符串替换只能作为末级兼容回退，并必须记录使用原因。

### 5.5 XPushList 相机端选片流程

当前 SonyEdge 完全没有 XPushList 客户端。Imaging Edge 7.8.5 中确认的流程为：

1. `X_TransferStart`
2. `X_GetPushRoot`
3. 使用 ContentDirectory 浏览返回的 `PushRoot`
4. 每完成一项后调用 `X_TransferProgress`，提交 `NumTotal` 与原版拼写的 `NumTransferd`
5. 结束时调用 `X_TransferEnd`，提交 `ErrCode`

服务类型及 SOAPAction 为 `urn:schemas-sony-com:service:XPushList:1`。后续实现必须从设备描述取得 XPushList 控制 URL，并保证取消、失败和正常完成都能落到明确的 `X_TransferEnd`。

### 5.6 SOAP 行为与错误

当前 ContentDirectory 请求已有带引号的 SOAPAction，但缺少统一请求器、标准 User-Agent、连接策略和结构化错误。

目标统一 SOAP 客户端应：

- 使用 `User-Agent: UPnP/1.0 DLNADOC/1.50`
- 使用 `Connection: close`
- 发送带引号的 `SOAPAction`
- 根据发现到的 service type/version 生成 namespace
- 对非 2xx 仍解析 SOAP Envelope
- 将 SOAP Fault、UPnP `errorCode/errorDescription` 转为显式异常
- 日志记录 action、目标 URL、HTTP 状态、耗时和错误码，但不打印大段二进制内容

### 5.7 下载完整性与重试

当前下载的主要风险：

- `Content-Length` 只用于进度，不与实际字节数比较。
- `DownloadValidator` 对未知或损坏文件不会拒绝。
- JPEG 只检查 SOI，未检查 EOI。
- 下载文件没有明确 `.part` 生命周期。
- MediaStore 插入失败后可能留下 pending 行。
- 无瞬时错误自动重试。
- 取消可能被后续状态覆盖成失败或完成。

目标实现：

- 下载到私有缓存中的唯一 `.part` 文件。
- 比较 HTTP Content-Length、DIDL `<res size>` 和实际大小；存在的声明值必须一致。
- JPEG 检查 SOI `FF D8` 与 EOI `FF D9`。
- ARW 检查 TIFF `II*\0` 或 `MM\0*` 文件头。
- 校验成功后才创建/提交 MediaStore 最终项。
- 任何失败删除 `.part` 和未提交 MediaStore 行。
- 仅对 503、超时、连接重置、unexpected EOF 等瞬时错误重试。
- 退避 300/800/1500 ms，单文件最多 5 次尝试。

### 5.8 网络绑定与日志

当前 `bindProcessToWifi` 绑定后没有任务级所有权，也没有 `bindProcessToNetwork(null)`。下载服务进程恢复时也不能保证使用相机 Wi-Fi。

目标行为：

- 发现、SOAP、Scalar 和下载任务都在明确的相机 Wi-Fi 网络上下文中执行。
- 任务开始时绑定或使用 `Network.openConnection`。
- 任务结束的 `finally` 中恢复 `bindProcessToNetwork(null)`。
- 日志使用稳定标签：`SonyEdge/SSDP`、`SonyEdge/UPnP`、`SonyEdge/SOAP`、`SonyEdge/DMS`、`SonyEdge/XPush`、`SonyEdge/Download`。

## 6. 风险优先级

### P0：实现后必须实机验证

- 标准 SSDP 多目标发现和动态设备描述。
- 资源 profile 解析与原图选择，防止把预览 JPEG 当原图。
- `.part`、长度/文件头校验、失败清理和自动重试。
- XPushList 完整生命周期。
- Wi-Fi 任务结束解绑。

### P1：同一阶段完成

- `GetSortCapabilities` 和排序降级。
- SOAP Fault/UPnPError 结构化异常。
- Compose 主流程的 Scalar 能力回退。
- 下载取消、重启和 MediaStore 事务一致性。

### P2：后续增强

- 持久化下载队列和进程重启恢复。
- ContentDirectory event subscription/update ID 增量刷新。
- 视频/XAVC 的完整导入体验和大文件断点续传。

## 7. Phase 2 实施顺序

1. 新增完整设备/服务模型与统一 SSDP/设备描述解析。
2. 新增统一 SOAP 请求器和错误模型。
3. 扩展 ContentDirectory capabilities、Browse 和 DIDL 数据模型。
4. 引入 `SonyResourceProfile` 并替换主要 URL 启发式。
5. 新增 XPushList 客户端和相机端选片状态机。
6. 重构下载临时文件、校验、重试和 MediaStore 提交。
7. 收敛 Wi-Fi 网络绑定作用域并补充可过滤日志。
8. 构建、安装，并按手机主动浏览、相机主动推送、JPEG/ARW、Scalar 四组流程执行 ADB 回归。

## 8. Phase 1 结论

SonyEdge 当前并非协议不可用：A7R III 的 ContentDirectory pull 浏览已经在实机工作，日期目录和照片下载链路具备可复用基础。但当前成功高度依赖 A7R III 的常见地址、端口和资源 URL 形态，尚未达到 Imaging Edge 的设备发现广度、资源 profile 精度、XPushList 支持和传输完整性。

因此 Phase 2 应保留现有 Compose UI 与浏览交互，重点替换协议底层的数据模型、发现、SOAP、DIDL 资源选择、推送状态机和下载事务，避免再次改动已经验证的界面架构。
