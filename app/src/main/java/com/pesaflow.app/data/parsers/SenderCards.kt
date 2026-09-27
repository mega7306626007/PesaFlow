package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Sender cards: one card per unknown sender instead of 700 rows to confirm.
// "Nancy · 12 transactions · 3 Jan – 28 Mar" — the user names her once and
// every row (past + future) resolves. Pure function, zero Android deps.
data class SenderCard(
    val merchant: String,
    val count: Int,
    val expenseTotal: Double,
    val incomeTotal: Double,
    val firstSeen: Long,
    val lastSeen: Long,
    val suggestedCategory: String
)

// System counterparties never need a "who is this?" — only human-like
// unknown senders surface as cards.
private val KNOWN_ENTITIES = setOf(
    "safaricom", "airtel", "telkom", "equitel",
    "kplc", "kenya power", "nairobi water", "nws",
    "kcb", "equity", "absa", "stanbic", "co-op", "coop",
    "family bank", "dtb", "ncba", "stanchart", "i&m",
    "helb", "fuliza", "shwari", "okoa", "sacco",
    "kra", "nhif", "nssf", "e-citizen", "ecitizen"
)

fun isKnownEntity(merchant: String): Boolean {
    val low = merchant.trim().lowercase()
    if (low.isEmpty() || low == "unknown party") return true
    // Pure Till/Paybill numbers are places, not people — no naming needed.
    if (low.matches(Regex("^(till\\s*)?[0-9]{5,9}$"))) return true
    return KNOWN_ENTITIES.any { low.contains(it) }
}

// Groups parsed rows by sender. isNamed covers user aliases (MerchantMemory):
// already-named senders never surface again. Only frequent senders surface —
// a one-off needs no naming ceremony. Sorted busiest-first so the first cards
// the user confirms clear the most rows.
fun groupSenderCards(
    parsed: List<PendingTransaction>,
    isNamed: (String) -> Boolean = { false },
    minTransactions: Int = 16
): List<SenderCard> {
    val byMerchant = parsed
        .filter { it.merchant.isNotBlank() }
        .groupBy { it.merchant.trim() }
        .filterKeys { !isKnownEntity(it) && !isNamed(it) }
    return byMerchant.map { (merchant, rows) ->
        val expenses = rows.filter { it.type == TransactionType.EXPENSE }
        val incomes = rows.filter { it.type == TransactionType.INCOME }
        // Suggested category = whatever most of their rows already say.
        val suggested = rows.groupingBy { it.category }.eachCount()
            .maxByOrNull { it.value }?.key ?: "Other"
        SenderCard(
            merchant = merchant,
            count = rows.size,
            expenseTotal = expenses.sumOf { it.amount },
            incomeTotal = incomes.sumOf { it.amount },
            firstSeen = rows.minOf { it.dateTimestamp },
            lastSeen = rows.maxOf { it.dateTimestamp },
            suggestedCategory = suggested
        )
    }.filter { it.count > minTransactions }.sortedByDescending { it.count }
}
