package dev.arnv.bluke.bluetooth

/** Only a live manual request can override an incoming host; history is not intent. */
internal fun canAdoptIncomingHost(
    incoming: String,
    requested: String?,
    manualRequestPending: Boolean,
    disconnectingOrRecovering: Boolean,
): Boolean = !disconnectingOrRecovering &&
    (!manualRequestPending || requested == null || requested == incoming)

internal fun needsConfirmedUnregistration(state: HidRegistrationAttempt.State?): Boolean =
    state in setOf(
        HidRegistrationAttempt.State.WAITING,
        HidRegistrationAttempt.State.REGISTERED,
        HidRegistrationAttempt.State.UNREGISTERING,
    )
