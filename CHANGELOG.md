# Changelog

All notable user-facing changes to Simply Bluke, a fork of [Bluke](https://github.com/arnav-kr/Bluke) by Arnav Kumar, are documented in this file. Entries before 1.1.1 come from the original Bluke project.
For detailed usage instructions, see the [Usage Guide](docs/USAGE.md).

## [1.1.1] - 2026-11-11

> [!NOTE]
> **New app ID:** Simply Bluke now uses the package name `com.wylify.simplybluke`, so it installs as its own app next to the original Bluke. Settings from earlier builds of this fork don't carry over. Only one of the two apps should be connected to a host at a time.

### Added
- **Touch keyboard for portrait:** A 10-key-wide layout with taller keys, plus `?123` (symbols and navigation) and `Fn` (F1–F12 and media keys) pages. Modifiers stay in place on every page.
- **Sticky modifiers:** Tap Ctrl, Alt, Shift, Win or Fn to apply it to the next key, double-tap to lock it, or hold it as before. Can be turned off in Keyboard settings.
- **Trackpad with keyboard:** An optional trackpad above the keyboard in portrait, or beside it in landscape (left or right, switchable from the toolbar).
- **Minimal keyboard style:** Flat keys with less padding, so keys are larger and easier to hit.
- **Keyboard rotation setting:** Keyboard mode can follow the phone's rotation, or stay in portrait or landscape.

### Improved & Fixed
- **More accurate typing:** Each touch picks one key, the one under your finger or the nearest one, and a key preview bubble shows what you pressed.
- **No stuck keys:** Rotating the phone, closing the keyboard or leaving the app releases all held keys.
- **Fewer permissions:** Android 12 and newer no longer ask for location access.

### Changed
- **New name and icon:** The app is now called Simply Bluke and has a black-and-white icon.
- **Bluetooth name:** The input device now introduces itself to computers as "Simply Bluke".
- **About screen:** Credits the original developer, explains that this is a fork, and shows how to install the original Bluke. Removed the sponsor link and the Report issue and Feature request links that pointed to the original project.

---

## [1.1.0] - 2026-10-09

> [!IMPORTANT]
> **One-time re-pairing required:** Bluke 1.1 introduces a unified Bluetooth HID controller descriptor. When upgrading from 1.0.x, please unpair/forget Bluke on **both** devices and pair fresh using **Scan** in Bluke. This ensures your host operating system caches the updated descriptor correctly.

### Added
- **Multimedia Remote Mode:** A dedicated input surface combining media playback, slide presentation controls, an integrated touchpad, and optional hardware volume-button forwarding.
- **Unified Gamepad Profiles:** Seamless, live switching between **Native** (PC/Linux), **Android** (mobile games), and **Web** (browser gamepad tester) mappings without reconnecting or re-pairing.
- **6 New Switch Sound Profiles:** Expanded built-in mechanical switch acoustics to 12 total profiles, adding Turquoise Tealios, Gateron Black Inks, Cherry MX Blues, Cherry MX Blacks, SKCM Blue Alps, and NovelKeys Creams.
- **Custom Sound Pack Import:** Import and manage custom **Mechvibes ZIP audio sprite packs** directly from storage, with automatic audio decoding, duplicate detection, and deletion controls.
- **International Character Layouts:** Added support for AZERTY, QWERTZ, Dvorak, Colemak, and Russian (ЙЦУКЕН) typing layouts with on-screen `Shift + Space` quick-cycling.
- **Keyboard Customization Studio:** Design custom themes with a visual RGB/HSV color picker, case finishes (metallic/matte), plate accents, keycap groups, and individual key styling overrides.
- **Independent Geometries:** Physical layouts (60%, 65%, 75%) are now completely decoupled from visual themes.
- **Touchpad Side-Rail Modifiers:** Optional mechanical modifier key strip (Ctrl, Alt, Shift, Meta) on the left or right of the touchpad gesture surface.
- **Diagnostic Logging:** Opt-in Bluetooth lifecycle and HID throughput logging under Settings > Support for easier troubleshooting and bug reporting.

### Improved & Fixed
- **Reorganized Settings:** Streamlined hierarchy grouped into *Personalization*, *Controls & connection*, and *Support*, complete with customizable toolbar quick-cycles.
- **Reliable Tap-and-Drag:** Restored standard delayed-click tap-and-drag gesture for smooth, dependable window and file dragging across host platforms.
- **Robust Bluetooth Lifecycle:** Serialized connection coordinator, bounded registration timeouts, and proactive pairing recovery advice prevent stuck connection states.
- **Input Performance:** 125 Hz (8 ms) analog stick report sampling with optimized UI recomposition for low-latency input.
- **Adaptive Remote Layouts:** Multimedia controls dynamically adapt to phone orientation (landscape vs. upright/portrait) with intuitive rotation.

---

## [1.0.7] - 2026-06-21
- Build and configuration maintenance.

## [1.0.5] - 2026-06-20
- Gamepad mode enabled by default.
- Optimized dependencies and reduced application package size.

## [1.0.4] - 2026-06-20
- Added experimental Gamepad controller mode.

## [1.0.0] - 2026-06-05
- Initial public release of Bluke.
- Driverless Bluetooth HID keyboard and touchpad emulation.
- 6 mechanical switch sound profiles (Cherry MX Brown, Holy Panda, Alpaca, Kailh Box Navy, Buckling Spring, Topre).
- Keycap themes (Olivia, Dracula, Oblivion, Retro, Cafe, Mizu).
- Material You dynamic theming, OLED high-contrast dark theme, and system haptics.
