package dev.jebaum.isometric

/**
 * Where a deliberately swallowed failure goes.
 *
 * Cues and persistence are best-effort on purpose: a vendor vibrator HAL or a
 * locked database must not take down a timer whose actual job is counting. But
 * a swallowed failure that leaves no trace is indistinguishable from hardware
 * that is simply quiet, which makes the next bug report unanswerable. Reporting
 * arrives through this interface rather than `android.util.Log` so
 * [RoutineViewModel] stays plain-JVM testable — the same reason its clock and
 * its stores are injected. [LogcatFailureReporter] is the Android half.
 *
 * Implementations are handed the operation and the exception and nothing else.
 * The operation label is written here, by hand, and carries no user data and no
 * values — never the preferences being written, never a weight. The throwable
 * goes through as-is, because a stack trace with its message intact is the
 * whole diagnostic; whatever the platform put in it travels with it.
 *
 * Implementations should not throw. Callers guard anyway, through
 * [reportSafely]: the promise is worth more than the trust.
 */
fun interface FailureReporter {
    fun report(operation: String, cause: Throwable)
}

/**
 * Reports without trusting the reporter: a diagnostics seam must never be able
 * to take down the routine it is observing.
 */
fun FailureReporter.reportSafely(operation: String, cause: Throwable) {
    runCatching { report(operation, cause) }
}

/** Keeps callers with nowhere to report to, particularly tests, lightweight. */
object SilentFailureReporter : FailureReporter {
    override fun report(operation: String, cause: Throwable) = Unit
}
