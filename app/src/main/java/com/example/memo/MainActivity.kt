package com.example.memo

import android.Manifest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.memo.viewModel.NoteViewModel
import com.example.memo.viewModel.LedgerViewModel
import com.example.memo.viewModel.MemoryViewModel
import com.example.memo.viewModel.AlarmViewModel
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.memo.data.Alarm
import com.example.memo.data.AlarmHelper
import com.example.memo.data.AppConfig
import com.example.memo.data.AppConfigLoader
import com.example.memo.data.Ledger
import com.example.memo.data.Note
import com.example.memo.data.Transaction
import com.example.memo.data.WatchListManager
import com.example.memo.ui.AlarmEditorScreen
import com.example.memo.ui.AlarmListContent
import com.example.memo.ui.LedgerDetailScreen
import com.example.memo.ui.LedgerListContent
import com.example.memo.ui.MemoryScreen
import com.example.memo.ui.NoteEditorScreen
import com.example.memo.ui.NoteListContent
import com.example.memo.ui.components.AddLedgerDialog
import com.example.memo.ui.components.AddTransactionBottomSheet
import com.example.memo.ui.components.AIChatDialog
import com.example.memo.ui.components.AutoControlSettingsDialog
import com.example.memo.ui.browser.BrowserScreen
import com.example.memo.ui.movie.WatchRecordDialog
import com.example.memo.ui.movie.WatchRecordListDialog
import com.example.memo.ui.theme.KotlinDSLMemoTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult

val requiredPermissions = arrayOf(
    Manifest.permission.READ_CALENDAR,
    Manifest.permission.WRITE_CALENDAR,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.WRITE_CONTACTS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.CAMERA
)

fun hasQueryAllPackagesPermission(context: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        val pm = context.packageManager
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.QUERY_ALL_PACKAGES
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        val testIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(testIntent, PackageManager.MATCH_ALL)
        apps.size > 50
    } else {
        true
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(this)
        setContent {
            KotlinDSLMemoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MemoApp()
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    object NoteList : Screen("note_list")
    object NoteEditor : Screen("note_editor")
    object LedgerList : Screen("ledger_list")
    object LedgerDetail : Screen("ledger_detail/{ledgerId}") {
        fun createRoute(ledgerId: Long) = "ledger_detail/$ledgerId"
    }
    object MemoryList : Screen("memory_list")
    object AlarmList : Screen("alarm_list")
    object AlarmEditor : Screen("alarm_editor")
    object Browser : Screen("browser/{url}") {
        fun createRoute(url: String): String {
            // 整个 URL 必须做 URL-encode，避免 / ? & 等字符干扰导航路由解析
            return "browser/${java.net.URLEncoder.encode(url, "UTF-8")}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoApp(
    noteViewModel: NoteViewModel = viewModel(),
    ledgerViewModel: LedgerViewModel = viewModel(),
    memoryViewModel: MemoryViewModel = viewModel(),
    alarmViewModel: AlarmViewModel = viewModel()
) {
    val context = LocalContext.current

    var permissionsRequested by remember { mutableStateOf(false) }
    var showAppPermissionGuide by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        if (!permissionsRequested) {
            val allGranted = requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (!allGranted) {
                permissionLauncher.launch(requiredPermissions)
            }
            permissionsRequested = true
        }
        if (!hasQueryAllPackagesPermission(context)) {
            showAppPermissionGuide = true
        }
    }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAutoControlSettings by remember { mutableStateOf(false) }
    var showAIChat by remember { mutableStateOf(false) }

    // 观影记录弹窗流程：单一 state 控制当前步骤
    // null = 关闭；ENTRY = 入口弹窗；PENDING/WATCHED = 列表弹窗
    // 从列表弹窗返回时只需把 state 设为 ENTRY，入口弹窗自动恢复显示
    var watchRecordStep by remember {
        mutableStateOf<com.example.memo.ui.movie.WatchRecordStep?>(null)
    }

    // 协程作用域（用于观影记录跳转浏览器时异步构建 URL）
    val scope = rememberCoroutineScope()

    // 默认影视站配置（用于《片名》点击跳转内置浏览器）
    var defaultVideoSite by remember { mutableStateOf<AppConfigLoader.VideoSite?>(null) }

    val apiUrl = AppConfig.apiUrl
    val model = AppConfig.model
    val apiKey = AppConfig.apiKey

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val allNotes by noteViewModel.allNotes.collectAsState(initial = emptyList())
    val allLedgers by ledgerViewModel.allLedgers.collectAsState(initial = emptyList())
    val allAlarms by alarmViewModel.allAlarms.collectAsState(initial = emptyList())

    // 当闹钟数据变化时，重新调度已启用的闹钟
    LaunchedEffect(allAlarms) {
        allAlarms.forEach { alarm ->
            if (alarm.isEnabled) {
                AlarmHelper.setAlarm(context, alarm)
            } else {
                AlarmHelper.cancelAlarm(context, alarm.id)
            }
        }
    }

    // 预加载默认影视站配置（观影记录卡片点击跳转用）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val config = AppConfigLoader.getConfig(context)
            defaultVideoSite = config.videoSites.firstOrNull { it.isDefault } ?: config.videoSites.firstOrNull()
        }
    }

    /**
     * 根据片名构建内置浏览器搜索 URL
     * 与 AI 助手跳转使用同一个默认影视站
     */
    fun buildVideoSearchUrl(keyword: String): String? {
        val site = defaultVideoSite ?: return null
        val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
        return site.searchUrlTemplate.replace("{keyword}", encoded)
    }

    val searchQuery by noteViewModel.searchQuery.collectAsState()
    val isSearching by noteViewModel.isSearching.collectAsState()

    val notes = if (isSearching) {
        noteViewModel.searchNotes(allNotes, searchQuery)
    } else {
        allNotes
    }

    var currentEditNote by remember { mutableStateOf<Note?>(null) }
    var currentLedger by remember { mutableStateOf<Ledger?>(null) }
    var currentEditAlarm by remember { mutableStateOf<Alarm?>(null) }

    var alarmSelectionMode by remember { mutableStateOf(false) }
    var selectedAlarms by remember { mutableStateOf<Set<Alarm>>(emptySet()) }

    var showAddLedgerDialog by remember { mutableStateOf(false) }
    var showAddTransactionSheet by remember { mutableStateOf(false) }
    val addTransactionSheetState = rememberModalBottomSheetState()

    var noteSelectionMode by remember { mutableStateOf(false) }
    var selectedNotes by remember { mutableStateOf<Set<Note>>(emptySet()) }

    var ledgerSelectionMode by remember { mutableStateOf(false) }
    var selectedLedgers by remember { mutableStateOf<Set<Ledger>>(emptySet()) }

    var isDeleting by remember { mutableStateOf(false) }

    val isInListPage = currentRoute == Screen.NoteList.route ||
            currentRoute == Screen.LedgerList.route ||
            currentRoute == Screen.AlarmList.route
    val isInSelectionMode = (currentRoute == Screen.NoteList.route && noteSelectionMode) ||
            (currentRoute == Screen.LedgerList.route && ledgerSelectionMode) ||
            (currentRoute == Screen.AlarmList.route && alarmSelectionMode)

    fun exitNoteSelectionMode() {
        noteSelectionMode = false
        selectedNotes = emptySet()
    }

    fun exitLedgerSelectionMode() {
        ledgerSelectionMode = false
        selectedLedgers = emptySet()
    }

    fun exitAlarmSelectionMode() {
        alarmSelectionMode = false
        selectedAlarms = emptySet()
    }

    fun batchDeleteNotes() {
        selectedNotes.forEach { note ->
            noteViewModel.deleteNote(note)
        }
        exitNoteSelectionMode()
    }

    fun batchDeleteLedgers() {
        selectedLedgers.forEach { ledger ->
            ledgerViewModel.deleteLedger(ledger)
        }
        exitLedgerSelectionMode()
    }

    fun batchDeleteAlarms() {
        selectedAlarms.forEach { alarm ->
            AlarmHelper.cancelAlarm(context, alarm.id)
            alarmViewModel.deleteAlarm(alarm)
        }
        exitAlarmSelectionMode()
    }

    fun toggleSelectAllNotes() {
        selectedNotes = if (selectedNotes.size == notes.size) {
            emptySet()
        } else {
            notes.toSet()
        }
    }

    fun toggleSelectAllLedgers() {
        selectedLedgers = if (selectedLedgers.size == allLedgers.size) {
            emptySet()
        } else {
            allLedgers.toSet()
        }
    }

    fun toggleSelectAllAlarms() {
        selectedAlarms = if (selectedAlarms.size == allAlarms.size) {
            emptySet()
        } else {
            allAlarms.toSet()
        }
    }

    fun handleFABAddClick() {
        when (currentRoute) {
            Screen.NoteList.route -> {
                currentEditNote = null
                navController.navigate(Screen.NoteEditor.route)
            }
            Screen.LedgerList.route -> {
                showAddLedgerDialog = true
            }
            Screen.AlarmList.route -> {
                currentEditAlarm = null
                navController.navigate(Screen.AlarmEditor.route)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.NoteList.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    bottom = if (isInListPage) 140.dp else 0.dp
                )
        ) {
            composable(Screen.NoteList.route) {
                NoteListContent(
                    onShowAboutDialog = { showAboutDialog = true },
                    showAboutDialog = showAboutDialog,
                    onDismissAboutDialog = { showAboutDialog = false },
                    showSettingsDialog = showSettingsDialog,
                    onShowSettingsDialog = { showSettingsDialog = true },
                    onDismissSettingsDialog = { showSettingsDialog = false },
                    notes = notes,
                    onNoteClick = { note ->
                        if (noteSelectionMode) {
                            selectedNotes = if (note in selectedNotes) {
                                selectedNotes - note
                            } else {
                                selectedNotes + note
                            }
                        } else {
                            currentEditNote = note
                            navController.navigate(Screen.NoteEditor.route)
                        }
                    },
                    onNoteLongClick = { note ->
                        if (!noteSelectionMode) {
                            noteSelectionMode = true
                            selectedNotes = setOf(note)
                        }
                    },
                    isSelectionMode = noteSelectionMode,
                    selectedNotes = selectedNotes,
                    onDeleteClick = { note ->
                        noteViewModel.deleteNote(note)
                    },
                    onExitSelectionMode = { exitNoteSelectionMode() },
                    searchQuery = searchQuery,
                    isSearching = isSearching,
                    onSearchQueryChange = { query ->
                        noteViewModel.setSearchQuery(query)
                    },
                    onClearSearch = {
                        noteViewModel.clearSearch()
                    },
                    onModelConfigChange = { newApiUrl, newModel, newApiKey ->
                        AppConfig.saveConfig(newApiUrl, newModel, newApiKey)
                    },
                    onMemoryClick = {
                        showSettingsDialog = false
                        navController.navigate(Screen.MemoryList.route)
                    },
                    onAutoControlClick = {
                        showSettingsDialog = false
                        showAutoControlSettings = true
                    },
                    onShowWatchRecord = {
                        // 打开观影记录入口弹窗（未观看/已看 二选一）
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.ENTRY
                    }
                )
            }

            composable(Screen.NoteEditor.route) {
                NoteEditorScreen(
                    note = currentEditNote,
                    onBack = { navController.popBackStack() },
                    onSave = { title, content ->
                        val noteToSave = if (currentEditNote != null) {
                            currentEditNote!!.copy(
                                title = title,
                                content = content,
                                timestamp = System.currentTimeMillis()
                            )
                        } else {
                            Note(title = title, content = content)
                        }
                        noteViewModel.addNote(noteToSave)
                    }
                )
            }

            composable(Screen.LedgerList.route) {
                LedgerListContent(
                    ledgers = allLedgers,
                    viewModel = ledgerViewModel,
                    onLedgerClick = { ledger ->
                        if (ledgerSelectionMode) {
                            selectedLedgers = if (ledger in selectedLedgers) {
                                selectedLedgers - ledger
                            } else {
                                selectedLedgers + ledger
                            }
                        } else {
                            currentLedger = ledger
                            navController.navigate(Screen.LedgerDetail.createRoute(ledger.id))
                        }
                    },
                    onLedgerLongClick = { ledger ->
                        if (!ledgerSelectionMode) {
                            ledgerSelectionMode = true
                            selectedLedgers = setOf(ledger)
                        }
                    },
                    isSelectionMode = ledgerSelectionMode,
                    selectedLedgers = selectedLedgers,
                    onExitSelectionMode = { exitLedgerSelectionMode() },
                    // 顶部三点菜单：复用笔记主界面的设置/观影记录入口
                    showSettingsDialog = showSettingsDialog,
                    onShowSettingsDialog = { showSettingsDialog = true },
                    onDismissSettingsDialog = { showSettingsDialog = false },
                    onModelConfigChange = { newApiUrl, newModel, newApiKey ->
                        AppConfig.saveConfig(newApiUrl, newModel, newApiKey)
                    },
                    onMemoryClick = {
                        showSettingsDialog = false
                        navController.navigate(Screen.MemoryList.route)
                    },
                    onAutoControlClick = {
                        showSettingsDialog = false
                        showAutoControlSettings = true
                    },
                    onShowWatchRecord = {
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.ENTRY
                    }
                )
            }

            composable(Screen.LedgerDetail.route) { backStackEntry ->
                val ledgerId = backStackEntry.arguments?.getString("ledgerId")?.toLongOrNull()
                val ledger = allLedgers.find { it.id == ledgerId } ?: currentLedger
                if (ledger != null) {
                    LedgerDetailScreen(
                        ledger = ledger,
                        viewModel = ledgerViewModel,
                        onBack = { navController.popBackStack() },
                        onAddTransaction = {
                            currentLedger = ledger
                            showAddTransactionSheet = true
                        },
                        onBatchDelete = { transactions ->
                            transactions.forEach { transaction ->
                                ledgerViewModel.deleteTransaction(transaction)
                            }
                        }
                    )
                }
            }

            composable(Screen.MemoryList.route) {
                MemoryScreen(
                    viewModel = memoryViewModel,
                    onBack = {
                        navController.popBackStack()
                        showSettingsDialog = true
                    }
                )
            }

            composable(Screen.AlarmList.route) {
                AlarmListContent(
                    alarms = allAlarms,
                    isSelectionMode = alarmSelectionMode,
                    selectedAlarms = selectedAlarms,
                    onAlarmClick = { alarm ->
                        if (alarmSelectionMode) {
                            selectedAlarms = if (alarm in selectedAlarms) {
                                selectedAlarms - alarm
                            } else {
                                selectedAlarms + alarm
                            }
                        } else {
                            currentEditAlarm = alarm
                            navController.navigate(Screen.AlarmEditor.route)
                        }
                    },
                    onAlarmLongClick = { alarm ->
                        if (!alarmSelectionMode) {
                            alarmSelectionMode = true
                            selectedAlarms = setOf(alarm)
                        }
                    },
                    onAlarmToggle = { alarm, enabled ->
                        alarmViewModel.toggleAlarmEnabled(alarm, enabled)
                        val updated = alarm.copy(isEnabled = enabled)
                        if (enabled) {
                            AlarmHelper.setAlarm(context, updated)
                        } else {
                            AlarmHelper.cancelAlarm(context, alarm.id)
                        }
                    },
                    onExitSelectionMode = { exitAlarmSelectionMode() },
                    // 顶部三点菜单：复用笔记主界面的设置/观影记录入口
                    showSettingsDialog = showSettingsDialog,
                    onShowSettingsDialog = { showSettingsDialog = true },
                    onDismissSettingsDialog = { showSettingsDialog = false },
                    onModelConfigChange = { newApiUrl, newModel, newApiKey ->
                        AppConfig.saveConfig(newApiUrl, newModel, newApiKey)
                    },
                    onMemoryClick = {
                        showSettingsDialog = false
                        navController.navigate(Screen.MemoryList.route)
                    },
                    onAutoControlClick = {
                        showSettingsDialog = false
                        showAutoControlSettings = true
                    },
                    onShowWatchRecord = {
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.ENTRY
                    }
                )
            }

            composable(Screen.AlarmEditor.route) {
                AlarmEditorScreen(
                    alarm = currentEditAlarm,
                    onBack = { navController.popBackStack() },
                    onSave = { alarmToSave ->
                        alarmViewModel.addAlarm(alarmToSave) { insertedId ->
                            val savedWithId = alarmToSave.copy(id = insertedId)
                            AlarmHelper.setAlarm(context, savedWithId)
                        }
                    }
                )
            }

            composable(Screen.Browser.route) { backStackEntry ->
                val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                val targetUrl = try {
                    java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                } catch (e: Exception) {
                    encodedUrl
                }
                BrowserScreen(
                    initialUrl = targetUrl,
                    onClose = { navController.popBackStack() }
                )
            }
        }

        if (isInListPage) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                if (!isInSelectionMode && !isSearching) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FloatingActionButton(
                            onClick = { showAIChat = true },
                            containerColor = Color(0xFFE91E63)//AI按钮框
                        ) {
                            Text("AI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }

                        FloatingActionButton(
                            onClick = { handleFABAddClick() },
                            containerColor = Color(0xFF2962FF)//新增按钮框
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isInSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    NavigationBar(//导航栏
                        containerColor = Color.White,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "笔记"
                                )
                            },
                            label = { Text("笔记", fontSize = 12.sp) },
                            selected = currentRoute == Screen.NoteList.route,
                            onClick = {
                                if (currentRoute != Screen.NoteList.route) {
                                    exitLedgerSelectionMode()
                                    navController.navigate(Screen.NoteList.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "账本"
                                )
                            },
                            label = { Text("账本", fontSize = 12.sp) },
                            selected = currentRoute == Screen.LedgerList.route,
                            onClick = {
                                if (currentRoute != Screen.LedgerList.route) {
                                    exitNoteSelectionMode()
                                    exitAlarmSelectionMode()
                                    navController.navigate(Screen.LedgerList.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = "闹钟"
                                )
                            },
                            label = { Text("闹钟", fontSize = 12.sp) },
                            selected = currentRoute == Screen.AlarmList.route,
                            onClick = {
                                if (currentRoute != Screen.AlarmList.route) {
                                    exitNoteSelectionMode()
                                    exitLedgerSelectionMode()
                                    navController.navigate(Screen.AlarmList.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }

                AnimatedVisibility(//底部操作栏，全选删除
                    visible = isInSelectionMode,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    Surface(
                        color = Color.White,
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    when (currentRoute) {
                                        Screen.NoteList.route -> toggleSelectAllNotes()
                                        Screen.LedgerList.route -> toggleSelectAllLedgers()
                                        Screen.AlarmList.route -> toggleSelectAllAlarms()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Select All",
                                    tint = Color(0xFF2962FF)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val isAllSelected = when (currentRoute) {
                                    Screen.NoteList.route -> selectedNotes.size == notes.size
                                    Screen.LedgerList.route -> selectedLedgers.size == allLedgers.size
                                    Screen.AlarmList.route -> selectedAlarms.size == allAlarms.size
                                    else -> false
                                }
                                Text(
                                    text = if (isAllSelected) "取消全选" else "全选",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2962FF)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable(
                                    onClick = {
                                        if (!isDeleting) {
                                            isDeleting = true
                                            when (currentRoute) {
                                                Screen.NoteList.route -> batchDeleteNotes()
                                                Screen.LedgerList.route -> batchDeleteLedgers()
                                                Screen.AlarmList.route -> batchDeleteAlarms()
                                            }
                                            isDeleting = false
                                        }
                                    }
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.Red
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "删除",
                                    fontSize = 12.sp,
                                    color = Color.Red
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAIChat) {
            AIChatDialog(
                onDismiss = { showAIChat = false },
                onAddNote = { note ->
                    noteViewModel.addNote(note)
                },
                onOpenBrowser = { url ->
                    // 关闭对话框并跳转到内置浏览器
                    showAIChat = false
                    navController.navigate(Screen.Browser.createRoute(url))
                }
            )
        }

        // 观影记录流程弹窗（统一 state 控制步骤，避免弹窗互相覆盖）
        when (watchRecordStep) {
            com.example.memo.ui.movie.WatchRecordStep.ENTRY -> {
                WatchRecordDialog(
                    onDismiss = { watchRecordStep = null },
                    onSelectPending = {
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.PENDING
                    },
                    onSelectWatched = {
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.WATCHED
                    }
                )
            }
            com.example.memo.ui.movie.WatchRecordStep.PENDING,
            com.example.memo.ui.movie.WatchRecordStep.WATCHED -> {
                val listType = watchRecordStep!!
                val movieTitles: List<String>
                val tvTitles: List<String>
                val allTitles: List<String>
                if (listType == com.example.memo.ui.movie.WatchRecordStep.PENDING) {
                    // 待看：电影/电视剧分开，用于分类切换
                    movieTitles = WatchListManager.getPendingList(context, WatchListManager.WatchType.MOVIE)
                    tvTitles = WatchListManager.getPendingList(context, WatchListManager.WatchType.TV)
                    allTitles = emptyList()
                } else {
                    // 已看：混合展示全部，无需分类
                    movieTitles = emptyList()
                    tvTitles = emptyList()
                    allTitles = WatchListManager.getWatchedList(context, WatchListManager.WatchType.ALL)
                }
                WatchRecordListDialog(
                    type = listType,
                    movieTitles = movieTitles,
                    tvTitles = tvTitles,
                    allTitles = allTitles,
                    onBack = {
                        // 返回入口弹窗：state 改回 ENTRY，列表弹窗消失，入口弹窗重新出现
                        watchRecordStep = com.example.memo.ui.movie.WatchRecordStep.ENTRY
                    },
                    onPlayMovie = { title, category ->
                        // 关闭弹窗，根据分类更新已看时间戳，跳转浏览器
                        watchRecordStep = null
                        val watchType = if (category == com.example.memo.ui.movie.WatchMovieCategory.MOVIE) {
                            WatchListManager.WatchType.MOVIE
                        } else {
                            WatchListManager.WatchType.TV
                        }
                        // PENDING 卡片：从待看移除 + 加入已看 + 加时间戳
                        // WATCHED 卡片：仅更新已看时间戳（updateWatchedTimestamp 内部已包含这两步）
                        WatchListManager.updateWatchedTimestamp(
                            context, title, watchType,
                            System.currentTimeMillis()
                        )
                        scope.launch {
                            val url = buildVideoSearchUrl(title)
                            if (url != null) {
                                navController.navigate(Screen.Browser.createRoute(url))
                            }
                        }
                    }
                )
            }
            null -> { /* 未打开，不渲染 */ }
        }

        if (showAddLedgerDialog) {
            AddLedgerDialog(
                onDismiss = { showAddLedgerDialog = false },
                onConfirm = { ledger ->
                    ledgerViewModel.addLedger(ledger)
                    showAddLedgerDialog = false
                }
            )
        }

        if (showAddTransactionSheet && currentLedger != null) {
            ModalBottomSheet(
                onDismissRequest = { showAddTransactionSheet = false },
                sheetState = addTransactionSheetState
            ) {
                AddTransactionBottomSheet(
                    onDismiss = { showAddTransactionSheet = false },
                    onConfirm = { transaction ->
                        ledgerViewModel.addTransaction(transaction)
                        showAddTransactionSheet = false
                    },
                    ledgerId = currentLedger!!.id
                )
            }
        }

        if (showAppPermissionGuide) {
            AlertDialog(
                onDismissRequest = { showAppPermissionGuide = false },
                title = {
                    Text(
                        text = "读取应用列表权限",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = "为了让 AI 助手能帮您打开手机上的应用（如抖音、微信等），需要读取应用列表的权限。\n\n请在接下来的设置中开启「所有文件访问」或「查询所有应用」权限。",
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAppPermissionGuide = false
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("去设置", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAppPermissionGuide = false }) {
                        Text("以后再说", fontSize = 16.sp)
                    }
                }
            )
        }

        if (showAutoControlSettings) {
            val isAgentMode by com.example.memo.agent.AgentConfig.isAgentModeFlow(context)
                .collectAsState(initial = false)
            AutoControlSettingsDialog(
                visible = showAutoControlSettings,
                onDismiss = {
                    showAutoControlSettings = false
                    showSettingsDialog = true
                },
                isAgentMode = isAgentMode
            )
        }
    }
}
