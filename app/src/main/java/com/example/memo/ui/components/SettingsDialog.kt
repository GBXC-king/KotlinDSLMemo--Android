package com.example.memo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.memo.data.AppConfig
import com.example.memo.data.ModelProvider
import com.example.memo.util.BatteryOptimizationHelper
import kotlinx.coroutines.launch
import android.content.Intent
import android.net.Uri
import android.provider.Settings

@Composable
fun SettingsDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onModelConfigChange: (apiUrl: String, model: String, apiKey: String) -> Unit,
    onMemoryClick: () -> Unit = {},
    onAutoControlClick: () -> Unit = {}
) {
    if (visible) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // 模型供应商互斥状态
        var selectedProvider by remember(visible) { mutableStateOf(AppConfig.provider) }

        // DSK 草稿
        var tempDskApiUrl by remember(visible) { mutableStateOf(AppConfig.dskApiUrl) }
        var tempDskModel by remember(visible) { mutableStateOf(AppConfig.dskModel) }
        var tempDskApiKey by remember(visible) { mutableStateOf("") }

        // GLM 草稿
        var tempGlmApiUrl by remember(visible) { mutableStateOf(AppConfig.glmApiUrl) }
        var tempGlmModel by remember(visible) { mutableStateOf(AppConfig.glmModel) }
        var tempGlmApiKeyId by remember(visible) { mutableStateOf(AppConfig.glmApiKeyId) }
        var tempGlmSecret by remember(visible) { mutableStateOf("") }

        // 阿里云 DashScope 草稿
        var tempAliyunApiUrl by remember(visible) { mutableStateOf(AppConfig.aliyunApiUrl) }
        var tempAliyunModel by remember(visible) { mutableStateOf(AppConfig.aliyunModel) }
        var tempAliyunApiKey by remember(visible) { mutableStateOf("") }

        var isAgentMode by remember(visible) { mutableStateOf(false) }
        var showConfirmDialog by remember { mutableStateOf(false) }
        var confirmMessage by remember { mutableStateOf("") }
        var showModelConfigDetail by remember { mutableStateOf(false) }
        var showLogSyncSettings by remember { mutableStateOf(false) }
        var showOtherSettings by remember { mutableStateOf(false) }
        var showAboutDialog by remember { mutableStateOf(false) }

        LaunchedEffect(visible) {
            if (visible) {
                isAgentMode = AgentConfig.getAgentMode(context)
            }
        }

        /**
         * 保存当前选中供应商的配置。
         * 留空字段保留旧值，模型以当前已选供应商的 AppConfig 为准。
         */
        fun saveCurrentProviderConfig() {
            when (selectedProvider) {
                ModelProvider.GLM -> {
                    val finalUrl = if (tempGlmApiUrl.isBlank()) AppConfig.glmApiUrl else tempGlmApiUrl.trim()
                    val finalModel = if (tempGlmModel.isBlank()) AppConfig.glmModel else tempGlmModel.trim()
                    val finalKeyId = if (tempGlmApiKeyId.isBlank()) AppConfig.glmApiKeyId else tempGlmApiKeyId.trim()
                    val finalSecret = if (tempGlmSecret.isBlank()) AppConfig.glmSecret else tempGlmSecret.trim()

                    // 校验：secret 留空时要求用户明确知晓
                    if (tempGlmApiKeyId.isNotBlank() && tempGlmSecret.isBlank()) {
                        confirmMessage = "已填写 API Key ID 但未填写签名密钥 secret，将使用已保存的 secret。"
                        showConfirmDialog = true
                        AppConfig.saveGlmConfig(finalUrl, finalModel, finalKeyId, finalSecret)
                        AppConfig.provider = ModelProvider.GLM
                        return
                    }
                    AppConfig.saveGlmConfig(finalUrl, finalModel, finalKeyId, finalSecret)
                    AppConfig.provider = ModelProvider.GLM
                    onModelConfigChange(finalUrl, finalModel, AppConfig.glmFullApiKey)
                }
                ModelProvider.ALIYUN -> {
                    val finalUrl = if (tempAliyunApiUrl.isBlank()) AppConfig.aliyunApiUrl else tempAliyunApiUrl.trim()
                    val finalModel = if (tempAliyunModel.isBlank()) AppConfig.aliyunModel else tempAliyunModel.trim()
                    val finalKey = if (tempAliyunApiKey.isBlank()) AppConfig.aliyunApiKey else tempAliyunApiKey.trim()
                    AppConfig.saveAliyunConfig(finalUrl, finalModel, finalKey)
                    AppConfig.provider = ModelProvider.ALIYUN
                    onModelConfigChange(finalUrl, finalModel, finalKey)
                }
                else -> {
                    val emptyFields = mutableListOf<String>()
                    if (tempDskApiUrl.isBlank()) emptyFields.add("API_URL")
                    if (tempDskModel.isBlank()) emptyFields.add("MODEL")
                    if (tempDskApiKey.isBlank()) emptyFields.add("apiKey")

                    if (emptyFields.isNotEmpty()) {
                        confirmMessage = emptyFields.joinToString("、") { "请输入$it" } + "，否则将使用默认方案"
                        showConfirmDialog = true
                        return
                    }
                    AppConfig.saveDskConfig(tempDskApiUrl.trim(), tempDskModel.trim(), tempDskApiKey.trim())
                    AppConfig.provider = ModelProvider.DSK
                    onModelConfigChange(tempDskApiUrl.trim(), tempDskModel.trim(), tempDskApiKey.trim())
                }
            }
        }

        fun confirmSaveWithDefaults() {
            when (selectedProvider) {
                ModelProvider.GLM -> {
                    val finalUrl = if (tempGlmApiUrl.isBlank()) AppConfig.glmApiUrl else tempGlmApiUrl.trim()
                    val finalModel = if (tempGlmModel.isBlank()) AppConfig.glmModel else tempGlmModel.trim()
                    val finalKeyId = if (tempGlmApiKeyId.isBlank()) AppConfig.glmApiKeyId else tempGlmApiKeyId.trim()
                    val finalSecret = if (tempGlmSecret.isBlank()) AppConfig.glmSecret else tempGlmSecret.trim()
                    AppConfig.saveGlmConfig(finalUrl, finalModel, finalKeyId, finalSecret)
                    AppConfig.provider = ModelProvider.GLM
                    onModelConfigChange(finalUrl, finalModel, AppConfig.glmFullApiKey)
                }
                ModelProvider.ALIYUN -> {
                    val finalUrl = if (tempAliyunApiUrl.isBlank()) AppConfig.aliyunApiUrl else tempAliyunApiUrl.trim()
                    val finalModel = if (tempAliyunModel.isBlank()) AppConfig.aliyunModel else tempAliyunModel.trim()
                    val finalKey = if (tempAliyunApiKey.isBlank()) AppConfig.aliyunApiKey else tempAliyunApiKey.trim()
                    AppConfig.saveAliyunConfig(finalUrl, finalModel, finalKey)
                    AppConfig.provider = ModelProvider.ALIYUN
                    onModelConfigChange(finalUrl, finalModel, finalKey)
                }
                else -> {
                    val finalApiUrl = if (tempDskApiUrl.isBlank()) AppConfig.dskApiUrl else tempDskApiUrl.trim()
                    val finalModel = if (tempDskModel.isBlank()) AppConfig.dskModel else tempDskModel.trim()
                    val finalApiKey = if (tempDskApiKey.isBlank()) AppConfig.dskApiKey else tempDskApiKey.trim()
                    AppConfig.saveDskConfig(finalApiUrl, finalModel, finalApiKey)
                    AppConfig.provider = ModelProvider.DSK
                    onModelConfigChange(finalApiUrl, finalModel, finalApiKey)
                }
            }
            showConfirmDialog = false
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("设置", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 模型配置（点击进入详细页面）
                    Card(
                        onClick = { showModelConfigDetail = true },
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
                                    text = "模型配置",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = when (selectedProvider) {
                                        ModelProvider.GLM -> "当前使用：智谱 GLM"
                                        ModelProvider.ALIYUN -> "当前使用：阿里云 DashScope"
                                        else -> "当前使用：DeepSeek"
                                    },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 模型记忆
                    Card(
                        onClick = {
                            onMemoryClick()
                        },
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
                                    text = "模型记忆",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "管理AI对话记忆内容",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Agent模式开关
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Agent模式",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "开启后AI将使用分步确认的智能执行流程",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAgentMode,
                            onCheckedChange = {
                                isAgentMode = it
                                scope.launch {
                                    AgentConfig.toggleAgentMode(context, it)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // 其他（手机自动操控/电池优化白名单/制作人员 全部挪到二级弹窗）
                    Card(
                        onClick = { showOtherSettings = true },
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
                                    text = "其他",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "手机自动操控、电池优化白名单、制作人员",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    // 日志同步设置入口
                    Card(
                        onClick = { showLogSyncSettings = true },
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
                                    text = "日志同步",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "一键分享日志、定时邮件同步",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "›",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            },
            shape = MaterialTheme.shapes.medium
        )

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("提示", fontWeight = FontWeight.Bold) },
                text = { Text(confirmMessage) },
                confirmButton = {
                    TextButton(onClick = { confirmSaveWithDefaults() }) {
                        Text("使用默认")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("返回修改")
                    }
                },
                shape = MaterialTheme.shapes.medium
            )
        }

        if (showModelConfigDetail) {
            AlertDialog(
                onDismissRequest = { showModelConfigDetail = false },
                title = { Text("模型配置", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ===== 互斥按钮：DSK / GLM / 阿里云 =====
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ProviderToggleButton(
                                label = "DSK",
                                subLabel = "DeepSeek",
                                selected = selectedProvider == ModelProvider.DSK,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedProvider = ModelProvider.DSK }
                            )
                            ProviderToggleButton(
                                label = "GLM",
                                subLabel = "智谱 BigModel",
                                selected = selectedProvider == ModelProvider.GLM,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedProvider = ModelProvider.GLM }
                            )
                            ProviderToggleButton(
                                label = "阿里云",
                                subLabel = "DashScope",
                                selected = selectedProvider == ModelProvider.ALIYUN,
                                modifier = Modifier.weight(1f),
                                onClick = { selectedProvider = ModelProvider.ALIYUN }
                            )
                        }

                        HorizontalDivider()

                        when (selectedProvider) {
                            ModelProvider.GLM -> {
                                // ===== GLM 配置界面 =====
                                OutlinedTextField(
                                    value = tempGlmApiUrl,
                                    onValueChange = { tempGlmApiUrl = it },
                                    label = { Text("API URL") },
                                    placeholder = { Text("例如: https://open.bigmodel.cn/api/paas/v4/chat/completions") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempGlmModel,
                                    onValueChange = { tempGlmModel = it },
                                    label = { Text("模型名称") },
                                    placeholder = { Text("例如: glm-4.7-flash") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempGlmApiKeyId,
                                    onValueChange = { tempGlmApiKeyId = it },
                                    label = { Text("API Key ID") },
                                    placeholder = { Text("智谱 API Key ID") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempGlmSecret,
                                    onValueChange = { tempGlmSecret = it },
                                    label = { Text("签名密钥 secret") },
                                    placeholder = { Text("留空保留已保存的 secret") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "保存后将自动使用 {API Key ID}.{secret} 作为完整鉴权 Token。",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ModelProvider.ALIYUN -> {
                                // ===== 阿里云 DashScope 配置界面 =====
                                OutlinedTextField(
                                    value = tempAliyunApiUrl,
                                    onValueChange = { tempAliyunApiUrl = it },
                                    label = { Text("API URL") },
                                    placeholder = { Text("例如: https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempAliyunModel,
                                    onValueChange = { tempAliyunModel = it },
                                    label = { Text("模型名称") },
                                    placeholder = { Text("例如: liveportrait") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempAliyunApiKey,
                                    onValueChange = { tempAliyunApiKey = it },
                                    label = { Text("API Key") },
                                    placeholder = { Text("阿里云 DashScope API Key（留空保留已保存）") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "默认使用 OpenAI 兼容接口 + liveportrait 模型。",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                // ===== DSK 配置界面 =====
                                OutlinedTextField(
                                    value = tempDskApiUrl,
                                    onValueChange = { tempDskApiUrl = it },
                                    label = { Text("API URL") },
                                    placeholder = { Text("例如: https://api.deepseek.com/v1") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempDskModel,
                                    onValueChange = { tempDskModel = it },
                                    label = { Text("模型名称") },
                                    placeholder = { Text("例如: deepseek-chat") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = tempDskApiKey,
                                    onValueChange = { tempDskApiKey = it },
                                    label = { Text("API Key") },
                                    placeholder = { Text("您的API密钥") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "提示：留空将使用默认配置",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        saveCurrentProviderConfig()
                        showModelConfigDetail = false
                    }) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showModelConfigDetail = false }) {
                        Text("取消")
                    }
                },
                shape = MaterialTheme.shapes.medium
            )
        }

        LogSyncSettingsDialog(
            visible = showLogSyncSettings,
            onDismiss = { showLogSyncSettings = false }
        )

        // 其他二级弹窗：手机自动操控 / 电池优化白名单 / 制作人员
        if (showOtherSettings) {
            AlertDialog(
                onDismissRequest = { showOtherSettings = false },
                title = { Text("其他", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 手机自动操控
                        OtherSettingsItem(
                            title = "手机自动操控",
                            description = "无障碍权限、悬浮球、自动操控开关等",
                            onClick = {
                                showOtherSettings = false
                                onAutoControlClick()
                            }
                        )
                        // 电池优化白名单
                        OtherSettingsItem(
                            title = "电池优化白名单",
                            description = "允许应用在后台运行闹钟，避免被系统省电策略杀死",
                            onClick = {
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                            }
                        )
                        // 制作人员
                        OtherSettingsItem(
                            title = "制作人员",
                            description = "查看开发者与版本信息",
                            onClick = {
                                showOtherSettings = false
                                showAboutDialog = true
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOtherSettings = false }) {
                        Text("返回设置")
                    }
                },
                shape = MaterialTheme.shapes.medium
            )
        }

        // 制作人员弹窗（在"其他"二级弹窗里点开）
        if (showAboutDialog) {
            AlertDialog(
                onDismissRequest = { showAboutDialog = false },
                title = { Text("制作人员", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("开发者：皖西学院304的king")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("版本：6.1.0")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("© 2026 所有雷霆权利保留")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("这是一个基于 Kotlin 和 Jetpack Compose 开发的备忘录应用")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAboutDialog = false }) {
                        Text("确定")
                    }
                },
                shape = MaterialTheme.shapes.medium
            )
        }

    }
}

/**
 * DSK / GLM 互斥切换按钮
 */
@Composable
private fun ProviderToggleButton(
    label: String,
    subLabel: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Surface(
        modifier = modifier
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subLabel,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * "其他"二级弹窗的单个条目
 */
@Composable
private fun OtherSettingsItem(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "›",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
