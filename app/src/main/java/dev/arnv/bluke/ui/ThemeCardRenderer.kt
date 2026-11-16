package dev.arnv.bluke.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.zxing.common.BitMatrix
import dev.arnv.bluke.ui.theme.contrastRatio
import dev.arnv.bluke.ui.theme.formatOpaqueHexColor

/**
 * Draws a shareable card for a keyboard theme: its name, the QR code and the five
 * base colors, on a quiet off-white page. The QR stays black on white so it scans
 * whatever the theme's colors are.
 */
object ThemeCardRenderer {
    const val WIDTH = 1080
    const val HEIGHT = 1440

    private const val MARGIN = 96f
    private const val PAGE = 0xFFFAFAF8.toInt()
    private const val INK = 0xFF1C1C1E.toInt()
    private const val MUTED = 0xFF8A8A8F.toInt()
    private const val FAINT = 0xFFB0B0B4.toInt()
    private const val HAIRLINE = 0xFFE6E6E3.toInt()

    fun render(theme: KeyboardThemeDefinition, qr: BitMatrix): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAGE)
        val centerX = WIDTH / 2f

        // Header: small caps label, then the name.
        val label = textPaint(28f, MUTED, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            letterSpacing = 0.24f
        }
        canvas.drawText("KEYBOARD THEME", centerX, 150f, label)
        val title = textPaint(76f, INK, Typeface.create("sans-serif-medium", Typeface.NORMAL)).apply {
            letterSpacing = -0.01f
        }
        canvas.drawText(fit(theme.name, title, WIDTH - MARGIN * 2), centerX, 250f, title)

        // Accent mark under the name.
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accentStyle.backgroundArgb }
        canvas.drawRoundRect(RectF(centerX - 40f, 290f, centerX + 40f, 298f), 4f, 4f, accent)

        // QR on a white tile with a hairline border.
        val tileSize = 680f
        val tile = RectF(centerX - tileSize / 2, 340f, centerX + tileSize / 2, 340f + tileSize)
        canvas.drawRoundRect(tile, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawRoundRect(
            tile,
            40f,
            40f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = HAIRLINE
            },
        )
        drawQr(canvas, qr, tile, inset = 24f)

        // Base colors.
        val swatches = listOf(
            "Case" to theme.caseArgb,
            "Plate" to theme.plateArgb,
            "Keys" to theme.alphaStyle.backgroundArgb,
            "Mods" to theme.modifierStyle.backgroundArgb,
            "Accent" to theme.accentStyle.backgroundArgb,
        )
        val rowTop = tile.bottom + 80f
        val radius = 42f
        val step = (WIDTH - MARGIN * 2) / swatches.size
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x1F000000
        }
        val name = textPaint(26f, MUTED, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        val hex = textPaint(24f, FAINT, Typeface.MONOSPACE)
        swatches.forEachIndexed { index, (swatchLabel, argb) ->
            val x = MARGIN + step * index + step / 2
            val y = rowTop + radius
            fill.color = argb or 0xFF000000.toInt()
            canvas.drawCircle(x, y, radius, fill)
            // A faint ring keeps pale colors visible on the pale page.
            if (contrastRatio(argb, PAGE) < 1.4) canvas.drawCircle(x, y, radius, ring)
            canvas.drawText(swatchLabel, x, y + radius + 48f, name)
            canvas.drawText(formatOpaqueHexColor(argb), x, y + radius + 84f, hex)
        }

        // Footer.
        val footer = textPaint(26f, FAINT, Typeface.create("sans-serif", Typeface.NORMAL))
        canvas.drawText("Scan in Simply Bluke  ·  Themes → Scan", centerX, HEIGHT - 80f, footer)
        return bitmap
    }

    /** Draws the QR modules crisply into [tile], keeping the code's own quiet zone. */
    private fun drawQr(canvas: Canvas, qr: BitMatrix, tile: RectF, inset: Float) {
        // Whole-pixel modules and origin, so neighbouring rows meet without seams.
        val area = tile.width() - inset * 2
        val module = (area / qr.width).toInt().toFloat()
        val size = module * qr.width
        val left = (tile.left + (tile.width() - size) / 2).toInt().toFloat()
        val top = (tile.top + (tile.height() - size) / 2).toInt().toFloat()
        val dark = Paint().apply { color = 0xFF000000.toInt() }
        for (y in 0 until qr.height) {
            var x = 0
            while (x < qr.width) {
                if (!qr.get(x, y)) {
                    x++
                    continue
                }
                // Draw runs of dark modules as one rectangle.
                val start = x
                while (x < qr.width && qr.get(x, y)) x++
                canvas.drawRect(left + start * module, top + y * module, left + x * module, top + (y + 1) * module, dark)
            }
        }
    }

    private fun textPaint(size: Float, color: Int, typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
    }

    /** [text] shortened with an ellipsis so it fits [maxWidth]. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.take(end).trimEnd() + "…") > maxWidth) end--
        return text.take(end).trimEnd() + "…"
    }
}
