package com.codex.sonyedge.ui.theme

import androidx.compose.ui.unit.dp

/** 布局尺寸令牌。 */
object Dimens {
    /** 页面左右边距 */
    val pageMargin = 20.dp
    /** 卡片列表页左右边距 */
    val listMargin = 16.dp

    /** 卡片 / 按钮圆角 */
    val cornerCard = 8.dp
    /** 次级圆角 */
    val cornerSmall = 6.dp
    /** 悬浮多选工具条圆角 */
    val cornerFloating = 12.dp

    /** 主按钮高度 */
    val buttonPrimary = 46.dp
    val buttonPrimaryTall = 48.dp
    /** 次级按钮高度 */
    val buttonSecondary = 40.dp
    /** 小按钮高度 */
    val buttonSmall = 34.dp

    /** 顶栏高度 */
    val topBar = 54.dp
    /** 连接后状态条高度 */
    val statusBar = 58.dp
    /** 底部导航高度（含 16dp 手势内边距） */
    val bottomNav = 72.dp
    /** 最小触控目标 */
    val touchTarget = 44.dp

    /** 日期时间线行高 */
    val timelineRow = 72.dp
    /** 时间线胶片条：首格宽 */
    val filmstripLead = 56.dp
    /** 时间线胶片条：后续格宽 */
    val filmstripCell = 34.dp
    /** 时间线胶片条高度 */
    val filmstripHeight = 46.dp
    /** 胶片条圆角 */
    val filmstripCorner = 5.dp

    /** 网格自适应最小格宽（紧凑屏 ≈3 列） */
    val gridMinCell = 108.dp
    /** 600–839dp 网格最小格宽 */
    val gridMinCellMedium = 132.dp
    /** ≥840dp 网格最小格宽 */
    val gridMinCellExpanded = 148.dp
    /** 网格间距 */
    val gridGap = 2.dp
    /** 多选时网格底部预留（悬浮工具条不遮挡最后一行） */
    val gridSelectionBottomInset = 126.dp

    /** 确认弹窗最大宽度 */
    val dialogMaxWidth = 300.dp
}
