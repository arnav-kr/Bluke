package dev.arnv.bluke.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay

/**
 * Result of reconciling the IME buffer against what the host has already received.
 *
 * Held separately from the composable so the diffing logic stays testable and so the
 * "what could not be typed" reporting has somewhere to live.
 */
data class ImeDiff(
    val backspaces: Int,
    val events: List<UnicodeEntry.KeyEvent>,
    /** Characters neither the host layout nor Unicode entry could produce, for user feedback. */
    val droppedChars: String,
    /** The portion of the target text that actually reached the host. */
    val sentText: String
)

/**
 * Works out the keystrokes that turn [sent] (what the host currently believes was typed) into
 * [target] (what the IME buffer now holds).
 *
 * The IME is free to rewrite text it already gave us - that is exactly what autocorrect and word
 * suggestions do - so a plain append is not enough. We keep the common prefix, backspace away the
 * rest, and retype the tail against the user's declared host layout.
 */
fun computeImeDiff(
    sent: String,
    target: String,
    layout: HostLayouts.HostLayout = HostLayouts.DEFAULT,
    unicodeMode: UnicodeEntry.UnicodeEntryMode = UnicodeEntry.UnicodeEntryMode.OFF
): ImeDiff {
    var prefix = 0
    while (prefix < sent.length && prefix < target.length && sent[prefix] == target[prefix]) {
        prefix++
    }
    val backspaces = sent.length - prefix
    val tail = target.substring(prefix)
    val translation = HidCharMap.translate(tail, layout, unicodeMode)

    // Characters that never reached the host must not be recorded as sent, or every later edit
    // would mis-count its backspaces. Rebuild the mirror from what actually went out.
    val droppedSet = translation.droppedChars.toSet()
    val landed = tail.filter { it !in droppedSet }
    return ImeDiff(
        backspaces = backspaces,
        events = translation.events,
        droppedChars = translation.droppedChars,
        sentText = target.substring(0, prefix) + landed
    )
}

/**
 * Full-screen surface that hands typing over to whichever IME the user has installed.
 *
 * Bluke's own keycaps send scancodes directly, which means no autocorrect, no word suggestions and
 * no access to the IME's clipboard or translate panels. Here we instead host a text field, let the
 * system keyboard do its work in it, and forward the committed text out over HID. Composing text
 * (the underlined in-progress word) is deliberately not forwarded until the IME commits it,
 * so suggestions and autocorrect settle locally instead of spraying corrections at the host.
 */
@Composable
fun ImeInputView(
    isConnected: Boolean,
    hostLayout: HostLayouts.HostLayout,
    unicodeMode: UnicodeEntry.UnicodeEntryMode,
    onStroke: (keyCode: Int, isPress: Boolean) -> Unit,
    onClose: () -> Unit,
    onCycleMode: () -> Unit,
    /** User-arranged extra key rows, resolved from [ImeKeyBar]. */
    keyRows: List<List<ImeKeyBar.KeyDef>>
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    var fieldValue by remember { mutableStateOf(TextFieldValue("")) }
    // What we have already transmitted to the host, mirrored locally so edits can be reconciled.
    var sentText by remember { mutableStateOf("") }
    var droppedNotice by remember { mutableStateOf<String?>(null) }
    // Feedback that is not about untypable characters, e.g. an empty clipboard.
    var statusNotice by remember { mutableStateOf<String?>(null) }

    // A single unbounded queue drained by one long-lived consumer. Autocorrect can rewrite a whole
    // word at once, so bursts of dozens of strokes are normal; they must go out in order, and
    // nothing may be dropped if another burst arrives mid-drain.
    val strokeQueue = remember { Channel<Pair<Int, Boolean>>(Channel.UNLIMITED) }

    // Bring the system keyboard up as soon as this view appears - it is the whole point of the mode.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        // A short delay lets the field finish attaching before we ask for the IME, otherwise
        // some OEM keyboards ignore the request.
        delay(150)
        keyboardController?.show()
    }

    // Pace the stream so hosts do not coalesce fast repeats of the same key.
    LaunchedEffect(strokeQueue) {
        for ((code, press) in strokeQueue) {
            onStroke(code, press)
            delay(4)
        }
    }

    fun queue(strokes: List<Pair<Int, Boolean>>) {
        strokes.forEach { strokeQueue.trySend(it) }
    }

    // Leaving the mode mid-drain could abandon a held Shift, which would leave the host stuck in
    // uppercase. Releasing it unconditionally on teardown is harmless if it was already up.
    DisposableEffect(Unit) {
        onDispose {
            strokeQueue.close()
            onStroke(KeyboardLayouts.MOD_LSHIFT, false)
        }
    }

    fun transmit(diff: ImeDiff) {
        val out = mutableListOf<Pair<Int, Boolean>>()
        repeat(diff.backspaces) {
            out.add(KeyboardLayouts.KEY_BACKSPACE to true)
            out.add(KeyboardLayouts.KEY_BACKSPACE to false)
        }
        diff.events.forEach { out.add(it.keyCode to it.isPress) }
        queue(out)
        droppedNotice = diff.droppedChars.takeIf { it.isNotEmpty() }
    }

    /**
     * Types the phone's clipboard out over HID.
     *
     * This is the reliable route for text the host layout cannot express - a password with unusual
     * symbols, a URL, a block of accented prose - because it goes through the same layout mapping
     * but does not depend on the user's IME being able to compose it first.
     */
    fun sendClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(context).toString()
        } else ""
        if (text.isEmpty()) {
            statusNotice = "Clipboard is empty"
            droppedNotice = null
            return
        }
        val translation = HidCharMap.translate(text, hostLayout, unicodeMode)
        queue(translation.events.map { it.keyCode to it.isPress })
        statusNotice = "Sent ${text.length} characters from the clipboard"
        droppedNotice = translation.droppedChars.takeIf { it.isNotEmpty() }
    }

    // Sends a bare key (Enter, arrows, ...) that has no place in the text buffer.
    fun tapKey(keyCode: Int) {
        queue(listOf(keyCode to true, keyCode to false))
    }

    /**
     * Types a single character through the host layout mapping.
     *
     * Symbols like `|` sit behind different shift/AltGr combinations on each layout, so they must go
     * through the same translation as typed text instead of a fixed scancode. Bypasses the staging
     * buffer: these are one-shot punches, not text the IME should try to autocorrect.
     */
    fun sendChar(char: Char) {
        val translation = HidCharMap.translate(char.toString(), hostLayout, unicodeMode)
        if (translation.events.isEmpty()) {
            statusNotice = "$char is not available on a ${hostLayout.displayName} keyboard"
            return
        }
        queue(translation.events.map { it.keyCode to it.isPress })
    }

    /**
     * Sends [keyCode] while [modifiers] are held, then releases everything in reverse order.
     *
     * Used for host navigation shortcuts (Alt+Left for Back, and so on) where the interesting part
     * is the combination rather than the key itself.
     */
    fun tapCombo(modifiers: List<Int>, keyCode: Int) {
        val out = mutableListOf<Pair<Int, Boolean>>()
        modifiers.forEach { out.add(it to true) }
        out.add(keyCode to true)
        out.add(keyCode to false)
        modifiers.reversed().forEach { out.add(it to false) }
        queue(out)
    }

    // Follows the app's light/dark theme rather than the keycap colorway: this screen is ordinary
    // Android UI wrapping the user's own IME, not a picture of a mechanical keyboard.
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // The activity is edge-to-edge, so without this the header would sit under the status
            // bar clock. imePadding keeps the key rows directly above the soft keyboard.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "System Keyboard",
                    color = onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isConnected) {
                        "Sending as ${hostLayout.displayName}" +
                            if (unicodeMode != UnicodeEntry.UnicodeEntryMode.OFF) {
                                " + ${unicodeMode.displayName}"
                            } else ""
                    } else {
                        "Not connected - nothing is being sent"
                    },
                    color = onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cycle on to the next input mode, matching the pill the other modes carry.
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            keyboardController?.hide()
                            onCycleMode()
                        }
                        .padding(horizontal = 10.dp)
                        .testTag("ime_mode_cycle_btn"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Mode",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            keyboardController?.hide()
                            onClose()
                        }
                        .testTag("ime_close_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // The staging buffer. Text lands here first so the IME can correct it in place; every
        // change is diffed against what the host already has.
        // Deliberately short: this is a staging line, not a document. The space it gives up goes to
        // the key rows below, which are what a physical-keyboard user actually reaches for.
        // Uses the theme's surface/onSurface pair rather than a translucent black over a keycap
        // legend colour, which left dark text on a dark plate in some colourways.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 96.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(10.dp)
                )
                .padding(12.dp)
        ) {
            if (fieldValue.text.isEmpty()) {
                Text(
                    text = "Start typing with your own keyboard...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 15.sp
                )
            }
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val diff = computeImeDiff(sentText, newValue.text, hostLayout, unicodeMode)
                    if (diff.backspaces > 0 || diff.events.isNotEmpty() || diff.droppedChars.isNotEmpty()) {
                        transmit(diff)
                        sentText = diff.sentText
                        statusNotice = null
                    }
                    fieldValue = newValue
                },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.SansSerif
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        // Enter is a key, not text: send it through and clear the staging buffer
                        // so the next line starts from a known-empty state on both ends.
                        tapKey(KeyboardLayouts.KEY_ENTER)
                        fieldValue = TextFieldValue("")
                        sentText = ""
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focusRequester)
                    .testTag("ime_input_field")
            )
        }

        droppedNotice?.let { dropped ->
            Text(
                text = if (unicodeMode == UnicodeEntry.UnicodeEntryMode.OFF) {
                    "Skipped \"$dropped\" - not on a ${hostLayout.displayName} keyboard. " +
                        "Turn on Unicode entry in Behavior settings to send these."
                } else {
                    "Skipped \"$dropped\" - ${unicodeMode.displayName} cannot express these"
                },
                color = MaterialTheme.colorScheme.error,
                fontSize = 10.sp,
                modifier = Modifier.testTag("ime_dropped_notice")
            )
        }

        statusNotice?.let { status ->
            Text(
                text = status,
                color = onSurfaceVariant,
                fontSize = 10.sp,
                modifier = Modifier.testTag("ime_status_notice")
            )
        }

        // Pushes the key rows down against the soft keyboard. They belong to the keyboard, not to
        // the text field, so they sit where a physical extension row would - under the thumbs,
        // directly above the IME - rather than floating under the input box.
        Spacer(modifier = Modifier.weight(1f))

        // The user's own key rows, configured in Behavior settings. Most of what is here a phone IME
        // either buries behind a symbol page or cannot produce at all.
        //
        // Everything is sent to the *host*: "Back" is the host's back navigation (Alt+Left in
        // browsers and file managers), not this phone's back gesture.
        keyRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { def ->
                    ImeAuxKey(def.label, Modifier.weight(1f), testTagSuffix = def.id) {
                        when (val action = def.action) {
                            is ImeKeyBar.Action.Key -> tapKey(action.keyCode)
                            is ImeKeyBar.Action.Combo -> tapCombo(action.modifiers, action.keyCode)
                            is ImeKeyBar.Action.Char -> sendChar(action.char)
                            is ImeKeyBar.Action.Special -> when (action.builtin) {
                                ImeKeyBar.Action.Builtin.SEND_CLIPBOARD -> sendClipboard()
                                // Cursor and Enter keys move the host's caret away from our staging
                                // buffer's tail, so the mirrored "already sent" text is no longer a
                                // safe basis for diffing. Clearing keeps the two in step.
                                ImeKeyBar.Action.Builtin.ENTER_AND_CLEAR -> {
                                    tapKey(KeyboardLayouts.KEY_ENTER)
                                    fieldValue = TextFieldValue("")
                                    sentText = ""
                                }
                                ImeKeyBar.Action.Builtin.CLEAR_STAGED -> {
                                    fieldValue = TextFieldValue("")
                                    sentText = ""
                                    droppedNotice = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImeAuxKey(
    label: String,
    modifier: Modifier = Modifier,
    /** Distinguishes keys that share a label, e.g. the host's Home key and the Super/launcher key. */
    testTagSuffix: String? = null,
    onClick: () -> Unit
) {
    // Styled like a key on the soft keyboard it sits above, in the app's own light/dark palette.
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .testTag("ime_aux_${testTagSuffix ?: label}"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
