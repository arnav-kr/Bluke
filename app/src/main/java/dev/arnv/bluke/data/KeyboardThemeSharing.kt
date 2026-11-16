package dev.arnv.bluke.data

import dev.arnv.bluke.ui.CUSTOM_KEYBOARD_THEME_PREFIX
import dev.arnv.bluke.ui.KeyboardKeyStyle
import dev.arnv.bluke.ui.KeyboardThemeDefinition
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor
import dev.arnv.bluke.ui.theme.parseOpaqueHexColor
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Turns keyboard themes into text people can pass around (a JSON file, a Markdown
 * file, or a simplybluke:// link that also fits in a QR code) and back.
 *
 * Everything read here comes from outside the app, so parsing is strict: sizes are
 * capped, colors are forced opaque, and the result always gets a fresh id.
 */
object KeyboardThemeSharing {
    const val FORMAT = "simplybluke-keyboard-theme"
    const val VERSION = 1
    const val LINK_SCHEME = "simplybluke"
    const val LINK_HOST = "theme"
    private const val LINK_PREFIX = "$LINK_SCHEME://$LINK_HOST?d="

    /** Larger input is rejected before parsing. */
    const val MAX_INPUT_CHARS = 512 * 1024
    private const val MAX_INFLATED_BYTES = 256 * 1024
    const val MAX_OVERRIDES = 400
    private const val MAX_NAME_LENGTH = 40
    private val overrideKeyPattern = Regex("""^\d{1,5}:\d{1,3}$""")

    class ImportException(message: String) : Exception(message)

    // ---------- Export ----------

    fun toJson(theme: KeyboardThemeDefinition): JSONObject = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("name", theme.name)
        .put("case", formatOpaqueHexColor(theme.caseArgb))
        .put("caseMetallic", theme.caseMetallic)
        .put("plate", formatOpaqueHexColor(theme.plateArgb))
        .put("alpha", styleToJson(theme.alphaStyle))
        .put("modifier", styleToJson(theme.modifierStyle))
        .put("accent", styleToJson(theme.accentStyle))
        .apply {
            if (theme.keyOverrides.isNotEmpty()) {
                put(
                    "overrides",
                    JSONObject().apply {
                        theme.keyOverrides.toSortedMap().forEach { (key, style) -> put(key, styleToJson(style)) }
                    },
                )
            }
        }

    fun toJsonText(theme: KeyboardThemeDefinition): String = toJson(theme).toString(2)

    /** A simplybluke://theme link holding the whole theme, compressed. Also the QR payload. */
    fun toLink(theme: KeyboardThemeDefinition): String {
        val bytes = toJson(theme).toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val compressed = try {
            deflater.setInput(bytes)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
            output.toByteArray()
        } finally {
            deflater.end()
        }
        return LINK_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    fun toMarkdown(theme: KeyboardThemeDefinition): String = buildString {
        val name = markdownEscape(theme.name)
        appendLine("# $name")
        appendLine()
        appendLine("A keyboard theme for Simply Bluke. To use it, open Settings → Keyboard → Themes → Import and pick this file, or open the link below on your phone.")
        appendLine()
        appendLine("| Part | Color | Text color | Text size |")
        appendLine("|---|---|---|---|")
        appendLine("| Case | `${formatOpaqueHexColor(theme.caseArgb)}`${if (theme.caseMetallic) " (metallic)" else ""} | | |")
        appendLine("| Background | `${formatOpaqueHexColor(theme.plateArgb)}` | | |")
        appendStyleRow("Alpha keys", theme.alphaStyle)
        appendStyleRow("Control keys", theme.modifierStyle)
        appendStyleRow("Accent keys", theme.accentStyle)
        theme.keyOverrides.toSortedMap().forEach { (key, style) -> appendStyleRow("Key `$key`", style) }
        appendLine()
        appendLine("Link: <${toLink(theme)}>")
        appendLine()
        appendLine("```json")
        appendLine(toJsonText(theme))
        appendLine("```")
    }

    private fun StringBuilder.appendStyleRow(label: String, style: KeyboardKeyStyle) {
        appendLine(
            "| $label | `${formatOpaqueHexColor(style.backgroundArgb)}` | `${formatOpaqueHexColor(style.legendArgb)}` | " +
                "${(style.legendScale * 100).toInt()}% |",
        )
    }

    private fun markdownEscape(text: String): String =
        text.replace(Regex("""[\\`*_\[\]#|<>]""")) { "\\${it.value}" }

    private fun styleToJson(style: KeyboardKeyStyle): JSONObject = JSONObject()
        .put("background", formatOpaqueHexColor(style.backgroundArgb))
        .put("legend", formatOpaqueHexColor(style.legendArgb))
        .apply { if (style.legendScale != 1f) put("legendScale", style.legendScale.toDouble()) }

    // ---------- Import ----------

    /**
     * Reads a theme from any of the shared forms: a link, a Markdown file with a
     * ```json block, or bare JSON. The returned theme has a new custom id.
     */
    @Throws(ImportException::class)
    fun parse(input: String): KeyboardThemeDefinition {
        if (input.length > MAX_INPUT_CHARS) throw ImportException("This file is too large to be a keyboard theme.")
        val text = input.trim().removePrefix("﻿")
        if (text.isEmpty()) throw ImportException("There is nothing to import.")
        val link = Regex("""$LINK_SCHEME://$LINK_HOST\?[^\s<>)"']+""").find(text)?.value
        val json = when {
            text.startsWith("{") -> text
            link != null -> decodeLink(link)
            else -> jsonFromMarkdown(text)
                ?: throw ImportException("No keyboard theme found. Use a Simply Bluke theme link, .json or .md file.")
        }
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw ImportException("The theme data is damaged and can't be read.")
        }
        return parseJson(root)
    }

    private fun jsonFromMarkdown(text: String): String? =
        Regex("""```(?:json)?\s*\n(\{[\s\S]*?\})\s*\n```""").findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { it.contains(FORMAT) }

    private fun decodeLink(link: String): String {
        val data = link.substringAfter("?").split('&')
            .firstOrNull { it.startsWith("d=") }
            ?.removePrefix("d=")
            ?: throw ImportException("This link has no theme in it.")
        val compressed = try {
            Base64.getUrlDecoder().decode(data)
        } catch (_: IllegalArgumentException) {
            throw ImportException("This theme link is damaged or cut short.")
        }
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                output.write(buffer, 0, count)
                if (output.size() > MAX_INFLATED_BYTES) throw ImportException("This theme link is too large.")
            }
            if (!inflater.finished()) throw ImportException("This theme link is damaged or cut short.")
            return output.toString(Charsets.UTF_8.name())
        } catch (_: DataFormatException) {
            throw ImportException("This theme link is damaged or cut short.")
        } finally {
            inflater.end()
        }
    }

    private fun parseJson(root: JSONObject): KeyboardThemeDefinition {
        val format = root.optString("format")
        if (format != FORMAT) throw ImportException("This isn't a Simply Bluke keyboard theme.")
        val version = root.optInt("version", 0)
        if (version < 1) throw ImportException("This theme file is missing its version.")
        if (version > VERSION) {
            throw ImportException("This theme was made with a newer Simply Bluke. Update the app to import it.")
        }
        val overridesJson = root.optJSONObject("overrides")
        val overrides = buildMap {
            overridesJson?.keys()?.forEach { key ->
                if (!overrideKeyPattern.matches(key)) return@forEach
                if (size >= MAX_OVERRIDES) throw ImportException("This theme changes too many single keys.")
                val style = overridesJson.optJSONObject(key) ?: return@forEach
                put(key, parseStyle(style, "key $key"))
            }
        }
        val name = root.optString("name")
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifEmpty { "Imported theme" }
        return KeyboardThemeDefinition(
            id = "$CUSTOM_KEYBOARD_THEME_PREFIX${UUID.randomUUID()}",
            name = name,
            caseArgb = color(root, "case", "case"),
            caseMetallic = root.optBoolean("caseMetallic", false),
            plateArgb = color(root, "plate", "background"),
            alphaStyle = parseStyle(requireObject(root, "alpha"), "alpha keys"),
            modifierStyle = parseStyle(requireObject(root, "modifier"), "control keys"),
            accentStyle = parseStyle(requireObject(root, "accent"), "accent keys"),
            keyOverrides = overrides,
            editable = true,
        )
    }

    private fun requireObject(root: JSONObject, field: String): JSONObject =
        root.optJSONObject(field) ?: throw ImportException("The theme is missing its $field key colors.")

    private fun parseStyle(json: JSONObject, part: String): KeyboardKeyStyle = KeyboardKeyStyle(
        backgroundArgb = color(json, "background", part),
        legendArgb = color(json, "legend", part),
        legendScale = json.optDouble("legendScale", 1.0)
            .takeUnless { it.isNaN() }
            ?.toFloat()
            ?.coerceIn(0.7f, 1.5f)
            ?: 1f,
    )

    /** Accepts "#RRGGBB" (the shared form) or an ARGB integer (the app's own storage). */
    private fun color(json: JSONObject, field: String, part: String): Int {
        val value = json.opt(field) ?: throw ImportException("The theme is missing a color for $part.")
        val argb = when (value) {
            is String -> parseOpaqueHexColor(value)
            is Number -> value.toLong().toInt()
            else -> null
        } ?: throw ImportException("The theme has an invalid color for $part.")
        return argb or 0xFF000000.toInt()
    }
}
