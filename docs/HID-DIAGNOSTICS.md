# Right-stick send diagnostics

This diagnostic build does not change HID descriptors, mappings, registration,
connection handling, or report cadence. It records the Boolean returned by
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

Report ID 3 contains unsigned little-endian axes: left X/Y at bytes 4–7 and
right X/Y at bytes 8–11. Center is 32767; extremes are 0 and 65535. Each log
includes the requested registered mode and connection epoch. These are app-side
metadata, not evidence of the descriptor cached by the receiving device.

Interpretation:

- Changing right X/Y and true send results: encoding is present and Android
  accepts sends; compare received reports and native host axes next.
- Unchanging right X/Y during held movement: investigate UI/state/encoding.
- False results or exceptions: investigate sender Bluetooth service/connection.

No physical OEM-device validation has been performed for this diagnostic change.

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
