package com.example.memo.ui.movie

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.WatchListManager

/**
 * 观影记录流程的当前步骤
 *
 * 用一个 state 统一管理弹窗流程，避免多个 boolean 互相覆盖。
 * ENTRY = 入口弹窗（未观看/已看 二选一）
 * PENDING = 未观看列表弹窗
 * WATCHED = 已看列表弹窗
 *
 * 返回逻辑：列表弹窗点"返回"时把 state 设为 ENTRY，入口弹窗自动重新显示
 */
enum class WatchRecordStep { ENTRY, PENDING, WATCHED }

/**
 * 观影记录入口弹窗
 *
 * 用户从主界面右上角菜单点击"观影记录"后弹出，包含两个入口：
 * - 未观看的影片：跳转到待看列表
 * - 看过的影片：跳转到已看列表
 */
@Composable
fun WatchRecordDialog(
    onDismiss: () -> Unit,
    onSelectPending: () -> Unit,
    onSelectWatched: () -> Unit
) {
    AlertDialog(
        // 点弹窗外区域不关闭弹窗（避免误触；想关弹窗只能走"关闭"按钮）
        onDismissRequest = { /* no-op */ },
        title = { Text("观影记录", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WatchRecordEntryItem(
                    title = "未观看的影片",
                    description = "查看待看电影和电视剧列表",
                    icon = { Icon(Icons.Default.Movie, contentDescription = null, tint = Color(0xFF1565C0)) },
                    onClick = onSelectPending
                )
                WatchRecordEntryItem(
                    title = "看过的影片",
                    description = "查看已观看过的电影和电视剧记录",
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32)) },
                    onClick = onSelectWatched
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", fontSize = 16.sp) }
        },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun WatchRecordEntryItem(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    description,
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
            }
            Text(
                text = "›",
                fontSize = 24.sp,
                color = Color(0xFF999999)
            )
        }
    }
}
