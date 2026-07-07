package com.example.memo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Alarm
import com.example.memo.data.repeatText
import com.example.memo.ui.components.SettingsDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListContent(
    alarms: List<Alarm>,
    isSelectionMode: Boolean,
    selectedAlarms: Set<Alarm>,
    onAlarmClick: (Alarm) -> Unit,
    onAlarmLongClick: (Alarm) -> Unit,
    onAlarmToggle: (Alarm, Boolean) -> Unit,
    onExitSelectionMode: () -> Unit,
    // 顶部右上角三点菜单（与笔记主界面一致：设置、观影记录）
    showSettingsDialog: Boolean = false,
    onShowSettingsDialog: () -> Unit = {},
    onDismissSettingsDialog: () -> Unit = {},
    onModelConfigChange: (apiUrl: String, model: String, apiKey: String) -> Unit = { _, _, _ -> },
    onMemoryClick: () -> Unit = {},
    onAutoControlClick: () -> Unit = {},
    onShowWatchRecord: () -> Unit = {}
) {
    SettingsDialog(
        visible = showSettingsDialog,
        onDismiss = onDismissSettingsDialog,
        onModelConfigChange = onModelConfigChange,
        onMemoryClick = onMemoryClick,
        onAutoControlClick = onAutoControlClick
    )

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            TopAppBar(
                title = { Text("已选择 ${selectedAlarms.size} 项", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = onExitSelectionMode) {
                        Text("取消", color = Color(0xFF2962FF), fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        } else {
            Column(
                modifier = Modifier
                    .background(Color.White)
                    // 状态栏留白：和笔记主界面一致，避免左上角文字被状态栏遮挡
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "我的闹钟",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = {
                                    menuExpanded = false
                                    onShowSettingsDialog()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("观影记录") },
                                onClick = {
                                    menuExpanded = false
                                    onShowWatchRecord()
                                }
                            )
                        }
                    }
                }
                Text(
                    text = "${alarms.size} 个闹钟",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5)),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alarms) { alarm ->
                    AlarmCard(
                        alarm = alarm,
                        isSelected = alarm in selectedAlarms,
                        isSelectionMode = isSelectionMode,
                        onClick = { onAlarmClick(alarm) },
                        onLongClick = { onAlarmLongClick(alarm) },
                        onToggle = { enabled -> onAlarmToggle(alarm, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    alarm: Alarm,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val timeText = String.format("%02d:%02d", alarm.hour, alarm.minute)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = timeText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (alarm.isEnabled) Color.Black else Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = alarm.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (alarm.isEnabled) Color.Black else Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = alarm.repeatText(),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF2962FF) else Color.Transparent)
                        .clickable { onClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            } else {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle,
                    enabled = !isSelectionMode
                )
            }
        }
    }
}
