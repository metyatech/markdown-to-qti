package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QtiXmlTest {
    @Test
    fun escapeXml_escapesReservedCharacters() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", escapeXml("&<>\"'"))
    }
}
