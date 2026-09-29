package com.pesaflow.app.ui.dashboard

// Safe-to-spend day math, pure and unit-tested. Dynamic by construction:
// the target is remaining money over remaining days (never a frozen
// monthly/30), so it breathes with every transaction and every sunrise.
// Yesterday needs no separate rollover — it already sits inside spentMonth.
data class SafeDayFigure(
    /** Days left including today. */
    val daysLeft: Int,
    val spentMonth: Double,
    /** monthlyLimit - spentMonth - reserves. May be negative (over pace). */
    val remaining: Double,
    /** remaining / daysLeft, floored at 0. */
    val dailyTarget: Int,
    /** dailyTarget paced by the weekday factor. */
    val allowance: Int,
    /** allowance - todaySpend. May be negative (paused). */
    val left: Int
)

fun safeDayFigure(
    monthlyLimit: Double,
    spentMonth: Double,
    planDaily: Int,
    billDaily: Int,
    weekdayFactor: Double,
    todaySpend: Int,
    dayOfMonth: Int,
    daysInMonth: Int
): SafeDayFigure {
    val daysLeft = (daysInMonth - dayOfMonth + 1).coerceAtLeast(1)
    val reserve = (planDaily + billDaily) * daysLeft
    val remaining = monthlyLimit - spentMonth - reserve
    val dailyTarget = (remaining / daysLeft).coerceAtLeast(0.0).toInt()
    val allowance = (dailyTarget * weekdayFactor).toInt()
    return SafeDayFigure(daysLeft, spentMonth, remaining, dailyTarget, allowance, allowance - todaySpend)
}
