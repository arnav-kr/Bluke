package dev.arnv.bluke.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HidDescriptorRevisionTest {
    @Test
    fun existingInstallWithOldDescriptorRequiresPairingRefresh() {
        assertTrue(
            requiresHidDescriptorRefresh(
                savedRevision = CURRENT_HID_DESCRIPTOR_REVISION - 1,
                isExistingInstallation = true,
            )
        )
    }

    @Test
    fun currentOrFreshInstallDoesNotRequirePairingRefresh() {
        assertFalse(
            requiresHidDescriptorRefresh(
                savedRevision = CURRENT_HID_DESCRIPTOR_REVISION,
                isExistingInstallation = true,
            )
        )
        assertFalse(
            requiresHidDescriptorRefresh(
                savedRevision = 0,
                isExistingInstallation = false,
            )
        )
    }
}
