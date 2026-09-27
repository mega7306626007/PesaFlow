package com.pesaflow.app.data.ledger

import android.content.SharedPreferences
import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType

// Triple memory: who they are (label) + usual category + direction scope.
// "Mother" can mean Upkeep-IN and still leave support-OUT alone — the two
// older single maps (alias-only, category-only) read through this store,
// so nothing learned before is lost.
data class ContactMemory(
    val label: String = "",
    val category: String = "",
    val scope: String = "BOTH" // IN | OUT | BOTH
)

// One normalized face per human: "nancy", "NANCY " and "Nancy Wanjiru" key
// alike. Whitespace collapses, words title-case for display.
fun normalizeContact(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").lowercase()
        .split(" ").joinToString(" ") { w ->
            w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

fun contactLabelKey(name: String) = "contact_label_" + normalizeContact(name)
fun contactCatKey(name: String) = "contact_cat_" + normalizeContact(name)
fun contactScopeKey(name: String) = "contact_scope_" + normalizeContact(name)

fun saveContactMemory(
    prefs: SharedPreferences,
    name: String,
    label: String,
    category: String,
    scope: String
): Boolean {
    val cleanLabel = label.trim().take(40)
    val cleanCat = category.trim()
    val cleanScope = scope.trim().uppercase().takeIf { it == "IN" || it == "OUT" } ?: "BOTH"
    if (normalizeContact(name).isEmpty() || (cleanLabel.isEmpty() && cleanCat.isEmpty())) return false
    prefs.edit()
        .putString(contactLabelKey(name), cleanLabel)
        .putString(contactCatKey(name), cleanCat)
        .putString(contactScopeKey(name), cleanScope)
        .apply()
    return true
}

// Reads every contact_* triple back. Scope-only rows (no label, no category)
// carry no usable memory and are dropped.
fun readContactMemories(all: Map<String, String>): Map<String, ContactMemory> {
    val labels = all.filterKeys { it.startsWith("contact_label_") }
    val cats = all.filterKeys { it.startsWith("contact_cat_") }
    val scopes = all.filterKeys { it.startsWith("contact_scope_") }
    val names = (labels.keys.map { it.removePrefix("contact_label_") } +
        cats.keys.map { it.removePrefix("contact_cat_") })
        .map { normalizeContact(it) }.toSet()
    return names.mapNotNull { n ->
        val label = labels["contact_label_$n"].orEmpty()
        val cat = cats["contact_cat_$n"].orEmpty()
        if (label.isBlank() && cat.isBlank()) return@mapNotNull null
        val scope = scopes["contact_scope_$n"].orEmpty().trim().uppercase()
            .takeIf { it == "IN" || it == "OUT" } ?: "BOTH"
        n to ContactMemory(label, cat, scope)
    }.toMap()
}

// Typing "Mother" fills Upkeep + Money-in automatically — only where nothing
// was typed or saved before (callers guard that). Vehicle words beat sacco
// ("Matatu sacco" is transport, not savings). Friends/strangers stay blank:
// no guess is safe there, so the card keeps asking.
fun suggestMemory(text: String): ContactMemory {
    val t = text.lowercase()
    fun has(vararg words: String) = words.any { it in t }
    return when {
        has("mum", "mother", "mom", "dad", "father", "parent", "guardian", "sponsor") -> ContactMemory("", "Upkeep", "IN")
        has("boss", "employer", "salary") -> ContactMemory("", "Salary", "IN")
        has("helb") -> ContactMemory("", "Salary", "IN")
        has("landlord", "caretaker", "house agent") -> ContactMemory("", "Rent", "OUT")
        has("school", "university", "college", "registrar", "exam", "fees") -> ContactMemory("", "School", "OUT")
        has("kplc", "token", "nairobi water", " water", "wifi") -> ContactMemory("", "Bills", "OUT")
        has("boda", "matatu", "bolt", "uber", "stage", "fare", "shuttle") -> ContactMemory("", "Transport", "OUT")
        has("kibanda", "mboga", "grocery", "naivas", "carrefour", "quickmart", "chips", "smocha", "restaurant", "kiosk", "butchery") -> ContactMemory("", "Food", "OUT")
        has("airtime", "bundle", "data") -> ContactMemory("", "Airtime", "OUT")
        has("chama", "sacco") -> ContactMemory("", "Savings", "OUT")
        else -> ContactMemory("", "", "BOTH")
    }
}

// Stamps remembered faces onto rows. Labels apply in every direction;
// categories only inside their scope. Never overwrites an existing face.
fun applyContactMemory(
    rows: List<PendingTransaction>,
    memories: Map<String, ContactMemory>
): List<PendingTransaction> {
    if (memories.isEmpty()) return rows
    return rows.map { r ->
        val mem = memories[normalizeContact(r.merchant)] ?: return@map r
        var q = r
        if (mem.label.isNotBlank() && q.displayMerchant.isBlank()) {
            q = q.copy(displayMerchant = r.merchant.trim() + " · " + mem.label)
        }
        if (mem.category.isNotBlank()) {
            val applies = when (mem.scope) {
                "IN" -> q.type == TransactionType.INCOME
                "OUT" -> q.type != TransactionType.INCOME
                else -> true
            }
            if (applies) q = q.copy(category = mem.category, displayCategory = mem.category)
        }
        q
    }
}
