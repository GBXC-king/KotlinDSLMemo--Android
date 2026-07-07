package com.example.memo.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.memo.data.AppConfig
import com.example.memo.data.Note
import com.example.memo.ui.components.HighlightText
import com.example.memo.ui.components.AutoControlSettingsDialog
import com.example.memo.ui.components.SettingsDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListContent(
    onShowAboutDialog: () -> Unit,
    showAboutDialog: Boolean,
    onDismissAboutDialog: () -> Unit,
    showSettingsDialog: Boolean,
    onShowSettingsDialog: () -> Unit,
    onDismissSettingsDialog: () -> Unit,
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
    isSelectionMode: Boolean,
    selectedNotes: Set<Note>,
    onDeleteClick: (Note) -> Unit,
    onExitSelectionMode: () -> Unit,
    searchQuery: String,
    isSearching: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onModelConfigChange: (apiUrl: String, model: String, apiKey: String) -> Unit,
    onMemoryClick: () -> Unit = {},
    onAutoControlClick: () -> Unit = {},
    onShowWatchRecord: () -> Unit = {}
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = onDismissAboutDialog,
            title = { Text("制作人员", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("开发者：皖西学院304的king")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("版本：7.0.1")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("© 2026 所有雷霆权利保留")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("这是一个基于 Kotlin 和 Jetpack Compose 开发的备忘录应用")
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissAboutDialog) {
                    Text("确定")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

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
                title = { Text("已选择 ${selectedNotes.size} 项", fontWeight = FontWeight.Bold) },
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
                    // 状态栏留白：和账本/闹钟主界面一致，避免左上角文字被状态栏遮挡
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部笔记",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
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
                    text = "${notes.size} 条笔记",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                SearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onSearch = { keyboardController?.hide() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = "搜索笔记",
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                onClearSearch()
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
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
                if (isSearching && searchQuery.isNotEmpty()) {
                    item {
                        Text(
                            text = "找到 ${notes.size} 条笔记",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                items(notes) { note ->
                    NoteCard(
                        note = note,
                        isSelected = note in selectedNotes,
                        isSelectionMode = isSelectionMode,
                        searchQuery = if (isSearching) searchQuery else "",
                        onClick = { onNoteClick(note) },
                        onLongClick = { onNoteLongClick(note) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索",
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = Color.Gray,
                fontSize = 16.sp
            )
        },
        textStyle = LocalTextStyle.current.copy(
            fontSize = 16.sp
        ),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),

        shape = RoundedCornerShape(24.dp),

        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}

@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    searchQuery: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("M月d日", Locale.CHINA)
    val dateStr = dateFormat.format(Date(note.timestamp))

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
                HighlightText(
                    text = if (note.title.isEmpty()) "无标题" else note.title,
                    highlightQuery = searchQuery,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))

                val contentPreview = "${dateStr} | ${note.content.take(20)}${if (note.content.length > 20) "..." else ""}"
                HighlightText(
                    text = contentPreview,
                    highlightQuery = searchQuery,
                    fontSize = 14.sp,
                    maxLines = 2
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
            }
        }
    }
}
