package dev.arnv.bluke.bluetooth

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestRequestProcessorTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun newerRequestCancelsInFlightWorkAndCompletesLatest() = runTest {
        val started = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val completed = mutableListOf<String>()
        val processor = LatestRequestProcessor<String>(backgroundScope) { request ->
            started += request
            if (request == "first") {
                try {
                    awaitCancellation()
                } finally {
                    cancelled += request
                }
            } else {
                completed += request
            }
        }

        processor.submit("first")
        runCurrent()
        processor.submit("latest")
        runCurrent()

        assertEquals(listOf("first", "latest"), started)
        assertEquals(listOf("first"), cancelled)
        assertEquals(listOf("latest"), completed)
        assertEquals("latest", processor.pending.value)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun pendingStateRetainsLatestRequestForLateRegistrationResume() = runTest {
        val processor = LatestRequestProcessor<String>(backgroundScope) { awaitCancellation() }

        processor.submit("host-a")
        processor.submit("host-b")
        runCurrent()

        assertTrue(processor.pending.value == "host-b")
    }
}
