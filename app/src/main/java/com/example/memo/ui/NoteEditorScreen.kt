package com.example.memo.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.memo.data.CalendarHelper
import com.example.memo.data.ContactInfo
import com.example.memo.data.Note
import com.example.memo.data.PhoneHelper
import com.example.memo.data.PhoneInfo
import java.text.SimpleDateFormat
import java.util.*

/**
 * 笔记编辑页面
 *
 * 该 Composable 函数渲染笔记的编辑界面，支持新建和编辑两种模式。
 * 核心功能：
 * 1. 编辑笔记的标题和内容
 * 2. 保存笔记（新建或更新）
 * 3. 自动识别标题中的日期时间格式（如 "2026.06.08.14.30"），
 *    并在保存时自动创建系统日历事件
 * 4. 自动识别标题中的打电话格式（如 "打电话给张三" 或 "张三13912345678"），
 *    并在保存时搜索通讯录或直接拨打
 * 5. 处理日历、通讯录、打电话等权限的请求
 *
 * @param note    要编辑的笔记，null 表示新建模式，非 null 表示编辑已有笔记
 * @param onBack  返回按钮回调（返回列表页）
 * @param onSave  保存按钮回调，参数为 (标题, 内容)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    note: Note?,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    // 标题和内容的本地状态，编辑模式下初始化为已有笔记的内容，新建模式下为空字符串
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }

    // 获取当前 Compose 上下文，用于权限检查和日历操作
    val context = LocalContext.current

    // 是否有待处理的电话操作（此时不应立即返回主页）
    var hasPendingPhoneOperation by remember { mutableStateOf(false) }

    // 是否有待处理的日历操作
    var hasPendingCalendarOperation by remember { mutableStateOf(false) }

    // 待处理的日历事件信息
    var pendingCalendarInfo by remember { mutableStateOf<Triple<String, String, Date>?>(null) }

    // ==================== 通讯录相关状态 ====================
    // 搜索到的联系人列表
    var searchedContacts by remember { mutableStateOf<List<ContactInfo>>(emptyList()) }
    // 是否显示联系人选择对话框
    var showContactDialog by remember { mutableStateOf(false) }
    // 要拨打的电话号码（仅号码，无姓名）
    var phoneToCall by remember { mutableStateOf<String?>(null) }
    // 是否显示号码已存在对话框（用于创建联系人场景）
    var showExistingContactDialog by remember { mutableStateOf(false) }
    // 已存在的联系人信息
    var existingContact by remember { mutableStateOf<ContactInfo?>(null) }
    // 是否显示拨打电话确认对话框
    var showCallConfirmDialog by remember { mutableStateOf(false) }
    // 即将拨打的联系人信息
    var contactToCall by remember { mutableStateOf<ContactInfo?>(null) }

    // 提取的电话信息
    var extractedPhoneInfo by remember { mutableStateOf<PhoneInfo?>(null) }

    // 待处理权限类型的标记
    var pendingPermissionType by remember { mutableStateOf<String?>(null) }

    /**
     * 通讯录权限请求启动器
     */
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingPermissionType = "READ_CONTACTS"
        }
    }

    /**
     * 打电话权限请求启动器
     */
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingPermissionType = "CALL_PHONE"
        }
    }

    /**
     * 写入通讯录权限请求启动器
     */
    val writeContactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingPermissionType = "WRITE_CONTACTS"
        }
    }

    /**
     * 拨打电话
     */
    fun makePhoneCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果拨打电话失败，尝试使用 ACTION_DIAL（不需要权限）
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            context.startActivity(intent)
        }
    }

    /**
     * 处理提取到的电话信息
     */
    fun handlePhoneInfo(info: PhoneInfo) {
        when {
            // 仅有电话号码，直接拨打
            info.phone != null && info.name == null -> {
                phoneToCall = info.phone
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    makePhoneCall(info.phone)
                } else {
                    callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                }
            }
            // 有姓名和电话号码，检查号码是否已存在
            info.phone != null && info.name != null -> {
                val existing = PhoneHelper.searchContactByPhone(context, info.phone)
                if (existing != null) {
                    existingContact = existing
                    showExistingContactDialog = true
                } else {
                    // 号码不存在，创建新联系人
                    val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        val success = PhoneHelper.createContact(context, info.name, info.phone)
                        if (success) {
                            android.widget.Toast.makeText(context, "已创建联系人：${info.name}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        writeContactPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                    }
                }
            }
            // 仅有姓名，搜索通讯录
            info.name != null && info.phone == null -> {
                val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    searchedContacts = PhoneHelper.searchContactsByName(context, info.name)
                    if (searchedContacts.isNotEmpty()) {
                        showContactDialog = true
                    } else {
                        android.widget.Toast.makeText(context, "未找到联系人：${info.name}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
    }

    // 监听权限请求结果
    LaunchedEffect(pendingPermissionType) {
        when (pendingPermissionType) {
            "READ_CONTACTS" -> {
                pendingPermissionType = null
                extractedPhoneInfo?.let { handlePhoneInfo(it) }
            }
            "CALL_PHONE" -> {
                pendingPermissionType = null
                phoneToCall?.let { makePhoneCall(it) }
            }
            "WRITE_CONTACTS" -> {
                pendingPermissionType = null
                extractedPhoneInfo?.let { info ->
                    if (info.name != null && info.phone != null) {
                        val success = PhoneHelper.createContact(context, info.name, info.phone)
                        if (success) {
                            android.widget.Toast.makeText(context, "已创建联系人：${info.name}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * 安全地处理电话信息（先检查权限，无权限则请求）
     */
    fun handlePhoneInfoSafely(info: PhoneInfo) {
        extractedPhoneInfo = info
        // 如果需要显示任何电话相关弹窗，标记为延迟返回
        when {
            // 仅有电话号码，直接拨打 - 不需要弹窗
            info.phone != null && info.name == null -> {
                handlePhoneInfo(info)
            }
            // 有姓名和电话号码，检查号码是否已存在 - 需要弹窗
            info.phone != null && info.name != null -> {
                hasPendingPhoneOperation = true
                handlePhoneInfo(info)
            }
            // 仅有姓名，搜索通讯录 - 可能需要弹窗
            info.name != null && info.phone == null -> {
                hasPendingPhoneOperation = true
                handlePhoneInfo(info)
            }
        }
    }

    /**
     * 日历权限请求启动器
     *
     * 当用户授予日历权限后，自动尝试从标题中提取日期并创建日历事件。
     * 如果用户拒绝权限，则不会创建日历事件（笔记仍然正常保存）。
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限已授予，从标题中提取日期并创建日历事件
            pendingCalendarInfo?.let { (cleanTitle, content, dateTime) ->
                val success = CalendarHelper.createCalendarEvent(context, cleanTitle, content, dateTime)
                if (success) {
                    android.widget.Toast.makeText(
                        context,
                        "已自动添加日程到系统日历",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        // 日历操作处理完毕，重置标记
        hasPendingCalendarOperation = false
    }

    /**
     * 安全地创建日历事件（先检查权限，无权限则请求）
     *
     * @param cleanTitle 清理后的标题（不含日期部分）
     * @param content    笔记内容，作为日历事件描述
     * @param dateTime   解析出的日期时间
     */
    fun createCalendarEventSafely(cleanTitle: String, content: String, dateTime: Date) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            // 已有权限，直接创建日历事件
            val success = CalendarHelper.createCalendarEvent(context, cleanTitle, content, dateTime)

            if (success) {
                android.widget.Toast.makeText(
                    context,
                    "已自动添加日程到系统日历",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            // 无权限，发起权限请求（结果由 permissionLauncher 回调处理）
            permissionLauncher.launch(Manifest.permission.WRITE_CALENDAR)
        }
    }

    // 格式化当前时间用于显示（如 "今天 14:30"）
    val dateFormat = SimpleDateFormat("今天 HH:mm", Locale.CHINA)
    val timeStr = dateFormat.format(Date())

    // 保存状态标记，防止重复点击保存按钮
    var isSaving by remember { mutableStateOf(false) }

    // 是否应该延迟返回（有待处理的操作时为 true）
    var shouldDelayReturn by remember { mutableStateOf(false) }

    // 保存时清理标题得到的干净标题
    var savedCleanTitle by remember { mutableStateOf<String?>(null) }
    var savedContent by remember { mutableStateOf<String?>(null) }

    /**
     * 检查是否可以返回主页
     * 只有当没有待处理的电话和日历操作时才返回
     */
    fun checkAndReturn() {
        if (!hasPendingPhoneOperation && !hasPendingCalendarOperation) {
            // 执行保存
            savedCleanTitle?.let { cleanTitle ->
                savedContent?.let { content ->
                    onSave(cleanTitle, content)
                }
            }
            // 返回主页
            onBack()
        }
    }

    // 监听日历操作完成，当 hasPendingCalendarOperation 从 true 变为 false 时检查是否可以返回
    LaunchedEffect(hasPendingCalendarOperation) {
        if (!hasPendingCalendarOperation && shouldDelayReturn) {
            checkAndReturn()
        }
    }

    // ==================== 联系人选择对话框 ====================
    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = {
                showContactDialog = false
                hasPendingPhoneOperation = false
                checkAndReturn()
            },
            title = { Text("选择联系人") },
            text = {
                LazyColumn {
                    items(searchedContacts) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showContactDialog = false
                                    contactToCall = contact
                                    showCallConfirmDialog = true
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = contact.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                contact.phone?.let {
                                    Text(text = it, color = Color.Gray, fontSize = 14.sp)
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "拨打电话",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showContactDialog = false
                    hasPendingPhoneOperation = false
                    checkAndReturn()
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 拨打电话确认对话框 ====================
    if (showCallConfirmDialog && contactToCall != null) {
        AlertDialog(
            onDismissRequest = {
                showCallConfirmDialog = false
                hasPendingPhoneOperation = false
                checkAndReturn()
            },
            title = { Text(contactToCall!!.name) },
            text = {
                Column {
                    contactToCall!!.phone?.let {
                        Text(text = it, fontSize = 18.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCallConfirmDialog = false
                        hasPendingPhoneOperation = false
                        contactToCall?.phone?.let { makePhoneCall(it) }
                        checkAndReturn()
                    }
                ) {
                    Text("拨打")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCallConfirmDialog = false
                    hasPendingPhoneOperation = false
                    checkAndReturn()
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 号码已存在对话框 ====================
    if (showExistingContactDialog && existingContact != null) {
        AlertDialog(
            onDismissRequest = {
                showExistingContactDialog = false
                hasPendingPhoneOperation = false
                checkAndReturn()
            },
            title = { Text("该号码已存在") },
            text = {
                Column {
                    Text(text = "姓名：${existingContact!!.name}")
                    existingContact!!.phone?.let {
                        Text(text = "电话：$it")
                    }
                    Text(text = "是否将其名称更换为：${extractedPhoneInfo?.name}", modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingContactDialog = false
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            val success = PhoneHelper.updateContactName(context, existingContact!!.id, extractedPhoneInfo?.name ?: "")
                            if (success) {
                                android.widget.Toast.makeText(context, "已更新联系人名称", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            writeContactPermissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
                        }
                        hasPendingPhoneOperation = false
                        checkAndReturn()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExistingContactDialog = false
                    hasPendingPhoneOperation = false
                    checkAndReturn()
                }) {
                    Text("取消")
                }
            }
        )
    }

    // ==================== 页面布局 ====================
    Scaffold(
        topBar = {
            TopAppBar(
                // 返回按钮
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!isSaving) onBack()  // 保存中不允许返回
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { },  // 标题栏不显示文字
                actions = {
                    // 保存按钮
                    IconButton(
                        onClick = {
                            if (isSaving) return@IconButton  // 防止重复保存

                            // 只有标题或内容不为空时才保存
                            if (title.isNotBlank() || content.isNotBlank()) {
                                isSaving = true
                                shouldDelayReturn = false

                                // 从标题中提取日期时间（如果有的话）
                                val (cleanTitle, dateTime) = CalendarHelper.extractDateTimeFromTitle(title)

                                // 从标题中提取电话信息（如果有的话）
                                val phoneInfo = PhoneHelper.extractPhoneInfoFromTitle(title)

                                // 使用清理后的标题保存（去除日期和电话相关信息）
                                val finalTitle = phoneInfo.cleanTitle ?: cleanTitle

                                // 保存清理后的标题和内容
                                savedCleanTitle = finalTitle
                                savedContent = content

                                // 如果标题中包含日期时间，设置日历操作标记
                                if (dateTime != null) {
                                    hasPendingCalendarOperation = true
                                    pendingCalendarInfo = Triple(cleanTitle, content, dateTime)
                                }

                                // 如果有待处理的电话或日历操作，不立即返回
                                if (phoneInfo.name != null || phoneInfo.phone != null || dateTime != null) {
                                    shouldDelayReturn = true
                                }

                                // 如果有待处理的操作，先处理操作，稍后再返回
                                if (shouldDelayReturn) {
                                    // 如果标题中包含电话相关信息，处理电话操作
                                    if (phoneInfo.name != null || phoneInfo.phone != null) {
                                        handlePhoneInfoSafely(phoneInfo)
                                    }
                                    // 如果标题中包含日期时间，自动创建日历事件
                                    if (dateTime != null) {
                                        createCalendarEventSafely(cleanTitle, content, dateTime)
                                    }
                                } else {
                                    // 没有待处理操作，直接保存并返回
                                    onSave(finalTitle, content)
                                    onBack()
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { paddingValues ->
        // 内容区域：标题输入 + 时间显示 + 内容输入
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 标题输入框：大字号、加粗、无边框
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = LocalTextStyle.current.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                placeholder = { Text("标题", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            // 当前时间显示
            Text(
                text = timeStr,
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 内容输入框：占满剩余空间、无边框
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = { Text("开始记录...", color = Color.LightGray) },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),  // weight(1f) 使输入框占满剩余垂直空间
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )
        }
    }
}
