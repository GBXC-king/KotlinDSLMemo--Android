package com.example.memo.ui.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.WatchListManager

/**
 * 影片类型筛选（电影/电视剧）
 */
enum class WatchMovieCategory(val displayName: String) {
    MOVIE("电影"),
    TV("电视剧")
}

/**
 * 观影记录列表弹窗
 *
 * 不同分类的展示方式：
 * - 未观看的影片（PENDING）：显示电影/电视剧切换按钮
 * - 看过的影片（WATCHED）：不需要分类，混合展示全部
 *
 * 翻页规则（与 AI 推荐弹窗不同）：
 * - 有多少显示多少，不循环补齐（避免首位相接）
 * - 末页即停，不再循环回第一页
 *
 * 弹窗关闭规则：
 * - 点弹窗外区域不会关闭弹窗（避免误触）
 * - 必须通过"返回"按钮关闭
 *
 * @param type 列表分类（PENDING 待看 / WATCHED 已看）
 * @param movieTitles 电影列表（仅 PENDING 使用）
 * @param tvTitles 电视剧列表（仅 PENDING 使用）
 * @param allTitles 全部影片（仅 WATCHED 使用，电影+电视剧合并）
 * @param onPlayMovie 用户点击影片卡片，参数为片名和分类。
 *        - PENDING 时：从未观看列表移除该片，加入已看并加当前时间戳
 *        - WATCHED 时：更新该片时间戳为当前时间（保留在已看）
 *        分类（电影/电视剧）用于正确归类到已看列表的对应字段
 */
@Composable
fun WatchRecordListDialog(
    type: WatchRecordStep,
    movieTitles: List<String>,
    tvTitles: List<String>,
    allTitles: List<String>,
    onBack: () -> Unit,
    onPlayMovie: (String, WatchMovieCategory) -> Unit
) {
    val batchSize = 5
    var selectedCategory by remember { mutableStateOf(WatchMovieCategory.MOVIE) }
    var offset by remember { mutableIntStateOf(0) }

    // 待看：根据选中的分类切换；已看：直接展示混合列表
    val titles = if (type == WatchRecordStep.PENDING) {
        if (selectedCategory == WatchMovieCategory.MOVIE) movieTitles else tvTitles
    } else {
        allTitles
    }

    // 当前批次的影片（不补齐：取多少返回多少，避免首位相接）
    val batch = WatchListManager.getBatch(titles, offset, batchSize, padToBatchSize = false)
    val totalBatches = if (titles.isEmpty()) 0 else
        ((titles.size + batchSize - 1) / batchSize).coerceAtLeast(1)
    val currentBatch = if (titles.isEmpty()) 0 else (offset / batchSize) + 1
    val canPrev = offset > 0
    val canNext = titles.size > batchSize && currentBatch < totalBatches

    AlertDialog(
        // 点弹窗外区域不关闭弹窗（避免误触；想关弹窗只能走"返回"按钮）
        onDismissRequest = { /* no-op */ },
        title = {
            // 标题行：分类名
            // 只有 PENDING（未观看）才显示电影/电视剧切换按钮
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (type == WatchRecordStep.PENDING) "未观看的影片" else "看过的影片",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                if (type == WatchRecordStep.PENDING) {
                    Spacer(modifier = Modifier.height(12.dp))
                    CategoryToggleRow(
                        selected = selectedCategory,
                        onSelect = { newCategory ->
                            if (newCategory != selectedCategory) {
                                selectedCategory = newCategory
                                offset = 0
                            }
                        }
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (titles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无${selectedCategory.displayName}记录",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    batch.forEach { title ->
                        WatchRecordCard(
                            title = title,
                            onClick = { onPlayMovie(title, selectedCategory) }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (titles.size > batchSize) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "共 ${titles.size} 个 · 第 $currentBatch / $totalBatches 批",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = {
                                    if (canPrev) {
                                        offset = (offset - batchSize).coerceAtLeast(0)
                                    }
                                },
                                enabled = canPrev
                            ) {
                                Text("上一页", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }

                            TextButton(
                                onClick = {
                                    if (canNext) {
                                        // 下一页：到末页即停，不再循环回第一页
                                        offset = (offset + batchSize).coerceAtMost(titles.size - 1)
                                    }
                                },
                                enabled = canNext
                            ) {
                                Text("下一页", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            // 把"返回"按钮放在 confirmButton 区域，明确指向 onBack 回调
            TextButton(onClick = onBack) {
                Text("返回", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {},
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * 电影/电视剧 互斥切换按钮组
 */
@Composable
private fun CategoryToggleRow(
    selected: WatchMovieCategory,
    onSelect: (WatchMovieCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WatchMovieCategory.values().forEach { category ->
            CategoryToggleButton(
                text = category.displayName,
                isSelected = category == selected,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(category) }
            )
        }
    }
}

/**
 * 单个互斥切换按钮（带选中态视觉反馈）
 */
@Composable
private fun CategoryToggleButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (isSelected) Color(0xFF1565C0) else Color(0xFFF5F5F5)
    val foreground = if (isSelected) Color.White else Color(0xFF333333)
    val border = if (isSelected) Color(0xFF1565C0) else Color(0xFFCCCCCC)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = foreground,
            fontSize = 17.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun WatchRecordCard(
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color(0xFF1565C0),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1565C0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
