package com.metyatech.markdowntoqti

import java.nio.file.Files
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class MarkdownToQtiImageTest {
    @Test
    fun convertMarkdownToQtiWithAssets_throwsWhenLocalImageMissing() {
        val tempDir = Files.createTempDirectory("qti-image-missing")
        val inputFile = tempDir.resolve("missing-image.md")
        val markdown = """
            # Missing Image

            ## Type
            descriptive

            ## Prompt
            Look at this image.

            ![Missing](images/missing.png)
        """.trimIndent()
        inputFile.writeText(markdown)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            convertMarkdownToQtiWithAssets(markdown, "missing-image", inputFile)
        }

        assertTrue(exception.message?.contains("Image file not found") == true)
    }

    @Test
    fun convertMarkdownToQtiWithAssets_allowsRemoteImageUrls() {
        val tempDir = Files.createTempDirectory("qti-image-remote")
        val inputFile = tempDir.resolve("remote-image.md")
        val markdown = """
            # Remote Image

            ## Type
            descriptive

            ## Prompt
            See the diagram.

            ![Diagram](https://example.com/diagram.png "Remote")
        """.trimIndent()
        inputFile.writeText(markdown)

        val result = convertMarkdownToQtiWithAssets(markdown, "remote-image", inputFile)

        assertEquals(0, result.localImages.size)
        assertTrue(result.qtiXml.contains("<qti-img src=\"https://example.com/diagram.png\" alt=\"Diagram\" title=\"Remote\"/>"))
    }
}