# Changelog

All notable user-facing changes and improvements to Bluke are documented in this file.
For detailed usage instructions, see the [Usage Guide](docs/USAGE.md).

## [1.1.0] - 2026-10-xx

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
