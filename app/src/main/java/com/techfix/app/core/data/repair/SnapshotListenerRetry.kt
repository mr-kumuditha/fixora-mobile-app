package com.techfix.app.core.data.repair

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlin.math.min

/**
 * When a Firestore snapshot listener should be re-established after it fails.
 *
 * A `addSnapshotListener` callback that reports an error is **finished** — the
 * registration is dropped and no further snapshots arrive on it. A screen that
 * is meant to track a repair live therefore has to register a new listener, or
 * it silently freezes on the last value it happened to receive. That is the
 * bug this exists to prevent, so the policy lives in one testable place rather
 * than inline in the repository.
 *
 * Split by whether re-listening could plausibly succeed:
 *
 * - **Recoverable** — the stream broke for reasons that pass: the backend was
 *   briefly unavailable, the deadline was exceeded, the auth token was being
 *   refreshed. Re-register, backing off so a genuinely down backend isn't
 *   hammered.
 * - **Terminal** — re-listening would fail identically every time
 *   (the rules deny it, the caller isn't signed in, the query is malformed).
 *   Give up and let the error surface, so the UI can show it instead of
 *   spinning forever.
 *
 * Anything that is not a [FirebaseFirestoreException] is treated as terminal:
 * it means the failure came from our own mapping code, not the network, and
 * retrying a programming error in a loop would hide it.
 */
internal object SnapshotListenerRetry {

    /** First backoff step; doubles from here. */
    const val INITIAL_BACKOFF_MS = 1_000L

    /** Ceiling, so a long outage settles into a steady slow poll. */
    const val MAX_BACKOFF_MS = 30_000L

    /**
     * Held as names rather than as `FirebaseFirestoreException.Code` values so
     * that initialising this object never touches the Firestore SDK's enum,
     * whose class initialiser needs the Android runtime. That keeps the
     * decision below testable as plain JVM code — which matters, because this
     * is the logic that broke live tracking.
     */
    private val TERMINAL_CODE_NAMES = setOf(
        "PERMISSION_DENIED",
        "UNAUTHENTICATED",
        "INVALID_ARGUMENT",
        "FAILED_PRECONDITION",
        "UNIMPLEMENTED",
        "OUT_OF_RANGE",
        "ALREADY_EXISTS",
        "DATA_LOSS",
        "NOT_FOUND",
    )

    /**
     * Anything that is not a [FirebaseFirestoreException] came from our own
     * mapping code rather than the network, and retrying a programming error
     * in a loop would hide it.
     */
    fun isRecoverable(error: Throwable): Boolean =
        error is FirebaseFirestoreException && isRecoverableCode(error.code.name)

    /** The classification itself, over the SDK's stable code names. */
    fun isRecoverableCode(codeName: String): Boolean = codeName !in TERMINAL_CODE_NAMES

    /**
     * Exponential backoff, capped. [attempt] is zero-based, matching the
     * counter `Flow.retryWhen` hands over.
     */
    fun backoffMillis(attempt: Long): Long {
        if (attempt < 0) return INITIAL_BACKOFF_MS
        // Shifting past 62 overflows a Long; the cap makes anything beyond a
        // handful of attempts identical anyway.
        val exponent = min(attempt, 32L).toInt()
        val raw = INITIAL_BACKOFF_MS shl exponent
        return if (raw <= 0L) MAX_BACKOFF_MS else min(raw, MAX_BACKOFF_MS)
    }
}
