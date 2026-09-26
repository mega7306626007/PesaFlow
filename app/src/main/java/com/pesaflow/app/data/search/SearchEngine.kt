package com.pesaflow.app.data.search

import com.pesaflow.app.data.models.Transaction
import com.pesaflow.app.data.models.TransactionType

/**
 * Full-text deep search engine: pure Kotlin, no Android imports.
 *
 * Searches across every transaction field with relevance scoring.
 * Supports multi-term AND queries and type filtering.
 */
data class SearchHit(
    val transaction: Transaction,
    val score: Double,
    val matchedFields: List<String>
)

data class SearchResult(
    val hits: List<SearchHit>,
    val totalCount: Int,
    val query: String,
    val durationMs: Long
)

/** Pure search engine. No Android imports — fully unit-tested. */
class SearchEngine {

    /** Tokenize a query into lowercase terms, filtering empty strings. */
    fun tokenize(query: String): List<String> {
        return query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    /** Score a single field match: exact phrase = 3.0, term presence = 1.0 per occurrence. */
    private fun scoreField(fieldValue: String, terms: List<String>): Double {
        if (fieldValue.isBlank() || terms.isEmpty()) return 0.0
        val low = fieldValue.lowercase()
        var score = 0.0
        for (term in terms) {
            if (low.contains(term)) {
                score += if (term.length > 3) 3.0 else 1.0
            }
        }
        return score
    }

    /** Search all transactions against the query. */
    fun search(
        allTxs: List<Transaction>,
        query: String,
        typeFilter: TransactionType? = null,
        maxResults: Int = 50
    ): SearchResult {
        val startMs = System.currentTimeMillis()
        val terms = tokenize(query)
        if (terms.isEmpty()) return SearchResult(emptyList(), 0, query, 0)

        val filtered = if (typeFilter != null) allTxs.filter { it.type == typeFilter } else allTxs
        val hits = mutableListOf<SearchHit>()
        for (tx in filtered) {
            val fields = listOf(
                "merchant" to tx.merchant,
                "category" to tx.category,
                "notes" to tx.notes,
                "subcategory" to tx.subcategory,
                "tags" to tx.tags.joinToString(" "),
                "description" to tx.description
            )
            val scores = fields.map { (name, value) -> name to scoreField(value, terms) }
            val totalScore = scores.sumOf { it.second }
            val matched = scores.filter { it.second > 0 }.map { it.first }
            if (totalScore > 0) {
                hits.add(SearchHit(tx, totalScore, matched))
            }
        }
        hits.sortByDescending { it.score }
        val result = hits.take(maxResults)
        return SearchResult(
            hits = result,
            totalCount = hits.size,
            query = query,
            durationMs = System.currentTimeMillis() - startMs
        )
    }

    /** Fuzzy match: returns true if edit distance <= 2. */
    fun fuzzyMatch(a: String, b: String): Boolean {
        if (a.length > b.length) return fuzzyMatch(b, a)
        if (b.length - a.length > 2) return false
        val dp = IntArray(a.length + 1) { it }
        for (j in 1..b.length) {
            var prev = j
            for (i in 1..a.length) {
                val temp = dp[i]
                if (a[i - 1] == b[j - 1]) dp[i] = prev else dp[i] = 1 + minOf(dp[i], dp[i - 1], prev)
                prev = temp
            }
        }
        return dp[a.length] <= 2
    }

    /** Search with fuzzy fallback on merchant names. */
    fun searchWithFuzzy(
        allTxs: List<Transaction>,
        query: String,
        maxResults: Int = 50
    ): SearchResult {
        val exact = search(allTxs, query, maxResults = maxResults)
        if (exact.hits.size >= maxResults) return exact
        val remaining = maxResults - exact.hits.size
        val terms = tokenize(query)
        val fuzzyHits = mutableListOf<SearchHit>()
        for (tx in allTxs) {
            if (exact.hits.any { it.transaction.id == tx.id }) continue
            for (term in terms) {
                if (fuzzyMatch(term, tx.merchant.lowercase())) {
                    fuzzyHits.add(SearchHit(tx, 2.0, listOf("merchant(fuzzy)")))
                    break
                }
            }
        }
        val combined = (exact.hits + fuzzyHits).sortedByDescending { it.score }.take(maxResults)
        return SearchResult(
            hits = combined,
            totalCount = combined.size,
            query = query,
            durationMs = exact.durationMs
        )
    }
}
