package com.codex.sonyedge.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 主题模式：跟随系统 / 强制浅色 / 强制深色（设置内可覆盖）。 */
enum class ThemeMode { System, Light, Dark }

/** 便捷访问当前令牌：`SonyEdgeTheme.colors.xxx` */
object SonyEdgeTheme {
    val colors: SonyEdgeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSonyEdgeColors.current
}

@Composable
fun resolveDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/**
 * 令牌单一来源同时驱动 ColorScheme 与系统栏颜色。
 * [previewImmersive] 为 true 时（大图预览层打开）系统栏转纯黑 + 浅图标。
 */
@Composable
fun SonyEdgeAppTheme(
    darkTheme: Boolean,
    previewImmersive: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) SonyEdgeDarkColors else SonyEdgeLightColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accentFill,
            onPrimary = colors.onAccentFill,
            primaryContainer = colors.amberBg,
            onPrimaryContainer = colors.accent,
            secondary = colors.text2,
            onSecondary = colors.bg,
            background = colors.bg,
            onBackground = colors.text1,
            surface = colors.surface,
            onSurface = colors.text1,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.text2,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.rose,
            onError = colors.bg,
            errorContainer = colors.roseBg,
            onErrorContainer = colors.rose,
            tertiary = colors.teal,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface2,
            inverseSurface = colors.text1,
            inverseOnSurface = colors.bg
        )
    } else {
        lightColorScheme(
            primary = colors.accentFill,
            onPrimary = colors.onAccentFill,
            primaryContainer = colors.amberBg,
            onPrimaryContainer = colors.accent,
            secondary = colors.text2,
            onSecondary = colors.bg,
            background = colors.bg,
            onBackground = colors.text1,
            surface = colors.surface,
            onSurface = colors.text1,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.text2,
            outline = colors.line,
            outlineVariant = colors.line,
            error = colors.rose,
            onError = colors.surface,
            errorContainer = colors.roseBg,
            onErrorContainer = colors.rose,
            tertiary = colors.teal,
            surfaceContainer = colors.surface,
            surfaceContainerHigh = colors.surface2,
            surfaceContainerHighest = colors.surface2,
            inverseSurface = colors.text1,
            inverseOnSurface = colors.bg
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            if (previewImmersive) {
                window.statusBarColor = PreviewBlack.toArgb()
                window.navigationBarColor = PreviewBlack.toArgb()
            } else {
                window.statusBarColor = colors.bg.toArgb()
                window.navigationBarColor = colors.surface.toArgb()
            }
            val controller = WindowCompat.getInsetsController(window, view)
            val lightIcons = !darkTheme && !previewImmersive
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }

    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(
        LocalSonyEdgeColors provides colors,
        LocalReduceMotion provides reduceMotion
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
