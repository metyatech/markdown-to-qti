package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class ScoringSectionTest {
    @Test
    fun parseScoringSection_acceptsIntegerAndDecimalPoints() {
        val lines =
            listOf(
                "- 2: Identifies chlorophyll as a light-absorbing pigment",
                "- 1.5: Mentions conversion of light energy to chemical energy",
            )

        val section = SectionContent("Scoring", lines, headingLine = 1, startLine = 2)
        val criteria = parseScoringSection(section, MarkdownQtiRenderer(), null)

        assertEquals(
            listOf(
                ScoringCriterion(BigDecimal("2"), "Identifies chlorophyll as a light-absorbing pigment"),
                ScoringCriterion(BigDecimal("1.5"), "Mentions conversion of light energy to chemical energy"),
            ),
            criteria,
        )
    }

    @Test
    fun parseScoringSection_rejectsNonNumericPoints() {
        val lines = listOf("- two: Gives a correct explanation")

        val section = SectionContent("Scoring", lines, headingLine = 1, startLine = 2)
        val exception =
            assertThrows<IllegalArgumentException> {
                parseScoringSection(section, MarkdownQtiRenderer(), null)
            }

        assertTrue(exception.message?.contains("points") == true)
    }

    @Test
    fun parseScoringSection_rejectsEmptyCriterion() {
        val lines = listOf("- 2:")

        val section = SectionContent("Scoring", lines, headingLine = 1, startLine = 2)
        val exception =
            assertThrows<IllegalArgumentException> {
                parseScoringSection(section, MarkdownQtiRenderer(), null)
            }

        assertTrue(exception.message?.contains("criterion") == true)
    }
}
