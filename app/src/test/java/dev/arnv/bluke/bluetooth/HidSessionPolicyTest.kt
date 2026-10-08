package dev.arnv.bluke.bluetooth

import org.junit.Assert.*
import org.junit.Test

class HidSessionPolicyTest {
    @Test fun completedOrAutomaticRequestDoesNotRejectSettingsHost() {
        assertTrue(canAdoptIncomingHost("B", "A", false, false))
        assertTrue(canAdoptIncomingHost("A", null, false, false))
    }

    @Test fun explicitSelectionWinsOnlyWhilePending() {
        assertFalse(canAdoptIncomingHost("A", "B", true, false))
        assertTrue(canAdoptIncomingHost("B", "B", true, false))
        assertTrue(canAdoptIncomingHost("A", "B", false, false))
    }

    @Test fun teardownCannotPublishAnIncomingConnection() {
        assertFalse(canAdoptIncomingHost("A", null, false, true))
        assertFalse(canAdoptIncomingHost("A", "A", true, true))
    }

    @Test fun everyAcceptedRegistrationMustConfirmTeardownIncludingNormalRestart() {
        for (state in HidRegistrationAttempt.State.entries) {
            assertEquals(state in setOf(HidRegistrationAttempt.State.WAITING,
                HidRegistrationAttempt.State.REGISTERED, HidRegistrationAttempt.State.UNREGISTERING),
                needsConfirmedUnregistration(state))
        }
        assertFalse(needsConfirmedUnregistration(null))
    }
}
