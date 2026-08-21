package dev.jebaum.isometric

import android.content.Context
import androidx.core.content.edit
import dev.jebaum.isometric.timer.DEFAULT_SETTINGS
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.isValid

/** Kept as an interface so the routine can be unit-tested against a fake. */
interface SettingsStore {
    fun load(): Settings
    fun save(settings: Settings)
    var cuesEnabled: Boolean

    /** Hold weight in pounds; 0 means bodyweight only. */
    var weightLb: Double
}

/**
 * Deliberately synchronous. DataStore would push the first read off the main
 * thread, but for five values that only buys a frame of the wrong routine on
 * launch; the file name matches the web app's `STORAGE_KEY` for continuity.
 */
class PreferencesSettingsStore(context: Context) : SettingsStore {
    private val preferences =
        context.getSharedPreferences("isometric-settings-v1", Context.MODE_PRIVATE)

    override fun load(): Settings {
        val candidate = Settings(
            cycles = preferences.getInt(KEY_CYCLES, DEFAULT_SETTINGS.cycles),
            hold = preferences.getInt(KEY_HOLD, DEFAULT_SETTINGS.hold),
            switch = preferences.getInt(KEY_SWITCH, DEFAULT_SETTINGS.switch),
            rest = preferences.getInt(KEY_REST, DEFAULT_SETTINGS.rest),
        )
        // Corrupted preferences fall back rather than crashing on launch.
        return if (candidate.isValid()) candidate else DEFAULT_SETTINGS
    }

    override fun save(settings: Settings) {
        preferences.edit {
            putInt(KEY_CYCLES, settings.cycles)
            putInt(KEY_HOLD, settings.hold)
            putInt(KEY_SWITCH, settings.switch)
            putInt(KEY_REST, settings.rest)
        }
    }

    override var cuesEnabled: Boolean
        get() = preferences.getBoolean(KEY_CUES, true)
        set(value) = preferences.edit { putBoolean(KEY_CUES, value) }

    override var weightLb: Double
        // Stored as hundredths of a pound: SharedPreferences has no double, and
        // a float round-trip would smear 12.1 into 12.100000381. Corrupted
        // values fall back like load() does, so an out-of-range weight can
        // never reach state the weight dialog cannot re-edit.
        get() = (preferences.getInt(KEY_WEIGHT_LB_HUNDREDTHS, 0) / 100.0)
            .takeIf { isValidWeightLb(it) } ?: 0.0
        set(value) = preferences.edit {
            putInt(KEY_WEIGHT_LB_HUNDREDTHS, weightLbHundredths(value))
        }

    private companion object {
        const val KEY_CYCLES = "cycles"
        const val KEY_HOLD = "hold"
        const val KEY_SWITCH = "switch"
        const val KEY_REST = "rest"
        const val KEY_CUES = "cues"
        const val KEY_WEIGHT_LB_HUNDREDTHS = "weightLbHundredths"
    }
}
