package dev.jebaum.isometric

import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.timer.PhaseId
import dev.jebaum.isometric.timer.PhaseKind
import dev.jebaum.isometric.timer.RoutineStatus
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.WARNING_SECONDS
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial cover for [RoutineViewModel]: cue dispatch under pause, skip,
 * completion and cue-toggle histories, plus the lifecycle and settings
 * contracts. Everything here drives the production class, not a copy of it.
 *
 * [CueDispatchTest] covers the ordinary paths; this file covers the edges.
 */
class RoutineViewModelTest {

    private val clock = FakeClock()
    private val player = RecordingCuePlayer()
    private val failures = RecordingFailureReporter()

    private fun model(
        settings: Settings = Settings(cycles = 2, holdSeconds = 5, switchSeconds = 2, restSeconds = 4),
        cuesEnabled: Boolean = true,
        history: CompletionHistoryStore = EmptyCompletionHistoryStore,
        wallNow: () -> Long = { 0L },
    ) = RoutineViewModel(
        store = FakeSettingsStore(settings, cuesEnabled),
        player = player,
        now = clock.now,
        history = history,
        wallNow = wallNow,
        failures = failures,
    )

    // ---- the cue sentinel cannot be re-broken -------------------------------

    @Test
    fun `enabling cues while paused mid-hold neither swallows nor duplicates`() {
        val m = model(Settings(cycles = 1, holdSeconds = 10, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 8.2) // ~1.8s left: Warn(3) and Warn(2) have fired
        m.toggle() // pause
        val warnsBefore = player.warnCount
        val secondsLeft = m.snapshot.secondsLeft

        m.setCues(false)
        m.setCues(true)
        m.toggle() // resume

        assertEquals(
            "resuming re-announced the phase or the current warning second",
            warnsBefore,
            player.warnCount,
        )
        assertEquals(1, player.enters.size)

        // The remaining warning seconds must still fire.
        clock.advanceBy(m, secondsLeft + 0.5)
        assertEquals(WARNING_SECONDS, player.warnCount)
    }

    @Test
    fun `enabling cues mid-hold outside the warning window still warns three times`() {
        val m = model(Settings(cycles = 1, holdSeconds = 30, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 5.0) // 25s left, far outside the window
        m.toggle() // pause
        m.setCues(false)
        m.setCues(true)
        m.toggle() // resume
        clock.advanceBy(m, 30.0)

        assertEquals(WARNING_SECONDS, player.warnCount)
    }

    @Test
    fun `a phase boundary crossed while cues are off is not announced retroactively`() {
        val m = model(Settings(cycles = 1, holdSeconds = 4, switchSeconds = 2, restSeconds = 0))
        m.toggle() // Enter(HOLD)
        m.setCues(false)
        clock.advanceBy(m, 4.5) // crosses into SWITCH silently
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
        clock.advanceBy(m, 3.0)
        assertEquals(listOf(PhaseKind.HOLD), player.enters)
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
            val recorder = RecordingCuePlayer()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, 3, 0, 0)), recorder, clock.now)
            m.toggle()
            clock.advanceBy(m, 1.0)
            m.reset()
            script.forEach(m::setCues)
            recorder.played.clear()
            m.toggle()
            if (script.last()) {
                assertEquals("script=$script", listOf<Cue>(Cue.Enter(PhaseKind.HOLD)), recorder.played)
            } else {
                assertTrue("script=$script", recorder.played.isEmpty())
            }
        }
    }

    // ---- completion --------------------------------------------------------

    @Test
    fun `done is announced exactly once no matter how many ticks follow`() {
        val m = model(Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 10.0)
        repeat(20) { clock.advanceBy(0.5); m.tick() }
        repeat(5) { m.skip() }

        assertEquals(1, player.doneCount)
    }

    @Test
    fun `skipping off the end announces and records done once and not again`() {
        val history = FakeCompletionHistory()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0),
            history = history,
            wallNow = { completedAt },
        )
        m.toggle()
        repeat(10) { clock.advanceBy(0.01); m.skip() }

        assertEquals(1, player.doneCount)
        assertEquals(2, player.enters.size)
        assertEquals(listOf(completedAt), history.entries)
    }

    /**
     * Regression: `toggle()` short-circuits on the live `routine.status()`, so a
     * tap landing after the routine finished but before the frame loop ticked
     * used to reset straight through the completion without announcing it.
     */
    @Test
    fun `tapping the button in the frame the routine completes still announces done`() {
        val m = model(Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 3.9) // still inside the routine (total 4s)
        assertEquals(RoutineStatus.RUNNING, m.snapshot.status)
        player.played.clear()

        clock.advanceBy(0.2) // the routine crosses the finish line with no tick in between
        m.toggle() // user taps "Pause"

        assertTrue(
            "expected a Done cue before the reset; got ${player.played}",
            player.played.any { it is Cue.Done },
        )
        assertEquals(
            "the tap should also have reset the routine",
            RoutineStatus.READY,
            m.snapshot.status,
        )
    }

    @Test
    fun `completion is recorded exactly once even while cues are disabled`() {
        val history = FakeCompletionHistory()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0),
            cuesEnabled = false,
            history = history,
            wallNow = { completedAt },
        )

        m.toggle()
        clock.advanceBy(m, 10.0)
        repeat(20) { clock.advanceBy(0.5); m.tick() }
        repeat(5) { m.skip() }

        assertEquals(listOf(completedAt), history.entries)
        assertEquals(completedAt, m.lastCompletionAt)
        assertEquals(1, m.historyVersion)
        assertTrue(player.played.isEmpty())
    }

    /**
     * The finish line is the worst place to throw: the routine is over, the
     * completion cue still has to sound, and the write that failed must not be
     * left half-applied in memory where a later read would report a completion
     * that never reached the database.
     */
    @Test
    fun `a history write that fails is reported once and records nothing`() {
        val history = FakeCompletionHistory(recordFails = true)
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0),
            history = history,
            wallNow = { 1_700_000_000_000L },
        )

        m.toggle()
        clock.advanceBy(m, 6.0)
        repeat(5) { m.skip() } // keep prodding a finished routine

        assertEquals(RoutineStatus.COMPLETE, m.snapshot.status)
        assertEquals("the completion was still announced", 1, player.doneCount)
        assertNull(m.lastCompletionAt)
        assertEquals(0, m.historyVersion)
        assertEquals(listOf("recording a completion in history"), failures.operations)
        assertEquals("history database is locked", failures.reports.single().second.message)
    }

    @Test
    fun `a preference save that fails still applies in memory and is reported`() {
        val m = RoutineViewModel(
            store = FailingSettingsStore(RoutinePreferences(Settings(2, 5, 2, 4))),
            player = player,
            now = clock.now,
            failures = failures,
        )

        m.updateWeight(12.5)

        assertEquals(12.5, m.weightLb, 0.0)
        assertEquals(listOf("saving preferences"), failures.operations)
        assertEquals("preferences file is gone", failures.reports.single().second.message)
    }

    @Test
    fun `a routine that completes cleanly reports nothing`() {
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0),
            history = FakeCompletionHistory(),
        )

        m.toggle()
        clock.advanceBy(m, 6.0)

        assertEquals(emptyList<String>(), failures.operations)
    }

    /**
     * The diagnostics seam is the newest thing in the failure path and the least
     * trustworthy: a reporter that throws would otherwise turn a swallowed
     * history write into a crash at the finish line — the exact outcome the
     * swallowing exists to prevent. Two failures are stacked deliberately, so
     * the routine has to survive the store and its observer both throwing.
     */
    @Test
    fun `a reporter that throws cannot take down the routine it observes`() {
        val exploding = FailureReporter { _, _ -> error("the reporter is the bug now") }
        val m = RoutineViewModel(
            store = FakeSettingsStore(
                Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0),
                cuesEnabled = true,
            ),
            player = player,
            now = clock.now,
            history = FakeCompletionHistory(recordFails = true),
            wallNow = { 1_700_000_000_000L },
            failures = exploding,
        )

        m.toggle()
        clock.advanceBy(m, 6.0)
        repeat(5) { m.skip() } // keep prodding a finished routine

        assertEquals(RoutineStatus.COMPLETE, m.snapshot.status)
        assertEquals("the completion was still announced", 1, player.doneCount)
        assertNull(m.lastCompletionAt)
    }

    @Test
    fun `ending an unfinished routine does not enter history`() {
        val history = FakeCompletionHistory()
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 10, switchSeconds = 0, restSeconds = 0),
            history = history,
        )

        m.toggle()
        clock.advanceBy(m, 2.0)
        m.reset()

        assertTrue(history.entries.isEmpty())
        assertNull(m.lastCompletionAt)
    }

    @Test
    fun `spacing warning ends at exactly eight hours`() {
        val completedAt = 1_700_000_000_000L
        val eligibleAt = completedAt + RoutineViewModel.MINIMUM_COMPLETION_GAP_MILLIS
        var wallTime = eligibleAt - 1L
        val m = model(history = FakeCompletionHistory(listOf(completedAt)), wallNow = { wallTime })

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
            history = FakeCompletionHistory(listOf(westCoastCompletion)),
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
        val history = FakeCompletionHistory()
        val completedAt = 1_700_000_000_000L
        val m = model(
            settings = Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0),
            history = history,
            wallNow = { completedAt },
        )

        m.updateWeight(12.5)
        m.toggle()
        clock.advanceBy(m, 6.0)

        assertEquals(listOf(WeightedCompletion(completedAt, 12.5)), history.weightHistory())
    }

    @Test
    fun `weight defaults to bodyweight zero and persists through the store`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4))
        val m = RoutineViewModel(store, player, clock.now)

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
        clock.advanceBy(m, 2.0)
        val before = m.snapshot

        m.updateWeight(20.0)

        assertEquals("the weight change touched the routine", before, m.snapshot)
        assertTrue(m.running)
    }

    @Test
    fun `updateWeight rejects values outside the accepted range without persisting`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4))
        val m = RoutineViewModel(store, player, clock.now)
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
        val m = RoutineViewModel(store, player, clock.now)

        m.toggle()
        clock.advanceBy(m, 3.0)
        m.reset()

        assertEquals("something persisted without a Save", 0, store.writes)
    }

    @Test
    fun `a save commits the settings and the cue toggle in one write`() {
        val store = FakeSettingsStore(Settings(2, 5, 2, 4), cuesEnabled = false)
        val m = RoutineViewModel(store, player, clock.now)
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
        val m = RoutineViewModel(store, player, clock.now)
        val bad = Settings(cycles = 0, holdSeconds = 35, switchSeconds = 5, restSeconds = 90)

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
            val recorder = RecordingCuePlayer()
            val m = RoutineViewModel(
                FakeSettingsStore(Settings(2, 5, 2, 4), cuesEnabled = cuesWereOn),
                recorder,
                clock.now,
            )
            m.toggle()
            clock.advanceBy(m, 3.0)
            recorder.played.clear()

            m.updatePreferences(Settings(1, 3, 0, 0), cuesEnabled = true)

            assertEquals("cuesWereOn=$cuesWereOn", RoutineStatus.READY, m.snapshot.status)
            assertTrue("the rebuild announced something: ${recorder.played}", recorder.played.isEmpty())

            m.toggle()
            assertEquals("cuesWereOn=$cuesWereOn", listOf<Cue>(Cue.Enter(PhaseKind.HOLD)), recorder.played)
        }
    }

    /**
     * The view model does not lean on the screen disabling settings while a
     * routine runs: a Save that leaves the schedule alone must not rebuild it,
     * and turning cues on must pick up where the routine already is.
     */
    @Test
    fun `a save that leaves the schedule alone does not disturb a running routine`() {
        val settings = Settings(cycles = 1, holdSeconds = 30, switchSeconds = 0, restSeconds = 0)
        val store = FakeSettingsStore(settings, cuesEnabled = false)
        val m = RoutineViewModel(store, player, clock.now)
        m.toggle()
        clock.advanceBy(m, 5.0) // 25s left, far outside the warning window
        val before = m.snapshot

        m.updatePreferences(settings, cuesEnabled = true)

        assertEquals("the save rebuilt a routine it did not change", before, m.snapshot)
        assertTrue(m.running)
        assertTrue("re-enabling cues replayed the current phase", player.played.isEmpty())
        assertEquals(RoutinePreferences(settings, cuesEnabled = true), store.saved)

        // The rest of the hold still announces normally.
        clock.advanceBy(m, 30.0)
        assertEquals(WARNING_SECONDS, player.warnCount)
    }

    /**
     * Regression: skipping the rebuild when the schedule is unchanged is the
     * right call for a running routine, but a *finished* one has to reset
     * anyway. Otherwise a Save made from the completion screen leaves it up,
     * with no way back to READY but a second tap on Done.
     */
    @Test
    fun `a save from the completion screen returns the routine to ready`() {
        val settings = Settings(cycles = 1, holdSeconds = 2, switchSeconds = 0, restSeconds = 0)
        val m = RoutineViewModel(FakeSettingsStore(settings), player, clock.now)
        m.toggle()
        clock.advanceBy(m, 6.0)
        assertEquals("the routine never finished", RoutineStatus.COMPLETE, m.snapshot.status)

        m.updatePreferences(settings, cuesEnabled = true)

        assertEquals(
            "the save left the completion screen up",
            RoutineStatus.READY,
            m.snapshot.status,
        )
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
        val m = RoutineViewModel(store, player, clock.now)
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
     * The readings are scripted rather than taken from [FakeClock] because the
     * point is *how many* times the clock is read. The fourth reading is
     * deliberately left unconsumed: if a second clock read is ever reintroduced
     * it will be taken, and progress will collapse to ~0 while the snapshot
     * still names the outgoing phase.
     */
    @Test
    fun `snapshot and progress are published from a single clock read`() {
        val readings = ArrayDeque(listOf(0.0, 0.0, 4.999_999, 5.000_001))
        var last = 0.0
        val m = RoutineViewModel(
            store = FakeSettingsStore(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0)),
            player = player,
            now = { (readings.removeFirstOrNull() ?: last).also { last = it } },
        )
        m.toggle()

        assertEquals(PhaseId.RIGHT_HOLD, m.snapshot.phase.id)
        assertEquals(1, m.snapshot.secondsLeft)
        assertEquals(RoutineStatus.RUNNING, m.snapshot.status)
        assertTrue(
            "progress ${m.progress} disagrees with the phase the snapshot names",
            m.progress > 0.99f,
        )
    }

    @Test
    fun `progress is frozen while paused and pinned at one when done`() {
        val m = model(Settings(cycles = 1, holdSeconds = 10, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 5.0)
        m.toggle() // pause
        val frozen = m.progress
        clock.advanceBy(60.0)
        m.tick()
        assertEquals("progress moved while paused", frozen, m.progress, 1e-6f)

        m.toggle() // resume
        clock.advanceBy(m, 30.0)
        assertEquals(RoutineStatus.COMPLETE, m.snapshot.status)
        assertEquals(1f, m.progress, 0f)
        assertEquals(
            0f,
            RoutineViewModel(
                FakeSettingsStore(Settings(1, 5, 0, 0)),
                RecordingCuePlayer(),
                clock.now,
            ).progress,
            0f,
        )
    }

    // ---- lifecycle and the frame loop --------------------------------------

    @Test
    fun `onCleared releases the player exactly once`() {
        val history = FakeCompletionHistory()
        val m = model(history = history)
        m.toggle()
        clock.advanceBy(m, 2.0)

        clearViewModel(m)

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
            val recorder = RecordingCuePlayer()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, 4, 0, 0)), recorder, clock.now)
            m.toggle()
            clock.advanceBy(m, 1.2) // 3s left: Warn(3) has fired
            stop(m)
            val before = recorder.played.size

            clock.advanceBy(FRAME_SECONDS)
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
        for ((holdSeconds, expected) in listOf(1 to 1, 2 to 2, 3 to 3, 4 to 3, 35 to 3)) {
            val recorder = RecordingCuePlayer()
            val m = RoutineViewModel(FakeSettingsStore(Settings(1, holdSeconds, 0, 0)), recorder, clock.now)
            m.toggle()
            clock.advanceBy(m, holdSeconds * 2.0 + 1.0)
            assertEquals(
                "holdSeconds=$holdSeconds",
                expected * 2, // two holds per cycle
                recorder.warnCount,
            )
        }
    }

    @Test
    fun `double tapping start pauses rather than re-announcing`() {
        val m = model()
        m.toggle()
        m.toggle()
        assertEquals(listOf<Cue>(Cue.Enter(PhaseKind.HOLD)), player.played)
        assertEquals(RoutineStatus.PAUSED, m.snapshot.status)
    }

    // ---- status ------------------------------------------------------------

    /**
     * Every transition the buttons can drive, asserted on the published status
     * one step at a time — and on [RoutineViewModel.running], because the frame
     * loop's condition is the reason a wrong status would show up as a frozen
     * or a runaway countdown rather than a wrong label.
     */
    @Test
    fun `status walks the routine lifecycle one transition at a time`() {
        val m = model(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0))
        assertEquals(RoutineStatus.READY, m.snapshot.status)
        assertFalse("the frame loop started before Start", m.running)
        assertFalse(m.active)

        m.toggle() // Start
        assertEquals(RoutineStatus.RUNNING, m.snapshot.status)
        assertTrue("the frame loop did not start", m.running)
        assertTrue(m.active)

        clock.advanceBy(m, 2.0)
        m.toggle() // Pause
        assertEquals(RoutineStatus.PAUSED, m.snapshot.status)
        assertFalse("the frame loop kept running while paused", m.running)
        assertTrue("a paused routine is still in progress", m.active)

        m.toggle() // Resume
        assertEquals(RoutineStatus.RUNNING, m.snapshot.status)
        assertTrue(m.running)

        clock.advanceBy(m, 20.0) // past the 10s schedule
        assertEquals(RoutineStatus.COMPLETE, m.snapshot.status)
        assertFalse("the frame loop kept running after the finish", m.running)
        assertFalse("a finished routine does not hold the screen awake", m.active)

        m.toggle() // "Again"
        assertEquals(RoutineStatus.READY, m.snapshot.status)
        assertFalse(m.running)
        assertFalse(m.active)
    }

    @Test
    fun `skip advances phases without leaving RUNNING, and ending returns to READY`() {
        val m = model(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 2, restSeconds = 0))
        m.toggle()

        m.skip()
        assertEquals(PhaseId.SWITCH, m.snapshot.phase.id)
        assertEquals(RoutineStatus.RUNNING, m.snapshot.status)

        m.reset()
        assertEquals(RoutineStatus.READY, m.snapshot.status)
        assertEquals(PhaseId.RIGHT_HOLD, m.snapshot.phase.id)
    }

    /** Skipping off the end while paused lands on COMPLETE, not PAUSED. */
    @Test
    fun `skipping off the end from a pause lands on COMPLETE`() {
        val m = model(Settings(cycles = 1, holdSeconds = 5, switchSeconds = 0, restSeconds = 0))
        m.toggle()
        clock.advanceBy(m, 1.0)
        m.toggle() // pause
        assertEquals(RoutineStatus.PAUSED, m.snapshot.status)

        repeat(4) { m.skip() }

        assertEquals(RoutineStatus.COMPLETE, m.snapshot.status)
        assertFalse(m.running)
    }
}
