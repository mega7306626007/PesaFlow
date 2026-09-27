package com.pesaflow.app.data.ledger

import com.pesaflow.app.data.models.PendingTransaction

// Identity memory: first-scan namings resolve every later row. "Nancy is
// mother" once → every future Nancy row arrives as Mom · Food with nothing
// left to confirm. Pure function — callers pass prefs-backed lookups.
// displayMerchant/displayCategory are the resolved face; category itself is
// overridden too so budgets, pending counts and auto-approve all agree.
fun resolveIdentities(
    rows: List<PendingTransaction>,
    aliasOf: (String) -> String?,
    categoryOf: (String) -> String?
): List<PendingTransaction> = rows.map { r ->
    val alias = aliasOf(r.merchant)?.takeIf { it.isNotBlank() }
    val learned = categoryOf(r.merchant)?.takeIf { it.isNotBlank() }
    if (alias == null && learned == null) r
    else r.copy(
        displayMerchant = alias ?: r.merchant,
        category = learned ?: r.category,
        displayCategory = learned ?: r.displayCategory
    )
}
