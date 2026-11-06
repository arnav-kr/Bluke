package dev.arnv.bluke.ui

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * An invisible view that owns the soft keyboard and forwards everything it types to [bridge].
 *
 * There is deliberately no text field. It reports itself to the IME as raw input
 * ([InputType.TYPE_NULL]), the same way terminal apps do, so the IME switches off autocorrect and
 * prediction and sends each key as it is pressed. The text lives on the host only.
 */
@SuppressLint("ViewConstructor")
class HidInputView(
    context: Context,
    private val bridge: PhoneKeyboardBridge,
    /** Text the IME is still composing (some IMEs compose even for raw input), for display only. */
    private val onComposingChanged: (String) -> Unit,
    /** Called once per user-visible keystroke, for sound and vibration feedback. */
    private val onKeystroke: () -> Unit,
    /** The keyboard app asked to paste; there is no local text to paste into, so the host gets it. */
    private val onPaste: () -> Unit,
) : View(context) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_NONE
        return RawConnection()
    }

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    // Hardware keyboards attached to the phone land here rather than in the InputConnection.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(event) || super.onKeyDown(keyCode, event)

    private fun handleKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            // Release events carry no new information: every press is sent as a full tap.
            return PhoneKeyboardBridge.androidKeyToHid(event.keyCode) != null || event.unicodeChar != 0
        }
        val stripped = event.metaState and
            (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK).inv()
        val code = event.getUnicodeChar(stripped)
        val char = if (code != 0 && (code and KeyCharacterMap.COMBINING_ACCENT) == 0) code.toChar() else null
        val handled = bridge.onAndroidKey(
            androidKeyCode = event.keyCode,
            char = char,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            meta = event.isMetaPressed,
        )
        if (handled) onKeystroke()
        return handled
    }

    /**
     * The editable here only ever holds text the IME is composing and has not committed. Committed
     * text is sent and cleared immediately, so deletes against an empty editable belong to the host.
     */
    private inner class RawConnection : BaseInputConnection(this@HidInputView, true) {

        private fun flush() {
            val editable = editable ?: return
            val text = editable.toString()
            editable.clear()
            onComposingChanged("")
            if (text.isNotEmpty()) {
                bridge.typeText(text)
                onKeystroke()
            }
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            // Replaces any composing region, which was never sent, so the editable now holds
            // exactly the committed text.
            super.commitText(text, newCursorPosition)
            flush()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            super.setComposingText(text, newCursorPosition)
            onComposingChanged(editable?.toString().orEmpty())
            return true
        }

        override fun finishComposingText(): Boolean {
            super.finishComposingText()
            flush()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            val editable = editable
            if (editable != null && editable.isNotEmpty()) {
                super.deleteSurroundingText(beforeLength, afterLength)
                onComposingChanged(editable.toString())
                return true
            }
            bridge.deleteBefore(beforeLength)
            bridge.deleteAfter(afterLength)
            if (beforeLength > 0 || afterLength > 0) onKeystroke()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean =
            deleteSurroundingText(beforeLength, afterLength)

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            // Anything still composing goes out first, so it lands before this key on the host.
            if (event.action == KeyEvent.ACTION_DOWN) flush()
            handleKey(event)
            return true
        }

        override fun performEditorAction(actionCode: Int): Boolean {
            flush()
            bridge.tapKey(KeyboardLayouts.KEY_ENTER)
            onKeystroke()
            return true
        }

        // Selection and cursor moves are the host's business; nothing local to move.
        override fun setSelection(start: Int, end: Int): Boolean = true

        // Keyboards with a clipboard panel either commit the clip as text, which goes out as typing
        // anyway, or ask the editor to paste, which lands here. Cut, copy and select-all have nothing
        // local to act on.
        override fun performContextMenuAction(id: Int): Boolean {
            if (id != android.R.id.paste && id != android.R.id.pasteAsPlainText) return false
            flush()
            onPaste()
            return true
        }
    }
}
