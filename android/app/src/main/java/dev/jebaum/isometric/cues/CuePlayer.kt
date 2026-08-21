package dev.jebaum.isometric.cues

import dev.jebaum.isometric.timer.PhaseKind

/**
 * During a hold you are usually not looking at the screen, so every phase
 * boundary gets a sound and a vibration.
 */
sealed interface Cue {
    /** A new phase began. */
    data class Enter(val kind: PhaseKind) : Cue

    /** One of the final seconds of a hold ticked by. */
    data object Warn : Cue

    /** The routine finished. */
    data object Done : Cue
}

/**
 * Kept as an interface so the routine's cue dispatch can be unit-tested against
 * a recording fake, with no audio hardware involved.
 */
interface CuePlayer {
    fun play(cue: Cue)
    fun release()
}
