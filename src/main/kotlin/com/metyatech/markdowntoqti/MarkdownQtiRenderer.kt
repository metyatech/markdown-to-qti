package com.metyatech.markdowntoqti

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableBody
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableHead
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.SourceSpan
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.nio.file.Path

internal data class RenderContext(
    val sectionName: String,
    val sourcePath: Path?,
    val sectionStartLine: Int,
)

internal enum class ClozeHandling {
    ENABLED,
    DISABLED,
}

internal data class RenderedMarkdown(
    val xml: String,
    val localImages: List<String>,
    val clozeBlanks: List<ClozeBlank>,
)

internal enum class ClozeBlankKind {
    EXACT,
    REGEX,
}

internal data class ClozeBlank(
    val answer: String,
    val kind: ClozeBlankKind,
)

internal class MarkdownQtiRenderer(
    private val parser: Parser = defaultParser(),
) {
    companion object {
        private const val MIN_HEADING_LEVEL = 1
        private const val MAX_HEADING_LEVEL = 6
        private const val CLOZE_ESC_OPEN_TOKEN = "__CLOZE_ESC_OPEN__"
        private const val CLOZE_ESC_CLOSE_TOKEN = "__CLOZE_ESC_CLOSE__"

        private fun defaultParser(): Parser =
            Parser
                .builder()
                .extensions(
                    listOf(
                        TablesExtension.create(),
                        StrikethroughExtension.create(),
                        TaskListItemsExtension.create(),
                    ),
                ).includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
                .build()
    }

    fun renderBlocks(
        markdown: String,
        context: RenderContext,
        clozeHandling: ClozeHandling,
    ): RenderedMarkdown {
        val normalized =
            if (clozeHandling == ClozeHandling.ENABLED) {
                preprocessClozeEscapes(markdown)
            } else {
                markdown
            }
        val document = parser.parse(normalized)
        val clozeBlanks =
            if (clozeHandling == ClozeHandling.ENABLED) {
                collectClozeBlanks(document)
            } else {
                emptyList()
            }
        val responseIds = responseIdsFor(clozeBlanks)
        val state =
            RenderState(
                clozeHandling = clozeHandling,
                clozeBlanks = clozeBlanks,
                responseIds = responseIds,
            )
        val builder = StringBuilder()
        renderChildren(document, builder, context, state)
        val xml = builder.toString().trimEnd()
        return RenderedMarkdown(
            xml = xml,
            localImages = state.localImages.distinct(),
            clozeBlanks = clozeBlanks,
        )
    }

    fun renderInline(
        markdown: String,
        context: RenderContext,
        clozeHandling: ClozeHandling,
    ): RenderedMarkdown {
        val normalized =
            if (clozeHandling == ClozeHandling.ENABLED) {
                preprocessClozeEscapes(markdown)
            } else {
                markdown
            }
        val document = parser.parse(normalized)
        val paragraph = document.firstChild
        if (paragraph !is Paragraph || paragraph.next != null) {
            throw unsupported("Inline content must not contain block elements", context, paragraph ?: document)
        }
        val clozeBlanks =
            if (clozeHandling == ClozeHandling.ENABLED) {
                collectClozeBlanks(document)
            } else {
                emptyList()
            }
        val responseIds = responseIdsFor(clozeBlanks)
        val state =
            RenderState(
                clozeHandling = clozeHandling,
                clozeBlanks = clozeBlanks,
                responseIds = responseIds,
            )
        val builder = StringBuilder()
        renderInlineChildren(paragraph, builder, context, state)
        val xml = builder.toString()
        return RenderedMarkdown(
            xml = xml,
            localImages = state.localImages.distinct(),
            clozeBlanks = clozeBlanks,
        )
    }

    fun renderChoiceOptions(
        markdown: String,
        context: RenderContext,
    ): List<ChoiceOption> {
        val document = parser.parse(markdown)
        val topLevelBlocks = document.children().toList()
        if (topLevelBlocks.isEmpty()) {
            return emptyList()
        }
        if (topLevelBlocks.size != 1 || topLevelBlocks.first() !is ListBlock) {
            throw unsupported("Options must be a single Markdown list of task items", context, topLevelBlocks.first())
        }
        val listBlock = topLevelBlocks.first() as ListBlock
        val options = mutableListOf<ChoiceOption>()
        listBlock.children().forEach { node ->
            val listItem =
                node as? ListItem
                    ?: throw unsupported("Options must be a list of task items", context, node)
            val taskMarker =
                findTaskMarker(listItem)
                    ?: throw unsupported("Options must use task list items (e.g., - [x] ...)", context, listItem)
            if (containsNestedList(listItem)) {
                throw unsupported("Options must be a single flat list (no nesting)", context, listItem)
            }
            val state =
                RenderState(
                    clozeHandling = ClozeHandling.DISABLED,
                    clozeBlanks = emptyList(),
                    responseIds = emptyList(),
                )
            val itemXml = renderChoiceContent(listItem, context, state)
            if (itemXml.isBlank()) {
                throw unsupported("Option text must not be empty", context, listItem)
            }
            options.add(
                ChoiceOption(
                    contentXml = itemXml.trim(),
                    isCorrect = taskMarker.isChecked,
                    localImages = state.localImages.distinct(),
                ),
            )
        }
        return options
    }

    private data class RenderState(
        val clozeHandling: ClozeHandling,
        val clozeBlanks: List<ClozeBlank>,
        val responseIds: List<String>,
        var blankIndex: Int = 0,
        val localImages: MutableList<String> = mutableListOf(),
    )

    private fun renderChildren(
        node: Node,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        node.children().forEach { child ->
            renderBlock(child, builder, context, state)
        }
    }

    private fun renderBlock(
        node: Node,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        when (node) {
            is Paragraph -> renderParagraph(node, builder, context, state, prefix = null)
            is Heading -> renderHeading(node, builder, context, state)
            is BlockQuote -> {
                builder.append("<qti-blockquote>\n")
                renderChildren(node, builder, context, state)
                builder.append("</qti-blockquote>\n")
            }
            is BulletList -> renderList(node, builder, context, state)
            is OrderedList -> renderList(node, builder, context, state)
            is FencedCodeBlock -> renderCodeBlock(node.literal, builder, state)
            is IndentedCodeBlock -> renderCodeBlock(node.literal, builder, state)
            is ThematicBreak -> builder.append("<qti-hr/>\n")
            is HtmlBlock -> throw unsupported("Raw HTML blocks are not supported in QTI output", context, node)
            is TableBlock -> renderTable(node, builder, context, state)
            is TaskListItemMarker -> return
            else -> {
                if (node is org.commonmark.node.LinkReferenceDefinition) {
                    return
                }
                throw unsupported("Unsupported block element: ${node.javaClass.simpleName}", context, node)
            }
        }
    }

    private fun renderParagraph(
        node: Paragraph,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
        prefix: String?,
    ) {
        builder.append("<qti-p>")
        if (!prefix.isNullOrEmpty()) {
            builder.append(escapeXml(prefix))
        }
        renderInlineChildren(node, builder, context, state)
        builder.append("</qti-p>\n")
    }

    private fun renderHeading(
        node: Heading,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        val level = node.level.coerceIn(MIN_HEADING_LEVEL, MAX_HEADING_LEVEL)
        builder.append("<qti-h").append(level).append(">")
        renderInlineChildren(node, builder, context, state)
        builder.append("</qti-h").append(level).append(">\n")
    }

    private fun renderList(
        node: ListBlock,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        val tag = if (node is BulletList) "qti-ul" else "qti-ol"
        builder.append("<").append(tag)
        if (node is org.commonmark.node.OrderedList && node.startNumber != 1) {
            builder.append(" start=\"").append(node.startNumber).append("\"")
        }
        builder.append(">\n")
        node.children().forEach { child ->
            val listItem =
                child as? ListItem
                    ?: throw unsupported("List must contain list items", context, child)
            renderListItem(listItem, builder, context, state, includeTaskPrefix = true)
        }
        builder.append("</").append(tag).append(">\n")
    }

    private fun renderListItem(
        node: ListItem,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
        includeTaskPrefix: Boolean,
    ) {
        builder.append("<qti-li>\n")
        renderListItemContent(node, builder, context, state, includeTaskPrefix)
        builder.append("</qti-li>\n")
    }

    private fun renderListItemContent(
        node: ListItem,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
        includeTaskPrefix: Boolean,
    ) {
        val taskMarker = findTaskMarker(node)
        val prefix =
            if (includeTaskPrefix && taskMarker != null) {
                if (taskMarker.isChecked) "[x] " else "[ ] "
            } else {
                null
            }
        var usedPrefix = false
        node.children().forEach { child ->
            if (!usedPrefix && prefix != null && child is Paragraph) {
                renderParagraph(child, builder, context, state, prefix = prefix)
                usedPrefix = true
            } else {
                renderBlock(child, builder, context, state)
            }
        }
        if (prefix != null && !usedPrefix) {
            renderParagraph(Paragraph(), builder, context, state, prefix = prefix)
        }
    }

    private fun renderChoiceContent(
        node: ListItem,
        context: RenderContext,
        state: RenderState,
    ): String {
        val children = node.children().filterNot { it is TaskListItemMarker }.toList()
        if (children.size == 1 && children.first() is Paragraph) {
            val builder = StringBuilder()
            renderInlineChildren(children.first(), builder, context, state)
            return builder.toString()
        }
        val builder = StringBuilder()
        renderListItemContent(node, builder, context, state, includeTaskPrefix = false)
        return builder.toString()
    }

    private fun containsNestedList(node: Node): Boolean {
        if (node is ListBlock) {
            return true
        }
        return node.children().any { child -> containsNestedList(child) }
    }

    private fun findTaskMarker(node: Node): TaskListItemMarker? {
        if (node is TaskListItemMarker) {
            return node
        }
        node.children().forEach { child ->
            val found = findTaskMarker(child)
            if (found != null) {
                return found
            }
        }
        return null
    }

    private fun renderCodeBlock(
        literal: String,
        builder: StringBuilder,
        state: RenderState,
    ) {
        builder.append("<qti-pre>")
        appendCodeFragments(literal, builder, state)
        builder.append("</qti-pre>\n")
    }

    private fun renderInlineCode(
        literal: String,
        builder: StringBuilder,
        state: RenderState,
    ) {
        appendCodeFragments(literal, builder, state)
    }

    private fun appendCodeFragments(
        literal: String,
        builder: StringBuilder,
        state: RenderState,
    ) {
        if (state.clozeHandling != ClozeHandling.ENABLED) {
            builder.append("<qti-code>")
            builder.append(escapeXml(decodeClozeEscapes(literal, state.clozeHandling)))
            builder.append("</qti-code>")
            return
        }
        val parts = parseClozePrompt(literal)
        parts.forEach { part ->
            when (part) {
                is ClozePart.Text -> {
                    builder.append("<qti-code>")
                    builder.append(escapeXml(decodeClozeEscapes(part.value, state.clozeHandling)))
                    builder.append("</qti-code>")
                }
                is ClozePart.Blank -> {
                    val responseId = state.responseIds[state.blankIndex]
                    builder.append("<qti-text-entry-interaction response-identifier=\"")
                    builder.append(responseId)
                    builder.append("\"/>")
                    state.blankIndex += 1
                }
            }
        }
    }

    private fun renderTable(
        node: TableBlock,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        builder.append("<qti-table>\n")
        node.children().forEach { child ->
            when (child) {
                is TableHead -> {
                    builder.append("<qti-thead>\n")
                    renderTableSection(child, builder, context, state)
                    builder.append("</qti-thead>\n")
                }
                is TableBody -> {
                    builder.append("<qti-tbody>\n")
                    renderTableSection(child, builder, context, state)
                    builder.append("</qti-tbody>\n")
                }
                else -> throw unsupported("Unsupported table element", context, child)
            }
        }
        builder.append("</qti-table>\n")
    }

    private fun renderTableSection(
        node: Node,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        node.children().forEach { row ->
            val tableRow =
                row as? TableRow
                    ?: throw unsupported("Table must contain rows", context, row)
            builder.append("<qti-tr>")
            tableRow.children().forEach { cellNode ->
                val cell =
                    cellNode as? TableCell
                        ?: throw unsupported("Table rows must contain cells", context, cellNode)
                renderTableCell(cell, builder, context, state)
            }
            builder.append("</qti-tr>\n")
        }
    }

    private fun renderTableCell(
        cell: TableCell,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        val tag = if (cell.isHeader) "qti-th" else "qti-td"
        builder.append("<").append(tag)
        cell.alignment?.let { alignment ->
            val alignValue = alignment.name.lowercase()
            builder.append(" style=\"text-align: ").append(escapeXml(alignValue)).append(";\"")
        }
        builder.append(">")
        val children = cell.children().toList()
        if (children.size == 1 && children.first() is Paragraph) {
            renderInlineChildren(children.first(), builder, context, state)
        } else {
            children.forEach { child ->
                when (child) {
                    is Paragraph -> renderInlineChildren(child, builder, context, state)
                    else -> renderInline(child, builder, context, state)
                }
            }
        }
        builder.append("</").append(tag).append(">")
    }

    private fun renderInlineChildren(
        node: Node,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        node.children().forEach { child ->
            renderInline(child, builder, context, state)
        }
    }

    private fun renderInline(
        node: Node,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        when (node) {
            is Text -> renderText(node.literal, builder, state)
            is Emphasis -> {
                builder.append("<qti-em>")
                renderInlineChildren(node, builder, context, state)
                builder.append("</qti-em>")
            }
            is StrongEmphasis -> {
                builder.append("<qti-strong>")
                renderInlineChildren(node, builder, context, state)
                builder.append("</qti-strong>")
            }
            is Strikethrough -> {
                builder.append("<qti-del>")
                renderInlineChildren(node, builder, context, state)
                builder.append("</qti-del>")
            }
            is Code -> renderInlineCode(node.literal, builder, state)
            is Link -> renderLink(node, builder, context, state)
            is Image -> renderImage(node, builder, context, state)
            is SoftLineBreak -> builder.append("\n")
            is HardLineBreak -> builder.append("<qti-br/>")
            is TaskListItemMarker -> return
            is HtmlInline -> throw unsupported("Raw HTML is not supported in QTI output", context, node)
            else -> throw unsupported("Unsupported inline element: ${node.javaClass.simpleName}", context, node)
        }
    }

    private fun renderText(
        text: String,
        builder: StringBuilder,
        state: RenderState,
    ) {
        if (state.clozeHandling == ClozeHandling.ENABLED) {
            val parts = parseClozePrompt(text)
            parts.forEach { part ->
                when (part) {
                    is ClozePart.Text -> {
                        val decoded = decodeClozeEscapes(part.value, state.clozeHandling)
                        builder.append(escapeXml(decoded))
                    }
                    is ClozePart.Blank -> {
                        val responseId = state.responseIds[state.blankIndex]
                        builder.append("<qti-text-entry-interaction response-identifier=\"")
                        builder.append(responseId)
                        builder.append("\"/>")
                        state.blankIndex += 1
                    }
                }
            }
        } else {
            builder.append(escapeXml(decodeClozeEscapes(text, state.clozeHandling)))
        }
    }

    private fun renderLink(
        node: Link,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        val destination = node.destination
        if (destination.isNullOrBlank()) {
            throw unsupported("Link destination must not be empty", context, node)
        }
        builder.append("<qti-a href=\"").append(escapeXml(destination)).append("\"")
        if (!node.title.isNullOrBlank()) {
            builder.append(" title=\"").append(escapeXml(node.title)).append("\"")
        }
        builder.append(">")
        renderInlineChildren(node, builder, context, state)
        builder.append("</qti-a>")
    }

    private fun renderImage(
        node: Image,
        builder: StringBuilder,
        context: RenderContext,
        state: RenderState,
    ) {
        val destination = node.destination
        if (destination.isNullOrBlank()) {
            throw unsupported("Image path must not be empty", context, node)
        }
        val altText = extractPlainText(node)
        builder.append("<qti-img src=\"").append(escapeXml(destination)).append("\"")
        builder.append(" alt=\"").append(escapeXml(altText)).append("\"")
        if (!node.title.isNullOrBlank()) {
            builder.append(" title=\"").append(escapeXml(node.title)).append("\"")
        }
        builder.append("/>")
        if (!isRemoteImagePath(destination)) {
            state.localImages.add(destination)
        }
    }

    private fun extractPlainText(node: Node): String {
        val builder = StringBuilder()

        fun collect(current: Node) {
            when (current) {
                is Text -> builder.append(decodeClozeEscapes(current.literal, ClozeHandling.DISABLED))
                is Code -> builder.append(decodeClozeEscapes(current.literal, ClozeHandling.DISABLED))
                is SoftLineBreak, is HardLineBreak -> builder.append(" ")
            }
            current.children().forEach { child -> collect(child) }
        }
        collect(node)
        return builder.toString()
    }

    private fun unsupported(
        message: String,
        context: RenderContext,
        node: Node,
    ): IllegalArgumentException {
        val location = node.sourceSpans.firstOrNull()
        val formattedLocation = formatLocation(context, location)
        return IllegalArgumentException("$message$formattedLocation")
    }

    private fun formatLocation(
        context: RenderContext,
        span: SourceSpan?,
    ): String {
        val location =
            if (span == null) {
                null
            } else {
                val line = context.sectionStartLine + span.lineIndex
                val column = span.columnIndex + 1
                "$line:$column"
            }
        val source = context.sourcePath?.toAbsolutePath()?.toString()
        return when {
            source != null && location != null -> " ($source:$location)"
            source != null -> " ($source)"
            location != null -> " (line $location)"
            else -> ""
        }
    }

    private fun collectClozeBlanks(document: Node): List<ClozeBlank> {
        val blanks = mutableListOf<ClozeBlank>()

        fun visit(node: Node) {
            when (node) {
                is Text -> {
                    val parts = parseClozePrompt(node.literal)
                    parts.filterIsInstance<ClozePart.Blank>().forEach { blanks.add(it.blank) }
                }
                is Code -> {
                    val parts = parseClozePrompt(node.literal)
                    parts.filterIsInstance<ClozePart.Blank>().forEach { blanks.add(it.blank) }
                    return
                }
                is FencedCodeBlock -> {
                    val parts = parseClozePrompt(node.literal)
                    parts.filterIsInstance<ClozePart.Blank>().forEach { blanks.add(it.blank) }
                    return
                }
                is IndentedCodeBlock -> {
                    val parts = parseClozePrompt(node.literal)
                    parts.filterIsInstance<ClozePart.Blank>().forEach { blanks.add(it.blank) }
                    return
                }
            }
            node.children().forEach { child -> visit(child) }
        }
        visit(document)
        return blanks
    }

    private fun responseIdsFor(blanks: List<ClozeBlank>): List<String> {
        if (blanks.isEmpty()) {
            return emptyList()
        }
        return if (blanks.size == 1) {
            listOf("RESPONSE")
        } else {
            blanks.indices.map { index -> "RESPONSE_${index + 1}" }
        }
    }

    private fun preprocessClozeEscapes(markdown: String): String =
        markdown
            .replace("\\{{", CLOZE_ESC_OPEN_TOKEN)
            .replace("\\}}", CLOZE_ESC_CLOSE_TOKEN)

    private fun decodeClozeEscapes(
        value: String,
        clozeHandling: ClozeHandling,
    ): String {
        if (clozeHandling == ClozeHandling.DISABLED) {
            return value
        }
        return value
            .replace(CLOZE_ESC_OPEN_TOKEN, "{{")
            .replace(CLOZE_ESC_CLOSE_TOKEN, "}}")
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
            if (prompt.startsWith(CLOZE_ESC_OPEN_TOKEN, index)) {
                buffer.append(CLOZE_ESC_OPEN_TOKEN)
                index += CLOZE_ESC_OPEN_TOKEN.length
                continue
            }
            if (prompt.startsWith(CLOZE_ESC_CLOSE_TOKEN, index)) {
                buffer.append(CLOZE_ESC_CLOSE_TOKEN)
                index += CLOZE_ESC_CLOSE_TOKEN.length
                continue
            }
            if (prompt.startsWith("{{", index)) {
                val endIndex = prompt.indexOf("}}", index + 2)
                require(endIndex != -1) { "Unclosed cloze blank in prompt" }
                val answer = prompt.substring(index + 2, endIndex).trim()
                require(answer.isNotEmpty()) { "Cloze blank must not be empty" }
                val blank =
                    if (answer.length >= 2 && answer.startsWith("/") && answer.endsWith("/")) {
                        val pattern = answer.substring(1, answer.length - 1)
                        require(pattern.isNotBlank()) { "Regex cloze blank must not be empty" }
                        ClozeBlank(pattern, ClozeBlankKind.REGEX)
                    } else {
                        ClozeBlank(answer, ClozeBlankKind.EXACT)
                    }
                flushText()
                parts.add(ClozePart.Blank(blank))
                index = endIndex + 2
                continue
            }
            buffer.append(prompt[index])
            index += 1
        }

        flushText()
        return parts
    }

    private sealed class ClozePart {
        data class Text(
            val value: String,
        ) : ClozePart()

        data class Blank(
            val blank: ClozeBlank,
        ) : ClozePart()
    }
}

private fun Node.children(): Sequence<Node> =
    sequence {
        var current = firstChild
        while (current != null) {
            yield(current)
            current = current.next
        }
    }
