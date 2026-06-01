package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class MarkdownToQtiGoldenTest {
    @Test
    fun convertMarkdownFixturesToQti() {
        val fixtures =
            listOf(
                "descriptive-with-scoring",
                "descriptive-with-explanation",
                "descriptive-with-image",
                "descriptive-with-markdown",
                "choice-with-scoring",
                "cloze-with-code",
                "cloze-with-scoring",
            )

        fixtures.forEach { fixtureId ->
            val markdown = readFixtureText("$fixtureId.md")
            val expectedXml = readFixtureText("$fixtureId.qti.xml")

            val actualXml = convertMarkdownToQti(markdown, fixtureId)

            assertEquals(
                normalizeXml(expectedXml),
                normalizeXml(actualXml),
                "Fixture mismatch: $fixtureId",
            )
        }
    }

    @Test
    fun convertMarkdownToQti_preservesRegexClozeBlankMetadata() {
        val markdown =
            """
            ---
            question_type: cloze
            time_budget_seconds: 60
            ---
            # Regex Blank

            ## Prompt
            Enter a three-digit code: {{/[0-9]{3}/}}.
            """.trimIndent()

        val actualXml = convertMarkdownToQti(markdown, "regex-cloze")

        assertTrue(actualXml.contains("interpretation=\"regex\""))
        assertTrue(actualXml.contains("<qti-value>[0-9]{3}</qti-value>"))
    }

    @Test
    fun convertMarkdownToQti_emitsDecimalMaxScoreWithoutTrailingZeros() {
        val markdown =
            """
            ---
            question_type: descriptive
            time_budget_seconds: 60
            ---
            # Decimal Scoring

            ## Prompt
            Explain how chlorophyll supports photosynthesis.

            ## Scoring
            - 2: Identifies chlorophyll as a light-absorbing pigment
            - 1.50: Mentions conversion of light energy to chemical energy
            """.trimIndent()

        val actualXml = convertMarkdownToQti(markdown, "decimal-scoring")
        val scoreDeclaration =
            """
            <qti-outcome-declaration identifier="SCORE" cardinality="single" base-type="float"/>
            """.trimIndent()

        assertTrue(actualXml.contains(scoreDeclaration))
        assertTrue(actualXml.contains("<qti-value>3.5</qti-value>"))
        assertTrue(!actualXml.contains("<qti-value>3.50</qti-value>"))
    }
}

private fun readFixtureText(name: String): String {
    val resourcePath = "fixtures/$name"
    val inputStream =
        Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
            ?: error("Fixture not found: $resourcePath")
    return inputStream.bufferedReader().use { it.readText() }
}

private fun normalizeXml(xml: String): String {
    val factory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    document.normalizeDocument()

    val transformer =
        TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
            setOutputProperty(OutputKeys.INDENT, "no")
        }

    val writer = StringWriter()
    transformer.transform(DOMSource(document), StreamResult(writer))

    return writer
        .toString()
        .replace(Regex(">\\s+<"), "><")
        .replace("\r\n", "\n")
        .trim()
}
