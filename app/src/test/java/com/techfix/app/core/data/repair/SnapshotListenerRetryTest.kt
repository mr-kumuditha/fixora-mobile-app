package com.techfix.app.core.data.repair

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for the Block 7 runtime bug: the customer's repair tracking
 * screen stopped updating partway through the technician's workflow.
 *
 * The cause was that a Firestore snapshot listener which reports an error is
 * finished — the registration is dropped — and the flow closed on that error,
 * so no new listener was ever registered. The screen kept rendering the last
 * status it had received. UNAVAILABLE, the code Firestore raises for exactly
 * the transient drops that happen while a screen is left open for minutes, is
 * therefore the single most important case here.
 */
class SnapshotListenerRetryTest {

    // The Firestore SDK's Code enum can't be initialised in a JVM unit test —
    // its class initialiser needs the Android runtime — so the classification
    // is exercised through the code names it is keyed on.

    @Test
    fun `a transient unavailable listener drop is re-established`() {
        // The exact failure that froze the tracking screen mid-repair.
        assertTrue(SnapshotListenerRetry.isRecoverableCode("UNAVAILABLE"))
    }

    @Test
    fun `the other transient codes are retried too`() {
        listOf("ABORTED", "DEADLINE_EXCEEDED", "INTERNAL", "RESOURCE_EXHAUSTED", "CANCELLED", "UNKNOWN")
            .forEach { code ->
                assertTrue("$code should be retried", SnapshotListenerRetry.isRecoverableCode(code))
            }
    }

    @Test
    fun `a rules rejection is terminal, not retried forever`() {
        // Re-listening would be denied identically every time, so the error has
        // to reach the UI instead of spinning.
        assertFalse(SnapshotListenerRetry.isRecoverableCode("PERMISSION_DENIED"))
        assertFalse(SnapshotListenerRetry.isRecoverableCode("UNAUTHENTICATED"))
    }

    @Test
    fun `every terminal code is refused`() {
        listOf(
            "PERMISSION_DENIED", "UNAUTHENTICATED", "INVALID_ARGUMENT", "FAILED_PRECONDITION",
            "UNIMPLEMENTED", "OUT_OF_RANGE", "ALREADY_EXISTS", "DATA_LOSS", "NOT_FOUND",
        ).forEach { code ->
            assertFalse("$code should be terminal", SnapshotListenerRetry.isRecoverableCode(code))
        }
    }

    @Test
    fun `a non-firestore failure is terminal so a mapping bug is not retried in a loop`() {
        assertFalse(SnapshotListenerRetry.isRecoverable(IllegalStateException("mapping blew up")))
        assertFalse(SnapshotListenerRetry.isRecoverable(IOException("not wrapped by firestore")))
    }

    @Test
    fun `backoff doubles from one second and caps`() {
        assertEquals(1_000L, SnapshotListenerRetry.backoffMillis(0))
        assertEquals(2_000L, SnapshotListenerRetry.backoffMillis(1))
        assertEquals(4_000L, SnapshotListenerRetry.backoffMillis(2))
        assertEquals(8_000L, SnapshotListenerRetry.backoffMillis(3))
        assertEquals(16_000L, SnapshotListenerRetry.backoffMillis(4))
        assertEquals(SnapshotListenerRetry.MAX_BACKOFF_MS, SnapshotListenerRetry.backoffMillis(5))
    }

    @Test
    fun `backoff never overflows or goes negative on a long outage`() {
        // A screen left open all day must keep polling at the cap, not wrap
        // around to a negative delay and throw.
        listOf(10L, 40L, 62L, 63L, 64L, 1_000L, Long.MAX_VALUE).forEach { attempt ->
            val delay = SnapshotListenerRetry.backoffMillis(attempt)
            assertTrue("attempt $attempt gave $delay", delay in 1..SnapshotListenerRetry.MAX_BACKOFF_MS)
        }
    }
}
