package com.codex.sonyedge.ui.theme

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** 动效时长令牌（ms）。 */
object Motion {
    /** 导航切换 fade-through */
    const val nav = 180
    /** 进出网格：共享轴水平 */
    const val grid = 240
    /** 预览开合：淡入 + 0.96→1.0 */
    const val preview = 220
    /** 预览关闭淡出 */
    const val previewExit = 160
    /** 工具栏显隐 */
    const val toolbar = 180
    /** 缩略图选中 */
    const val select = 150
    /** 进度条 */
    const val progress = 400
    /** 连接步骤旋转环一圈 */
    const val spinner = 900
}

/** 系统"减少动效"开启时为 true：去位移，只留交叉淡化。 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}
