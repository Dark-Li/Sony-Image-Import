# SonyEdge 中文说明

SonyEdge 是一个面向老款 Sony 相机 Wi-Fi 照片导入协议的 Android 工具。它连接相机自己发出的 Wi-Fi，使用老版 Imaging Edge Mobile / PlayMemories Mobile 风格的 UPnP/DLNA ContentDirectory 服务浏览相机存储卡，并把可获得的原图下载到手机相册目录。

项目目前以 Sony A7R III（ILCE-7RM3）作为实机验证基线，但设计目标并不局限于 A7R III。理论上，只要相机通过旧版 Imaging Edge / PlayMemories 手机导入链路暴露类似的 Wi-Fi/DLNA 内容服务，就有机会被 SonyEdge 支持。不同机型、固件和相机连接模式可能会暴露不同端口、目录结构或文件格式，因此最终兼容性仍需要实机验证。

当前项目的重点不是做一个完整 Sony SDK 客户端，而是把“连接相机 Wi-Fi、按日期浏览、预览、选择、批量导入”这个流程做稳定，并把协议能力通过真实下载结果验证出来。

## 当前能力

- 连接老款 Sony 相机 Wi-Fi，并发现相机上的 DMS/ContentDirectory 服务。
- 记住可用的相机端点，减少每次重新探测的等待时间。
- 打开相机内容根目录后自动进入类似 `PhotoRoot / Date` 的日期目录，让用户直接选择拍摄日期文件夹。
- 按相机文件夹浏览照片，支持大文件夹分页加载。
- 使用 Kotlin + Jetpack Compose 构建主界面，保留旧 Java Activity 作为迁移参考。
- 提供 Library、Albums、Transfers、Tools 四个主要页面。
- 在 Library 中用网格浏览照片，支持缩略图、全屏预览、选择模式、批量下载。
- 支持预览页上一张/下一张、单张下载、选择状态切换。
- 下载文件保存到 Android 相册目录 `DCIM/Sony Picture`。
- 批量下载时显示当前文件、进度、成功/失败数量、取消和失败重试。
- 对下载结果做扩展名、MIME、文件大小和文件头检查，避免把“原图支持”当作未经验证的假设。
- 诊断日志被放在 Tools 页面，用于排查相机连接、机型兼容性和协议问题。

## 已验证状态

- Sony A7R III（ILCE-7RM3）的 DMS 服务发现已在 `192.168.122.1:64321` 上验证可用。
- 通过 A7R III 实机测试验证过原始 JPEG 下载。
- 项目仍保留 ScalarWebAPI 探测逻辑作为诊断路径，但当前主要导入路径是 UPnP/DLNA ContentDirectory。

其他老款 Sony 相机是否兼容，需要看它们在手机导入模式下是否暴露类似服务。SonyEdge 会尽量通过探测和诊断日志暴露这些能力，而不是硬编码假设某个机型一定支持。

RAW/ARW 是否能通过相机 Wi-Fi 直接导出，也必须以具体机型的实机协议返回为准。Sony 官方帮助文档说明“发送到智能手机”可能会把 RAW 转换为 JPEG，因此本项目不会默认宣称 RAW 一定可导出。

## 适用范围

优先目标：

- 使用旧版 Imaging Edge Mobile / PlayMemories Mobile Wi-Fi 导入链路的 Sony 相机。
- 相机在连接手机时暴露 UPnP/DLNA ContentDirectory 或类似内容浏览服务。
- 用户希望绕开官方 App 的后台断连、批量选择和导入效率问题。

不保证适用：

- 只支持新 Camera Remote SDK 或蓝牙/云同步链路的机型。
- 不在 Wi-Fi 导入模式下暴露照片目录的机型。
- 需要登录、加密认证或专有授权握手才能访问照片的机型。

## 界面结构

- **Library**：默认首页。显示当前相机文件夹中的照片网格，支持预览、选择和下载。
- **Albums**：相机文件夹浏览页。主要用于按日期文件夹进入某一天的照片。
- **Transfers**：下载任务页。显示当前批量导入状态、进度、失败项和打开图库入口。
- **Tools**：连接和诊断页。包含连接探测、Root、Refresh、诊断日志清理等维护操作。

## 构建要求

项目使用 Android Gradle Plugin、Kotlin 和 Jetpack Compose。

主要依赖：

- `androidx.recyclerview:recyclerview:1.3.2`
- `com.google.android.material:material:1.12.0`
- Kotlin Android plugin `2.0.21`
- `androidx.activity:activity-compose:1.9.3`
- Jetpack Compose UI/Foundation/Material icons `1.7.5`
- Jetpack Compose Material 3 `1.3.1`
- `androidx.exifinterface:exifinterface:1.3.7`

本地工具要求：

- JDK 17（可使用 Android Studio 自带 JBR）
- Android SDK，至少包含 `compileSdk 35`
- Android SDK Build Tools

示例构建命令：

```powershell
$env:JAVA_HOME = "D:\Software\Android Studio\jbr"
$env:ANDROID_HOME = "D:\Software\AndroidSDK"
$env:ANDROID_SDK_ROOT = "D:\Software\AndroidSDK"
& "C:\Users\N.k\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat" :app:assembleDebug
```

每次 `assembleDebug` 会生成常规 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

同时会归档一份带版本号和时间戳的 APK：

```text
app/build/outputs/versioned-apk/SonyEdge-v<versionName>-<versionCode>-debug-<timestamp>.apk
```

## 版本规则

当前基线：

- `versionName`: `0.1.14`
- `versionCode`: `15`

给真机安装新的构建前，需要递增版本号，避免不同 APK 在手机和归档目录中混淆。

推荐命令：

```powershell
.\scripts\bump-version.ps1 patch
```

较大的功能更新可以使用 `minor` 或 `major`。

更多说明见 [docs/release-versioning.md](docs/release-versioning.md)。

## 真机部署测试

连接 Android 手机并授权 USB 调试后，可以运行：

```powershell
.\scripts\android-deploy-test.ps1
```

脚本会构建 debug APK、安装到手机、启动 `com.codex.sonyedge`，并保存日志和截图：

- 日志：`build/device-logs/`
- 截图：`build/device-screenshots/`

只截图、不重新安装：

```powershell
.\scripts\android-screenshot.ps1
```

## 相机使用流程

1. 在相机上开启手机连接/发送到智能手机相关的 Wi-Fi 模式。
2. 在 Android 手机系统 Wi-Fi 设置中连接相机热点。
3. 打开 SonyEdge。
4. 在 Library 或 Tools 中执行连接/浏览。
5. App 会尽量自动进入日期文件夹列表。
6. 在 Albums 中选择某一天。
7. 在 Library 中预览、勾选照片并批量下载。
8. 下载完成后可在 Transfers 中打开图库查看。

如果连接失败，优先查看 Tools 页面诊断日志，确认手机是否仍连接在相机 Wi-Fi、相机是否还处于可传输状态，以及是否发现 `192.168.122.1:64321` 这类 DMS 服务。不同机型的网关和端口可能不同，诊断日志比固定地址更可靠。

## 分支管理

本项目使用简化 Git Flow：

- `master`：稳定可用版本。
- `develop`：日常集成分支。
- `feature/<name>`：新功能或试验分支。
- `fix/<name>`：针对性修复分支。

默认不推送 feature 分支。功能验证完成后，按用户指令合并到 `develop`；只有明确要求发布稳定版本时，才把 `develop` 合并到 `master`。

详细规则见 [docs/branching-workflow.md](docs/branching-workflow.md)。

## 项目文档

- [协议说明](docs/sony-imaging-edge-protocol.md)
- [Compose 依赖说明](docs/compose-dependency-setup.md)
- [版本与 APK 归档规则](docs/release-versioning.md)
- [分支工作流](docs/branching-workflow.md)
- [UI 迭代记录](docs/ui-iteration-log.md)

## 重要限制

- 本项目不实现 FTP 兜底路线。
- 当前导入能力以具体相机的实机协议返回为准；A7R III 是已验证基线，不是唯一目标机型。
- 相机 Wi-Fi 速度受设备、协议和相机硬件限制影响明显，批量导入大量原图时需要预期较长耗时。
- 如果相机端退出传输模式、手机切换网络或系统限制后台网络，下载任务可能失败，需要重新连接后重试。
