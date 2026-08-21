package dev.jebaum.isometric

import android.util.Log

/**
 * The app's one route to Logcat, kept apart from [FailureReporter] so the
 * `android.util.Log` import never reaches a class the JVM tests construct.
 *
 * Warning rather than error: every caller has already degraded gracefully, so
 * these read as "the countdown carried on without its beep", not as crashes.
 * One tag for all of them, so `adb logcat -s IsometricTimer:W` is the whole
 * story of what the app has quietly given up on.
 */
object LogcatFailureReporter : FailureReporter {
    override fun report(operation: String, cause: Throwable) {
        Log.w(TAG, "$operation failed", cause)
    }

    // Within the 23 characters that Log tags are historically truncated at.
    private const val TAG = "IsometricTimer"
}
