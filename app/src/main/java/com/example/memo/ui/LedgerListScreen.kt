package com.example.memo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Ledger
import com.example.memo.data.Transaction
import com.example.memo.ui.components.SettingsDialog
import com.example.memo.viewModel.LedgerViewModel

val ledgerCardColors = listOf(
    Color(0xFFFF6B6B),
    Color(0xFF4ECDC4),
    Color(0xFF45B7D1),
    Color(0xFF96CEB4),
    Color(0xFFFFEAA7),
    Color(0xFFDDA0DD),
    Color(0xFF98D8C8),
    Color(0xFFF7DC6F),
    Color(0xFFBB8FCE),
    Color(0xFF85C1E9)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerListContent(
    ledgers: List<Ledger>,
    viewModel: LedgerViewModel,
    onLedgerClick: (Ledger) -> Unit,
    onLedgerLongClick: (Ledger) -> Unit,
    isSelectionMode: Boolean,
    selectedLedgers: Set<Ledger>,
    onExitSelectionMode: () -> Unit,
    // 顶部右上角三点菜单（与笔记主界面一致：设置、观影记录）
    showSettingsDialog: Boolean = false,
    onShowSettingsDialog: () -> Unit = {},
    onDismissSettingsDialog: () -> Unit = {},
    onModelConfigChange: (apiUrl: String, model: String, apiKey: String) -> Unit = { _, _, _ -> },
    onMemoryClick: () -> Unit = {},
    onAutoControlClick: () -> Unit = {},
    onShowWatchRecord: () -> Unit = {}
) {
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
                title = { Text("已选择 ${selectedLedgers.size} 项", fontWeight = FontWeight.Bold) },
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
                    // 状态栏留白：和笔记主界面一致，避免左上角文字被状态栏遮挡
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "我的账本",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
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
                    text = "${ledgers.size} 个账本",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
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
                items(ledgers) { ledger ->
                    LedgerCard(
                        ledger = ledger,
                        viewModel = viewModel,
                        isSelected = ledger in selectedLedgers,
                        isSelectionMode = isSelectionMode,
                        onClick = { onLedgerClick(ledger) },
                        onLongClick = { onLedgerLongClick(ledger) }
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerCard(
    ledger: Ledger,
    viewModel: LedgerViewModel,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val transactions by remember(ledger.id) {
        viewModel.getTransactionsByLedgerId(ledger.id)
    }.collectAsState(initial = emptyList())

    val currentMonthStart = viewModel.getCurrentMonthStart()
    val currentMonthEnd = viewModel.getCurrentMonthEnd()

    val currentMonthTransactions = viewModel.filterTransactionsByTimeRange(transactions, currentMonthStart, currentMonthEnd)

    val currentMonthExpense = viewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_EXPENSE)
    val currentMonthIncome = viewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_INCOME)
    val currentMonthTotal = currentMonthExpense + currentMonthIncome

    val cardColor = ledgerCardColors[ledger.color % ledgerCardColors.size]

    // 格式化数字：整数显示整数，小数显示小数
    fun formatAmount(amount: Double): String {
        return if (amount == amount.toLong().toDouble()) {
            amount.toLong().toString()
        } else {
            amount.toString()
        }
    }

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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ledger.title.take(1),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = ledger.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "本月共 ${formatAmount(currentMonthTotal)}${ledger.unit}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
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
            } else {
                Text(
                    text = ledger.unit,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
