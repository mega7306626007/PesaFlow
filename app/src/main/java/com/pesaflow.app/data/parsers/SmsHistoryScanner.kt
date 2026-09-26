package com.pesaflow.app.data.parsers

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// First-run M-Pesa history scan: last 60 days of SMS for figure confirmation
// and gentle re-adjustment of onboarding estimates. Read-only — nothing is
// written here; callers queue via tryQueuePending (which dedupes) and use the
// aggregates as editable starting suggestions, never silent facts.
data class SmsScanResult(
    val found: Int = 0,
    val parsed: List<PendingTransaction> = emptyList(),
    val unreadable: Int = 0,
    val incomeTotal: Double = 0.0,
    val expenseTotal: Double = 0.0,
    val byCategory: Map<String, Double> = emptyMap(),
    // Window the totals cover — monthly paces divide by months scanned,
    // not a hardcoded 2 (60-day scans lied for every other window).
    val daysBack: Int = 60
) {
    private val months: Double get() = (daysBack / 30.0).coerceAtLeast(1.0 / 30)
    val monthlyIncome: Double get() = incomeTotal / months
    val monthlyExpense: Double get() = expenseTotal / months
    fun monthlyFor(vararg names: String): Double {
        val keys = names.map { it.lowercase() }.toSet()
        return byCategory.filterKeys { it.lowercase() in keys }.values.sum() / months
    }
}

suspend fun scanRecentSms(
    context: Context,
    daysBack: Int = 60,
    maxRows: Int = 500,
    // Chunked paging with honest progress: big inboxes (5-month onboarding
    // scans) report per-page instead of hanging silently. Cancel cooperates
    // between pages; partial results still return.
    pageSize: Int = 150,
    onProgress: (found: Int, parsed: Int) -> Unit = { _, _ -> },
    isCancelled: () -> Boolean = { false }
): SmsScanResult {
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
        return SmsScanResult()
    }
    val since = System.currentTimeMillis() - daysBack * 24L * 60 * 60 * 1000
    val parsed = mutableListOf<PendingTransaction>()
    var found = 0
    var unreadable = 0
    var newestBalance: Double? = null
    // Newest-first page order: the first balance tail found is the latest wallet figure.
    try {
        // MPESA/Safaricom first, then telcos, then any KES-denominated body
        // (bank KES texts come from a dozen sender IDs — the body is the net).
        var offset = 0
        while (offset < maxRows && !isCancelled()) {
            var pageRows = 0
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf("_id", "address", "body", "date"),
                "(address LIKE ? OR address LIKE ? OR address LIKE ? OR address LIKE ? OR address LIKE ? OR body LIKE ? OR body LIKE ?) AND date >= ?",
                arrayOf("%MPESA%", "%Safaricom%", "%AIRTEL%", "%TELKOM%", "%EQUITEL%", "%M-PESA%", "%KES%", since.toString()),
                "date DESC LIMIT $pageSize OFFSET $offset"
            )?.use { c ->
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val addrIdx = c.getColumnIndexOrThrow("address")
                while (c.moveToNext()) {
                    found++
                    pageRows++
                    val body = c.getString(bodyIdx) ?: ""
                    val sender = try { c.getString(addrIdx) ?: "" } catch (e: Exception) { "" }
                    // Sender powers bank-name patterns — dropping it blinds them.
                    // Harvest the wallet balance even from unparseable bodies.
                    if (newestBalance == null) parseBalance(body)?.let { newestBalance = it }
                    val p = MpesaParser.parseMessage(body, sender)
                    if (p == null) unreadable++ else parsed.add(p)
                }
            }
            offset += pageSize
            onProgress(found, parsed.size)
            if (pageRows < pageSize) break
        }
    } catch (e: SecurityException) {
        return SmsScanResult()
    } catch (e: Exception) {
        return SmsScanResult(found = found)
    }
    // Wallet display: newest balance tail seen (scan order is newest-first).
    newestBalance?.let { saveMpesaBalance(context, it) }
    var income = 0.0
    var expense = 0.0
    val cats = mutableMapOf<String, Double>()
    parsed.forEach { p ->
        // Only true spending paces budgets — transfers/savings moves are not expenses.
        if (p.type == TransactionType.INCOME) income += p.amount
        else if (p.type == TransactionType.EXPENSE) expense += p.amount
        if (p.type == TransactionType.EXPENSE) cats[p.category] = (cats[p.category] ?: 0.0) + p.amount
    }
    return SmsScanResult(found, parsed, unreadable, income, expense, cats, daysBack)
}
