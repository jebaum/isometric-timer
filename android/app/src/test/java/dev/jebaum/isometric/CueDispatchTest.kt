package dev.jebaum.isometric

import dev.jebaum.isometric.cues.Cue
import dev.jebaum.isometric.cues.CuePlayer
import dev.jebaum.isometric.timer.Kind
import dev.jebaum.isometric.timer.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingCuePlayer(private val onPlay: (Cue) -> Unit = {}) : CuePlayer {
    val played = mutableListOf<Cue>()
    override fun play(cue: Cue) {
        played += cue
        onPlay(cue)
    }

    override fun release() = Unit
}

/**
 * Exercises [RoutineViewModel]'s cue dispatch — the one piece of stateful logic
 * that a device would otherwise be needed to check. Every dependency, including
 * the clock, arrives through the constructor, so this drives the production
 * class rather than a copy of it.
 */
class CueDispatchTest {

    private var time = 1_000.0
    private val player = RecordingCuePlayer()

    private fun viewModel(
        settings: Settings = Settings(cycles = 2, hold = 5, switch = 2, rest = 4),
        cuesEnabled: Boolean = true,
        player: CuePlayer = this.player,
    ) = RoutineViewModel(
        store = FakeSettingsStore(settings, cuesEnabled),
        player = player,
        now = { time },
    )

    /** Advances the clock in 50 ms steps, ticking the way the frame loop does. */
    private fun run(model: RoutineViewModel, seconds: Double) {
        var left = seconds
        while (left > 0) {
            time += 0.05
            left -= 0.05
            if (model.running) model.tick()
        }
    }

    private fun enters() = player.played.filterIsInstance<Cue.Enter>().map { it.kind }

    @Test
    fun `the first Start cues the opening hold`() {
        viewModel().toggle()

        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
    }

    /**
     * Regression: the cue resync used to overwrite the "nothing cued yet"
     * sentinel with index 0 — the index the opening phase is about to have — so
     * the routine began in silence.
     */
    @Test
    fun `enabling cues before Start still cues the opening hold`() {
        val model = viewModel(cuesEnabled = false)

        model.setCues(true)
        model.toggle()

        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
    }

    @Test
    fun `flipping cues off then on before Start still cues the opening hold`() {
        val model = viewModel()

        model.setCues(false)
        model.setCues(true)
        model.toggle()

        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
    }

    @Test
    fun `a full routine announces every boundary exactly once`() {
        val model = viewModel()
        model.toggle()
        run(model, 30.0)

        assertEquals(
            listOf(Kind.HOLD, Kind.SWITCH, Kind.HOLD, Kind.REST, Kind.HOLD, Kind.SWITCH, Kind.HOLD),
            enters(),
        )
        assertEquals(1, player.played.count { it is Cue.Done })
    }

    @Test
    fun `the closing seconds of a hold warn once each`() {
        val model = viewModel(Settings(cycles = 1, hold = 10, switch = 0, rest = 0))
        model.toggle()
        run(model, 9.5)

        assertEquals(3, player.played.count { it is Cue.Warn })
    }

    @Test
    fun `switch and rest phases never warn`() {
        val model = viewModel(Settings(cycles = 2, hold = 4, switch = 3, rest = 3))
        model.toggle()
        run(model, 26.0) // the routine itself is 25s; run past the end

        // Four holds of 4s, each warning at 3, 2 and 1 second remaining, and
        // nothing at all from the two switches or the rest.
        assertEquals(12, player.played.count { it is Cue.Warn })
        assertTrue(model.snapshot.done)
    }

    @Test
    fun `pausing and resuming inside the warning window does not double-warn`() {
        val model = viewModel(Settings(cycles = 1, hold = 10, switch = 0, rest = 0))
        model.toggle()
        run(model, 8.0)

        val before = player.played.count { it is Cue.Warn }
        repeat(6) {
            model.toggle()
            time += 5.0
            model.toggle()
        }
        assertEquals(before, player.played.count { it is Cue.Warn })
    }

    @Test
    fun `a long background gap crossing several phases announces only the phase landed on`() {
        val model = viewModel(Settings(cycles = 4, hold = 35, switch = 5, rest = 90))
        model.toggle()
        player.played.clear()

        time += 300.0 // away for five minutes
        model.tick()

        assertEquals(1, player.played.size)
        assertTrue(player.played.single() is Cue.Enter)
    }

    @Test
    fun `rapid skipping announces each phase once and finishes with one done`() {
        val model = viewModel(Settings(cycles = 4, hold = 35, switch = 5, rest = 90))
        model.toggle()
        repeat(40) {
            time += 0.001
            model.skip()
        }
        assertEquals(15, player.played.count { it is Cue.Enter })
        assertEquals(1, player.played.count { it is Cue.Done })
    }

    @Test
    fun `restarting after completion cues the opening hold again`() {
        val model = viewModel(Settings(cycles = 1, hold = 2, switch = 0, rest = 0))
        model.toggle()
        run(model, 6.0)
        player.played.clear()

        model.toggle() // "Again" resets
        model.toggle() // Start
        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
    }

    @Test
    fun `nothing is announced while cues are disabled`() {
        val model = viewModel(cuesEnabled = false)
        model.toggle()
        run(model, 30.0)

        assertTrue(player.played.isEmpty())
    }

    @Test
    fun `saving settings resets the routine so the next Start cues again`() {
        val model = viewModel()
        model.toggle()
        run(model, 6.0)
        player.played.clear()

        model.setSettings(Settings(cycles = 1, hold = 3, switch = 0, rest = 0))
        assertTrue(player.played.isEmpty())
        assertTrue(!model.snapshot.started)

        model.toggle()
        assertEquals(listOf<Cue>(Cue.Enter(Kind.HOLD)), player.played)
    }

    /**
     * A misbehaving vibrator HAL or a released tone generator must not escape
     * into the frame loop, which would stop the countdown dead.
     */
    @Test
    fun `a cue player that throws does not break the routine`() {
        val exploding = RecordingCuePlayer { error("vendor HAL said no") }
        val model = viewModel(player = exploding)

        model.toggle()
        run(model, 30.0)

        assertTrue("the routine still finished", model.snapshot.done)
        assertTrue("cues were still attempted", exploding.played.isNotEmpty())
    }

    @Test
    fun `progress tracks the current phase without appearing in the snapshot`() {
        val model = viewModel(Settings(cycles = 1, hold = 10, switch = 0, rest = 0))
        model.toggle()
        val before = model.snapshot

        time += 0.5
        model.tick()

        // Half a second into a ten second hold: progress moved, but nothing the
        // snapshot carries has changed yet, so readers of it need not recompose.
        assertEquals(0.05f, model.progress, 1e-4f)
        assertEquals(before, model.snapshot)
    }
}
