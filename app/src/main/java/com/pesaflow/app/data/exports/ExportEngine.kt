package com.pesaflow.app.data.exports

import com.pesaflow.app.data.models.Budget
import com.pesaflow.app.data.models.Bill
import com.pesaflow.app.data.models.Debt
import com.pesaflow.app.data.models.SavingsGoal
import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType
import com.pesaflow.app.data.models.UniversityProfile
import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV/PDF export engine: pure Kotlin, no Android imports.
 *
 * Exports every table to a portable, compiler-checked format.
 * Uses kotlinx.serialization for JSON backup and a hand-written
 * CSV writer for spreadsheet interoperability.
 */
class ExportEngine(
    private val json: Json = ExportJson
) {

    companion object {
        val ExportJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
        private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }

    /** Export everything as a CSV string with headers. */
    fun exportCsv(
        transactions: List<Transaction>,
        budgets: List<Budget> = emptyList(),
        goals: List<SavingsGoal> = emptyList(),
        bills: List<Bill> = emptyList(),
        debts: List<Debt> = emptyList(),
        profile: UniversityProfile? = null
    ): String {
        val sb = StringBuilder()
        // Transactions — every field, compiler-checked.
        sb.append("type,date,merchant,category,subcategory,amount,payment_method,source,notes,tags,recurring,confirmed,created_at\n")
        transactions.sortedBy { it.dateTimestamp }.forEach { tx ->
            sb.append(quote(tx.type.name)).append(",")
            sb.append(quote(dateFmt.format(Date(tx.dateTimestamp)))).append(",")
            sb.append(quote(tx.merchant)).append(",")
            sb.append(quote(tx.category)).append(",")
            sb.append(quote(tx.subcategory)).append(",")
            sb.append(tx.amount).append(",")
            sb.append(quote(tx.paymentMethod.name)).append(",")
            sb.append(quote(tx.source.name)).append(",")
            sb.append(quote(tx.notes)).append(",")
            sb.append(quote(tx.tags.joinToString(";"))).append(",")
            sb.append(tx.recurring).append(",")
            sb.append(tx.confirmed).append(",")
            sb.append(quote(dateFmt.format(Date(tx.createdAt))))
            sb.append("\n")
        }
        // Budgets
        sb.append("\n# BUDGETS\n")
        sb.append("category,limit_amount,type,start,end,shared_with\n")
        budgets.sortedBy { it.startTimestamp }.forEach { b ->
            sb.append(quote(b.category)).append(",")
            sb.append(b.limitAmount).append(",")
            sb.append(quote(b.type.name)).append(",")
            sb.append(quote(dateFmt.format(Date(b.startTimestamp)))).append(",")
            sb.append(quote(dateFmt.format(Date(b.endTimestamp)))).append(",")
            sb.append(quote(b.sharedWith))
            sb.append("\n")
        }
        // Goals
        sb.append("\n# SAVINGS GOALS\n")
        sb.append("title,target_amount,current_amount,target_date\n")
        goals.sortedBy { it.targetTimestamp }.forEach { g ->
            sb.append(quote(g.title)).append(",")
            sb.append(g.targetAmount).append(",")
            sb.append(g.currentAmount).append(",")
            sb.append(quote(dateFmt.format(Date(g.targetTimestamp))))
            sb.append("\n")
        }
        // Bills
        sb.append("\n# BILLS\n")
        sb.append("name,amount,due_date,category,frequency,status\n")
        bills.sortedBy { it.dueDate }.forEach { b ->
            sb.append(quote(b.name)).append(",")
            sb.append(b.amount).append(",")
            sb.append(quote(dateFmt.format(Date(b.dueDate)))).append(",")
            sb.append(quote(b.category)).append(",")
            sb.append(quote(b.frequency)).append(",")
            sb.append(quote(b.status))
            sb.append("\n")
        }
        // Debts
        sb.append("\n# DEBTS\n")
        sb.append("person,amount,date_borrowed,due_date,description,status,direction\n")
        debts.sortedBy { it.dueDate }.forEach { d ->
            sb.append(quote(d.person)).append(",")
            sb.append(d.amount).append(",")
            sb.append(quote(dateFmt.format(Date(d.dateBorrowed)))).append(",")
            sb.append(quote(dateFmt.format(Date(d.dueDate)))).append(",")
            sb.append(quote(d.description)).append(",")
            sb.append(quote(d.status)).append(",")
            sb.append(quote(d.direction))
            sb.append("\n")
        }
        return sb.toString()
    }

    /** Build a full backup payload (JSON) from every table. */
    fun buildBackupPayload(
        transactions: List<Transaction>,
        pending: List<com.pesaflow.app.data.models.PendingTransaction>,
        budgets: List<Budget>,
        goals: List<SavingsGoal>,
        profile: UniversityProfile?,
        bills: List<Bill>,
        debts: List<Debt>,
        meals: List<com.pesaflow.app.data.models.MealItem>,
        chamas: List<com.pesaflow.app.data.models.ChamaGroup>,
        belongings: List<com.pesaflow.app.data.models.Belonging>,
        kitchenStock: List<com.pesaflow.app.data.models.KitchenStock>
    ): com.pesaflow.app.data.backup.BackupPayload {
        return com.pesaflow.app.data.backup.BackupPayload(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            transactions = transactions,
            pending = pending,
            budgets = budgets,
            goals = goals,
            profile = profile,
            bills = bills,
            debts = debts,
            meals = meals,
            chamas = chamas,
            belongings = belongings,
            kitchenStock = kitchenStock
        )
    }

    /** Serialize backup payload to JSON string. */
    fun backupToJson(payload: com.pesaflow.app.data.backup.BackupPayload): String {
        return json.encodeToString(com.pesaflow.app.data.backup.BackupPayload.serializer(), payload)
    }

    /** Generate a human-readable summary string for sharing. */
    fun generateShareSummary(
        transactions: List<Transaction>,
        periodDays: Int = 30
    ): String {
        val now = System.currentTimeMillis()
        val since = now - periodDays * 24L * 60 * 60 * 1000
        val window = transactions.filter { it.dateTimestamp >= since && !it.isSample }
        val expenses = window.filter { it.type == TransactionType.EXPENSE }
        val incomes = window.filter { it.type == TransactionType.INCOME }
        val totalSpent = expenses.sumOf { it.amount }.toInt()
        val totalIncome = incomes.sumOf { it.amount }.toInt()
        val net = totalIncome - totalSpent
        val foodSpend = expenses.filter { it.category.contains("food", ignoreCase = true) }.sumOf { it.amount }.toInt()
        val transportSpend = expenses.filter { it.category.contains("transport", ignoreCase = true) }.sumOf { it.amount }.toInt()
        val dayCount = window.map { it.dateTimestamp / (24L * 60 * 60 * 1000) }.toSet().size
        val savingsPct = if (totalIncome > 0) { (net / totalIncome * 100).toInt() } else { 0 }
        val topCat = expenses.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.maxByOrNull { it.value }
        val biggest = expenses.maxByOrNull { it.amount }
        val netWorth = transactions.filter { !it.isSample }.sumOf {
            when (it.type) {
                TransactionType.INCOME -> it.amount
                TransactionType.EXPENSE -> -it.amount
                TransactionType.SAVING -> 0.0 // savings are still money held
                TransactionType.INVESTMENT -> it.amount
                TransactionType.TRANSFER -> 0.0
            }
        }.toInt()
        val shareText = """PesaFlow $periodDays-day summary:
Income: KSh $totalIncome
Expenses: KSh $totalSpent ($savingsPct% savings)
Net flow: KSh $net
Food: KSh $foodSpend
Transport: KSh $transportSpend
Active days: $dayCount
${topCat?.let { "Top: ${it.key} KSh ${it.value.toInt()}" } ?: ""}${biggest?.let { " · Biggest: ${it.merchant} KSh ${it.amount.toInt()}" } ?: ""}
Net worth: KSh $netWorth
${if (expenses.isEmpty()) "No spending logged — full month ahead!" else "Keep the ritual going."}""".trimIndent()
        return shareText
    }

    private fun quote(s: String): String = "\"${s.replace("\"", "\"\"")}\""
}
