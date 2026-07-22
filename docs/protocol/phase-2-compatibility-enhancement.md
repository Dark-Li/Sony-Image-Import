# SonyEdge Phase 2 协议兼容增强与实机验证

## 1. 范围

本阶段以 Imaging Edge Mobile 7.8.5 的可观察网络行为为参考，增强 SonyEdge 对老款 Sony 相机 Wi-Fi 导入协议的兼容性。分析仅针对用户自有 APK、相机和手机，不复制官方代码，不绕过认证、加密或访问控制。

分析输入：

- 官方 APK：`D:/Data/Downloads/imaging_edge_mobile_ver7.8.5.apk`
- JADX：`D:/Software/jadx-gui-1.5.3-win`
- APK SHA-256：`7E9C23DBF420E0BA8827ECAF3E2B8A72BA6E3865388FEE1F5980A906D7EECEDE`
- 实机：Sony `ILCE-7RM3`、OPPO `CPH2025`、ADB 序列号 `909e29e1`

## 2. 已实现能力

### 2.1 发现与设备描述

- SSDP 同时搜索 `ssdp:all`、root device、MediaServer、ContentDirectory、XPushList 和 ScalarWebAPI。
- 每个目标发送两轮 M-SEARCH，并解析状态码、`LOCATION`、`USN`、`ST`、`SERVER` 和缓存期限。
- 快速结束同时遵守 `MX: 2`，并等待最后一个新 Sony `LOCATION` 收敛；没有 Sony 响应时保留 4.5 秒完整窗口。
- 每次连接重新获取 UPnP 设备描述，避免切换相机或工作模式后复用旧服务集合。
- 解析设备 identity、UDN、service type、control URL、SCPD URL、event URL 和 Scalar action-list URL。

### 2.2 ContentDirectory 与 DIDL

- 首次浏览调用 `GetSortCapabilities`，A7R III 返回 `dc:date`，客户端选择 `-dc:date`。
- 排序能力按设备 UDN、描述地址和控制端点缓存；临时失败不写入全局缓存。
- 相机拒绝排序条件时，单次回退为空 `SortCriteria`。
- `BrowseDirectChildren` 按 32 项分页，保留 `NumberReturned`、`TotalMatches`、`UpdateID`。
- 解析 container 日期、child count、class、限制属性，以及 item 的多组 `<res>`。
- 根据 `DLNA.ORG_PN`、`SONY.COM_PN`、MIME、大小和分辨率区分 thumbnail、large preview 和 original。

### 2.3 Scalar 与 XPushList

- ScalarWebAPI 从设备描述动态获取服务 URL，调用前缓存 `getAvailableApiList`，内容列表支持计数和分页。
- ContentDirectory 缺失时可通过 Scalar `setCameraFunction` 进入 Contents Transfer 后重新发现服务。
- XPushList 实现 `X_TransferStart`、`X_GetPushRoot`、`X_TransferProgress`、`X_TransferEnd`。
- 相机端选片列表收到后自动交给前台下载服务，完整列表由服务持有并逐文件报告进度。
- `X_TransferStart` 后由会话守卫持有所有权；ViewModel 销毁会把尚未移交的会话交给进程级清理协调器，下载服务启动成功后才原子移交终结责任。清理协调器对同一会话去重，并按退避重试终止请求。
- `X_TransferProgress` 和 `X_TransferEnd` 有有限重试；只有 `X_TransferEnd` 成功后才向 UI 发布完整成功状态。

### 2.4 下载、校验和网络绑定

- 下载使用唯一 `.part` 文件，发布 MediaStore 前校验 HTTP 长度、DIDL 长度和实际文件长度。
- 响应读取以 `Content-Length` 优先、DIDL `<res size>` 次选；达到精确长度后不再等待 Sony HTTP 服务关闭连接。
- JPEG 从 SOI 开始解析顶层 marker 流，跳过 APP/EXIF 段中的内嵌缩略图，支持 stuffed byte、RST 和多扫描；允许主 EOI 后存在 Sony 尾随数据。
- ARW 按 TIFF little/big endian 文件头和声明格式交叉验证；HTTP 与 DIDL 均未给出长度时拒绝保存，避免把正常 EOF 和连接提前关闭混为一谈。
- 对 503、超时、连接重置、EOF 和截断采用最多 5 次有限退避重试；失败和取消删除临时文件及待发布 MediaStore 条目。
- 浏览、SOAP、XPush 和下载共享引用计数式进程 Wi-Fi 绑定；存在活跃租约时不会切换或解绑网络。

## 3. A7R III 实机结果

当前相机模式设备描述：

| 项目 | 实测值 |
| --- | --- |
| Friendly name | `ILCE-7RM3` |
| UDN | `uuid:00000000-0000-0010-8000-e8e8b7349c13` |
| 描述地址 | `http://192.168.122.1:64321/dd.xml` |
| 内容服务 | `urn:schemas-upnp-org:service:ContentDirectory:1` |
| 内容控制地址 | `http://192.168.122.1:64321/upnp/control/ContentDirectory` |
| 其他服务 | ConnectionManager、DigitalImaging |
| 当前未广告 | XPushList、ScalarWebAPI |

浏览验证：

- 自动进入 `Camera / PhotoRoot / Date`。
- 日期根目录返回 6 个文件夹。
- `2026-7-21` 返回 37 张照片，分页为 32 + 5。
- 日期目录浏览没有重复调用 `GetSortCapabilities`。
- 每张 JPEG 返回 4 个资源；下载选择 `ORG_DSC06913.JPG`，预览选择 `JPEG_LRG`/`JPEG_TN`。

下载验证：

- 文件：`DSC06913.JPG`
- 实际长度：`36,012,032` 字节
- MIME：`image/jpeg`
- 文件头：`FF D8 FF E1 ... Exif ... II 2A 00`
- 主 JPEG EOI 后 Sony 附加数据：`801,791` 字节（顶层 marker 流解析结果）
- 保存位置：`DCIM/Sony Picture/DSC06913.JPG`
- Android crash buffer：空

最终候选 `0.3.9 (26)` 已在同一实机完成发现、日期目录浏览、37 项分页和单张 JPEG 原图下载；该版本同时将 XPush 终止重试交由进程级协调器持有。实机证据保存在忽略目录 `build/protocol-analysis/device-test-v0.3.*`，包含 UI tree、截图和按协议标签过滤的 logcat。

## 4. 当前能力矩阵

| 能力 | 代码状态 | A7R III 当前模式实测 |
| --- | --- | --- |
| SSDP 多目标发现 | 已实现 | 通过 |
| 完整设备描述解析 | 已实现 | 通过 |
| 按日期列目录 | 已实现 | 通过 |
| DMS 分页 | 已实现 | 37 项通过 |
| JPEG 缩略图/预览 | 已实现 | 通过 |
| JPEG 原图下载 | 已实现 | 通过 |
| ARW 原图下载 | 已实现校验与资源选择 | 当前目录未广告 ARW，未实测 |
| XPush 相机端选片 | 已实现 | 当前模式未广告 XPushList；无服务错误路径通过 |
| Scalar 回退 | 已实现 | 当前模式未广告 ScalarWebAPI，未实测 |

## 5. 未解决限制

- A7R III 当前“手机浏览相机内容”模式只广告 ContentDirectory，无法在同一会话内完成 XPushList 和 Scalar 的成功路径验证。
- 当前日期目录只提供 JPEG original，没有 ARW 资源；不能把 ARW 代码校验等同于实机成功。
- Android 平台 XML 工厂不支持部分 XXE/secure-processing feature。设备描述仅来自用户主动连接的本地相机 AP，但仍需在后续版本评估替代解析器或预解析 DOCTYPE 拒绝。
- SOAP 使用阻塞式 `HttpURLConnection`，协程取消不能立即中断所有阻塞调用，最长受连接和读取超时约束。
- 尚未完成 20/100 张持续批量导入、锁屏和后台长时间压力测试。

## 6. 交付物

- 候选 APK：`app/build/outputs/versioned-apk/SonyEdge-v0.3.9-26-debug-20260722-123322.apk`
- Phase 1 报告：`docs/protocol/phase-1-protocol-gap-analysis.md`
- 本报告：`docs/protocol/phase-2-compatibility-enhancement.md`
