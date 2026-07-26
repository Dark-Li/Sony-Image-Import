package com.codex.sonyedge.ui.browse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.CameraConnectPhase
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.PrimaryButton
import com.codex.sonyedge.ui.shell.SecondaryButton
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.DisplayFamily
import com.codex.sonyedge.ui.theme.Motion
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

private val STEP_TITLES = listOf("连接相机 Wi-Fi", "验证相机", "准备照片")

/** 01b/01c · 连接中：垂直步骤时间线；失败时出现 rose 容器卡。 */
@Composable
fun ConnectingScreen(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val failed = state.connectPhase == CameraConnectPhase.Failed
    val currentStep = when (state.connectPhase) {
        CameraConnectPhase.RequestingWifi -> 0
        CameraConnectPhase.VerifyingCamera -> 1
        CameraConnectPhase.PreparingLibrary -> 2
        CameraConnectPhase.Failed -> state.connectFailedStep
        else -> 0
    }
    val model = state.rememberedCamera?.modelName?.takeIf { it.isNotBlank() }
        ?: state.connectedCamera?.modelName?.takeIf { it.isNotBlank() }
        ?: "Sony 相机"

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Dimens.pageMargin)
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(96.dp))
            Text(
                if (failed) "连接失败" else "正在连接",
                color = if (failed) colors.rose else colors.text3,
                fontSize = 13.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                model,
                color = colors.text1,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 27.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(44.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp)) {
                STEP_TITLES.forEachIndexed { index, title ->
                    ConnectStepRow(
                        title = title,
                        state = when {
                            index < currentStep -> StepState.Done
                            index == currentStep && failed -> StepState.Failed
                            index == currentStep -> StepState.Active
                            else -> StepState.Pending
                        },
                        showRail = index != STEP_TITLES.lastIndex
                    )
                }
            }
            if (failed) {
                Spacer(Modifier.height(28.dp))
                ConnectFailedCard(state, actions)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!failed) {
                MonoText("通常需要 5–10 秒", color = colors.text3, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
            TextButton(
                onClick = actions.onCancelCameraConnection,
                modifier = Modifier.defaultMinSize(minHeight = Dimens.touchTarget)
            ) {
                Text("取消连接", color = colors.text2, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (state.credentialsDialogVisible) {
        CameraCredentialsDialog(
            ssidPrefill = state.credentialsSsidPrefill,
            onDismiss = actions.onDismissCameraCredentials,
            onSubmit = actions.onSubmitCameraCredentials
        )
    }
}

private enum class StepState { Done, Active, Failed, Pending }

@Composable
private fun ConnectStepRow(title: String, state: StepState, showRail: Boolean) {
    val colors = SonyEdgeTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    StepState.Done -> Box(
                        Modifier.size(22.dp).background(colors.teal, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "已完成",
                            tint = if (colors.isDark) Color(0xFF0E0F11) else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    StepState.Active -> AmberSpinner(size = 22.dp)

                    StepState.Failed -> Box(
                        Modifier.size(22.dp).background(colors.rose, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PriorityHigh,
                            contentDescription = "失败",
                            tint = if (colors.isDark) Color(0xFF0E0F11) else Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    StepState.Pending -> Canvas(Modifier.size(20.dp)) {
                        drawCircle(
                            color = colors.text3,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
            Spacer(Modifier.width(18.dp))
            Text(
                title,
                color = when (state) {
                    StepState.Pending -> colors.text3
                    StepState.Failed -> colors.rose
                    else -> colors.text1
                },
                fontSize = 17.sp,
                fontWeight = if (state == StepState.Active || state == StepState.Failed) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Medium
                }
            )
        }
        if (showRail) {
            Box(
                Modifier
                    .padding(start = 11.dp)
                    .width(2.dp)
                    .height(34.dp)
                    .background(SonyEdgeTheme.colors.line)
            )
        }
    }
}

/** 进行中节点：琥珀旋转环 900ms/圈。 */
@Composable
private fun AmberSpinner(size: androidx.compose.ui.unit.Dp) {
    val colors = SonyEdgeTheme.colors
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.spinner, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinnerAngle"
    )
    Canvas(
        Modifier
            .size(size)
            .graphicsLayer { rotationZ = angle }
    ) {
        drawArc(
            color = colors.accent,
            startAngle = 0f,
            sweepAngle = 260f,
            useCenter = false,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(
                this.size.width - 2.dp.toPx(),
                this.size.height - 2.dp.toPx()
            )
        )
    }
}

/** 失败卡：rose 容器 + 重试 / 手动输入 Wi-Fi。 */
@Composable
private fun ConnectFailedCard(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val (title, message) = when (state.connectFailedStep) {
        0 -> "无法连接相机 Wi-Fi" to "请确认相机的发送功能已开启、Wi-Fi 名称与密码正确，然后重试。"
        2 -> "无法准备照片" to "已连接到相机，但读取照片列表失败。请靠近相机后重试。"
        else -> "无法验证相机" to "请确认相机屏幕停留在“发送到智能手机”界面并保持靠近，然后重试。"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.roseBg, RoundedCornerShape(Dimens.cornerCard))
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = colors.rose, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(message, color = colors.text2, fontSize = 13.5.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(14.dp))
            Row {
                PrimaryButton(
                    text = "重试",
                    onClick = actions.onConnectCameraWifi,
                    modifier = Modifier.weight(1f),
                    minHeight = Dimens.buttonPrimary
                )
                Spacer(Modifier.width(10.dp))
                SecondaryButton(
                    text = "手动输入 Wi-Fi",
                    onClick = actions.onAddCamera,
                    modifier = Modifier.weight(1.2f),
                    minHeight = Dimens.buttonPrimary
                )
            }
        }
    }
}
