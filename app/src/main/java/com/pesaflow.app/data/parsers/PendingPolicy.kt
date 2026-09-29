package com.pesaflow.app.data.parsers

// Pending-to-ledger sync policy: which queued rows may confirm themselves.
// Single source for the dashboard bulk bar and the onboarding first-sync,
// so "sure" never means two different things on two screens. Pure logic.
object PendingPolicy {
    /** History-vouched threshold shared by every "confirm sure" surface. */
    const val SURE_CONFIDENCE = 0.85f

    /**
     * A row may auto-confirm only with a carrier reference code (M-Pesa TID,
     * bank ref — bank-grade records) AND sure confidence. Codeless shapes
     * (bare receipts, header-less catcher) always wait for human eyes.
     */
    fun isAutoApprovable(sourceTransactionId: String?, effectiveConfidence: Float): Boolean =
        !sourceTransactionId.isNullOrBlank() && effectiveConfidence >= SURE_CONFIDENCE
}
