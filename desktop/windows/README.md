# Bluke WiFi Receiver — Windows (native)

A single self-contained `.exe` receiver for the Bluke app's **WiFi Remote**
module. No Python, no .NET, no runtime — just one native executable that injects
keyboard and mouse input via the Win32 `SendInput` API and advertises itself
over mDNS so the phone discovers it automatically.

This is the recommended receiver for Windows. (The cross-platform Python script
in [`../bluke_receiver.py`](../bluke_receiver.py) works too, and is the option
for Linux/macOS.)

## Get the receiver

**Option A — download** a prebuilt `bluke_receiver.exe` from the project
releases (if provided) and just run it.

**Option B — build it yourself** (needs [mingw-w64](https://www.mingw-w64.org/)
gcc on PATH):

```bat
build.bat
```

or directly:

```bat
gcc -O2 -o bluke_receiver.exe main.c -lws2_32 -luser32
```

## Run

```bat
bluke_receiver.exe
```

It prints the PC's IP address and a random 6-digit **PIN**:

```
====================================================
  Bluke WiFi Receiver (Windows)
  Address : 192.168.1.2:9570
  PIN     : 424242
  Enter this PIN in the Bluke app (WiFi Remote).
====================================================
```

On the phone: open Bluke → tap the **WiFi icon** in the top bar → the PC appears
under *Receivers on this network* → **Connect** and enter the PIN (only the
first time per PC). If it doesn't appear (some routers block mDNS), use *Manual
connection* with the printed IP and PIN.

Both devices must be on the same network.

**First run:** Windows will show a firewall prompt — allow access on private
networks so the phone can reach the receiver.

## Options

| Flag | Description |
|------|-------------|
| `--port N` | Listening port (default `9570`) |
| `--pin XXXXXX` | Use a fixed PIN instead of a random one |
| `--name "My PC"` | Receiver name shown on the phone |

## What works

- Keyboard: full HID usage → virtual-key mapping (letters, digits, symbols,
  function keys, navigation, numpad, modifiers), with correct press/release
  diffing and an automatic release-all on disconnect so no key sticks.
- Mouse: relative movement, left/right/middle buttons, scroll wheel.

## Current limitations

- Gamepad reports are received but not injected (would need a virtual gamepad
  driver such as ViGEmBus).
- Caps/Num/Scroll lock LED state isn't reported back to the phone yet.
- Keys are injected by virtual-key code, so they follow the PC's active keyboard
  layout (same as a normal USB keyboard would).
- PIN-authenticated but unencrypted — use on trusted networks.

## How it works

See the heavily-commented [`main.c`](main.c). The wire protocol matches
`app/src/main/java/dev/arnv/bluke/network/WifiInputManager.kt`, and the `INPUT`
frames carry the exact same HID report bytes Bluke sends over Bluetooth, so both
transports behave identically.
