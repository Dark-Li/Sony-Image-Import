package com.codex.sonyedge.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.MonoFamily
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

/** 实心主按钮：琥珀填充，44–48dp 高（大字号下允许换行不裁切）。 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    minHeight: androidx.compose.ui.unit.Dp = Dimens.buttonPrimary
) {
    val colors = SonyEdgeTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        shape = RoundedCornerShape(Dimens.cornerCard),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentFill,
            contentColor = colors.onAccentFill,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.text3
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

/** 次级描边按钮。 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    minHeight: androidx.compose.ui.unit.Dp = Dimens.buttonSecondary
) {
    val colors = SonyEdgeTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = minHeight),
        shape = RoundedCornerShape(Dimens.cornerCard),
        border = BorderStroke(1.dp, colors.line),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.text1,
            disabledContentColor = colors.text3
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Medium, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** 卡片容器：surface + line 描边 + 8dp 圆角，无阴影。 */
@Composable
fun TokenCard(
    modifier: Modifier = Modifier,
    container: Color = SonyEdgeTheme.colors.surface,
    border: Color = SonyEdgeTheme.colors.line,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = container,
        shape = RoundedCornerShape(Dimens.cornerCard),
        border = BorderStroke(1.dp, border),
        content = content
    )
}

/** 分组眉题：11sp / 0.12em 字距。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        color = SonyEdgeTheme.colors.text2,
        fontSize = 11.sp,
        letterSpacing = 1.3.sp,
        fontWeight = FontWeight.Medium
    )
}

/** 等宽信息文本（文件名 / SSID / 计数 / 路径 / 版本）。 */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = SonyEdgeTheme.colors.text2,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    maxLines: Int = 1
) {
    Text(
        text,
        modifier = modifier,
        color = color,
        fontFamily = MonoFamily,
        fontSize = fontSize,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
    )
}

/**
 * 通用确认弹窗：≤300dp 宽、8dp 圆角；危险确认文字用 rose 700。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false
) {
    val colors = SonyEdgeTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.sizeIn(maxWidth = Dimens.dialogMaxWidth),
            color = colors.surface,
            shape = RoundedCornerShape(Dimens.cornerCard),
            border = BorderStroke(1.dp, colors.line)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = colors.text1, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(message, color = colors.text2, fontSize = 13.sp, lineHeight = 19.sp)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = colors.text2, fontSize = 14.sp)
                    }
                    TextButton(onClick = onConfirm) {
                        Text(
                            confirmText,
                            color = if (danger) colors.rose else colors.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/** 品牌行：琥珀 10dp 方点 + SonyEdge 字标。 */
@Composable
fun BrandRow(subtitle: String? = null) {
    val colors = SonyEdgeTheme.colors
    Column {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(10.dp)
                    .background(colors.accentFill, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "SonyEdge",
                color = colors.text1,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                color = colors.text3,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
