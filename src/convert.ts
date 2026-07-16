import { existsSync, statSync } from "node:fs";
import path from "node:path";

import { addDecimals, DECIMAL_ZERO, type Decimal, formatDecimal, parseDecimal } from "./decimal.js";
import { parseDocument } from "yaml";
import { escapeXml } from "./escape-xml.js";
import {
  type ChoiceOption,
  type ClozeBlank,
  isRemoteImagePath,
  MarkdownQtiRenderer,
  type RenderedMarkdown
} from "./markdown-renderer.js";

export type QuestionType = "descriptive" | "choice" | "cloze";

export interface ScoringCriterion {
  points: Decimal;
  criterionXml: string;
}

export interface ParsedScoringCriterion {
  criterionXml: string;
}

export interface QtiConversionOptions {
  scoringPoints?: number[] | undefined;
}

export interface LocalImage {
  sourcePath: string;
  outputRelativePath: string;
}

export interface QtiConversionResult {
  qtiXml: string;
  localImages: LocalImage[];
  timeBudgetSeconds: number | null;
}

export class SectionContent {
  constructor(
    readonly name: string,
    readonly lines: string[],
    readonly headingLine: number,
    readonly startLine: number
  ) {}

  text(): string {
    return this.lines.join("\n");
  }

  isBlank(): boolean {
    return this.lines.every((line) => line.trim() === "");
  }
}

interface MarkdownQuestion {
  identifier: string;
  title: string;
  type: QuestionType;
  timeBudgetSeconds: number | null;
  prompt: RenderedMarkdown;
  explanation: RenderedMarkdown | null;
  options: ChoiceOption[];
  scoring: ScoringCriterion[];
}

interface QuestionFrontmatter {
  questionType: QuestionType;
  timeBudgetSeconds: number | null;
}

const ALLOWED_SECTION_NAMES = new Set(["Type", "Prompt", "Options", "Explanation", "Scoring"]);
const MIN_FENCE_LENGTH = 3;
const MAX_LEADING_SPACES = 3;

export function convertMarkdownToQti(markdown: string, fixtureId: string): string {
  const parsed = parseMarkdownQuestion(markdown, fixtureId, null);
  parsed.question.scoring = combineScoring(parsed.parsedScoring, undefined, null);
  return buildQti(parsed.question);
}

export function convertMarkdownToQtiWithAssets(
  markdown: string,
  fixtureId: string,
  sourcePath: string,
  options: QtiConversionOptions = {}
): QtiConversionResult {
  const parsed = parseMarkdownQuestion(markdown, fixtureId, sourcePath);
  parsed.question.scoring = combineScoring(parsed.parsedScoring, options.scoringPoints, sourcePath);
  const localImages = resolveLocalImages(parsed.imageSources, sourcePath);
  return {
    qtiXml: buildQti(parsed.question),
    localImages,
    timeBudgetSeconds: parsed.question.timeBudgetSeconds
  };
}

function combineScoring(
  parsedScoring: ParsedScoringCriterion[],
  scoringPoints: number[] | undefined,
  sourcePath: string | null
): ScoringCriterion[] {
  if (parsedScoring.length === 0 && scoringPoints === undefined) {
    return [];
  }
  if (parsedScoring.length === 0) {
    throw schemaError(
      "scoring criteria absent in question, but manifest item specifies points",
      sourcePath,
      null
    );
  }
  if (scoringPoints === undefined) {
    throw schemaError(
      "scoring criteria present, but manifest item points missing",
      sourcePath,
      null
    );
  }
  if (parsedScoring.length !== scoringPoints.length) {
    throw schemaError(
      `scoring criteria count (${parsedScoring.length}) does not match manifest points count (${scoringPoints.length})`,
      sourcePath,
      null
    );
  }
  return parsedScoring.map((criterion, index) => {
    const value = scoringPoints[index];
    if (value === undefined || !Number.isInteger(value) || value <= 0) {
      throw schemaError("manifest points must be positive integers", sourcePath, null);
    }
    return { points: parseDecimal(String(value)), criterionXml: criterion.criterionXml };
  });
}

interface MarkdownParseResult {
  question: MarkdownQuestion;
  imageSources: string[];
  parsedScoring: ParsedScoringCriterion[];
}

function parseMarkdownQuestion(
  markdown: string,
  identifier: string,
  sourcePath: string | null
): MarkdownParseResult {
  const normalized = markdown.replace(/\r\n/gu, "\n");
  const lines = normalized.split("\n");
  let index = 0;
  let frontmatter: QuestionFrontmatter | null = null;
  if (lines[0]?.trim() === "---") {
    const endIndex = lines.slice(1).findIndex((line) => line.trim() === "---");
    if (endIndex === -1) {
      throw new Error("Missing closing frontmatter delimiter");
    }
    const frontmatterLines = lines.slice(1, endIndex + 1);
    index = endIndex + 2;
    frontmatter = parseQuestionFrontmatter(frontmatterLines, sourcePath);
  }

  const nextNonEmptyLine = (): string | null => {
    while (index < lines.length) {
      const line = lines[index] ?? "";
      index += 1;
      if (line.trim() !== "") {
        return line;
      }
    }
    return null;
  };

  const titleLine = nextNonEmptyLine();
  if (titleLine === null) {
    throw new Error("Missing title heading");
  }
  if (!titleLine.startsWith("# ")) {
    throw new Error("Title must start with '# '");
  }
  const title = titleLine.slice(2).trim();
  if (title === "") {
    throw new Error("Title must not be empty");
  }

  const sectionsInOrder: SectionContent[] = [];
  const sectionsByName = new Map<string, SectionContent>();
  while (index < lines.length) {
    const line = lines[index] ?? "";
    if (line.trim() === "") {
      index += 1;
      continue;
    }
    if (!line.startsWith("## ")) {
      throw new Error(`Unexpected content outside section: ${line}`);
    }
    const headingLine = index + 1;
    const heading = line.slice(3).trim();
    if (!ALLOWED_SECTION_NAMES.has(heading)) {
      throw schemaError(`Unknown section heading: ${heading}`, sourcePath, headingLine);
    }
    if (sectionsByName.has(heading)) {
      throw schemaError(`Duplicate section heading: ${heading}`, sourcePath, headingLine);
    }
    index += 1;
    const content: string[] = [];
    const startLine = index + 1;
    let fence: FenceState | null = null;
    while (index < lines.length) {
      const current = lines[index] ?? "";
      if (fence === null && current.startsWith("## ")) {
        break;
      }
      fence = updateFenceState(current, fence);
      content.push(current);
      index += 1;
    }
    const section = new SectionContent(heading, content, headingLine, startLine);
    sectionsInOrder.push(section);
    sectionsByName.set(heading, section);
  }

  let type: QuestionType;
  if (frontmatter !== null) {
    const typeSection = sectionsByName.get("Type");
    if (typeSection !== undefined) {
      throw schemaError(
        "## Type is deprecated and not allowed with frontmatter",
        sourcePath,
        typeSection.headingLine
      );
    }
    type = frontmatter.questionType;
  } else {
    type = parseLegacyType(sectionsByName.get("Type"), sourcePath);
  }

  validateSectionOrder(sectionsInOrder, type, sourcePath, frontmatter !== null);

  const renderer = new MarkdownQtiRenderer();

  const promptSection = sectionsByName.get("Prompt");
  if (promptSection === undefined) {
    throw new Error("Missing ## Prompt section");
  }
  if (promptSection.isBlank()) {
    throw new Error("Prompt section must not be empty");
  }
  validateNoH1H2HeadingsInContent(promptSection, sourcePath);
  const promptRender = renderer.renderBlocks(
    promptSection.text(),
    { sectionName: "Prompt", sourcePath, sectionStartLine: promptSection.startLine },
    type === "cloze" ? "enabled" : "disabled"
  );
  if (type === "cloze" && promptRender.clozeBlanks.length === 0) {
    throw new Error("Cloze prompt must include at least one blank");
  }

  let explanationRender: RenderedMarkdown | null = null;
  const explanationSection = sectionsByName.get("Explanation");
  if (explanationSection !== undefined) {
    if (explanationSection.isBlank()) {
      throw new Error("Explanation section must not be empty");
    }
    validateNoH1H2HeadingsInContent(explanationSection, sourcePath);
    explanationRender = renderer.renderBlocks(
      explanationSection.text(),
      {
        sectionName: "Explanation",
        sourcePath,
        sectionStartLine: explanationSection.startLine
      },
      "disabled"
    );
  }

  const scoringSection = sectionsByName.get("Scoring");
  const parsedScoring =
    scoringSection === undefined ? [] : parseScoringSection(scoringSection, renderer, sourcePath);

  const optionsSection = sectionsByName.get("Options");
  let options: ChoiceOption[] = [];
  if (type === "choice") {
    if (optionsSection === undefined) {
      throw new Error("Missing ## Options section");
    }
    if (optionsSection.isBlank()) {
      throw new Error("Options must not be empty");
    }
    validateNoH1H2HeadingsInContent(optionsSection, sourcePath);
    const renderedOptions = renderer.renderChoiceOptions(optionsSection.text(), {
      sectionName: "Options",
      sourcePath,
      sectionStartLine: optionsSection.startLine
    });
    if (renderedOptions.length === 0) {
      throw new Error("Options must not be empty");
    }
    const correctCount = renderedOptions.filter((option) => option.isCorrect).length;
    if (correctCount !== 1) {
      throw new Error("Choice question must have exactly one correct option");
    }
    options = renderedOptions;
  } else if (optionsSection !== undefined) {
    throw schemaError(
      "## Options is only allowed for type 'choice'",
      sourcePath,
      optionsSection.headingLine
    );
  }

  const imageSources = new Set<string>();
  promptRender.localImages.forEach((image) => imageSources.add(image));
  explanationRender?.localImages.forEach((image) => imageSources.add(image));
  options.forEach((option) => option.localImages.forEach((image) => imageSources.add(image)));

  const question: MarkdownQuestion = {
    identifier,
    title,
    type,
    timeBudgetSeconds: frontmatter?.timeBudgetSeconds ?? null,
    prompt: promptRender,
    explanation: explanationRender,
    options,
    scoring: []
  };

  return { question, imageSources: [...imageSources], parsedScoring };
}

function parseQuestionFrontmatter(lines: string[], sourcePath: string | null): QuestionFrontmatter {
  const text = lines.join("\n");
  const document = parseDocument(text, { prettyErrors: false });
  if (document.errors.length > 0) {
    const messages = document.errors.map((err) => err.message).join("; ");
    throw schemaError(`Invalid frontmatter YAML: ${messages}`, sourcePath, 2);
  }
  const raw = document.toJS({}) ?? {};
  if (typeof raw !== "object" || raw === null || Array.isArray(raw)) {
    throw schemaError("Frontmatter must be a YAML map", sourcePath, 2);
  }
  const values = raw as Record<string, unknown>;
  if (!("question_type" in values)) {
    throw schemaError("Missing required frontmatter: question_type", sourcePath, 2);
  }
  const typeValue = values.question_type;
  if (typeof typeValue !== "string") {
    throw schemaError("question_type must be a string", sourcePath, 2);
  }
  let questionType: QuestionType;
  switch (typeValue) {
    case "descriptive":
    case "choice":
    case "cloze":
      questionType = typeValue;
      break;
    case "multi":
    case "order":
    case "match":
      throw new Error(`Unsupported question_type: ${typeValue}`);
    default:
      throw new Error(`Unknown question_type: ${typeValue}`);
  }
  const timeBudgetSeconds =
    "time_budget_seconds" in values
      ? parsePositiveInt(
          values.time_budget_seconds as string | number | undefined,
          "time_budget_seconds",
          sourcePath,
          2
        )
      : null;
  return { questionType, timeBudgetSeconds };
}

function parseLegacyType(
  typeSection: SectionContent | undefined,
  sourcePath: string | null
): QuestionType {
  if (typeSection === undefined) {
    throw new Error("Missing ## Type section");
  }
  const firstTypeLine = typeSection.lines[0];
  if (firstTypeLine === undefined) {
    throw schemaError("Type value missing", sourcePath, typeSection.startLine);
  }
  if (firstTypeLine.trim() === "") {
    throw schemaError(
      "Type value must be on the line immediately after ## Type",
      sourcePath,
      typeSection.startLine
    );
  }
  const typeValue = firstTypeLine.trim();
  if (typeSection.lines.slice(1).some((line) => line.trim() !== "")) {
    throw schemaError(
      "Type section must contain only a single word",
      sourcePath,
      typeSection.startLine
    );
  }
  switch (typeValue) {
    case "descriptive":
    case "choice":
    case "cloze":
      return typeValue;
    default:
      throw new Error(`Unknown question type: ${typeValue}`);
  }
}

export function parsePositiveInt(
  value: string | number | undefined,
  fieldName: string,
  sourcePath: string | null,
  line: number | null
): number {
  if (value === undefined || value === null) {
    throw schemaError(`Missing required value: ${fieldName}`, sourcePath, line);
  }
  let parsed: number;
  if (typeof value === "number") {
    parsed = value;
  } else if (typeof value === "string") {
    const trimmed = value.trim();
    if (!/^[+-]?[0-9]+$/u.test(trimmed)) {
      throw schemaError(`${fieldName} must be a positive integer`, sourcePath, line);
    }
    parsed = Number(trimmed);
  } else {
    throw schemaError(`${fieldName} must be a positive integer`, sourcePath, line);
  }
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw schemaError(`${fieldName} must be a positive integer`, sourcePath, line);
  }
  return parsed;
}

export function parseScoringSection(
  section: SectionContent,
  renderer: MarkdownQtiRenderer,
  sourcePath: string | null
): ParsedScoringCriterion[] {
  const criteria: ParsedScoringCriterion[] = [];
  const inlinePointsPattern = /^[0-9]+(?:\.[0-9]+)?:/u;
  section.lines.forEach((rawLine, index) => {
    if (rawLine.trim() === "") {
      return;
    }
    if (/^\s/u.test(rawLine)) {
      throw schemaError(
        "Scoring must be a single flat list (no indentation)",
        sourcePath,
        section.startLine + index
      );
    }
    if (!rawLine.startsWith("- ")) {
      throw schemaError(
        "Scoring section must be a Markdown list with '- <criterion>' items",
        sourcePath,
        section.startLine + index
      );
    }
    const content = rawLine.slice(2).trim();
    if (content === "") {
      throw schemaError(
        "Scoring criterion must not be empty",
        sourcePath,
        section.startLine + index
      );
    }
    if (inlinePointsPattern.test(content)) {
      throw schemaError(
        "points must be supplied by manifest item points",
        sourcePath,
        section.startLine + index
      );
    }
    const rendered = renderer.renderInline(
      content,
      { sectionName: "Scoring", sourcePath, sectionStartLine: section.startLine + index },
      "disabled"
    );
    criteria.push({ criterionXml: rendered.xml });
  });
  return criteria;
}

function validateSectionOrder(
  sections: SectionContent[],
  type: QuestionType,
  sourcePath: string | null,
  usesFrontmatter: boolean
): void {
  if (sections.length === 0) {
    throw new Error(usesFrontmatter ? "Missing ## Prompt section" : "Missing ## Type section");
  }
  const typeIndex = usesFrontmatter ? -1 : sections.findIndex((section) => section.name === "Type");
  if (!usesFrontmatter && typeIndex !== 0) {
    throw schemaError(
      "First section must be ## Type",
      sourcePath,
      sections[0]?.headingLine ?? null
    );
  }
  const promptIndex = sections.findIndex((section) => section.name === "Prompt");
  if (promptIndex === -1) {
    return;
  }
  if (!usesFrontmatter && promptIndex < typeIndex) {
    throw schemaError(
      "## Prompt must appear after ## Type",
      sourcePath,
      sections[promptIndex]?.headingLine ?? null
    );
  }
  const explanationIndex = sections.findIndex((section) => section.name === "Explanation");
  const scoringIndex = sections.findIndex((section) => section.name === "Scoring");
  if (type === "choice") {
    const optionsIndex = sections.findIndex((section) => section.name === "Options");
    if (optionsIndex !== -1 && optionsIndex < promptIndex) {
      throw schemaError(
        "## Options must appear after ## Prompt",
        sourcePath,
        sections[optionsIndex]?.headingLine ?? null
      );
    }
    if (explanationIndex !== -1 && optionsIndex !== -1 && explanationIndex < optionsIndex) {
      throw schemaError(
        "## Explanation must appear after ## Options",
        sourcePath,
        sections[explanationIndex]?.headingLine ?? null
      );
    }
    if (scoringIndex !== -1 && optionsIndex !== -1 && scoringIndex < optionsIndex) {
      throw schemaError(
        "## Scoring must appear after ## Options",
        sourcePath,
        sections[scoringIndex]?.headingLine ?? null
      );
    }
  } else {
    if (explanationIndex !== -1 && explanationIndex < promptIndex) {
      throw schemaError(
        "## Explanation must appear after ## Prompt",
        sourcePath,
        sections[explanationIndex]?.headingLine ?? null
      );
    }
    if (scoringIndex !== -1 && scoringIndex < promptIndex) {
      throw schemaError(
        "## Scoring must appear after ## Prompt",
        sourcePath,
        sections[scoringIndex]?.headingLine ?? null
      );
    }
  }
}

function validateNoH1H2HeadingsInContent(section: SectionContent, sourcePath: string | null): void {
  let fence: FenceState | null = null;
  section.lines.forEach((rawLine, index) => {
    fence = updateFenceState(rawLine, fence);
    if (fence !== null) {
      return;
    }
    const leadingSpaces = rawLine.length - rawLine.replace(/^ +/u, "").length;
    if (leadingSpaces > MAX_LEADING_SPACES) {
      return;
    }
    const rest = rawLine.slice(leadingSpaces);
    if (rest.startsWith("# ") || rest.startsWith("## ")) {
      throw schemaError(
        `Headings inside ## ${section.name} must use '###' or deeper (found '${rest.slice(0, 3).trim()}')`,
        sourcePath,
        section.startLine + index
      );
    }
  });
}

interface FenceState {
  fenceChar: string;
  fenceLength: number;
}

function updateFenceState(line: string, state: FenceState | null): FenceState | null {
  const leadingSpaces = line.length - line.replace(/^ +/u, "").length;
  if (leadingSpaces > MAX_LEADING_SPACES) {
    return state;
  }
  const rest = line.slice(leadingSpaces);
  const fenceChar = rest[0];
  if (fenceChar !== "`" && fenceChar !== "~") {
    return state;
  }
  let runLength = 0;
  while (rest[runLength] === fenceChar) {
    runLength += 1;
  }
  if (runLength < MIN_FENCE_LENGTH) {
    return state;
  }
  if (state === null) {
    return { fenceChar, fenceLength: runLength };
  }
  if (
    state.fenceChar === fenceChar &&
    runLength >= state.fenceLength &&
    rest.slice(runLength).trim() === ""
  ) {
    return null;
  }
  return state;
}

function schemaError(message: string, sourcePath: string | null, line: number | null): Error {
  let suffix = "";
  if (sourcePath !== null && line !== null) {
    suffix = ` (${path.resolve(sourcePath)}:${line})`;
  } else if (sourcePath !== null) {
    suffix = ` (${path.resolve(sourcePath)})`;
  } else if (line !== null) {
    suffix = ` (line ${line})`;
  }
  return new Error(message + suffix);
}

function resolveLocalImages(imageSources: string[], sourcePath: string): LocalImage[] {
  const sourceDir = path.dirname(sourcePath);
  const result: LocalImage[] = [];
  for (const source of imageSources) {
    if (isRemoteImagePath(source)) {
      continue;
    }
    if (path.isAbsolute(source)) {
      throw new Error(`Image path must be relative in ${path.resolve(sourcePath)}: ${source}`);
    }
    const outputRelativePath = safeLocalImagePath(source, sourcePath);
    const resolvedSource = path.resolve(sourceDir, outputRelativePath);
    if (!existsSync(resolvedSource) || !statSync(resolvedSource).isFile()) {
      throw new Error(`Image file not found in ${path.resolve(sourcePath)}: ${source}`);
    }
    result.push({ sourcePath: resolvedSource, outputRelativePath });
  }
  return result;
}

function safeLocalImagePath(source: string, sourcePath: string): string {
  const normalized = path.normalize(source);
  if (normalized === "." || normalized === "") {
    throw new Error(`Image path must not be empty in ${path.resolve(sourcePath)}: ${source}`);
  }
  if (
    path.isAbsolute(normalized) ||
    normalized === ".." ||
    normalized.startsWith(`..${path.sep}`)
  ) {
    throw new Error(
      `Image path must stay inside the question directory in ${path.resolve(sourcePath)}: ${source}`
    );
  }
  return normalized;
}

function isBlockContent(xml: string): boolean {
  if (xml.includes("<qti-p") || xml.includes("<qti-ul") || xml.includes("<qti-ol")) {
    return true;
  }
  if (xml.includes("<qti-blockquote") || xml.includes("<qti-pre") || xml.includes("<qti-table")) {
    return true;
  }
  if (xml.includes("<qti-hr") || xml.includes("<qti-h")) {
    return true;
  }
  return xml.includes("<qti-li");
}

function appendXml(xml: string): string {
  if (xml.trim() === "") {
    return "";
  }
  return `${xml.replace(/\s+$/u, "")}\n`;
}

function buildQti(question: MarkdownQuestion): string {
  let builder = '<?xml version="1.0" encoding="UTF-8"?>\n';
  builder +=
    "<qti-assessment-item\n" +
    '    xmlns="http://www.imsglobal.org/xsd/imsqti_v3p0"\n' +
    `    identifier="${escapeXml(question.identifier)}"\n` +
    `    title="${escapeXml(question.title)}"\n` +
    '    adaptive="false"\n' +
    '    time-dependent="false">\n';
  builder += buildResponseDeclaration(question);
  builder += buildOutcomeDeclarations(question);
  builder += buildItemBody(question);
  builder += buildResponseProcessing(question);
  builder += buildModalFeedback(question);
  builder += "</qti-assessment-item>\n";
  return builder;
}

function hasExplanation(question: MarkdownQuestion): boolean {
  return question.explanation !== null && question.explanation.xml.trim() !== "";
}

function buildResponseDeclaration(question: MarkdownQuestion): string {
  switch (question.type) {
    case "descriptive":
      return '  <qti-response-declaration identifier="RESPONSE" cardinality="single" base-type="string"/>\n';
    case "choice": {
      let builder =
        '  <qti-response-declaration identifier="RESPONSE" cardinality="single" base-type="identifier">\n';
      const correctIndex = question.options.findIndex((option) => option.isCorrect);
      const correctId = `CHOICE_${correctIndex + 1}`;
      builder += "    <qti-correct-response>\n";
      builder += `      <qti-value>${correctId}</qti-value>\n`;
      builder += "    </qti-correct-response>\n";
      builder += "  </qti-response-declaration>\n";
      return builder;
    }
    case "cloze": {
      const blanks = question.prompt.clozeBlanks;
      let builder = "";
      if (blanks.length === 1) {
        builder +=
          '  <qti-response-declaration identifier="RESPONSE" cardinality="single" base-type="string"';
        builder += buildClozeDeclarationBody(blanks[0] as ClozeBlank);
      } else {
        blanks.forEach((blank, index) => {
          const identifier = `RESPONSE_${index + 1}`;
          builder += `  <qti-response-declaration identifier="${identifier}" cardinality="single" base-type="string"`;
          builder += buildClozeDeclarationBody(blank);
        });
      }
      return builder;
    }
  }
}

function buildClozeDeclarationBody(blank: ClozeBlank): string {
  let builder = blank.kind === "regex" ? ' interpretation="regex">\n' : ">\n";
  builder += "    <qti-correct-response>\n";
  builder += `      <qti-value>${escapeXml(blank.answer)}</qti-value>\n`;
  builder += "    </qti-correct-response>\n";
  builder += "  </qti-response-declaration>\n";
  return builder;
}

function buildOutcomeDeclarations(question: MarkdownQuestion): string {
  if (question.scoring.length === 0 && !hasExplanation(question)) {
    return "";
  }
  let builder = "";
  if (question.scoring.length > 0) {
    const maxScore = question.scoring.reduce(
      (total, criterion) => addDecimals(total, criterion.points),
      DECIMAL_ZERO
    );
    builder +=
      '  <qti-outcome-declaration identifier="SCORE" cardinality="single" base-type="float"/>\n';
    builder +=
      '  <qti-outcome-declaration identifier="MAXSCORE" cardinality="single" base-type="float">\n';
    builder += "    <qti-default-value>\n";
    builder += `      <qti-value>${escapeXml(formatDecimal(maxScore))}</qti-value>\n`;
    builder += "    </qti-default-value>\n";
    builder += "  </qti-outcome-declaration>\n";
  }
  if (hasExplanation(question)) {
    builder +=
      '  <qti-outcome-declaration identifier="FEEDBACK" cardinality="single" base-type="identifier"/>\n';
  }
  return builder;
}

function buildItemBody(question: MarkdownQuestion): string {
  let builder = "  <qti-item-body>\n";
  builder += appendXml(question.prompt.xml);
  switch (question.type) {
    case "descriptive":
      builder += '    <qti-extended-text-interaction response-identifier="RESPONSE"/>\n';
      break;
    case "choice": {
      builder += '    <qti-choice-interaction response-identifier="RESPONSE" max-choices="1">\n';
      question.options.forEach((option, index) => {
        const identifier = `CHOICE_${index + 1}`;
        const content = option.contentXml.trim();
        if (isBlockContent(content)) {
          builder += `      <qti-simple-choice identifier="${identifier}">\n`;
          builder += appendXml(content);
          builder += "      </qti-simple-choice>\n";
        } else {
          builder += `      <qti-simple-choice identifier="${identifier}">${content}</qti-simple-choice>\n`;
        }
      });
      builder += "    </qti-choice-interaction>\n";
      break;
    }
    case "cloze":
      break;
  }

  if (question.scoring.length > 0) {
    builder += '    <qti-rubric-block view="scorer">\n';
    question.scoring.forEach((criterion) => {
      const points = formatDecimal(criterion.points);
      builder += `      <qti-p>[${escapeXml(points)}] ${criterion.criterionXml}</qti-p>\n`;
    });
    builder += "    </qti-rubric-block>\n";
  }
  builder += "  </qti-item-body>\n";
  return builder;
}

function buildResponseProcessing(question: MarkdownQuestion): string {
  if (!hasExplanation(question)) {
    return "";
  }
  let builder = "  <qti-response-processing>\n";
  builder += '    <qti-set-outcome-value identifier="FEEDBACK">\n';
  builder += '      <qti-base-value base-type="identifier">EXPLANATION</qti-base-value>\n';
  builder += "    </qti-set-outcome-value>\n";
  builder += "  </qti-response-processing>\n";
  return builder;
}

function buildModalFeedback(question: MarkdownQuestion): string {
  const explanation = question.explanation;
  if (explanation === null || explanation.xml.trim() === "") {
    return "";
  }
  let builder =
    '  <qti-modal-feedback outcome-identifier="FEEDBACK" identifier="EXPLANATION" show-hide="show">\n';
  builder += "    <qti-content-body>\n";
  builder += appendXml(explanation.xml);
  builder += "    </qti-content-body>\n";
  builder += "  </qti-modal-feedback>\n";
  return builder;
}
