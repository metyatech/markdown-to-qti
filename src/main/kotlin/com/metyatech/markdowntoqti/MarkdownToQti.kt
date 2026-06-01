@file:Suppress("MagicNumber", "MaximumLineLength", "MaxLineLength")

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
    val timeBudgetSeconds: Int?,
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
    val timeBudgetSeconds: Int?,
)

internal data class SectionContent(
    val name: String,
    val lines: List<String>,
    val headingLine: Int,
    val startLine: Int,
) {
    fun text(): String = lines.joinToString("\n")

    fun isBlank(): Boolean = lines.all { it.isBlank() }
}

private data class MarkdownParseResult(
    val question: MarkdownQuestion,
    val imageSources: List<String>,
)

private data class QuestionFrontmatter(
    val questionType: QuestionType,
    val timeBudgetSeconds: Int,
)

fun convertMarkdownToQti(
    markdown: String,
    fixtureId: String,
): String {
    val parsed = parseMarkdownQuestion(markdown, fixtureId, null)
    return QtiBuilder(parsed.question).build()
}

fun convertMarkdownToQtiWithAssets(
    markdown: String,
    fixtureId: String,
    sourcePath: Path,
): QtiConversionResult {
    val parsed = parseMarkdownQuestion(markdown, fixtureId, sourcePath)
    val localImages = resolveLocalImages(parsed.imageSources, sourcePath)
    return QtiConversionResult(QtiBuilder(parsed.question).build(), localImages, parsed.question.timeBudgetSeconds)
}

private const val MIN_FENCE_LENGTH = 3
private const val MAX_LEADING_SPACES = 3

@Suppress("LongMethod", "CyclomaticComplexMethod", "ThrowsCount")
private fun parseMarkdownQuestion(
    markdown: String,
    identifier: String,
    sourcePath: Path?,
): MarkdownParseResult {
    val normalized = markdown.replace("\r\n", "\n")
    val lines = normalized.split("\n")
    var index = 0
    val frontmatter =
        if (lines.firstOrNull()?.trim() == "---") {
            val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
            require(endIndex != -1) { "Missing closing frontmatter delimiter" }
            val frontmatterLines = lines.subList(1, endIndex + 1)
            index = endIndex + 2
            parseQuestionFrontmatter(frontmatterLines, sourcePath)
        } else {
            null
        }

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

    val titleLine =
        nextNonEmptyLine()
            ?: throw IllegalArgumentException("Missing title heading")
    require(titleLine.first.startsWith("# ")) { "Title must start with '# '" }
    val title = titleLine.first.removePrefix("# ").trim()
    require(title.isNotBlank()) { "Title must not be empty" }

    val sectionsInOrder = mutableListOf<SectionContent>()
    val sectionsByName = mutableMapOf<String, SectionContent>()
    while (index < lines.size) {
        val line = lines[index]
        if (line.isBlank()) {
            index += 1
            continue
        }
        require(line.startsWith("## ")) { "Unexpected content outside section: $line" }
        val headingLine = index + 1
        val heading = line.removePrefix("## ").trim()
        if (!ALLOWED_SECTION_NAMES.contains(heading)) {
            throw schemaError("Unknown section heading: $heading", sourcePath, headingLine)
        }
        if (sectionsByName.containsKey(heading)) {
            throw schemaError("Duplicate section heading: $heading", sourcePath, headingLine)
        }
        index += 1

        val content = mutableListOf<String>()
        val startLine = index + 1
        var fence: FenceState? = null
        while (index < lines.size) {
            val current = lines[index]
            if (fence == null && current.startsWith("## ")) {
                break
            }
            fence = updateFenceState(current, fence)
            content.add(lines[index])
            index += 1
        }
        val section = SectionContent(heading, content, headingLine, startLine)
        sectionsInOrder.add(section)
        sectionsByName[heading] = section
    }

    val type =
        if (frontmatter != null) {
            if (sectionsByName.containsKey("Type")) {
                throw schemaError(
                    "## Type is deprecated and not allowed with frontmatter",
                    sourcePath,
                    sectionsByName["Type"]?.headingLine,
                )
            }
            frontmatter.questionType
        } else {
            parseLegacyType(sectionsByName["Type"], sourcePath)
        }

    validateSectionOrder(sectionsInOrder, type, sourcePath, frontmatter != null)

    val renderer = MarkdownQtiRenderer()

    val promptSection =
        sectionsByName["Prompt"]
            ?: throw IllegalArgumentException("Missing ## Prompt section")
    require(promptSection.lines.any { it.isNotBlank() }) { "Prompt section must not be empty" }
    validateNoH1H2HeadingsInContent(promptSection, sourcePath)
    val promptContext = RenderContext("Prompt", sourcePath, promptSection.startLine)
    val promptRender =
        renderer.renderBlocks(
            markdown = promptSection.text(),
            context = promptContext,
            clozeHandling = if (type == QuestionType.CLOZE) ClozeHandling.ENABLED else ClozeHandling.DISABLED,
        )
    if (type == QuestionType.CLOZE) {
        require(promptRender.clozeBlanks.isNotEmpty()) { "Cloze prompt must include at least one blank" }
    }

    val explanationRender =
        sectionsByName["Explanation"]?.let { section ->
            require(section.lines.any { it.isNotBlank() }) { "Explanation section must not be empty" }
            validateNoH1H2HeadingsInContent(section, sourcePath)
            val context = RenderContext("Explanation", sourcePath, section.startLine)
            renderer.renderBlocks(section.text(), context, ClozeHandling.DISABLED)
        }

    val scoring =
        sectionsByName["Scoring"]?.let { section ->
            parseScoringSection(section, renderer, sourcePath)
        } ?: emptyList()

    val optionsSection = sectionsByName["Options"]
    val options =
        if (type == QuestionType.CHOICE) {
            val optionsRequiredSection =
                optionsSection
                    ?: throw IllegalArgumentException("Missing ## Options section")
            require(optionsRequiredSection.lines.any { it.isNotBlank() }) { "Options must not be empty" }
            validateNoH1H2HeadingsInContent(optionsRequiredSection, sourcePath)
            val context = RenderContext("Options", sourcePath, optionsRequiredSection.startLine)
            val renderedOptions = renderer.renderChoiceOptions(optionsRequiredSection.text(), context)
            require(renderedOptions.isNotEmpty()) { "Options must not be empty" }
            val correctCount = renderedOptions.count { it.isCorrect }
            require(correctCount == 1) { "Choice question must have exactly one correct option" }
            renderedOptions
        } else {
            if (optionsSection != null) {
                throw schemaError(
                    "## Options is only allowed for type 'choice'",
                    sourcePath,
                    optionsSection.headingLine,
                )
            }
            emptyList()
        }

    val imageSources = mutableSetOf<String>()
    imageSources.addAll(promptRender.localImages)
    explanationRender?.let { imageSources.addAll(it.localImages) }
    options.forEach { option -> imageSources.addAll(option.localImages) }

    val question =
        MarkdownQuestion(
            identifier = identifier,
            title = title,
            type = type,
            timeBudgetSeconds = frontmatter?.timeBudgetSeconds,
            prompt = promptRender,
            explanation = explanationRender,
            options = options,
            scoring = scoring,
        )

    return MarkdownParseResult(question, imageSources.toList())
}

private fun parseQuestionFrontmatter(
    lines: List<String>,
    sourcePath: Path?,
): QuestionFrontmatter {
    val values = parseSimpleYamlMap(lines, sourcePath, lineOffset = 2)
    val typeValue = values["question_type"] ?: throw schemaError("Missing required frontmatter: question_type", sourcePath, 2)
    val questionType =
        when (typeValue) {
            "descriptive" -> QuestionType.DESCRIPTIVE
            "choice" -> QuestionType.CHOICE
            "cloze" -> QuestionType.CLOZE
            "multi", "order", "match" -> throw IllegalArgumentException("Unsupported question_type: $typeValue")
            else -> throw IllegalArgumentException("Unknown question_type: $typeValue")
        }
    val timeBudgetSeconds =
        parsePositiveInt(values["time_budget_seconds"], "time_budget_seconds", sourcePath, 2)
    return QuestionFrontmatter(questionType, timeBudgetSeconds)
}

private fun parseLegacyType(
    typeSection: SectionContent?,
    sourcePath: Path?,
): QuestionType {
    val section = typeSection ?: throw IllegalArgumentException("Missing ## Type section")
    val firstTypeLine =
        section.lines.firstOrNull()
            ?: throw schemaError("Type value missing", sourcePath, section.startLine)
    if (firstTypeLine.isBlank()) {
        throw schemaError("Type value must be on the line immediately after ## Type", sourcePath, section.startLine)
    }
    val typeValue = firstTypeLine.trim()
    if (section.lines.drop(1).any { it.isNotBlank() }) {
        throw schemaError("Type section must contain only a single word", sourcePath, section.startLine)
    }
    return when (typeValue) {
        "descriptive" -> QuestionType.DESCRIPTIVE
        "choice" -> QuestionType.CHOICE
        "cloze" -> QuestionType.CLOZE
        else -> throw IllegalArgumentException("Unknown question type: $typeValue")
    }
}

private fun parseSimpleYamlMap(
    lines: List<String>,
    sourcePath: Path?,
    lineOffset: Int,
): Map<String, String> {
    val values = mutableMapOf<String, String>()
    lines.forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#")) {
            return@forEachIndexed
        }
        if (rawLine.firstOrNull()?.isWhitespace() == true) {
            throw schemaError("Frontmatter must be a flat YAML map", sourcePath, lineOffset + index)
        }
        val separator = line.indexOf(':')
        if (separator <= 0) {
            throw schemaError("Invalid frontmatter entry: $line", sourcePath, lineOffset + index)
        }
        val key = line.substring(0, separator).trim()
        val value = line.substring(separator + 1).trim().trim('"', '\'')
        require(key.isNotBlank()) { "Frontmatter key must not be empty" }
        require(!values.containsKey(key)) { "Duplicate frontmatter key: $key" }
        values[key] = value
    }
    return values
}

internal fun parsePositiveInt(
    value: String?,
    fieldName: String,
    sourcePath: Path?,
    line: Int?,
): Int {
    val raw = value ?: throw schemaError("Missing required value: $fieldName", sourcePath, line)
    val parsed = raw.toIntOrNull() ?: throw schemaError("$fieldName must be a positive integer", sourcePath, line)
    if (parsed <= 0) {
        throw schemaError("$fieldName must be a positive integer", sourcePath, line)
    }
    return parsed
}

internal fun parseScoringSection(
    section: SectionContent,
    renderer: MarkdownQtiRenderer,
    sourcePath: Path?,
): List<ScoringCriterion> {
    val criteria = mutableListOf<ScoringCriterion>()
    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?):\s*(.*)$""")

    section.lines.forEachIndexed { index, rawLine ->
        if (rawLine.isBlank()) {
            return@forEachIndexed
        }
        if (rawLine.firstOrNull()?.isWhitespace() == true) {
            throw schemaError(
                "Scoring must be a single flat list (no indentation)",
                sourcePath,
                section.startLine + index,
            )
        }
        if (!rawLine.startsWith("- ")) {
            throw schemaError(
                "Scoring section must be a Markdown list with '- <points>: <criterion>' items",
                sourcePath,
                section.startLine + index,
            )
        }
        val content = rawLine.removePrefix("- ").trim()
        val match =
            pattern.matchEntire(content)
                ?: throw IllegalArgumentException("Invalid scoring points in line: $rawLine")
        val points = match.groupValues[1]
        val criterion = match.groupValues[2].trim()
        require(criterion.isNotBlank()) { "Scoring criterion must not be empty: $rawLine" }
        val context = RenderContext("Scoring", sourcePath, section.startLine + index)
        val rendered = renderer.renderInline(criterion, context, ClozeHandling.DISABLED)
        criteria.add(ScoringCriterion(BigDecimal(points), rendered.xml))
    }

    require(criteria.isNotEmpty()) { "Scoring section must not be empty" }
    return criteria
}

@Suppress("ThrowsCount")
private fun validateSectionOrder(
    sections: List<SectionContent>,
    type: QuestionType,
    sourcePath: Path?,
    usesFrontmatter: Boolean,
) {
    require(sections.isNotEmpty()) {
        if (usesFrontmatter) {
            "Missing ## Prompt section"
        } else {
            "Missing ## Type section"
        }
    }
    val typeIndex =
        if (usesFrontmatter) {
            -1
        } else {
            sections.indexOfFirst { it.name == "Type" }
        }
    if (!usesFrontmatter && typeIndex != 0) {
        val line = sections.firstOrNull()?.headingLine
        throw schemaError("First section must be ## Type", sourcePath, line)
    }
    val promptIndex = sections.indexOfFirst { it.name == "Prompt" }
    if (promptIndex == -1) {
        return
    }
    if (!usesFrontmatter && promptIndex < typeIndex) {
        throw schemaError("## Prompt must appear after ## Type", sourcePath, sections[promptIndex].headingLine)
    }
    if (type == QuestionType.CHOICE) {
        val optionsIndex = sections.indexOfFirst { it.name == "Options" }
        if (optionsIndex != -1 && optionsIndex < promptIndex) {
            throw schemaError("## Options must appear after ## Prompt", sourcePath, sections[optionsIndex].headingLine)
        }
        val explanationIndex = sections.indexOfFirst { it.name == "Explanation" }
        if (explanationIndex != -1 && optionsIndex != -1 && explanationIndex < optionsIndex) {
            throw schemaError(
                "## Explanation must appear after ## Options",
                sourcePath,
                sections[explanationIndex].headingLine,
            )
        }
        val scoringIndex = sections.indexOfFirst { it.name == "Scoring" }
        if (scoringIndex != -1 && optionsIndex != -1 && scoringIndex < optionsIndex) {
            throw schemaError("## Scoring must appear after ## Options", sourcePath, sections[scoringIndex].headingLine)
        }
    } else {
        val explanationIndex = sections.indexOfFirst { it.name == "Explanation" }
        if (explanationIndex != -1 && explanationIndex < promptIndex) {
            throw schemaError(
                "## Explanation must appear after ## Prompt",
                sourcePath,
                sections[explanationIndex].headingLine,
            )
        }
        val scoringIndex = sections.indexOfFirst { it.name == "Scoring" }
        if (scoringIndex != -1 && scoringIndex < promptIndex) {
            throw schemaError("## Scoring must appear after ## Prompt", sourcePath, sections[scoringIndex].headingLine)
        }
    }
}

private fun validateNoH1H2HeadingsInContent(
    section: SectionContent,
    sourcePath: Path?,
) {
    var fence: FenceState? = null
    section.lines.forEachIndexed { index, rawLine ->
        fence = updateFenceState(rawLine, fence)
        if (fence != null) {
            return@forEachIndexed
        }

        val leadingSpaces = rawLine.takeWhile { it == ' ' }.length
        if (leadingSpaces > MAX_LEADING_SPACES) {
            return@forEachIndexed
        }
        val rest = rawLine.drop(leadingSpaces)
        if (rest.startsWith("# ") || rest.startsWith("## ")) {
            throw schemaError(
                "Headings inside ## ${section.name} must use '###' or deeper (found '${rest.take(3).trim()}')",
                sourcePath,
                section.startLine + index,
            )
        }
    }
}

private data class FenceState(
    val fenceChar: Char,
    val fenceLength: Int,
)

private fun updateFenceState(
    line: String,
    state: FenceState?,
): FenceState? {
    val leadingSpaces = line.takeWhile { it == ' ' }.length
    if (leadingSpaces > MAX_LEADING_SPACES) {
        return state
    }
    val rest = line.drop(leadingSpaces)
    val fenceChar = rest.firstOrNull()
    if (fenceChar != '`' && fenceChar != '~') {
        return state
    }
    val runLength = rest.takeWhile { it == fenceChar }.length
    if (runLength < MIN_FENCE_LENGTH) {
        return state
    }
    return if (state == null) {
        FenceState(fenceChar, runLength)
    } else {
        if (state.fenceChar == fenceChar && runLength >= state.fenceLength && rest.drop(runLength).trim().isEmpty()) {
            null
        } else {
            state
        }
    }
}

private fun schemaError(
    message: String,
    sourcePath: Path?,
    line: Int?,
): IllegalArgumentException {
    val suffix =
        when {
            sourcePath != null && line != null -> " (${sourcePath.toAbsolutePath()}:$line)"
            sourcePath != null -> " (${sourcePath.toAbsolutePath()})"
            line != null -> " (line $line)"
            else -> ""
        }
    return IllegalArgumentException(message + suffix)
}

private val ALLOWED_SECTION_NAMES =
    setOf(
        "Type",
        "Prompt",
        "Options",
        "Explanation",
        "Scoring",
    )

private fun resolveLocalImages(
    imageSources: List<String>,
    sourcePath: Path,
): List<LocalImage> {
    val sourceDir = sourcePath.parent
    return imageSources.mapNotNull { source ->
        if (isRemoteImagePath(source)) {
            return@mapNotNull null
        }
        val sourcePathValue = Path.of(source)
        require(!sourcePathValue.isAbsolute) {
            "Image path must be relative in ${sourcePath.toAbsolutePath()}: $source"
        }
        val resolvedSource = sourceDir.resolve(sourcePathValue).normalize()
        require(Files.exists(resolvedSource) && Files.isRegularFile(resolvedSource)) {
            "Image file not found in ${sourcePath.toAbsolutePath()}: $source"
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

private class QtiBuilder(
    private val question: MarkdownQuestion,
) {
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
                val blanks = question.prompt.clozeBlanks
                if (blanks.size == 1) {
                    builder.append(
                        "  <qti-response-declaration identifier=\"RESPONSE\" cardinality=\"single\" base-type=\"string\"",
                    )
                    appendClozeDeclarationBody(builder, blanks.first())
                } else {
                    blanks.forEachIndexed { index, blank ->
                        val identifier = "RESPONSE_${index + 1}"
                        builder.append(
                            "  <qti-response-declaration identifier=\"$identifier\" cardinality=\"single\" base-type=\"string\"",
                        )
                        appendClozeDeclarationBody(builder, blank)
                    }
                }
            }
        }
    }

    private fun appendClozeDeclarationBody(
        builder: StringBuilder,
        blank: ClozeBlank,
    ) {
        if (blank.kind == ClozeBlankKind.REGEX) {
            builder.append(" interpretation=\"regex\">\n")
        } else {
            builder.append(">\n")
        }
        builder.append("    <qti-correct-response>\n")
        builder.append("      <qti-value>${escapeXml(blank.answer)}</qti-value>\n")
        builder.append("    </qti-correct-response>\n")
        builder.append("  </qti-response-declaration>\n")
    }

    private fun appendOutcomeDeclarations(builder: StringBuilder) {
        if (question.scoring.isEmpty() && !hasExplanation()) {
            return
        }
        if (question.scoring.isNotEmpty()) {
            val maxScore = question.scoring.fold(BigDecimal.ZERO) { total, criterion -> total + criterion.points }
            val maxScoreValue = maxScore.stripTrailingZeros().toPlainString()
            builder.append(
                "  <qti-outcome-declaration identifier=\"SCORE\" cardinality=\"single\" base-type=\"float\"/>\n",
            )
            builder.append(
                "  <qti-outcome-declaration identifier=\"MAXSCORE\" cardinality=\"single\" base-type=\"float\">\n",
            )
            builder.append("    <qti-default-value>\n")
            builder.append("      <qti-value>${escapeXml(maxScoreValue)}</qti-value>\n")
            builder.append("    </qti-default-value>\n")
            builder.append("  </qti-outcome-declaration>\n")
        }
        if (hasExplanation()) {
            builder.append(
                "  <qti-outcome-declaration identifier=\"FEEDBACK\" cardinality=\"single\" base-type=\"identifier\"/>\n",
            )
        }
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
        builder.append(
            "  <qti-modal-feedback outcome-identifier=\"FEEDBACK\" identifier=\"EXPLANATION\" show-hide=\"show\">\n",
        )
        builder.append("    <qti-content-body>\n")
        appendXml(builder, explanation.xml)
        builder.append("    </qti-content-body>\n")
        builder.append("  </qti-modal-feedback>\n")
    }

    private fun appendXml(
        builder: StringBuilder,
        xml: String,
    ) {
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
