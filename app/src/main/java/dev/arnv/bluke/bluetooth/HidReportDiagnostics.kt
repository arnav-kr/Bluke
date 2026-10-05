package dev.arnv.bluke.bluetooth

/** Only used by the serialized report sender; never changes or schedules HID reports. */
internal class HidReportDiagnostics {
    private data class Window(
        var lastLogMillis: Long,
        var lastAccepted: Boolean,
        var attempts: Int = 0,
        var failures: Int = 0,
    )

    private val windows = mutableMapOf<Int, Window>()

    fun record(reportId: Int, accepted: Boolean, nowMillis: Long): String? {
        val previous = windows[reportId]
        val window = previous ?: Window(nowMillis, accepted).also { windows[reportId] = it }
        window.attempts++
        if (!accepted) window.failures++
        val changed = window.lastAccepted != accepted
        window.lastAccepted = accepted
        if (previous != null && !changed && nowMillis - window.lastLogMillis < 1_000L) return null
        val summary = "accepted=$accepted attempts=${window.attempts} failures=${window.failures}"
        window.lastLogMillis = nowMillis
        window.attempts = 0
        window.failures = 0
        return summary
    }
}

internal fun gamepadAxisDiagnostic(report: ByteArray): String {
    if (report.size != GAMEPAD_REPORT_SIZE_BYTES) return "unexpectedLength=${report.size}"
    fun axis(offset: Int) = (report[offset].toInt() and 0xFF) or
        ((report[offset + 1].toInt() and 0xFF) shl 8)
    return "leftX=${axis(4)} leftY=${axis(6)} rightX=${axis(8)} rightY=${axis(10)}"
}
