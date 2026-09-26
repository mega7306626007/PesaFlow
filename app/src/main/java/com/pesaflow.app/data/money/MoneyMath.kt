package com.pesaflow.app.data.money

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

// Single home for hero money math: every balance, income and savings figure
// the UI shows must come through here, so the ledger can never be counted
// two different ways on two different screens.
// (Best-of both trees: ported from PesaFlow main and adapted — this tree
// flags demo rows with isSample instead of an OPENING source, and books
// opening upkeep as real INCOME, so statistics exclude samples only.)

fun ledgerBalance(txs: List<Transaction>): Double = txs.sumOf {
    when (it.type) {
        TransactionType.INCOME -> it.amount
        TransactionType.EXPENSE -> -it.amount
        TransactionType.SAVING -> -it.amount
        TransactionType.INVESTMENT -> -it.amount
        TransactionType.TRANSFER -> 0.0
    }
}

// Ziidi holding: top-ups in as SAVING, withdrawals back as INCOME (both
// merchant "Ziidi"). Never negative — a ledger can't over-withdraw. The hero
// card adds it back because moving money into Ziidi already left the
// balance: the pair is neutral by construction, so hero == total funds
// under control, not a double count.
fun ziidiHoldings(txs: List<Transaction>): Double = txs.sumOf {
    when {
        it.type == TransactionType.SAVING && it.merchant.contains("ziidi", ignoreCase = true) -> it.amount
        it.type == TransactionType.INCOME && it.merchant.contains("ziidi", ignoreCase = true) -> -it.amount
        else -> 0.0
    }
}.coerceAtLeast(0.0)

fun heroMoney(txs: List<Transaction>): Double = ledgerBalance(txs) + ziidiHoldings(txs)

fun isCurrentMonth(ts: Long, nowMs: Long = System.currentTimeMillis()): Boolean {
    val ref = Calendar.getInstance().apply { timeInMillis = nowMs }
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return c.get(Calendar.YEAR) == ref.get(Calendar.YEAR) &&
        c.get(Calendar.MONTH) == ref.get(Calendar.MONTH)
}

// Month-scoped totals for rates and verdicts: demo/sample rows never move
// statistics (they still count in the balance — cash is cash).
fun monthScopedTotal(
    txs: List<Transaction>,
    type: TransactionType,
    nowMs: Long = System.currentTimeMillis(),
    excludeSamples: Boolean = true
): Double = txs.filter {
    it.type == type && (!excludeSamples || !it.isSample) && isCurrentMonth(it.dateTimestamp, nowMs)
}.sumOf { it.amount }
