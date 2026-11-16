# Privacy Policy for Bluke

Last updated: November 16, 2026

**Bluke** ("the App") is committed to protecting your privacy. This Privacy Policy explains how the App handles user information and data.

---

## 1. Information Collection and Use

Bluke operates as a standalone tool that turns your Android device into a Bluetooth HID keyboard. 

* **No Personal Data Collection**: The App does not collect, store, transmit, or share any personal information, such as your name, email address, phone number, location, or device identifiers.
* **No Keystroke Logging**: The App emulates a physical keyboard and processes your key taps locally and in real time. Your keystrokes are transmitted directly to your paired target device (e.g., PC, Mac, Smart TV) via Bluetooth HID and are **never** logged, saved, cached, or transmitted over the internet or to any third party.
* **No Telemetry or Analytics**: The App does not contain any tracking software, telemetry, cookies, or third-party analytics libraries.
* **Optional Update Check**: If you turn on "Check for updates" (the App asks once when first opened; it is off unless you agree), the App asks GitHub's public API for the latest Simply Bluke release while the App is open, at most once per interval you choose. The request contains no personal data or keystrokes; like any web request, GitHub sees your IP address. Nothing is downloaded unless you tap Download, which opens the link in your browser.

---

## 2. Permissions Required

To function properly, Bluke requires the following system permissions on your Android device:

* **Bluetooth / Bluetooth Connect / Bluetooth Advertise / Bluetooth Scan**: Used exclusively to discover, pair with, and connect to host devices (PCs, Macs, TVs, etc.) as a Bluetooth HID device.
* **Vibrate**: Used solely to provide tactile feedback (haptics) when keys are pressed.
* **Internet**: Used only by the optional update check described above. It is never used while the update check is off, except when you tap "Check now".
* **Camera** (optional): Used only while the keyboard theme QR scanner is open, to read a theme QR code. The App asks for it the first time you tap Scan, and you can say no and import a QR code from a picture instead. Camera frames are read on your device and discarded right away; no picture is saved, shared, or sent anywhere.

None of these permissions are used to collect data or track your behavior.

---

## 3. Data Transmission and Security

All communication between the App and your paired device occurs directly over local Bluetooth connections. Your input never goes over the network. The only network request the App can make is the optional update check, which talks to api.github.com over HTTPS and sends nothing about you or your input.

---

## 4. Third-Party Services

The App does not integrate with any ad networks or cloud databases. The only third-party service it can contact is GitHub, and only when you turn on the update check or tap "Check now". GitHub's own privacy statement applies to that request.

---

## 5. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. Any changes will be posted directly within the App's source repository with an updated date at the top of this document.

---

## 6. Contact Us

If you have any questions or feedback regarding this Privacy Policy, please open an issue in the official source code repository.
