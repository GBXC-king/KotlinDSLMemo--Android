package com.example.memo.ui.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.WatchListManager

/**
 * 影视推荐弹窗
 *
 * 展示一批卡片（每次 5 个），底部有刷新按钮。卡片点击后触发跳转内置浏览器。
 *
 * 支持两种模式：
 * - AI_RECOMMEND：AI 推荐的 15 个。用户点击其中一个 → 该名字进已看，其余 14 个进待看。
 *   用户不选关闭弹窗 → 全部 15 个进待看。
 * - PENDING_LIST：展示待看列表。用户点击其中一个 → 该名字进已看并从待看移除。
 *   用户不选关闭弹窗 → 无操作。
 *
 * 分批展示规则：一次 5 个，刷新后下一批 5 个；到末尾不足 5 个时从开头补齐（循环）。
 *
 * @param mode 弹窗模式
 * @param titles 完整名字列表（AI 推荐 15 个，或待看列表的全部）
 * @param watchType 类型（电影/电视剧/全部）
 * @param onPlayMovie 用户点击卡片时回调，参数为该卡片的名字
 * @param onDismissAll 用户关闭弹窗且未选择任何卡片时回调
 *   （AI_RECOMMEND 模式下将全部加入待看；PENDING_LIST 模式下由调用方决定是否处理）
 */
@Composable
fun MovieRecommendDialog(
    mode: MovieDialogMode,
    titles: List<String>,
    watchType: WatchListManager.WatchType,
    onPlayMovie: (String) -> Unit,
    onDismissAll: () -> Unit
) {
    var offset by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    val batchSize = 5

    AlertDialog(
        onDismissRequest = {
            if (selected == null) onDismissAll()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (mode) {
                        MovieDialogMode.AI_RECOMMEND -> "为您推荐"
                        MovieDialogMode.PENDING_LIST -> "待看列表"
                    },
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = {
                    if (selected == null) onDismissAll()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (titles.isEmpty()) {
                    Text(
                        text = "暂无推荐内容",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                } else {
                    // 当前这批 5 个
                    val batch = WatchListManager.getBatch(titles, offset, batchSize)
                    batch.forEach { title ->
                        MovieCard(
                            title = title,
                            onClick = {
                                selected = title
                                onPlayMovie(title)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "共 ${titles.size} 个 · 第 ${offset / batchSize + 1} 批",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { offset += batchSize },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("换一批")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun MovieCard(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFFE3F2FD),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color(0xFF1565C0),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color(0xFF1565C0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

enum class MovieDialogMode {
    /** AI 推荐的 15 个，点击后 1 个进已看、14 个进待看 */
    AI_RECOMMEND,
    /** 待看列表展示，点击后从待看移除并进已看 */
    PENDING_LIST
}
