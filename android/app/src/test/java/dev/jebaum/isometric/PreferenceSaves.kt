package dev.jebaum.isometric

import dev.jebaum.isometric.timer.Settings

/** Stands in for SharedPreferences, holding the saved value in memory. */
internal class FakeSettingsStore(
    settings: Settings,
    cuesEnabled: Boolean = true,
    weightLb: Double = 0.0,
) : SettingsStore {
    /** What is on "disk": one value, written whole or not at all. */
    var saved = RoutinePreferences(settings, cuesEnabled, weightLb)
        private set

    /** Counts writes, so a Save that half-applies cannot pass unnoticed. */
    var writes = 0
        private set

    override fun load(): RoutinePreferences = saved

    override fun save(preferences: RoutinePreferences) {
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
