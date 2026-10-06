# Shared controller descriptor — 2026-10-06

## Current status

Revision 5 is implemented on `refactor` in `936c3d8`, with subsequent connection
and touchpad fixes. The user reported controls working on their respective tested
hosts after this change; that is not a complete OEM/OS/game matrix. See
[release notes](../CHANGELOG.md) and [upgrade instructions](USAGE.md#updating-and-pairing-caches).

## Rollback

The original Native/Web implementation is preserved in commit `9a5c132`, tagged
`checkpoint/native-web-before-shared-descriptor`. Its executable source is the
same as `526c4fa`. See [checkpoint instructions](NATIVE-WEB-CHECKPOINT.md).
The checkpoint commit and shared-descriptor implementation have been pushed to
`refactor`. The checkpoint tag is local; use the commit ID in other clones.

## Evidence and decision

The user confirmed that switching Web to Android fails after reconnect, but
forgetting both devices and pairing again **without restarting Bluke**, with
Android selected, restores the right stick. This demonstrates that a running
Bluke can advertise a usable Android descriptor; registration/reconnect alone
does not reliably refresh the receiver's input definition.

AOSP's [HID host connection implementation](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/bta/hh/bta_hh_act.cc)
can skip SDP discovery for known devices. ASSUMPTION: that caching path explains
the tested OEM behavior; we have no receiver HCI capture proving its exact path.

Simply replacing Rx with Rz in every mode would risk Windows browser regressions:
[Chromium Windows Raw Input](https://raw.githubusercontent.com/chromium/chromium/main/device/gamepad/raw_input_gamepad_device_win.cc)
assigns raw axis indices from HID usage minus 0x30, placing Rz at index 5, not 3.
Without a recognized device mapping, those indices are passed through.
[Android's documented game convention](https://developer.android.com/games/sdk/game-controller/controller-input)
uses Z/Rz, while [Chromium's Android generic mapping](https://raw.githubusercontent.com/chromium/chromium/main/device/gamepad/android/java/src/org/chromium/device/gamepad/GamepadMappings.java)
also accepts Rx for horizontal movement. Its newer selection prefers Rx over Z.

The implementation therefore advertises X/Y/Z/Rx/Rz in every mode. Only report
values and existing button/hat mappings change at runtime:

| Field (payload bytes) | Native / Web | Android |
| --- | --- | --- |
| X/Y (4–7) | Left stick | Left stick |
| Z (8–9) | Right horizontal | Right horizontal |
| Rx (10–11) | Right vertical | Right horizontal (browser fallback) |
| Rz (12–13) | Center | Right vertical |

Report ID remains 3. Buttons, hat, keyboard, mouse and consumer-control descriptor
fields are unchanged. The original first 12 payload bytes remain unchanged for
Native/Web; their extra Rz axis stays centered. Descriptor length is 239 bytes,
and gamepad payload length is 14 bytes, excluding report ID. Existing 8 ms report
cadence, permission handling, lifecycle policy and SDK/dependency versions stay
unchanged. Mode transitions no longer request HID teardown; existing lifecycle
recovery machinery is retained rather than rewritten in this experiment.

## Migration and risks

Descriptor revision advances from 4 to 5 using the existing update notice. Users
upgrading from an older descriptor must forget both devices and pair again once. No
app-data reset is needed. Subsequent mode changes advertise no different bytes.
Later revision-5 connection/touchpad updates need no additional pairing refresh.
Rolling back to a different descriptor requires another pairing refresh.

ASSUMPTION: retaining the original desktop usages/offsets preserves the intended
desktop controls. Games may enumerate or auto-bind the added axis differently;
Android apps may interpret mirrored Rx as an additional control. This is a test
build, not a claim of universal compatibility or a release-ready validation.

## Required physical verification

1. Refresh pairing once. Launch initially in Web; switch to Android while still
   connected, test both right-stick axes in Minecraft and an Android browser.
2. Repeat every mode transition, holding/releasing buttons and sticks; verify
   there is no reconnect, stuck input or gamepad-page navigation.
3. Cold-start in each mode, reconnect, background/resume, and toggle Bluetooth.
4. On Windows, test joy.cpl, Chromium and a native game in Native/Web. On Linux,
   test evtest, Chromium and SDL/Steam. Check the added axis stays centered in
   desktop modes and does not capture game bindings.
5. Verify the upgrade notice with revision 4 preferences, and clean installation.

No physical receiver was available to this run. Automated results are recorded
in AUDIT.md; they cannot establish host caching, OEM or game compatibility.
