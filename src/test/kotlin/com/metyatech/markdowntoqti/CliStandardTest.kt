package com.metyatech.markdowntoqti

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class CliStandardTest {
    @Test
    fun cli_printsVersion_andExitsZero() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)

        val exitCode = runCli(arrayOf("--version"), output = printStream)

        assertEquals(0, exitCode)
        val output = outputStream.toString().trim()
        assertTrue(output.contains("markdown-to-qti version"), "Output should contain version info: $output")
    }

    @Test
    fun cli_printsVersionShort_andExitsZero() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)

        val exitCode = runCli(arrayOf("-V"), output = printStream)

        assertEquals(0, exitCode)
        val output = outputStream.toString().trim()
        assertTrue(output.contains("markdown-to-qti version"), "Output should contain version info: $output")
    }

    @Test
    fun cli_printsHelp_andExitsZero() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)

        val exitCode = runCli(arrayOf("--help"), output = printStream)

        assertEquals(0, exitCode)
        val output = outputStream.toString().trim()
        assertTrue(output.contains("Usage: markdown-to-qti"), "Output should contain usage info: $output")
    }

    @Test
    fun cli_printsHelpShort_andExitsZero() {
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)

        val exitCode = runCli(arrayOf("-h"), output = printStream)

        assertEquals(0, exitCode)
        val output = outputStream.toString().trim()
        assertTrue(output.contains("Usage: markdown-to-qti"), "Output should contain usage info: $output")
    }
}
