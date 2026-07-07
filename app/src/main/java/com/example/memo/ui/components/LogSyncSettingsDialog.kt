package com.example.memo.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.util.LogSyncScheduler
import com.example.memo.util.LogZipHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSyncSettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 加载配置
    val emailConfig = remember { mutableStateOf(LogSyncScheduler.getEmailConfig(context)) }
    val syncTime = remember { mutableStateOf(LogSyncScheduler.getSyncTime(context)) }
    val syncEnabled = remember { mutableStateOf(LogSyncScheduler.isSyncEnabled(context)) }

    // 表单状态
    var smtpHost by remember { mutableStateOf(emailConfig.value.smtpHost) }
    var smtpPort by remember { mutableStateOf(emailConfig.value.smtpPort) }
    var emailUser by remember { mutableStateOf(emailConfig.value.emailUser) }
    var emailPassword by remember { mutableStateOf(emailConfig.value.emailPassword) }
    var emailTo by remember { mutableStateOf(emailConfig.value.emailTo) }
    var syncHour by remember { mutableStateOf(syncTime.value.first) }
    var syncMinute by remember { mutableStateOf(syncTime.value.second) }
    var isSharing by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // 时间选择器
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = syncHour,
            initialMinute = syncMinute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择同步时间") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    syncHour = timePickerState.hour
                    syncMinute = timePickerState.minute
                    LogSyncScheduler.saveSyncTime(context, syncHour, syncMinute)
                    if (syncEnabled.value) {
                        LogSyncScheduler.setSyncEnabled(context, true) // 重新调度
                    }
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志同步设置", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 一键发送日志邮件
                Button(
                    onClick = {
                        isSharing = true
                        scope.launch {
                            val config = LogSyncScheduler.getEmailConfig(context)
                            if (config.smtpHost.isBlank() || config.emailUser.isBlank() ||
                                config.emailPassword.isBlank() || config.emailTo.isBlank()) {
                                Toast.makeText(context, "请先配置邮箱信息", Toast.LENGTH_SHORT).show()
                                isSharing = false
                                return@launch
                            }

                            val zipUri = LogZipHelper.zipLogs(context)
                            if (zipUri == null) {
                                Toast.makeText(context, "打包日志失败", Toast.LENGTH_SHORT).show()
                                isSharing = false
                                return@launch
                            }

                            val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                                .format(java.util.Date())
                            val subject = "备忘录日志 - $now"
                            val body = "这是来自备忘录应用的手动日志发送。\n\n发送时间: $now"

                            val (success, errorMsg) = com.example.memo.util.EmailSender.sendEmail(
                                context = context,
                                smtpHost = config.smtpHost,
                                smtpPort = config.smtpPort,
                                username = config.emailUser,
                                password = config.emailPassword,
                                toEmail = config.emailTo,
                                subject = subject,
                                body = body,
                                attachmentUri = zipUri
                            )

                            if (success) {
                                Toast.makeText(context, "日志邮件发送成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "日志邮件发送失败: $errorMsg", Toast.LENGTH_LONG).show()
                            }
                            isSharing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSharing
                ) {
                    Text(if (isSharing) "发送中..." else "一键发送日志邮件")
                }

                HorizontalDivider()

                // 邮箱配置
                Text("邮箱配置", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedTextField(
                    value = smtpHost,
                    onValueChange = { smtpHost = it },
                    label = { Text("SMTP服务器") },
                    placeholder = { Text("例如: smtp.qq.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = smtpPort,
                    onValueChange = { smtpPort = it },
                    label = { Text("SMTP端口") },
                    placeholder = { Text("例如: 465") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailUser,
                    onValueChange = { emailUser = it },
                    label = { Text("发件人邮箱") },
                    placeholder = { Text("您的邮箱地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailPassword,
                    onValueChange = { emailPassword = it },
                    label = { Text("邮箱密码/授权码") },
                    placeholder = { Text("邮箱密码或授权码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = emailTo,
                    onValueChange = { emailTo = it },
                    label = { Text("收件人邮箱") },
                    placeholder = { Text("接收日志的邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        LogSyncScheduler.saveEmailConfig(
                            context,
                            smtpHost.trim(),
                            smtpPort.trim(),
                            emailUser.trim(),
                            emailPassword.trim(),
                            emailTo.trim()
                        )
                        Toast.makeText(context, "邮箱配置已保存", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存邮箱配置")
                }

                HorizontalDivider()

                // 定时同步设置
                Text("定时同步", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用定时同步", fontWeight = FontWeight.Medium)
                        Text(
                            text = "每天自动发送日志到指定邮箱",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = syncEnabled.value,
                        onCheckedChange = { enabled ->
                            syncEnabled.value = enabled
                            LogSyncScheduler.setSyncEnabled(context, enabled)
                            if (enabled) {
                                Toast.makeText(context, "定时同步已开启", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "定时同步已关闭", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("同步时间", fontWeight = FontWeight.Medium)
                        Text(
                            text = String.format("每天 %02d:%02d", syncHour, syncMinute),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showTimePicker = true }) {
                        Text("修改")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        shape = MaterialTheme.shapes.medium
    )
}
