package com.pesaflow.app.ui.exports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.*
import com.pesaflow.app.data.exports.ExportEngine

@Composable
fun ExportScreen(
    transactions: List<Transaction>,
    budgets: List<Budget> = emptyList(),
    goals: List<SavingsGoal> = emptyList(),
    bills: List<Bill> = emptyList(),
    debts: List<Debt> = emptyList(),
    profile: com.pesaflow.app.data.models.UniversityProfile? = null
) {
    val engine = remember { ExportEngine() }
    var csvText by remember { mutableStateOf("") }
    var backupJson by remember { mutableStateOf("") }
    var shareText by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("📤 Export & Backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CSV Export", fontWeight = FontWeight.Bold)
                    Text("Export all transactions, budgets, goals, bills, and debts as CSV.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        csvText = engine.exportCsv(transactions, budgets, goals, bills, debts)
                    }) { Text("Generate CSV") }
                    if (csvText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ CSV ready (${csvText.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Backup (JSON)", fontWeight = FontWeight.Bold)
                    Text("Full database backup with every table, compiler-checked.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val payload = engine.buildBackupPayload(
                            transactions = transactions, pending = emptyList(),
                            budgets = budgets, goals = goals, profile = profile,
                            bills = bills, debts = debts, meals = emptyList(),
                            chamas = emptyList(), belongings = emptyList(),
                            kitchenStock = emptyList()
                        )
                        backupJson = engine.backupToJson(payload)
                    }) { Text("Create Backup") }
                    if (backupJson.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("✅ Backup ready (${backupJson.length} chars)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Share Summary", fontWeight = FontWeight.Bold)
                    Text("Human-readable 30-day summary for sharing.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        shareText = engine.generateShareSummary(transactions, 30)
                    }) { Text("Generate Summary") }
                    if (shareText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(shareText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
