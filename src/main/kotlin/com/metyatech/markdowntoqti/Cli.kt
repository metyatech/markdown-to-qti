package com.metyatech.markdowntoqti

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource
import java.io.StringReader

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

fun runCli(
    args: Array<String>,
    output: PrintStream = System.out,
    error: PrintStream = System.err,
): Int {
    val parsed = parseArgs(args, error) ?: return 1
    val inputs = resolveInputs(parsed.inputPaths, error) ?: return 1

    if (!parsed.validateOnly && parsed.outputDir == null) {
        error.println("output directory is required unless --validate-only is set.")
        return 1
    }

    val outputDir = parsed.outputDir
    if (outputDir != null) {
        Files.createDirectories(outputDir)
    }

    inputs.forEach { inputPath ->
        val markdown = inputPath.readText()
        val identifier = inputPath.fileNameWithoutExtension()
        val qtiXml = convertMarkdownToQti(markdown, identifier)
        if (parsed.validateOnly) {
            validateXml(qtiXml)
            if (parsed.verbose) {
                output.println("Validated: ${inputPath.toAbsolutePath()}")
            }
        } else {
            val outputFile = outputDir!!.resolve("$identifier.qti.xml")
            outputFile.writeText(qtiXml)
            if (parsed.verbose) {
                output.println("Wrote: ${outputFile.toAbsolutePath()}")
            }
        }
    }

    return 0
}

private data class CliOptions(
    val inputPaths: List<Path>,
    val outputDir: Path?,
    val validateOnly: Boolean,
    val verbose: Boolean,
)

private fun parseArgs(args: Array<String>, error: PrintStream): CliOptions? {
    if (args.isEmpty()) {
        printUsage(error)
        return null
    }

    val inputPaths = mutableListOf<Path>()
    var outputDir: Path? = null
    var validateOnly = false
    var verbose = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--input" -> {
                val value = args.getOrNull(index + 1)
                    ?: run {
                        error.println("Missing value for --input")
                        return null
                    }
                inputPaths.add(Path.of(value))
                index += 2
            }
            "--output-dir" -> {
                val value = args.getOrNull(index + 1)
                    ?: run {
                        error.println("Missing value for --output-dir")
                        return null
                    }
                outputDir = Path.of(value)
                index += 2
            }
            "--validate-only" -> {
                validateOnly = true
                index += 1
            }
            "--verbose" -> {
                verbose = true
                index += 1
            }
            "--help", "-h" -> {
                printUsage(error)
                return null
            }
            else -> {
                error.println("Unknown argument: $arg")
                printUsage(error)
                return null
            }
        }
    }

    if (inputPaths.isEmpty()) {
        error.println("At least one --input is required.")
        return null
    }

    return CliOptions(
        inputPaths = inputPaths,
        outputDir = outputDir,
        validateOnly = validateOnly,
        verbose = verbose,
    )
}

private fun resolveInputs(paths: List<Path>, error: PrintStream): List<Path>? {
    val resolved = mutableListOf<Path>()
    paths.forEach { path ->
        if (!Files.exists(path)) {
            error.println("Input not found: ${path.toAbsolutePath()}")
            return null
        }
        if (path.isDirectory()) {
            Files.list(path).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension.equals("md", ignoreCase = true) }
                    .forEach { resolved.add(it) }
            }
        } else {
            resolved.add(path)
        }
    }

    if (resolved.isEmpty()) {
        error.println("No Markdown files found in inputs.")
        return null
    }

    return resolved
}

private fun validateXml(xml: String) {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }
    val builder = factory.newDocumentBuilder()
    builder.parse(InputSource(StringReader(xml)))
}

private fun Path.fileNameWithoutExtension(): String {
    val filename = name
    return if (filename.contains('.')) {
        filename.substringBeforeLast('.')
    } else {
        filename
    }
}

private fun printUsage(error: PrintStream) {
    error.println("Usage: markdown-to-qti --input <path> [--input <path> ...] --output-dir <dir> [--validate-only] [--verbose]")
}
