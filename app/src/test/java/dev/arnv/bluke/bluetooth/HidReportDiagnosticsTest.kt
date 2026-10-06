package dev.arnv.bluke.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HidReportDiagnosticsTest {
    @Test
    fun throttlesSuccessAndCountsWindowAttempts() {
        val diagnostics = HidReportDiagnostics()
        assertEquals("accepted=true attempts=1 failures=0", diagnostics.record(3, true, 0))
        assertNull(diagnostics.record(3, true, 8))
        assertEquals("accepted=true attempts=2 failures=0", diagnostics.record(3, true, 1000))
    }

    @Test
    fun logsFailureAndRecoveryImmediatelyButThrottlesRepeatedFailures() {
        val diagnostics = HidReportDiagnostics()
        diagnostics.record(3, true, 0)
        assertEquals("accepted=false attempts=1 failures=1", diagnostics.record(3, false, 8))
        assertNull(diagnostics.record(3, false, 16))
        assertEquals("accepted=true attempts=2 failures=1", diagnostics.record(3, true, 24))
    }

    @Test
    fun reportIdsHaveIndependentWindows() {
        val diagnostics = HidReportDiagnostics()
        diagnostics.record(3, true, 0)
        assertEquals("accepted=true attempts=1 failures=0", diagnostics.record(1, true, 8))
    }

    @Test
    fun decodesUnsignedLittleEndianAxesFromActualPayload() {
        val report = buildGamepadReport(0, 0, -1f, 0f, 1f, -1f)
        assertEquals("X=0 Y=32767 Z=65535 Rx=0 Rz=32767", gamepadAxisDiagnostic(report))
        assertEquals("unexpectedLength=0", gamepadAxisDiagnostic(byteArrayOf()))
    }
}
