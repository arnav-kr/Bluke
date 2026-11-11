# Changelog

All notable user-facing changes to Simply Bluke, a fork of [Bluke](https://github.com/arnav-kr/Bluke) by Arnav Kumar, are documented in this file. Entries before 1.1.1 come from the original Bluke project.
For detailed usage instructions, see the [Usage Guide](docs/USAGE.md).

## [Unreleased]

> [!IMPORTANT]
> **Pair again once:** This update adds horizontal scrolling to the touchpad, which changes what Simply Bluke advertises over Bluetooth. Forget the computer on your phone, remove Simply Bluke on the computer, and pair again. Until then, the touchpad may not work on that computer. The app shows a reminder on first launch.

### Added
- **Key press vibration:** The phone vibrates on each key press, even when the system touch feedback setting is off. Turn it on or off from the new vibration button in the keyboard toolbar, and choose Light, Medium or Strong in Keyboard settings.
- **Mac modifier keys:** A new PC/Mac button in the keyboard toolbar (also in Keyboard settings → Modifier keys) swaps Alt and Win so Command sits next to the space bar and Option outside it, labelled Cmd and Opt.
- **Natural scrolling setting:** Settings → Controls & connection → Natural scrolling chooses whether two-finger scrolling follows your fingers (natural, the default and the previous behavior) or works like a mouse wheel. It applies to the Touchpad mode, the keyboard's trackpad and the Multimedia touchpad.
- **Horizontal two-finger scrolling:** Swipe two fingers left or right to scroll sideways on every touchpad. Each swipe locks to one direction, so vertical scrolling doesn't drift sideways. It follows the natural scrolling setting and the scroll speed.

### Improved & Fixed
- **System bars stay visible:** Keyboard, Touchpad, Gamepad and Multimedia modes no longer hide the status and navigation bars, and their controls stay clear of the bars and the camera cutout instead of sliding under them.
- **Status bar icons match the app theme:** When the app's dark theme setting differs from the phone's, the status and navigation bar icons now stay readable.

### Removed
- **Key sounds:** The switch sound profiles, Mechvibes sound pack import, the sound button in the keyboard toolbar and the sound settings are gone. Imported sound packs are deleted from the phone on first launch.

---

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
