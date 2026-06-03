import { fromMarkdown } from "mdast-util-from-markdown";
import { gfmStrikethroughFromMarkdown } from "mdast-util-gfm-strikethrough";
import { gfmTableFromMarkdown } from "mdast-util-gfm-table";
import { gfmTaskListItemFromMarkdown } from "mdast-util-gfm-task-list-item";
import { gfmStrikethrough } from "micromark-extension-gfm-strikethrough";
import { gfmTable } from "micromark-extension-gfm-table";
import { gfmTaskListItem } from "micromark-extension-gfm-task-list-item";
import type {
  AlignType,
  Image,
  Link,
  List,
  ListItem,
  Nodes,
  Paragraph,
  Parent,
  RootContent,
  Table,
  TableCell
} from "mdast";

import { escapeXml } from "./escape-xml.js";

export type ClozeHandling = "enabled" | "disabled";

export interface RenderContext {
  sectionName: string;
  sourcePath: string | null;
  sectionStartLine: number;
}

export type ClozeBlankKind = "exact" | "regex";

export interface ClozeBlank {
  answer: string;
  kind: ClozeBlankKind;
}

export interface RenderedMarkdown {
  xml: string;
  localImages: string[];
  clozeBlanks: ClozeBlank[];
}

export interface ChoiceOption {
  contentXml: string;
  isCorrect: boolean;
  localImages: string[];
}

const CLOZE_ESC_OPEN_TOKEN = "__CLOZE_ESC_OPEN__";
const CLOZE_ESC_CLOSE_TOKEN = "__CLOZE_ESC_CLOSE__";
const MIN_HEADING_LEVEL = 1;
const MAX_HEADING_LEVEL = 6;

export function isRemoteImagePath(source: string): boolean {
  const normalized = source.toLowerCase();
  return (
    normalized.startsWith("http://") ||
    normalized.startsWith("https://") ||
    normalized.startsWith("data:")
  );
}

function parseMarkdown(text: string): Parent {
  return fromMarkdown(text, {
    extensions: [gfmTable(), gfmStrikethrough({ singleTilde: false }), gfmTaskListItem()],
    mdastExtensions: [
      gfmTableFromMarkdown(),
      gfmStrikethroughFromMarkdown(),
      gfmTaskListItemFromMarkdown()
    ]
  });
}

function childrenOf(node: unknown): RootContent[] {
  const parent = node as Partial<Parent>;
  return Array.isArray(parent.children) ? (parent.children as RootContent[]) : [];
}

interface RenderState {
  clozeHandling: ClozeHandling;
  responseIds: string[];
  blankIndex: number;
  localImages: string[];
}

type ClozePart = { kind: "text"; value: string } | { kind: "blank"; blank: ClozeBlank };

export class MarkdownQtiRenderer {
  renderBlocks(
    markdown: string,
    context: RenderContext,
    clozeHandling: ClozeHandling
  ): RenderedMarkdown {
    const normalized = clozeHandling === "enabled" ? preprocessClozeEscapes(markdown) : markdown;
    const document = parseMarkdown(normalized);
    const clozeBlanks = clozeHandling === "enabled" ? collectClozeBlanks(document) : [];
    const state: RenderState = {
      clozeHandling,
      responseIds: responseIdsFor(clozeBlanks),
      blankIndex: 0,
      localImages: []
    };
    let builder = "";
    for (const child of childrenOf(document)) {
      builder += this.renderBlock(child, context, state);
    }
    return {
      xml: builder.replace(/\s+$/u, ""),
      localImages: distinct(state.localImages),
      clozeBlanks
    };
  }

  renderInline(
    markdown: string,
    context: RenderContext,
    clozeHandling: ClozeHandling
  ): RenderedMarkdown {
    const normalized = clozeHandling === "enabled" ? preprocessClozeEscapes(markdown) : markdown;
    const document = parseMarkdown(normalized);
    const blocks = childrenOf(document);
    const paragraph = blocks[0];
    if (paragraph === undefined || paragraph.type !== "paragraph" || blocks.length > 1) {
      throw unsupported(
        "Inline content must not contain block elements",
        context,
        paragraph ?? document
      );
    }
    const clozeBlanks = clozeHandling === "enabled" ? collectClozeBlanks(document) : [];
    const state: RenderState = {
      clozeHandling,
      responseIds: responseIdsFor(clozeBlanks),
      blankIndex: 0,
      localImages: []
    };
    const xml = this.renderInlineChildren(paragraph, context, state);
    return { xml, localImages: distinct(state.localImages), clozeBlanks };
  }

  renderChoiceOptions(markdown: string, context: RenderContext): ChoiceOption[] {
    const document = parseMarkdown(markdown);
    const topLevelBlocks = childrenOf(document);
    if (topLevelBlocks.length === 0) {
      return [];
    }
    const listBlock = topLevelBlocks[0];
    if (topLevelBlocks.length !== 1 || listBlock === undefined || listBlock.type !== "list") {
      throw unsupported(
        "Options must be a single Markdown list of task items",
        context,
        listBlock ?? document
      );
    }
    const options: ChoiceOption[] = [];
    for (const node of childrenOf(listBlock)) {
      if (node.type !== "listItem") {
        throw unsupported("Options must be a list of task items", context, node);
      }
      const listItem = node as ListItem;
      if (listItem.checked === null || listItem.checked === undefined) {
        throw unsupported("Options must use task list items (e.g., - [x] ...)", context, listItem);
      }
      if (containsNestedList(listItem)) {
        throw unsupported("Options must be a single flat list (no nesting)", context, listItem);
      }
      const state: RenderState = {
        clozeHandling: "disabled",
        responseIds: [],
        blankIndex: 0,
        localImages: []
      };
      const itemXml = this.renderChoiceContent(listItem, context, state);
      if (itemXml.trim() === "") {
        throw unsupported("Option text must not be empty", context, listItem);
      }
      options.push({
        contentXml: itemXml.trim(),
        isCorrect: listItem.checked === true,
        localImages: distinct(state.localImages)
      });
    }
    return options;
  }

  private renderBlock(node: RootContent, context: RenderContext, state: RenderState): string {
    switch (node.type) {
      case "paragraph":
        return this.renderParagraph(node, context, state, null);
      case "heading": {
        const level = clamp(node.depth, MIN_HEADING_LEVEL, MAX_HEADING_LEVEL);
        return `<qti-h${level}>${this.renderInlineChildren(node, context, state)}</qti-h${level}>\n`;
      }
      case "blockquote":
        return `<qti-blockquote>\n${this.renderChildren(node, context, state)}</qti-blockquote>\n`;
      case "list":
        return this.renderList(node, context, state);
      case "code":
        return this.renderCodeBlock(codeLiteral(node.value), state);
      case "thematicBreak":
        return "<qti-hr/>\n";
      case "html":
        throw unsupported("Raw HTML blocks are not supported in QTI output", context, node);
      case "table":
        return this.renderTable(node, context, state);
      case "definition":
        return "";
      default:
        throw unsupported(`Unsupported block element: ${node.type}`, context, node);
    }
  }

  private renderChildren(node: Parent, context: RenderContext, state: RenderState): string {
    let builder = "";
    for (const child of childrenOf(node)) {
      builder += this.renderBlock(child, context, state);
    }
    return builder;
  }

  private renderParagraph(
    node: Paragraph,
    context: RenderContext,
    state: RenderState,
    prefix: string | null
  ): string {
    let builder = "<qti-p>";
    if (prefix !== null && prefix !== "") {
      builder += escapeXml(prefix);
    }
    builder += this.renderInlineChildren(node, context, state);
    builder += "</qti-p>\n";
    return builder;
  }

  private renderList(node: List, context: RenderContext, state: RenderState): string {
    const tag = node.ordered === true ? "qti-ol" : "qti-ul";
    let builder = `<${tag}`;
    if (node.ordered === true) {
      const start = node.start ?? 1;
      if (start !== 1) {
        builder += ` start="${start}"`;
      }
    }
    builder += ">\n";
    for (const child of childrenOf(node)) {
      if (child.type !== "listItem") {
        throw unsupported("List must contain list items", context, child);
      }
      builder += this.renderListItem(child as ListItem, context, state, true);
    }
    builder += `</${tag}>\n`;
    return builder;
  }

  private renderListItem(
    node: ListItem,
    context: RenderContext,
    state: RenderState,
    includeTaskPrefix: boolean
  ): string {
    return `<qti-li>\n${this.renderListItemContent(node, context, state, includeTaskPrefix)}</qti-li>\n`;
  }

  private renderListItemContent(
    node: ListItem,
    context: RenderContext,
    state: RenderState,
    includeTaskPrefix: boolean
  ): string {
    const isTask = node.checked === true || node.checked === false;
    const prefix = includeTaskPrefix && isTask ? (node.checked === true ? "[x] " : "[ ] ") : null;
    let builder = "";
    let usedPrefix = false;
    for (const child of childrenOf(node)) {
      if (!usedPrefix && prefix !== null && child.type === "paragraph") {
        builder += this.renderParagraph(child, context, state, prefix);
        usedPrefix = true;
      } else {
        builder += this.renderBlock(child, context, state);
      }
    }
    if (prefix !== null && !usedPrefix) {
      builder += this.renderParagraph({ type: "paragraph", children: [] }, context, state, prefix);
    }
    return builder;
  }

  private renderChoiceContent(node: ListItem, context: RenderContext, state: RenderState): string {
    const children = childrenOf(node);
    const first = children[0];
    if (children.length === 1 && first !== undefined && first.type === "paragraph") {
      return this.renderInlineChildren(first, context, state);
    }
    return this.renderListItemContent(node, context, state, false);
  }

  private renderCodeBlock(literal: string, state: RenderState): string {
    return `<qti-pre>${this.appendCodeFragments(literal, state)}</qti-pre>\n`;
  }

  private appendCodeFragments(literal: string, state: RenderState): string {
    if (state.clozeHandling !== "enabled") {
      return `<qti-code>${escapeXml(decodeClozeEscapes(literal, state.clozeHandling))}</qti-code>`;
    }
    let builder = "";
    for (const part of parseClozePrompt(literal)) {
      if (part.kind === "text") {
        builder += `<qti-code>${escapeXml(decodeClozeEscapes(part.value, state.clozeHandling))}</qti-code>`;
      } else {
        builder += this.emitBlankInteraction(state);
      }
    }
    return builder;
  }

  private renderTable(node: Table, context: RenderContext, state: RenderState): string {
    const rows = childrenOf(node).filter((child) => child.type === "tableRow");
    const align = node.align ?? [];
    let builder = "<qti-table>\n";
    const headerRow = rows[0];
    if (headerRow !== undefined) {
      builder += "<qti-thead>\n";
      builder += this.renderTableRow(headerRow, align, true, context, state);
      builder += "</qti-thead>\n";
    }
    const bodyRows = rows.slice(1);
    if (bodyRows.length > 0) {
      builder += "<qti-tbody>\n";
      for (const row of bodyRows) {
        builder += this.renderTableRow(row, align, false, context, state);
      }
      builder += "</qti-tbody>\n";
    }
    builder += "</qti-table>\n";
    return builder;
  }

  private renderTableRow(
    row: RootContent,
    align: AlignType[],
    isHeader: boolean,
    context: RenderContext,
    state: RenderState
  ): string {
    let builder = "<qti-tr>";
    childrenOf(row).forEach((cellNode, columnIndex) => {
      if (cellNode.type !== "tableCell") {
        throw unsupported("Table rows must contain cells", context, cellNode);
      }
      builder += this.renderTableCell(
        cellNode as TableCell,
        align[columnIndex] ?? null,
        isHeader,
        context,
        state
      );
    });
    builder += "</qti-tr>\n";
    return builder;
  }

  private renderTableCell(
    cell: TableCell,
    alignment: AlignType,
    isHeader: boolean,
    context: RenderContext,
    state: RenderState
  ): string {
    const tag = isHeader ? "qti-th" : "qti-td";
    let builder = `<${tag}`;
    if (alignment !== null && alignment !== undefined) {
      builder += ` style="text-align: ${escapeXml(alignment)};"`;
    }
    builder += ">";
    builder += this.renderInlineChildren(cell, context, state);
    builder += `</${tag}>`;
    return builder;
  }

  private renderInlineChildren(node: Parent, context: RenderContext, state: RenderState): string {
    let builder = "";
    for (const child of childrenOf(node)) {
      builder += this.renderInlineNode(child, context, state);
    }
    return builder;
  }

  private renderInlineNode(node: RootContent, context: RenderContext, state: RenderState): string {
    switch (node.type) {
      case "text":
        return this.renderText(node.value, state);
      case "emphasis":
        return `<qti-em>${this.renderInlineChildren(node, context, state)}</qti-em>`;
      case "strong":
        return `<qti-strong>${this.renderInlineChildren(node, context, state)}</qti-strong>`;
      case "delete":
        return `<qti-del>${this.renderInlineChildren(node, context, state)}</qti-del>`;
      case "inlineCode":
        return this.appendCodeFragments(node.value, state);
      case "link":
        return this.renderLink(node, context, state);
      case "image":
        return this.renderImage(node, context, state);
      case "break":
        return "<qti-br/>";
      case "html":
        throw unsupported("Raw HTML is not supported in QTI output", context, node);
      default:
        throw unsupported(`Unsupported inline element: ${node.type}`, context, node);
    }
  }

  private renderText(text: string, state: RenderState): string {
    if (state.clozeHandling !== "enabled") {
      return escapeXml(decodeClozeEscapes(text, state.clozeHandling));
    }
    let builder = "";
    for (const part of parseClozePrompt(text)) {
      if (part.kind === "text") {
        builder += escapeXml(decodeClozeEscapes(part.value, state.clozeHandling));
      } else {
        builder += this.emitBlankInteraction(state);
      }
    }
    return builder;
  }

  private emitBlankInteraction(state: RenderState): string {
    const responseId = state.responseIds[state.blankIndex] ?? "RESPONSE";
    state.blankIndex += 1;
    return `<qti-text-entry-interaction response-identifier="${responseId}"/>`;
  }

  private renderLink(node: Link, context: RenderContext, state: RenderState): string {
    const destination = node.url;
    if (destination === undefined || destination === null || destination.trim() === "") {
      throw unsupported("Link destination must not be empty", context, node);
    }
    let builder = `<qti-a href="${escapeXml(destination)}"`;
    if (node.title !== undefined && node.title !== null && node.title.trim() !== "") {
      builder += ` title="${escapeXml(node.title)}"`;
    }
    builder += ">";
    builder += this.renderInlineChildren(node, context, state);
    builder += "</qti-a>";
    return builder;
  }

  private renderImage(node: Image, _context: RenderContext, state: RenderState): string {
    const destination = node.url;
    if (destination === undefined || destination === null || destination.trim() === "") {
      throw unsupported("Image path must not be empty", _context, node);
    }
    const altText = node.alt ?? "";
    let builder = `<qti-img src="${escapeXml(destination)}"`;
    builder += ` alt="${escapeXml(altText)}"`;
    if (node.title !== undefined && node.title !== null && node.title.trim() !== "") {
      builder += ` title="${escapeXml(node.title)}"`;
    }
    builder += "/>";
    if (!isRemoteImagePath(destination)) {
      state.localImages.push(destination);
    }
    return builder;
  }
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function codeLiteral(value: string): string {
  return value === "" ? "" : `${value}\n`;
}

function distinct(values: string[]): string[] {
  return [...new Set(values)];
}

function containsNestedList(node: unknown): boolean {
  const typed = node as { type?: string };
  if (typed.type === "list") {
    return true;
  }
  return childrenOf(node).some((child) => containsNestedList(child));
}

function responseIdsFor(blanks: ClozeBlank[]): string[] {
  if (blanks.length === 0) {
    return [];
  }
  if (blanks.length === 1) {
    return ["RESPONSE"];
  }
  return blanks.map((_blank, index) => `RESPONSE_${index + 1}`);
}

function collectClozeBlanks(document: Parent): ClozeBlank[] {
  const blanks: ClozeBlank[] = [];
  const visit = (node: Nodes): void => {
    if (node.type === "text" || node.type === "inlineCode" || node.type === "code") {
      for (const part of parseClozePrompt(node.value)) {
        if (part.kind === "blank") {
          blanks.push(part.blank);
        }
      }
      if (node.type !== "text") {
        return;
      }
    }
    for (const child of childrenOf(node)) {
      visit(child as Nodes);
    }
  };
  visit(document as Nodes);
  return blanks;
}

function preprocessClozeEscapes(markdown: string): string {
  return markdown
    .split("\\{{")
    .join(CLOZE_ESC_OPEN_TOKEN)
    .split("\\}}")
    .join(CLOZE_ESC_CLOSE_TOKEN);
}

function decodeClozeEscapes(value: string, clozeHandling: ClozeHandling): string {
  if (clozeHandling === "disabled") {
    return value;
  }
  return value.split(CLOZE_ESC_OPEN_TOKEN).join("{{").split(CLOZE_ESC_CLOSE_TOKEN).join("}}");
}

function parseClozePrompt(prompt: string): ClozePart[] {
  const parts: ClozePart[] = [];
  let buffer = "";
  let index = 0;
  const flushText = (): void => {
    if (buffer.length > 0) {
      parts.push({ kind: "text", value: buffer });
      buffer = "";
    }
  };
  while (index < prompt.length) {
    if (prompt.startsWith(CLOZE_ESC_OPEN_TOKEN, index)) {
      buffer += CLOZE_ESC_OPEN_TOKEN;
      index += CLOZE_ESC_OPEN_TOKEN.length;
      continue;
    }
    if (prompt.startsWith(CLOZE_ESC_CLOSE_TOKEN, index)) {
      buffer += CLOZE_ESC_CLOSE_TOKEN;
      index += CLOZE_ESC_CLOSE_TOKEN.length;
      continue;
    }
    if (prompt.startsWith("{{", index)) {
      const endIndex = prompt.indexOf("}}", index + 2);
      if (endIndex === -1) {
        throw new Error("Unclosed cloze blank in prompt");
      }
      const answer = prompt.slice(index + 2, endIndex).trim();
      if (answer.length === 0) {
        throw new Error("Cloze blank must not be empty");
      }
      let blank: ClozeBlank;
      if (answer.length >= 2 && answer.startsWith("/") && answer.endsWith("/")) {
        const pattern = answer.slice(1, answer.length - 1);
        if (pattern.trim() === "") {
          throw new Error("Regex cloze blank must not be empty");
        }
        blank = { answer: pattern, kind: "regex" };
      } else {
        blank = { answer, kind: "exact" };
      }
      flushText();
      parts.push({ kind: "blank", blank });
      index = endIndex + 2;
      continue;
    }
    buffer += prompt[index];
    index += 1;
  }
  flushText();
  return parts;
}

function unsupported(message: string, context: RenderContext, node: unknown): Error {
  return new Error(`${message}${formatLocation(context, node)}`);
}

function formatLocation(context: RenderContext, node: unknown): string {
  const position = (node as { position?: { start?: { line?: number; column?: number } } }).position;
  let location: string | null = null;
  if (position?.start?.line !== undefined && position.start.column !== undefined) {
    const line = context.sectionStartLine + (position.start.line - 1);
    location = `${line}:${position.start.column}`;
  }
  const source = context.sourcePath;
  if (source !== null && location !== null) {
    return ` (${source}:${location})`;
  }
  if (source !== null) {
    return ` (${source})`;
  }
  if (location !== null) {
    return ` (line ${location})`;
  }
  return "";
}
