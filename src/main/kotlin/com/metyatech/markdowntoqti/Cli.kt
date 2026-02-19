package com.metyatech.markdowntoqti

import org.xml.sax.InputSource
import java.io.PrintStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private const val VERSION = "0.1.0"

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}

fun runCli(
    args: Array<String>,
    output: PrintStream = System.out,
    error: PrintStream = System.err,
): Int {
    val parsed =
        when (val result = parseArgs(args, output, error)) {
            is CliResult.Options -> result.options
            is CliResult.HelpOrVersion -> return 0
            is CliResult.Error -> return 1
        }

    val inputs = resolveInputs(parsed.inputPaths, error) ?: return 1

    val outputDir = parsed.outputDir
    if (outputDir != null) {
        Files.createDirectories(outputDir)
    }
    val assessmentItemsByOutputDir = mutableMapOf<Path, MutableList<AssessmentItemRef>>()

    for (inputPath in inputs) {
        try {
            val markdown = inputPath.readText()
            val identifier = inputPath.fileNameWithoutExtension()
            val conversion = convertMarkdownToQtiWithAssets(markdown, identifier, inputPath)
            if (parsed.validateOnly) {
                validateXml(conversion.qtiXml)
                if (parsed.verbose) {
                    output.println("Validated: ${inputPath.toAbsolutePath()}")
                }
            } else {
                val resolvedOutputDir = (outputDir ?: defaultOutputDirFor(inputPath)).normalize()
                Files.createDirectories(resolvedOutputDir)
                val outputFile = resolvedOutputDir.resolve("$identifier.qti.xml")
                outputFile.writeText(conversion.qtiXml)
                copyLocalImages(conversion.localImages, resolvedOutputDir)
                registerAssessmentItem(assessmentItemsByOutputDir, resolvedOutputDir, identifier)
                if (parsed.verbose) {
                    output.println("Wrote: ${outputFile.toAbsolutePath()}")
                }
            }
        } catch (
            @Suppress("TooGenericExceptionCaught") exception: Exception,
        ) {
            val message = exception.message?.takeIf { it.isNotBlank() } ?: exception.javaClass.simpleName
            error.println("Error in ${inputPath.toAbsolutePath()}: $message")
            return 1
        }
    }

    if (!parsed.validateOnly) {
        writeAssessmentTests(assessmentItemsByOutputDir, parsed.testTitle, output, parsed.verbose)
    }

    return 0
}

private data class CliOptions(
    val inputPaths: List<Path>,
    val outputDir: Path?,
    val validateOnly: Boolean,
    val verbose: Boolean,
    val testTitle: String,
)

private sealed class CliResult {
    data class Options(
        val options: CliOptions,
    ) : CliResult()

    object HelpOrVersion : CliResult()

    object Error : CliResult()
}

private data class AssessmentItemRef(
    val identifier: String,
    val href: String,
)

private fun parseArgs(
    args: Array<String>,
    output: PrintStream,
    error: PrintStream,
): CliResult {
    if (args.isEmpty()) {
        printUsage(error)
        return CliResult.Error
    }

    val inputPaths = mutableListOf<Path>()
    var outputDir: Path? = null
    var validateOnly = false
    var verbose = false
    var testTitle: String? = null

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--input" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --input")
                            return CliResult.Error
                        }
                inputPaths.add(Path.of(value))
                index += 2
            }
            "--output-dir" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --output-dir")
                            return CliResult.Error
                        }
                outputDir = Path.of(value)
                index += 2
            }
            "--validate-only" -> {
                validateOnly = true
                index += 1
            }
            "--test-title" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --test-title")
                            return CliResult.Error
                        }
                testTitle = value
                index += 2
            }
            "--verbose" -> {
                verbose = true
                index += 1
            }
            "--version", "-V" -> {
                output.println("markdown-to-qti version $VERSION")
                return CliResult.HelpOrVersion
            }
            "--help", "-h" -> {
                printUsage(output)
                return CliResult.HelpOrVersion
            }
            else -> {
                error.println("Unknown argument: $arg")
                printUsage(error)
                return CliResult.Error
            }
        }
    }

    if (inputPaths.isEmpty()) {
        error.println("At least one --input is required.")
        return CliResult.Error
    }
    if (testTitle.isNullOrBlank()) {
        error.println("--test-title is required.")
        return CliResult.Error
    }

    return CliResult.Options(
        CliOptions(
            inputPaths = inputPaths,
            outputDir = outputDir,
            validateOnly = validateOnly,
            verbose = verbose,
            testTitle = testTitle,
        ),
    )
}

private fun resolveInputs(
    paths: List<Path>,
    error: PrintStream,
): List<Path>? {
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
    val factory =
        DocumentBuilderFactory.newInstance().apply {
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

private fun copyLocalImages(
    images: List<LocalImage>,
    outputDir: Path,
) {
    images.forEach { image ->
        val destination = outputDir.resolve(image.outputRelativePath)
        destination.parent?.let { Files.createDirectories(it) }
        Files.copy(image.sourcePath, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun printUsage(stream: PrintStream) {
    stream.println(
        "Usage: markdown-to-qti --input <path> [--input <path> ...] " +
            "--test-title <title> [--output-dir <dir>] [--validate-only] [--verbose] [--version] [--help]",
    )
    stream.println("When --output-dir is omitted, output is written to <input-directory>/qti-out.")
}

private fun defaultOutputDirFor(inputPath: Path): Path {
    val parent = inputPath.toAbsolutePath().parent ?: Path.of(".").toAbsolutePath()
    return parent.resolve("qti-out")
}

private fun registerAssessmentItem(
    assessmentItemsByOutputDir: MutableMap<Path, MutableList<AssessmentItemRef>>,
    outputDir: Path,
    identifier: String,
) {
    val items = assessmentItemsByOutputDir.getOrPut(outputDir) { mutableListOf() }
    items.add(AssessmentItemRef(identifier, "$identifier.qti.xml"))
}

private fun writeAssessmentTests(
    assessmentItemsByOutputDir: Map<Path, List<AssessmentItemRef>>,
    testTitle: String,
    output: PrintStream,
    verbose: Boolean,
) {
    assessmentItemsByOutputDir.forEach { (outputDir, items) ->
        if (items.isEmpty()) {
            return@forEach
        }
        val xml = buildAssessmentTest(items, testTitle)
        val testFile = outputDir.resolve("assessment-test.qti.xml")
        testFile.writeText(xml)
        if (verbose) {
            output.println("Wrote: ${testFile.toAbsolutePath()}")
        }
    }
}

private fun buildAssessmentTest(
    items: List<AssessmentItemRef>,
    testTitle: String,
): String {
    val builder = StringBuilder()
    builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    builder.append(
        "<qti-assessment-test\n" +
            "    xmlns=\"http://www.imsglobal.org/xsd/imsqti_v3p0\"\n" +
            "    identifier=\"assessment-test\"\n" +
            "    title=\"${escapeXml(testTitle)}\">\n",
    )
    builder.append(
        "  <qti-test-part identifier=\"part-1\" navigation-mode=\"linear\" submission-mode=\"individual\">\n",
    )
    builder.append("    <qti-assessment-section identifier=\"section-1\" title=\"Section 1\" visible=\"true\">\n")
    items.forEach { item ->
        builder.append("      <qti-assessment-item-ref identifier=\"")
        builder.append(escapeXml(item.identifier))
        builder.append("\" href=\"")
        builder.append(escapeXml(item.href))
        builder.append("\"/>\n")
    }
    builder.append("    </qti-assessment-section>\n")
    builder.append("  </qti-test-part>\n")
    builder.append("</qti-assessment-test>\n")
    return builder.toString()
}
