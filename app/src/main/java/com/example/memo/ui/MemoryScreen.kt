package com.example.memo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Memory
import com.example.memo.viewModel.MemoryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onBack: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val allMemories by viewModel.allMemories.collectAsState(initial = emptyList())
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    val memories = if (isSearching) {
        viewModel.searchMemories(allMemories, searchQuery)
    } else {
        allMemories
    }

    var selectionMode by remember { mutableStateOf(false) }
    var selectedMemories by remember { mutableStateOf<Set<Memory>>(emptySet()) }

    var showActionDialog by remember { mutableStateOf(false) }
    var longPressedMemory by remember { mutableStateOf<Memory?>(null) }

    var showTagManagerDialog by remember { mutableStateOf(false) }
    var tagEditMemory by remember { mutableStateOf<Memory?>(null) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteMemory by remember { mutableStateOf<Memory?>(null) }

    fun exitSelectionMode() {
        selectionMode = false
        selectedMemories = emptySet()
    }

    fun toggleSelectAll() {
        selectedMemories = if (selectedMemories.size == memories.size) {
            emptySet()
        } else {
            memories.toSet()
        }
    }

    fun batchDelete() {
        selectedMemories.forEach { memory ->
            viewModel.deleteMemory(memory)
        }
        exitSelectionMode()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        if (selectionMode) {
            TopAppBar(
                title = { Text("已选择 ${selectedMemories.size} 项", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { exitSelectionMode() }) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    TextButton(onClick = { toggleSelectAll() }) {
                        Text(
                            if (selectedMemories.size == memories.size) "取消全选" else "全选",
                            color = Color(0xFF2962FF),
                            fontSize = 16.sp
                        )
                    }
                    TextButton(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = selectedMemories.isNotEmpty()
                    ) {
                        Text("删除", color = Color.Red, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        } else {
            TopAppBar(
                title = { Text("模型记忆", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }

        Column(
            modifier = Modifier.background(Color.White)
        ) {
            if (!selectionMode) {
                Text(
                    text = "${allMemories.size} 条记忆",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .heightIn(min = 48.dp),
                    placeholder = {
                        Text(
                            text = "搜索记忆、标签",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    },
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                viewModel.clearSearch()
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedContainerColor = Color(0xFFF5F5F5)
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (memories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isSearching) "未找到相关记忆" else "暂无记忆",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AI会自动记住重要信息",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(memories) { memory ->
                        MemoryCard(
                            memory = memory,
                            isSelected = memory in selectedMemories,
                            isSelectionMode = selectionMode,
                            onClick = {
                                if (selectionMode) {
                                    selectedMemories = if (memory in selectedMemories) {
                                        selectedMemories - memory
                                    } else {
                                        selectedMemories + memory
                                    }
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    longPressedMemory = memory
                                    showActionDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showActionDialog && longPressedMemory != null) {
        AlertDialog(
            onDismissRequest = {
                showActionDialog = false
                longPressedMemory = null
            },
            title = { Text(longPressedMemory!!.title, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showActionDialog = false
                            deleteMemory = longPressedMemory
                            showDeleteConfirmDialog = true
                            longPressedMemory = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("删除", color = Color.Red, fontSize = 16.sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            showActionDialog = false
                            selectionMode = true
                            selectedMemories = setOf(longPressedMemory!!)
                            longPressedMemory = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckBox, contentDescription = null, tint = Color(0xFF2962FF))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("批量删除", color = Color(0xFF2962FF), fontSize = 16.sp)
                        }
                    }
                    TextButton(
                        onClick = {
                            showActionDialog = false
                            tagEditMemory = longPressedMemory
                            showTagManagerDialog = true
                            longPressedMemory = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, tint = Color(0xFF2962FF))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("标签管理", color = Color(0xFF2962FF), fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showActionDialog = false
                    longPressedMemory = null
                }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTagManagerDialog && tagEditMemory != null) {
        TagManagerDialog(
            memory = tagEditMemory!!,
            onDismiss = {
                showTagManagerDialog = false
                tagEditMemory = null
            },
            onConfirm = { newTags ->
                viewModel.updateMemoryTags(tagEditMemory!!, newTags)
                showTagManagerDialog = false
                tagEditMemory = null
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                deleteMemory = null
            },
            title = { Text("确认删除", fontWeight = FontWeight.Bold) },
            text = {
                if (deleteMemory != null) {
                    Text("确定要删除记忆\"${deleteMemory!!.title}\"吗？")
                } else {
                    Text("确定要删除选中的 ${selectedMemories.size} 条记忆吗？")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deleteMemory != null) {
                            viewModel.deleteMemory(deleteMemory!!)
                            deleteMemory = null
                        } else {
                            batchDelete()
                        }
                        showDeleteConfirmDialog = false
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    deleteMemory = null
                }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)
    val dateStr = dateFormat.format(Date(memory.timestamp))

    val tags = memory.tags.split("#").filter { it.isNotBlank() }

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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = memory.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                if (isSelectionMode) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF2962FF) else Color.Transparent),
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
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = memory.content,
                fontSize = 14.sp,
                color = Color.Black,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = dateStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (tags.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.take(3).forEach { tag ->
                            Surface(
                                color = Color(0xFFE8F0FE),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 12.sp,
                                    color = Color(0xFF2962FF),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TagManagerDialog(
    memory: Memory,
    onDismiss: () -> Unit,
    onConfirm: (newTags: String) -> Unit
) {
    var currentTagsText by remember { mutableStateOf(memory.tags) }
    var newTagInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun addTag() {
        val trimmedTag = newTagInput.trim()
        if (trimmedTag.isNotBlank()) {
            val cleanTag = trimmedTag.removePrefix("#").trim()
            if (cleanTag.isNotBlank()) {
                val existingTags = currentTagsText.split("#").filter { it.isNotBlank() }
                if (cleanTag !in existingTags) {
                    currentTagsText = if (currentTagsText.isBlank()) {
                        "#$cleanTag"
                    } else {
                        "$currentTagsText #$cleanTag"
                    }
                }
                newTagInput = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = currentTagsText,
                    onValueChange = { currentTagsText = it },
                    label = { Text("当前标签") },
                    placeholder = { Text("例如：#大门 #密码 #家庭") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )

                OutlinedTextField(
                    value = newTagInput,
                    onValueChange = { newTagInput = it },
                    label = { Text("添加新标签") },
                    placeholder = { Text("输入标签后按回车添加") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            addTag()
                            keyboardController?.hide()
                        }
                    ),
                    trailingIcon = {
                        if (newTagInput.isNotBlank()) {
                            IconButton(onClick = { addTag() }) {
                                Icon(Icons.Default.Add, contentDescription = "添加标签")
                            }
                        }
                    }
                )

                Text(
                    text = "提示：标签以 # 开头，多个标签用空格分隔",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(currentTagsText.trim())
            }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
