package com.codex.sonyedge.ui

import java.util.Locale

/** 日期目录解析结果：大数字 + 「7月 · 周五」副行。 */
data class DateFolderLabel(
    /** 两位日期字符串，如 "24"；解析失败为 null */
    val day: String?,
    /** 「7月 · 周五」；无法解析星期时仅「7月」；完全失败为 null */
    val subtitle: String?,
    /** 原始标题（回退展示用） */
    val raw: String
)

private val WEEKDAYS = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

/**
 * 解析相机日期目录标题（常见 "2026-07-24" / "20260724" / "07-24"）。
 * [dateHint] 为 DIDL 的 dc:date 字段，优先使用。
 */
fun parseDateFolderLabel(title: String, dateHint: String?): DateFolderLabel {
    val candidates = listOfNotNull(dateHint?.takeIf { it.isNotBlank() }, title)
    for (candidate in candidates) {
        // 兼容 "2026-07-24" / "2026-7-2" / "20260724"
        val full = Regex("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})").find(candidate)
            ?: Regex("(\\d{4})(\\d{2})(\\d{2})").find(candidate)
        if (full != null) {
            val (y, m, d) = full.destructured
            val year = y.toIntOrNull()
            val month = m.toIntOrNull()
            val day = d.toIntOrNull()
            if (year != null && month != null && day != null && month in 1..12 && day in 1..31) {
                val weekday = runCatching {
                    val cal = java.util.Calendar.getInstance()
                    cal.isLenient = false
                    cal.set(year, month - 1, day)
                    WEEKDAYS[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
                }.getOrNull()
                val subtitle = if (weekday != null) "${month}月 · $weekday" else "${month}月"
                return DateFolderLabel(String.format(Locale.US, "%02d", day), subtitle, title)
            }
        }
        val short = Regex("^(\\d{2})[-/.](\\d{2})$").find(candidate.trim())
        if (short != null) {
            val (m, d) = short.destructured
            val month = m.toIntOrNull()
            val day = d.toIntOrNull()
            if (month != null && day != null && month in 1..12 && day in 1..31) {
                return DateFolderLabel(String.format(Locale.US, "%02d", day), "${month}月", title)
            }
        }
    }
    return DateFolderLabel(null, null, title)
}

/** 日期目录标题 → 「7月24日」；无法解析时返回原文。 */
fun formatDateFolderTitle(title: String, dateHint: String? = null): String {
    val label = parseDateFolderLabel(title, dateHint)
    val day = label.day?.toIntOrNull() ?: return title
    val month = label.subtitle?.substringBefore("月")?.toIntOrNull() ?: return title
    return "${month}月${day}日"
}

/** 字节格式化："336 MB" / "1.3 GB" / "512 KB"。 */
fun formatTransferBytes(bytes: Long): String {
    if (bytes <= 0) return "0 KB"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb)
    val mb = bytes / (1024.0 * 1024.0)
    if (mb >= 1.0) {
        return if (mb >= 100) {
            String.format(Locale.US, "%.0f MB", mb)
        } else {
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
    val kb = bytes / 1024.0
    return if (kb >= 1.0) String.format(Locale.US, "%.0f KB", kb) else "$bytes B"
}

/** 速度："7.2 MB/s"；无数据时「计算中」。 */
fun transferSpeedText(speedBps: Long): String =
    if (speedBps > 0) "${formatTransferBytes(speedBps)}/s" else "计算中"

/** 剩余时间："剩 1 分 17 秒"。 */
fun transferEtaText(etaSeconds: Long, speedBps: Long, bytesTotal: Long): String =
    when {
        bytesTotal <= 0L -> "剩余未知"
        speedBps <= 0L -> "计算中"
        etaSeconds <= 0L -> "即将完成"
        else -> "剩 ${formatEtaDuration(etaSeconds)}"
    }

fun formatEtaDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return when {
        hours > 0 -> "$hours 时 $minutes 分"
        minutes > 0 -> "$minutes 分 $secs 秒"
        else -> "$secs 秒"
    }
}
