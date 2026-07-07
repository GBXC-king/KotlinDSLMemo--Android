package com.example.memo.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgentThinkingPanel(
    stepRecords: List<AgentStepRecord>,
    isRunning: Boolean
) {
    if (stepRecords.isEmpty() && !isRunning) return

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🤖 Agent 思考过程",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(32.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (expanded) "收起" else "展开", fontSize = 12.sp)
            }
        }

        AnimatedVisibility(visible = expanded) {
            val configuration = LocalConfiguration.current
            val maxHeight = configuration.screenHeightDp * 0.45
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                stepRecords.forEach { step ->
                    AgentStepCard(step = step)
                }
                if (isRunning && stepRecords.isEmpty()) {
                    Text(
                        text = "正在思考中...",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AgentStepCard(step: AgentStepRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "步骤 ${step.stepIndex}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )

            step.thought?.let {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "💭 ",
                        fontSize = 12.sp
                    )
                    Text(
                        text = it,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            step.action?.let { action ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "⚡ ",
                        fontSize = 12.sp
                    )
                    Column {
                        Text(
                            text = "调用工具: $action",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        step.actionInput?.let { params ->
                            if (params.isNotEmpty()) {
                                val paramsStr = params.entries.joinToString(", ") { (k, v) -> "$k=$v" }
                                Text(
                                    text = "参数: $paramsStr",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            step.observation?.let { obs ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "📋 ",
                        fontSize = 12.sp
                    )
                    Text(
                        text = obs,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            step.finalAnswer?.let { answer ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "✅ ",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "完成",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
