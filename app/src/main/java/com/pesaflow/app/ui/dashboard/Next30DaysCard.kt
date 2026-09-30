package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pesaflow.app.data.analytics.detectRecurring
import com.pesaflow.app.data.analytics.predictPaydays
import com.pesaflow.app.data.finance.projectCashFlow
import com.pesaflow.app.data.money.ledgerBalance
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.PpCard
import com.pesaflow.app.ui.theme.PpCardKind
import com.pesaflow.app.ui.theme.PpProgress
import com.pesaflow.app.ui.theme.PpProgressKind
import com.pesaflow.app.ui.theme.PpSectionHeader
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


// "Next 30 days": the app's eyes forward — paydays, open bills and monthly
// commitments projected against money held. Low point and broke date, not
// just rear-view stats.
@Composable
fun Next30DaysCard(
    transactions: List<Transaction>,
    bills: List<com.pesaflow.app.data.models.Bill>,
    hide: Boolean = false
) {
    val now = System.currentTimeMillis()
    val projection = remember(transactions, bills, now) {
        projectCashFlow(
            balance = ledgerBalance(transactions),
            now = now,
            horizonDays = 30,
            paydays = predictPaydays(transactions, now),
            bills = bills.filter { it.status != "PAID" },
            recurring = detectRecurring(transactions)
        )
    }
    val fmt = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val low = projection.lowest
    val lowDate = fmt.format(Date(low.dayStart))
    val lowText = if (hide) "••••" else "KSh ${low.balance.toInt()}"
    val maxBal = projection.days.maxOf { it.balance }.coerceAtLeast(1.0)

    PpCard(kind = PpCardKind.LARGE) {
        Column(verticalArrangement = Arrangement.spacedBy(ppSpacing.sm)) {
            PpSectionHeader(
                title = "Next 30 days 🔮",
                subtitle = "Paydays + bills + subscriptions vs KSh ${if (hide) "••••" else ledgerBalance(transactions).toInt()} held"
            )
            if (projection.brokeDate != null) {
                Text(
                    "Goes negative around ${fmt.format(Date(projection.brokeDate))} — low $lowText. Move money or delay spending. ⚠️",
                    style = ppTypography.bodyMedium,
                    color = ppColors.error
                )
            } else {
                Text(
                    "Stays positive — low $lowText around $lowDate.",
                    style = ppTypography.bodyMedium,
                    color = ppColors.success
                )
            }
            // Mini projection bars on canvas, today → day 30, normalized to the
            // peak. Red below the zero line, gold above.
            val zeroY = if (projection.days.any { it.balance < 0 }) {
                val minBal = projection.days.minOf { it.balance }
                (maxBal / (maxBal - minBal)).toFloat() * 96f
            } else 96f
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = ppSpacing.xs)
            ) {
                val n = projection.days.size
                val barW = size.width / (n * 1.5f)
                projection.days.forEachIndexed { i, day ->
                    val h = ((kotlin.math.abs(day.balance) / maxBal) * 96.0).toFloat()
                    val x = i * (size.width / n.toFloat()) + barW / 4f
                    val color = if (day.balance < 0) ppColors.error else ppColors.gold
                    drawRect(
                        color = color,
                        topLeft = Offset(x, zeroY - h),
                        size = Size(barW, h)
                    )
                }
                drawLine(
                    color = ppColors.border,
                    start = Offset(0f, zeroY),
                    end = Offset(size.width, zeroY),
                    strokeWidth = 1.5f
                )
            }
            Text(
                "D-${projection.days.size - 1} → D-0 · " + when {
                    projection.days.count { it.events.isNotEmpty() } == 0 -> "no known events — quiet month ahead"
                    else -> "${projection.days.count { it.events.isNotEmpty() }} event days (paydays, bills, subscriptions)"
                },
                style = ppTypography.bodySmall,
                color = ppColors.textTertiary
            )
        }
    }
}
