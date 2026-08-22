package dev.jebaum.isometric

import dev.jebaum.isometric.timer.Settings

/**
 * Stands in for SharedPreferences, holding the saved value in memory.
 *
 * The two failure flags stand in for a disk that is gone or a preferences file
 * that cannot be parsed. They live here rather than in separate failing stores
 * so a test reads as "this store, but the read fails" — and so a store that
 * refuses both halves is one object, not a pair that could disagree.
 */
internal class FakeSettingsStore(
    settings: Settings,
    cuesEnabled: Boolean = true,
    weightLb: Double = 0.0,
    /** Stands in for a corrupt or unreadable preferences file. */
    private val loadFails: Boolean = false,
    /** Stands in for a disk that refuses every write. */
    private val saveFails: Boolean = false,
) : SettingsStore {
    /** What is on "disk": one value, written whole or not at all. */
    var saved = RoutinePreferences(settings, cuesEnabled, weightLb)
        private set

    /** Counts writes, so a Save that half-applies cannot pass unnoticed. */
    var writes = 0
        private set

    override fun load(): RoutinePreferences {
        if (loadFails) error("preferences file is unreadable")
        return saved
    }

    override fun save(preferences: RoutinePreferences) {
        if (saveFails) error("preferences file is gone")
        saved = preferences
        writes++
    }
}

/**
 * The settings dialog commits every field together, so the tests reach the view
 * model the same way: changing one field is a Save that carries the others
 * through unchanged.
 */
internal fun RoutineViewModel.setCues(enabled: Boolean) = updatePreferences(settings, enabled)

internal fun RoutineViewModel.setSettings(value: Settings) =
    updatePreferences(value, cuesEnabled)
