package com.metyatech.markdowntoqti

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource

class CliTest {
    @Test
    fun cli_writesQtiOutput() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val fixtureId = "choice-with-scoring"
        val markdown = readFixtureText("$fixtureId.md")
        val expectedXml = readFixtureText("$fixtureId.qti.xml")
        val inputFile = tempDir.resolve("$fixtureId.md")
        inputFile.writeText(markdown)

        val exitCode = runCli(
            arrayOf(
                "--input",
                inputFile.toString(),
                "--output-dir",
                outputDir.toString(),
            ),
        )

        assertEquals(0, exitCode)
        val outputFile = outputDir.resolve("$fixtureId.qti.xml")
        assertTrue(Files.exists(outputFile))
        val actualXml = outputFile.readText()
        assertEquals(normalizeXml(expectedXml), normalizeXml(actualXml))
    }

    @Test
    fun cli_validateOnly_doesNotWriteOutput() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val fixtureId = "descriptive-with-scoring"
        val markdown = readFixtureText("$fixtureId.md")
        val inputFile = tempDir.resolve("$fixtureId.md")
        inputFile.writeText(markdown)

        val exitCode = runCli(
            arrayOf(
                "--input",
                inputFile.toString(),
                "--output-dir",
                outputDir.toString(),
                "--validate-only",
            ),
        )

        assertEquals(0, exitCode)
        val outputFile = outputDir.resolve("$fixtureId.qti.xml")
        assertFalse(Files.exists(outputFile))
    }

    @Test
    fun cli_requiresOutputDirWhenNotValidateOnly() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val fixtureId = "cloze-with-scoring"
        val markdown = readFixtureText("$fixtureId.md")
        val inputFile = tempDir.resolve("$fixtureId.md")
        inputFile.writeText(markdown)

        val stderr = ByteArrayOutputStream()
        val exitCode = runCli(
            arrayOf(
                "--input",
                inputFile.toString(),
            ),
            error = PrintStream(stderr),
        )

        assertEquals(1, exitCode)
        assertTrue(stderr.toString().contains("output"))
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
