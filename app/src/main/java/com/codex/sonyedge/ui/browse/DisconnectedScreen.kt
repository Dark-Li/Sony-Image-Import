package com.codex.sonyedge.ui.browse

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codex.sonyedge.SonyEdgeUiState
import com.codex.sonyedge.isCameraTransferBusy
import com.codex.sonyedge.ui.shell.BrandRow
import com.codex.sonyedge.ui.shell.MonoText
import com.codex.sonyedge.ui.shell.PrimaryButton
import com.codex.sonyedge.ui.shell.SecondaryButton
import com.codex.sonyedge.ui.shell.SectionLabel
import com.codex.sonyedge.ui.shell.SonyEdgeActions
import com.codex.sonyedge.ui.shell.TokenCard
import com.codex.sonyedge.ui.theme.Dimens
import com.codex.sonyedge.ui.theme.DisplayFamily
import com.codex.sonyedge.ui.theme.SonyEdgeTheme

/** 01a · 未连接：品牌行 + 相机插画 + 设备票据卡 + 扫码 + 帮助入口。 */
@Composable
fun DisconnectedScreen(state: SonyEdgeUiState, actions: SonyEdgeActions) {
    val colors = SonyEdgeTheme.colors
    val busy = isCameraTransferBusy(state)
    val remembered = state.rememberedCamera
    var helpVisible by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.pageMargin)
    ) {
        Spacer(Modifier.height(24.dp))
        BrandRow(subtitle = "索尼相机照片导入")
        Spacer(Modifier.height(28.dp))
        CameraIllustration(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(30.dp))

        if (remembered != null) {
            val model = remembered.modelName?.takeIf { it.isNotBlank() } ?: "Sony 相机"
            TokenCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    SectionLabel("已保存相机")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        model,
                        color = colors.text1,
                        fontFamily = DisplayFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    MonoText(remembered.ssid, fontSize = 11.5.sp, color = colors.text3)
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        text = "一键连接",
                        icon = Icons.Default.Bolt,
                        onClick = actions.onConnectCameraWifi,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = Dimens.buttonPrimary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = "扫描相机二维码",
                icon = Icons.Default.QrCodeScanner,
                onClick = actions.onScanCameraQr,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                minHeight = Dimens.buttonPrimary
            )
        } else {
            // 无保存相机：扫码升级为主 CTA
            PrimaryButton(
                text = "扫描相机二维码",
                icon = Icons.Default.QrCodeScanner,
                onClick = actions.onScanCameraQr,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                minHeight = Dimens.buttonPrimaryTall
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = actions.onAddCamera,
            enabled = !busy,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .defaultMinSize(minHeight = Dimens.touchTarget)
        ) {
            Text("添加其他相机", color = colors.text2, fontSize = 14.sp)
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(28.dp))
        TextButton(
            onClick = { helpVisible = true },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .defaultMinSize(minHeight = Dimens.touchTarget)
        ) {
            Icon(
                Icons.Default.HelpOutline,
                contentDescription = null,
                tint = colors.text3,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("连接遇到问题?", color = colors.text3, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
    }

    if (helpVisible) {
        ConnectHelpDialog(onDismiss = { helpVisible = false })
    }
    if (state.credentialsDialogVisible) {
        CameraCredentialsDialog(
            ssidPrefill = state.credentialsSsidPrefill,
            onDismiss = actions.onDismissCameraCredentials,
            onSubmit = actions.onSubmitCameraCredentials
        )
    }
}

/** 相机插画：深色机身 + 镜头 + 琥珀指示点。 */
@Composable
private fun CameraIllustration(modifier: Modifier = Modifier) {
    val colors = SonyEdgeTheme.colors
    val body = if (colors.isDark) Color(0xFF26282D) else Color(0xFF23252A)
    val lensOuter = if (colors.isDark) Color(0xFF1B1D21) else Color(0xFF17181C)
    Box(
        modifier
            .width(196.dp)
            .height(122.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.BottomCenter)
                .background(body, RoundedCornerShape(14.dp))
        )
        // 军舰部
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(44.dp)
                .height(20.dp)
                .background(body, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
        )
        // 手柄
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp, top = 10.dp)
                .width(22.dp)
                .height(84.dp)
                .background(lensOuter.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
        )
        // 镜头
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(top = 8.dp)
                .size(66.dp)
                .background(lensOuter, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Color(0xFF0B0C0E), CircleShape)
            )
        }
        // 琥珀指示点
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 30.dp, end = 20.dp)
                .size(10.dp)
                .background(colors.accentFill, CircleShape)
        )
    }
}

/** 「连接遇到问题?」三步指引对话框。 */
@Composable
private fun ConnectHelpDialog(onDismiss: () -> Unit) {
    val colors = SonyEdgeTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(Dimens.cornerCard),
        containerColor = colors.surface,
        title = { Text("如何连接相机", color = colors.text1, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HelpStep(1, "在相机菜单中打开「发送到智能手机」功能。")
                HelpStep(2, "首次连接时，用「扫描相机二维码」扫相机屏幕上的二维码。")
                HelpStep(3, "保持手机靠近相机，等待连接完成即可浏览照片。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun HelpStep(index: Int, text: String) {
    val colors = SonyEdgeTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .background(colors.amberBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$index", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = colors.text2, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}

/** 手动输入相机 Wi-Fi 信息（添加其他相机 / 连接失败后的手动通道）。 */
@Composable
fun CameraCredentialsDialog(
    ssidPrefill: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    val colors = SonyEdgeTheme.colors
    var ssid by remember(ssidPrefill) { mutableStateOf(ssidPrefill) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.line,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.text3,
        cursorColor = colors.accent,
        focusedTextColor = colors.text1,
        unfocusedTextColor = colors.text1
    )
    AlertDialog(
        onDismissRequest = {
            password = ""
            onDismiss()
        },
        shape = RoundedCornerShape(Dimens.cornerCard),
        containerColor = colors.surface,
        title = { Text("连接相机 Wi-Fi", color = colors.text1, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "请输入相机屏幕上显示的 Wi-Fi 信息。开放热点可留空密码。",
                    color = colors.text2,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Wi-Fi 名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(Dimens.cornerCard),
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("密码（可选）") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = colors.text3) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                tint = colors.text3
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    shape = RoundedCornerShape(Dimens.cornerCard),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val submittedSsid = ssid.trim()
                    val submittedPassword = password
                    password = ""
                    onSubmit(submittedSsid, submittedPassword)
                },
                enabled = ssid.isNotBlank()
            ) {
                Text(
                    "连接",
                    color = if (ssid.isNotBlank()) colors.accent else colors.text3,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                password = ""
                onDismiss()
            }) { Text("取消", color = colors.text2) }
        }
    )
}
