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

const val VERSION = "0.1.0"

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
            is ParseResult.Success -> result.options
            is ParseResult.HelpOrVersion -> return 0
            is ParseResult.Error -> return 1
        }
    if (parsed.manifestPath != null) {
        return runManifestCli(parsed, output, error)
    }
    val inputs = resolveInputs(parsed.inputPaths, error) ?: return 1

    val outputDir = parsed.outputDir
    if (outputDir != null && !parsed.validateOnly) {
        Files.createDirectories(outputDir)
    }
    val assessmentItemsByOutputDir = mutableMapOf<Path, MutableList<AssessmentItemRef>>()
    val generatedFiles = mutableListOf<String>()

    inputs.forEach { inputSource ->
        val markdown = inputSource.readText()
        val identifier = inputSource.identifier
        val sourcePath = inputSource.path ?: Path.of(".").toAbsolutePath()
        val conversion = convertMarkdownToQtiWithAssets(markdown, identifier, sourcePath)
        if (parsed.validateOnly) {
            validateXml(conversion.qtiXml)
            if (parsed.verbose) {
                output.println("Validated: ${inputSource.displayName}")
            }
        } else {
            val resolvedOutputDir = (outputDir ?: defaultOutputDirFor(sourcePath)).normalize()
            Files.createDirectories(resolvedOutputDir)
            val outputFile = resolvedOutputDir.resolve("$identifier.qti.xml")
            outputFile.writeText(conversion.qtiXml, Charsets.UTF_8)
            generatedFiles.add(outputFile.toAbsolutePath().toString())
            copyLocalImages(conversion.localImages, resolvedOutputDir).forEach {
                generatedFiles.add(it.toAbsolutePath().toString())
            }
            registerAssessmentItem(assessmentItemsByOutputDir, resolvedOutputDir, identifier)
            if (parsed.verbose) {
                output.println("Wrote: ${outputFile.toAbsolutePath()}")
            }
        }
    }

    if (!parsed.validateOnly) {
        writeAssessmentTests(assessmentItemsByOutputDir, parsed.testTitle ?: "", null, output, parsed.verbose).forEach {
            generatedFiles.add(it.toAbsolutePath().toString())
        }
    }

    if (parsed.json) {
        writeJsonSummary(output, generatedFiles)
    }

    return 0
}

private fun writeJsonSummary(
    output: PrintStream,
    generatedFiles: List<String>,
) {
    val json =
        buildString {
            append("{\n")
            append("  \"version\": \"$VERSION\",\n")
            append("  \"generatedFiles\": [\n")
            generatedFiles.forEachIndexed { index, file ->
                append("    \"")
                append(file.replace("\\", "\\\\").replace("\"", "\\\""))
                append("\"")
                if (index < generatedFiles.size - 1) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}")
        }
    output.println(json)
}

private data class CliOptions(
    val inputPaths: List<Path>,
    val manifestPath: Path?,
    val outputDir: Path?,
    val validateOnly: Boolean,
    val verbose: Boolean,
    val testTitle: String?,
    val json: Boolean,
)

private sealed interface ParseResult {
    data class Success(
        val options: CliOptions,
    ) : ParseResult

    data object HelpOrVersion : ParseResult

    data object Error : ParseResult
}

private data class AssessmentItemRef(
    val identifier: String,
    val href: String,
)

private data class ManifestSpec(
    val title: String,
    val timeLimitSeconds: Int?,
    val itemPaths: List<Path>,
)

@Suppress("ReturnCount")
private fun parseArgs(
    args: Array<String>,
    output: PrintStream,
    error: PrintStream,
): ParseResult {
    if (args.isEmpty()) {
        printUsage(error)
        return ParseResult.Error
    }

    val inputPaths = mutableListOf<Path>()
    var manifestPath: Path? = null
    var outputDir: Path? = null
    var validateOnly = false
    var verbose = false
    var testTitle: String? = null
    var json = false

    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--input" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --input")
                            return ParseResult.Error
                        }
                inputPaths.add(Path.of(value))
                index += 2
            }
            "--manifest" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --manifest")
                            return ParseResult.Error
                        }
                manifestPath = Path.of(value)
                index += 2
            }
            "--output-dir" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --output-dir")
                            return ParseResult.Error
                        }
                outputDir = Path.of(value)
                index += 2
            }
            "--validate-only", "--dry-run" -> {
                validateOnly = true
                index += 1
            }
            "--test-title" -> {
                val value =
                    args.getOrNull(index + 1)
                        ?: run {
                            error.println("Missing value for --test-title")
                            return ParseResult.Error
                        }
                testTitle = value
                index += 2
            }
            "--verbose" -> {
                verbose = true
                index += 1
            }
            "--json" -> {
                json = true
                index += 1
            }
            "--version", "-V" -> {
                output.println("markdown-to-qti version $VERSION")
                return ParseResult.HelpOrVersion
            }
            "--help", "-h" -> {
                printUsage(output)
                return ParseResult.HelpOrVersion
            }
            else -> {
                error.println("Unknown argument: $arg")
                printUsage(error)
                return ParseResult.Error
            }
        }
    }

    if (manifestPath != null && inputPaths.isNotEmpty()) {
        error.println("--manifest cannot be combined with --input.")
        return ParseResult.Error
    }
    if (manifestPath == null && inputPaths.isEmpty()) {
        error.println("At least one --input is required.")
        return ParseResult.Error
    }
    if (manifestPath == null && testTitle.isNullOrBlank()) {
        error.println("--test-title is required.")
        return ParseResult.Error
    }

    return ParseResult.Success(
        CliOptions(
            inputPaths = inputPaths,
            manifestPath = manifestPath,
            outputDir = outputDir,
            validateOnly = validateOnly,
            verbose = verbose,
            testTitle = testTitle,
            json = json,
        ),
    )
}

private fun runManifestCli(
    parsed: CliOptions,
    output: PrintStream,
    error: PrintStream,
): Int {
    val manifestPath = parsed.manifestPath ?: return 1
    if (!Files.exists(manifestPath)) {
        error.println("Manifest not found: ${manifestPath.toAbsolutePath()}")
        return 1
    }
    val manifest =
        try {
            parseManifest(manifestPath)
        } catch (exception: IllegalArgumentException) {
            error.println(exception.message)
            return 1
        }
    val outputDir = (parsed.outputDir ?: defaultOutputDirFor(manifestPath)).normalize()
    val generatedFiles = mutableListOf<String>()
    val itemRefs = mutableListOf<AssessmentItemRef>()
    var summedTimeBudget = 0

    if (!parsed.validateOnly) {
        Files.createDirectories(outputDir)
    }

    manifest.itemPaths.forEach { itemPath ->
        if (!Files.exists(itemPath)) {
            error.println("Manifest item not found: ${itemPath.toAbsolutePath()}")
            return 1
        }
        val identifier = itemPath.fileNameWithoutExtension()
        val conversion =
            try {
                convertMarkdownToQtiWithAssets(itemPath.readText(Charsets.UTF_8), identifier, itemPath)
            } catch (exception: IllegalArgumentException) {
                error.println(exception.message)
                return 1
            }
        summedTimeBudget += conversion.timeBudgetSeconds ?: 0
        if (parsed.validateOnly) {
            validateXml(conversion.qtiXml)
            if (parsed.verbose) {
                output.println("Validated: ${itemPath.toAbsolutePath()}")
            }
        } else {
            val outputFile = outputDir.resolve("$identifier.qti.xml")
            outputFile.writeText(conversion.qtiXml, Charsets.UTF_8)
            generatedFiles.add(outputFile.toAbsolutePath().toString())
            copyLocalImages(conversion.localImages, outputDir).forEach {
                generatedFiles.add(it.toAbsolutePath().toString())
            }
            if (parsed.verbose) {
                output.println("Wrote: ${outputFile.toAbsolutePath()}")
            }
        }
        itemRefs.add(AssessmentItemRef(identifier, "$identifier.qti.xml"))
    }

    val resolvedTimeLimit = manifest.timeLimitSeconds ?: summedTimeBudget
    if (parsed.validateOnly) {
        validateXml(buildAssessmentTest(itemRefs, manifest.title, resolvedTimeLimit))
    } else {
        val assessmentXml = buildAssessmentTest(itemRefs, manifest.title, resolvedTimeLimit)
        val assessmentFile = outputDir.resolve("assessment-test.qti.xml")
        assessmentFile.writeText(assessmentXml, Charsets.UTF_8)
        generatedFiles.add(assessmentFile.toAbsolutePath().toString())
        if (parsed.verbose) {
            output.println("Wrote: ${assessmentFile.toAbsolutePath()}")
        }
    }

    if (parsed.json) {
        writeJsonSummary(output, generatedFiles)
    }
    return 0
}

private fun parseManifest(path: Path): ManifestSpec {
    val lines = path.readText(Charsets.UTF_8).replace("\r\n", "\n").split("\n")
    var title: String? = null
    var timeLimitSeconds: Int? = null
    val items = mutableListOf<Path>()
    var inItems = false
    lines.forEachIndexed { index, rawLine ->
        val lineNumber = index + 1
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) {
            return@forEachIndexed
        }
        if (inItems && rawLine.startsWith("  - ")) {
            val item = rawLine.removePrefix("  - ").trim().trim('"', '\'')
            require(item.isNotBlank()) {
                "Manifest item must not be empty (${path.toAbsolutePath()}:$lineNumber)"
            }
            items.add(
                path
                    .toAbsolutePath()
                    .parent
                    .resolve(item)
                    .normalize(),
            )
            return@forEachIndexed
        }
        inItems = false
        when {
            line.startsWith("title:") -> title = line.removePrefix("title:").trim().trim('"', '\'')
            line.startsWith("time_limit_seconds:") ->
                timeLimitSeconds =
                    parsePositiveInt(
                        line.removePrefix("time_limit_seconds:").trim(),
                        "time_limit_seconds",
                        path,
                        lineNumber,
                    )
            line == "items:" -> inItems = true
            line.startsWith("type:") ->
                throw IllegalArgumentException("Manifest field 'type' is deprecated and not accepted")
            else ->
                throw IllegalArgumentException("Unknown manifest field (${path.toAbsolutePath()}:$lineNumber): $line")
        }
    }
    val manifestTitle = title
    require(!manifestTitle.isNullOrBlank()) { "Manifest title is required (${path.toAbsolutePath()})" }
    require(items.isNotEmpty()) { "Manifest items are required (${path.toAbsolutePath()})" }
    return ManifestSpec(manifestTitle, timeLimitSeconds, items)
}

private data class ConversionInputSource(
    val identifier: String,
    val displayName: String,
    val path: Path?, // null for stdin
    val readText: () -> String,
)

private fun resolveInputs(
    paths: List<Path>,
    error: PrintStream,
): List<ConversionInputSource>? {
    val resolved = mutableListOf<ConversionInputSource>()
    paths.forEach { path ->
        if (path.toString() == "-") {
            resolved.add(
                ConversionInputSource(
                    identifier = "stdin",
                    displayName = "stdin",
                    path = null,
                    readText = { System.`in`.bufferedReader().readText() },
                ),
            )
            return@forEach
        }
        if (!Files.exists(path)) {
            error.println("Input not found: ${path.toAbsolutePath()}")
            return null
        }
        if (path.isDirectory()) {
            Files.list(path).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && it.extension.equals("md", ignoreCase = true) }
                    .forEach {
                        resolved.add(
                            ConversionInputSource(
                                identifier = it.fileNameWithoutExtension(),
                                displayName = it.toAbsolutePath().toString(),
                                path = it,
                                readText = { it.readText(Charsets.UTF_8) },
                            ),
                        )
                    }
            }
        } else {
            resolved.add(
                ConversionInputSource(
                    identifier = path.fileNameWithoutExtension(),
                    displayName = path.toAbsolutePath().toString(),
                    path = path,
                    readText = { path.readText(Charsets.UTF_8) },
                ),
            )
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
): List<Path> {
    val copied = mutableListOf<Path>()
    images.forEach { image ->
        val destination = outputDir.resolve(image.outputRelativePath)
        destination.parent?.let { Files.createDirectories(it) }
        Files.copy(image.sourcePath, destination, StandardCopyOption.REPLACE_EXISTING)
        copied.add(destination)
    }
    return copied
}

private fun printUsage(error: PrintStream) {
    error.println(
        "Usage: markdown-to-qti --manifest <path> | --input <path> [--input <path> ...] --test-title <title>" +
            " [--output-dir <dir>] [--validate-only] [--dry-run] [--verbose] [--version] [--json]",
    )
    error.println("Options:")
    error.println("  --manifest <path>  Manifest YAML file for the canonical package conversion.")
    error.println("  --input <path>      Markdown file or directory (directories scan for *.md). Use '-' for stdin.")
    error.println("  --test-title <title> Assessment test title (required).")
    error.println(
        "  --output-dir <dir>  Output directory for .qti.xml files." +
            " Defaults to qti-out under each input file directory.",
    )
    error.println("  --validate-only     Parse and validate XML without writing files.")
    error.println("  --dry-run           Alias for --validate-only.")
    error.println("  --verbose           Log processed files.")
    error.println("  --json              Output machine-readable JSON summary to stdout.")
    error.println("  --version, -V       Show version.")
    error.println("  --help, -h          Show help.")
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
    timeLimitSeconds: Int?,
    output: PrintStream,
    verbose: Boolean,
): List<Path> {
    val written = mutableListOf<Path>()
    assessmentItemsByOutputDir.forEach { (outputDir, items) ->
        if (items.isEmpty()) {
            return@forEach
        }
        val xml = buildAssessmentTest(items, testTitle, timeLimitSeconds)
        val testFile = outputDir.resolve("assessment-test.qti.xml")
        testFile.writeText(xml, Charsets.UTF_8)
        written.add(testFile)
        if (verbose) {
            output.println("Wrote: ${testFile.toAbsolutePath()}")
        }
    }
    return written
}

private fun buildAssessmentTest(
    items: List<AssessmentItemRef>,
    testTitle: String,
    timeLimitSeconds: Int?,
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
    if (timeLimitSeconds != null) {
        builder.append("    <qti-time-limits max-time=\"")
        builder.append(formatSecondsAsIsoDuration(timeLimitSeconds))
        builder.append("\"/>\n")
    }
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
