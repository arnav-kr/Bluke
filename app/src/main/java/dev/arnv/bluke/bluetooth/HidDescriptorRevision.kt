package dev.arnv.bluke.bluetooth

internal const val HID_DESCRIPTOR_REVISION_PREFERENCE = "hid_descriptor_revision"
internal const val CURRENT_HID_DESCRIPTOR_REVISION = 4

internal fun requiresHidDescriptorRefresh(
    savedRevision: Int,
    isExistingInstallation: Boolean,
): Boolean = isExistingInstallation && savedRevision < CURRENT_HID_DESCRIPTOR_REVISION
