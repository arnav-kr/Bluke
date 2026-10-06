<div align="center">

<img src="Logo.svg" width="128" height="128" alt="Bluke Logo" />

# Bluke

Bluke turns your Android phone into a Bluetooth keyboard, touchpad, gamepad, and multimedia remote without needing any host companion software.

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg?style=for-the-badge)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/arnav-kr/Bluke?style=for-the-badge&color=orange)](https://github.com/arnav-kr/Bluke/releases)

</div>

---

## Features

- Direct Bluetooth HID connection with no companion apps or server software running on the host
- Four input modes: Keyboard, Touchpad, Gamepad, and Multimedia remote
- 12 mechanical switch sound profiles, plus support for importing custom Mechvibes sound packs
- Multiple typing layouts (QWERTY, AZERTY, QWERTZ, Dvorak, Colemak, Russian ЙЦУКЕН) with `Shift + Space` cycling
- Multiple keyboard geometries (60%, 65%, 75%) and custom themes with case, plate, and keycap styling
- Gamepad controller with live switching between Native, Android, and Web mappings
- Multimedia remote with presentation controls, touchpad, and phone volume button forwarding
- Built for peak UX with haptic feedback, OLED black mode, and Material You dynamic theming

## Requirements

- **Android 9 (API level 28) or higher** with firmware supporting the Bluetooth HID Device profile. Profile availability depends on device chipset and OEM ROM.
- A compatible Bluetooth host (Windows, Linux, Android, ChromeOS). Apple and macOS devices are currently unsupported / future scope.

## Build and Installation

### Prerequisites

- Android Studio / JDK compatible with the project's Android Gradle Plugin.
- Android SDK 36.1, as configured in `app/build.gradle.kts`.
- The included Gradle Wrapper.

### Setup and Compilation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/arnav-kr/Bluke.git
   cd Bluke
   ```

2. **Build with Gradle**:
   ```bash
   ./gradlew assembleDebug      # Linux / macOS
   .\gradlew.bat assembleDebug  # Windows PowerShell
   ```

3. **Install on device**:
   Enable USB Debugging on your Android phone and run:
   ```bash
   ./gradlew installDebug
   ```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## Star History

<a href="https://www.star-history.com/?repos=arnav-kr%2FBluke&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=arnav-kr/Bluke&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=arnav-kr/Bluke&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=arnav-kr/Bluke&type=date&legend=top-left" />
 </picture>
</a>

## License

This project is licensed under the [AGPL-3.0](LICENSE).

## Credits

* **[kbsim](https://github.com/tplai/kbsim)**: Part of mechanical switch audio assets are sourced from [kbs.im](https://kbs.im).

## Contributors

<a href="https://github.com/arnav-kr/Bluke/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=arnav-kr/Bluke" alt="Bluke Contributors" />
</a>

 See the [full contributor graph](https://github.com/arnav-kr/Bluke/graphs/contributors).