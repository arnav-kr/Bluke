# Bluke User Guide & Troubleshooting

Bluke transforms your Android phone into a driverless, low-latency Bluetooth mechanical keyboard, touchpad, gamepad, and multimedia remote.

---

## 1. Connecting to a Host

Bluke communicates directly with your host device (PC, laptop, tablet, or phone) like any physical Bluetooth accessory—no companion apps, background computer software, or Wi-Fi setup needed.

1. **Turn on Bluetooth** on both your phone and the computer/host device.
2. **Make the host discoverable** in its operating system Bluetooth settings.
3. **Open Bluke** and grant Bluetooth permissions when prompted.
4. **Tap Scan** on Bluke's home screen, select your computer from the list, and confirm pairing on both screens.
   - *Note:* If prompted for a 6-digit PIN on either screen, enter the matching PIN on both devices.
5. Once the indicator dot turns green and displays your host's name, choose any input mode to start.

> [!IMPORTANT]
> **Pairing vs. Connecting:**
> In Android, being listed as "Paired" in system Bluetooth settings only means the devices remember each other—it does **not** mean the input session is active. Always initiate the connection through Bluke's in-app **Scan** or **Connect** button, rather than tapping your phone in your computer's Bluetooth menu.

> [!TIP]
> **Connecting to a Linux Computer (Ubuntu, Fedora, Arch, etc.)?**
> If your Linux desktop doesn't show the pairing or PIN confirmation popup when connecting from Bluke, use the [Host-Initiated Workaround](#linux-pairing-prompt-does-not-appear-on-the-computer): tap Scan in Bluke once (which makes your phone visible to Linux), then initiate pairing **from your Linux Bluetooth settings**.

---

## 2. Core Interface: The Unified App Bar

Every control surface in Bluke shares a consistent, unified top toolbar built around two simple actions:

* **Tap to Cycle:** Tapping any toolbar button quickly cycles through your active favorites (such as typing layouts, color themes, switch sound profiles, gamepad modes, or pointer speeds).
* **Hold to Configure:** Long-pressing any toolbar button opens its full settings screen, allowing you to choose from the complete library or customize which options appear in your quick-cycle loop.

The toolbar also displays your connection status (green for active, red/gray for offline), the connected device name, and buttons to return Home or disconnect.

---

## 3. Input Modes & Controls

### Mechanical Keyboard
An authentic mechanical typing surface featuring tactile haptics, realistic acoustics, and customizable layouts.

- **Typing Layouts:** Choose between **QWERTY**, **AZERTY**, **QWERTZ**, **Dvorak**, **Colemak**, and **Russian (ЙЦУКЕН)**.
- **On-Screen Shortcut:** Hold on-screen **Shift** and tap **Space** to switch typing layouts on the fly without leaving the keyboard.
  > [!TIP]
  > Keyboards send physical key positions rather than typed letters. To type in Russian (ЙЦУКЕН) or French (AZERTY), make sure your computer is also set to that language. See [Troubleshooting: Host Keyboard Languages](#russian-or-accented-characters-do-not-type-correctly).
- **Physical Layouts (Geometries):** Choose your preferred keyboard form factor: **Classic 75%**, **Compact 75%**, **Inline 75%**, **Standard 65%**, **Balanced 65%**, **Extended 65%**, or **HHKB 60%**.
- **In-Place Fn Shortcuts:** Holding the **Fn** key transforms F1–F12 keys into media and presentation controls right on the keycaps.
- **Switch Acoustics:** Turn typing sounds on/off or cycle between 12 mechanical switch profiles directly from the toolbar.

---

### Precision Touchpad
A smooth pointer surface with multi-touch gestures and optional modifier keys.

- **Tap-and-Drag:** Position your cursor over an item, **tap once, immediately touch down again nearby (within 180 ms), and move without lifting**. Lift your finger to drop.
  - *Tip:* Touching the screen with a 3rd finger immediately cancels an active drag.
- **Multi-Touch Gestures:**
  - Two-finger drag: scroll vertically or horizontally.
  - Two-finger tap: right-click.
  - Three-finger tap: middle-click.
- **Modifier Key Strips:** Add a strip of modifier keys (Ctrl, Alt, Shift, Meta) to the **Left**, **Right**, or turn it **Off** from the toolbar. This gives you easy access to shortcuts without covering the touchpad surface.
- **Pointer & Scroll Speeds:** Adjust pointer and scrolling sensitivity from fine control (0.25x, 0.5x) to high speed (1.5x, 2.0x) from the toolbar.

---

### Gamepad Controller
A dual-stick controller with D-pad, face buttons (A/B/X/Y), shoulder bumpers, triggers, and Start/Select buttons.

Bluke includes **three controller profiles** that switch instantly without disconnecting:

| Profile | Best For | What it Does |
| :--- | :--- | :--- |
| **Native** | Windows & Linux PC games | Standard PC gamepad controls |
| **Android** | Android games & Android TV | Calibrated for Android mobile games (fixes the right stick) |
| **Web** | Browser games & online testers | Sends D-pad directions as buttons so browser games recognize them |

> [!TIP]
> **Playing PC Games on Steam?**
> Bluke is recognized by Windows as a standard generic controller. Many modern Windows games require Xbox controller format. Enable **Steam Input** to play any PC game with Bluke. See [Troubleshooting: PC Games & Steam](#pc-games-ignore-the-controller).

---

### Multimedia Remote
A dedicated remote for couch media browsing and presentations.

- **Presentation Controls:** Start a slideshow (F5), navigate slides (Next/Previous), or exit (Escape), with an integrated mini-touchpad for pointing.
- **Media Controls:** Play/Pause, Next/Previous track, and Volume Up/Down/Mute.
- **Adaptive Layout:** Automatically rearranges buttons for easy one-handed thumb use whether holding your phone upright (portrait) or sideways (landscape).
- **Phone Volume Buttons:** Optionally use your phone's physical volume rocker to adjust your computer's volume while the Multimedia screen is open.

---

## 4. Settings & Customization

### Personalization
* **Keyboard Theme Studio:**
  * Choose from built-in theme presets: *Olivia, Dracula, Model M, Mizu, Oblivion, Cafe, Laser, HHKB, 9009, 8008*.
  * Create custom themes using the visual color picker.
  * Customize case color, matte or metallic finish, plate accents, keycap groups, and individual keys.
* **Key Sound Packs & Mechvibes Import:**
  * Choose from 12 built-in switch sounds: *Cherry MX Brown, Holy Panda, Alpaca, Turquoise Tealios, Gateron Black Ink, Cherry MX Black, Cherry MX Blue, Kailh Box Navy, Buckling Spring, SKCM Blue Alps, Topre 45g, NovelKeys Cream*.
  * **Import Mechvibes Packs:** Tap Import to load any standard Mechvibes ZIP sound pack (including audio sprite packs) from your phone's files.
  * Delete imported packs anytime (built-in switch sounds are permanently available).
* **Look & Feel:** Toggle pure OLED black mode, keypress vibration (haptics), and Material You dynamic system colors.

### Controls & Connection
* **Quick-Cycle Choices:** Pick which layouts, themes, sounds, and controller modes appear when tapping toolbar buttons.
* **Behavior:** Turn on phone volume button forwarding, or enable the experimental *Keep audio on this phone* setting.
* **Controller Settings:** Set your default gamepad mode.

### Support & Developer Options
* Access in-app help, view open-source licenses, and test input response.
* **Developer Mode:** Tapping the Bluke logo 5 times in *Settings > About* reveals developer tools and connection logs (`BlukeHID` and `BlukeLifecycle`).

---

## 5. Troubleshooting & FAQ

### Connection & Pairing

#### Phone says "Paired" in Bluetooth settings, but Bluke will not connect
* **Cause:** Your phone and computer paired, but the computer did not open the input connection.
* **Fix:** Unpair/forget Bluke on **both** devices. Turn Bluetooth off and on, open Bluke, and tap **Scan** to pair from inside the app.

#### Controls or joysticks behave erratically after an app update
* **Cause:** Computers remember old controller profiles for previously paired devices. When Bluke updates its controller layout, your computer may still expect the old layout.
* **Fix:** Unpair/forget Bluke on **both** devices, then pair fresh using **Scan** in Bluke. (Clearing Bluke's app data is not required).

#### Pairing keeps failing or disconnecting immediately
* **Cause:** One device forgot the pairing while the other still has it saved, causing a connection conflict.
* **Fix:** Delete the pairing on both your phone and computer, restart Bluetooth, and pair again.

#### Linux pairing prompt does not appear on the computer
* **Cause:** Many Linux desktop systems (GNOME, KDE, Blueman) fail to show the pairing popup when a mobile keyboard initiates the connection.
* **Fix:**
  1. In Bluke, tap **Scan**, select your Linux computer, and attempt to pair once. (Even if no popup appears, your phone is now discovered by Linux).
  2. On your Linux computer, open **Bluetooth Settings** (or Blueman). Your phone will now appear in the list of discovered devices.
  3. Click your phone **from the computer** to start pairing from the Linux side.
  4. The pairing prompt with matching PIN will now appear on both screens. Accept on both devices.
  5. Once paired, return to Bluke and tap **Connect**.

#### Bluke connects to the wrong computer automatically
* **Cause:** If multiple paired computers are nearby, Bluke may reconnect to the first one it finds.
* **Fix:** Turn off Bluetooth on nearby computers you aren't using, or turn off *Reconnect on Launch* in Bluke's connection settings.

---

### Gaming & Controllers

#### PC games ignore the controller
* **Cause:** Bluke is recognized by Windows as a standard generic controller. Many modern PC games only accept Xbox controllers.
* **Fix:**
  1. Open **Steam > Settings > Controller**.
  2. Turn on **Enable Steam Input for Generic Controllers**.
  3. Steam will automatically translate Bluke's controls so any game works. For non-Steam games, add the game to your Steam library (*Games > Add a Non-Steam Game*) or use a tool like `x360ce`.

#### Browser games or web testers ignore the D-pad
* **Cause:** Many web browsers ignore directional pads configured as hat switches.
* **Fix:** Switch Bluke's controller profile to **Web** mode, which sends D-pad directions as standard buttons.

#### Android games do not recognize the right analog stick
* **Cause:** Android expects a different stick layout than Windows or Linux.
* **Fix:** Switch Bluke's controller profile to **Android** mode.

---

### Keyboard & Typing

#### Russian or accented characters do not type correctly
* **Cause:** Keyboards send physical key positions rather than typed letters. Your computer turns those keypresses into characters using whatever keyboard language is currently active on the computer.
* **Fix:** When switching to the Russian layout in Bluke, switch your computer's input language to Russian as well.

---

### Touchpad & Gestures

#### Tap-and-drag drops items or does not engage
* **Cause:** Tap-and-drag requires two quick taps: **tap once, immediately land a second finger nearby (within 180 ms), and drag without lifting**. It is not a single long-press.
* **Fix:** Practice the two-touch motion. Make sure a second or third finger is not resting on the screen, as extra touch points cancel the drag.

---

### Background Sessions & Audio

#### Bluke disconnects when the phone screen turns off or locks
* **Cause:** Bluke runs in the background to keep the connection alive, but aggressive phone battery savers can kill background Bluetooth processes.
* **Fix:** Go to your phone's **Settings > Apps > Bluke > Battery** and select **Unrestricted**.

#### Computer plays its audio through the phone
* **Cause:** Some computers mistake the phone for a Bluetooth speaker when connecting.
* **Fix:** Turn on *Keep audio on this phone* in Bluke's settings, or select your computer's built-in speakers in your computer's sound settings.

---

### Device Compatibility

#### "Unsupported Device" error
* **Cause:** Bluke requires Android 9 or higher and phone firmware that supports Bluetooth HID Device mode. Some budget phones or modified manufacturer ROMs disable this feature.
* **Fix:** Tap **Retry** once. If it continues to fail, your phone's software does not support this feature.

#### Apple / macOS / iOS Support
* Apple devices have strict restrictions on third-party Bluetooth input peripherals, leading to inconsistent behavior. macOS and iOS devices are considered **unsupported / future scope**.

---

## 6. Capturing Diagnostics for Bug Reports

If you experience connection drops, unresponsive buttons, or incorrect joystick axes, capturing an in-app diagnostic log helps diagnose the problem quickly.

### In-App Log Collection (No PC Required)

1. **Enable Developer Mode:** Go to **Settings > About** and tap the Bluke logo 5 times.
2. **Open the Log Viewer:** Go to **Developer Options > Developer Logs** and tap **Clear** to remove old entries.
3. **Reproduce the Issue:**
   - **For Controller / Joystick Axis Issues:** Connect to your host and move the analog sticks in each direction (up, down, left, right), holding each direction for ~2 seconds so samples are recorded.
   - **For Connection Drops:** Attempt to connect, lock/unlock the phone, or tap Disconnect and reconnect to capture the failure sequence.
4. **Export the Log:** Return to **Developer Logs** and tap **Export** (or copy the text).

> [!WARNING]
> **Privacy Notice:** Bluetooth diagnostic logs include your device's Bluetooth MAC address, and active keystrokes record raw keycodes. Avoid typing passwords while logs are recording, and review the exported text before sharing publicly.

### Advanced PC Capture (ADB)
If you have your phone connected to a computer with USB Debugging enabled, you can stream both input and Bluetooth lifecycle logs live from terminal:
```bash
adb logcat -s BlukeHID BlukeLifecycle
```
