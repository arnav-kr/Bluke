package dev.arnv.bluke.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import dev.arnv.bluke.QuickCycleActivity
import dev.arnv.bluke.sound.KeyboardSoundSynthesizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

const val HOST_LAYOUT_PREFERENCE = "host_layout"
const val UNICODE_ENTRY_MODE_PREFERENCE = "unicode_entry_mode"
private const val PHONE_KEYBOARD_SOUND_PREFERENCE = "phone_keyboard_sound_enabled"
private const val PHONE_KEYBOARD_VIBRATION_PREFERENCE = "phone_keyboard_vibration_enabled"

private val PhoneKeyboardCanvasColor = Color(0xFF1D1D1D)

/** How long the record of sent keys stays up after the last keystroke. */
private const val ECHO_TIMEOUT_MILLIS = 4_000L

/** Queued strokes (about half a second's worth) above which progress and Stop are shown. */
private const val SHOW_PROGRESS_STROKES = 100

/**
 * Types on the host with whichever keyboard app the phone has installed.
 *
 * There is no text box: keystrokes go straight to the host as they are made (see
 * [PhoneKeyboardBridge] for why), and the host's own screen is where the text appears. Above the
 * keyboard sits a user-configurable bar of the keys a phone keyboard lacks - Ctrl, Alt, Esc, arrows,
 * F-keys - see [ImeKeyBar].
 */
@Composable
fun PhoneKeyboardView(
    isConnected: Boolean,
    sharedPrefs: SharedPreferences,
    soundSynth: KeyboardSoundSynthesizer,
    launchMode: Int,
    onModeChange: (Int) -> Unit,
    onClose: () -> Unit,
    sendKey: (keyCode: Int, isPress: Boolean) -> Unit,
    /** Lets go of every key and modifier on the host. */
    releaseAll: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current

    val hostLayout = remember(sharedPrefs) {
        HostLayouts.byId(sharedPrefs.getString(HOST_LAYOUT_PREFERENCE, null))
    }
    val unicodeMode = remember(sharedPrefs) {
        UnicodeEntry.UnicodeEntryMode.byId(sharedPrefs.getString(UNICODE_ENTRY_MODE_PREFERENCE, null))
    }
    val keyRows = remember(sharedPrefs) { ImeKeyBar.resolve(ImeKeyBar.load(sharedPrefs)) }

    // Off by default: the phone keyboard already clicks and buzzes for its own keys, and doubling
    // that up is the more common complaint. These cover the key bar too, which has no other feedback.
    var isSoundEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean(PHONE_KEYBOARD_SOUND_PREFERENCE, false))
    }
    var isVibrationEnabled by remember {
        // Until toggled here, follow the app-wide haptics choice from Look & Feel.
        mutableStateOf(
            sharedPrefs.getBoolean(
                PHONE_KEYBOARD_VIBRATION_PREFERENCE,
                sharedPrefs.getBoolean("haptics_enabled", true),
            )
        )
    }

    var droppedNotice by remember { mutableStateOf<String?>(null) }
    var statusNotice by remember { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf("") }

    // ModifierLatch is plain state mutated from the InputConnection too; bumping this after every
    // keystroke is what lets the key bar redraw when a one-shot modifier is used up.
    var latchRevision by remember { mutableIntStateOf(0) }
    val latch = remember { ModifierLatch() }
    val echo = remember { KeystrokeEcho() }
    var echoClears by remember { mutableIntStateOf(0) }
    // Declared ahead of the stroke queue so the echo can stay up while a long send drains.
    var pendingStrokes by remember { mutableIntStateOf(0) }
    // Derived, so the screen only recomposes when sending starts or stops, not on every stroke.
    val isSending by remember { derivedStateOf { pendingStrokes > 0 } }
    LaunchedEffect(latchRevision, isSending) {
        if (isSending) return@LaunchedEffect
        delay(ECHO_TIMEOUT_MILLIS)
        if (echo.entries.isNotEmpty()) {
            echo.clear()
            echoClears++
        }
    }

    // One unbounded queue drained by one long-lived consumer. A clipboard send is hundreds of
    // strokes; they must go out in order, and nothing may be dropped if more arrives mid-drain.
    val strokeQueue = remember { Channel<Pair<Int, Boolean>>(Channel.UNLIMITED) }
    // pendingStrokes counts strokes queued but not yet sent. Everything that touches it runs on
    // the main thread.
    fun enqueue(stroke: Pair<Int, Boolean>) {
        if (strokeQueue.trySend(stroke).isSuccess) pendingStrokes++
    }
    LaunchedEffect(strokeQueue) {
        for ((code, press) in strokeQueue) {
            sendKey(code, press)
            pendingStrokes = (pendingStrokes - 1).coerceAtLeast(0)
            // Paced so hosts do not coalesce fast repeats of the same key.
            delay(4)
        }
    }

    /** Drops whatever has not gone out yet, such as the rest of a long clipboard send. */
    fun stopSending() {
        if (pendingStrokes == 0) return
        while (strokeQueue.tryReceive().isSuccess) Unit
        pendingStrokes = 0
        // The last stroke out may have been a press whose release was just dropped.
        releaseAll()
        statusNotice = "Stopped - the rest was not sent"
    }

    // Leaving the app mid-send must not keep typing into the host where nobody is watching.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopSending()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bridge = remember(hostLayout, unicodeMode) {
        PhoneKeyboardBridge(
            hostLayout = { hostLayout },
            unicodeMode = { unicodeMode },
            modifiers = latch,
            emit = { strokes ->
                strokes.forEach(::enqueue)
                droppedNotice = null
                statusNotice = null
                latchRevision++
            },
            onDropped = { droppedNotice = it },
            echo = echo,
        )
    }

    val feedback = {
        if (isSoundEnabled) soundSynth.playPress()
        if (isVibrationEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun sendClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context).toString()
        } else ""
        if (text.isEmpty()) {
            statusNotice = "Clipboard is empty"
            return
        }
        // Typed as plain text: a modifier latched for the next key should not turn a pasted
        // password into a string of shortcuts.
        val translation = HidCharMap.translate(text, hostLayout, unicodeMode)
        translation.events.forEach { enqueue(it.keyCode to it.isPress) }
        echo.pasted(text)
        latchRevision++
        statusNotice = "Typing ${text.length} characters from the clipboard"
        droppedNotice = translation.droppedChars.takeIf { it.isNotEmpty() }
    }

    val inputView = remember(bridge) {
        HidInputView(
            context = context,
            bridge = bridge,
            onComposingChanged = { composing = it },
            onKeystroke = { feedback() },
            onPaste = {
                sendClipboard()
                feedback()
            },
        )
    }

    // Bring the keyboard up as soon as the mode opens - it is the whole point of the mode. The
    // short delay lets the view attach first; some OEM keyboards ignore an earlier request.
    LaunchedEffect(inputView) {
        delay(150)
        inputView.showKeyboard()
    }

    DisposableEffect(Unit) {
        onDispose {
            strokeQueue.close()
            // A latched or mid-burst modifier must not stay held on the host after we leave.
            latch.clear()
            releaseAll()
        }
    }

    fun onKeyBarPress(def: ImeKeyBar.KeyDef) {
        when (val action = def.action) {
            is ImeKeyBar.Action.Key -> bridge.tapKey(action.keyCode)
            is ImeKeyBar.Action.Combo -> bridge.tapKey(action.keyCode, action.modifiers)
            is ImeKeyBar.Action.Char -> bridge.typeText(action.char.toString())
            is ImeKeyBar.Action.Modifier -> {
                latch.tap(action.keyCode)
                latchRevision++
            }
            is ImeKeyBar.Action.Special -> when (action.builtin) {
                ImeKeyBar.Action.Builtin.SEND_CLIPBOARD -> sendClipboard()
            }
        }
        feedback()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PhoneKeyboardCanvasColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            // Keeps the key bar directly above the soft keyboard.
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("phone_keyboard_view_root"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolbarPill(onClick = {
                inputView.hideKeyboard()
                onClose()
            }) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("Close", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            ToolbarPill(
                onClick = {
                    val enabled = sharedPrefs.enabledInputModes()
                    val index = enabled.indexOfFirst { it.id == launchMode }.coerceAtLeast(0)
                    inputView.hideKeyboard()
                    onModeChange(enabled[(index + 1) % enabled.size].id)
                },
                onLongClick = {
                    context.startActivity(Intent(context, QuickCycleActivity::class.java))
                },
            ) {
                Icon(Icons.Default.PhoneAndroid, "Switch mode", tint = Color.White, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    InputMode.PHONE_KEYBOARD.displayName,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            ToolbarPill(onClick = {
                sendClipboard()
                feedback()
            }) {
                Icon(
                    Icons.Default.ContentPaste,
                    "Type the clipboard on the host",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp).testTag("phone_keyboard_paste"),
                )
                Spacer(Modifier.width(4.dp))
                Text("Paste", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            ToolbarPill(onClick = {
                isSoundEnabled = !isSoundEnabled
                sharedPrefs.edit { putBoolean(PHONE_KEYBOARD_SOUND_PREFERENCE, isSoundEnabled) }
                if (isSoundEnabled) soundSynth.playPress()
            }) {
                Icon(
                    if (isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    if (isSoundEnabled) "Key sounds on" else "Key sounds off",
                    tint = Color.White.copy(alpha = if (isSoundEnabled) 1f else 0.5f),
                    modifier = Modifier.size(14.dp).testTag("phone_keyboard_sound_toggle"),
                )
            }
            ToolbarPill(onClick = {
                isVibrationEnabled = !isVibrationEnabled
                sharedPrefs.edit { putBoolean(PHONE_KEYBOARD_VIBRATION_PREFERENCE, isVibrationEnabled) }
                if (isVibrationEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }) {
                Icon(
                    Icons.Default.Vibration,
                    if (isVibrationEnabled) "Vibration on" else "Vibration off",
                    tint = Color.White.copy(alpha = if (isVibrationEnabled) 1f else 0.5f),
                    modifier = Modifier.size(14.dp).testTag("phone_keyboard_vibration_toggle"),
                )
            }
            ToolbarPill(onClick = { inputView.showKeyboard() }) {
                Icon(Icons.Default.Keyboard, "Show keyboard", tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }

        // The rest of the screen is a target for bringing the keyboard back after it is dismissed.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { inputView.showKeyboard() }
                .testTag("phone_keyboard_surface"),
            contentAlignment = Alignment.Center,
        ) {
            // Zero-size host for the view that actually owns the IME connection.
            AndroidView(factory = { inputView }, modifier = Modifier.size(1.dp).alpha(0f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                val echoEntries = remember(latchRevision, echoClears) { echo.entries }
                KeystrokeEchoLine(echoEntries)
                Text(
                    text = if (isConnected) {
                        "Typing straight into the host as ${hostLayout.displayName}" +
                            if (unicodeMode != UnicodeEntry.UnicodeEntryMode.OFF) " + ${unicodeMode.displayName}" else ""
                    } else {
                        "Not connected - nothing is being sent"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Watch the host's screen. Tap here if the keyboard goes away.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
                if (composing.isNotEmpty()) {
                    // Some keyboards still compose a word before committing it; show it so the
                    // wait before it reaches the host does not look like a dropped keystroke.
                    Text(
                        text = "Composing: $composing",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("phone_keyboard_composing"),
                    )
                }
                droppedNotice?.let { dropped ->
                    Text(
                        text = if (unicodeMode == UnicodeEntry.UnicodeEntryMode.OFF) {
                            "Skipped \"$dropped\" - not on a ${hostLayout.displayName} keyboard. " +
                                "Turn on Unicode entry in Phone Keyboard settings to send these."
                        } else {
                            "Skipped \"$dropped\" - ${unicodeMode.displayName} cannot express these"
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("phone_keyboard_dropped_notice"),
                    )
                }
                statusNotice?.let { status ->
                    Text(
                        text = status,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("phone_keyboard_status_notice"),
                    )
                }
                SendProgress(pending = { pendingStrokes }, onStop = ::stopSending)
            }
        }

        // Everything here goes to the *host*: "Back" is the host's back navigation (Alt+Left),
        // not this phone's back gesture.
        val latchStates = remember(latchRevision) { latch.snapshot() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                keyRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        row.forEach { def ->
                            val latchState = (def.action as? ImeKeyBar.Action.Modifier)
                                ?.let { latchStates[it.keyCode] }
                                ?: ModifierLatch.State.OFF
                            KeyBarKey(
                                label = def.label,
                                latchState = latchState,
                                modifier = Modifier.weight(1f),
                                testTagSuffix = def.id,
                                onClick = { onKeyBarPress(def) },
                            )
                        }
                    }
                }
            }
            ArrowCluster(onPress = ::onKeyBarPress)
        }
    }
}

/**
 * How much of a long send (a clipboard, usually) is still queued, with a way to stop it. Takes the
 * count as a lambda so only this line redraws as it ticks down, not the whole screen.
 */
@Composable
private fun SendProgress(pending: () -> Int, onStop: () -> Unit) {
    val left = pending()
    if (left <= SHOW_PROGRESS_STROKES) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Sending - $left keystrokes to go",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp,
            modifier = Modifier.testTag("phone_keyboard_send_progress"),
        )
        ToolbarPill(onClick = onStop) {
            Text(
                "Stop",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("phone_keyboard_stop_sending"),
            )
        }
    }
}

/** The arrows as an inverted T on their own tinted panel, so they read as one control. */
@Composable
private fun ArrowCluster(onPress: (ImeKeyBar.KeyDef) -> Unit) {
    val (up, left, down, right) = ImeKeyBar.ARROW_CLUSTER
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(4.dp)
            .testTag("phone_keyboard_arrows"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        @Composable
        fun Arrow(def: ImeKeyBar.KeyDef) = KeyBarKey(
            label = def.label,
            latchState = ModifierLatch.State.OFF,
            modifier = Modifier.width(40.dp),
            testTagSuffix = def.id,
            fontSize = 18.sp,
        ) { onPress(def) }
        Arrow(up)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Arrow(left)
            Arrow(down)
            Arrow(right)
        }
    }
}

/**
 * What was just sent, newest on the right: typed text as it would appear, other keys as labelled
 * caps. Scrolled to the end so the latest keystroke is always in view.
 */
@Composable
private fun KeystrokeEchoLine(entries: List<KeystrokeEcho.Entry>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .horizontalScroll(rememberScrollState(), reverseScrolling = true)
            .testTag("phone_keyboard_echo"),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            when (entry) {
                is KeystrokeEcho.Entry.Text -> Text(
                    text = entry.text,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                is KeystrokeEcho.Entry.Pasted -> PastedEcho(entry)
                is KeystrokeEcho.Entry.Key -> Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (entry.count > 1) "${entry.label} ×${entry.count}" else entry.label,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** A clipboard send in the echo: what was pasted, marked as a paste rather than typing. */
@Composable
private fun PastedEcho(entry: KeystrokeEcho.Entry.Pasted) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("phone_keyboard_echo_paste"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.ContentPaste, "Pasted", tint = accent, modifier = Modifier.size(16.dp))
        Text(
            text = entry.preview,
            color = Color.White,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        if (entry.truncated) {
            Text(
                text = "${entry.length} chars",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun KeyBarKey(
    label: String,
    latchState: ModifierLatch.State,
    modifier: Modifier = Modifier,
    /** Distinguishes keys that share a label. */
    testTagSuffix: String,
    fontSize: TextUnit = 11.sp,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    // ONCE is outlined, LOCKED is filled, so the two are told apart at a glance.
    val background = when (latchState) {
        ModifierLatch.State.LOCKED -> accent
        else -> Color.White.copy(alpha = 0.12f)
    }
    val border = when (latchState) {
        ModifierLatch.State.OFF -> Color.White.copy(alpha = 0.08f)
        else -> accent
    }
    val textColor = if (latchState == ModifierLatch.State.LOCKED) MaterialTheme.colorScheme.onPrimary else Color.White
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(if (latchState == ModifierLatch.State.OFF) 1.dp else 2.dp, border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .testTag("phone_keyboard_key_$testTagSuffix"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
