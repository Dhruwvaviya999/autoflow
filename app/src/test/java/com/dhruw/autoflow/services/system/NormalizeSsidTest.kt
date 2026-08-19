package com.dhruw.autoflow.services.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizeSsidTest {

    @Test
    fun `quoted ssid loses its quotes`() {
        assertEquals("Home", NetworkMonitor.normalizeSsid("\"Home\""))
    }

    @Test
    fun `plain ssid passes through`() {
        assertEquals("Home", NetworkMonitor.normalizeSsid("Home"))
    }

    @Test
    fun `unknown or blank ssid becomes null`() {
        assertNull(NetworkMonitor.normalizeSsid("<unknown ssid>"))
        assertNull(NetworkMonitor.normalizeSsid(""))
        assertNull(NetworkMonitor.normalizeSsid(null))
        assertNull(NetworkMonitor.normalizeSsid("\"\""))
    }
}
