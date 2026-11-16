package dev.arnv.bluke.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** QR codes for theme links, made and read without the camera. */
object ThemeQrCode {
    /**
     * The QR modules for [text], or null when it is too long for a QR code.
     * Error correction drops from M to L for long texts so more themes fit.
     *
     * Some codes contain data that looks like the corner markers, and then the
     * finder (used for photos, screenshots and the camera) can't locate them. Each
     * candidate is checked with that finder; if it fails, the other mask patterns
     * (same data, scrambled differently) are tried and the first one that scans wins.
     */
    fun encode(text: String): BitMatrix? {
        for (level in listOf(ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)) {
            val default = encodeWith(text, level, mask = null) ?: continue
            if (scansWithFinder(default, text)) return default
            for (mask in 0..7) {
                val candidate = encodeWith(text, level, mask) ?: continue
                if (scansWithFinder(candidate, text)) return candidate
            }
            return default
        }
        return null
    }

    private fun encodeWith(text: String, level: ErrorCorrectionLevel, mask: Int?): BitMatrix? = try {
        val hints = mutableMapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to level,
            EncodeHintType.MARGIN to 4,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        if (mask != null) hints[EncodeHintType.QR_MASK_PATTERN] = mask
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    } catch (_: WriterException) {
        null // Too long at this level.
    } catch (_: IllegalArgumentException) {
        null
    }

    /** True when the finder reads [matrix] back as [text] at a couple of sizes. */
    private fun scansWithFinder(matrix: BitMatrix, text: String): Boolean = listOf(3, 8).all { scale ->
        val size = matrix.width * scale
        val pixels = IntArray(size * size) { index ->
            if (matrix.get((index % size) / scale, (index / size) / scale)) BLACK else WHITE
        }
        try {
            QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))),
                mapOf(DecodeHintType.TRY_HARDER to true),
            ).text == text
        } catch (_: ReaderException) {
            false
        }
    }

    /** Black-on-white bitmap of [matrix], each module [scale] pixels wide. */
    fun toBitmap(matrix: BitMatrix, scale: Int = 12): Bitmap {
        val size = matrix.width * scale
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] = if (matrix.get(x / scale, y / scale)) BLACK else WHITE
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Reads the text of the first QR code found in the image at [uri], or null. */
    fun decode(resolver: ContentResolver, uri: Uri): String? {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_DECODE_SIDE) decoder.setTargetSampleSize((longest + MAX_DECODE_SIDE - 1) / MAX_DECODE_SIDE)
        }
        return decode(bitmap)
    }

    fun decode(bitmap: Bitmap): String? {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return decode(pixels, bitmap.width, bitmap.height)
    }

    /**
     * Reads a QR code from ARGB [pixels] laid out row by row. Tries the clean-image
     * reader first (exported PNGs, screenshots of just the code), then the detector
     * that finds a code inside a larger picture, also at half size, since ZXing's
     * finder can miss perfectly sharp codes at some scales.
     */
    fun decode(pixels: IntArray, width: Int, height: Int): String? {
        decodeSource(RGBLuminanceSource(width, height, pixels))?.let { return it }
        if (width >= 80 && height >= 80) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            val half = IntArray(halfWidth * halfHeight) { index ->
                pixels[(index / halfWidth) * 2 * width + (index % halfWidth) * 2]
            }
            decodeSource(RGBLuminanceSource(halfWidth, halfHeight, half))?.let { return it }
        }
        return null
    }

    /**
     * Reads a QR code from a camera frame's luminance (Y) plane, where each row is
     * [rowStride] bytes long. Uses only the fast detector passes so the live
     * scanner keeps up with the camera.
     */
    fun decodeLuminance(luminance: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, rowStride, height, 0, 0, width, height, false)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        for (bitmap in listOf(BinaryBitmap(HybridBinarizer(source)), BinaryBitmap(HybridBinarizer(source.invert())))) {
            try {
                return QRCodeReader().decode(bitmap, hints).text
            } catch (_: ReaderException) {
            }
        }
        return null
    }

    private fun decodeSource(source: com.google.zxing.LuminanceSource): String? {
        val attempts = listOf(
            BinaryBitmap(HybridBinarizer(source)) to mapOf(DecodeHintType.PURE_BARCODE to true),
            BinaryBitmap(HybridBinarizer(source)) to mapOf(DecodeHintType.TRY_HARDER to true),
            BinaryBitmap(GlobalHistogramBinarizer(source)) to mapOf(DecodeHintType.TRY_HARDER to true),
            BinaryBitmap(HybridBinarizer(source.invert())) to mapOf(DecodeHintType.TRY_HARDER to true),
        )
        for ((bitmap, hints) in attempts) {
            try {
                return QRCodeReader().decode(bitmap, hints).text
            } catch (_: NotFoundException) {
            } catch (_: ReaderException) {
            }
        }
        return null
    }

    private const val MAX_DECODE_SIDE = 2048
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
