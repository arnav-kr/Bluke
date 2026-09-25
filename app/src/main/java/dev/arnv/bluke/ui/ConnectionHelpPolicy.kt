package dev.arnv.bluke.ui

internal const val CONNECTION_HELP_ATTEMPT_LIMIT = 3
internal const val CONNECTION_HELP_STALL_MILLIS = 12_000L
internal const val CONNECTION_HELP_CHURN_LIMIT = 4
internal const val CONNECTION_HELP_CHURN_WINDOW_MILLIS = 120_000L

internal fun shouldOfferConnectionHelp(
    connectionAttempts: Int = 0,
    stalledForMillis: Long = 0L,
    transitionTimestamps: List<Long> = emptyList(),
    nowMillis: Long = 0L,
): Boolean {
    if (connectionAttempts >= CONNECTION_HELP_ATTEMPT_LIMIT) return true
    if (stalledForMillis >= CONNECTION_HELP_STALL_MILLIS) return true

    val windowStart = nowMillis - CONNECTION_HELP_CHURN_WINDOW_MILLIS
    return transitionTimestamps.count { it in windowStart..nowMillis } >= CONNECTION_HELP_CHURN_LIMIT
}
