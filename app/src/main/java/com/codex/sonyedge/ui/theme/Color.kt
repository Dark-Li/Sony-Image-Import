package com.codex.sonyedge.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * SonyEdge 设计令牌单一来源。
 * 同一份令牌同时驱动 Material ColorScheme 与系统栏配色。
 */
@Immutable
data class SonyEdgeColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    /** 强调色（文字/图标形态） */
    val accent: Color,
    /** 强调色（实心填充形态） */
    val accentFill: Color,
    val onAccentFill: Color,
    val teal: Color,
    val rose: Color,
    val amber: Color,
    val tealBg: Color,
    val roseBg: Color,
    val amberBg: Color,
    val isDark: Boolean
)

val SonyEdgeLightColors = SonyEdgeColors(
    bg = Color(0xFFFAF9F7),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFEDE8),
    line = Color(0xFFE5E2DB),
    text1 = Color(0xFF1D1C19),
    text2 = Color(0xFF5C5A54),
    text3 = Color(0xFF949088),
    accent = Color(0xFFB26E10),
    accentFill = Color(0xFFE8A33C),
    onAccentFill = Color(0xFF1C1204),
    teal = Color(0xFF0E8578),
    rose = Color(0xFFC2483C),
    amber = Color(0xFF96650A),
    tealBg = Color(0xFFDFF2EE),
    roseBg = Color(0xFFFBE9E6),
    amberBg = Color(0xFFF7EDD6),
    isDark = false
)

val SonyEdgeDarkColors = SonyEdgeColors(
    bg = Color(0xFF0E0F11),
    surface = Color(0xFF17181B),
    surface2 = Color(0xFF212327),
    line = Color(0xFF292B30),
    text1 = Color(0xFFF1F1EF),
    text2 = Color(0xFFA9ABAF),
    text3 = Color(0xFF686B71),
    accent = Color(0xFFE8A33C),
    accentFill = Color(0xFFE8A33C),
    onAccentFill = Color(0xFF1C1204),
    teal = Color(0xFF53CBBB),
    rose = Color(0xFFE5695C),
    amber = Color(0xFFE8A33C),
    tealBg = Color(0xFF132A27),
    roseBg = Color(0xFF331A17),
    amberBg = Color(0xFF332711),
    isDark = true
)

/** 大图预览沉浸层在两种主题下都是纯黑。 */
val PreviewBlack = Color(0xFF000000)

val LocalSonyEdgeColors = staticCompositionLocalOf { SonyEdgeLightColors }
