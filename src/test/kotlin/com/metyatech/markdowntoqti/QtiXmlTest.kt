package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class QtiXmlTest {
    @Test
    fun formatSecondsAsIsoDuration_formatsPositiveSecondsAsCanonicalDuration() {
        assertEquals("PT1S", formatSecondsAsIsoDuration(1))
        assertEquals("PT90S", formatSecondsAsIsoDuration(90))
        assertEquals("PT300S", formatSecondsAsIsoDuration(300))
    }

    @Test
    fun formatSecondsAsIsoDuration_rejectsNonPositiveSeconds() {
        assertThrows(IllegalArgumentException::class.java) {
            formatSecondsAsIsoDuration(0)
        }
    }
}
