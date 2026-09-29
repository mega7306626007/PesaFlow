package com.pesaflow.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.ui.theme.PpQuickAction
import com.pesaflow.app.ui.theme.ppSpacing
import com.pesaflow.app.ui.theme.ppTypography
import com.pesaflow.app.ui.theme.ppColors
import com.pesaflow.app.viewmodels.FinanceViewModel


// Personalized header: greeting first, actions after the hero.
@Composable
fun HomeGreeting(userName: String) {
    Column {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..21 -> "evening"
            else -> "night"
        }
        // Greetings stay in plain English in every language mode —
        // a wrong Sheng greeting is worse than no Sheng at all.
        val greet = when (part) {
            "morning" -> "Good morning ☀️"
            "afternoon" -> "Good afternoon 🌤️"
            "evening" -> "Good evening 🌙"
            else -> "Burning the midnight oil 🦉"
        }
        Text(
            if (userName.isNotBlank()) "$greet, $userName" else greet,
            style = ppTypography.h2,
            color = ppColors.textPrimary
        )
        Spacer(modifier = Modifier.height(ppSpacing.xs))
        Text(
            "Here's your financial picture today",
            style = ppTypography.bodySmall,
            color = ppColors.textTertiary
        )
        Text(
            java.text.SimpleDateFormat("EEEE, d MMM", java.util.Locale.getDefault()).format(java.util.Date()),
            style = ppTypography.bodySmall,
            color = ppColors.textTertiary
        )
    }
}

@Composable
fun HomeQuickActions(
    viewModel: FinanceViewModel,
    onQuickAdd: (TransactionType) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ppSpacing.md)) {
        PpQuickAction(
            label = viewModel.getLocalizedString("expense_btn"),
            icon = Icons.Filled.Remove,
            onClick = { onQuickAdd(TransactionType.EXPENSE) },
            modifier = Modifier.weight(1f)
        )
        PpQuickAction(
            label = viewModel.getLocalizedString("income_btn"),
            icon = Icons.Filled.Add,
            onClick = { onQuickAdd(TransactionType.INCOME) },
            modifier = Modifier.weight(1f)
        )
    }
}
