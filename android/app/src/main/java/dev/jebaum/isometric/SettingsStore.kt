package dev.jebaum.isometric

import android.content.Context
import androidx.core.content.edit
import dev.jebaum.isometric.timer.DEFAULT_SETTINGS
import dev.jebaum.isometric.timer.Settings
import dev.jebaum.isometric.timer.isValid

/**
 * Everything the app remembers between launches, as one value.
 *
 * The settings dialog stages its fields and commits them together, so the thing
 * being saved really is a single value — splitting it into separate settings,
 * cue and weight APIs let a half-written Save reach disk.
 */
data class RoutinePreferences(
    val settings: Settings = DEFAULT_SETTINGS,
    val cuesEnabled: Boolean = true,
    /** Hold weight in pounds; 0 means bodyweight only. */
    val weightLb: Double = 0.0,
)

/** Kept as an interface so the routine can be unit-tested against a fake. */
interface SettingsStore {
    fun load(): RoutinePreferences
    fun save(preferences: RoutinePreferences)
}

/**
 * The on-disk contract, in one place. Every installed device already holds its
 * settings under these exact strings, so renaming one silently strands them —
 * the app would come back up on defaults. `SettingsKeysTest` pins each literal
 * so a rename fails the build instead of the upgrade.
 */
internal object SettingsKeys {
    const val FILE = "isometric-settings-v1"
    const val CYCLES = "cycles"
    const val HOLD = "hold"
    const val SWITCH = "switch"
    const val REST = "rest"
    const val CUES = "cues"
    const val WEIGHT_LB_HUNDREDTHS = "weightLbHundredths"
}

/**
 * Deliberately synchronous. DataStore would push the first read off the main
 * thread, but for six values that only buys a frame of the wrong routine on
 * launch. The names it reads and writes live in [SettingsKeys].
 */
class PreferencesSettingsStore(context: Context) : SettingsStore {
    // Held as the application context because this store now outlives the
    // constructor call: the lazy handle below keeps it for the store's
    // lifetime, and retaining an Activity that long would leak it.
    private val context: Context = context.applicationContext

    // Opened lazily so the first touch happens inside [load] or [save], both of
    // which their caller guards. Constructing this store runs in the Activity's
    // view-model factory, where a throw is a launch that never completes — and
    // opening the file is the half most likely to throw.
    private val file by lazy {
        context.getSharedPreferences(SettingsKeys.FILE, Context.MODE_PRIVATE)
    }

    override fun load(): RoutinePreferences {
        val candidate = Settings(
            cycles = file.getInt(SettingsKeys.CYCLES, DEFAULT_SETTINGS.cycles),
            holdSeconds = file.getInt(SettingsKeys.HOLD, DEFAULT_SETTINGS.holdSeconds),
            switchSeconds = file.getInt(SettingsKeys.SWITCH, DEFAULT_SETTINGS.switchSeconds),
            restSeconds = file.getInt(SettingsKeys.REST, DEFAULT_SETTINGS.restSeconds),
        )
        val defaults = RoutinePreferences()
        return RoutinePreferences(
            // Corrupted preferences fall back rather than crashing on launch.
            settings = if (candidate.isValid()) candidate else defaults.settings,
            cuesEnabled = file.getBoolean(SettingsKeys.CUES, defaults.cuesEnabled),
            // Stored as hundredths of a pound: SharedPreferences has no double,
            // and a float round-trip would smear 12.1 into 12.100000381.
            // Corrupted values fall back like the settings do, so an
            // out-of-range weight can never reach state the weight dialog
            // cannot re-edit.
            weightLb = (file.getInt(SettingsKeys.WEIGHT_LB_HUNDREDTHS, 0) / 100.0)
                .takeIf { isValidWeightLb(it) } ?: defaults.weightLb,
        )
    }

    /** One `edit` block, so a Save reaches the file as one commit, not three. */
    override fun save(preferences: RoutinePreferences) {
        file.edit {
            putInt(SettingsKeys.CYCLES, preferences.settings.cycles)
            putInt(SettingsKeys.HOLD, preferences.settings.holdSeconds)
            putInt(SettingsKeys.SWITCH, preferences.settings.switchSeconds)
            putInt(SettingsKeys.REST, preferences.settings.restSeconds)
            putBoolean(SettingsKeys.CUES, preferences.cuesEnabled)
            putInt(SettingsKeys.WEIGHT_LB_HUNDREDTHS, weightLbHundredths(preferences.weightLb))
        }
    }
}
