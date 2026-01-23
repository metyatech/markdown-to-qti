package com.metyatech.markdowntoqti

import java.math.BigDecimal

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
    val text: String,
    val isCorrect: Boolean,
)

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
    val prompt: String,
    val answer: String? = null,
    val explanation: String? = null,
    val options: List<ChoiceOption> = emptyList(),
    val blanks: List<ClozeBlank> = emptyList(),
    val scoring: List<ScoringCriterion> = emptyList(),
)

fun convertMarkdownToQti(markdown: String, fixtureId: String): String {
    val question = parseMarkdownQuestion(markdown, fixtureId)
    return QtiBuilder(question).build()
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

private fun parseMarkdownQuestion(markdown: String, identifier: String): MarkdownQuestion {
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
    val prompt = normalizeSectionText(promptSection, "Prompt")
    if (prompt.isBlank()) {
        throw IllegalArgumentException("Prompt must not be empty")
    }

    val answer = sections["Answer"]?.let { normalizeSectionText(it, "Answer") }
    val explanation = sections["Explanation"]?.let { normalizeSectionText(it, "Explanation") }

    val scoring = sections["Scoring"]?.let { parseScoringSection(it) } ?: emptyList()

    return when (type) {
        QuestionType.DESCRIPTIVE -> MarkdownQuestion(
            identifier = identifier,
            title = title,
            type = type,
            prompt = prompt,
            answer = answer,
            explanation = explanation,
            scoring = scoring,
        )
        QuestionType.CHOICE -> {
            val optionsSection = sections["Options"]
                ?: throw IllegalArgumentException("Missing ## Options section")
            val options = parseOptions(optionsSection)
            MarkdownQuestion(
                identifier = identifier,
                title = title,
                type = type,
                prompt = prompt,
                explanation = explanation,
                options = options,
                scoring = scoring,
            )
        }
        QuestionType.CLOZE -> {
            val parts = parseClozePrompt(prompt)
            val blanks = parts.filterIsInstance<ClozePart.Blank>().map { it.blank }
            if (blanks.isEmpty()) {
                throw IllegalArgumentException("Cloze prompt must include at least one blank")
            }
            MarkdownQuestion(
                identifier = identifier,
                title = title,
                type = type,
                prompt = prompt,
                explanation = explanation,
                blanks = blanks,
                scoring = scoring,
            )
        }
    }
}

private fun parseOptions(lines: List<String>): List<ChoiceOption> {
    val options = mutableListOf<ChoiceOption>()
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
        options.add(ChoiceOption(text, isCorrect))
    }

    if (options.isEmpty()) {
        throw IllegalArgumentException("Options must not be empty")
    }
    val correctCount = options.count { it.isCorrect }
    if (correctCount != 1) {
        throw IllegalArgumentException("Choice question must have exactly one correct option")
    }

    return options
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
                builder.append("    <qti-p>${escapeXml(question.prompt)}</qti-p>\n")
                builder.append("    <qti-extended-text-interaction response-identifier=\"RESPONSE\"/>\n")
                question.answer?.let { answerText ->
                    builder.append("    <qti-rubric-block view=\"candidate\">\n")
                    builder.append("      <qti-p>${escapeXml(answerText)}</qti-p>\n")
                    builder.append("    </qti-rubric-block>\n")
                }
                appendExplanation(builder, question.explanation)
            }
            QuestionType.CHOICE -> {
                builder.append("    <qti-p>${escapeXml(question.prompt)}</qti-p>\n")
                builder.append("    <qti-choice-interaction response-identifier=\"RESPONSE\" max-choices=\"1\">\n")
                question.options.forEachIndexed { index, option ->
                    val identifier = "CHOICE_${index + 1}"
                    builder.append(
                        "      <qti-simple-choice identifier=\"$identifier\">${escapeXml(option.text)}</qti-simple-choice>\n",
                    )
                }
                builder.append("    </qti-choice-interaction>\n")
                appendExplanation(builder, question.explanation)
            }
            QuestionType.CLOZE -> {
                val parts = parseClozePrompt(question.prompt)
                val responseIds = if (question.blanks.size == 1) {
                    listOf("RESPONSE")
                } else {
                    question.blanks.indices.map { "RESPONSE_${it + 1}" }
                }
                builder.append("    <qti-p>")
                var blankIndex = 0
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
                builder.append("</qti-p>\n")
                appendExplanation(builder, question.explanation)
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

    private fun appendExplanation(builder: StringBuilder, explanation: String?) {
        if (explanation.isNullOrBlank()) {
            return
        }
        builder.append("    <qti-rubric-block view=\"candidate\">\n")
        builder.append("      <qti-p>${escapeXml(explanation)}</qti-p>\n")
        builder.append("    </qti-rubric-block>\n")
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
