package com.example.memo.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Alarm
import com.example.memo.repository.RepositoryProvider
import com.example.memo.service.AlarmService
import com.example.memo.ui.theme.KotlinDSLMemoTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * 闹钟响铃全屏提醒界面
 *
 * 当闹钟触发时弹出，即使在锁屏黑屏状态下也会亮屏显示。
 * 提供黄色的稍后提醒按钮，以及一个可左右滑动关闭本次提醒的圆圈。
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // 确保窗口在锁屏上显示并保持屏幕常亮
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val alarmId = intent.getLongExtra(AlarmService.EXTRA_ALARM_ID, -1L)

        setContent {
            KotlinDSLMemoTheme {
                AlarmRingingScreen(alarmId = alarmId)
            }
        }
    }
}

@Composable
fun AlarmRingingScreen(alarmId: Long) {
    val context = LocalContext.current
    var alarm by remember { mutableStateOf<Alarm?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(alarmId) {
        if (alarmId > 0) {
            scope.launch {
                val loaded = RepositoryProvider.getAlarmRepository().getAlarmById(alarmId)
                alarm = loaded
            }
        }
    }

    val timeStr = remember {
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date())
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1B2A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 上方：时间和标题
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = timeStr,
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = alarm?.title ?: "闹钟",
                    fontSize = 24.sp,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // 中间：黄色稍后提醒按钮（始终显示，使用当前闹钟配置或默认 10 分钟）
            Button(
                onClick = { sendAction(context, AlarmService.ACTION_SNOOZE) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
            ) {
                Icon(
                    imageVector = Icons.Default.Snooze,
                    contentDescription = null,
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "稍后提醒 (${alarm?.snoozeInterval ?: 10} 分钟)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // 下方：滑动关闭
            SlideToDismiss(
                onDismiss = { sendAction(context, AlarmService.ACTION_STOP) }
            )
        }
    }
}

@Composable
fun SlideToDismiss(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thumbSize = 56.dp
    val thumbPx = with(density) { thumbSize.roundToPx() }

    // 使用 BoxWithConstraints 获取父容器最大宽度，确保定位准确
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(32.dp)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = with(density) { maxWidth.roundToPx() }
        val maxOffsetPx = remember(trackWidthPx) {
            (trackWidthPx - thumbPx - with(density) { 8.dp.roundToPx() }).coerceAtLeast(0)
        }
        val offsetX = remember { Animatable(maxOffsetPx.toFloat()) }

        // 当宽度变化时重新调整偏移量，确保拇指始终可见
        LaunchedEffect(maxOffsetPx) {
            if (offsetX.value > maxOffsetPx) {
                offsetX.snapTo(maxOffsetPx.toFloat())
            }
        }

        // 背景提示文字和左箭头
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 20.dp)
            )
            Text(
                text = "滑到左侧关闭闹钟",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        // 可拖动圆圈，初始在最右侧
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(thumbSize)
                .background(Color.White, CircleShape)
                .pointerInput(maxOffsetPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = offsetX.value
                            val threshold = maxOffsetPx * 0.2f
                            when {
                                current <= threshold -> {
                                    scope.launch {
                                        offsetX.animateTo(0f, tween(150))
                                        onDismiss()
                                    }
                                }
                                else -> {
                                    scope.launch {
                                        offsetX.animateTo(maxOffsetPx.toFloat(), tween(200))
                                    }
                                }
                            }
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        val newValue = (offsetX.value + dragAmount).coerceIn(0f, maxOffsetPx.toFloat())
                        scope.launch { offsetX.snapTo(newValue) }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "●",
                fontSize = 12.sp,
                color = Color(0xFF0D1B2A)
            )
        }
    }
}

private fun sendAction(context: Context, action: String) {
    val intent = Intent(context, AlarmService::class.java).apply {
        this.action = action
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
    if (context is AlarmRingingActivity) {
        context.finish()
    }
}
