package com.example.memo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import com.example.memo.viewModel.LedgerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerDetailScreen(
    ledger: Ledger,
    viewModel: LedgerViewModel,
    onBack: () -> Unit,
    onAddTransaction: () -> Unit,
    onBatchDelete: (List<Transaction>) -> Unit
) {
    val transactions by remember(ledger.id) {
        viewModel.getTransactionsByLedgerId(ledger.id)
    }.collectAsState(initial = emptyList())

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTransactions by remember { mutableStateOf<Set<Transaction>>(emptySet()) }
    var isDeleting by remember { mutableStateOf(false) }

    val currentMonthStart = viewModel.getCurrentMonthStart()
    val currentMonthEnd = viewModel.getCurrentMonthEnd()
    val lastMonthStart = viewModel.getLastMonthStart()
    val lastMonthEnd = viewModel.getLastMonthEnd()
    val currentYearStart = viewModel.getCurrentYearStart()
    val currentYearEnd = viewModel.getCurrentYearEnd()

    val currentMonthTransactions = viewModel.filterTransactionsByTimeRange(transactions, currentMonthStart, currentMonthEnd)
    val lastMonthTransactions = viewModel.filterTransactionsByTimeRange(transactions, lastMonthStart, lastMonthEnd)
    val currentYearTransactions = viewModel.filterTransactionsByTimeRange(transactions, currentYearStart, currentYearEnd)

    val currentMonthExpense = viewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_EXPENSE)
    val currentMonthIncome = viewModel.calculateTotal(currentMonthTransactions, Transaction.TYPE_INCOME)
    val lastMonthExpense = viewModel.calculateTotal(lastMonthTransactions, Transaction.TYPE_EXPENSE)
    val lastMonthIncome = viewModel.calculateTotal(lastMonthTransactions, Transaction.TYPE_INCOME)
    val currentYearExpense = viewModel.calculateTotal(currentYearTransactions, Transaction.TYPE_EXPENSE)
    val currentYearIncome = viewModel.calculateTotal(currentYearTransactions, Transaction.TYPE_INCOME)

    fun enterSelectionMode(transaction: Transaction) {
        isSelectionMode = true
        selectedTransactions = setOf(transaction)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedTransactions = emptySet()
    }

    fun toggleTransactionSelection(transaction: Transaction) {
        selectedTransactions = if (transaction in selectedTransactions) {
            selectedTransactions - transaction
        } else {
            selectedTransactions + transaction
        }
    }

    fun toggleSelectAll() {
        selectedTransactions = if (selectedTransactions.size == transactions.size) {
            emptySet()
        } else {
            transactions.toSet()
        }
    }

    fun batchDelete() {
        onBatchDelete(selectedTransactions.toList())
        exitSelectionMode()
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("已选择 ${selectedTransactions.size} 项", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { exitSelectionMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = { exitSelectionMode() }) {
                            Text("取消", color = Color(0xFF2962FF), fontSize = 16.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            } else {
                TopAppBar(
                    title = { Text(ledger.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FloatingActionButton(
                        onClick = onAddTransaction,
                        containerColor = Color(0xFF2962FF)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isSelectionMode,
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
                            modifier = Modifier.clickable { toggleSelectAll() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Select All",
                                tint = Color(0xFF2962FF)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedTransactions.size == transactions.size) "取消全选" else "全选",
                                fontSize = 12.sp,
                                color = Color(0xFF2962FF)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(
                                enabled = !isDeleting,
                                onClick = {
                                    if (!isDeleting) {
                                        isDeleting = true
                                        batchDelete()
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsRow(
                    label = "本月",
                    income = currentMonthIncome,
                    expense = currentMonthExpense,
                    unit = ledger.unit
                )
                Divider(color = Color(0xFFEEEEEE))
                StatsRow(
                    label = "上月",
                    income = lastMonthIncome,
                    expense = lastMonthExpense,
                    unit = ledger.unit
                )
                Divider(color = Color(0xFFEEEEEE))
                StatsRow(
                    label = "本年度",
                    income = currentYearIncome,
                    expense = currentYearExpense,
                    unit = ledger.unit
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "记账流水",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions) { transaction ->
                    TransactionCard(
                        transaction = transaction,
                        unit = ledger.unit,
                        isSelected = transaction in selectedTransactions,
                        isSelectionMode = isSelectionMode,
                        onClick = {
                            if (isSelectionMode) {
                                toggleTransactionSelection(transaction)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                enterSelectionMode(transaction)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun StatsRow(
    label: String,
    income: Double,
    expense: Double,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Row {
                Text(
                    text = "收入 ",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "+${income}$unit",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = "支出 ",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    text = "-${expense}$unit",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
fun TransactionCard(
    transaction: Transaction,
    unit: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MM-dd", Locale.CHINA)
    val dateStr = dateFormat.format(Date(transaction.timestamp))

    val isIncome = transaction.type == Transaction.TYPE_INCOME
    val amountColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
    val amountPrefix = if (isIncome) "+" else "-"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
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
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dateStr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                if (transaction.note.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = transaction.note,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$amountPrefix${transaction.amount}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 14.sp,
                    color = amountColor,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}
