# Bluke WiFi Receiver

Companion agent for the **WiFi Remote** module of the Bluke Android app.

Bluetooth HID needs no software on the PC because the OS natively understands
"keyboard". Networks are different: no OS accepts keyboard input from a raw
socket, so using Bluke over WiFi requires this small receiver running on the
computer. It listens on the LAN, advertises itself via mDNS (so the phone can
discover it automatically), and injects the received keyboard / mouse input
into the operating system.

## Which receiver to use

- **Windows** → use the native single-`.exe` receiver in [`windows/`](windows/).
  No Python or runtime needed. (The Python script below also works on Windows if
  you prefer.)
- **Linux / macOS** → use the cross-platform Python script below.

## Quick start (Python — cross-platform)

```bash
pip install zeroconf pynput
python bluke_receiver.py
```

The receiver prints its IP address and a random 6-digit **PIN**.

On the phone: open Bluke → tap the **WiFi icon** in the top bar → the PC should
appear under *Receivers on this network* → tap **Connect** and enter the PIN
(only needed the first time per PC). If discovery doesn't work on your network
(some routers block mDNS), use *Manual connection* with the printed IP and PIN.

Both devices must be on the same network (e.g. the same WiFi).

## Options

| Flag | Description |
|------|-------------|
| `--port N` | Listening port (default `9570`) |
| `--pin XXXXXX` | Use a fixed PIN instead of a random one |
| `--name "My PC"` | Receiver name shown on the phone |

## Platform notes

- **Windows**: works out of the box. Allow the app through the firewall prompt
  on first run (private networks is enough).
- **Linux**: works on X11. On Wayland, `pynput` needs additional setup
  (see the pynput documentation).
- **macOS**: grant the terminal *Accessibility* permission
  (System Settings → Privacy & Security → Accessibility).

## Protocol

TCP, length-prefixed binary frames: `u16 length | u8 type | payload`.
The `INPUT` frames carry the exact same HID report bytes Bluke sends over
Bluetooth (report 1 = keyboard, 2 = mouse, 3 = gamepad), so the two transports
stay behaviourally identical. Full details in the docstring of
[`bluke_receiver.py`](bluke_receiver.py) and in
`app/src/main/java/dev/arnv/bluke/network/WifiInputManager.kt`.

## Current limitations

- Gamepad reports are received but not injected (a virtual gamepad driver such
  as ViGEmBus would be required on Windows).
- Caps/Num/Scroll lock LED state is not reported back to the phone yet, so the
  on-screen lock indicators follow local presses instead of the host state.
- The connection is PIN-authenticated but not encrypted; use it on networks
  you trust.
