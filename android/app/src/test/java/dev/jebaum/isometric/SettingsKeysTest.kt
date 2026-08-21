package dev.jebaum.isometric

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The preferences file name and its keys are a contract with every device the
 * app is already installed on: rename one and those settings are unreachable,
 * so the next launch quietly comes back on defaults. Nothing about that failure
 * is visible from the code, which is why the literals are pinned here — a
 * rename has to break the build to be noticed.
 *
 * Changing a value in this test is never the fix. Migrating the data is.
 */
class SettingsKeysTest {

    @Test
    fun `the preferences file name is the one already on device`() {
        assertEquals("isometric-settings-v1", SettingsKeys.FILE)
    }

    @Test
    fun `every stored key keeps the name it was written under`() {
        assertEquals("cycles", SettingsKeys.CYCLES)
        assertEquals("hold", SettingsKeys.HOLD)
        assertEquals("switch", SettingsKeys.SWITCH)
        assertEquals("rest", SettingsKeys.REST)
        assertEquals("cues", SettingsKeys.CUES)
        assertEquals("weightLbHundredths", SettingsKeys.WEIGHT_LB_HUNDREDTHS)
    }
}
