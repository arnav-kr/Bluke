# Bluke 1.1 usage and troubleshooting

These instructions describe the current `refactor` build, not a guarantee of
availability in a published release. See [the changelog](../CHANGELOG.md).

## Connect a host

1. Enable Bluetooth on the phone running Bluke and the receiving host.
2. Open Bluke, grant the requested Bluetooth permissions and make the host
   discoverable using its Bluetooth settings.
3. Use **Scan** in Bluke, select the host and approve pairing on both devices.
   The operating system may request a PIN instead of a confirmation.
4. Once connected, launch Keyboard, Touchpad, Gamepad or Multimedia.

Pairing and a working HID connection are separate steps. Being listed as paired
in Settings does not by itself mean input is connected. If an attempt fails
after pairing through Settings, forget the pairing on both devices and retry
using Bluke's Scan button. This is a recovery suggestion, not a rule that all
Settings-initiated connections fail.

For an HID service error, use **Restart HID Service** and let recovery finish.
If firmware never acknowledges cleanup, toggling Bluetooth may still be necessary.
Repeated tapping cannot force Android to complete a pending operation. The
unsupported-device screen provides **Retry**; not every timeout proves incompatibility.

## Updating and pairing caches

The shared controller descriptor is revision **5**. Pairings made with an older
descriptor may retain old input definitions. Forget each device on the other,
then pair once through Bluke after the update notice. Do not clear Bluke's app
data just to refresh the host's pairing cache.

No further refresh is needed just to switch Native/Android/Web or install the
latest connection and touchpad fixes over a revision-5 build. Rolling back to a
different descriptor may require another refresh.

## Controller mappings

| Mode | Intended starting point | D-pad |
| --- | --- | --- |
| Native | Desktop/native games | Hat switch |
| Android | Android games/hosts | Hat switch, Android button/axis mapping |
| Web | Browser games/testers | Buttons 12–15 |

Tap the Gamepad toolbar's Mode control to cycle modes; hold it to open controller
settings. Mapping changes keep the same advertised descriptor and do not restart
HID. A browser tester recognizing inputs does not prove a game binds them; check
the game's controls/remapping options too.

## Touchpad dragging

Position the host cursor over the item. **Tap once, quickly touch down again
nearby, then move without lifting that second touch. Lift to drop/release.**
This restores main's tap-and-drag gesture; a single long press is not its trigger.
The first click is deferred for 180 ms, so the second touch must arrive promptly.
The second landing uses main's 100 px proximity tolerance. Two quick taps without
movement produce a double-click. Adding another finger cancels an armed drag.

Two-finger scrolling, right/middle multi-finger taps, pointer/scroll speed controls
and selectable button partitions remain available. Modifier keys occupy a side
rail rather than covering the gesture surface.

## Keyboard and sounds

- In **Settings > Keyboard**, choose typing layout, keyboard geometry, theme and
  sound pack independently. Typing choices: QWERTY, AZERTY, QWERTZ, Dvorak,
  Colemak and Russian ЙЦУКЕН.
- Hold on-screen Shift and tap Space to cycle Bluke's typing layout; release the
  keys to apply it. This does **not** switch the host's input source. The host
  interprets HID keys; enable a Russian input source there for Cyrillic output.
- Copy a built-in theme to customize it. Case/keyboard background, key colors
  and legends belong to the keyboard theme, not the app-wide color palette.
- Use checkboxes in each layout, theme or sound list to configure toolbar cycles.
  The last enabled choice cannot be removed. Tap the toolbar control to cycle;
  hold to open its configuration.
- Import supported Mechvibes ZIPs from **Key Sound Packs**, then select the pack.
  Imported packs can be deleted; built-ins cannot. Check **Look & Feel > Enable
  Sounds** if silent. Not every third-party archive is necessarily valid.

## Multimedia and limitations

Multimedia combines media/presentation buttons with a touchpad. Optional phone
volume-button forwarding is under **Controls & connection > Behavior** and applies
while Multimedia is open. **Keep audio on this phone** is a separate, best-effort
audio-routing setting; OEM/host behavior can differ.

The foreground session service supports active connections while the app
backgrounds or the screen locks. It cannot guarantee survival of every OEM
power-management action or Bluetooth failure. Android 9+ alone does not guarantee
HID Device support. Compatibility across all features on TVs, consoles, macOS
and iOS/iPadOS is not established.

## Reporting a problem

Include sender model/firmware, receiving device/OS, mode, build version, whether
pairing was refreshed and exact reproduction steps. Capture an intermittent
failure from before connecting through the failure, without filtering the export.

Follow [HID and lifecycle diagnostics](HID-DIAGNOSTICS.md). Logs and bug reports
can contain Bluetooth addresses and keyboard input: avoid passwords, redact
identifiers before sharing and do not publish full bug reports publicly.
