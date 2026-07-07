package com.example.memo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memo.data.Ledger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLedgerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Ledger) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("元") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建账本",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("账本名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("单位（默认：元）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            Ledger(
                                title = title.trim(),
                                unit = unit.ifBlank { "元" },
                                color = (Math.random() * 10).toInt()
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("确认")
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
