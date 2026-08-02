package dev.jebaum.isometric

import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.cues.CuePlayer
import dev.jebaum.isometric.timer.Kind
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.WARNING_SECONDS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class Store(
    private var settings: Settings,
    override var cuesEnabled: Boolean = true,
) : SettingsStore {
    override fun load(): Settings = settings
    override fun save(settings: Settings) {
        this.settings = settings
    }
}

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
    ) = RoutineViewModel(
        store = Store(settings, cuesEnabled),
        player = player,
        now = { time },
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

        m.updateCuesEnabled(false)
        m.updateCuesEnabled(true)
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
        m.updateCuesEnabled(false)
        m.updateCuesEnabled(true)
        m.toggle() // resume
        run(m, 30.0)

        assertEquals(WARNING_SECONDS, player.played.count { it is Cue.Warn })
    }

    @Test
    fun `a phase boundary crossed while cues are off is not announced retroactively`() {
        val m = model(Settings(cycles = 1, hold = 4, switch = 2, rest = 0))
        m.toggle() // Enter(HOLD)
        m.updateCuesEnabled(false)
        run(m, 4.5) // crosses into SWITCH silently
        player.played.clear()
        m.toggle() // pause
        m.updateCuesEnabled(true)
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
            val m = RoutineViewModel(Store(Settings(1, 3, 0, 0)), recorder, { time })
            m.toggle()
            run(m, 1.0)
            m.reset()
            script.forEach(m::updateCuesEnabled)
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
    fun `skipping off the end announces done once and not again`() {
        val m = model(Settings(cycles = 1, hold = 5, switch = 0, rest = 0))
        m.toggle()
        repeat(10) { time += 0.01; m.skip() }

        assertEquals(1, player.played.count { it is Cue.Done })
        assertEquals(2, player.played.count { it is Cue.Enter })
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

    // ---- settings contract -------------------------------------------------

    /**
     * Regression: `updateSettings` used to persist before building, so an
     * invalid value reached SharedPreferences and *then* threw out of the click
     * handler, leaving in-memory state the view model could not build from.
     */
    @Test
    fun `updateSettings rejects a routine it cannot build without persisting it`() {
        val original = Settings(2, 5, 2, 4)
        val store = Store(original)
        val m = RoutineViewModel(store, player, { time })
        val bad = Settings(cycles = 0, hold = 35, switch = 5, rest = 90)

        val thrown = runCatching { m.updateSettings(bad) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException, got $thrown", thrown is IllegalArgumentException)
        assertEquals("the invalid value reached storage", original, store.load())
        assertEquals("in-memory settings were mutated", original, m.settings)
    }

    @Test
    fun `the screens save order leaves the cue state clean either way round`() {
        for (reversed in listOf(false, true)) {
            val recorder = Recorder()
            val m = RoutineViewModel(
                Store(Settings(2, 5, 2, 4), cuesEnabled = false),
                recorder,
                { time },
            )
            val next = Settings(1, 3, 0, 0)
            if (reversed) {
                m.updateSettings(next)
                m.updateCuesEnabled(true)
            } else {
                m.updateCuesEnabled(true)
                m.updateSettings(next)
            }
            m.toggle()
            assertEquals("reversed=$reversed", listOf<Cue>(Cue.Enter(Kind.HOLD)), recorder.played)
        }
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
            store = Store(Settings(cycles = 1, hold = 5, switch = 0, rest = 0)),
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
            RoutineViewModel(Store(Settings(1, 5, 0, 0)), Recorder(), { time }).progress,
            0f,
        )
    }

    // ---- lifecycle and the frame loop --------------------------------------

    @Test
    fun `onCleared releases the player exactly once`() {
        val m = model()
        m.toggle()
        run(m, 2.0)
        m.javaClass.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(m)
        assertEquals(1, player.releases)
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
            val m = RoutineViewModel(Store(Settings(1, 4, 0, 0)), recorder, { time })
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
            val m = RoutineViewModel(Store(Settings(1, hold, 0, 0)), recorder, { time })
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
