# Changelog

All notable user-facing changes to Simply Bluke, a fork of [Bluke](https://github.com/arnav-kr/Bluke) by Arnav Kumar, are documented in this file. Entries before 1.1.1 come from the original Bluke project.
For detailed usage instructions, see the [Usage Guide](docs/USAGE.md).

## [Unreleased]

### Added
- **Share keyboard themes:** Every theme's ⋮ menu has **Share**. It shows a QR code and can send the theme as a QR image, a `.json` file, or a readable `.md` file (color table plus the data), or save the file to your device. Themes carry colors only, not layout settings, and nothing is uploaded: the QR code holds the whole theme.
- **Import themes:** Settings → Keyboard → Themes → **Import** reads a `.json` or `.md` theme file, or a QR code from a screenshot or photo. Opening a theme file from another app also offers to import it. You see a preview first; imported themes get their own copy and never replace an existing one (a number is added if the name is taken).
- **New theme from scratch:** **New theme** can start from one color (a dark or light keyboard is built around it, with readable key text), from a blank grey keyboard, or as a copy of the current theme.
- **Touch layout preview:** The theme editor can preview the Touch layout (ABC, ?123 and Fn pages) as well as the physical layouts, on flat keys or keycaps, on the theme's own case color.
- **Scan theme QR codes in the app:** Themes has a scan button (also in Import → **Scan a QR code**) that reads a theme QR code from another phone's screen or a printout with the camera, then shows the import preview. The camera permission is asked only when you first scan, and is optional: picture import still works without it. Frames are read on the phone and never saved or sent. Includes a flashlight button for dark rooms.
- **Readable text check:** The editor shows each key group's text contrast and warns when key text is hard to read, with a one-tap fix.

### Improved & Fixed
- Custom themes are read from storage once and kept in memory instead of on every screen, and one damaged theme no longer hides the others.
- The Touchpad mode's modifier keys now follow a theme change as soon as you come back to it.

## [1.1.8] - 2026-11-15

### Added
- **Trackpad button in the keyboard toolbar:** Turn the keyboard's trackpad on or off straight from the top bar, without opening Settings.
- **Laptop layout for tablets and foldables:** On wide screens (600dp and up, like a tablet or an unfolded foldable), Settings → Keyboard → **Laptop layout** keeps your full keyboard layout with the trackpad on, in portrait and landscape. The trackpad spans the screen below the keyboard like a laptop, or above it; the toolbar button switches between the two.
- **Resizable keyboard:** Drag the handle just above the keyboard to set how much of the screen it takes (15–90% of the screen height). With the trackpad above it, the trackpad gets the rest. Portrait and landscape each remember their own height; double-tap the handle to go back to the automatic height.
- **Settings button in every top bar:** A gear at the right end of the Keyboard, Touchpad, Gamepad and Multimedia top bars opens that mode's settings (Keyboard settings, Controller settings, or the main settings for Touchpad and Multimedia).

### Improved & Fixed
- **One trackpad setting:** Settings → Keyboard now has a single "Trackpad with keyboard" switch for both the on-screen keyboard and hardware keyboard mode, instead of one for each. Until you change it, the trackpad shows in hardware keyboard mode and stays hidden otherwise.
- **Layout button explains itself:** When the Touch keyboard is in use (portrait with the Touch layout, or landscape with the trackpad on), the layout button is greyed out, since switching layouts would change nothing. Tapping it shows a short message saying why and how to get the layouts back.
- **New trackpad look:** Every trackpad (Touchpad mode, the one next to the keyboard and the media remote) is now OLED black with viewfinder-style corner brackets, edge ticks and a small centre reticle, drawn in your Material theme colour. The brackets brighten while you touch, and the touch point shows as a ring in the same colour.

## [1.1.6] - 2026-11-13

### Added
- **Update check (opt-in):** Settings → About → Updates can check GitHub for a new Simply Bluke release while the app is open: daily, every 3 days, weekly (default), every 2 weeks or monthly. When there is one, a dialog shows what's new and opens the APK download only if you tap Download. Skip hides that version. The first time you open the app, it asks whether to turn this on. It stays off unless you say yes, never runs in the background, and waits while a host is connected so it can't interrupt typing. This adds the Internet permission, used only for this check.

## [1.1.5] - 2026-11-12

### Added
- **Portrait for every mode:** Touchpad, Gamepad and Multimedia now turn upright with the phone, like the keyboard. Gamepad puts the shoulder buttons on top and the sticks at the bottom, under your thumbs. Multimedia stacks the media keys and arrows above a large touchpad. Layout edits made in Gamepad's Edit Layout are saved separately for portrait and landscape. The setting is now Settings → Keyboard → **Screen rotation** and applies to all modes; choose "Always landscape" to keep the old behavior.

### Improved & Fixed
- **Bigger toolbar buttons:** The bar at the top of every mode has larger buttons, icons and labels that are easier to hit. Caps, Num and Scroll Lock each get their own button, and the bar scrolls sideways when it doesn't fit.
- **Minimal keyboard by default:** The keyboard now uses the Minimal style unless you've picked one. If you never chose a style, your keyboard switches to Minimal; pick Mechanical in Settings → Keyboard to go back.

## [1.1.4] - 2026-11-12

### Added
- **Hardware keyboard mode:** On phones with a keyboard, like the Clicks Communicator or Unihertz Titan 2 Elite, or with a keyboard attached, the phone's keys type on the computer and the on-screen letters are hidden. A small strip keeps Esc, Tab, Ctrl, Win/Cmd, Alt/Opt, arrows, navigation keys and an F1–F12 page, and a trackpad fills the rest of the screen. The phone's Alt key types the symbols printed on its keys. Turns on automatically when a keyboard is found; change it in Settings → Keyboard → Hardware keyboard.

## [1.1.3] - 2026-11-12

### Added
- **Touchpad settings:** Settings → Controls & connection → Touchpad now has pointer speed and scroll speed sliders (0.25x to 3x), a **Gesture sensitivity** choice (Low, Medium, High) for how easily taps and scrolls are picked up, and switches for the new scrolling options below. Changes apply straight away to the Touchpad mode, the keyboard's trackpad and the Multimedia touchpad.
- **Scroll in any direction:** A two-finger swipe that starts diagonally scrolls both ways at once. A swipe that starts straight still stays on its line, and turning firmly sideways frees it.
- **Scroll glide:** Lift both fingers while still moving and the scroll coasts to a stop. Touch the pad to stop it. Can be turned off.
- **Shift + wheel sideways scrolling:** An alternative for computers where sideways scrolling does nothing because they were paired before 1.1.2. It needs no new pairing.

### Improved & Fixed
- **No pointer jump after scrolling:** Lifting one finger at the end of a two-finger scroll no longer makes the pointer jump or drift.
- **Two-finger taps don't scroll, and scrolls don't right-click:** Scrolling starts only after the fingers have moved a little, and a short scroll no longer ends in a right-click.
- **Smoother touchpad:** The finger markers no longer redraw the whole touchpad on every movement.

---

## [1.1.2] - 2026-11-11

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
