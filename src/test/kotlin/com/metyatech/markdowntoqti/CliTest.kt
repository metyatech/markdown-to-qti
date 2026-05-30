package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    inputFile.toString(),
                    "--test-title",
                    "Assessment Test",
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

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    inputFile.toString(),
                    "--test-title",
                    "Assessment Test",
                    "--output-dir",
                    outputDir.toString(),
                    "--validate-only",
                ),
            )

        assertEquals(0, exitCode)
        val outputFile = outputDir.resolve("$fixtureId.qti.xml")
        assertFalse(Files.exists(outputFile))
        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertFalse(Files.exists(assessmentTest))
    }

    @Test
    fun cli_defaultsOutputDirWhenNotProvided() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val fixtureId = "cloze-with-scoring"
        val markdown = readFixtureText("$fixtureId.md")
        val expectedXml = readFixtureText("$fixtureId.qti.xml")
        val inputFile = tempDir.resolve("$fixtureId.md")
        inputFile.writeText(markdown)

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    inputFile.toString(),
                    "--test-title",
                    "Assessment Test",
                ),
            )

        assertEquals(0, exitCode)
        val outputDir = tempDir.resolve("qti-out")
        val outputFile = outputDir.resolve("$fixtureId.qti.xml")
        assertTrue(Files.exists(outputFile))
        val actualXml = outputFile.readText()
        assertEquals(normalizeXml(expectedXml), normalizeXml(actualXml))

        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(assessmentTest))
        val assessmentXml = assessmentTest.readText()
        assertTrue(assessmentXml.contains("qti-assessment-item-ref"))
        assertTrue(assessmentXml.contains("identifier=\"$fixtureId\""))
        assertTrue(assessmentXml.contains("href=\"$fixtureId.qti.xml\""))
    }

    @Test
    fun cli_copiesLocalImagesToOutputDir() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val imagesDir = tempDir.resolve("images").createDirectories()
        val imageFile = imagesDir.resolve("diagram.png")
        imageFile.writeText("fake image")

        val markdown =
            """
            ---
            question_type: descriptive
            time_budget_seconds: 60
            ---
            # Image Prompt

            ## Prompt
            Identify the highlighted part.

            ![Alt text](images/diagram.png "Diagram")
            """.trimIndent()
        val inputFile = tempDir.resolve("image-prompt.md")
        inputFile.writeText(markdown)

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    inputFile.toString(),
                    "--test-title",
                    "Assessment Test",
                    "--output-dir",
                    outputDir.toString(),
                ),
            )

        assertEquals(0, exitCode)
        val outputFile = outputDir.resolve("image-prompt.qti.xml")
        assertTrue(Files.exists(outputFile))
        val outputXml = outputFile.readText()
        assertTrue(outputXml.contains("<qti-img src=\"images/diagram.png\" alt=\"Alt text\" title=\"Diagram\"/>"))

        val copiedImage = outputDir.resolve("images").resolve("diagram.png")
        assertTrue(Files.exists(copiedImage))

        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(assessmentTest))
        val assessmentXml = assessmentTest.readText()
        assertTrue(assessmentXml.contains("identifier=\"image-prompt\""))
    }

    @Test
    fun cli_validateOnly_withoutOutputDir_doesNotWriteOutput() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val fixtureId = "choice-with-scoring"
        val markdown = readFixtureText("$fixtureId.md")
        val inputFile = tempDir.resolve("$fixtureId.md")
        inputFile.writeText(markdown)

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    inputFile.toString(),
                    "--test-title",
                    "Assessment Test",
                    "--validate-only",
                ),
            )

        assertEquals(0, exitCode)
        val outputDir = tempDir.resolve("qti-out")
        val outputFile = outputDir.resolve("$fixtureId.qti.xml")
        assertFalse(Files.exists(outputDir))
        assertFalse(Files.exists(outputFile))
        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertFalse(Files.exists(assessmentTest))
    }

    @Test
    fun cli_defaultsOutputDirPerInputDirectory() {
        val firstDir = Files.createTempDirectory("qti-cli-test-1")
        val secondDir = Files.createTempDirectory("qti-cli-test-2")
        val firstId = "choice-with-scoring"
        val secondId = "descriptive-with-scoring"
        val firstInput = firstDir.resolve("$firstId.md")
        val secondInput = secondDir.resolve("$secondId.md")
        firstInput.writeText(readFixtureText("$firstId.md"))
        secondInput.writeText(readFixtureText("$secondId.md"))

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    firstInput.toString(),
                    "--input",
                    secondInput.toString(),
                    "--test-title",
                    "Assessment Test",
                ),
            )

        assertEquals(0, exitCode)
        val firstDirOut = firstDir.resolve("qti-out")
        val secondDirOut = secondDir.resolve("qti-out")
        val firstOutput = firstDirOut.resolve("$firstId.qti.xml")
        val secondOutput = secondDirOut.resolve("$secondId.qti.xml")
        assertTrue(Files.exists(firstOutput))
        assertTrue(Files.exists(secondOutput))

        val firstAssessment = firstDirOut.resolve("assessment-test.qti.xml")
        val secondAssessment = secondDirOut.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(firstAssessment))
        assertTrue(Files.exists(secondAssessment))
        assertTrue(firstAssessment.readText().contains("identifier=\"$firstId\""))
        assertTrue(secondAssessment.readText().contains("identifier=\"$secondId\""))
    }

    @Test
    fun cli_writesAssessmentTestWithOrderedItems() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val firstId = "choice-with-scoring"
        val secondId = "descriptive-with-scoring"
        val firstInput = tempDir.resolve("$firstId.md")
        val secondInput = tempDir.resolve("$secondId.md")
        firstInput.writeText(readFixtureText("$firstId.md"))
        secondInput.writeText(readFixtureText("$secondId.md"))
        val expectedAssessment = readFixtureText("assessment-test-two-items.qti.xml")

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    firstInput.toString(),
                    "--input",
                    secondInput.toString(),
                    "--output-dir",
                    outputDir.toString(),
                    "--test-title",
                    "Assessment Test",
                ),
            )

        assertEquals(0, exitCode)
        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(assessmentTest))
        val actualXml = assessmentTest.readText()
        assertEquals(normalizeXml(expectedAssessment), normalizeXml(actualXml))
    }

    @Test
    fun cli_writesAssessmentTestWithCustomTitle() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val firstId = "choice-with-scoring"
        val secondId = "descriptive-with-scoring"
        val firstInput = tempDir.resolve("$firstId.md")
        val secondInput = tempDir.resolve("$secondId.md")
        firstInput.writeText(readFixtureText("$firstId.md"))
        secondInput.writeText(readFixtureText("$secondId.md"))
        val expectedAssessment = readFixtureText("assessment-test-two-items-custom-title.qti.xml")

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    firstInput.toString(),
                    "--input",
                    secondInput.toString(),
                    "--output-dir",
                    outputDir.toString(),
                    "--test-title",
                    "Custom Exam",
                ),
            )

        assertEquals(0, exitCode)
        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(assessmentTest))
        val actualXml = assessmentTest.readText()
        assertEquals(normalizeXml(expectedAssessment), normalizeXml(actualXml))
    }

    @Test
    fun cli_writesAssessmentTestWithJapaneseTitle() {
        val tempDir = Files.createTempDirectory("qti-cli-test")
        val outputDir = Files.createTempDirectory("qti-cli-out")
        val firstId = "choice-with-scoring"
        val secondId = "descriptive-with-scoring"
        val firstInput = tempDir.resolve("$firstId.md")
        val secondInput = tempDir.resolve("$secondId.md")
        firstInput.writeText(readFixtureText("$firstId.md"))
        secondInput.writeText(readFixtureText("$secondId.md"))
        val expectedAssessment = readFixtureText("assessment-test-two-items-jp-title.qti.xml")

        val exitCode =
            runCli(
                arrayOf(
                    "--input",
                    firstInput.toString(),
                    "--input",
                    secondInput.toString(),
                    "--output-dir",
                    outputDir.toString(),
                    "--test-title",
                    "日本語テスト",
                ),
            )

        assertEquals(0, exitCode)
        val assessmentTest = outputDir.resolve("assessment-test.qti.xml")
        assertTrue(Files.exists(assessmentTest))
        val actualXml = assessmentTest.readText()
        assertEquals(normalizeXml(expectedAssessment), normalizeXml(actualXml))
    }

    @Test
    fun cli_manifest_writesItemsInManifestOrderWithSummedTimeLimit() {
        val tempDir = Files.createTempDirectory("qti-cli-manifest-test")
        val outputDir = Files.createTempDirectory("qti-cli-manifest-out")
        val firstInput = tempDir.resolve("first.q.md")
        val secondInput = tempDir.resolve("second.q.md")
        firstInput.writeText(
            """
            ---
            question_type: descriptive
            time_budget_seconds: 45
            ---
            # First

            ## Prompt
            Explain the first concept.
            """.trimIndent(),
        )
        secondInput.writeText(
            """
            ---
            question_type: choice
            time_budget_seconds: 75
            ---
            # Second

            ## Prompt
            Select the second option.

            ## Options
            - [ ] First
            - [x] Second
            """.trimIndent(),
        )
        val manifest = tempDir.resolve("manifest.yaml")
        manifest.writeText(
            """
            title: Manifest Exam
            items:
              - second.q.md
              - first.q.md
            """.trimIndent(),
        )

        val exitCode =
            runCli(
                arrayOf(
                    "--manifest",
                    manifest.toString(),
                    "--output-dir",
                    outputDir.toString(),
                ),
            )

        assertEquals(0, exitCode)
        val assessmentXml = outputDir.resolve("assessment-test.qti.xml").readText()
        assertTrue(assessmentXml.contains("title=\"Manifest Exam\""))
        assertTrue(assessmentXml.contains("<qti-time-limits max-time=\"PT120S\"/>"))
        assertTrue(
            assessmentXml.indexOf("identifier=\"second.q\"") <
                assessmentXml.indexOf("identifier=\"first.q\""),
        )
    }

    @Test
    fun cli_manifest_usesExplicitManifestTimeLimit() {
        val tempDir = Files.createTempDirectory("qti-cli-manifest-test")
        val outputDir = Files.createTempDirectory("qti-cli-manifest-out")
        val input = tempDir.resolve("question.q.md")
        input.writeText(
            """
            ---
            question_type: descriptive
            time_budget_seconds: 45
            ---
            # Timed Question

            ## Prompt
            Explain the concept.
            """.trimIndent(),
        )
        val manifest = tempDir.resolve("manifest.yaml")
        manifest.writeText(
            """
            title: Explicit Time Exam
            time_limit_seconds: 300
            items:
              - question.q.md
            """.trimIndent(),
        )

        val exitCode =
            runCli(
                arrayOf(
                    "--manifest",
                    manifest.toString(),
                    "--output-dir",
                    outputDir.toString(),
                ),
            )

        assertEquals(0, exitCode)
        val assessmentXml = outputDir.resolve("assessment-test.qti.xml").readText()
        assertTrue(assessmentXml.contains("<qti-time-limits max-time=\"PT300S\"/>"))
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
