<div align="center">

<img src="Logo.svg" width="128" height="128" alt="Bluke Logo" />

# Bluke

Bluke turns a compatible Android phone into a Bluetooth keyboard, touchpad,
gamepad and multimedia remote.

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg?style=for-the-badge)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/arnav-kr/Bluke?style=for-the-badge&color=orange)](https://github.com/arnav-kr/Bluke/releases)

</div>

---

## Features

* **No host app required**: Connect directly using Android's Bluetooth HID Device API.
* **Four input modes**: Keyboard, Touchpad, Gamepad and Multimedia, with Native/Android/Web controller mappings.
* **Switch sound synthesis**: Generates mechanical switch acoustics (Cherry MX Brown, Holy Panda, Alpaca, Kailh Box Navy, Buckling Spring, and Topre) in real-time.
* **Keyboard themes**: Includes built-in presets and editable custom themes with per-theme case, plate, keycap, and legend colors.
* **System integration**: Supports system haptics, OLED black mode, and Material You dynamic color schemes.
* **Custom sounds and layouts**: Import supported Mechvibes packs and choose typing layouts independently of keyboard geometry and theme.

[Changelog](CHANGELOG.md) · [Usage & troubleshooting](docs/USAGE.md)

## Requirement

- **Android 9 (API level 28) or higher** with firmware supporting [Bluetooth HID Device](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice).
- A compatible Bluetooth host. Support varies by firmware, receiving OS and app; not all phones, TVs or consoles are supported.


## Build and Installation

### Prerequisites

- Android Studio/JDK compatible with the project's pinned Android Gradle Plugin.
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
   ./gradlew assembleDebug
   ```

3. **Install on device**:
   Enable USB Debugging on your Android phone and install the app via Android Studio or run:
   ```bash
    ./gradlew installDebug
    ```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`. The debug APK
is at `app/build/outputs/apk/debug/app-debug.apk`.

## License

This project is licensed under the [AGPL-3.0](LICENSE)

## Credits

* **[kbsim](https://github.com/tplai/kbsim)**: The user interface design is inspired by their web keyboard simulator ([kbs.im](https://kbs.im)), and the mechanical switch audio assets are sourced from their project.

## Author

- **Arnav Kumar** ([@arnav-kr](https://github.com/arnav-kr))
