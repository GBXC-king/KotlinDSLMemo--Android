package com.example.memo.ui

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Alarm
import com.example.memo.data.hasDay
import com.example.memo.data.toggleDay

/**
 * 闹钟新建/编辑页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    alarm: Alarm?,
    onBack: () -> Unit,
    onSave: (Alarm) -> Unit
) {
    val context = LocalContext.current

    var title by remember { mutableStateOf(alarm?.title ?: "闹钟") }
    var hour by remember { mutableStateOf(alarm?.hour ?: 7) }
    var minute by remember { mutableStateOf(alarm?.minute ?: 0) }
    var repeatDays by remember { mutableStateOf(alarm?.repeatDays ?: Alarm.NEVER_MASK) }
    var ringtoneType by remember { mutableStateOf(alarm?.ringtoneType ?: Alarm.RINGTONE_SYSTEM) }
    var ringtoneUri by remember { mutableStateOf(alarm?.ringtoneUri ?: "") }
    var vibrate by remember { mutableStateOf(alarm?.vibrate ?: true) }
    var deleteAfterDismiss by remember { mutableStateOf(alarm?.deleteAfterDismiss ?: false) }
    var snoozeEnabled by remember { mutableStateOf(alarm?.snoozeEnabled ?: true) }
    var snoozeInterval by remember { mutableStateOf(alarm?.snoozeInterval ?: 10) }
    var snoozeCount by remember { mutableStateOf(alarm?.snoozeCount ?: 5) }

    var showTimePicker by remember { mutableStateOf(false) }
    var showSnoozeIntervalMenu by remember { mutableStateOf(false) }
    var showSnoozeCountMenu by remember { mutableStateOf(false) }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.let { uri ->
                ringtoneUri = uri.toString()
                ringtoneType = Alarm.RINGTONE_SYSTEM
            }
        }
    }

    fun openRingtonePicker() {
        val currentUri = if (ringtoneUri.isNotBlank()) Uri.parse(ringtoneUri) else null
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择闹钟铃声")
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        }
        ringtonePickerLauncher.launch(intent)
    }

    fun getRingtoneTitle(uriString: String): String {
        return if (uriString.isBlank()) {
            "默认闹钟铃声"
        } else {
            runCatching {
                RingtoneManager.getRingtone(context, Uri.parse(uriString))?.getTitle(context) ?: "未知铃声"
            }.getOrDefault("未知铃声")
        }
    }

    fun buildAlarm(): Alarm {
        return (alarm ?: Alarm()).copy(
            hour = hour,
            minute = minute,
            title = title.ifBlank { "闹钟" },
            repeatDays = repeatDays,
            ringtoneType = ringtoneType,
            ringtoneUri = ringtoneUri,
            vibrate = vibrate,
            deleteAfterDismiss = deleteAfterDismiss,
            snoozeEnabled = snoozeEnabled,
            snoozeInterval = snoozeInterval,
            snoozeCount = snoozeCount,
            isEnabled = alarm?.isEnabled ?: true
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { },
                actions = {
                    IconButton(
                        onClick = {
                            onSave(buildAlarm())
                            onBack()
                        }
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF5F5F5))
        ) {
            // 主题输入
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("主题", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("闹钟") },
                        singleLine = true
                    )
                }
            }

            // 时间选择
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("时间", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(
                        text = String.format("%02d:%02d", hour, minute),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2962FF)
                    )
                }
            }

            // 重复方式
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("重复方式", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    RepeatSelector(
                        repeatDays = repeatDays,
                        onRepeatDaysChange = { repeatDays = it }
                    )
                }
            }

            // 铃声
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = ringtoneType != Alarm.RINGTONE_SILENT) {
                                openRingtonePicker()
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("铃声", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (ringtoneType == Alarm.RINGTONE_SILENT) "静音" else getRingtoneTitle(ringtoneUri),
                                fontSize = 14.sp,
                                color = if (ringtoneType == Alarm.RINGTONE_SILENT) Color.Gray else Color(0xFF2962FF)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (ringtoneType == Alarm.RINGTONE_SILENT) Color.Gray else Color.Black,
                            modifier = Modifier.alpha(if (ringtoneType == Alarm.RINGTONE_SILENT) 0.3f else 1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("静音", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        Switch(
                            checked = ringtoneType == Alarm.RINGTONE_SILENT,
                            onCheckedChange = { checked ->
                                ringtoneType = if (checked) Alarm.RINGTONE_SILENT else Alarm.RINGTONE_SYSTEM
                            }
                        )
                    }
                }
            }

            // 震动
            SettingSwitchItem(
                title = "震动",
                checked = vibrate,
                onCheckedChange = { vibrate = it }
            )

            // 关闭后删除（仅一次性闹钟）
            if (repeatDays == Alarm.NEVER_MASK) {
                SettingSwitchItem(
                    title = "提醒关闭后删除此闹钟",
                    subtitle = "只作用于一次性闹钟",
                    checked = deleteAfterDismiss,
                    onCheckedChange = { deleteAfterDismiss = it }
                )
            }

            // 稍后提醒
            SettingSwitchItem(
                title = "稍后提醒",
                checked = snoozeEnabled,
                onCheckedChange = { snoozeEnabled = it }
            )

            if (snoozeEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSnoozeIntervalMenu = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("提醒间隔", modifier = Modifier.weight(1f), fontSize = 16.sp)
                            Text("$snoozeInterval 分钟", color = Color(0xFF2962FF), fontWeight = FontWeight.Medium)
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSnoozeCountMenu = true }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("提醒次数", modifier = Modifier.weight(1f), fontSize = 16.sp)
                            Text("$snoozeCount 次", color = Color(0xFF2962FF), fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        var pickerPeriod by remember { mutableStateOf(if (hour < 12) 0 else 1) }
        var pickerHour12 by remember { mutableStateOf(hourTo12(hour)) }
        var pickerMinute by remember { mutableStateOf(minute) }

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    hour = hour12To24(pickerPeriod, pickerHour12)
                    minute = pickerMinute
                    showTimePicker = false
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WheelPicker(
                        items = listOf("上午", "下午"),
                        selectedIndex = pickerPeriod,
                        onSelectedIndexChange = { pickerPeriod = it },
                        cyclic = false,
                        modifier = Modifier.width(80.dp)
                    )
                    WheelPicker(
                        items = (1..12).map { it.toString() },
                        selectedIndex = pickerHour12 - 1,
                        onSelectedIndexChange = { pickerHour12 = it + 1 },
                        cyclic = true,
                        modifier = Modifier.width(80.dp)
                    )
                    WheelPicker(
                        items = (0..59).map { it.toString().padStart(2, '0') },
                        selectedIndex = pickerMinute,
                        onSelectedIndexChange = { pickerMinute = it },
                        cyclic = true,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        )
    }

    if (showSnoozeIntervalMenu) {
        AlertDialog(
            onDismissRequest = { showSnoozeIntervalMenu = false },
            title = { Text("提醒间隔") },
            text = {
                Column {
                    Alarm.SNOOZE_INTERVALS.forEach { interval ->
                        TextButton(
                            onClick = {
                                snoozeInterval = interval
                                showSnoozeIntervalMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$interval 分钟")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    if (showSnoozeCountMenu) {
        AlertDialog(
            onDismissRequest = { showSnoozeCountMenu = false },
            title = { Text("提醒次数") },
            text = {
                Column {
                    Alarm.SNOOZE_COUNTS.forEach { count ->
                        TextButton(
                            onClick = {
                                snoozeCount = count
                                showSnoozeCountMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$count 次")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun RepeatSelector(
    repeatDays: Int,
    onRepeatDaysChange: (Int) -> Unit
) {
    val presets = listOf(
        "每天" to Alarm.EVERYDAY_MASK,
        "工作日" to Alarm.WEEKDAY_MASK,
        "周末" to Alarm.WEEKEND_MASK,
        "永不" to Alarm.NEVER_MASK
    )
    val firstRowDays = listOf("周一", "周二", "周三")
    val secondRowDays = listOf("周四", "周五", "周六", "周日")

    fun recomputeAfterToggle(dayBit: Int): Int {
        val toggled = repeatDays.toggleDay(dayBit)
        return when (toggled) {
            Alarm.EVERYDAY_MASK -> Alarm.EVERYDAY_MASK
            Alarm.WEEKDAY_MASK -> Alarm.WEEKDAY_MASK
            Alarm.WEEKEND_MASK -> Alarm.WEEKEND_MASK
            Alarm.NEVER_MASK -> Alarm.NEVER_MASK
            else -> toggled
        }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            presets.forEach { (name, mask) ->
                val selected = repeatDays == mask
                AssistChip(
                    onClick = { onRepeatDaysChange(mask) },
                    label = { Text(name) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) Color(0xFF2962FF) else Color(0xFFF0F0F0),
                        labelColor = if (selected) Color.White else Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            firstRowDays.forEachIndexed { index, name ->
                val selected = repeatDays.hasDay(index)
                AssistChip(
                    onClick = { onRepeatDaysChange(recomputeAfterToggle(index)) },
                    label = { Text(name) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) Color(0xFF2962FF) else Color(0xFFF0F0F0),
                        labelColor = if (selected) Color.White else Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            secondRowDays.forEachIndexed { index, name ->
                val actualIndex = index + firstRowDays.size
                val selected = repeatDays.hasDay(actualIndex)
                AssistChip(
                    onClick = { onRepeatDaysChange(recomputeAfterToggle(actualIndex)) },
                    label = { Text(name) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) Color(0xFF2962FF) else Color(0xFFF0F0F0),
                        labelColor = if (selected) Color.White else Color.Black
                    )
                )
            }
        }
    }
}



@Composable
fun SettingSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

private fun hourTo12(hour24: Int): Int {
    return when (hour24) {
        0 -> 12
        in 1..12 -> hour24
        else -> hour24 - 12
    }
}

private fun hour12To24(period: Int, hour12: Int): Int {
    return when {
        period == 0 && hour12 == 12 -> 0
        period == 0 -> hour12
        hour12 == 12 -> 12
        else -> hour12 + 12
    }
}

/**
 * 上下滚轮选择器（仿 iOS 滚轮样式）
 *
 * @param items 滚轮数据列表
 * @param selectedIndex 当前选中索引
 * @param onSelectedIndexChange 选中变化回调
 * @param cyclic 是否循环滚动
 * @param itemHeight 每项高度
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    cyclic: Boolean = false,
    itemHeight: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.roundToPx() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val scope = rememberCoroutineScope()

    // 循环模式下扩展列表：前后各追加 items.size 个元素，保证首尾衔接
    val extendedItems = remember(items, cyclic) {
        if (cyclic) {
            items + items + items
        } else {
            items
        }
    }

    // 循环模式下的初始偏移
    val initialOffset = if (cyclic) items.size + selectedIndex else selectedIndex

    LaunchedEffect(Unit) {
        if (cyclic) {
            listState.scrollToItem(initialOffset)
        }
    }

    // 监听滚动，检测选中项变化并触发震动
    var lastNotifiedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 滚动结束后吸附到最近项
            val firstVisible = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val targetIndex = if (offset > itemHeightPx / 2) firstVisible + 1 else firstVisible

            val actualIndex: Int
            if (cyclic) {
                actualIndex = ((targetIndex - items.size) % items.size + items.size) % items.size
            } else {
                actualIndex = targetIndex.coerceIn(0, items.size - 1)
            }

            if (actualIndex != lastNotifiedIndex) {
                lastNotifiedIndex = actualIndex
                onSelectedIndexChange(actualIndex)
                triggerHapticFeedback(context)
            }

            // 吸附动画
            val targetItem = if (cyclic) targetIndex else actualIndex
            listState.animateScrollToItem(targetItem)
        }
    }

    // 滚动过程中，每经过一个完整 item 就震动一次
    LaunchedEffect(Unit) {
        var lastCenter = -1
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .map { (idx, offset) -> idx + (offset / itemHeightPx) }
            .distinctUntilChanged()
            .collect { centerItem ->
                if (lastCenter != -1 && centerItem != lastCenter) {
                    triggerHapticFeedback(context)
                }
                lastCenter = centerItem
            }
    }

    Box(
        modifier = modifier
            .height(itemHeight * 5)
            .drawBehind {
                val lineHeight = 1.dp.toPx()
                val topY = (itemHeight * 2).toPx()
                val bottomY = (itemHeight * 3).toPx()
                val dividerColor = Color(0xFFB0B0B0).copy(alpha = 0.5f)
                drawLine(dividerColor, Offset(0f, topY), Offset(size.width, topY), lineHeight)
                drawLine(dividerColor, Offset(0f, bottomY), Offset(size.width, bottomY), lineHeight)
            }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * 2),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(extendedItems.size) { index ->
                val actualIndex: Int
                if (cyclic) {
                    actualIndex = ((index - items.size) % items.size + items.size) % items.size
                } else {
                    actualIndex = index
                }
                val isSelected = if (cyclic) {
                    index == initialOffset
                } else {
                    actualIndex == selectedIndex
                }

                Text(
                    text = extendedItems[index],
                    fontSize = if (isSelected) 24.sp else 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) Color(0xFF2962FF) else Color(0xFF333333),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .wrapContentHeight(Alignment.CenterVertically),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

private fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(10)
            }
        }
    } catch (_: Exception) {
    }
}
