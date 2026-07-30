package com.codex.sonyedge.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.codex.sonyedge.R

/**
 * 数字展示字体：Space Grotesk（latin 子集，仅用于数字 / ASCII 展示，
 * 日期大数字、进度百分比、机型名）。中文正文走系统字体。
 */
val DisplayFamily = FontFamily(
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold)
)

/**
 * 机器信息等宽字体：JetBrains Mono（文件名 / SSID / 计数 / 速度 / ETA /
 * 字节 / 路径 / 版本）。
 */
val MonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium)
)
