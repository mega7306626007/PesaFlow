package com.pesaflow.app.data.finance

import com.pesaflow.app.data.models.BudgetType
import com.pesaflow.app.data.time.TimeRange
import com.pesaflow.app.data.time.addDays
import com.pesaflow.app.data.time.monthRange
import com.pesaflow.app.data.time.startOfDay
import com.pesaflow.app.data.time.thisWeekRange
import com.pesaflow.app.data.time.todayRange
import com.pesaflow.app.data.time.yearRange

// Budget progress windows: budgets are paced against the CURRENT period,
// never the stored [startTimestamp, endTimestamp] (those go stale the month
// after creation and sum whole histories — "113k of 150k while holding 5k").
// Day→today, Week→Mon–Sun, Month→calendar month, Semester→profile window
// (rolling 120d without one), Annual→calendar year. Pure, unit-tested.
fun budgetWindowRange(
    type: BudgetType,
    now: Long,
    semesterStartMs: Long = 0L,
    semesterEndMs: Long = 0L
): TimeRange {
    return when (type) {
        BudgetType.DAILY -> todayRange(now)
        BudgetType.WEEKLY -> thisWeekRange(now)
        BudgetType.MONTHLY -> monthRange(now)
        BudgetType.SEMESTER -> {
            val tomorrow = addDays(startOfDay(now), 1)
            val start = if (semesterStartMs > 0) semesterStartMs else now - 120L * 24 * 60 * 60 * 1000
            val end = if (semesterEndMs > start) minOf(semesterEndMs, tomorrow) else tomorrow
            if (start >= end) TimeRange(end, end) else TimeRange(start, end)
        }
        BudgetType.ANNUAL -> yearRange(now)
    }
}
