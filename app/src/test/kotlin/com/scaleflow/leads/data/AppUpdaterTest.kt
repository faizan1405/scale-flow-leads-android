package com.scaleflow.leads.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun comparesSemanticVersionsCorrectly() {
        assertTrue(AppUpdater.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(AppUpdater.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(AppUpdater.isNewerVersion("2.0.0", "1.9.9"))
        assertFalse(AppUpdater.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isNewerVersion("0.9.9", "1.0.0"))
    }
}
