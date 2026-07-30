# Claude Code 提示词 — SonyEdge UI 重构(按设计文档落地)

> 用法:
> 1. 下载本项目的 `handoff/` 文件夹,把 `handoff/screens/` 整个拷入仓库 `docs/design/redesign-v1/screens/`(20 张高保真界面截图,浅色 + 深色 + 全部状态)。
> 2. 在仓库根目录运行 Claude Code,粘贴下面整段作为任务提示词。文字规格自包含;截图是视觉基准。

---

你是资深 Android 工程师,负责按已定稿的设计文档重构 SonyEdge 的 UI 层。仓库为 Kotlin + Jetpack Compose(Material 3),主 UI 目前集中在 `app/src/main/java/com/codex/sonyedge/SonyEdgeUi.kt`,状态在 `SonyEdgeViewModel.kt`,入口 `ComposeMainActivity.kt`。

## 视觉基准(先看图)

`docs/design/redesign-v1/screens/` 下的 20 张截图是每个页面的像素级基准,**动手写每个屏幕前先读对应截图**,以图为准、以下文规格为尺寸/色值依据:

| 截图 | 内容 |
|---|---|
| 01a-disconnected | 未连接(设备票据卡 + 扫码) |
| 01b-connecting / 01c-connect-failed | 连接步骤时间线 / 失败卡 + 手动 Wi-Fi 入口 |
| 01d-date-timeline | 日期时间线(连接后落点,含空目录行) |
| 01e-receiving-push | 相机推送横幅 |
| 02a-photo-grid / 02b-multi-select | 网格 / 多选 + 悬浮工具条 |
| 02c-empty-folder / 02d-timeout-retry | 空目录 / 读取超时 |
| 02e-photo-preview | 大图预览(沉浸黑 + 上下工具栏) |
| 03a–03e transfer-* | 传输五态:空 / 下载中 / 完成 / 部分失败 / 中断 |
| 04a-settings | 设置(分组卡 + 折叠高级) |
| 04b–04e dark-* | 深色模式四屏(未连接 / 时间线 / 多选 / 传输) |

截图中的风景照片均为占位示例;截图顶部标签与底部灰字为设计标注,不属于界面。

## 铁律(先读)

1. **只改 UI 层**。协议与下载栈零行为变更:`DiscoveryClient`、`DmsContentClient`(32 条分页)、`DownloadService`/`DownloadValidator`、`CameraWifiConnector`、`XPushListClient`、endpoint 缓存、`autoNextDateFolder`(停在 Date 层)、`folderCache`/`browseRequestId`、图片三级加载与缓存(128MiB 内存 / 512MiB 磁盘 / 7 天 TTL)、EXIF 方向、前台通知与 PendingIntent 目标全部保留。
2. 每完成一个阶段跑一次 `./gradlew assembleDebug`,编译不过不进入下一阶段。
3. 所有 UI 文案中文;IP/DMS/SSDP/PhotoRoot 等术语只允许出现在诊断日志导出内容中,不出现在界面上。
4. 小步提交,每阶段一个 commit,信息用中文。

## 设计方向

安静的专业摄影工具:浅色默认、深色为夜间模式(跟随系统 + 设置内可覆盖);照片是屏幕上最亮的内容;唯一强调色为琥珀,只用于主操作与选中;成功=teal、错误=rose、等待=amber;文件名/SSID/计数/速度/ETA/字节/路径/版本一律等宽字体;大日期数字是时间线的视觉锚点;大图预览两种主题下都是纯黑沉浸。卡片圆角 8dp,无渐变、无玻璃拟态、无胶囊泛滥。

## 阶段 0 · 主题令牌(ui/theme)

新建 `ui/theme` 包:`SonyEdgeColors`(data class + CompositionLocal)、`Type.kt`、`Dimens.kt`、`Motion.kt`。单一令牌源同时驱动 ColorScheme 与系统栏颜色(浅色页深图标,深色页与预览页浅图标)。

| 令牌 | Light | Dark |
|---|---|---|
| bg | #FAF9F7 | #0E0F11 |
| surface | #FFFFFF | #17181B |
| surface2 | #EFEDE8 | #212327 |
| line | #E5E2DB | #292B30 |
| text1 / text2 / text3 | #1D1C19 / #5C5A54 / #949088 | #F1F1EF / #A9ABAF / #686B71 |
| accent(文字/图标) | #B26E10 | #E8A33C |
| accentFill / onAccentFill | #E8A33C / #1C1204 | #E8A33C / #1C1204 |
| teal / rose / amber | #0E8578 / #C2483C / #96650A | #53CBBB / #E5695C / #E8A33C |
| tealBg / roseBg / amberBg | #DFF2EE / #FBE9E6 / #F7EDD6 | #132A27 / #331A17 / #332711 |

字体:UI 正文系统中文(Noto Sans SC 回退);数字展示 Space Grotesk 700(日期 30sp、进度百分比 26sp、机型 16–27sp);机器信息 IBM Plex Mono / JetBrains Mono 11–12.5sp。字体文件放 `res/font`,下载 latin 子集即可(数字/ASCII 用途)。
尺寸:页边距 20dp(卡片列表 16dp);圆角 卡片/按钮 8dp、次级 6dp、悬浮多选条 12dp;主按钮 44–48dp、次级 38–40dp、小按钮 34dp;顶栏 54dp;底部导航 72dp(含 16dp 手势内边距);触控目标 ≥44dp。
动效:导航切换 180ms fade-through;进出网格 240ms 共享轴水平;预览开合 220ms 淡入 + 0.96→1.0;工具栏显隐 180ms;选中缩略图 150ms;进度条 400ms;系统"减少动效"时去位移只留交叉淡化。

## 阶段 1 · 拆包(不改视觉)

把 `SonyEdgeUi.kt` 按边界拆为:`ui/shell`(脚手架、状态条、底部导航/Rail)、`ui/browse`(连接状态、日期时间线、网格、多选)、`ui/preview`、`ui/transfers`、`ui/settings`、`ui/image`(现有加载/缓存逻辑移到独立 API 后面,行为不变)。导航改为显式 sealed 路由,替代从 activeTab+状态推断。

## 阶段 2 · 逐屏重构(按以下规格)

**浏览 Tab 无独立首页**:未连接 → 连接中 → 日期时间线 → 照片网格。

1. **未连接**:品牌行(琥珀 10dp 方点 + SonyEdge);设备票据卡 = 眉题「已保存相机」11sp/0.12em 字距 + 机型 Space Grotesk 24sp + SSID mono + 实心「一键连接」46dp;次级描边「扫描相机二维码」;无保存相机时扫码升为主 CTA;底部「连接遇到问题?」弹帮助对话框(三步指引)。
2. **连接中**:居中机型名 SG 27sp;垂直步骤时间线(左 2dp 竖轨,节点 20dp):连接相机 Wi-Fi → 验证相机 → 准备照片;进行中=琥珀旋转环 900ms/圈、完成=teal 对勾、失败=rose;底部「取消连接」+“通常需要 5–10 秒”。失败后出现 rose 容器卡(重试 / 手动输入 Wi-Fi,表单内联展开);手动输入仅失败后可见。
3. **日期时间线**(连接成功落点):顶部状态条 58dp = teal 8dp 呼吸点 + 机型 + 刷新 + 断开(确认弹窗);行高 72dp = 日期数字 SG 30sp + “7月 · 周五”10.5sp + 右侧三格胶片条(56+34+34 × 46dp,5dp 圆角,取该目录前 3 张缩略图)+ mono 计数;空目录行透明度 55% + 虚线占位;相机推送时顶部插入 teal 横幅「正在接收相机选择 · 查看」。状态:骨架加载、超时(cloud_off + 重试)、空。
4. **照片网格**:全出血 `LazyVerticalGrid(Adaptive(108.dp))` ≈3 列、2dp 间距、1:1、不显示文件名、`itemKey` 保留;顶栏 54dp = 返回 + 日期 + mono 计数 + 多选按钮;首次进入可关闭提示条「长按缩略图可进入多选」;加载=12 格脉冲骨架。**多选**:顶栏变 关闭 + “已选 n 张”(n 琥珀) + 全选/反选;底部悬浮工具条(左右 12dp、底 16dp、12dp 圆角、半透明模糊背板)= 清除 + 实心「导入 n 张」(0 张禁用灰);选中图 3dp 琥珀描边 + 20dp 琥珀对勾 + 内容 0.85 缩放,未选图 75% 透明度;滚动区底部预留 126dp。
5. **大图预览**:纯黑;保留现有 pager、1×–5× 捏合、双击 2.2×、EXIF、邻图预取;顶部渐变遮罩 = 关闭 + 文件名 mono 12.5sp + “12 / 37” mono;底部渐变 = 前后翻页箭头 + 实心「导入」+ 幽灵「选择/已选」;单击切换控件显隐 180ms;控件含手势导航安全边距;预览层在网格之上,关闭回网格保留滚动位置。
6. **传输**:页眉「传输」19sp + mono 路径 `DCIM/Sony Picture` + 「打开相册」;任务卡 = 标题 14sp/600 + 状态字 11.5sp(正在导入=accent/已完成=teal/部分失败=rose/已中断=amber)+ 右侧大百分比 SG 26sp + 3dp 进度条 + mono 三行(当前文件 / 速度·剩余时间 / n/m 张·字节),数据直连 `onDownloadProgress` 现有字段;下载中含「取消导入」;部分失败含「重试失败 + 查看相册」;连接中断顶置 amber 横幅「重新连接」+ 卡内「继续」;空态含「浏览相机照片」。
7. **设置**:分组卡(眉题 11sp/0.12em):已保存的相机(机型 + SSID mono + rose「忘记」确认弹窗 + 添加其他相机)/ 接收(「接收相机选择」开关)/ 存储(保存位置 mono + 打开系统相册)/ 高级(可折叠:导出诊断日志、清除缩略图缓存)/ 关于(品牌方点 + 版本 mono)。
8. **通用**:确认弹窗 ≤300dp、8dp 圆角、危险确认 rose 700;Snackbar 反色底、底栏上方 14dp、3.2s;底部导航 3 项(浏览/传输/设置),选中=琥珀实底图标 + text1 标签,预览与多选时隐藏。

## 阶段 3 · 返回栈与响应式

系统返回优先级:关预览 → 退多选清空选择 → 网格回时间线 → 连接中取消 → 弹窗自关 → 传输/设置回浏览 → 退出。
响应式:<600dp 底部导航;600–839dp NavigationRail + 网格 minSize 132dp + 内容列限宽 560dp;≥840dp 可选双栏(左时间线/右网格)+ minSize 148dp;全部用 WindowInsets,不写死屏高;大字号下按钮换行不裁切。

## 验收清单

- [ ] assembleDebug 通过;深浅色下 8 个页面 + 空/加载/超时/多选/五种传输状态全部可达
- [ ] 界面无任何协议术语;按钮文字无换行/截断
- [ ] 500 张目录滚动不跳动、缩略图键稳定;多选工具条不遮挡最后一行
- [ ] 返回键优先级逐条实测;下载通知点击仍回传输页
- [ ] 断开时进行中任务转「已中断」,重连后可继续
- [ ] 大字号(fontScale 1.3)与手势导航安全区正常
