package dev.jebaum.isometric

import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.cues.CuePlayer
import dev.jebaum.isometric.timer.Kind
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.WARNING_SECONDS
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder : CuePlayer {
    val played = mutableListOf<Cue>()
    var releases = 0
    override fun play(cue: Cue) {
        played += cue
    }

    override fun release() {
        releases += 1
    }
}

private class History(initial: List<Long> = emptyList()) : CompletionHistoryStore {
    val completions = initial.map { WeightedCompletion(it, 0.0) }.toMutableList()
    var closes = 0

    /** Derived, so the two views of the log cannot fall out of sync. */
    val entries: List<Long> get() = completions.map { it.completedAtMillis }

    override fun record(completedAtMillis: Long, weightLb: Double) {
        completions += WeightedCompletion(completedAtMillis, weightLb)
    }

    override fun latest(): Long? = entries.maxOrNull()

    override fun between(startInclusiveMillis: Long, endExclusiveMillis: Long): List<Long> =
        entries.filter { it in startInclusiveMillis until endExclusiveMillis }.sorted()

    override fun weightHistory(): List<WeightedCompletion> =
        completions.sortedBy { it.completedAtMillis }

    override fun close() {
        closes++
    }
}

/**
 * Adversarial cover for [RoutineViewModel]: cue dispatch under pause, skip,
 * completion and cue-toggle histories, plus the lifecycle and settings
 * contracts. Everything here drives the production class, not a copy of it.
 *
 * [CueDispatchTest] covers the ordinary paths; this file covers the edges that
 * two review passes turned up.
 */
class RoutineViewModelTest {

    private var time = 1_000.0
    private val player = Recorder()

    private fun model(
        settings: Settings = Settings(cycles = 2, hold = 5, switch = 2, rest = 4),
        cuesEnabled: Boolean = true,
        history: CompletionHistoryStore = EmptyCompletionHistoryStore,
        wallNow: () -> Long = { 0L },
    ) = RoutineViewModel(
        store = FakeSettingsStore(settings, cuesEnabled),
        player = player,
        now = { time },
        history = history,
        wallNow = wallNow,
    )

    private fun run(m: RoutineViewModel, seconds: Double, step: Double = 1.0 / 60.0) {
        repeat((seconds / step).toInt()) {
            time += step
            if (m.running) m.tick()
        }
    }

    // ---- the cue sentinel cannot be re-broken -------------------------------

    @Test
    fun `enabling cues while paused mid-hold neither swallows nor duplicates`() {
        val m = model(Settings(cycles = 1, hold = 10, switch = 0, rest = 0))
        m.toggle()
        run(m, 8.2) // ~1.8s left: Warn(3) and Warn(2) have fired
        m.toggle() // pause
        val warnsBefore = player.played.count { it is Cue.Warn }
        val secondsLeft = m.snapshot.secondsLeft

        m.setCues(false)
        m.setCues(true)
        m.toggle() // resume

        assertEquals(
            "resuming re-announced the phase or the current warning second",
            warnsBefore,
            player.played.count { it is Cue.Warn },
        )
        assertEquals(1, player.played.count { it is Cue.Enter })

        // The remaining warning seconds must still fire.
        run(m, secondsLeft + 0.5)
        assertEquals(WARNING_SECONDS, player.played.count { it is Cue.Warn })
    }

    @Test
    fun `enabling cues mid-hold outside the warning window still warns three times`() {
        val m = model(Settings(cycles = 1, hold = 30, switch = 0, rest = 0))
        m.toggle()
        run(m, 5.0) // 25s left, far outside the window
        m.toggle() // pause
        m.setCues(false)
        m.setCues(true)
        m.toggle() // resume
        run(m, 30.0)

        assertEquals(WARNING_SECONDS, player.played.count { it is Cue.Warn })
    }

    @Test
    fun `a phase boundary crossed while cues are off is not announced retroactively`() {
        val m = model(Settings(cycles = 1, hold = 4, switch = 2, rest = 0))
        m.toggle() // Enter(HOLD)
        m.setCues(false)
        run(m, 4.5) // crosses into SWITCH silently
        player.played.clear()
        m.toggle() // pause
        m.setCues(true)
        m.toggle() // resume

        assertTrue(
            "re-enabling cues replayed a boundary that already went by: ${player.played}",
            player.played.isEmpty(),
        )

        // The *next* boundary must still announce. A 4s hold is inside the
        // warning window one second in, so filter to the boundary cue.
        run(m, 3.0)
        assertEquals(
            listOf<Cue>(Cue.Enter(Kind.HOLD)),
            player.played.filterIsInstance<Cue.Enter>(),
        )
    }

    @Test
    fun `reset then start always re-announces regardless of the cue toggle history`() {
        val scripts = listOf(
            listOf(false),
            listOf(true),
            listOf(false, true),
            listOf(true, false, true),
        )
        for (script in scripts) {
            val recorder = Recorder()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, 3, 0, 0)), recorder, { time })
            m.toggle()
            run(m, 1.0)
            m.reset()
            script.forEach(m::setCues)
            recorder.played.clear()
            m.toggle()
            if (script.last()) {
                assertEquals("script=$script", listOf<Cue>(Cue.Enter(Kind.HOLD)), recorder.played)
            } else {
                assertTrue("script=$script", recorder.played.isEmpty())
            }
        }
    }

    // ---- completion --------------------------------------------------------

    @Test
    fun `done is announced exactly once no matter how many ticks follow`() {
        val m = model(Settings(cycles = 1, hold = 2, switch = 0, rest = 0))
        m.toggle()
        run(m, 10.0)
        repeat(20) { time += 0.5; m.tick() }
        repeat(5) { m.skip() }

        assertEquals(1, player.played.count { it is Cue.Done })
    }

    @Test
    fun `skipping off the end announces and records done once and not again`() {
        val history = History()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, hold = 5, switch = 0, rest = 0),
            history = history,
            wallNow = { completedAt },
        )
        m.toggle()
        repeat(10) { time += 0.01; m.skip() }

        assertEquals(1, player.played.count { it is Cue.Done })
        assertEquals(2, player.played.count { it is Cue.Enter })
        assertEquals(listOf(completedAt), history.entries)
    }

    /**
     * Regression: `toggle()` short-circuits on the live `routine.done()`, so a
     * tap landing after the routine finished but before the frame loop ticked
     * used to reset straight through the completion without announcing it.
     */
    @Test
    fun `tapping the button in the frame the routine completes still announces done`() {
        val m = model(Settings(cycles = 1, hold = 2, switch = 0, rest = 0))
        m.toggle()
        run(m, 3.9) // still inside the routine (total 4s)
        assertFalse(m.snapshot.done)
        player.played.clear()

        time += 0.2 // the routine crosses the finish line with no tick in between
        m.toggle() // user taps "Pause"

        assertTrue(
            "expected a Done cue before the reset; got ${player.played}",
            player.played.any { it is Cue.Done },
        )
        assertFalse("the tap should also have reset the routine", m.snapshot.started)
    }

    @Test
    fun `completion is recorded exactly once even while cues are disabled`() {
        val history = History()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, hold = 2, switch = 0, rest = 0),
            cuesEnabled = false,
            history = history,
            wallNow = { completedAt },
        )

        m.toggle()
        run(m, 10.0)
        repeat(20) { time += 0.5; m.tick() }
        repeat(5) { m.skip() }

        assertEquals(listOf(completedAt), history.entries)
        assertEquals(completedAt, m.lastCompletionAt)
        assertEquals(1, m.historyVersion)
        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `ending an unfinished routine does not enter history`() {
        val history = History()
        val m = model(
            settings = Settings(cycles = 1, hold = 10, switch = 0, rest = 0),
            history = history,
        )

        m.toggle()
        run(m, 2.0)
        m.reset()

        assertTrue(history.entries.isEmpty())
        assertNull(m.lastCompletionAt)
    }

    @Test
    fun `spacing warning ends at exactly eight hours`() {
        val completedAt = 1_700_000_000_000L
        val eligibleAt = completedAt + RoutineViewModel.MINIMUM_COMPLETION_GAP_MILLIS
        var wallTime = eligibleAt - 1L
        val m = model(history = History(listOf(completedAt)), wallNow = { wallTime })

        assertEquals(eligibleAt, m.spacingWarningAt())
        wallTime = eligibleAt
        assertNull(m.spacingWarningAt())
    }

    @Test
    fun `eight hour gap uses elapsed UTC time across a west to east timezone change`() {
        val westCoastCompletion = ZonedDateTime.of(
            2026, 8, 15, 8, 0, 0, 0,
            ZoneId.of("America/Los_Angeles"),
        ).toInstant().toEpochMilli()
        var eastCoastNow = ZonedDateTime.of(
            2026, 8, 15, 16, 0, 0, 0,
            ZoneId.of("America/New_York"),
        ).toInstant().toEpochMilli()
        val eligibleAt = westCoastCompletion + RoutineViewModel.MINIMUM_COMPLETION_GAP_MILLIS
        val m = model(
            history = History(listOf(westCoastCompletion)),
            wallNow = { eastCoastNow },
        )

        // 8 AM Pacific to 4 PM Eastern looks like eight hours on the clocks,
        // but represents only five elapsed hours. Eligibility is 7 PM Eastern.
        assertEquals(eligibleAt, m.spacingWarningAt())
        assertEquals(
            19,
            java.time.Instant.ofEpochMilli(eligibleAt)
                .atZone(ZoneId.of("America/New_York"))
                .hour,
        )

        eastCoastNow = eligibleAt
        assertNull(m.spacingWarningAt())
    }

    // ---- weight ------------------------------------------------------------

    @Test
    fun `completion records the weight in effect`() {
        val history = History()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, hold = 2, switch = 0, rest = 0),
            history = history,
            wallNow = { completedAt },
        )

        m.updateWeight(12.5)
        m.toggle()
        run(m, 6.0)

        assertEquals(listOf(WeightedCompletion(completedAt, 12.5)), history.weightHistory())
    }

    @Test
    fun `weight defaults to bodyweight zero and persists through the store`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4))
        val m = RoutineViewModel(store, player, { time })

        assertEquals(0.0, m.weightLb, 0.0)

        m.updateWeight(17.25)

        assertEquals(17.25, m.weightLb, 0.0)
        assertEquals(
            "the weight write dropped the rest of the saved value",
            RoutinePreferences(Settings(2, 5, 2, 4), cuesEnabled = true, weightLb = 17.25),
            store.saved,
        )
    }

    @Test
    fun `updating the weight does not disturb an active routine`() {
        val m = model()
        m.toggle()
        run(m, 2.0)
        val before = m.snapshot

        m.updateWeight(20.0)

        assertEquals("the weight change touched the routine", before, m.snapshot)
        assertTrue(m.running)
    }

    @Test
    fun `updateWeight rejects values outside the accepted range without persisting`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4))
        val m = RoutineViewModel(store, player, { time })
        m.updateWeight(10.0)

        for (bad in listOf(-0.5, MAX_WEIGHT_LB + 0.01)) {
            val thrown = runCatching { m.updateWeight(bad) }.exceptionOrNull()
            assertTrue(
                "expected IllegalArgumentException for $bad, got $thrown",
                thrown is IllegalArgumentException,
            )
        }

        assertEquals(10.0, m.weightLb, 0.0)
        assertEquals(10.0, store.saved.weightLb, 0.0)
    }

    // ---- the Save transaction ----------------------------------------------

    /**
     * Only a Save writes. Construction reads the store and must not echo it
     * back, and running the routine — toggle, tick, reset — persists nothing.
     * Dismissing the dialog is not exercised here because it cannot fail: it is
     * a Compose state flip that never reaches the view model at all.
     */
    @Test
    fun `loading preferences never writes them back`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4))
        val m = RoutineViewModel(store, player, { time })

        m.toggle()
        run(m, 3.0)
        m.reset()

        assertEquals("something persisted without a Save", 0, store.writes)
    }

    @Test
    fun `a save commits the settings and the cue toggle in one write`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4), cuesEnabled = false)
        val m = RoutineViewModel(store, player, { time })
        val next = Settings(1, 3, 0, 0)

        m.updatePreferences(next, cuesEnabled = true)

        assertEquals(RoutinePreferences(next, cuesEnabled = true), store.saved)
        assertEquals("the two fields went to storage separately", 1, store.writes)
        assertEquals(next, m.settings)
        assertTrue(m.cuesEnabled)
    }

    /**
     * Regression: the screen used to persist the cue toggle and the settings in
     * two calls, so a value the routine could not be built from threw out of the
     * second — after the first had already reached SharedPreferences. Rejecting
     * a Save must leave both fields exactly as they were, in memory and on disk.
     */
    @Test
    fun `a rejected save persists neither field and mutates no state`() {
        val original = RoutinePreferences(Settings(2, 5, 2, 4), cuesEnabled = false)
        val store = FakeSettingsStore(original.settings, original.cuesEnabled)
        val m = RoutineViewModel(store, player, { time })
        val bad = Settings(cycles = 0, hold = 35, switch = 5, rest = 90)

        val thrown = runCatching { m.updatePreferences(bad, cuesEnabled = true) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $thrown", thrown is IllegalArgumentException)
        assertEquals("a rejected save reached storage", original, store.saved)
        assertEquals("a rejected save reached storage", 0, store.writes)
        assertEquals("in-memory settings were mutated", original.settings, m.settings)
        assertFalse("the cue toggle was mutated", m.cuesEnabled)
    }

    /**
     * The schedule changed, so the routine is rebuilt — once, and with a clean
     * cue state, so the next Start announces itself whichever way the cue toggle
     * moved in the same Save.
     */
    @Test
    fun `a save that changes the schedule rebuilds the routine and re-announces`() {
        for (cuesWereOn in listOf(false, true)) {
            val recorder = Recorder()
            val m = RoutineViewModel(
                FakeSettingsStore(Settings(2, 5, 2, 4), cuesEnabled = cuesWereOn),
                recorder,
                { time },
            )
            m.toggle()
            run(m, 3.0)
            recorder.played.clear()

            m.updatePreferences(Settings(1, 3, 0, 0), cuesEnabled = true)

            assertFalse("cuesWereOn=$cuesWereOn", m.snapshot.started)
            assertTrue("the rebuild announced something: ${recorder.played}", recorder.played.isEmpty())

            m.toggle()
            assertEquals("cuesWereOn=$cuesWereOn", listOf<Cue>(Cue.Enter(Kind.HOLD)), recorder.played)
        }
    }

    /**
     * The view model does not lean on the screen disabling settings while a
     * routine runs: a Save that leaves the schedule alone must not rebuild it,
     * and turning cues on must pick up where the routine already is.
     */
    @Test
    fun `a save that leaves the schedule alone does not disturb a running routine`() {
        val settings = Settings(cycles = 1, hold = 30, switch = 0, rest = 0)
        val store = FakeSettingsStore(settings, cuesEnabled = false)
        val m = RoutineViewModel(store, player, { time })
        m.toggle()
        run(m, 5.0) // 25s left, far outside the warning window
        val before = m.snapshot

        m.updatePreferences(settings, cuesEnabled = true)

        assertEquals("the save rebuilt a routine it did not change", before, m.snapshot)
        assertTrue(m.running)
        assertTrue("re-enabling cues replayed the current phase", player.played.isEmpty())
        assertEquals(RoutinePreferences(settings, cuesEnabled = true), store.saved)

        // The rest of the hold still announces normally.
        run(m, 30.0)
        assertEquals(WARNING_SECONDS, player.played.count { it is Cue.Warn })
    }

    /**
     * Regression: skipping the rebuild when the schedule is unchanged is the
     * right call for a running routine, but a *finished* one has to reset
     * anyway. Otherwise a Save made from the completion screen leaves it up,
     * with no way back to READY but a second tap on Done.
     */
    @Test
    fun `a save from the completion screen returns the routine to ready`() {
        val settings = Settings(cycles = 1, hold = 2, switch = 0, rest = 0)
        val m = RoutineViewModel(FakeSettingsStore(settings), player, { time })
        m.toggle()
        run(m, 6.0)
        assertTrue("the routine never finished", m.snapshot.done)

        m.updatePreferences(settings, cuesEnabled = true)

        assertFalse("the save left the completion screen up", m.snapshot.done)
        assertFalse(m.snapshot.started)
    }

    /**
     * The weight is saved through the same value as the settings and the cue
     * toggle, but is edited from a different dialog. A Save that rebuilt the
     * saved value from only the two fields it stages would silently reset the
     * user's weight to bodyweight.
     */
    @Test
    fun `a settings save carries the weight through untouched`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4), cuesEnabled = false)
        val m = RoutineViewModel(store, player, { time })
        m.updateWeight(25.0)
        val next = Settings(1, 3, 0, 0)

        m.updatePreferences(next, cuesEnabled = true)

        assertEquals(
            "the settings save dropped the weight",
            RoutinePreferences(next, cuesEnabled = true, weightLb = 25.0),
            store.saved,
        )
        assertEquals(25.0, m.weightLb, 0.0)
    }

    // ---- publish() consistency ---------------------------------------------

    /**
     * Regression: `publish()` used to call `snapshot()` and `progress()`
     * separately, so each read the clock independently. Reads straddling a
     * phase mark published a snapshot and a progress describing different
     * phases — the bar resetting a frame before the label changed.
     *
     * The fourth reading below is deliberately left unconsumed: if a second
     * clock read is ever reintroduced it will be taken, and progress will
     * collapse to ~0 while the snapshot still names the outgoing phase.
     */
    @Test
    fun `snapshot and progress are published from a single clock read`() {
        val readings = ArrayDeque(listOf(0.0, 0.0, 4.999_999, 5.000_001))
        var last = 0.0
        val m = RoutineViewModel(
            store = FakeSettingsStore(Settings(cycles = 1, hold = 5, switch = 0, rest = 0)),
            player = player,
            now = { (readings.removeFirstOrNull() ?: last).also { last = it } },
        )
        m.toggle()

        assertEquals("RIGHT SIDE", m.snapshot.phase.label)
        assertEquals(1, m.snapshot.secondsLeft)
        assertFalse(m.snapshot.done)
        assertTrue(
            "progress ${m.progress} disagrees with the phase the snapshot names",
            m.progress > 0.99f,
        )
    }

    @Test
    fun `progress is frozen while paused and pinned at one when done`() {
        val m = model(Settings(cycles = 1, hold = 10, switch = 0, rest = 0))
        m.toggle()
        run(m, 5.0)
        m.toggle() // pause
        val frozen = m.progress
        time += 60.0
        m.tick()
        assertEquals("progress moved while paused", frozen, m.progress, 1e-6f)

        m.toggle() // resume
        run(m, 30.0)
        assertTrue(m.snapshot.done)
        assertEquals(1f, m.progress, 0f)
        assertEquals(
            0f,
            RoutineViewModel(FakeSettingsStore(Settings(1, 5, 0, 0)), Recorder(), { time }).progress,
            0f,
        )
    }

    // ---- lifecycle and the frame loop --------------------------------------

    @Test
    fun `onCleared releases the player exactly once`() {
        val history = History()
        val m = model(history = history)
        m.toggle()
        run(m, 2.0)
        m.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(m)
        assertEquals(1, player.releases)
        assertEquals(1, history.closes)
    }

    /**
     * The real loop is `while (running) { withFrameNanos {}; tick() }`, so it
     * always gets one more `tick()` after `running` goes false. That extra tick
     * must be silent.
     */
    @Test
    fun `the trailing tick after a pause or a reset announces nothing`() {
        val stops = listOf<(RoutineViewModel) -> Unit>({ it.toggle() }, { it.reset() })
        for (stop in stops) {
            val recorder = Recorder()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, 4, 0, 0)), recorder, { time })
            m.toggle()
            run(m, 1.2) // 3s left: Warn(3) has fired
            stop(m)
            val before = recorder.played.size

            time += 1.0 / 60.0
            m.tick()
            m.tick()

            assertEquals(
                "trailing tick spoke up: ${recorder.played.drop(before)}",
                before,
                recorder.played.size,
            )
        }
    }

    @Test
    fun `holds of one two and three seconds warn once per remaining second`() {
        for ((hold, expected) in listOf(1 to 1, 2 to 2, 3 to 3, 4 to 3, 35 to 3)) {
            val recorder = Recorder()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, hold, 0, 0)), recorder, { time })
            m.toggle()
            run(m, hold * 2.0 + 1.0)
            assertEquals(
                "hold=$hold",
                expected * 2, // two holds per cycle
                recorder.played.count { it is Cue.Warn },
            )
        }
    }

    @Test
    fun `double tapping start pauses rather than re-announcing`() {
        val m = model()
        m.toggle()
        m.toggle()
        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
        assertTrue(m.snapshot.paused)
    }
}
