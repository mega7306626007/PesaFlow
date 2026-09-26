package com.pesaflow.app.ui.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.analytics.*

@Composable
fun AnalyticsScreen(
    transactions: List<Transaction>,
    periodDays: Int = 30
) {
    val report = remember(transactions, periodDays) {
        buildAnalyticsReport(transactions, periodDays)
    }
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        item {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📊 ${periodDays}-Day Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total spent: KSh ${report.totalSpent.toInt()}")
                    Text("Total income: KSh ${report.totalIncome.toInt()}")
                    Text("Net flow: KSh ${report.netFlow.toInt()}")
                    Text("Savings rate: ${report.savingsRate}%")
                    Text("Daily average: KSh ${report.dailyAvg.toInt()}")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text("Top Category", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            report.categorySummaries.sortedByDescending { it.total }.take(5).forEach { cat ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${cat.category.label}: KSh ${cat.total.toInt()}")
                        Text("${cat.sharePercent}%")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text("Category Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            report.categorySummaries.filter { it.trendPercent != 0.0 }.take(5).forEach { cat ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${cat.category.label}: ${cat.trendPercent.toInt()}%")
                        Text(if (cat.trendPercent > 0) "📈" else "📉")
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text("Monthly Trends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            report.monthlyTrends.takeLast(6).forEach { month ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(month.monthId, fontWeight = FontWeight.Bold)
                        Text("Total: KSh ${month.total.toInt()}")
                        ExpenseCategory.entries.forEach { cat ->
                            val v = month.byCategory[cat] ?: 0.0
                            if (v > 0) {
                                Text("  ${cat.label}: KSh ${v.toInt()}")
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text("Spending Heatmap (${report.heatmap.weekLabels.size} weeks)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            report.heatmap.grid.forEach { week ->
                Row { week.forEach { v ->
                    Text(if (v > 500) "🔴" else if (v > 200) "🟡" else "🟢")
                } }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
        item {
            Text("💡 Insights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val growing = fastestGrowingCategory(report)
            if (growing != null) {
                Text("⚠️ ${growing.label} is growing fastest")
            }
            val over = overBudgetCategories(report)
            if (over.isNotEmpty()) {
                over.forEach { cat ->
                    Text("🔥 ${cat.category.label} exceeding budget")
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
