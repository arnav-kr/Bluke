package dev.arnv.bluke.bluetooth

internal const val HID_DESCRIPTOR_REVISION_PREFERENCE = "hid_descriptor_revision"
// 6: horizontal scrolling (AC Pan) added to the mouse report.
internal const val CURRENT_HID_DESCRIPTOR_REVISION = 6

internal fun requiresHidDescriptorRefresh(
    savedRevision: Int,
    isExistingInstallation: Boolean,
): Boolean = isExistingInstallation && savedRevision < CURRENT_HID_DESCRIPTOR_REVISION
