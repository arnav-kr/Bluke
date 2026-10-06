# Changelog

User-facing changes in Bluke. Commits on `refactor` are not published releases;
see [GitHub Releases](https://github.com/arnav-kr/Bluke/releases) for availability.
Technical investigation and test history are in [the audit](docs/AUDIT.md).

## 1.1 — Unreleased

Notes for the current `refactor` branch (version code 10).

### Upgrade notice

- Upgrading from an older HID descriptor requires forgetting the pairing on
  **both devices** and pairing again once. Keep Bluke open and use **Scan**.
  The app includes a descriptor-update notice. Clearing app data is not required.
- Builds already using shared descriptor revision 5 do not need another pairing
  refresh for the connection or touchpad fixes below.
- Native, Android and Web now share one descriptor. Switching modes changes
  reports only: no HID restart or re-pairing on each mode change.

### Added

- **Android** and **Web** controller mappings alongside Native. Android maps the
  right stick for Android hosts; Web reports D-pad directions as buttons 12–15.
- **Multimedia** mode with playback, volume, navigation and presentation controls,
  a touchpad, and adaptive landscape/upright controls. Optional phone-volume-button
  forwarding works while Multimedia is open.
- AZERTY, QWERTZ, Dvorak, Colemak and Russian ЙЦУКЕН typing layouts alongside
  QWERTY. On-screen **Shift + Space** cycles Bluke's typing layouts; the host's
  input language remains separately configured.
- Custom keyboard themes with live preview, per-theme case styling and group/
  individual-key customization. Keyboard geometry and theme are separate choices.
- Mechvibes ZIP sound-pack import, including supported audio-sprite packs,
  duplicate-import feedback and deletion of imported packs. Built-ins cannot
  be deleted.
- Touchpad modifier-key side rails and Fn media legends that do not cover the
  function keys.
- Expanded opt-in HID send-result and Bluetooth lifecycle diagnostics.

### Improved and fixed

- Reorganized settings into Personalization, Controls & connection, and Support.
  Layout, theme and sound quick-cycle membership is configured in each native
  list; at least one choice remains enabled and selection stays in the cycle.
- Improved keyboard previews, compact theme actions, forced-RTL keyboard behavior
  and restoration of saved sound preferences after updates.
- Refined Multimedia spacing, orientation handling, button feedback, toolbar
  controls and upright media icons. Back from an input mode returns to Home.
- Improved first-use Gamepad guidance, reconnect controls and unsupported-HID
  recovery screens, retaining foreground-service support for background/lock sessions.
- Automatic reconnect yields to an already connected host. Ordinary Disconnect
  no longer restarts HID; proxy state can reconcile missing connection callbacks.
- Serialized HID recovery, bounded registration waits and clearer recovery errors
  address stale connected states and competing connection attempts.
- Added conditional pairing advice and a Scan action after failed host connections,
  including observed Settings-initiated handshakes. The suggestion is not proof
  that pairing through Settings caused a failure.
- Restored main's touchpad **tap, then touch again and drag** sequence: delay the
  first click, hold left when movement begins and release on lift. Cancellation
  clears delayed clicks and held buttons.
- Sampled analog gamepad reports at 8 ms intervals, reduced unnecessary
  recomposition and batched custom-controller layout persistence.

### Removed from development builds

- Retired the experimental gyro-mouse and combined keyboard+touchpad modes.
  Current modes: Keyboard, Touchpad, Gamepad and Multimedia.

### Compatibility and validation

- Firmware must expose Android's HID Device role. Brand/model alone is not a
  reliable compatibility verdict; Retry remains available.
- Browser games may use different mappings or ignore D-pad input even when a
  tester recognizes it. Host OS, game and firmware testing is still required;
  this is not a universal compatibility claim for TVs, consoles, macOS or iOS.
- Latest code verification: **183 passing tests**, **0 lint errors**, **22 warnings**.
  The full physical OEM/host matrix remains incomplete.
- See [usage and troubleshooting](docs/USAGE.md). App-data reset is not part of
  the normal upgrade procedure.
