# Right-stick send diagnostics

The diagnostics record the Boolean returned by
`BluetoothHidDevice.sendReport()` after the call, rather than logging only the
attempted payload. A true result is not proof of delivery or host interpretation.

## Collecting evidence

1. In Settings > About, tap the Bluke logo five times to enable developer options.
2. Open Developer options > Developer Logs and clear the old logs.
3. Connect in Android mode. Move only the right stick: hold right, left, up, and
   down for two seconds each, releasing to center between directions.
4. Open Developer Logs, search for `BlukeHID`, and share or copy the results.
5. Repeat with the working and failing senders, ideally using the same receiver.

Alternatively, after enabling developer mode, use `adb logcat -s BlukeHID`.

Successful/repeated outcomes are sampled at most once per second per report ID;
success/failure transitions are logged immediately. `attempts` and `failures`
count calls since the previous emitted sample, while `Data` and axes describe
only the current sample. Brief movements can fall between samples, hence the
two-second holds. Logging is disabled when developer mode is off.

Descriptor revision 5 uses a 14-byte report ID 3: unsigned little-endian X/Y at
bytes 4–7, Z at 8–9, Rx at 10–11, and Rz at 12–13. Native/Web put right X/Y in
Z/Rx and center Rz. Android puts right X in both Z and Rx, and right Y in Rz.
Center is 32767; extremes are 0 and 65535. Each report log
includes the selected output mapping and connection epoch. These are app-side
metadata, not evidence of the descriptor cached by the receiving device.

Interpretation:

- Changing right X/Y and true send results: encoding is present and Android
  accepts sends; compare received reports and native host axes next.
- Unchanging right X/Y during held movement: investigate UI/state/encoding.
- False results or exceptions: investigate sender Bluetooth service/connection.

No physical OEM-device validation has been performed for this diagnostic change.

## Expanded lifecycle capture (2026-10-06)

Developer-mode logs also record visibility changes, connect/disconnect requests
and API acceptance, pairing/adapter broadcasts, proxy callbacks, registration
cleanup and acceptance, descriptor bytes and SHA-256, protocol/get-report/set-report
callbacks, virtual cable unplug, and manager shutdown. Each lifecycle event
includes monotonic milliseconds, thread, epoch, foreground/registration/mode
state, pending target, and published host. Copied/exported entries now preserve
wall-clock milliseconds and severity. Public connection callbacks do not expose
the HCI reason code: a disconnected callback must not be described as proof that
the remote host initiated the disconnect.

Enable developer mode before restarting Bluke, clear the logs, reproduce once,
then export **without a search filter** to retain both `BlukeHID` and
`BlukeLifecycle`. The in-memory viewer retains 1,000 entries. Report payloads
remain sampled; this is not a complete over-the-air capture. Avoid typing
passwords during capture: keyboard report bytes and Bluetooth addresses are
sensitive. Disable diagnostics and delete exports after debugging.

The supplied working/failing logs used different receiving phones. They cannot
isolate sender behavior without repeating against the same receiver.

## Receiver-side capture

The receiver is the Android phone running Minecraft/the browser, not the phone
running Bluke as the controller. Connect that receiver by USB and enable USB
debugging. While Bluke is connected, run:

```powershell
adb devices
adb shell dumpsys input > receiver-input.txt
adb shell dumpsys bluetooth_manager > receiver-bluetooth.txt
```

`dumpsys input` is a capability/mapping snapshot, not a live movement recording.
It may include the input device name, vendor/product identity, motion ranges,
and key-layout selection. For native kernel events, try:

```powershell
adb shell getevent -lp
adb shell getevent -lt /dev/input/eventN
```

Replace eventN with the controller node found by the first command; do not use
the touchscreen node. Move each stick axis separately, then stop with Ctrl+C.
Stock firmware may deny access. Do not root/unlock a phone just for this test.
An Android input-event diagnostic app can instead read `InputDevice` motion
ranges and `MotionEvent` axis values without kernel access. Browser testers
alone show the browser mapping, not all native Android axes.

## HCI capture without a PC

1. On the receiver, enable Android Developer options > Bluetooth HCI snoop log
   (full/enabled, where offered), then toggle Bluetooth off/on.
2. Connect Bluke in Android mode, hold the right stick in each direction for
   two seconds, and reproduce the disconnect if possible.
3. Immediately choose Developer options > Take bug report, preferably Full,
   and share the ZIP privately. Some OEMs omit or filter the HCI attachment.
4. Turn snoop logging off afterwards. Repeat with the other sender and the
   **same receiver**, naming each capture with sender, receiver and result.

For an SDP descriptor comparison, start capture before one fresh pairing.
Ordinary reconnects can reuse SDP data and omit the descriptor exchange. This
is diagnostic collection, not a proposed repeated-repair workaround.

With a PC, `adb bugreport receiver-bugreport.zip` provides the report. Capturing
the sender too helps compare accepted reports with transmitted packets. Bluke
cannot enable Android HCI snoop or access protected Bluetooth system logs using
its ordinary app permissions.

Bug reports/HCI logs can contain device identifiers and unrelated personal
information; do not upload them publicly. Use a short isolated test session.

Primary references:

- [AOSP Bluetooth debugging](https://source.android.com/docs/core/connect/bluetooth/verifying_debugging)
- [Android bug reports](https://developer.android.com/studio/debug/bug-report)
- [AOSP getevent](https://source.android.com/docs/core/interaction/input/getevent)
- [Android InputDevice API](https://developer.android.com/reference/android/view/InputDevice)

## Verification (2026-10-05)

`gradlew.bat assembleDebug testDebugUnitTest --console=plain` with the existing
Gradle user cache:

```text
BUILD SUCCESSFUL in 4m 5s
47 actionable tasks: 10 executed, 37 up-to-date
Configuration cache entry stored.
```

JUnit XML totals: 146 tests, zero failures, zero errors, zero skipped. Four new
tests cover throttling, failure/recovery transitions, independent report IDs,
and decoding actual unsigned little-endian payload axes. `git diff --check`
passed. Lint was not rerun for this diagnostic patch.

## Expanded logging verification (2026-10-06)

`gradlew.bat assembleDebug testDebugUnitTest lintDebug --console=plain`:

```text
BUILD SUCCESSFUL in 4m 6s
56 actionable tasks: 14 executed, 42 up-to-date
Configuration cache entry reused.
```

JUnit XML totals: 146 tests, zero failures/errors/skips. Lint XML: zero errors,
22 warnings (20 SDK/dependency/version availability notices, existing
ObsoleteSdkInt resource folder and UseKtx in KeyboardSoundSynthesizer). No
descriptor, mapping, dependency, SDK, or connection-policy change. Callback
logging has been compiled/linted but not exercised on physical devices.

APK: `bluke-1.1-bluetooth-lifecycle-diagnostics.apk`, SHA-256
`BF81345651BA8ED09701765A6050AC39436789A87039D154FF08E5D340EB6676`.
