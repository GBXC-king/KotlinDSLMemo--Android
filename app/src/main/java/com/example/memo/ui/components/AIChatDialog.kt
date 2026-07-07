package com.example.memo.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.memo.agent.AgentActionParser
import com.example.memo.agent.AgentConfig
import com.example.memo.agent.AgentConfirmDialog
import com.example.memo.agent.AgentEngine
import com.example.memo.agent.AgentPlan
import com.example.memo.agent.AgentStepRecord
import com.example.memo.agent.AgentThinkingPanel
import com.example.memo.agent.AgentToolRegistry
import com.example.memo.agent.PendingAppSelection
import com.example.memo.autoui.engine.UiAutoController
import com.example.memo.data.*
import com.example.memo.data.AppHelper
import com.example.memo.repository.RepositoryProvider
import com.example.memo.ui.movie.MovieDialogMode
import com.example.memo.ui.movie.MovieRecommendDialog
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.NoteViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** 附件上限（与产品要求一致） */
private const val MAX_ATTACHMENT_COUNT = 10
/** 图片大小上限：5 MB */
private const val MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024
/** 文档大小上限：20 MB */
private const val MAX_DOCUMENT_SIZE_BYTES = 20L * 1024 * 1024

/** AI 聊天-文件选择器支持的常见文档 MIME 类型 */
private val DOCUMENT_MIME_TYPES = arrayOf(
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "text/plain"
)

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun AIChatDialog(
    onDismiss: () -> Unit,
    onAddNote: (Note) -> Unit,
    onOpenBrowser: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val ledgerViewModel: LedgerViewModel = viewModel()
    val noteViewModel: NoteViewModel = viewModel()
    val memoryViewModel: MemoryViewModel = viewModel()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showApiKeyInput by remember { mutableStateOf(AppConfig.apiKey.isBlank()) }
    var apiKeyInput by remember { mutableStateOf(AppConfig.apiKey) }

    // 默认影视站配置（用于《片名》点击跳转内置浏览器）
    var defaultVideoSite by remember { mutableStateOf<AppConfigLoader.VideoSite?>(null) }

    // 影视推荐弹窗状态
    var showMovieDialog by remember { mutableStateOf(false) }
    var movieDialogMode by remember { mutableStateOf(MovieDialogMode.AI_RECOMMEND) }
    var movieDialogTitles by remember { mutableStateOf<List<String>>(emptyList()) }
    var movieDialogType by remember { mutableStateOf(WatchListManager.WatchType.ALL) }
    // 最近已看弹窗状态
    var showRecentWatchedDialog by remember { mutableStateOf(false) }
    var recentWatchedList by remember { mutableStateOf<List<String>>(emptyList()) }

    var showContactDialog by remember { mutableStateOf(false) }
    var showCallConfirmDialog by remember { mutableStateOf(false) }
    var showExistingContactDialog by remember { mutableStateOf(false) }
    var showDeleteContactDialog by remember { mutableStateOf(false) }
    var searchedContacts by remember { mutableStateOf(listOf<ContactInfo>()) }
    var contactToCall by remember { mutableStateOf<ContactInfo?>(null) }
    var existingContact by remember { mutableStateOf<ContactInfo?>(null) }
    var pendingPhoneInfo by remember { mutableStateOf<PhoneInfo?>(null) }
    var contactToDelete by remember { mutableStateOf<ContactInfo?>(null) }

    var pendingDeleteLedgerName by remember { mutableStateOf<String?>(null) }
    var showDeleteLedgerConfirmDialog by remember { mutableStateOf(false) }

    var pendingAppSelection by remember { mutableStateOf(listOf<String>()) }
    var pendingAppDeepLinks by remember { mutableStateOf(listOf<String?>()) }
    var pendingAppSelectionTitle by remember { mutableStateOf("") }
    var showAppSelectionDialog by remember { mutableStateOf(false) }

    var agentPendingAppSelection by remember { mutableStateOf<PendingAppSelection?>(null) }
    var agentAppSelectionPackageNames by remember { mutableStateOf(listOf<String>()) }
    var showAgentAppSelectionDialog by remember { mutableStateOf(false) }

    var isAgentMode by remember { mutableStateOf(false) }
    var pendingAgentPlan by remember { mutableStateOf<AgentPlan?>(null) }
    var pendingAgentActions by remember { mutableStateOf<List<AIAction>?>(null) }
    var showAgentConfirmDialog by remember { mutableStateOf(false) }

    var agentStepRecords by remember { mutableStateOf(listOf<AgentStepRecord>()) }
    var isAgentRunning by remember { mutableStateOf(false) }

    // 新版 AI 助手界面状态：加号面板展开、已选附件
    // 注意：仅在 GLM 供应商下才显示 + 按钮和附件功能
    var isPlusPanelExpanded by remember { mutableStateOf(false) }
    var attachments by remember { mutableStateOf(listOf<Attachment>()) }
    // 缓存的"当前激活供应商"，用于判断是否显示加号按钮和决定发送时使用哪个 Helper
    var currentProvider by remember { mutableStateOf(AppConfig.provider) }
    // 拍照时目标文件 + content URI（由 FileProvider 生成；保存用，发送时可删除）
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    // 弹窗提示：附件超限/重复/过大
    var attachmentWarning by remember { mutableStateOf<String?>(null) }
    // 标记"会话退出/进程关闭"清理钩子
    val clearAttachmentsOnExit = remember<() -> Unit> { { attachments = emptyList() } }
    DisposableEffect(Unit) {
        onDispose { clearAttachmentsOnExit() }
    }

    fun addSystemMessage(text: String) {
        val msg = ChatMessage(content = text, role = MessageRole.ASSISTANT)
        messages = messages + msg
    }

    // -----------------------------------------------------------------
    // 附件辅助函数（严格按依赖顺序定义；Kotlin 局部函数不会前向提升）
    //   顺序：addSystemMessage → handleIngestResult → ingestAttachments
    //         → takePictureLauncher → launchCameraInternal
    //         → cameraPermissionLauncher → takePhoto
    //         → photoPickerLauncher / filePickerLauncher
    // -----------------------------------------------------------------

    /**
     * 处理解析后的附件：去重 + 数量限制
     */
    fun handleIngestResult(newOnes: List<Attachment>) {
        if (newOnes.isEmpty()) return
        val existing = attachments
        val merged = mutableListOf<Attachment>()
        var dupHit: String? = null
        for (att in newOnes) {
            val isDup = existing.any { it.displayName == att.displayName && it.sizeBytes == att.sizeBytes }
                || merged.any { it.displayName == att.displayName && it.sizeBytes == att.sizeBytes }
            if (isDup) {
                dupHit = att.displayName
                continue
            }
            merged.add(att)
        }
        if (dupHit != null) {
            attachmentWarning = "文件「$dupHit」已添加"
        }
        val remainSlots = MAX_ATTACHMENT_COUNT - existing.size
        if (remainSlots <= 0) {
            attachmentWarning = "单次对话最多支持 $MAX_ATTACHMENT_COUNT 个附件"
            return
        }
        val toAdd = if (merged.size > remainSlots) merged.take(remainSlots) else merged
        if (toAdd.size < merged.size) {
            attachmentWarning = "已达附件上限 $MAX_ATTACHMENT_COUNT 个"
        }
        attachments = existing + toAdd
    }

    /**
     * 把 Uris 解析为 Attachment 列表，并执行去重 / 数量 / 大小校验
     */
    suspend fun ingestAttachments(uris: List<Uri>, source: String) {
        val newOnes = mutableListOf<Attachment>()
        for (uri in uris) {
            val (name, size) = queryFileNameAndSize(context, uri)
            val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(name)
            val type = if (mime.startsWith("image/")) AttachmentType.IMAGE else AttachmentType.DOCUMENT
            val attName = name ?: "${source}_${System.currentTimeMillis()}"
            val attSize = size ?: 0L
            // 大小校验
            val maxSize = if (type == AttachmentType.IMAGE) MAX_IMAGE_SIZE_BYTES else MAX_DOCUMENT_SIZE_BYTES
            if (attSize > maxSize) {
                attachmentWarning = if (type == AttachmentType.IMAGE) {
                    "图片「$attName」超过 5MB，无法添加"
                } else {
                    "文档「$attName」超过 20MB，无法添加"
                }
                continue
            }
            // 图片生成缩略图（80x80 等比裁剪）
            val thumb = if (type == AttachmentType.IMAGE) loadImageThumbnail(context, uri) else null
            newOnes.add(
                Attachment(
                    uri = uri,
                    displayName = attName,
                    mimeType = mime,
                    sizeBytes = attSize,
                    type = type,
                    thumbnail = thumb
                )
            )
        }
        handleIngestResult(newOnes)
    }

    /**
     * 发送前把图片/文档读取为 base64 字符串
     * 仅在真正调用 GLM 接口时执行，避免不必要的内存占用
     */
    suspend fun encodeAttachmentsForUpload(list: List<Attachment>): List<Attachment> {
        return withContext(Dispatchers.IO) {
            list.map { att ->
                if (!att.base64Data.isNullOrEmpty()) return@map att
                try {
                    val bytes = context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                    if (bytes == null) att
                    else att.copy(base64Data = GlmHelper.encodeToBase64(bytes))
                } catch (e: Exception) {
                    android.util.Log.e("AIChatDialog", "附件编码失败: ${att.displayName}", e)
                    att
                }
            }
        }
    }

    /**
     * 清理拍照临时文件（仅删除我们自己 FileProvider 生成的本地路径）
     */
    fun cleanupCameraTempFiles(list: List<Attachment>) {
        for (att in list) {
            att.localFilePath?.let { path ->
                try {
                    val f = File(path)
                    if (f.exists() && f.absolutePath.contains(context.cacheDir.absolutePath)) {
                        f.delete()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    // 拍照：需要预先创建文件 + FileProvider content URI
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val file = pendingCameraFile
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val (name, size) = queryFileNameAndSize(context, uri)
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val att = Attachment(
                    uri = uri,
                    displayName = name ?: file?.name ?: "photo_${System.currentTimeMillis()}.jpg",
                    mimeType = mime,
                    sizeBytes = size ?: (file?.length() ?: 0L),
                    type = AttachmentType.IMAGE,
                    thumbnail = null,
                    base64Data = null,
                    localFilePath = file?.absolutePath
                )
                handleIngestResult(listOf(att))
            }
        } else {
            // 用户取消拍照，删除空文件
            pendingCameraFile?.takeIf { it.exists() && it.length() == 0L }?.delete()
        }
        pendingCameraFile = null
        pendingCameraUri = null
    }

    /**
     * 启动相机（FileProvider 形式）
     * 仅在已授予相机权限时调用
     */
    fun launchCameraInternal(file: File?, uri: Uri?) {
        if (file == null || uri == null) {
            addSystemMessage("无法启动相机：内部错误")
            return
        }
        takePictureLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限授予后重新触发拍照
            launchCameraInternal(pendingCameraFile, pendingCameraUri)
        } else {
            addSystemMessage("需要相机权限才能拍照，请授予权限后重试")
        }
    }

    /**
     * 拍照入口：检查权限 + 创建临时文件 + 启动相机
     */
    fun takePhoto() {
        if (currentProvider != ModelProvider.GLM && currentProvider != ModelProvider.ALIYUN) {
            addSystemMessage("当前模型不支持图片识别，请先在设置中切换到 GLM 智谱 AI 或阿里云通义千问")
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            // 预创建文件，等权限回来后由 launcher 回调使用
            val (file, uri) = createCameraOutputFile(context)
            pendingCameraFile = file
            pendingCameraUri = uri
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val (file, uri) = createCameraOutputFile(context)
        pendingCameraFile = file
        pendingCameraUri = uri
        launchCameraInternal(file, uri)
    }

    // 相册多选：仅图片
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                ingestAttachments(uris, source = "相册")
            }
        }
    }

    // 文件选择器：支持常见文档格式（pdf/doc/docx/xlsx/pptx/txt）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                ingestAttachments(listOf(uri), source = "文件")
            }
        }
    }

    var pendingEventAction: AIAction? by remember { mutableStateOf(null) }
    var calendarPermissionResult: Boolean? by remember { mutableStateOf(null) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        calendarPermissionResult = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        FlashlightHelper.init(context)
        isAgentMode = AgentConfig.getAgentMode(context)
        AppListCache.updateCacheIfNeeded(context)
        currentProvider = AppConfig.provider
        // 预加载默认影视站配置（用于《片名》点击跳转）
        defaultVideoSite = withContext(Dispatchers.IO) {
            val config = AppConfigLoader.getConfig(context)
            config.videoSites.firstOrNull { it.isDefault } ?: config.videoSites.firstOrNull()
        }
    }

    /**
     * 根据片名构建内置浏览器搜索 URL
     * 使用默认站点的 searchUrlTemplate，把 {keyword} 替换为 URL 编码后的片名
     */
    fun buildVideoSearchUrl(keyword: String): String? {
        val site = defaultVideoSite ?: return null
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
        return site.searchUrlTemplate.replace("{keyword}", encoded)
    }

    /**
     * 处理影视卡片点击：跳内置浏览器搜索 + 写入已看 + 其余写入待看
     * @param title 用户点击的影视名
     * @param allTitles 弹窗中的全部名字列表（AI推荐模式有15个，待看模式为待看列表）
     * @param mode 弹窗模式
     * @param type 影视类型
     */
    fun handleMoviePlay(
        title: String,
        allTitles: List<String>,
        mode: MovieDialogMode,
        type: WatchListManager.WatchType
    ) {
        // 跳内置浏览器
        buildVideoSearchUrl(title)?.let { url ->
            onOpenBrowser(url)
        } ?: addSystemMessage("影视站配置未加载完成，请稍后再试")

        // 写入已看（去重）
        WatchListManager.addToWatched(context, title, type)

        when (mode) {
            MovieDialogMode.AI_RECOMMEND -> {
                // 其余14个写入待看（去重）
                val rest = allTitles.filter { it != title }
                WatchListManager.addAllToPending(context, rest, type)
            }
            MovieDialogMode.PENDING_LIST -> {
                // 从待看移除（addToWatched 已自动移除，这里无需重复）
            }
        }
        showMovieDialog = false
    }

    /**
     * 展示待看列表弹窗
     */
    fun showPendingListDialog(type: WatchListManager.WatchType) {
        val list = WatchListManager.getPendingList(context, type)
        if (list.isEmpty()) {
            addSystemMessage("您的待看列表是空的")
            return
        }
        movieDialogMode = MovieDialogMode.PENDING_LIST
        movieDialogTitles = list
        movieDialogType = type
        showMovieDialog = true
    }

    /**
     * 展示 AI 推荐弹窗
     */
    fun showRecommendationsDialog(titles: List<String>, type: WatchListManager.WatchType) {
        if (titles.isEmpty()) {
            addSystemMessage("推荐列表为空")
            return
        }
        movieDialogMode = MovieDialogMode.AI_RECOMMEND
        movieDialogTitles = titles
        movieDialogType = type
        showMovieDialog = true
    }

    /**
     * 展示最近已看弹窗
     */
    fun showRecentWatchedDialog() {
        val list = WatchListManager.getRecentWatched(context, 3, WatchListManager.WatchType.ALL)
        recentWatchedList = list
        showRecentWatchedDialog = true
    }

    /**
     * 处理"需要推荐"二次请求（非 agent 模式）
     * 当待看数量 < 15 时，发送"需要推荐:类型"给 AI，AI 返回推荐结果后弹窗
     */
    suspend fun requestRecommendations(type: WatchListManager.WatchType, typeStr: String) {
        val recommendPrompt = "需要推荐:$typeStr"
        val userMsg = ChatMessage(content = recommendPrompt, role = MessageRole.USER)
        messages = messages + userMsg

        DeepSeekHelper.sendChatMessageStreaming(
            messages = messages,
            onChunk = { chunk ->
                scope.launch {
                    val aiMsg = ChatMessage(content = chunk, role = MessageRole.ASSISTANT)
                    messages = messages + aiMsg
                }
            },
            onComplete = { response ->
                scope.launch {
                    val (displayText, actions) = DeepSeekHelper.extractAction(response)
                    // 显示 AI 回复
                    if (displayText.isNotBlank()) {
                        val aiMsg = ChatMessage(content = displayText, role = MessageRole.ASSISTANT)
                        messages = messages.dropLast(1) + aiMsg
                    }
                    // 处理推荐结果
                    val recommendAction = actions.firstOrNull { it.type == ActionType.SHOW_RECOMMENDATIONS }
                    if (recommendAction != null) {
                        showRecommendationsDialog(recommendAction.movieTitles, type)
                    }
                    isLoading = false
                }
            },
            onError = { error ->
                scope.launch {
                    addSystemMessage("推荐失败：$error")
                    isLoading = false
                }
            }
        )
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun makePhoneCall(phoneNumber: String) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    fun handlePhoneCallByName(name: String) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            val contacts = PhoneHelper.searchContactsByName(context, name)
            if (contacts.size == 1 && contacts[0].name == name) {
                contactToCall = contacts[0]
                showCallConfirmDialog = true
            } else if (contacts.isNotEmpty()) {
                searchedContacts = contacts
                showContactDialog = true
            } else {
                addSystemMessage("通讯录中没有找到包含\"$name\"的联系人")
            }
        } else {
            addSystemMessage("需要读取通讯录权限才能搜索联系人")
        }
    }

    fun handlePhoneCallByNumber(phone: String) {
        makePhoneCall(phone)
    }

    fun handleCreateContact(name: String, phone: String) {
        val existing = PhoneHelper.searchContactByPhone(context, phone)
        if (existing != null) {
            existingContact = existing
            pendingPhoneInfo = PhoneInfo(name = name, phone = phone, cleanTitle = "")
            showExistingContactDialog = true
        } else {
            PhoneHelper.createContact(context, name, phone)
            addSystemMessage("已创建联系人：$name ($phone)")
        }
    }

    fun handleDeleteContact(name: String) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            addSystemMessage("需要写入通讯录权限才能删除联系人")
            return
        }
        val contacts = PhoneHelper.searchContactsByName(context, name)
        if (contacts.isEmpty()) {
            addSystemMessage("通讯录中没有找到包含\"$name\"的联系人")
        } else if (contacts.size == 1 && contacts[0].name == name) {
            contactToDelete = contacts[0]
            showDeleteContactDialog = true
        } else {
            searchedContacts = contacts
            showContactDialog = true
            addSystemMessage("找到多个匹配的联系人，请选择要删除的")
        }
    }

    fun handleSearchContact(name: String) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            addSystemMessage("需要读取通讯录权限才能查询联系人")
            return
        }
        val contacts = PhoneHelper.searchContactsByName(context, name)
        if (contacts.isEmpty()) {
            addSystemMessage("通讯录中没有找到与\"$name\"相关的联系人")
        } else {
            val exactMatch = contacts.find { it.name == name }
            if (exactMatch != null && contacts.size == 1) {
                val phoneStr = exactMatch.phone?.let { "，电话：$it" } ?: "，暂无电话号码"
                addSystemMessage("找到联系人：${exactMatch.name}$phoneStr")
            } else {
                val exactMatches = contacts.filter { it.name == name }
                val similarContacts = contacts.filter { it.name != name }
                val sb = StringBuilder()
                if (exactMatches.isNotEmpty()) {
                    sb.append("找到精确匹配的联系人：\n")
                    exactMatches.forEach { c ->
                        sb.append("• ${c.name}")
                        c.phone?.let { sb.append("（$it）") }
                        sb.append("\n")
                    }
                }
                if (similarContacts.isNotEmpty()) {
                    sb.append("\n为您找到相似联系人：\n")
                    similarContacts.take(10).forEach { c ->
                        sb.append("• ${c.name}")
                        c.phone?.let { sb.append("（$it）") }
                        sb.append("\n")
                    }
                    if (similarContacts.size > 10) {
                        sb.append("...还有${similarContacts.size - 10}个相似联系人")
                    }
                }
                addSystemMessage(sb.toString().trim())
            }
        }
    }

    fun handleSearchPhoneNumber(phone: String) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            addSystemMessage("需要读取通讯录权限才能查询号码")
            return
        }
        val contact = PhoneHelper.searchContactByPhone(context, phone)
        if (contact != null) {
            addSystemMessage("号码 $phone 对应的联系人是：${contact.name}")
        } else {
            addSystemMessage("通讯录中没有找到号码为 $phone 的联系人")
        }
    }

    suspend fun createLinkedAlarm(title: String, timestamp: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val alarm = Alarm(
                    hour = calendar.get(Calendar.HOUR_OF_DAY),
                    minute = calendar.get(Calendar.MINUTE),
                    title = title,
                    repeatDays = Alarm.NEVER_MASK,
                    ringtoneType = Alarm.RINGTONE_SYSTEM,
                    vibrate = true,
                    deleteAfterDismiss = false,
                    snoozeEnabled = false,
                    snoozeInterval = 10,
                    snoozeCount = 5,
                    isEnabled = true
                )
                val id = RepositoryProvider.getAlarmRepository().insertAlarm(alarm)
                AlarmHelper.setAlarm(context, alarm.copy(id = id))
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun executeCreateEvent(action: AIAction) {
        val title = action.title ?: "日程提醒"
        val content = action.content ?: ""
        val timestamp = action.dateTime
        if (timestamp != null) {
            val success = CalendarHelper.createCalendarEvent(context, title, content, Date(timestamp))
            if (success) {
                // 联动创建应用内一次性闹钟
                val alarmCreated = createLinkedAlarm(title, timestamp)
                if (alarmCreated) {
                    addSystemMessage("已创建日程和闹钟：$title")
                } else {
                    addSystemMessage("已创建日程：$title（闹钟联动失败）")
                }
            } else {
                addSystemMessage("创建日程失败，请检查日历权限或系统设置")
            }
        } else {
            addSystemMessage("创建日程失败：时间信息不完整")
        }
    }

    suspend fun executeCreateAlarm(action: AIAction) {
        val title = action.title ?: "闹钟"
        val hour = action.alarmHour ?: return
        val minute = action.alarmMinute ?: 0
        val repeatDays = action.alarmRepeatDays ?: 0

        withContext(Dispatchers.IO) {
            try {
                val alarm = Alarm(
                    hour = hour,
                    minute = minute,
                    title = title,
                    repeatDays = repeatDays,
                    ringtoneType = Alarm.RINGTONE_SYSTEM,
                    vibrate = true,
                    deleteAfterDismiss = repeatDays == Alarm.NEVER_MASK,
                    snoozeEnabled = true,
                    snoozeInterval = 10,
                    snoozeCount = 5,
                    isEnabled = true
                )
                val id = RepositoryProvider.getAlarmRepository().insertAlarm(alarm)
                AlarmHelper.setAlarm(context, alarm.copy(id = id))
                val timeStr = String.format("%02d:%02d", hour, minute)
                val repeatText = alarm.repeatText()
                addSystemMessage("已创建闹钟：$title（$timeStr，重复：$repeatText）")
            } catch (e: Exception) {
                addSystemMessage("创建闹钟失败：${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun handleCreateLedger(name: String, unit: String) {
        val ledger = Ledger(
            title = name,
            unit = unit,
            color = (Math.random() * 10).toInt()
        )
        ledgerViewModel.addLedger(ledger)
        addSystemMessage("已创建账本：$name（单位：$unit）")
    }

    suspend fun handleDeleteLedger(name: String) {
        val ledgers = withContext(Dispatchers.IO) {
            ledgerViewModel.allLedgers.first()
        }
        val matchedLedgers = ledgers.filter { it.title.contains(name) }
        if (matchedLedgers.isEmpty()) {
            addSystemMessage("没有找到名为\"$name\"的账本")
        } else if (matchedLedgers.size == 1 && matchedLedgers[0].title == name) {
            pendingDeleteLedgerName = matchedLedgers[0].title
            showDeleteLedgerConfirmDialog = true
        } else {
            val ledgerNames = matchedLedgers.joinToString("、") { it.title }
            addSystemMessage("找到多个匹配的账本：$ledgerNames，请说清楚要删除哪个")
        }
    }

    suspend fun handleCreateTransaction(ledgerName: String, type: Int, amount: Double, note: String, date: Long? = null) {
        withContext(Dispatchers.IO) {
            var matchedLedger = ledgerViewModel.findLedgerByTitle(ledgerName)

            if (matchedLedger == null) {
                val newLedger = Ledger(title = ledgerName, unit = "元", color = (Math.random() * 10).toInt())
                val newId = ledgerViewModel.addLedgerSync(newLedger)
                matchedLedger = newLedger.copy(id = newId)
                addSystemMessage("已自动创建账本：$ledgerName")
            }

            val transaction = Transaction(
                ledgerId = matchedLedger.id,
                type = type,
                amount = amount,
                note = note,
                timestamp = date ?: System.currentTimeMillis()
            )
            ledgerViewModel.addTransaction(transaction)

            val dateFormat = java.text.SimpleDateFormat("MM月dd日", Locale.CHINA)
            val dateStr = dateFormat.format(java.util.Date(date ?: System.currentTimeMillis()))
            val typeStr = if (type == Transaction.TYPE_INCOME) "收入" else "支出"
            addSystemMessage("已在${matchedLedger.title}账本中记录${dateStr} ${typeStr}：$amount${matchedLedger.unit}${if (note.isNotEmpty()) "（备注：$note）" else ""}")
        }
    }

    suspend fun analyzeLedgerData(ledger: Ledger) {
        val transactions = ledgerViewModel.getTransactionsByLedgerId(ledger.id).first()

        val currentMonthStart = ledgerViewModel.getCurrentMonthStart()
        val currentMonthEnd = ledgerViewModel.getCurrentMonthEnd()
        val currentMonthTransactions = ledgerViewModel.filterTransactionsByTimeRange(transactions, currentMonthStart, currentMonthEnd)
        val currentMonthIncome = ledgerViewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_INCOME)
        val currentMonthExpense = ledgerViewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_EXPENSE)

        val dateFormat = java.text.SimpleDateFormat("MM-dd", Locale.CHINA)
        val transactionStr = transactions.take(50).joinToString("\n") { t ->
            val typeStr = if (t.type == Transaction.TYPE_INCOME) "收入" else "支出"
            val dateStr = dateFormat.format(java.util.Date(t.timestamp))
            "- $dateStr $typeStr ${t.amount}${ledger.unit}${if (t.note.isNotEmpty()) "（${t.note}）" else ""}"
        }

        val queryPrompt = """
请分析以下账本数据，给用户总结和建议：

账本名称：${ledger.title}
单位：${ledger.unit}
总记录数：${transactions.size} 条

本月统计：
- 本月收入：$currentMonthIncome${ledger.unit}
- 本月支出：$currentMonthExpense${ledger.unit}
- 本月结余：${currentMonthIncome - currentMonthExpense}${ledger.unit}

最近记录（最多50条）：
$transactionStr

请用中文给出：
1. 简要总结
2. 收支分析
3. 合理建议
        """.trimIndent()

        val queryMessage = ChatMessage(content = queryPrompt, role = MessageRole.USER)

        withContext(Dispatchers.IO) {
            DeepSeekHelper.sendChatMessage(
                messages = listOf(queryMessage),
                onResponse = { response ->
                    scope.launch {
                        val aiMessage = ChatMessage(content = response, role = MessageRole.ASSISTANT)
                        messages = messages + aiMessage
                    }
                },
                onError = { error ->
                    scope.launch {
                        addSystemMessage("分析失败：$error")
                    }
                }
            )
        }
    }

    fun handleMatchLedger(keyword: String) {
        scope.launch {
            isLoading = true
            val ledgers = ledgerViewModel.allLedgers.first()
            val ledgerNames = ledgers.map { it.title }

            if (ledgerNames.isEmpty()) {
                val newLedger = Ledger(title = keyword, unit = "元", color = (Math.random() * 10).toInt())
                ledgerViewModel.addLedger(newLedger)
                addSystemMessage("没有找到账本，已自动创建\"$keyword\"账本")
                isLoading = false
                return@launch
            }

            val matchPrompt = """
以下是所有账本名称列表：
${ledgerNames.joinToString("、") { "「$it」" }}

用户提到的关键词是："$keyword"

请判断：
1. 哪个账本与关键词最匹配、最相关？
2. 如果没有任何相关的账本，请回答"新建账本"

请只回复账本名称（和列表中完全一致），或者"新建账本"三个字，不要回复其他内容。
            """.trimIndent()

            val matchMessage = ChatMessage(content = matchPrompt, role = MessageRole.USER)

            withContext(Dispatchers.IO) {
                DeepSeekHelper.sendChatMessage(
                    messages = listOf(matchMessage),
                    onResponse = { response ->
                        scope.launch {
                            val result = response.trim()
                            val matchedLedger = ledgers.find { it.title == result }

                            if (matchedLedger != null) {
                                analyzeLedgerData(matchedLedger)
                            } else {
                                val newLedger = Ledger(title = keyword, unit = "元", color = (Math.random() * 10).toInt())
                                val newId = ledgerViewModel.addLedgerSync(newLedger)
                                val createdLedger = newLedger.copy(id = newId)
                                addSystemMessage("没有找到相关账本，已自动创建\"$keyword\"账本")
                                analyzeLedgerData(createdLedger)
                            }
                            isLoading = false
                        }
                    },
                    onError = { error ->
                        scope.launch {
                            addSystemMessage("匹配账本失败：$error")
                            isLoading = false
                        }
                    }
                )
            }
        }
    }

    fun handleQueryLedger(ledgerName: String) {
        scope.launch {
            isLoading = true
            val ledgers = ledgerViewModel.allLedgers.first()
            val matchedLedger = ledgers.find { it.title == ledgerName }
                ?: ledgers.firstOrNull { it.title.contains(ledgerName) }

            if (matchedLedger == null) {
                addSystemMessage("没有找到名为\"$ledgerName\"的账本")
                isLoading = false
                return@launch
            }

            val transactions = ledgerViewModel.getTransactionsByLedgerId(matchedLedger.id).first()

            val currentMonthStart = ledgerViewModel.getCurrentMonthStart()
            val currentMonthEnd = ledgerViewModel.getCurrentMonthEnd()
            val currentMonthTransactions = ledgerViewModel.filterTransactionsByTimeRange(transactions, currentMonthStart, currentMonthEnd)
            val currentMonthIncome = ledgerViewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_INCOME)
            val currentMonthExpense = ledgerViewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_EXPENSE)

            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
            val transactionStr = transactions.take(50).joinToString("\n") { t ->
                val typeStr = if (t.type == Transaction.TYPE_INCOME) "收入" else "支出"
                val dateStr = dateFormat.format(Date(t.timestamp))
                "- $dateStr $typeStr ${t.amount}${matchedLedger.unit}${if (t.note.isNotEmpty()) "（${t.note}）" else ""}"
            }

            val queryPrompt = """
请分析以下账本数据，给出总结和建议：

账本名称：${matchedLedger.title}
单位：${matchedLedger.unit}
总记录数：${transactions.size} 条

本月统计：
- 本月收入：$currentMonthIncome${matchedLedger.unit}
- 本月支出：$currentMonthExpense${matchedLedger.unit}
- 本月结余：${currentMonthIncome - currentMonthExpense}${matchedLedger.unit}

最近记录（最多50条）：
$transactionStr

请用中文给出：
1. 简要总结
2. 收支分析
3. 合理建议
            """.trimIndent()

            val queryMessage = ChatMessage(content = queryPrompt, role = MessageRole.USER)

            withContext(Dispatchers.IO) {
                DeepSeekHelper.sendChatMessage(
                    messages = listOf(queryMessage),
                    onResponse = { response ->
                        scope.launch {
                            val aiMessage = ChatMessage(content = response, role = MessageRole.ASSISTANT)
                            messages = messages + aiMessage
                            isLoading = false
                        }
                    },
                    onError = { error ->
                        scope.launch {
                            val errorMessage = ChatMessage(
                                content = "查询分析失败：$error",
                                role = MessageRole.ASSISTANT
                            )
                            messages = messages + errorMessage
                            isLoading = false
                        }
                    }
                )
            }
        }
    }

    suspend fun handleOpenApp(appName: String) {
        val apps = AppListCache.getCachedApps(context)
        val matchedApps = AppHelper.searchInstalledAppsWithList(apps, appName)

        when {
            matchedApps.isEmpty() -> {
                addSystemMessage("未找到应用：$appName，请确认应用名称是否正确（本机共检测到${apps.size}个应用）")
            }
            else -> {
                pendingAppSelection = matchedApps.map { it.packageName }
                pendingAppDeepLinks = emptyList()
                pendingAppSelectionTitle = ""
                showAppSelectionDialog = true
            }
        }
    }

    suspend fun executeAction(action: AIAction) {
        // 记录工具调用
        val actionParams = mutableMapOf<String, String>()
        action.contactName?.let { actionParams["联系人"] = it }
        action.phoneNumber?.let { actionParams["电话"] = it }
        action.title?.let { actionParams["标题"] = it }
        action.content?.let { actionParams["内容"] = it }
        action.ledgerName?.let { actionParams["账本"] = it }
        action.transactionType?.let { actionParams["类型"] = it.toString() }
        action.transactionAmount?.let { actionParams["金额"] = it.toString() }
        action.transactionNote?.let { actionParams["备注"] = it }
        action.transactionDate?.let { actionParams["日期"] = it.toString() }
        action.matchKeyword?.let { actionParams["关键词"] = it }
        com.example.memo.util.OperationLogger.logToolCall(action.type.name, actionParams, null)

        when (action.type) {
            ActionType.CREATE_NOTE -> {
                val title = action.title ?: "AI 记录"
                val content = action.content ?: ""
                val note = Note(title = title, content = content)
                onAddNote(note)
            }
            ActionType.CREATE_CONTACT -> {
                val name = action.contactName
                val phone = action.phoneNumber
                if (name != null && phone != null) {
                    handleCreateContact(name, phone)
                }
            }
            ActionType.DELETE_CONTACT -> {
                val name = action.contactName
                if (name != null) {
                    handleDeleteContact(name)
                }
            }
            ActionType.SEARCH_CONTACT -> {
                val name = action.contactName
                if (name != null) {
                    handleSearchContact(name)
                }
            }
            ActionType.SEARCH_PHONE_NUMBER -> {
                val phone = action.phoneNumber
                if (phone != null) {
                    handleSearchPhoneNumber(phone)
                }
            }
            ActionType.CREATE_EVENT -> {
                val hasReadPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
                val hasWritePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

                if (hasReadPermission && hasWritePermission) {
                    executeCreateEvent(action)
                } else {
                    pendingEventAction = action
                    calendarPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                        )
                    )
                }
            }
            ActionType.CREATE_ALARM -> {
                executeCreateAlarm(action)
            }
            ActionType.CALL_PHONE -> {
                val phone = action.phoneNumber
                if (phone != null) {
                    handlePhoneCallByNumber(phone)
                }
            }
            ActionType.SEARCH_CONTACT_FOR_CALL -> {
                val name = action.contactName
                if (name != null) {
                    handlePhoneCallByName(name)
                }
            }
            ActionType.FLASHLIGHT_ON -> {
                val success = FlashlightHelper.turnOn(context)
                addSystemMessage(if (success) "手电筒已打开" else "手电筒打开失败，请重试")
            }
            ActionType.FLASHLIGHT_OFF -> {
                val success = FlashlightHelper.turnOff(context)
                addSystemMessage(if (success) "手电筒已关闭" else "手电筒关闭失败，请重试")
            }
            ActionType.CREATE_LEDGER -> {
                val name = action.ledgerName
                val unit = action.ledgerUnit ?: "元"
                if (name != null) {
                    handleCreateLedger(name, unit)
                }
            }
            ActionType.DELETE_LEDGER -> {
                val name = action.ledgerName
                if (name != null) {
                    handleDeleteLedger(name)
                }
            }
            ActionType.CREATE_TRANSACTION -> {
                val name = action.ledgerName
                val type = action.transactionType
                val amount = action.transactionAmount
                val note = action.transactionNote ?: ""
                val date = action.transactionDate
                if (name != null && type != null && amount != null && amount > 0) {
                    handleCreateTransaction(name, type, amount, note, date)
                }
            }
            ActionType.QUERY_LEDGER -> {
                val name = action.ledgerName
                if (name != null) {
                    handleQueryLedger(name)
                }
            }
            ActionType.MATCH_LEDGER -> {
                val keyword = action.matchKeyword
                if (keyword != null) {
                    handleMatchLedger(keyword)
                }
            }
            ActionType.OPEN_APP -> {
                val appName = action.appName
                if (appName != null) {
                    handleOpenApp(appName)
                }
            }
            ActionType.RECOMMEND_APP -> {
            }
            ActionType.WATCH_VIDEO -> {
                // 旧的跳外部 App 路径已删除，保留空分支以兼容旧 ActionType
            }
            ActionType.PLAY_IN_BROWSER -> {
                val title = action.movieTitle
                if (title != null) {
                    buildVideoSearchUrl(title)?.let { url ->
                        onOpenBrowser(url)
                    } ?: addSystemMessage("影视站配置未加载完成，请稍后再试")
                }
            }
            ActionType.QUERY_PENDING -> {
                val typeStr = action.movieType ?: "影视"
                val type = WatchListManager.WatchType.fromString(typeStr)
                val count = WatchListManager.getPendingCount(context, type)
                if (count >= 15) {
                    // 待看≥15，直接弹待看列表，不再调 AI
                    showPendingListDialog(type)
                } else {
                    // 待看<15，发送"需要推荐"给 AI
                    scope.launch { requestRecommendations(type, typeStr) }
                }
            }
            ActionType.SHOW_PENDING -> {
                val typeStr = action.movieType ?: "影视"
                showPendingListDialog(WatchListManager.WatchType.fromString(typeStr))
            }
            ActionType.SHOW_RECOMMENDATIONS -> {
                showRecommendationsDialog(action.movieTitles, WatchListManager.WatchType.fromString(action.movieType))
            }
            ActionType.QUERY_WATCHED -> {
                showRecentWatchedDialog()
            }
            ActionType.NONE -> {}
            else -> {}
        }
    }

    // 从AI响应中提取应用名称，返回匹配的应用列表
    fun extractAppNamesFromAIResponse(response: String, installedApps: List<AppHelper.AppInfo>): List<AppHelper.AppInfo> {
        val result = mutableListOf<AppHelper.AppInfo>()
        val responseLower = response.lowercase()

        for (app in installedApps) {
            val appNameLower = app.name.lowercase()
            // 检查应用名称是否出现在AI回复中
            if (responseLower.contains(appNameLower)) {
                result.add(app)
            }
        }

        return result.distinctBy { it.packageName }
    }

    LaunchedEffect(calendarPermissionResult) {
        calendarPermissionResult?.let { granted ->
            if (granted && pendingEventAction != null) {
                val action = pendingEventAction!!
                pendingEventAction = null
                calendarPermissionResult = null
                executeCreateEvent(action)
            } else if (!granted) {
                addSystemMessage("需要日历权限才能创建日程提醒，请授予权限后重试")
                pendingEventAction = null
                calendarPermissionResult = null
            }
        }
    }

    fun handleQuickCommand(input: String): Boolean {
        val trimmed = input.trim().lowercase()

        // 手电筒控制
        if (trimmed.matches(Regex("^(打开|开启|开)手电筒[。.]?$")) || 
            trimmed.matches(Regex("^手电筒(打开|开启)[。.]?$"))) {
            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                FlashlightHelper.turnOn(context)
                addSystemMessage("手电筒已打开")
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                addSystemMessage("需要相机权限才能使用手电筒，请授予权限后重试")
            }
            return true
        }
        
        if (trimmed.matches(Regex("^(关闭|关掉|关)手电筒[。.]?$")) || 
            trimmed.matches(Regex("^手电筒(关闭|关掉)[。.]?$"))) {
            FlashlightHelper.turnOff(context)
            addSystemMessage("手电筒已关闭")
            return true
        }

        // 时间查询
        if (trimmed.matches(Regex("^(现在)?几点(了)?[。.]?$")) || 
            trimmed.matches(Regex("^什么时间[。.]?$"))) {
            val now = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            addSystemMessage("现在是 $now")
            return true
        }

        // 日期查询
        if (trimmed.matches(Regex("^(今天|今天日期)?(几月几号|星期几|周几)[。.]?$"))) {
            val now = java.util.Date()
            val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日 EEEE", java.util.Locale.CHINESE)
            val dateStr = dateFormat.format(now)
            addSystemMessage("今天是 $dateStr")
            return true
        }

        // 返回桌面（需要无障碍服务）
        if (trimmed.matches(Regex("^(回桌面|回到主屏幕|按home|回主屏幕)[。.]?$"))) {
            val service = com.example.memo.autoui.service.AutoAccessibilityService.getInstance()
            if (service != null) {
                service.pressHome()
                addSystemMessage("已返回桌面")
            } else {
                addSystemMessage("无障碍服务未开启，无法执行此操作")
            }
            return true
        }

        // 按返回键（需要无障碍服务）
        if (trimmed.matches(Regex("^(按返回|返回|back)[。.]?$"))) {
            val service = com.example.memo.autoui.service.AutoAccessibilityService.getInstance()
            if (service != null) {
                service.pressBack()
                addSystemMessage("已按返回键")
            } else {
                addSystemMessage("无障碍服务未开启，无法执行此操作")
            }
            return true
        }

        return false
    }

    /**
     * 根据当前供应商选择合适的 Helper 调用 sendChatMessageStreaming
     *  - GLM：走 GlmHelper（兼容 DSK 同等系统提示词，输出文本工具调用标记）
     *  - DSK：走 DeepSeekHelper
     */
    suspend fun dispatchChatStreaming(
        historyMessages: List<ChatMessage>,
        onChunk: (String) -> Unit,
        onComplete: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        when (currentProvider) {
            ModelProvider.GLM -> {
                GlmHelper.sendChatMessageStreaming(
                    messages = historyMessages,
                    onChunk = onChunk,
                    onComplete = onComplete,
                    onError = onError
                )
            }
            ModelProvider.ALIYUN -> {
                // 阿里云 DashScope 的 OpenAI 兼容接口，调用逻辑与 DSK 相同
                // DeepSeekHelper 内部通过 AppConfig.apiUrl / model / apiKey 自动取到阿里云配置
                DeepSeekHelper.sendChatMessageStreaming(
                    messages = historyMessages,
                    onChunk = onChunk,
                    onComplete = onComplete,
                    onError = onError
                )
            }
            else -> {
                DeepSeekHelper.sendChatMessageStreaming(
                    messages = historyMessages,
                    onChunk = onChunk,
                    onComplete = onComplete,
                    onError = onError
                )
            }
        }
    }

    /**
     * 解析并执行 AI 返回的 Actions（与 DSK 路径完全相同的执行管线）
     *  - 用于 GLM 路径返回的文本工具调用标记
     */
    suspend fun executeAIActions(actions: List<AIAction>) {
        if (actions.isEmpty()) return
        val recommendActions = actions.filter { it.type == ActionType.RECOMMEND_APP }
        val otherActions = actions.filter { it.type != ActionType.OPEN_APP && it.type != ActionType.NONE && it.type != ActionType.RECOMMEND_APP }
        val openAppActions = actions.filter { it.type == ActionType.OPEN_APP }

        val independentTypes = setOf(
            ActionType.CREATE_NOTE, ActionType.FLASHLIGHT_ON,
            ActionType.FLASHLIGHT_OFF, ActionType.CREATE_LEDGER,
            ActionType.DELETE_LEDGER, ActionType.CREATE_TRANSACTION,
            ActionType.QUERY_LEDGER, ActionType.MATCH_LEDGER,
            ActionType.CREATE_EVENT, ActionType.CREATE_ALARM,
            ActionType.CREATE_CONTACT,
            ActionType.DELETE_CONTACT, ActionType.SEARCH_CONTACT,
            ActionType.SEARCH_PHONE_NUMBER, ActionType.CALL_PHONE,
            ActionType.SEARCH_CONTACT_FOR_CALL,
            ActionType.PLAY_IN_BROWSER,
            ActionType.QUERY_PENDING,
            ActionType.SHOW_PENDING,
            ActionType.SHOW_RECOMMENDATIONS,
            ActionType.QUERY_WATCHED
        )
        val independentActions = otherActions.filter { it.type in independentTypes }
        val dependentActions = otherActions.filter { it.type !in independentTypes }

        if (independentActions.size > 1) {
            val jobs = independentActions.map { action -> scope.async { executeAction(action) } }
            jobs.forEach { it.await() }
        } else {
            independentActions.forEach { executeAction(it) }
        }
        dependentActions.forEach { executeAction(it) }

        // 推荐应用：二次请求
        if (recommendActions.isNotEmpty()) {
            val recommendCategory = recommendActions.first().recommendCategory ?: ""
            val installedApps = withContext(Dispatchers.IO) { AppListCache.getCachedApps(context) }
            val appListText = installedApps.take(100).joinToString("\n") { app -> app.name }
            val recommendPrompt = """
                用户需求：$recommendCategory

                用户手机上已安装的应用列表：
                $appListText

                请根据用户需求，从以上列表中找出最匹配的应用。如果有多个匹配的应用，请列出所有相关的应用名称，然后追加：
                {打开应用:"应用名称1"} {打开应用:"应用名称2"} ...
            """.trimIndent()
            val recommendMessage = ChatMessage(content = recommendPrompt, role = MessageRole.USER)
            dispatchChatStreaming(
                historyMessages = listOf(
                    ChatMessage(content = "你是应用推荐助手。用户需要：$recommendCategory", role = MessageRole.USER),
                    recommendMessage
                ),
                onChunk = { /* 推荐阶段不实时更新 */ },
                onComplete = { recommendResponse ->
                    val recommendedApps = extractAppNamesFromAIResponse(recommendResponse, installedApps)
                    val (recommendText, _) = DeepSeekHelper.extractAction(recommendResponse)
                    if (recommendText.isNotBlank()) {
                        messages = messages + ChatMessage(content = recommendText, role = MessageRole.ASSISTANT)
                    }
                    if (recommendedApps.isNotEmpty()) {
                        pendingAppSelection = recommendedApps.map { it.packageName }
                        showAppSelectionDialog = true
                        addSystemMessage("为您找到以下${recommendCategory}应用，请选择：")
                    } else {
                        addSystemMessage("抱歉，您的手机上没有找到符合需求的${recommendCategory}应用")
                    }
                },
                onError = { error -> addSystemMessage("推荐应用失败：$error") }
            )
        } else {
            openAppActions.forEach { executeAction(it) }
        }
    }

    fun sendMessage() {
        if ((inputText.isBlank() && attachments.isEmpty()) || isLoading) return

        // 快照附件：发送时锁定列表，失败时回写原列表
        val pendingAttachments = attachments
        // 按当前供应商过滤实际要发送的附件：
        //  - GLM  ：保留全部（GLM 视觉模型支持图片 + 文档）
        //  - 阿里云：qwen-omni 只接受 image_url / video_url / video / text，不支持 file_url 协议
        //           所以只保留图片，文档类丢弃并提示
        //  - DSK  ：不支持多模态，全部丢弃并提示
        val effectiveAttachments = when (currentProvider) {
            ModelProvider.GLM -> pendingAttachments
            ModelProvider.ALIYUN -> {
                val (images, docs) = pendingAttachments.partition { it.type == AttachmentType.IMAGE }
                if (docs.isNotEmpty()) {
                    addSystemMessage("通义千问 qwen-omni 不支持文档识别，已自动丢弃 ${docs.size} 个非图片附件")
                }
                images
            }
            else -> {
                if (pendingAttachments.isNotEmpty()) {
                    addSystemMessage("当前模型不支持图片/文件识别，已自动丢弃附件")
                }
                emptyList()
            }
        }
        // 把"待发送的附件"绑定到 user 消息上，MessageBubble 会据此在气泡上方渲染横向预览
        // 阿里云下用过滤后的列表，避免气泡显示被丢弃的文档
        val userMessage = ChatMessage(
            content = inputText,
            role = MessageRole.USER,
            attachments = effectiveAttachments
        )
        val currentInput = inputText
        messages = messages + userMessage
        inputText = ""
        // 输入区附件清空 + 关闭加号面板
        attachments = emptyList()
        isPlusPanelExpanded = false
        keyboardController?.hide()

        // 记录用户消息
        com.example.memo.util.OperationLogger.logUserMessage(currentInput)

        // 先尝试快速命令（本地执行，不经过AI）
        if (handleQuickCommand(currentInput)) {
            return
        }

        isLoading = true

        // ===== 分支 0：带附件 → 强制走多模态路径，不进 Agent 模式 =====
        // Agent 引擎目前只走 DeepSeekHelper 文本通道，无法传图；
        // 用户带图片提问时应走 GLM 视觉模型 或 阿里云 qwen-omni 多模态模型 直接识别
        if (effectiveAttachments.isNotEmpty()) {
            scope.launch {
                val encoded = encodeAttachmentsForUpload(effectiveAttachments)
                val streamingMessage = ChatMessage(content = "", role = MessageRole.ASSISTANT)
                messages = messages + streamingMessage

                // GLM 和 阿里云 DashScope 的 OpenAI 兼容接口都接受 image_url(base64) 协议
                // GlmHelper 内部通过 AppConfig.apiUrl / apiKey / model 自动取到当前供应商配置
                // （GLM 取智谱 url+key，ALIYUN 取 DashScope url+sk-... key），所以同一函数两条路径都通用
                GlmHelper.sendChatMessageWithAttachments(
                    messages = messages.dropLast(1),
                    attachments = encoded,
                    onChunk = { chunk ->
                        scope.launch {
                            messages = messages.dropLast(1) + ChatMessage(content = chunk, role = MessageRole.ASSISTANT)
                        }
                    },
                    onComplete = { response ->
                        scope.launch {
                            messages = messages.dropLast(1) + ChatMessage(content = response, role = MessageRole.ASSISTANT)
                            com.example.memo.util.OperationLogger.logAIResponse(response)
                            val (text, actions) = DeepSeekHelper.extractAction(response)
                            val aiMessage = ChatMessage(content = text, role = MessageRole.ASSISTANT)
                            messages = messages.dropLast(1) + aiMessage
                            cleanupCameraTempFiles(encoded)
                            isLoading = false
                            executeAIActions(actions)
                        }
                    },
                    onError = { error ->
                        // 使用非可取消的独立 scope：防止用户看到错误后立刻关掉对话框
                        // 导致 onDispose 清空附件、又导致 rememberCoroutineScope 被取消，
                        // 附件状态最终丢失
                        kotlinx.coroutines.CoroutineScope(
                            kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()
                        ).launch {
                            attachments = pendingAttachments
                            val errorMessage = ChatMessage(
                                content = "抱歉，出错了：$error（附件已保留，请重试）",
                                role = MessageRole.ASSISTANT
                            )
                            messages = messages.dropLast(1) + errorMessage
                            isLoading = false
                        }
                    }
                )
            }
            return
        }

        if (isAgentMode) {
            agentStepRecords = emptyList()
            isAgentRunning = true
            UiAutoController.startOperation()

            scope.launch {
                val autoControlEnabled = AgentConfig.getAutoControlEnabled(context)
                val toolRegistry = AgentToolRegistry(
                    context = context,
                    ledgerViewModel = ledgerViewModel,
                    noteViewModel = noteViewModel,
                    memoryViewModel = memoryViewModel,
                    onCreateNote = { title, content ->
                        val note = Note(title = title, content = content, timestamp = System.currentTimeMillis())
                        onAddNote(note)
                        addSystemMessage("已创建笔记：$title")
                    },
                    onAppSelectionNeeded = { selection ->
                        agentPendingAppSelection = selection
                        agentAppSelectionPackageNames = selection.packageNames
                        showAgentAppSelectionDialog = true
                        addSystemMessage("根据\"${selection.category}\"找到以下${selection.apps.size}个应用，请选择要打开的应用：")
                    },
                    autoControlEnabled = autoControlEnabled,
                    onPlayInBrowser = { title ->
                        // 本地兜底校验：防止 AI 误判非影视内容（如"今日金价查询"）调用此工具
                        // 命中时：不跳电影站、不写入已看记录，给用户提示
                        if (WatchListManager.isLikelyMovieTitle(title)) {
                            buildVideoSearchUrl(title)?.let { url ->
                                onOpenBrowser(url)
                            }
                            WatchListManager.addToWatched(context, title, WatchListManager.WatchType.ALL)
                        } else {
                            addSystemMessage("检测到\"$title\"不像影视剧内容，已拦截跳转影视站和写入观影记录的操作。如需搜索其他内容，请配置通用浏览器搜索。")
                        }
                    },
                    onShowMovieRecommendations = { titles, type ->
                        showRecommendationsDialog(titles, type)
                    },
                    onShowPendingList = { type ->
                        showPendingListDialog(type)
                    },
                    onShowRecentWatched = {
                        showRecentWatchedDialog()
                    }
                )
                val agentEngine = AgentEngine(toolRegistry, maxSteps = 15)

                UiAutoController.setOnStopCallback {
                    agentEngine.cancel()
                }

                agentEngine.run(
                    userInput = currentInput,
                    callback = object : AgentEngine.AgentCallback {
                        override fun onStepUpdate(step: AgentStepRecord) {
                            scope.launch {
                                agentStepRecords = agentEngine.stepRecords.toList()
                            }
                        }

                        override fun onFinalAnswer(answer: String) {
                            scope.launch {
                                isLoading = false
                                isAgentRunning = false
                                UiAutoController.stopOperation("任务完成")
                                UiAutoController.clearOnStopCallback()
                                val aiMessage = ChatMessage(content = answer, role = MessageRole.ASSISTANT)
                                messages = messages + aiMessage
                                // 记录AI回复
                                com.example.memo.util.OperationLogger.logAIResponse(answer)
                            }
                        }

                        override fun onError(error: String) {
                            scope.launch {
                                isLoading = false
                                isAgentRunning = false
                                UiAutoController.stopOperation("出错：$error")
                                UiAutoController.clearOnStopCallback()
                                addSystemMessage("Agent执行出错：$error")
                            }
                        }

                        override fun onStopped(reason: String) {
                            scope.launch {
                                isLoading = false
                                isAgentRunning = false
                                UiAutoController.clearOnStopCallback()
                                addSystemMessage("⚠️ $reason")
                            }
                        }
                    }
                )
            }
            return
        }

        // ===== 分支 1：DSK 或无附件 → 走 DeepSeekHelper / GlmHelper 文本流式 =====
        scope.launch {
            // 预加载可能需要的数据（与 AI 调用并行）
            val preloadApps = scope.async(Dispatchers.IO) {
                AppListCache.getCachedApps(context)
            }

            // 添加一个空的 AI 消息，用于流式更新
            val aiMessageIndex = messages.size
            val streamingMessage = ChatMessage(content = "", role = MessageRole.ASSISTANT)
            messages = messages + streamingMessage

            withContext(Dispatchers.IO) {
                dispatchChatStreaming(
                    historyMessages = messages.dropLast(1), // 不包含空的 streamingMessage
                    onChunk = { chunk ->
                        // 流式更新消息内容
                        scope.launch {
                            messages = messages.dropLast(1) + ChatMessage(content = chunk, role = MessageRole.ASSISTANT)
                        }
                    },
                    onComplete = { response ->
                        scope.launch {
                            // 确保最终内容已更新
                            messages = messages.dropLast(1) + ChatMessage(content = response, role = MessageRole.ASSISTANT)
                            
                            // 记录AI回复
                            com.example.memo.util.OperationLogger.logAIResponse(response)

                            val (text, actions) = DeepSeekHelper.extractAction(response)
                            val aiMessage = ChatMessage(content = text, role = MessageRole.ASSISTANT)
                            messages = messages.dropLast(1) + aiMessage

                            val recommendActions = actions.filter { it.type == ActionType.RECOMMEND_APP }
                            val otherActions = actions.filter { it.type != ActionType.OPEN_APP && it.type != ActionType.NONE && it.type != ActionType.RECOMMEND_APP }
                            val openAppActions = actions.filter { it.type == ActionType.OPEN_APP }

                            val allActionsToExecute = otherActions + openAppActions

                            if (isAgentMode && allActionsToExecute.isNotEmpty() && recommendActions.isEmpty()) {
                                val plan = AgentActionParser.buildPlanFromActions(allActionsToExecute)
                                pendingAgentPlan = plan
                                pendingAgentActions = allActionsToExecute
                                showAgentConfirmDialog = true
                                isLoading = false
                            } else {
                                // 并行执行独立任务（互不依赖的操作同时进行）
                                val independentTypes = setOf(
                                    ActionType.CREATE_NOTE, ActionType.FLASHLIGHT_ON,
                                    ActionType.FLASHLIGHT_OFF, ActionType.CREATE_LEDGER,
                                    ActionType.DELETE_LEDGER, ActionType.CREATE_TRANSACTION,
                                    ActionType.QUERY_LEDGER, ActionType.MATCH_LEDGER,
                                    ActionType.CREATE_EVENT, ActionType.CREATE_ALARM,
                                    ActionType.CREATE_CONTACT,
                                    ActionType.DELETE_CONTACT, ActionType.SEARCH_CONTACT,
                                    ActionType.SEARCH_PHONE_NUMBER, ActionType.CALL_PHONE,
                                    ActionType.SEARCH_CONTACT_FOR_CALL,
                                    // 影视相关（内置浏览器方案）
                                    ActionType.PLAY_IN_BROWSER,
                                    ActionType.QUERY_PENDING,
                                    ActionType.SHOW_PENDING,
                                    ActionType.SHOW_RECOMMENDATIONS,
                                    ActionType.QUERY_WATCHED
                                )
                                val independentActions = otherActions.filter { it.type in independentTypes }
                                val dependentActions = otherActions.filter { it.type !in independentTypes }

                                // 并行执行独立操作
                                if (independentActions.size > 1) {
                                    withContext(Dispatchers.Main) {
                                        val jobs = independentActions.map { action ->
                                            scope.async { executeAction(action) }
                                        }
                                        jobs.forEach { it.await() }
                                    }
                                } else {
                                    independentActions.forEach { executeAction(it) }
                                }

                                // 顺序执行有依赖关系的操作
                                dependentActions.forEach { executeAction(it) }

                                if (recommendActions.isNotEmpty()) {
                                val recommendCategory = recommendActions.first().recommendCategory ?: ""
                                // 使用预加载的应用列表
                                val installedApps = preloadApps.await()
                                val appListText = installedApps.take(100).joinToString("\n") { app -> app.name }

                                // 构建需要AI推荐的消息
                                val recommendPrompt = """
用户需求：$recommendCategory

用户手机上已安装的应用列表：
$appListText

请根据用户需求，从以上列表中找出最匹配的应用。如果有多个匹配的应用，请列出所有相关的应用名称（只需要应用名称，不需要包名），然后在回复末尾添加标记：
{打开应用:"应用名称1"} {打开应用:"应用名称2"} ...
                                """.trimIndent()

                                val recommendMessage = ChatMessage(content = recommendPrompt, role = MessageRole.USER)

                                // 重新发送消息给AI（使用流式）
                                DeepSeekHelper.sendChatMessageStreaming(
                                    messages = listOf(
                                        ChatMessage(content = "你是应用推荐助手。用户需要：$recommendCategory", role = MessageRole.USER),
                                        recommendMessage
                                    ),
                                    onChunk = { chunk ->
                                        scope.launch {
                                            val recommendAiMessage = ChatMessage(content = chunk, role = MessageRole.ASSISTANT)
                                            messages = messages + recommendAiMessage
                                        }
                                    },
                                    onComplete = { recommendResponse ->
                                        scope.launch {
                                            // 解析AI推荐后返回的应用
                                            val recommendedApps = extractAppNamesFromAIResponse(recommendResponse, installedApps)
                                            val (recommendText, _) = DeepSeekHelper.extractAction(recommendResponse)

                                            // 显示AI的推荐回复
                                            if (recommendText.isNotBlank()) {
                                                val recommendAiMessage = ChatMessage(content = recommendText, role = MessageRole.ASSISTANT)
                                                messages = messages.dropLast(1) + recommendAiMessage
                                            }

                                            if (recommendedApps.isNotEmpty()) {
                                                // 弹出选择框让用户选择
                                                pendingAppSelection = recommendedApps.map { it.packageName }
                                                showAppSelectionDialog = true
                                                addSystemMessage("为您找到以下${recommendCategory}应用，请选择：")
                                            } else {
                                                addSystemMessage("抱歉，您的手机上没有找到符合需求的${recommendCategory}应用")
                                            }
                                            isLoading = false
                                        }
                                    },
                                    onError = { error ->
                                        scope.launch {
                                            addSystemMessage("推荐应用失败：$error")
                                            isLoading = false
                                        }
                                    }
                                )
                            } else {
                                // 没有推荐应用，直接执行打开应用的动作
                                isLoading = false
                                for (action in openAppActions) {
                                    executeAction(action)
                                }
                            }
                            }
                        }
                    },
                    onError = { error ->
                        scope.launch {
                            val errorMessage = ChatMessage(
                                content = "抱歉，出错了：$error",
                                role = MessageRole.ASSISTANT
                            )
                            messages = messages.dropLast(1) + errorMessage
                            isLoading = false
                        }
                    }
                )
            }
        }
    }

    // 全屏 AI 助手界面（仿 DeepSeek 风格）

    // 拦截系统返回键：默认行为是 finish Activity 回到桌面，这里改为只关闭 AI 助手界面
    // 优先级低于"加号面板展开时按返回关闭面板"——加号面板展开时让 BackHandler 先消费一次
    BackHandler(enabled = isPlusPanelExpanded) {
        isPlusPanelExpanded = false
    }
    BackHandler(enabled = !isPlusPanelExpanded) {
        onDismiss()
    }

    // 兜底关闭加号面板：只要 IME 弹出就强制收起
    // 解决 onFocusChanged 抢不过 IME inset 派发的时序问题：
    // 用户带附件点输入框时，IME 已可见但 panel 还在展开态，AIChatInputBar 高度超出
    // imePadding 后的可用区，输入行被推到可见区下方（被键盘遮住）。
    // 这里用 LaunchedEffect 响应 isImeVisible 变化，与 IME inset 派发同步触发，
    // 保证 panel 在 IME 可见的同一帧内就收起。
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(imeVisible) {
        if (imeVisible && isPlusPanelExpanded) {
            isPlusPanelExpanded = false
        }
    }

    // 临时启用 edge-to-edge + 关闭系统 adjustResize，让 WindowInsets.ime 准确派发到本 Composable
    // 关键：必须把 softInputMode 改成 ADJUST_NOTHING，否则系统会先按 adjustResize 把
    // 内容区缩小到 screen - 键盘高度，Compose 再在这个缩小后的区域上减一次 imeHeightDp，
    // 等于把输入框往上推了 2 倍键盘高度，用户根本看不见。
    // 离开时恢复原状，避免影响其他页面。
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            // 记录旧值，离开时还原
            val previousSoftInputMode = window.attributes.softInputMode
            window.setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            )
            onDispose {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                window.setSoftInputMode(previousSoftInputMode)
            }
        } else {
            onDispose { }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            // 关键：拦截空白区域的点击事件，防止穿透到下方主界面
            // （如点击发送按钮时不会误触发底层主界面的闹钟/笔记按钮）
            // 按钮等可交互子元素依然会优先消费点击事件
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            // imePadding() 是 Compose 原生处理 IME 高度的方式：内部监听 WindowInsets.ime
            // 实时跟随键盘的显示/隐藏/拖动，把 Column 内容整体上抬到键盘之上。
            // 配合上面的 SOFT_INPUT_ADJUST_NOTHING 不会产生双重 padding。
            .imePadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 顶部栏：标题 + 关闭按钮
            // 加 statusBarsPadding() 是因为 edge-to-edge 模式下（见上面 setDecorFitsSystemWindows(false)）
            // 内容会延伸到状态栏后面，padding 把标题栏顶到状态栏下沿，避免被通知栏遮挡
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color(0xFF1A1A1A)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AI 助手",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A)
                    )
                }
                // 右侧留白，保持标题居中偏左
                Spacer(modifier = Modifier.width(48.dp))
            }

            if (showApiKeyInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(80.dp))
                    Text(
                        text = "请输入 API Key",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1A1A1A)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "开始和 AI 助手对话",
                        fontSize = 13.sp,
                        color = Color(0xFF999999)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("sk-...", color = Color(0xFFBBBBBB)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE0E0E0),
                            unfocusedBorderColor = Color(0xFFE0E0E0)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            AppConfig.apiKey = apiKeyInput.trim()
                            showApiKeyInput = false
                        },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1A1A1A)
                        )
                    ) {
                        Text("开始对话", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            } else {
                if (isAgentMode && (agentStepRecords.isNotEmpty() || isAgentRunning)) {
                    AgentThinkingPanel(
                        stepRecords = agentStepRecords,
                        isRunning = isAgentRunning
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // 消息区 / 空状态
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty()) {
                        AIEmptyState(modifier = Modifier.fillMaxSize())
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(messages) { message ->
                                MessageBubble(
                                    message = message,
                                    onWatchMovie = { movieTitle ->
                                        buildVideoSearchUrl(movieTitle)?.let { url ->
                                            onOpenBrowser(url)
                                        } ?: addSystemMessage("影视站配置未加载完成，请稍后再试")
                                    }
                                )
                            }

                            if (isLoading) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    Color(0xFFF5F5F5),
                                                    RoundedCornerShape(14.dp)
                                                )
                                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                "思考中...",
                                                color = Color(0xFF999999),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部输入区域
                AIChatInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isPlusPanelExpanded = isPlusPanelExpanded,
                    onTogglePlusPanel = {
                        // 点"+"展开面板时，如果键盘还开着先收键盘：
                        // 否则 AIChatInputBar 高度从 ~60dp 涨到 ~280dp，会撑爆
                        // imePadding 减出来的可用区，导致输入行被压到键盘下方。
                        // 收起键盘 → IME inset 归零 → 整屏可用 → panel + 输入行都能放下。
                        if (!isPlusPanelExpanded) {
                            keyboardController?.hide()
                        }
                        isPlusPanelExpanded = !isPlusPanelExpanded
                    },
                    attachments = attachments,
                    onRemoveAttachment = { att -> attachments = attachments - att },
                    onPickFromGallery = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    onTakePhoto = { takePhoto() },
                    onPickFile = {
                        filePickerLauncher.launch(DOCUMENT_MIME_TYPES)
                    },
                    onSend = { sendMessage() },
                    onAttachmentWarningShown = { attachmentWarning = null },
                    attachmentWarning = attachmentWarning,
                    isLoading = isLoading,
                    canSend = (inputText.isNotBlank() || attachments.isNotEmpty()) && !isLoading,
                    // 加号入口：GLM 视觉模型 + 阿里云 qwen-omni 多模态模型 都支持附件
                    showPlusButton = currentProvider == ModelProvider.GLM || currentProvider == ModelProvider.ALIYUN
                )
            }
        }
    }

    if (attachmentWarning != null) {
        AlertDialog(
            onDismissRequest = { attachmentWarning = null },
            title = { Text("提示") },
            text = { Text(attachmentWarning!!) },
            confirmButton = {
                TextButton(onClick = { attachmentWarning = null }) {
                    Text("好的")
                }
            }
        )
    }

    if (showContactDialog) {
        AlertDialog(
            onDismissRequest = { showContactDialog = false },
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
                TextButton(onClick = { showContactDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCallConfirmDialog && contactToCall != null) {
        AlertDialog(
            onDismissRequest = { showCallConfirmDialog = false },
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
                        contactToCall?.phone?.let { makePhoneCall(it) }
                    }
                ) {
                    Text("拨打")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showExistingContactDialog && existingContact != null) {
        AlertDialog(
            onDismissRequest = { showExistingContactDialog = false },
            title = { Text("该号码已存在") },
            text = {
                Column {
                    Text(text = "姓名：${existingContact!!.name}")
                    existingContact!!.phone?.let {
                        Text(text = "电话：$it")
                    }
                    Text(
                        text = "是否将其名称更换为：${pendingPhoneInfo?.name}",
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingContactDialog = false
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            val success = PhoneHelper.updateContactName(context, existingContact!!.id, pendingPhoneInfo?.name ?: "")
                            if (success) {
                                addSystemMessage("已更新联系人名称")
                            }
                        } else {
                            addSystemMessage("需要写入通讯录权限")
                        }
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExistingContactDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteContactDialog && contactToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteContactDialog = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text(text = "确定要删除联系人\"${contactToDelete!!.name}\"吗？")
                    contactToDelete!!.phone?.let {
                        Text(text = "电话：$it", color = Color.Gray)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteContactDialog = false
                        val success = PhoneHelper.deleteContact(context, contactToDelete!!.id)
                        if (success) {
                            addSystemMessage("已删除联系人：${contactToDelete!!.name}")
                        } else {
                            addSystemMessage("删除联系人失败")
                        }
                        contactToDelete = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteContactDialog = false
                    contactToDelete = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteLedgerConfirmDialog && pendingDeleteLedgerName != null) {
        AlertDialog(
            onDismissRequest = { showDeleteLedgerConfirmDialog = false },
            title = { Text("确认删除") },
            text = {
                Text("确定要删除账本\"$pendingDeleteLedgerName\"吗？该账本下的所有记录也会被删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteLedgerConfirmDialog = false
                        val ledgerName = pendingDeleteLedgerName!!
                        scope.launch {
                            val ledgers = ledgerViewModel.allLedgers.first()
                            val ledger = ledgers.find { it.title == ledgerName }
                            if (ledger != null) {
                                ledgerViewModel.deleteLedger(ledger)
                                addSystemMessage("已删除账本：$ledgerName")
                            }
                        }
                        pendingDeleteLedgerName = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteLedgerConfirmDialog = false
                    pendingDeleteLedgerName = null
                }) {
                    Text("取消")
                }
            }
        )
    }

    // 影视推荐弹窗
    if (showMovieDialog) {
        MovieRecommendDialog(
            mode = movieDialogMode,
            titles = movieDialogTitles,
            watchType = movieDialogType,
            onPlayMovie = { title ->
                handleMoviePlay(title, movieDialogTitles, movieDialogMode, movieDialogType)
            },
            onDismissAll = {
                // AI推荐模式：未选择则全部加入待看
                if (movieDialogMode == MovieDialogMode.AI_RECOMMEND) {
                    WatchListManager.addAllToPending(context, movieDialogTitles, movieDialogType)
                    addSystemMessage("已将${movieDialogTitles.size}个影视加入待看列表")
                }
                showMovieDialog = false
            }
        )
    }

    // 最近已看弹窗
    if (showRecentWatchedDialog) {
        AlertDialog(
            onDismissRequest = { showRecentWatchedDialog = false },
            title = { Text("最近观看记录", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (recentWatchedList.isEmpty()) {
                        Text(
                            text = "暂无观看记录",
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            color = Color.Gray,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        recentWatchedList.forEachIndexed { index, title ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showRecentWatchedDialog = false
                                        buildVideoSearchUrl(title)?.let { url ->
                                            onOpenBrowser(url)
                                        }
                                    },
                                color = Color(0xFFE3F2FD),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. $title",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    color = Color(0xFF1565C0),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (index < recentWatchedList.size - 1) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRecentWatchedDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    if (showAppSelectionDialog && pendingAppSelection.isNotEmpty()) {
        val isSingleApp = pendingAppSelection.size == 1
        val dialogTitle = if (pendingAppSelectionTitle.isNotBlank()) pendingAppSelectionTitle
            else if (isSingleApp) "确认打开应用" else "选择要打开的应用"
        val dialogSubtitle = if (pendingAppSelectionTitle.isNotBlank()) {
            if (isSingleApp) "是否使用此应用？" else "请选择要使用的应用："
        } else {
            if (isSingleApp) "是否打开此应用？" else "找到多个应用，请选择："
        }
        AlertDialog(
            onDismissRequest = {
                showAppSelectionDialog = false
                pendingAppSelection = emptyList()
                pendingAppDeepLinks = emptyList()
                pendingAppSelectionTitle = ""
            },
            title = {
                Text(
                    text = dialogTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dialogSubtitle,
                        fontSize = 18.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        itemsIndexed(pendingAppSelection) { index, packageName ->
                            val appName = AppHelper.getAppName(context, packageName) ?: packageName
                            val deepLink = pendingAppDeepLinks.getOrNull(index)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        showAppSelectionDialog = false
                                        val success = if (deepLink != null) {
                                            AppHelper.openAppWithScheme(context, packageName, deepLink)
                                        } else {
                                            AppHelper.openApp(context, packageName)
                                        }
                                        if (success) {
                                            addSystemMessage(if (deepLink != null) "正在跳转到：$appName" else "正在打开：$appName")
                                        } else {
                                            addSystemMessage("无法打开应用：$appName")
                                        }
                                        pendingAppSelection = emptyList()
                                        pendingAppDeepLinks = emptyList()
                                        pendingAppSelectionTitle = ""
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = appName,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAppSelectionDialog = false
                        pendingAppSelection = emptyList()
                        pendingAppDeepLinks = emptyList()
                        pendingAppSelectionTitle = ""
                    }
                ) {
                    Text("取消", fontSize = 18.sp)
                }
            },
            dismissButton = {}
        )
    }

    if (showAgentAppSelectionDialog && agentPendingAppSelection != null) {
        val selection = agentPendingAppSelection!!
        AlertDialog(
            onDismissRequest = {
                showAgentAppSelectionDialog = false
                agentPendingAppSelection = null
                agentAppSelectionPackageNames = emptyList()
            },
            title = {
                Text(
                    text = "选择要打开的应用",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "根据\"${selection.category}\"找到以下应用：",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        itemsIndexed(selection.apps) { index, appName ->
                            val packageName = selection.packageNames.getOrNull(index) ?: ""
                            val description = selection.descriptions.getOrNull(index) ?: ""
                            val deepLink = selection.deepLinks.getOrNull(index)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        showAgentAppSelectionDialog = false
                                        val success = if (deepLink != null) {
                                            AppHelper.openAppWithScheme(context, packageName, deepLink)
                                        } else {
                                            AppHelper.openApp(context, packageName)
                                        }
                                        if (success) {
                                            addSystemMessage(if (deepLink != null) "正在跳转到：$appName" else "正在打开：$appName")
                                        } else {
                                            addSystemMessage("无法打开应用：$appName")
                                        }
                                        agentPendingAppSelection = null
                                        agentAppSelectionPackageNames = emptyList()
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = appName,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = description,
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAgentAppSelectionDialog = false
                        agentPendingAppSelection = null
                        agentAppSelectionPackageNames = emptyList()
                        addSystemMessage("已取消选择")
                    }
                ) {
                    Text("取消", fontSize = 18.sp)
                }
            },
            dismissButton = {}
        )
    }

    AgentConfirmDialog(
        visible = showAgentConfirmDialog,
        plan = pendingAgentPlan,
        onConfirm = {
            showAgentConfirmDialog = false
            val actions = pendingAgentActions
            pendingAgentPlan = null
            pendingAgentActions = null
            actions?.let { actionList ->
                val otherActions = actionList.filter { it.type != ActionType.OPEN_APP && it.type != ActionType.NONE && it.type != ActionType.RECOMMEND_APP }
                val openAppActions = actionList.filter { it.type == ActionType.OPEN_APP }
                scope.launch {
                    for (action in otherActions) {
                        executeAction(action)
                    }
                    for (action in openAppActions) {
                        executeAction(action)
                    }
                }
            }
        },
        onCancel = {
            showAgentConfirmDialog = false
            pendingAgentPlan = null
            pendingAgentActions = null
            addSystemMessage("已取消执行")
        },
        onDismiss = {
            showAgentConfirmDialog = false
        }
    )
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onWatchMovie: (String) -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val showAttachmentStrip = isUser && message.attachments.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Text("AI", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // 用户消息：附件预览条 + 气泡（垂直方向）
        // AI 消息：仅气泡
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            // 附件预览：横向滚动 LazyRow，最大宽度受气泡约束
            if (showAttachmentStrip) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(message.attachments, key = { it.uri.toString() }) { att ->
                        // 已发送的附件只读预览：不传 onRemove 即可隐藏右上角 ×
                        AttachmentCard(attachment = att)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .background(
                        if (isUser) Color(0xFF1A1A1A) else Color(0xFFF5F5F5),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // SelectionContainer 让用户长按消息气泡即可选中/复制文字
                // AI 消息里的片名卡片（clickable Surface）会中断选择，但其他文本片段可跨段选中
                SelectionContainer {
                    if (isUser) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    } else {
                        // AI 消息：识别《片名》并渲染为可点击的影视搜索卡片
                        MovieAwareText(
                            text = message.content,
                            onWatchMovie = onWatchMovie
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E8E8)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "我",
                    color = Color(0xFF1A1A1A),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 影视感知文本：识别《片名》标记，渲染为可点击卡片，点击后触发影视搜索
 */
@Composable
private fun MovieAwareText(
    text: String,
    onWatchMovie: (String) -> Unit
) {
    val moviePattern = remember { Regex("《([^》]+)》") }
    val matches = moviePattern.findAll(text).toList()

    // 没有匹配到片名，按普通文本渲染
    if (matches.isEmpty()) {
        Text(
            text = text,
            color = Color.Black,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        return
    }

    Column {
        var lastIndex = 0
        for (match in matches) {
            // 片名前的普通文本片段
            if (match.range.first > lastIndex) {
                Text(
                    text = text.substring(lastIndex, match.range.first),
                    color = Color.Black,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            // 影片名卡片
            val movieTitle = match.groupValues[1]
            Surface(
                modifier = Modifier
                    .clickable { onWatchMovie(movieTitle) },
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "《$movieTitle》",
                        color = Color(0xFF1976D2),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "搜索播放",
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            lastIndex = match.range.last + 1
        }
        // 末尾剩余的普通文本
        if (lastIndex < text.length) {
            Text(
                text = text.substring(lastIndex),
                color = Color(0xFF1A1A1A),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * AI 助手空状态：显示「可以让 AI 做的事」欢迎界面
 */
@Composable
private fun AIEmptyState(modifier: Modifier = Modifier) {
    val features = listOf(
        "记录笔记" to "把想法、灵感、待办快速写下来",
        "管理联系人" to "新增、查询、拨打电话",
        "创建日程提醒" to "说「明天下午3点开会」即可",
        "管理账本" to "记一笔支出 / 分析账本数据",
        "打开/关闭手电筒" to "直接说「打开手电筒」",
        "搜索影视" to "推荐电影 / 跳转内置浏览器观看",
        "打开应用" to "说「打开微信」即可"
    )

    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 32.dp)
        ) {
            Text(
                text = "你好，我是 AI 助手",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "我可以帮你做这些事",
                fontSize = 14.sp,
                color = Color(0xFF999999)
            )
            Spacer(modifier = Modifier.height(24.dp))
            features.forEach { (title, desc) ->
                FeatureItem(title = title, desc = desc)
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun FeatureItem(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = desc,
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
        }
    }
}

/**
 * 底部输入栏：白色圆角输入框 + 加号按钮 + 发送按钮 + 可展开操作面板
 */
@Composable
private fun AIChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isPlusPanelExpanded: Boolean,
    onTogglePlusPanel: () -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit,
    onSend: () -> Unit,
    onAttachmentWarningShown: () -> Unit,
    attachmentWarning: String?,
    isLoading: Boolean,
    canSend: Boolean,
    showPlusButton: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // 展开的操作面板
        AnimatedVisibility(
            visible = isPlusPanelExpanded,
            enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(160))
        ) {
            PlusActionPanel(
                attachments = attachments,
                onRemoveAttachment = onRemoveAttachment,
                onPickFromGallery = onPickFromGallery,
                onTakePhoto = onTakePhoto,
                onPickFile = onPickFile
            )
        }

        // 附件警告条（持续显示 3 秒后自动消失）
        LaunchedEffect(attachmentWarning) {
            if (attachmentWarning != null) {
                kotlinx.coroutines.delay(3000)
                onAttachmentWarningShown()
            }
        }

        // 输入行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 白色圆角输入框（BasicTextField + 自定义外观）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = if (showPlusButton) "发个消息或添加附件" else "发个消息试试吧",
                        color = Color(0xFFBDBDBD),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        // 获焦时自动收起加号面板：
                        // 展开状态下 PlusActionPanel（~175dp）会占用大量高度，
                        // 弹出输入法后 AIChatInputBar 总高度 + 顶栏容易超出 imePadding 后的可用区，
                        // 导致输入行被压扁、文字显示不全。收起后只剩 ~60dp 的输入行，
                        // 键盘上方有充足空间显示文字。
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused && isPlusPanelExpanded) {
                                onTogglePlusPanel()
                            }
                        },
                    textStyle = TextStyle(
                        color = Color(0xFF1A1A1A),
                        fontSize = 15.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1A1A1A)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    maxLines = 4
                )
            }

            // 加号按钮（仅在 GLM 供应商下显示；DSK 完全隐藏）
            if (showPlusButton) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularIconButton(
                    icon = Icons.Default.Add,
                    contentDescription = "更多",
                    onClick = onTogglePlusPanel,
                    tint = Color(0xFF1A1A1A),
                    rotation = if (isPlusPanelExpanded) 45f else 0f
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 发送按钮（圆形）
            CircularIconButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = "发送",
                onClick = onSend,
                enabled = canSend,
                tint = if (canSend) Color.White else Color(0xFFBDBDBD),
                background = if (canSend) Color(0xFF1A1A1A) else Color(0xFFF0F0F0)
            )
        }
    }
}

/**
 * 圆形图标按钮：用于输入栏右侧的加号/发送按钮
 */
@Composable
private fun CircularIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color(0xFF1A1A1A),
    background: Color = Color(0xFFF0F0F0),
    rotation: Float = 0f
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) background else Color(0xFFF5F5F5))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(rotationZ = rotation)
        )
    }
}

/**
 * 加号按钮展开后的操作面板：
 *   - 顶部：横向滑动的已选附件卡片
 *   - 底部：拍照 / 相册 / 文件 三个圆角功能按钮
 */
@Composable
private fun PlusActionPanel(
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 4.dp)
    ) {
        // 横向滑动附件卡片栏
        if (attachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(attachments, key = { it.uri.toString() }) { att ->
                    AttachmentCard(
                        attachment = att,
                        onRemove = { onRemoveAttachment(att) }
                    )
                }
            }
        }

        // 三个圆角功能按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoCamera,
                label = "拍照",
                onClick = onTakePhoto
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.PhotoLibrary,
                label = "相册",
                onClick = onPickFromGallery
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.AttachFile,
                label = "文件",
                onClick = onPickFile
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

/**
 * 圆角功能按钮：图标 + 文字
 */
@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF5F5F5))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF1A1A1A),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFF1A1A1A),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ======================================================================
// 附件工具函数（顶层）
// ======================================================================

/**
 * 从 content URI 读取 displayName 和 size
 *  - 先用 OpenableColumns 查 DISPLAY_NAME / SIZE
 *  - 都拿不到时再退回到按路径解析
 */
fun queryFileNameAndSize(context: android.content.Context, uri: Uri): Pair<String?, Long?> {
    val resolver = context.contentResolver
    var name: String? = null
    var size: Long? = null
    try {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}
    if (name == null) {
        name = uri.lastPathSegment
    }
    if (size == null) {
        try {
            resolver.openAssetFileDescriptor(uri, "r")?.use { size = it.length }
        } catch (_: Exception) {}
    }
    return name to size
}

/**
 * 从文件名猜测 MIME
 */
fun guessMimeFromName(name: String?): String {
    if (name.isNullOrBlank()) return "application/octet-stream"
    val lower = name.lowercase(Locale.getDefault())
    return when {
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".webp") -> "image/webp"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".doc") -> "application/msword"
        lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        lower.endsWith(".xls") -> "application/vnd.ms-excel"
        lower.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        lower.endsWith(".ppt") -> "application/vnd.ms-powerpoint"
        lower.endsWith(".pptx") -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        lower.endsWith(".txt") -> "text/plain"
        else -> "application/octet-stream"
    }
}

/**
 * 加载图片缩略图：读取尺寸后按 2 的幂次缩放，再用 centerCrop 切到 80x80
 * 文档不生成缩略图
 */
fun loadImageThumbnail(context: android.content.Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        // 1. 读取原始尺寸
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return null
        // 2. 计算 inSampleSize（目标边长 160，留出余量给 Crop）
        var inSampleSize = 1
        val target = 160
        var w = srcW
        var h = srcH
        while (w / inSampleSize > target || h / inSampleSize > target) {
            inSampleSize *= 2
        }
        // 3. 加载缩放后的位图
        val loadOpts = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        val scaled = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, loadOpts) } ?: return null
        // 4. 中心裁剪到 80x80
        val side = minOf(scaled.width, scaled.height)
        val x = (scaled.width - side) / 2
        val y = (scaled.height - side) / 2
        val square = Bitmap.createBitmap(scaled, x, y, side, side)
        if (square.width == 80) square else Bitmap.createScaledBitmap(square, 80, 80, true)
    } catch (e: Exception) {
        null
    }
}

/**
 * 创建拍照落盘文件（FileProvider 形式）
 *  - 路径：context.cacheDir/images/IMG_yyyyMMdd_HHmmss.jpg
 *  - 返回 (File, content:// URI) 给相机 intent 使用
 */
fun createCameraOutputFile(context: android.content.Context): Pair<File, Uri> {
    val dir = File(context.cacheDir, "images").apply { if (!exists()) mkdirs() }
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val file = File(dir, "IMG_$ts.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

/**
 * 附件卡片：图片显示 80x80 缩略图，文档显示文件类型图标 + 文件名
 * 通用：右下角显示文件大小；右上角可选显示删除按钮（已发送的预览默认不显示）
 * 大小超过阈值的卡片：边框标黄 + 角标"较大文件"提示
 *
 * @param attachment    附件数据
 * @param onRemove      删除回调（传 null/空 表示卡片只读，不显示右上角 ×）
 */
@Composable
fun AttachmentCard(
    attachment: Attachment,
    onRemove: (() -> Unit)? = null
) {
    val sizeText = attachment.readableSize()
    val isOversize = when (attachment.type) {
        AttachmentType.IMAGE -> attachment.sizeBytes > MAX_IMAGE_SIZE_BYTES
        AttachmentType.DOCUMENT -> attachment.sizeBytes > MAX_DOCUMENT_SIZE_BYTES
    }

    val borderColor = if (isOversize) Color(0xFFFFB300) else Color.Transparent
    val showOversizeBadge = isOversize
    val showRemoveButton = onRemove != null

    Box(
        modifier = Modifier
            .width(96.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFEEEEEE))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部：缩略图或文件类型图标
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                when (attachment.type) {
                    AttachmentType.IMAGE -> {
                        val bmp = attachment.thumbnail
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Image,
                                contentDescription = null,
                                tint = Color(0xFF9E9E9E),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    AttachmentType.DOCUMENT -> {
                        Icon(
                            imageVector = iconForDocument(attachment.displayName, attachment.mimeType),
                            contentDescription = null,
                            tint = colorForDocument(attachment.displayName, attachment.mimeType),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 文件名（单行省略）
            Text(
                text = attachment.displayName,
                fontSize = 11.sp,
                color = Color(0xFF1A1A1A),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        // 右下：文件大小
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
        ) {
            Surface(
                color = Color(0xCC1A1A1A),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = sizeText,
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        // 右上：删除按钮（已发送的预览不显示）
        if (showRemoveButton) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC1A1A1A))
                    .clickable { onRemove?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        // 较大文件角标
        if (showOversizeBadge) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
            ) {
                Surface(
                    color = Color(0xFFFFB300),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "较大文件",
                        color = Color.White,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

private fun iconForDocument(name: String, mime: String): ImageVector {
    val lower = name.lowercase(Locale.getDefault())
    val m = mime.lowercase(Locale.getDefault())
    return when {
        m == "application/pdf" || lower.endsWith(".pdf") -> Icons.Filled.PictureAsPdf
        m.contains("sheet") || m.contains("excel") || lower.endsWith(".xls") || lower.endsWith(".xlsx") -> Icons.Filled.TableChart
        m == "text/plain" || lower.endsWith(".txt") -> Icons.Filled.TextSnippet
        m.contains("word") || lower.endsWith(".doc") || lower.endsWith(".docx") -> Icons.Filled.Description
        m.contains("presentation") || m.contains("powerpoint") || lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

private fun colorForDocument(name: String, mime: String): Color {
    val lower = name.lowercase(Locale.getDefault())
    val m = mime.lowercase(Locale.getDefault())
    return when {
        m == "application/pdf" || lower.endsWith(".pdf") -> Color(0xFFE53935)
        m.contains("sheet") || m.contains("excel") || lower.endsWith(".xls") || lower.endsWith(".xlsx") -> Color(0xFF2E7D32)
        m.contains("word") || lower.endsWith(".doc") || lower.endsWith(".docx") -> Color(0xFF1565C0)
        m.contains("presentation") || m.contains("powerpoint") || lower.endsWith(".ppt") || lower.endsWith(".pptx") -> Color(0xFFEF6C00)
        else -> Color(0xFF616161)
    }
}
