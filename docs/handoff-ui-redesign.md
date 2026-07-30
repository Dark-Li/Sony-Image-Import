# SonyEdge UI 重构 Handoff

分支：`feature/ui-connection-flow`（基于 master）
时间：2026-07-26
范围：按 `docs/design/redesign-v1/` 定稿设计完成 UI 层整体重构 + 三轮真机优化 + 应用图标替换。
**协议与下载栈零行为变更**（仅 `DmsContentClient` 新增一个只读方法、`DownloadService` 换了一个图标引用，见下文「协议栈接触点」）。

## 提交列表（旧 → 新）

| 提交 | 内容 |
|---|---|
| `d036edb` | 阶段0：`ui/theme` 主题令牌与字体资源 |
| `f3331b3` | 阶段1+2：拆包 UI 层并按设计稿逐屏重构 |
| `98ee7fb` | 阶段3：返回栈优先级、响应式与收尾清理 |
| `ae58a1c` | 优化：传输实时进度、时间线缩略图预取、状态条截断修复、日期解析兼容 |
| `39149a5` | 优化：缩略图裁掉相机自带 letterbox 黑边，网格铺满 |
| `9c7d49a` | 优化：大目录分页流式渲染 + 缩略图请求限流去重 |
| `888ac55` | feat: 新应用图标（橙环镜头 + α） |

## 文件地图

```
app/src/main/java/com/codex/sonyedge/
├── ComposeMainActivity.kt        # 入口：装配 SonyEdgeActions、edge-to-edge、日志导出分享
├── SonyEdgeViewModel.kt          # 状态层（原有逻辑 + UI 扩展状态，见下）
├── UiPrefsStore.kt               # SharedPreferences：主题覆盖/接收开关/网格提示
└── ui/
    ├── Format.kt                 # 日期目录解析(单/双位月日+紧凑格式)、字节/速度/ETA 格式化
    ├── theme/                    # 阶段0 令牌单一来源
    │   ├── Color.kt              # SonyEdgeColors 深浅两套 + CompositionLocal + PreviewBlack
    │   ├── Type.kt               # DisplayFamily(Space Grotesk 500/700) MonoFamily(JetBrains Mono)
    │   ├── Dimens.kt / Motion.kt # 尺寸/动效令牌；LocalReduceMotion
    │   └── Theme.kt              # SonyEdgeAppTheme：令牌→ColorScheme+系统栏；ThemeMode
    ├── shell/
    │   ├── Routes.kt             # sealed SonyEdgeRoute + routeFor(state)（显式路由）
    │   ├── SonyEdgeShell.kt      # SonyEdgeApp：主题/路由动效/底部导航/Rail/Snackbar/预览层
    │   └── Components.kt         # PrimaryButton/SecondaryButton/TokenCard/SectionLabel/
    │                             # MonoText/ConfirmDialog/BrandRow
    ├── browse/
    │   ├── DisconnectedScreen.kt # 01a + 帮助对话框 + 手动 Wi-Fi 表单(CameraCredentialsDialog)
    │   ├── ConnectingScreen.kt   # 01b/01c 步骤时间线 + 900ms 琥珀旋转环 + rose 失败卡
    │   ├── DateTimelineScreen.kt # 01d/01e 状态条(呼吸点)/推送横幅/胶片条/骨架/超时/空
    │   └── PhotoGridScreen.kt    # 02a-02d 网格/多选/悬浮工具条/提示条/骨架/空/超时
    ├── preview/PhotoPreviewOverlay.kt  # 02e 纯黑沉浸 + 渐变工具栏（pager/缩放逻辑原样迁移）
    ├── transfers/TransfersScreen.kt    # 03a-03e 五态 + 历史卡 + 中断横幅
    ├── settings/SettingsScreen.kt      # 04a 分组卡 + 折叠高级 + 外观(主题三选)
    └── image/
        ├── CameraImageLoader.kt  # 三级缓存原样迁移 + 黑边裁剪 + 4并发限流 + in-flight 去重
        └── CameraImages.kt       # RemoteCameraImage / ProgressiveCameraImage / Zoomable
```

已删除：`SonyEdgeUi.kt`（2423 行单文件，全部拆入上述包）。

## ViewModel 扩展（均为 UI 层状态，不触协议）

新增字段：`themeMode` `receiveCameraSelectionEnabled` `gridHintDismissed` `selectionMode`
`downloadBatchTitle` `downloadCurrentFile`(读现有 EXTRA_FILENAME) `transferHistory`
`transientMessage`(Snackbar) `connectFailedStep` `folderPreviews` `browseTotalCount`。

行为变化（UI 流程层）：
- 连接成功后不再进「已连接首页」，直接 `openFolder("0", auto)` 落到日期时间线
  （复用 `autoNextDateFolder`，仍停在 Date 层）；`connectionHomeVisible` 保留字段但恒 false。
- XPush 自动接收受设置开关（默认开=原行为）门控；接收失败后回落到常规浏览。
- 返回链：关预览 → 退多选 → 网格回时间线 → 连接中取消 → 传输/设置回浏览 → 退出
  （`backFromCameraContent` + `shouldHandleBack`；Scalar 平铺库无 Date 栈时直接退出，防卡死）。
- 批次终态自动归档 `TransferRecord`（Done/PartialFail/Interrupted），最新在前，留 20 条，仅会话内存。

## 协议栈接触点（全部为加法/引用，原路径未动）

1. `DmsContentClient.browseFirstItems(objectId, count)`：**新增**公开方法，单次 Browse
   取首页前 N 条（供时间线胶片条预取）；私有 `browsePage` 加了 requestedCount 重载，
   原 32 条分页调用点等价委托。
2. `SonyCameraRepository.browse(...)` 增加可选 `onPage` 回调（透传已有 `PageListener`），
   `browseFolderPreview(...)` 新增。
3. `DownloadService` 仅 `setSmallIcon` 换成 `R.drawable.ic_stat_sonyedge`。

## 三轮真机优化（ILCE-7RM3 实测）

1. **传输实时进度**：卡片百分比 = (已完成张数 + 当前文件字节进度) / 总数，跟随服务端
   500ms 广播连续增长（此前每张跳一次）；mono 三行显示文件名/速度·剩余/张数·字节。
2. **时间线**：胶片条串行预取每目录前 3 张（只写 `folderPreviews`，不污染 `folderCache`）；
   日期解析兼容 `2026-7-2`；状态条机型截断修复（weight 平分 bug）。
3. **黑边裁剪**：相机缩略图自带 letterbox，解码后按行/列亮度扫描裁边（≤20 亮度、整行几乎
   全黑才裁、每侧 ≤1/3 防误裁夜景）；仅网格/胶片条启用，预览原图；内存缓存按 `@trim` 分键。
4. **流式渲染**：800+ 张目录点入 ~0.5s 出第一页 32 张，顶栏 `n/total 张` 推进（此前 ~10s 白骨架）；
   浏览请求过期时回调抛异常中止剩余翻页；缩略图网络抓取 4 并发信号量 + 同 key 去重。

## 应用图标（方案 2c 橙环 + α）

- 三层 Adaptive：渐变背景 / 镜头组+α 前景 / monochrome 描边环+α；全 vector。
- α 来自 Noto Sans Bold U+03B1（fontsource greek 子集）fontTools 提取轮廓转 path，
  **非索尼官方字形**；未用任何官方素材。
- Legacy PNG 5 密度由 `scratchpad/render_icons.py`（Pillow，8x 超采样，22% 圆角）生成；
  脚本在会话临时目录，如需重生成可按提交内规格重写。
- 通知小图标 `ic_stat_sonyedge`（24dp 白，环+α）。
- 旧 `ic_launcher_artwork.png` / `ic_launcher_empty_foreground.xml` 已删。

## 设计取舍（与规格的偏差，均已在对话确认或注明）

- 手动 Wi-Fi 用统一规格对话框呈现（失败卡与「添加其他相机」入口），未做卡内内联展开。
- ≥840dp 双栏（规格标注"可选"）未实现；网格 minSize 108/132/148dp 分级已生效。
- 设置页新增「外观」分组（跟随系统/浅色/深色），规格正文提到但分组清单未列。
- 传输历史仅会话内存，重启应用清空（规格未要求持久化）。
- 中断任务的「继续」映射为重试失败项（`retryFailed`）；服务无按字节续传能力。

## 验证状态

真机（ILCE-7RM3 + vivo 系 Android，深浅色均过）：
- 连接三态、816 张目录流式加载与快速滚动、多选/导入/实时进度/完成归档、
  黑边裁剪、时间线预取、深色失败页/时间线 —— 截图验证全部通过。
- 图标：应用信息页 ✓；通知 icon 经 `dumpsys notification` 确认指向新资源 ✓；
  **桌面图标被该机第三方图标包接管**，显示不受应用控制（非 bug）；
  Android 13 主题图标（monochrome）未在无图标包设备上目测，待核。
- 未覆盖：fontScale 1.3 实测、连接中断→重连继续的完整链路（需人为断 Wi-Fi）、
  XPush 相机推送流程（需相机端操作）、600dp+ 平板布局。

## 环境备注

- 构建需 JDK 17+：`JAVA_HOME="C:/Program Files/Java/jdk-22" ./gradlew assembleDebug`
  （gradle.properties 未固定 JDK，命令行需显式指定）。
- assembleDebug 自动归档版本化 APK 到 `app/build/outputs/versioned-apk/`。
- adb 位于 `D:\Software\scrcpy-win64-v4.0\adb.exe`。
- 字体：`res/font/` 下 Space Grotesk 500/700 + JetBrains Mono 400/500
  （fontsource latin 子集，仅数字/ASCII 展示用途；中文正文走系统字体）。
