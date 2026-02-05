package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class MarkdownToQtiErrorTest {
    @Test
    fun convertMarkdownToQti_requiresTitleHeading() {
        val markdown =
            """


            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "missing-title")
            }

        assertTrue(exception.message?.contains("Missing title heading") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsInvalidTitlePrefix() {
        val markdown =
            """
            #Title

            ## Type
            descriptive

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "bad-title")
            }

        assertTrue(exception.message?.contains("Title must start with '# '") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsEmptyTitle() {
        val markdown =
            """
            # 

            ## Type
            descriptive

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "empty-title")
            }

        assertTrue(exception.message?.contains("Title must not be empty") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsContentOutsideSections() {
        val markdown =
            """
            # Title
            Not in section

            ## Type
            descriptive

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "content-outside-section")
            }

        assertTrue(exception.message?.contains("Unexpected content outside section") == true)
    }

    @Test
    fun convertMarkdownToQti_requiresTypeSection() {
        val markdown =
            """
            # Title

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "missing-type")
            }

        assertTrue(exception.message?.contains("Missing ## Type section") == true)
    }

    @Test
    fun convertMarkdownToQti_requiresTypeValue() {
        val markdown =
            """
            # Title

            ## Type

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "missing-type-value")
            }

        assertTrue(exception.message?.contains("Type value missing") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsUnknownType() {
        val markdown =
            """
            # Title

            ## Type
            essay

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "unknown-type")
            }

        assertTrue(exception.message?.contains("Unknown question type") == true)
    }

    @Test
    fun convertMarkdownToQti_requiresPromptSection() {
        val markdown =
            """
            # Title

            ## Type
            descriptive
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "missing-prompt")
            }

        assertTrue(exception.message?.contains("Missing ## Prompt section") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsEmptyPrompt() {
        val markdown =
            """
            # Title

            ## Type
            descriptive

            ## Prompt

            ## Explanation
            Explanation.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "empty-prompt")
            }

        assertTrue(exception.message?.contains("Prompt section must not be empty") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsEmptyExplanation() {
        val markdown =
            """
            # Title

            ## Type
            descriptive

            ## Prompt
            Prompt.

            ## Explanation

            ## Scoring
            - 1: Criterion
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "empty-explanation")
            }

        assertTrue(exception.message?.contains("Explanation section must not be empty") == true)
    }

    @Test
    fun convertMarkdownToQti_requiresOptionsForChoice() {
        val markdown =
            """
            # Title

            ## Type
            choice

            ## Prompt
            Prompt.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "missing-options")
            }

        assertTrue(exception.message?.contains("Missing ## Options section") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsInvalidOptionFormat() {
        val markdown =
            """
            # Title

            ## Type
            choice

            ## Prompt
            Prompt.

            ## Options
            - 1
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "invalid-option")
            }

        assertTrue(exception.message?.contains("Options must use task list items") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsEmptyOptionsList() {
        val markdown =
            """
            # Title

            ## Type
            choice

            ## Prompt
            Prompt.

            ## Options
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "empty-options")
            }

        assertTrue(exception.message?.contains("Options must not be empty") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsMultipleCorrectOptions() {
        val markdown =
            """
            # Title

            ## Type
            choice

            ## Prompt
            Prompt.

            ## Options
            - [x] A
            - [x] B
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "multi-correct")
            }

        assertTrue(exception.message?.contains("Choice question must have exactly one correct option") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsClozeWithoutBlanks() {
        val markdown =
            """
            # Title

            ## Type
            cloze

            ## Prompt
            No blanks here.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "cloze-no-blank")
            }

        assertTrue(exception.message?.contains("Cloze prompt must include at least one blank") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsUnclosedClozeBlank() {
        val markdown =
            """
            # Title

            ## Type
            cloze

            ## Prompt
            Unclosed {{answer.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "cloze-unclosed")
            }

        assertTrue(exception.message?.contains("Unclosed cloze blank") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsEmptyClozeBlank() {
        val markdown =
            """
            # Title

            ## Type
            cloze

            ## Prompt
            Empty {{ }} blank.
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "cloze-empty-blank")
            }

        assertTrue(exception.message?.contains("Cloze blank must not be empty") == true)
    }

    @Test
    fun convertMarkdownToQti_rejectsRawHtmlBlocks() {
        val markdown =
            """
            # Title

            ## Type
            descriptive

            ## Prompt
            <div>Raw HTML</div>
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQti(markdown, "raw-html")
            }

        assertTrue(exception.message?.contains("Raw HTML") == true)
    }

    @Test
    fun convertMarkdownToQtiWithAssets_rejectsAbsoluteImagePath() {
        val tempDir = Files.createTempDirectory("qti-image-absolute")
        val absolutePath = tempDir.resolve("absolute.png").toAbsolutePath()
        val markdown =
            """
            # Title

            ## Type
            descriptive

            ## Prompt
            ![Alt]($absolutePath)
            """.trimIndent()

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                convertMarkdownToQtiWithAssets(markdown, "image-absolute", tempDir.resolve("input.md"))
            }

        assertTrue(exception.message?.contains("Image path must be relative") == true)
    }
}
