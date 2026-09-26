package com.pesaflow.app.data.parsers

import com.pesaflow.app.data.models.PendingTransaction
import com.pesaflow.app.data.models.TransactionType
import java.util.Calendar

// Session-aware SMS analysis: WHERE in the academic year each transaction fell.
//
// Regimes (never more than these four):
// - SESSION: in class, normal campus spending — the ONLY months that train averages.
// - BREAK: long vacation (May-Aug classic) — quarantined unless rhythms say otherwise.
// - ATTACHMENT: practicum/attachment months — a third regime, never "normal".
// - PRE_UNI: before campus life started (first-years, gap years) — labeled, not averaged.
//
// Layer order: stated semester dates (onboarding profile) always
// win; university typical-pattern defaults only prefill empty date fields;
// transport-collapse auto-detection proposes break months the calendar missed
// (strikes, late reporting, gap years) for one-tap confirmation.
enum class Regime { SESSION, BREAK, ATTACHMENT, PRE_UNI }

data class SessionWindow(val startMillis: Long, val endMillis: Long) {
    fun contains(ts: Long): Boolean = ts >= startMillis && ts <= endMillis
}

data class SessionCalendar(
    val sessionSpans: List<SessionWindow>,
    val attachmentSpans: List<SessionWindow> = emptyList()
) {
    fun regimeOf(ts: Long): Regime {
        if (attachmentSpans.any { it.contains(ts) }) return Regime.ATTACHMENT
        if (sessionSpans.any { it.contains(ts) }) return Regime.SESSION
        val earliest = sessionSpans.minOfOrNull { it.startMillis } ?: Long.MAX_VALUE
        if (ts < earliest) return Regime.PRE_UNI
        return Regime.BREAK
    }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Build a calendar from STATED semester dates (Layer 1 — always wins).
 * Continuing students get the prior academic year reconstructed (same dates
 * shifted 365 days back); first-years get PRE_UNI for everything before start.
 */
fun resolveCalendar(
    statedStart: Long,
    statedEnd: Long,
    now: Long = System.currentTimeMillis(),
    firstYear: Boolean = false,
    attachmentSpans: List<SessionWindow> = emptyList()
): SessionCalendar {
    if (statedStart <= 0 || statedEnd <= statedStart) return SessionCalendar(emptyList(), attachmentSpans)
    val spans = mutableListOf(SessionWindow(statedStart, statedEnd))
    if (!firstYear) {
        val prior = SessionWindow(statedStart - 365 * DAY_MS, statedEnd - 365 * DAY_MS)
        if (prior.endMillis < statedStart && prior.startMillis < now) spans.add(0, prior)
    }
    return SessionCalendar(spans, attachmentSpans)
}

/** Month key "yyyy-MM" for transport series. */
fun monthKey(ts: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
}

/**
 * Transport-collapse detector (Layer 3): months whose Transport spend falls
 * below 30% of the median month are proposed as break months. Needs 3+ months
 * of data; December/Easter dips don't trigger (single low months inside an
 * otherwise flat series stay under the bar only if the collapse is deep).
 * Pure function — the caller intersects with the stated calendar.
 */
fun detectBreakMonths(
    monthlyTransport: Map<String, Double>,
    minMonths: Int = 3,
    collapseRatio: Double = 0.3
): Set<String> {
    if (monthlyTransport.size < minMonths) return emptySet()
    val sorted = monthlyTransport.values.sorted()
    val median = if (sorted.size % 2 == 1) sorted[sorted.size / 2]
    else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
    if (median <= 0) return emptySet()
    return monthlyTransport.filter { it.value < collapseRatio * median }.keys
}

/** Typical-pattern prefill defaults (Layer 2): convenience, never truth. */
data class UniDefault(
    val name: String,
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
    val note: String = ""
)

val UNIVERSITY_DEFAULTS = listOf(
    UniDefault("University of Nairobi", 9, 1, 4, 30),
    UniDefault("Kenyatta University", 9, 1, 4, 30),
    UniDefault("JKUAT", 9, 1, 4, 30),
    UniDefault("Moi University", 9, 1, 4, 30),
    UniDefault("Egerton University", 9, 1, 4, 30),
    UniDefault("Maseno University", 9, 1, 4, 30),
    UniDefault("Masinde Muliro (MMUST)", 9, 1, 4, 30),
    UniDefault("Kisii University", 9, 1, 4, 30),
    UniDefault("Technical University of Kenya", 9, 8, 4, 30),
    UniDefault("Pwani University", 9, 1, 4, 30),
    UniDefault("University of Eldoret", 9, 1, 4, 30),
    UniDefault("Chuka University", 9, 1, 4, 30),
    UniDefault("South Eastern Kenya (SEKU)", 9, 7, 4, 2),
    UniDefault("Co-operative University", 9, 1, 4, 30),
    UniDefault("KCA University", 8, 25, 4, 30, "Confirm — intakes shift; weekend tracks differ"),
    UniDefault("Daystar University", 9, 1, 4, 30, "Trimester school — confirm dates, do not trust blindly")
)

/** Per-regime slice of a scan: session pace is normalized per-day, never per-month. */
data class RegimeSlice(
    val regime: Regime,
    val income: Double,
    val expense: Double,
    val days: Double,
    val txCount: Int
) {
    /** Session monthly pace: per-day rate x 30. Partial months handled by construction. */
    val monthlyPace: Double get() = if (days > 0) expense / days * 30 else 0.0
    val monthlyIncomePace: Double get() = if (days > 0) income / days * 30 else 0.0
}

fun summarizeByRegime(
    parsed: List<PendingTransaction>,
    regimeOf: (Long) -> Regime
): Map<Regime, RegimeSlice> {
    return Regime.values().associateWith { regime ->
        val rows = parsed.filter { regimeOf(it.dateTimestamp) == regime }
        val days = if (rows.isEmpty()) 0.0 else {
            val span = (rows.maxOf { it.dateTimestamp } - rows.minOf { it.dateTimestamp }) / DAY_MS.toDouble() + 1
            span.coerceAtLeast(1.0)
        }
        RegimeSlice(
            regime = regime,
            income = rows.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            expense = rows.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            days = days,
            txCount = rows.size
        )
    }
}

/** Monthly Transport series for the collapse detector (EXPENSE + Transport only). */
fun monthlyTransportSeries(parsed: List<PendingTransaction>): Map<String, Double> {
    return parsed
        .filter { it.type == TransactionType.EXPENSE && it.category.equals("Transport", ignoreCase = true) }
        .groupBy { monthKey(it.dateTimestamp) }
        .mapValues { (_, rows) -> rows.sumOf { it.amount } }
}

/**
 * Current-semester start guess (Layer 2): the university's typical intake
 * month/day, most recent occurrence at or before now. Unknown schools fall
 * back to 240 days before the stated end. Prefill-grade, never truth — the
 * confirmation queue lets the student correct it in one tap.
 */
fun guessSemesterStart(
    universityName: String,
    semesterEnd: Long,
    now: Long = System.currentTimeMillis()
): Long {
    if (universityName.isNotBlank()) {
        val match = UNIVERSITY_DEFAULTS.firstOrNull {
            universityName.contains(it.name, ignoreCase = true) ||
                it.name.contains(universityName, ignoreCase = true)
        }
        if (match != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            val startYear =
                if (cal.get(Calendar.MONTH) + 1 >= match.startMonth) cal.get(Calendar.YEAR)
                else cal.get(Calendar.YEAR) - 1
            return Calendar.getInstance().apply {
                set(startYear, match.startMonth - 1, match.startDay, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
    return semesterEnd - 240 * DAY_MS
}
