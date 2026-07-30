package com.codex.sonyedge

import android.content.Context
import com.codex.sonyedge.ui.theme.ThemeMode

/** UI 偏好持久化：主题覆盖 / 接收相机选择开关 / 网格首次提示。 */
class UiPrefsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sonyedge_ui_prefs", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.System.name)!!)
        }.getOrDefault(ThemeMode.System)
        set(value) {
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    var receiveCameraSelection: Boolean
        get() = prefs.getBoolean(KEY_RECEIVE_SELECTION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RECEIVE_SELECTION, value).apply()
        }

    var gridHintDismissed: Boolean
        get() = prefs.getBoolean(KEY_GRID_HINT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GRID_HINT, value).apply()
        }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_RECEIVE_SELECTION = "receive_camera_selection"
        const val KEY_GRID_HINT = "grid_hint_dismissed"
    }
}
