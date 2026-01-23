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
    val criterion: String,
)

private data class ChoiceOption(
    val parts: List<InlineContent>,
    val isCorrect: Boolean,
)

private sealed class InlineContent {
    data class Text(val value: String) : InlineContent()
    data class Image(
        val source: String,
        val alt: String,
        val title: String?,
    ) : InlineContent()
}

private data class ClozeBlank(
    val answer: String,
)

private sealed class ClozePart {
    data class Text(val value: String) : ClozePart()
    data class Blank(val blank: ClozeBlank) : ClozePart()
}

private data class MarkdownQuestion(
    val identifier: String,
    val title: String,
    val type: QuestionType,
    val promptParts: List<InlineContent>,
    val explanationParts: List<InlineContent>? = null,
    val options: List<ChoiceOption> = emptyList(),
    val blanks: List<ClozeBlank> = emptyList(),
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

private data class MarkdownParseResult(
    val question: MarkdownQuestion,
    val imageSources: List<String>,
)

fun convertMarkdownToQti(markdown: String, fixtureId: String): String {
    val parsed = parseMarkdownQuestion(markdown, fixtureId)
    return QtiBuilder(parsed.question).build()
}

fun convertMarkdownToQtiWithAssets(markdown: String, fixtureId: String, sourcePath: Path): QtiConversionResult {
    val parsed = parseMarkdownQuestion(markdown, fixtureId)
    val localImages = resolveLocalImages(parsed.imageSources, sourcePath)
    return QtiConversionResult(QtiBuilder(parsed.question).build(), localImages)
}

fun parseScoringSection(lines: List<String>): List<ScoringCriterion> {
    val criteria = mutableListOf<ScoringCriterion>()
    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?):\s*(.*)$""")

    lines.forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return@forEach
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
        criteria.add(ScoringCriterion(BigDecimal(points), criterion))
    }

    return criteria
}

private fun parseMarkdownQuestion(markdown: String, identifier: String): MarkdownParseResult {
    val normalized = markdown.replace("\r\n", "\n")
    val lines = normalized.split("\n")
    var index = 0

    fun nextNonEmptyLine(): String? {
        while (index < lines.size) {
            val line = lines[index]
            index += 1
            if (line.isNotBlank()) {
                return line
            }
        }
        return null
    }

    val titleLine = nextNonEmptyLine()
        ?: throw IllegalArgumentException("Missing title heading")
    if (!titleLine.startsWith("# ")) {
        throw IllegalArgumentException("Title must start with '# '")
    }
    val title = titleLine.removePrefix("# ").trim()
    if (title.isBlank()) {
        throw IllegalArgumentException("Title must not be empty")
    }

    val sections = mutableMapOf<String, List<String>>()
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
        while (index < lines.size && !lines[index].startsWith("## ")) {
            content.add(lines[index])
            index += 1
        }
        sections[heading] = content
    }

    val typeSection = sections["Type"]
        ?: throw IllegalArgumentException("Missing ## Type section")
    val typeValue = typeSection.firstOrNull { it.isNotBlank() }
        ?.trim()
        ?: throw IllegalArgumentException("Type value missing")
    val type = when (typeValue) {
        "descriptive" -> QuestionType.DESCRIPTIVE
        "choice" -> QuestionType.CHOICE
        "cloze" -> QuestionType.CLOZE
        else -> throw IllegalArgumentException("Unknown question type: $typeValue")
    }

    val promptSection = sections["Prompt"]
        ?: throw IllegalArgumentException("Missing ## Prompt section")
    val promptText = normalizeSectionText(promptSection, "Prompt")
    val promptParse = parseInlineContent(promptText, "Prompt")
    ensureSectionHasContent(promptParse.parts, "Prompt")

    val explanationParse = sections["Explanation"]?.let { sectionLines ->
        val explanationText = normalizeSectionText(sectionLines, "Explanation")
        val parsed = parseInlineContent(explanationText, "Explanation")
        ensureSectionHasContent(parsed.parts, "Explanation")
        parsed
    }

    val scoring = sections["Scoring"]?.let { parseScoringSection(it) } ?: emptyList()

    val imageSources = mutableListOf<String>()
    imageSources.addAll(promptParse.imageSources)
    explanationParse?.let { imageSources.addAll(it.imageSources) }

    val question = when (type) {
        QuestionType.DESCRIPTIVE -> MarkdownQuestion(
            identifier = identifier,
            title = title,
            type = type,
            promptParts = promptParse.parts,
            explanationParts = explanationParse?.parts,
            scoring = scoring,
        )
        QuestionType.CHOICE -> {
            val optionsSection = sections["Options"]
                ?: throw IllegalArgumentException("Missing ## Options section")
            val optionsParse = parseOptions(optionsSection)
            optionsParse.imageSources.forEach { imageSources.add(it) }
            MarkdownQuestion(
                identifier = identifier,
                title = title,
                type = type,
                promptParts = promptParse.parts,
                explanationParts = explanationParse?.parts,
                options = optionsParse.options,
                scoring = scoring,
            )
        }
        QuestionType.CLOZE -> {
            val blanks = collectClozeBlanks(promptParse.parts)
            if (blanks.isEmpty()) {
                throw IllegalArgumentException("Cloze prompt must include at least one blank")
            }
            MarkdownQuestion(
                identifier = identifier,
                title = title,
                type = type,
                promptParts = promptParse.parts,
                explanationParts = explanationParse?.parts,
                blanks = blanks,
                scoring = scoring,
            )
        }
    }

    return MarkdownParseResult(question, imageSources.distinct())
}

private data class OptionsParseResult(
    val options: List<ChoiceOption>,
    val imageSources: List<String>,
)

private fun parseOptions(lines: List<String>): OptionsParseResult {
    val options = mutableListOf<ChoiceOption>()
    val imageSources = mutableListOf<String>()
    val pattern = Regex("""^-\s*\[(x| )]\s+(.+)$""")

    lines.forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return@forEach
        }
        val match = pattern.matchEntire(line)
            ?: throw IllegalArgumentException("Invalid option format: $rawLine")
        val isCorrect = match.groupValues[1] == "x"
        val text = match.groupValues[2].trim()
        if (text.isBlank()) {
            throw IllegalArgumentException("Option text must not be empty")
        }
        val parsed = parseInlineContent(text, "Options")
        ensureSectionHasContent(parsed.parts, "Options")
        imageSources.addAll(parsed.imageSources)
        options.add(ChoiceOption(parsed.parts, isCorrect))
    }

    if (options.isEmpty()) {
        throw IllegalArgumentException("Options must not be empty")
    }
    val correctCount = options.count { it.isCorrect }
    if (correctCount != 1) {
        throw IllegalArgumentException("Choice question must have exactly one correct option")
    }

    return OptionsParseResult(options, imageSources)
}

private data class InlineParseResult(
    val parts: List<InlineContent>,
    val imageSources: List<String>,
)

private fun parseInlineContent(text: String, sectionName: String): InlineParseResult {
    val parts = mutableListOf<InlineContent>()
    val imageSources = mutableListOf<String>()
    var cursor = 0

    fun appendText(value: String) {
        if (value.isNotEmpty()) {
            parts.add(InlineContent.Text(value))
        }
    }

    while (cursor < text.length) {
        val start = text.indexOf("![", cursor)
        if (start == -1) {
            appendText(text.substring(cursor))
            break
        }
        if (start > cursor) {
            appendText(text.substring(cursor, start))
        }

        val altStart = start + 2
        val altEnd = text.indexOf("](", altStart)
        if (altEnd == -1) {
            throw IllegalArgumentException("Invalid image syntax in $sectionName: missing ']('")
        }
        val altText = text.substring(altStart, altEnd)
        val closeParen = text.indexOf(')', altEnd + 2)
        if (closeParen == -1) {
            throw IllegalArgumentException("Invalid image syntax in $sectionName: missing ')' ")
        }
        val inner = text.substring(altEnd + 2, closeParen).trim()
        if (inner.isEmpty()) {
            throw IllegalArgumentException("Image path must not be empty in $sectionName")
        }

        val (rawPath, title) = parseImagePathAndTitle(inner, sectionName)
        val source = stripAngleBrackets(rawPath)
        if (source.isBlank()) {
            throw IllegalArgumentException("Image path must not be empty in $sectionName")
        }
        parts.add(InlineContent.Image(source = source, alt = altText, title = title))
        imageSources.add(source)

        cursor = closeParen + 1
    }

    return InlineParseResult(parts, imageSources)
}

private fun parseImagePathAndTitle(value: String, sectionName: String): Pair<String, String?> {
    val doubleQuoteMatch = Regex("""^(.+?)\s+\"(.*)\"$""").matchEntire(value)
    if (doubleQuoteMatch != null) {
        val path = doubleQuoteMatch.groupValues[1].trim()
        return path to doubleQuoteMatch.groupValues[2]
    }

    val singleQuoteMatch = Regex("""^(.+?)\s+'(.*)'$""").matchEntire(value)
    if (singleQuoteMatch != null) {
        val path = singleQuoteMatch.groupValues[1].trim()
        return path to singleQuoteMatch.groupValues[2]
    }

    if (value.any { it.isWhitespace() } && !(value.startsWith("<") && value.endsWith(">"))) {
        throw IllegalArgumentException("Image path must not contain spaces unless wrapped in <> in $sectionName")
    }

    return value.trim() to null
}

private fun stripAngleBrackets(value: String): String =
    if (value.startsWith("<") && value.endsWith(">") && value.length > 2) {
        value.substring(1, value.length - 1)
    } else {
        value
    }

private fun ensureSectionHasContent(parts: List<InlineContent>, sectionName: String) {
    val hasContent = parts.any { part ->
        when (part) {
            is InlineContent.Image -> true
            is InlineContent.Text -> part.value.isNotBlank()
        }
    }
    if (!hasContent) {
        throw IllegalArgumentException("$sectionName section must not be empty")
    }
}

private fun collectClozeBlanks(parts: List<InlineContent>): List<ClozeBlank> {
    return parts.flatMap { part ->
        when (part) {
            is InlineContent.Text -> parseClozePrompt(part.value)
                .filterIsInstance<ClozePart.Blank>()
                .map { it.blank }
            is InlineContent.Image -> emptyList()
        }
    }
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

private fun isRemoteImagePath(source: String): Boolean {
    val normalized = source.lowercase()
    return normalized.startsWith("http://") ||
        normalized.startsWith("https://") ||
        normalized.startsWith("data:")
}

private fun parseClozePrompt(prompt: String): List<ClozePart> {
    val parts = mutableListOf<ClozePart>()
    val buffer = StringBuilder()
    var index = 0

    fun flushText() {
        if (buffer.isNotEmpty()) {
            parts.add(ClozePart.Text(buffer.toString()))
            buffer.clear()
        }
    }

    while (index < prompt.length) {
        if (prompt.startsWith("\\{{", index)) {
            buffer.append("{{")
            index += 3
            continue
        }
        if (prompt.startsWith("\\}}", index)) {
            buffer.append("}}")
            index += 3
            continue
        }
        if (prompt.startsWith("{{", index)) {
            val endIndex = prompt.indexOf("}}", index + 2)
            if (endIndex == -1) {
                throw IllegalArgumentException("Unclosed cloze blank in prompt")
            }
            val answer = prompt.substring(index + 2, endIndex)
            if (answer.isBlank()) {
                throw IllegalArgumentException("Cloze blank must not be empty")
            }
            flushText()
            parts.add(ClozePart.Blank(ClozeBlank(answer.trim())))
            index = endIndex + 2
            continue
        }
        buffer.append(prompt[index])
        index += 1
    }

    flushText()
    return parts
}

private fun normalizeSectionText(lines: List<String>, sectionName: String): String {
    val text = lines
        .dropWhile { it.isBlank() }
        .dropLastWhile { it.isBlank() }
        .joinToString("\n") { it.trimEnd() }
        .trim()
    if (text.isBlank()) {
        throw IllegalArgumentException("$sectionName section must not be empty")
    }
    return text
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
        appendItemBody(builder)
        builder.append("</qti-assessment-item>\n")
        return builder.toString()
    }

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
                val blanks = question.blanks
                if (blanks.size == 1) {
                    builder.append(
                        "  <qti-response-declaration identifier=\"RESPONSE\" cardinality=\"single\" base-type=\"string\">\n",
                    )
                    builder.append("    <qti-correct-response>\n")
                    builder.append("      <qti-value>${escapeXml(blanks.first().answer)}</qti-value>\n")
                    builder.append("    </qti-correct-response>\n")
                    builder.append("  </qti-response-declaration>\n")
                } else {
                    blanks.forEachIndexed { index, blank ->
                        val identifier = "RESPONSE_${index + 1}"
                        builder.append(
                            "  <qti-response-declaration identifier=\"$identifier\" cardinality=\"single\" base-type=\"string\">\n",
                        )
                        builder.append("    <qti-correct-response>\n")
                        builder.append("      <qti-value>${escapeXml(blank.answer)}</qti-value>\n")
                        builder.append("    </qti-correct-response>\n")
                        builder.append("  </qti-response-declaration>\n")
                    }
                }
            }
        }
    }

    private fun appendItemBody(builder: StringBuilder) {
        builder.append("  <qti-item-body>\n")
        when (question.type) {
            QuestionType.DESCRIPTIVE -> {
                builder.append("    <qti-p>")
                appendInlineContent(builder, question.promptParts)
                builder.append("</qti-p>\n")
                builder.append("    <qti-extended-text-interaction response-identifier=\"RESPONSE\"/>\n")
                appendExplanation(builder, question.explanationParts)
            }
            QuestionType.CHOICE -> {
                builder.append("    <qti-p>")
                appendInlineContent(builder, question.promptParts)
                builder.append("</qti-p>\n")
                builder.append("    <qti-choice-interaction response-identifier=\"RESPONSE\" max-choices=\"1\">\n")
                question.options.forEachIndexed { index, option ->
                    val identifier = "CHOICE_${index + 1}"
                    builder.append(
                        "      <qti-simple-choice identifier=\"$identifier\">",
                    )
                    appendInlineContent(builder, option.parts)
                    builder.append("</qti-simple-choice>\n")
                }
                builder.append("    </qti-choice-interaction>\n")
                appendExplanation(builder, question.explanationParts)
            }
            QuestionType.CLOZE -> {
                val responseIds = if (question.blanks.size == 1) {
                    listOf("RESPONSE")
                } else {
                    question.blanks.indices.map { "RESPONSE_${it + 1}" }
                }
                builder.append("    <qti-p>")
                var blankIndex = 0
                question.promptParts.forEach { inlinePart ->
                    when (inlinePart) {
                        is InlineContent.Text -> {
                            val parts = parseClozePrompt(inlinePart.value)
                            parts.forEach { part ->
                                when (part) {
                                    is ClozePart.Text -> builder.append(escapeXml(part.value))
                                    is ClozePart.Blank -> {
                                        val responseId = responseIds[blankIndex]
                                        builder.append("<qti-text-entry-interaction response-identifier=\"$responseId\"/>")
                                        blankIndex += 1
                                    }
                                }
                            }
                        }
                        is InlineContent.Image -> appendImage(builder, inlinePart)
                    }
                }
                builder.append("</qti-p>\n")
                appendExplanation(builder, question.explanationParts)
            }
        }

        if (question.scoring.isNotEmpty()) {
            builder.append("    <qti-rubric-block view=\"scorer\">\n")
            question.scoring.forEach { criterion ->
                val points = criterion.points.stripTrailingZeros().toPlainString()
                builder.append("      <qti-p>[${escapeXml(points)}] ${escapeXml(criterion.criterion)}</qti-p>\n")
            }
            builder.append("    </qti-rubric-block>\n")
        }
        builder.append("  </qti-item-body>\n")
    }

    private fun appendExplanation(builder: StringBuilder, explanationParts: List<InlineContent>?) {
        if (explanationParts == null || explanationParts.isEmpty()) {
            return
        }
        builder.append("    <qti-rubric-block view=\"candidate\">\n")
        builder.append("      <qti-p>")
        appendInlineContent(builder, explanationParts)
        builder.append("</qti-p>\n")
        builder.append("    </qti-rubric-block>\n")
    }

    private fun appendInlineContent(builder: StringBuilder, parts: List<InlineContent>) {
        parts.forEach { part ->
            when (part) {
                is InlineContent.Text -> builder.append(escapeXml(part.value))
                is InlineContent.Image -> appendImage(builder, part)
            }
        }
    }

    private fun appendImage(builder: StringBuilder, image: InlineContent.Image) {
        builder.append("<qti-img src=\"${escapeXml(image.source)}\" alt=\"${escapeXml(image.alt)}\"")
        if (!image.title.isNullOrBlank()) {
            builder.append(" title=\"${escapeXml(image.title)}\"")
        }
        builder.append("/>")
    }
}

private fun escapeXml(value: String): String =
    buildString(value.length) {
        value.forEach { char ->
            when (char) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(char)
            }
        }
    }
