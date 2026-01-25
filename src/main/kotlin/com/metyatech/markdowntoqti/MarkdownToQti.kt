package com.metyatech.markdowntoqti

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

private enum class QuestionType {
    DESCRIPTIVE,
    CHOICE,
    CLOZE,
}

data class ScoringCriterion(
    val points: BigDecimal,
    val criterionXml: String,
)

internal data class ChoiceOption(
    val contentXml: String,
    val isCorrect: Boolean,
    val localImages: List<String>,
)

private data class MarkdownQuestion(
    val identifier: String,
    val title: String,
    val type: QuestionType,
    val prompt: RenderedMarkdown,
    val explanation: RenderedMarkdown? = null,
    val options: List<ChoiceOption> = emptyList(),
    val scoring: List<ScoringCriterion> = emptyList(),
)

data class LocalImage(
    val sourcePath: Path,
    val outputRelativePath: Path,
)

data class QtiConversionResult(
    val qtiXml: String,
    val localImages: List<LocalImage>,
)

internal data class SectionContent(
    val name: String,
    val lines: List<String>,
    val startLine: Int,
) {
    fun text(): String = lines.joinToString("\n")
    fun isBlank(): Boolean = lines.all { it.isBlank() }
}

private data class MarkdownParseResult(
    val question: MarkdownQuestion,
    val imageSources: List<String>,
)

fun convertMarkdownToQti(markdown: String, fixtureId: String): String {
    val parsed = parseMarkdownQuestion(markdown, fixtureId, null)
    return QtiBuilder(parsed.question).build()
}

fun convertMarkdownToQtiWithAssets(markdown: String, fixtureId: String, sourcePath: Path): QtiConversionResult {
    val parsed = parseMarkdownQuestion(markdown, fixtureId, sourcePath)
    val localImages = resolveLocalImages(parsed.imageSources, sourcePath)
    return QtiConversionResult(QtiBuilder(parsed.question).build(), localImages)
}

private fun parseMarkdownQuestion(markdown: String, identifier: String, sourcePath: Path?): MarkdownParseResult {
    val normalized = markdown.replace("\r\n", "\n")
    val lines = normalized.split("\n")
    var index = 0

    fun nextNonEmptyLine(): Pair<String, Int>? {
        while (index < lines.size) {
            val line = lines[index]
            val lineNumber = index + 1
            index += 1
            if (line.isNotBlank()) {
                return line to lineNumber
            }
        }
        return null
    }

    val titleLine = nextNonEmptyLine()
        ?: throw IllegalArgumentException("Missing title heading")
    if (!titleLine.first.startsWith("# ")) {
        throw IllegalArgumentException("Title must start with '# '")
    }
    val title = titleLine.first.removePrefix("# ").trim()
    if (title.isBlank()) {
        throw IllegalArgumentException("Title must not be empty")
    }

    val sections = mutableMapOf<String, SectionContent>()
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }
        if (!line.startsWith("## ")) {
            throw IllegalArgumentException("Unexpected content outside section: $line")
        }
        val heading = line.removePrefix("## ").trim()
        index += 1

        val content = mutableListOf<String>()
        val startLine = index + 1
        while (index < lines.size && !lines[index].startsWith("## ")) {
            content.add(lines[index])
            index += 1
        }
        sections[heading] = SectionContent(heading, content, startLine)
    }

    val typeSection = sections["Type"]
        ?: throw IllegalArgumentException("Missing ## Type section")
    val typeValue = typeSection.lines.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: throw IllegalArgumentException("Type value missing")
    val type = when (typeValue) {
        "descriptive" -> QuestionType.DESCRIPTIVE
        "choice" -> QuestionType.CHOICE
        "cloze" -> QuestionType.CLOZE
        else -> throw IllegalArgumentException("Unknown question type: $typeValue")
    }

    val renderer = MarkdownQtiRenderer()

    val promptSection = sections["Prompt"]
        ?: throw IllegalArgumentException("Missing ## Prompt section")
    if (promptSection.isBlank()) {
        throw IllegalArgumentException("Prompt section must not be empty")
    }
    val promptContext = RenderContext("Prompt", sourcePath, promptSection.startLine)
    val promptRender = renderer.renderBlocks(
        markdown = promptSection.text(),
        context = promptContext,
        clozeHandling = if (type == QuestionType.CLOZE) ClozeHandling.ENABLED else ClozeHandling.DISABLED,
    )
    if (type == QuestionType.CLOZE && promptRender.clozeAnswers.isEmpty()) {
        throw IllegalArgumentException("Cloze prompt must include at least one blank")
    }

    val explanationRender = sections["Explanation"]?.let { section ->
        if (section.isBlank()) {
            throw IllegalArgumentException("Explanation section must not be empty")
        }
        val context = RenderContext("Explanation", sourcePath, section.startLine)
        renderer.renderBlocks(section.text(), context, ClozeHandling.DISABLED)
    }

    val scoring = sections["Scoring"]?.let { section ->
        parseScoringSection(section, renderer, sourcePath)
    } ?: emptyList()

    val options = if (type == QuestionType.CHOICE) {
        val optionsSection = sections["Options"]
            ?: throw IllegalArgumentException("Missing ## Options section")
        if (optionsSection.isBlank()) {
            throw IllegalArgumentException("Options must not be empty")
        }
        val context = RenderContext("Options", sourcePath, optionsSection.startLine)
        val renderedOptions = renderer.renderChoiceOptions(optionsSection.text(), context)
        if (renderedOptions.isEmpty()) {
            throw IllegalArgumentException("Options must not be empty")
        }
        val correctCount = renderedOptions.count { it.isCorrect }
        if (correctCount != 1) {
            throw IllegalArgumentException("Choice question must have exactly one correct option")
        }
        renderedOptions
    } else {
        emptyList()
    }

    val imageSources = mutableSetOf<String>()
    imageSources.addAll(promptRender.localImages)
    explanationRender?.let { imageSources.addAll(it.localImages) }
    options.forEach { option -> imageSources.addAll(option.localImages) }

    val question = MarkdownQuestion(
        identifier = identifier,
        title = title,
        type = type,
        prompt = promptRender,
        explanation = explanationRender,
        options = options,
        scoring = scoring,
    )

    return MarkdownParseResult(question, imageSources.toList())
}

internal fun parseScoringSection(
    section: SectionContent,
    renderer: MarkdownQtiRenderer,
    sourcePath: Path?,
): List<ScoringCriterion> {
    val criteria = mutableListOf<ScoringCriterion>()
    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?):\s*(.*)$""")

    section.lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return@forEachIndexed
        }
        val content = if (line.startsWith("- ")) {
            line.removePrefix("- ").trim()
        } else {
            line
        }
        val match = pattern.matchEntire(content)
            ?: throw IllegalArgumentException("Invalid scoring points in line: $rawLine")
        val points = match.groupValues[1]
        val criterion = match.groupValues[2].trim()
        if (criterion.isBlank()) {
            throw IllegalArgumentException("Scoring criterion must not be empty: $rawLine")
        }
        val context = RenderContext("Scoring", sourcePath, section.startLine + index)
        val rendered = renderer.renderInline(criterion, context, ClozeHandling.DISABLED)
        criteria.add(ScoringCriterion(BigDecimal(points), rendered.xml))
    }

    return criteria
}

private fun resolveLocalImages(imageSources: List<String>, sourcePath: Path): List<LocalImage> {
    val sourceDir = sourcePath.parent
    return imageSources.mapNotNull { source ->
        if (isRemoteImagePath(source)) {
            return@mapNotNull null
        }
        val sourcePathValue = Path.of(source)
        if (sourcePathValue.isAbsolute) {
            throw IllegalArgumentException("Image path must be relative in ${sourcePath.toAbsolutePath()}: $source")
        }
        val resolvedSource = sourceDir.resolve(sourcePathValue).normalize()
        if (!Files.exists(resolvedSource) || !Files.isRegularFile(resolvedSource)) {
            throw IllegalArgumentException("Image file not found in ${sourcePath.toAbsolutePath()}: $source")
        }
        LocalImage(resolvedSource, sourcePathValue.normalize())
    }
}

internal fun isRemoteImagePath(source: String): Boolean {
    val normalized = source.lowercase()
    return normalized.startsWith("http://") ||
        normalized.startsWith("https://") ||
        normalized.startsWith("data:")
}

private class QtiBuilder(private val question: MarkdownQuestion) {
    fun build(): String {
        val builder = StringBuilder()
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        builder.append(
            "<qti-assessment-item\n" +
                "    xmlns=\"http://www.imsglobal.org/xsd/imsqti_v3p0\"\n" +
                "    identifier=\"${escapeXml(question.identifier)}\"\n" +
                "    title=\"${escapeXml(question.title)}\"\n" +
                "    adaptive=\"false\"\n" +
                "    time-dependent=\"false\">\n",
        )

        appendResponseDeclaration(builder)
        appendOutcomeDeclarations(builder)
        appendItemBody(builder)
        appendResponseProcessing(builder)
        appendModalFeedback(builder)
        builder.append("</qti-assessment-item>\n")
        return builder.toString()
    }

    private fun hasExplanation(): Boolean = question.explanation?.xml?.isNotBlank() == true

    private fun appendResponseDeclaration(builder: StringBuilder) {
        when (question.type) {
            QuestionType.DESCRIPTIVE -> {
                builder.append(
                    "  <qti-response-declaration identifier=\"RESPONSE\" cardinality=\"single\" base-type=\"string\"/>\n",
                )
            }
            QuestionType.CHOICE -> {
                builder.append(
                    "  <qti-response-declaration identifier=\"RESPONSE\" cardinality=\"single\" base-type=\"identifier\">\n",
                )
                val correctIndex = question.options.indexOfFirst { it.isCorrect }
                val correctId = "CHOICE_${correctIndex + 1}"
                builder.append("    <qti-correct-response>\n")
                builder.append("      <qti-value>$correctId</qti-value>\n")
                builder.append("    </qti-correct-response>\n")
                builder.append("  </qti-response-declaration>\n")
            }
            QuestionType.CLOZE -> {
                val blanks = question.prompt.clozeAnswers
                if (blanks.size == 1) {
                    builder.append(
                        "  <qti-response-declaration identifier=\"RESPONSE\" cardinality=\"single\" base-type=\"string\">\n",
                    )
                    builder.append("    <qti-correct-response>\n")
                    builder.append("      <qti-value>${escapeXml(blanks.first())}</qti-value>\n")
                    builder.append("    </qti-correct-response>\n")
                    builder.append("  </qti-response-declaration>\n")
                } else {
                    blanks.forEachIndexed { index, blank ->
                        val identifier = "RESPONSE_${index + 1}"
                        builder.append(
                            "  <qti-response-declaration identifier=\"$identifier\" cardinality=\"single\" base-type=\"string\">\n",
                        )
                        builder.append("    <qti-correct-response>\n")
                        builder.append("      <qti-value>${escapeXml(blank)}</qti-value>\n")
                        builder.append("    </qti-correct-response>\n")
                        builder.append("  </qti-response-declaration>\n")
                    }
                }
            }
        }
    }

    private fun appendOutcomeDeclarations(builder: StringBuilder) {
        if (!hasExplanation()) {
            return
        }
        builder.append("  <qti-outcome-declaration identifier=\"FEEDBACK\" cardinality=\"single\" base-type=\"identifier\"/>\n")
    }

    private fun appendItemBody(builder: StringBuilder) {
        builder.append("  <qti-item-body>\n")
        appendXml(builder, question.prompt.xml)
        when (question.type) {
            QuestionType.DESCRIPTIVE -> {
                builder.append("    <qti-extended-text-interaction response-identifier=\"RESPONSE\"/>\n")
            }
            QuestionType.CHOICE -> {
                builder.append("    <qti-choice-interaction response-identifier=\"RESPONSE\" max-choices=\"1\">\n")
                question.options.forEachIndexed { index, option ->
                    val identifier = "CHOICE_${index + 1}"
                    val content = option.contentXml.trim()
                    if (isBlockContent(content)) {
                        builder.append("      <qti-simple-choice identifier=\"$identifier\">\n")
                        appendXml(builder, content)
                        builder.append("      </qti-simple-choice>\n")
                    } else {
                        builder.append("      <qti-simple-choice identifier=\"$identifier\">")
                        builder.append(content)
                        builder.append("</qti-simple-choice>\n")
                    }
                }
                builder.append("    </qti-choice-interaction>\n")
            }
            QuestionType.CLOZE -> Unit
        }

        if (question.scoring.isNotEmpty()) {
            builder.append("    <qti-rubric-block view=\"scorer\">\n")
            question.scoring.forEach { criterion ->
                val points = criterion.points.stripTrailingZeros().toPlainString()
                builder.append("      <qti-p>[${escapeXml(points)}] ")
                builder.append(criterion.criterionXml)
                builder.append("</qti-p>\n")
            }
            builder.append("    </qti-rubric-block>\n")
        }
        builder.append("  </qti-item-body>\n")
    }

    private fun appendResponseProcessing(builder: StringBuilder) {
        if (!hasExplanation()) {
            return
        }
        builder.append("  <qti-response-processing>\n")
        builder.append("    <qti-set-outcome-value identifier=\"FEEDBACK\">\n")
        builder.append("      <qti-base-value base-type=\"identifier\">EXPLANATION</qti-base-value>\n")
        builder.append("    </qti-set-outcome-value>\n")
        builder.append("  </qti-response-processing>\n")
    }

    private fun appendModalFeedback(builder: StringBuilder) {
        val explanation = question.explanation
        if (explanation == null || explanation.xml.isBlank()) {
            return
        }
        builder.append("  <qti-modal-feedback outcome-identifier=\"FEEDBACK\" identifier=\"EXPLANATION\" show-hide=\"show\">\n")
        builder.append("    <qti-content-body>\n")
        appendXml(builder, explanation.xml)
        builder.append("    </qti-content-body>\n")
        builder.append("  </qti-modal-feedback>\n")
    }

    private fun appendXml(builder: StringBuilder, xml: String) {
        if (xml.isBlank()) {
            return
        }
        val trimmed = xml.trimEnd()
        builder.append(trimmed)
        if (!trimmed.endsWith("\n")) {
            builder.append("\n")
        }
    }

    private fun isBlockContent(xml: String): Boolean {
        if (xml.contains("<qti-p") || xml.contains("<qti-ul") || xml.contains("<qti-ol")) {
            return true
        }
        if (xml.contains("<qti-blockquote") || xml.contains("<qti-pre") || xml.contains("<qti-table")) {
            return true
        }
        if (xml.contains("<qti-hr") || xml.contains("<qti-h")) {
            return true
        }
        return xml.contains("<qti-li")
    }
}
