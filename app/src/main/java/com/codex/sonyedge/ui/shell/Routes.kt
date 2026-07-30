package com.codex.sonyedge.ui.shell

import com.codex.sonyedge.CameraConnectPhase
import com.codex.sonyedge.SonyEdgeTab
import com.codex.sonyedge.SonyEdgeUiState

/**
 * 显式 sealed 路由：替代此前从 activeTab + 多个状态位推断当前屏幕的方式。
 * 大图预览是叠加层（previewIndex != null），不占路由。
 */
sealed interface SonyEdgeRoute {
    /** 浏览 Tab · 未连接（设备票据卡 + 扫码） */
    data object Disconnected : SonyEdgeRoute

    /** 浏览 Tab · 连接步骤时间线（含失败卡） */
    data object Connecting : SonyEdgeRoute

    /** 浏览 Tab · 日期时间线（连接后落点） */
    data object DateTimeline : SonyEdgeRoute

    /** 浏览 Tab · 照片网格（含空目录 / 超时状态） */
    data object PhotoGrid : SonyEdgeRoute

    data object Transfers : SonyEdgeRoute
    data object Settings : SonyEdgeRoute

    val isBrowse: Boolean
        get() = this !is Transfers && this !is Settings
}

/** 当前是否位于某个具体日期目录内（时间线的下一层）。 */
fun SonyEdgeUiState.isInsideDateFolder(): Boolean =
    folderStack.lastOrNull()?.title?.equals("Date", ignoreCase = true) == true ||
        photos.isNotEmpty()

fun routeFor(state: SonyEdgeUiState): SonyEdgeRoute = when (state.activeTab) {
    SonyEdgeTab.Transfers -> SonyEdgeRoute.Transfers
    SonyEdgeTab.Settings -> SonyEdgeRoute.Settings
    SonyEdgeTab.Library, SonyEdgeTab.Camera -> when (state.connectPhase) {
        CameraConnectPhase.RequestingWifi,
        CameraConnectPhase.VerifyingCamera,
        CameraConnectPhase.PreparingLibrary,
        CameraConnectPhase.Failed -> SonyEdgeRoute.Connecting

        CameraConnectPhase.Disconnected,
        CameraConnectPhase.WaitingForCredentials -> SonyEdgeRoute.Disconnected

        CameraConnectPhase.Connected ->
            if (state.isInsideDateFolder()) SonyEdgeRoute.PhotoGrid else SonyEdgeRoute.DateTimeline
    }
}
