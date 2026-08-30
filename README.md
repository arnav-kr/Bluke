<div align="center">

<img src="Logo.svg" width="128" height="128" alt="Bluke Logo" />

# Bluke

Bluke turns your Android device into a driverless, wireless Bluetooth HID mechanical keyboard simulator.

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg?style=for-the-badge)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/arnav-kr/Bluke?style=for-the-badge&color=orange)](https://github.com/arnav-kr/Bluke/releases)

</div>

---

## Features
* **No host software required**: Uses Android's native Bluetooth HID profile to connect directly with Windows, macOS, Linux, ChromeOS, Android TV, and game consoles.
* **Switch sound synthesis**: Generates mechanical switch acoustics (Cherry MX Brown, Holy Panda, Alpaca, Kailh Box Navy, Buckling Spring, and Topre) in real-time.
* **Themes and case colors**: Includes built-in presets (Olivia, Dracula, Oblivion, Retro, Cafe, and Mizu) and selectable case colors.
* **System integration**: Supports system haptics, OLED black mode, and Material You dynamic color schemes.
* **Bring your own keyboard**: Optionally type with your installed IME (Gboard, SwiftKey, Samsung Keyboard, ...) instead of the on-screen keycaps, so autocorrect, word suggestions, and the clipboard and translate panels stay available. Committed text is relayed to the host as keystrokes.
* **Host keyboard layouts**: Because HID transmits key positions rather than characters, the host's own layout decides what arrives. Select it from US QWERTY, UK, German, Hungarian, French AZERTY, Italian, Spanish, Swedish/Finnish, Norwegian or Danish to type accented characters correctly. See the note below on verification.
* **Clipboard relay**: Send your phone's clipboard to the host as keystrokes - useful for passwords, URLs, or text your host layout cannot otherwise reach.
* **Unicode entry fallback**: Optionally type characters with no key on the host layout (emoji, Greek, ...) via the host OS's Unicode escape hatch - Linux IBus `Ctrl+Shift+U`, Windows Alt+Numpad, or macOS Unicode Hex Input.

> [!NOTE]
> Only the **US QWERTY** layout table is verified against real hardware. The other layouts are derived from each locale's published layout and are marked *Unverified* in settings; please report any character that comes out wrong. The Unicode entry modes depend on host OS features that are not available in every application - the clipboard relay is the more dependable route for important text.


## Requirement
- **Android 9 (API level 28) or higher** (where the `BluetoothHidDevice` API was introduced).
- A device with hardware support for the **Bluetooth HID Device (HID-over-GATT)** profile. Note that profile availability may vary based on device chipset and manufacturer ROM.


## Build and Installation
### Prerequisites
- Android Studio Koala or higher.
- Android SDK 36.
- Global Gradle or Gradle Wrapper installed.

### Setup and Compilation
1. **Clone the repository**:
   ```bash
   git clone https://github.com/arnav-kr/Bluke.git
   cd Bluke
   ```

2. **Build with Gradle**:
   ```bash
   gradle assembleDebug
   ```

3. **Install on device**:
   Enable USB Debugging on your Android phone and install the app via Android Studio or run:
   ```bash
    gradle installDebug
    ```

## License
This project is licensed under the [AGPL-3.0](LICENSE)

## Credits
* **[kbsim](https://github.com/tplai/kbsim)**: The user interface design is inspired by their web keyboard simulator ([kbs.im](https://kbs.im)), and the mechanical switch audio assets are sourced from their project.

## Author
- **Arnav Kumar** ([@arnav-kr](https://github.com/arnav-kr))
