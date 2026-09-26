package com.pesaflow.app.ui.recurring

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.analytics.*
import com.pesaflow.app.ui.theme.PesaWarningContainer

@Composable
fun RecurringScreen(
    transactions: List<Transaction>
) {
    val preview = remember(transactions) {
        buildRecurringPreview(transactions)
    }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Text("🔄 Recurring Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${preview.count} patterns · KSh ${preview.totalMonthlyCommitment.toInt()}/mo total", style = MaterialTheme.typography.bodySmall)
        }
        items(preview.patterns) { pattern ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pattern.confidence < 0.5) PesaWarningContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(pattern.merchant, fontWeight = FontWeight.Bold)
                        Text("Confidence: ${"%.0f".format(pattern.confidence * 100)}%")
                    }
                    Text(pattern.suggestion)
                    Text("KSh ${pattern.amount.toInt()} · every ${pattern.medianIntervalDays}d · ${pattern.occurrences}x")
                    Text("Next expected: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date(pattern.nextExpectedDate))}")
                    if (pattern.variance > 0.3) {
                        Text("⚠️ Irregular pattern — verify", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (preview.atRisk.isNotEmpty()) {
            item {
                Text("⚠️ Irregular patterns to verify", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            items(preview.atRisk) { pattern ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = PesaWarningContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("⚠️ ${pattern.merchant} — ${pattern.occurrences}x with high variance", fontWeight = FontWeight.Bold)
                        Text("KSh ${pattern.amount.toInt()} · every ${pattern.medianIntervalDays}d")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
