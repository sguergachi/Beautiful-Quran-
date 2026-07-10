package com.beautifulquran.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Documents the Settings field the Root Viewer / Timings Lab gate depends on.
 * Persistence itself needs Android SharedPreferences; this keeps the default
 * and copy semantics covered on the JVM.
 */
class DeveloperModeSettingsTest {

    @Test
    fun `developer mode defaults off`() {
        assertFalse(Settings().developerModeEnabled)
    }

    @Test
    fun `developer mode toggles via copy`() {
        val on = Settings().copy(developerModeEnabled = true)
        assertTrue(on.developerModeEnabled)
        assertFalse(on.copy(developerModeEnabled = false).developerModeEnabled)
    }
}
