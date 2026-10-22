package com.lumvane.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunSpecTest {
    @Test
    fun splitCidr_parses() {
        assertEquals("172.19.0.1" to 30, splitCidr("172.19.0.1/30"))
        assertEquals("fdfe:dcba:9876::1" to 126, splitCidr("fdfe:dcba:9876::1/126"))
    }

    @Test
    fun splitCidr_rejectsMalformed() {
        assertNull(splitCidr("10.0.0.1"))
        assertNull(splitCidr("10.0.0.1/x"))
    }

    @Test
    fun formatSpeed_units() {
        assertEquals("0 B/s", formatSpeed(0))
        assertEquals("1.5 KB/s", formatSpeed(1536))
        assertEquals("2.0 MB/s", formatSpeed(2L * 1024 * 1024))
    }
}
