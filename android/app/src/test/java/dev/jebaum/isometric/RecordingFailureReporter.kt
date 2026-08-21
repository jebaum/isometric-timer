package dev.jebaum.isometric

/**
 * Stands in for Logcat, holding what would have been written.
 *
 * Shared rather than duplicated per test file: the point of these assertions is
 * that a swallowed failure names its operation, so the two suites have to agree
 * on what a report looks like.
 */
internal class RecordingFailureReporter : FailureReporter {
    val reports = mutableListOf<Pair<String, Throwable>>()

    val operations: List<String> get() = reports.map { it.first }

    override fun report(operation: String, cause: Throwable) {
        reports += operation to cause
    }
}
