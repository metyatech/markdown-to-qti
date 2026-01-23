package com.metyatech.markdowntoqti

import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource

class MarkdownToQtiGoldenTest {
    @Test
    fun convertMarkdownFixturesToQti() {
        val fixtures = listOf(
            "descriptive-with-scoring",
            "descriptive-with-explanation",
            "descriptive-with-image",
            "choice-with-scoring",
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
}

private fun readFixtureText(name: String): String {
    val resourcePath = "fixtures/$name"
    val inputStream = Thread.currentThread().contextClassLoader.getResourceAsStream(resourcePath)
        ?: error("Fixture not found: $resourcePath")
    return inputStream.bufferedReader().use { it.readText() }
}

private fun normalizeXml(xml: String): String {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    document.normalizeDocument()

    val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        setOutputProperty(OutputKeys.INDENT, "no")
    }

    val writer = StringWriter()
    transformer.transform(DOMSource(document), StreamResult(writer))

    return writer.toString()
        .replace(Regex(">\\s+<"), "><")
        .replace("\r\n", "\n")
        .trim()
}
