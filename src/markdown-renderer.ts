import { fromMarkdown } from "mdast-util-from-markdown";
import { gfmStrikethroughFromMarkdown } from "mdast-util-gfm-strikethrough";
import { gfmTableFromMarkdown } from "mdast-util-gfm-table";
import { gfmTaskListItemFromMarkdown } from "mdast-util-gfm-task-list-item";
import { toHast } from "mdast-util-to-hast";
import { gfmStrikethrough } from "micromark-extension-gfm-strikethrough";
import { gfmTable } from "micromark-extension-gfm-table";
import { gfmTaskListItem } from "micromark-extension-gfm-task-list-item";
import { raw } from "hast-util-raw";
import { toHtml } from "hast-util-to-html";
import type { Element, Nodes, Parent, Root as HastRoot, RootContent } from "hast";
import type { ListItem, Root as MdastRoot } from "mdast";

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
  isBlockContent: boolean;
}

const CLOZE_ESC_OPEN_TOKEN = "__CLOZE_ESC_OPEN__";
const CLOZE_ESC_CLOSE_TOKEN = "__CLOZE_ESC_CLOSE__";

const XML_SERIALIZER_OPTIONS = {
  closeSelfClosing: true,
  tightSelfClosing: false,
  characterReferences: { useNamedReferences: true }
} as const;

export function isRemoteImagePath(source: string): boolean {
  const normalized = source.toLowerCase();
  return (
    normalized.startsWith("http://") ||
    normalized.startsWith("https://") ||
    normalized.startsWith("data:")
  );
}

function parseMarkdown(text: string): MdastRoot {
  return fromMarkdown(text, {
    extensions: [gfmTable(), gfmStrikethrough({ singleTilde: false }), gfmTaskListItem()],
    mdastExtensions: [
      gfmTableFromMarkdown(),
      gfmStrikethroughFromMarkdown(),
      gfmTaskListItemFromMarkdown()
    ]
  });
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
    const document = this.renderHast(markdown, clozeHandling, "normal");
    const clozeBlanks = clozeHandling === "enabled" ? collectClozeBlanks(document) : [];
    const state = createRenderState(clozeHandling, clozeBlanks);
    transformClozeTextNodes(document, state);
    collectLocalImages(document, state);
    return {
      xml: serialize(document.children).replace(/\s+$/u, ""),
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
    const mdast = parseMarkdown(normalized);
    const blocks = mdast.children;
    const paragraph = blocks[0];
    if (paragraph === undefined || paragraph.type !== "paragraph" || blocks.length > 1) {
      throw unsupported(
        "Inline content must not contain block elements",
        context,
        paragraph ?? mdast
      );
    }

    const document = prepareHast(toHast(mdast, { allowDangerousHtml: true }), "normal");
    const paragraphElement = document.children[0];
    if (paragraphElement?.type !== "element" || paragraphElement.tagName !== "p") {
      throw unsupported("Inline content did not produce a paragraph", context, paragraph);
    }
    const clozeBlanks = clozeHandling === "enabled" ? collectClozeBlanks(document) : [];
    const state = createRenderState(clozeHandling, clozeBlanks);
    transformClozeTextNodes(document, state);
    collectLocalImages(document, state);
    return {
      xml: serialize(paragraphElement.children),
      localImages: distinct(state.localImages),
      clozeBlanks
    };
  }

  renderChoiceOptions(markdown: string, context: RenderContext): ChoiceOption[] {
    const document = parseMarkdown(markdown);
    const topLevelBlocks = document.children;
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
    for (const node of listBlock.children) {
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

      const optionDocument = this.renderChoiceHast(listItem);
      const optionElement = optionDocument.children[0];
      if (optionElement?.type !== "element" || optionElement.tagName !== "li") {
        throw unsupported("Option did not produce a list item", context, listItem);
      }
      const state = createRenderState("disabled", []);
      transformClozeTextNodes(optionDocument, state);
      collectLocalImages(optionDocument, state);

      const firstChild = optionElement.children[0];
      const isSingleParagraph =
        optionElement.children.length === 1 &&
        firstChild?.type === "element" &&
        firstChild.tagName === "p";
      const isInlineContent = optionElement.children.every(
        (child) => child.type !== "element" || !isBlockElement(child.tagName)
      );
      const contentNodes = isSingleParagraph
        ? (firstChild as Element).children
        : optionElement.children;
      const contentXml = serialize(contentNodes).trim();
      if (contentXml === "") {
        throw unsupported("Option text must not be empty", context, listItem);
      }
      options.push({
        contentXml,
        isCorrect: listItem.checked === true,
        localImages: distinct(state.localImages),
        isBlockContent: !isInlineContent
      });
    }
    return options;
  }

  private renderHast(
    markdown: string,
    clozeHandling: ClozeHandling,
    taskListMode: "normal" | "choice"
  ): HastRoot {
    const normalized = clozeHandling === "enabled" ? preprocessClozeEscapes(markdown) : markdown;
    return prepareHast(
      toHast(parseMarkdown(normalized), { allowDangerousHtml: true }),
      taskListMode
    );
  }

  private renderChoiceHast(listItem: ListItem): HastRoot {
    const root = toHast({ type: "root", children: [listItem] } as MdastRoot, {
      allowDangerousHtml: true
    });
    return prepareHast(root, "choice");
  }
}

function createRenderState(clozeHandling: ClozeHandling, clozeBlanks: ClozeBlank[]): RenderState {
  return {
    clozeHandling,
    responseIds: responseIdsFor(clozeBlanks),
    blankIndex: 0,
    localImages: []
  };
}

function prepareHast(tree: Nodes, taskListMode: "normal" | "choice"): HastRoot {
  const parsed = raw(tree);
  if (parsed.type !== "root") {
    throw new Error("Markdown conversion did not produce a HAST root");
  }
  removeComments(parsed);
  parsed.children = parsed.children.filter(
    (child) => child.type !== "text" || child.value.trim() !== ""
  );
  normalizeTaskListInputs(parsed, taskListMode);
  normalizeTableAlignment(parsed);
  return parsed;
}

function serialize(nodes: RootContent[]): string {
  return toHtml(nodes, XML_SERIALIZER_OPTIONS);
}

function removeComments(parent: Parent): void {
  parent.children = parent.children.filter((child) => child.type !== "comment");
  for (const child of parent.children) {
    if (child.type === "element") {
      removeComments(child);
    }
  }
}

function normalizeTaskListInputs(parent: Parent, taskListMode: "normal" | "choice"): void {
  const nextChildren: RootContent[] = [];
  for (const child of parent.children) {
    if (child.type === "element" && child.tagName === "input" && isTaskCheckbox(child)) {
      if (taskListMode === "normal") {
        nextChildren.push({
          type: "text",
          value: child.properties.checked === true ? "[x]" : "[ ]"
        });
      }
      continue;
    }
    if (child.type === "element") {
      normalizeTaskListInputs(child, taskListMode);
    }
    nextChildren.push(child);
  }
  parent.children = nextChildren;
}

function isTaskCheckbox(element: Element): boolean {
  return element.properties.type === "checkbox";
}

function isBlockElement(tagName: string): boolean {
  return /^(address|article|aside|blockquote|details|dialog|div|dl|fieldset|figcaption|figure|footer|form|h[1-6]|header|hgroup|hr|main|nav|ol|p|pre|section|table|ul)$/u.test(
    tagName
  );
}

function normalizeTableAlignment(parent: Parent): void {
  for (const child of parent.children) {
    if (child.type !== "element") {
      continue;
    }
    if (
      (child.tagName === "th" || child.tagName === "td") &&
      typeof child.properties.align === "string"
    ) {
      const alignment = child.properties.align;
      const existingStyle =
        typeof child.properties.style === "string" ? child.properties.style.trim() : "";
      child.properties.style =
        existingStyle === ""
          ? `text-align: ${alignment};`
          : `${existingStyle} text-align: ${alignment};`;
      delete child.properties.align;
    }
    normalizeTableAlignment(child);
  }
}

function collectLocalImages(parent: Parent, state: RenderState): void {
  for (const child of parent.children) {
    if (child.type !== "element") {
      continue;
    }
    if (child.tagName === "img" && typeof child.properties.src === "string") {
      if (!isRemoteImagePath(child.properties.src)) {
        state.localImages.push(child.properties.src);
      }
    }
    collectLocalImages(child, state);
  }
}

function transformClozeTextNodes(parent: Parent, state: RenderState): void {
  if (state.clozeHandling !== "enabled") {
    decodeTextEscapes(parent);
    return;
  }
  const nextChildren: RootContent[] = [];
  for (const child of parent.children) {
    if (child.type === "text") {
      nextChildren.push(...renderTextNodes(child.value, state));
      continue;
    }
    if (child.type === "element") {
      transformClozeTextNodes(child, state);
    }
    nextChildren.push(child);
  }
  parent.children = nextChildren;
}

function decodeTextEscapes(parent: Parent): void {
  for (const child of parent.children) {
    if (child.type === "text") {
      child.value = decodeClozeEscapes(child.value, "disabled");
    } else if (child.type === "element") {
      decodeTextEscapes(child);
    }
  }
}

function renderTextNodes(value: string, state: RenderState): RootContent[] {
  const nodes: RootContent[] = [];
  for (const part of parseClozePrompt(value)) {
    if (part.kind === "text") {
      if (part.value !== "") {
        nodes.push({ type: "text", value: decodeClozeEscapes(part.value, state.clozeHandling) });
      }
    } else {
      const responseId = state.responseIds[state.blankIndex] ?? "RESPONSE";
      state.blankIndex += 1;
      nodes.push({
        type: "element",
        tagName: "qti-text-entry-interaction",
        properties: { "response-identifier": responseId },
        children: []
      });
    }
  }
  return nodes;
}

function collectClozeBlanks(root: HastRoot): ClozeBlank[] {
  const blanks: ClozeBlank[] = [];
  const visit = (node: Nodes): void => {
    if (node.type === "text") {
      for (const part of parseClozePrompt(node.value)) {
        if (part.kind === "blank") {
          blanks.push(part.blank);
        }
      }
      return;
    }
    if (node.type === "element") {
      for (const child of node.children) {
        visit(child);
      }
    } else if (node.type === "root") {
      for (const child of node.children) {
        visit(child);
      }
    }
  };
  visit(root);
  return blanks;
}

function distinct(values: string[]): string[] {
  return [...new Set(values)];
}

function containsNestedList(node: unknown): boolean {
  const typed = node as { type?: string; children?: unknown[] };
  if (typed.type === "list") {
    return true;
  }
  return (typed.children ?? []).some((child) => containsNestedList(child));
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
