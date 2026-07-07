package com.example.memo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.agent.AgentConfig
import com.example.memo.autoui.service.FloatingBallService
import com.example.memo.autoui.util.AccessibilityHelper
import kotlinx.coroutines.launch

@Composable
fun AutoControlSettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    isAgentMode: Boolean
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isAutoControlEnabled by remember(visible) { mutableStateOf(false) }
    var isFloatingBallEnabled by remember(visible) { mutableStateOf(false) }
    var hasAccessibilityPermission by remember(visible) { mutableStateOf(false) }
    var hasOverlayPermission by remember(visible) { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            isAutoControlEnabled = AgentConfig.getAutoControlEnabled(context)
            hasAccessibilityPermission = AccessibilityHelper.isAccessibilityEnabled(context)
            hasOverlayPermission = AccessibilityHelper.canDrawOverlays(context)
            isFloatingBallEnabled = FloatingBallService.isRunning
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("手机自动操控", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 自动操控总开关
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAgentMode)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UI检测 + LLM决策",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (isAgentMode)
                                    "开启后AI可通过无障碍服务自动操控手机"
                                else
                                    "需先开启Agent模式才能使用此功能",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoControlEnabled && isAgentMode,
                            onCheckedChange = { enabled ->
                                if (!isAgentMode) return@Switch
                                isAutoControlEnabled = enabled
                                scope.launch {
                                    AgentConfig.toggleAutoControl(context, enabled)
                                }
                            },
                            enabled = isAgentMode
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 无障碍权限
                Card(
                    onClick = {
                        AccessibilityHelper.openAccessibilitySettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasAccessibilityPermission)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "无障碍权限",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (hasAccessibilityPermission) "已开启，AI可以操控手机" else "未开启，点击去设置",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (hasAccessibilityPermission) "✓" else "›",
                            fontSize = 20.sp,
                            color = if (hasAccessibilityPermission)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 悬浮窗权限
                Card(
                    onClick = {
                        AccessibilityHelper.openOverlaySettings(context)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasOverlayPermission)
                            MaterialTheme.colorScheme.surfaceVariant
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "悬浮窗权限",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (hasOverlayPermission) "已开启，悬浮球可正常显示" else "未开启，点击去设置",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (hasOverlayPermission) "✓" else "›",
                            fontSize = 20.sp,
                            color = if (hasOverlayPermission)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 悬浮球开关
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "悬浮球",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "开启后屏幕边缘显示AI悬浮球，点击可快速对话",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isFloatingBallEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (!hasOverlayPermission) {
                                        AccessibilityHelper.openOverlaySettings(context)
                                    } else {
                                        FloatingBallService.start(context)
                                        isFloatingBallEnabled = true
                                    }
                                } else {
                                    FloatingBallService.stop(context)
                                    isFloatingBallEnabled = false
                                }
                            },
                            enabled = hasOverlayPermission
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠️ 安全提示：AI操控手机时，点击屏幕任意位置可立即终止操作",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("返回")
            }
        },
        shape = MaterialTheme.shapes.medium
    )
}
