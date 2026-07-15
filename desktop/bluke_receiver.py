#!/usr/bin/env python3
"""Bluke WiFi receiver.

Companion agent for the Bluke Android app's WiFi Remote module. Listens on the
local network, and injects the keyboard / mouse input sent by the phone into
the operating system. Works on Windows, Linux (X11) and macOS.

Usage:
    pip install zeroconf pynput
    python bluke_receiver.py [--port 9570] [--pin 123456]

A PIN is printed at startup; enter it on the phone when connecting.

Wire protocol (must match WifiInputManager.kt):
    frame  = u16 big-endian length, then `length` bytes: [u8 type][payload]
    HELLO      0x01  client->server  [u8 protocolVersion][utf8 device name]
    AUTH       0x02  client->server  [utf8 PIN]
    AUTH_OK    0x03  server->client  [utf8 receiver name]
    AUTH_FAIL  0x04  server->client
    INPUT      0x10  client->server  [u8 reportId][HID report bytes]
    LED        0x20  server->client  [u8 led bitmask]
    PING       0x30  client->server
    PONG       0x31  server->client

INPUT payloads reuse Bluke's Bluetooth HID report formats:
    report 1 (keyboard): [modifiers][reserved][6 x key usage id]
    report 2 (mouse):    [buttons][dx][dy][wheel]   (signed deltas)
    report 3 (gamepad):  not injected (needs a virtual gamepad driver)
"""

import argparse
import random
import socket
import struct
import sys
import threading

try:
    from pynput.keyboard import Controller as KeyboardController, Key, KeyCode
    from pynput.mouse import Controller as MouseController, Button
except ImportError:
    sys.exit("Missing dependency: pip install pynput")

try:
    from zeroconf import ServiceInfo, Zeroconf
except ImportError:
    ServiceInfo = Zeroconf = None  # discovery is optional; manual IP still works

TYPE_HELLO = 0x01
TYPE_AUTH = 0x02
TYPE_AUTH_OK = 0x03
TYPE_AUTH_FAIL = 0x04
TYPE_INPUT = 0x10
TYPE_LED = 0x20
TYPE_PING = 0x30
TYPE_PONG = 0x31

DEFAULT_PORT = 9570
SERVICE_TYPE = "_bluke._tcp.local."

# ---------------------------------------------------------------------------
# HID keyboard usage id -> pynput key
# ---------------------------------------------------------------------------

HID_KEYS = {}
for i in range(26):  # 0x04..0x1D -> a..z
    HID_KEYS[0x04 + i] = KeyCode.from_char(chr(ord("a") + i))
for i, ch in enumerate("1234567890"):  # 0x1E..0x27
    HID_KEYS[0x1E + i] = KeyCode.from_char(ch)

HID_KEYS.update({
    0x28: Key.enter,
    0x29: Key.esc,
    0x2A: Key.backspace,
    0x2B: Key.tab,
    0x2C: Key.space,
    0x2D: KeyCode.from_char("-"),
    0x2E: KeyCode.from_char("="),
    0x2F: KeyCode.from_char("["),
    0x30: KeyCode.from_char("]"),
    0x31: KeyCode.from_char("\\"),
    0x32: KeyCode.from_char("#"),
    0x33: KeyCode.from_char(";"),
    0x34: KeyCode.from_char("'"),
    0x35: KeyCode.from_char("`"),
    0x36: KeyCode.from_char(","),
    0x37: KeyCode.from_char("."),
    0x38: KeyCode.from_char("/"),
    0x39: Key.caps_lock,
    0x46: Key.print_screen,
    0x47: Key.scroll_lock,
    0x48: Key.pause,
    0x49: Key.insert,
    0x4A: Key.home,
    0x4B: Key.page_up,
    0x4C: Key.delete,
    0x4D: Key.end,
    0x4E: Key.page_down,
    0x4F: Key.right,
    0x50: Key.left,
    0x51: Key.down,
    0x52: Key.up,
    0x53: Key.num_lock,
    0x54: KeyCode.from_char("/"),   # keypad
    0x55: KeyCode.from_char("*"),
    0x56: KeyCode.from_char("-"),
    0x57: KeyCode.from_char("+"),
    0x58: Key.enter,                # keypad enter
    0x63: KeyCode.from_char("."),
    0x65: Key.menu,
})
for i in range(12):  # 0x3A..0x45 -> F1..F12
    HID_KEYS[0x3A + i] = getattr(Key, f"f{i + 1}")
for i in range(9):  # 0x59..0x61 -> keypad 1..9
    HID_KEYS[0x59 + i] = KeyCode.from_char(str(i + 1))
HID_KEYS[0x62] = KeyCode.from_char("0")  # keypad 0

# modifier bit index (0..7, from modifier byte) -> key
HID_MODIFIERS = [
    Key.ctrl_l, Key.shift_l, Key.alt_l, Key.cmd_l,
    Key.ctrl_r, Key.shift_r, Key.alt_r, Key.cmd_r,
]

MOUSE_BUTTONS = [Button.left, Button.right, Button.middle]


def signed(b):
    return b - 256 if b > 127 else b


class InputInjector:
    """Turns Bluke HID reports into OS input events, tracking key state so a
    dropped connection can release everything it pressed."""

    def __init__(self):
        self.keyboard = KeyboardController()
        self.mouse = MouseController()
        self.prev_mods = 0
        self.prev_keys = set()
        self.prev_buttons = 0

    def handle(self, report_id, data):
        if report_id == 1:
            self._keyboard_report(data)
        elif report_id == 2:
            self._mouse_report(data)
        # report 3 (gamepad) needs a virtual gamepad driver (e.g. ViGEmBus); ignored

    def _keyboard_report(self, data):
        if len(data) < 8:
            return
        mods = data[0]
        keys = {b for b in data[2:8] if b}

        for bit in range(8):
            mask = 1 << bit
            was, now = self.prev_mods & mask, mods & mask
            if now and not was:
                self.keyboard.press(HID_MODIFIERS[bit])
            elif was and not now:
                self.keyboard.release(HID_MODIFIERS[bit])

        for usage in self.prev_keys - keys:
            key = HID_KEYS.get(usage)
            if key:
                self.keyboard.release(key)
        for usage in keys - self.prev_keys:
            key = HID_KEYS.get(usage)
            if key:
                self.keyboard.press(key)

        self.prev_mods = mods
        self.prev_keys = keys

    def _mouse_report(self, data):
        if len(data) < 4:
            return
        buttons, dx, dy, wheel = data[0], signed(data[1]), signed(data[2]), signed(data[3])

        if dx or dy:
            self.mouse.move(dx, dy)
        if wheel:
            self.mouse.scroll(0, wheel)
        for bit, button in enumerate(MOUSE_BUTTONS):
            mask = 1 << bit
            was, now = self.prev_buttons & mask, buttons & mask
            if now and not was:
                self.mouse.press(button)
            elif was and not now:
                self.mouse.release(button)
        self.prev_buttons = buttons

    def release_all(self):
        """Safety: called on disconnect so no key stays stuck down."""
        for usage in self.prev_keys:
            key = HID_KEYS.get(usage)
            if key:
                try:
                    self.keyboard.release(key)
                except Exception:
                    pass
        for bit in range(8):
            if self.prev_mods & (1 << bit):
                try:
                    self.keyboard.release(HID_MODIFIERS[bit])
                except Exception:
                    pass
        for bit, button in enumerate(MOUSE_BUTTONS):
            if self.prev_buttons & (1 << bit):
                try:
                    self.mouse.release(button)
                except Exception:
                    pass
        self.prev_mods = 0
        self.prev_keys = set()
        self.prev_buttons = 0


def read_frame(conn):
    header = recv_exact(conn, 2)
    if header is None:
        return None
    (length,) = struct.unpack(">H", header)
    if length < 1:
        return None
    frame = recv_exact(conn, length)
    if frame is None:
        return None
    return frame[0], frame[1:]


def recv_exact(conn, n):
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def send_frame(conn, ftype, payload=b""):
    conn.sendall(struct.pack(">H", len(payload) + 1) + bytes([ftype]) + payload)


def handle_client(conn, addr, pin, receiver_name):
    print(f"[+] Connection from {addr[0]}:{addr[1]}")
    injector = InputInjector()
    authed = False
    device_name = "?"
    try:
        while True:
            frame = read_frame(conn)
            if frame is None:
                break
            ftype, payload = frame

            if ftype == TYPE_HELLO:
                if len(payload) >= 1:
                    device_name = payload[1:].decode("utf-8", "replace") or "?"
                print(f"    Hello from '{device_name}' (protocol v{payload[0] if payload else '?'})")
            elif ftype == TYPE_AUTH:
                if payload.decode("utf-8", "replace").strip() == pin:
                    authed = True
                    send_frame(conn, TYPE_AUTH_OK, receiver_name.encode("utf-8"))
                    print(f"[+] '{device_name}' authenticated. Receiving input.")
                else:
                    send_frame(conn, TYPE_AUTH_FAIL)
                    print(f"[!] Wrong PIN from '{device_name}'. Closing.")
                    break
            elif ftype == TYPE_PING:
                send_frame(conn, TYPE_PONG)
            elif ftype == TYPE_INPUT:
                if authed and len(payload) >= 1:
                    injector.handle(payload[0], payload[1:])
            # unknown frame types are ignored (forward compatibility)
    except (ConnectionError, OSError) as e:
        print(f"[!] Connection error: {e}")
    finally:
        injector.release_all()
        try:
            conn.close()
        except OSError:
            pass
        print(f"[-] '{device_name}' ({addr[0]}) disconnected.")


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))  # no packets are actually sent
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


def advertise(port, receiver_name):
    if Zeroconf is None:
        print("[!] 'zeroconf' not installed - the phone won't auto-discover this PC.")
        print("    Install it (pip install zeroconf) or use manual connection.")
        return None
    ip = local_ip()
    info = ServiceInfo(
        SERVICE_TYPE,
        f"{receiver_name}.{SERVICE_TYPE}",
        addresses=[socket.inet_aton(ip)],
        port=port,
        properties={"ver": "1"},
    )
    zc = Zeroconf()
    zc.register_service(info)
    print(f"[+] Advertising as '{receiver_name}' via mDNS")
    return zc


def main():
    parser = argparse.ArgumentParser(description="Bluke WiFi receiver")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--pin", default=None, help="fixed PIN (random if omitted)")
    parser.add_argument("--name", default=None, help="receiver name shown on the phone")
    args = parser.parse_args()

    pin = args.pin or f"{random.SystemRandom().randint(0, 999999):06d}"
    receiver_name = args.name or f"Bluke Receiver ({socket.gethostname()})"

    print("=" * 52)
    print("  Bluke WiFi Receiver")
    print(f"  Address : {local_ip()}:{args.port}")
    print(f"  PIN     : {pin}")
    print("  Enter this PIN in the Bluke app (WiFi Remote).")
    print("=" * 52)

    zc = advertise(args.port, receiver_name)

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", args.port))
    server.listen(1)

    try:
        while True:
            conn, addr = server.accept()
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            # one client at a time keeps injected input unambiguous
            t = threading.Thread(target=handle_client, args=(conn, addr, pin, receiver_name), daemon=True)
            t.start()
            t.join()
    except KeyboardInterrupt:
        print("\n[-] Shutting down.")
    finally:
        if zc:
            zc.close()
        server.close()


if __name__ == "__main__":
    main()
