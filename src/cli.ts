#!/usr/bin/env node

import {
  copyFileSync,
  existsSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  statSync,
  writeFileSync
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { isMap, isScalar, isSeq, LineCounter, type Node, parseDocument } from "yaml";

import {
  convertMarkdownToQtiWithAssets,
  type LocalImage,
  type QtiConversionResult
} from "./convert.js";
import { escapeXml } from "./escape-xml.js";

export const VERSION = "0.1.0";

interface CliOptions {
  inputPaths: string[];
  manifestPath: string | null;
  outputDir: string | null;
  validateOnly: boolean;
  verbose: boolean;
  testTitle: string | null;
  json: boolean;
}

interface AssessmentItemRef {
  identifier: string;
  href: string;
}

interface ManifestItemSpec {
  id: string;
  ref: string;
  points: number[] | null;
  resolvedPath: string;
}

interface ManifestSpec {
  title: string;
  timeLimitSeconds: number | null;
  items: ManifestItemSpec[];
}

interface ConvertedManifestItem {
  item: ManifestItemSpec;
  conversion: QtiConversionResult;
}

interface ConversionInputSource {
  identifier: string;
  displayName: string;
  path: string | null;
  readText: () => string;
}

type ParseResult = { kind: "success"; options: CliOptions } | { kind: "help" } | { kind: "error" };

export function runCli(
  args: string[],
  output: NodeJS.WritableStream = process.stdout,
  error: NodeJS.WritableStream = process.stderr
): number {
  const parsed = parseArgs(args, output, error);
  if (parsed.kind === "help") return 0;
  if (parsed.kind === "error") return 1;
  if (parsed.options.manifestPath !== null) {
    return runManifestCli(parsed.options, output, error);
  }
  const inputs = resolveInputs(parsed.options.inputPaths, error);
  if (inputs === null) return 1;

  const outputDir = parsed.options.outputDir;
  if (outputDir !== null && !parsed.options.validateOnly) {
    mkdirSync(outputDir, { recursive: true });
  }
  const assessmentItemsByOutputDir = new Map<string, AssessmentItemRef[]>();
  const generatedFiles: string[] = [];

  for (const inputSource of inputs) {
    const markdown = inputSource.readText();
    const identifier = inputSource.identifier;
    const sourcePath = inputSource.path ?? path.resolve(".");
    try {
      const conversion = convertMarkdownToQtiWithAssets(markdown, identifier, sourcePath);
      if (parsed.options.validateOnly) {
        if (parsed.options.verbose) writeln(output, `Validated: ${inputSource.displayName}`);
      } else {
        const resolvedOutputDir = path.normalize(outputDir ?? defaultOutputDirFor(sourcePath));
        mkdirSync(resolvedOutputDir, { recursive: true });
        const outputFile = path.join(resolvedOutputDir, `${identifier}.qti.xml`);
        writeFileSync(outputFile, conversion.qtiXml, "utf8");
        generatedFiles.push(path.resolve(outputFile));
        for (const copied of copyLocalImages(conversion.localImages, resolvedOutputDir)) {
          generatedFiles.push(path.resolve(copied));
        }
        registerAssessmentItem(assessmentItemsByOutputDir, resolvedOutputDir, identifier);
        if (parsed.options.verbose) writeln(output, `Wrote: ${path.resolve(outputFile)}`);
      }
    } catch (exception) {
      writeln(error, exception instanceof Error ? exception.message : String(exception));
      return 1;
    }
  }

  if (!parsed.options.validateOnly) {
    for (const written of writeAssessmentTests(
      assessmentItemsByOutputDir,
      parsed.options.testTitle ?? "",
      null,
      output,
      parsed.options.verbose
    )) {
      generatedFiles.push(path.resolve(written));
    }
  }
  if (parsed.options.json) writeJsonSummary(output, generatedFiles);
  return 0;
}

function parseArgs(
  args: string[],
  output: NodeJS.WritableStream,
  error: NodeJS.WritableStream
): ParseResult {
  if (args.length === 0) {
    printUsage(error);
    return { kind: "error" };
  }
  const inputPaths: string[] = [];
  let manifestPath: string | null = null;
  let outputDir: string | null = null;
  let validateOnly = false;
  let verbose = false;
  let testTitle: string | null = null;
  let json = false;

  for (let index = 0; index < args.length; ) {
    const arg = args[index];
    switch (arg) {
      case "--input": {
        const value = args[index + 1];
        if (value === undefined) {
          writeln(error, "Missing value for --input");
          return { kind: "error" };
        }
        inputPaths.push(value);
        index += 2;
        break;
      }
      case "--manifest": {
        const value = args[index + 1];
        if (value === undefined) {
          writeln(error, "Missing value for --manifest");
          return { kind: "error" };
        }
        manifestPath = value;
        index += 2;
        break;
      }
      case "--output-dir": {
        const value = args[index + 1];
        if (value === undefined) {
          writeln(error, "Missing value for --output-dir");
          return { kind: "error" };
        }
        outputDir = value;
        index += 2;
        break;
      }
      case "--validate-only":
      case "--dry-run":
        validateOnly = true;
        index += 1;
        break;
      case "--test-title": {
        const value = args[index + 1];
        if (value === undefined) {
          writeln(error, "Missing value for --test-title");
          return { kind: "error" };
        }
        testTitle = value;
        index += 2;
        break;
      }
      case "--verbose":
        verbose = true;
        index += 1;
        break;
      case "--json":
        json = true;
        index += 1;
        break;
      case "--version":
      case "-V":
        writeln(output, `markdown-to-qti version ${VERSION}`);
        return { kind: "help" };
      case "--help":
      case "-h":
        printUsage(output);
        return { kind: "help" };
      default:
        writeln(error, `Unknown argument: ${String(arg)}`);
        printUsage(error);
        return { kind: "error" };
    }
  }
  if (manifestPath !== null && inputPaths.length > 0) {
    writeln(error, "--manifest cannot be combined with --input.");
    return { kind: "error" };
  }
  if (manifestPath === null && inputPaths.length === 0) {
    writeln(error, "At least one --input is required.");
    return { kind: "error" };
  }
  if (manifestPath === null && (testTitle === null || testTitle.trim() === "")) {
    writeln(error, "--test-title is required.");
    return { kind: "error" };
  }
  return {
    kind: "success",
    options: { inputPaths, manifestPath, outputDir, validateOnly, verbose, testTitle, json }
  };
}

function runManifestCli(
  parsed: CliOptions,
  output: NodeJS.WritableStream,
  error: NodeJS.WritableStream
): number {
  const manifestPath = parsed.manifestPath;
  if (manifestPath === null) return 1;
  if (!existsSync(manifestPath)) {
    writeln(error, `Manifest not found: ${path.resolve(manifestPath)}`);
    return 1;
  }
  let manifest: ManifestSpec;
  try {
    manifest = parseManifest(manifestPath);
  } catch (exception) {
    writeln(error, exception instanceof Error ? exception.message : String(exception));
    return 1;
  }
  const outputDir = path.normalize(parsed.outputDir ?? defaultOutputDirFor(manifestPath));
  const generatedFiles: string[] = [];
  const itemRefs: AssessmentItemRef[] = [];
  const convertedItems: ConvertedManifestItem[] = [];

  for (const item of manifest.items) {
    const refPath = item.resolvedPath;
    let conversion: QtiConversionResult;
    try {
      conversion = convertMarkdownToQtiWithAssets(readFileSync(refPath, "utf8"), item.id, refPath, {
        scoringPoints: item.points ?? undefined
      });
    } catch (exception) {
      writeln(error, exception instanceof Error ? exception.message : String(exception));
      return 1;
    }
    convertedItems.push({ item, conversion });
  }

  let resolvedTimeLimit: number | null;
  try {
    resolvedTimeLimit = resolveManifestTimeLimit(manifest.timeLimitSeconds, convertedItems);
  } catch (exception) {
    writeln(error, exception instanceof Error ? exception.message : String(exception));
    return 1;
  }

  if (!parsed.validateOnly) mkdirSync(outputDir, { recursive: true });
  for (const { item, conversion } of convertedItems) {
    const refPath = item.resolvedPath;
    if (parsed.validateOnly) {
      if (parsed.verbose) writeln(output, `Validated: ${refPath}`);
    } else {
      const outputFile = path.join(outputDir, `${item.id}.qti.xml`);
      writeFileSync(outputFile, conversion.qtiXml, "utf8");
      generatedFiles.push(path.resolve(outputFile));
      for (const copied of copyLocalImages(conversion.localImages, outputDir)) {
        generatedFiles.push(path.resolve(copied));
      }
      if (parsed.verbose) writeln(output, `Wrote: ${path.resolve(outputFile)}`);
    }
    itemRefs.push({ identifier: item.id, href: `${item.id}.qti.xml` });
  }
  if (!parsed.validateOnly) {
    const assessmentXml = buildAssessmentTest(itemRefs, manifest.title, resolvedTimeLimit);
    const assessmentFile = path.join(outputDir, "assessment-test.qti.xml");
    writeFileSync(assessmentFile, assessmentXml, "utf8");
    generatedFiles.push(path.resolve(assessmentFile));
    if (parsed.verbose) writeln(output, `Wrote: ${path.resolve(assessmentFile)}`);
  }
  if (parsed.json) writeJsonSummary(output, generatedFiles);
  return 0;
}

function resolveManifestTimeLimit(
  manifestTimeLimitSeconds: number | null,
  convertedItems: ConvertedManifestItem[]
): number | null {
  if (manifestTimeLimitSeconds !== null) return manifestTimeLimitSeconds;

  const specifiedTimeBudgets = convertedItems.filter(
    ({ conversion }) => conversion.timeBudgetSeconds !== null
  );
  if (specifiedTimeBudgets.length === 0) return null;
  if (specifiedTimeBudgets.length !== convertedItems.length) {
    throw new Error(
      "time_limit_secondsのないmanifestでは、time_budget_secondsあり／なしを混在できない"
    );
  }
  return specifiedTimeBudgets.reduce(
    (sum, { conversion }) => sum + (conversion.timeBudgetSeconds ?? 0),
    0
  );
}

const MANIFEST_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]*$/u;
const WINDOWS_DRIVE_PATTERN = /^[A-Za-z]:/u;
const URL_SCHEME_PATTERN = /^[A-Za-z][A-Za-z0-9+.-]*:\/\//u;

function parseManifest(manifestPath: string): ManifestSpec {
  const resolvedManifest = path.resolve(manifestPath);
  const text = readFileSync(manifestPath, "utf8");
  const lineCounter = new LineCounter();
  const doc = parseDocument(text, { lineCounter });
  if (doc.errors.length > 0) {
    const first = doc.errors[0];
    throw manifestError(
      first?.message ?? "Invalid YAML",
      resolvedManifest,
      offsetLine(first?.pos?.[0], lineCounter)
    );
  }
  const root = doc.contents;
  if (!isMap(root)) {
    throw manifestError("Manifest must be a YAML mapping", resolvedManifest, null);
  }

  const allowedKeys = new Set(["title", "time_limit_seconds", "items"]);
  let titleNode: Node | null = null;
  let timeLimitNode: Node | null = null;
  let itemsNode: Node | null = null;
  for (const pair of root.items) {
    const keyNode = pair.key;
    const key = isScalar(keyNode) ? String(keyNode.value) : String(keyNode);
    const keyLine = nodeLine(keyNode, lineCounter);
    if (key === "type") {
      throw manifestError("Manifest field 'type' is not accepted", resolvedManifest, keyLine);
    }
    if (!allowedKeys.has(key)) {
      throw manifestError(`Unknown manifest field: ${key}`, resolvedManifest, keyLine);
    }
    if (key === "title") titleNode = pair.value as Node | null;
    else if (key === "time_limit_seconds") timeLimitNode = pair.value as Node | null;
    else itemsNode = pair.value as Node | null;
  }

  const title = scalarString(titleNode);
  if (title === null || title.trim() === "") {
    throw manifestError(
      "Manifest title is required and must be a non-empty string",
      resolvedManifest,
      nodeLine(titleNode, lineCounter)
    );
  }

  let timeLimitSeconds: number | null = null;
  if (isPresentNode(timeLimitNode)) {
    timeLimitSeconds = positiveInteger(
      timeLimitNode,
      "time_limit_seconds",
      resolvedManifest,
      lineCounter
    );
  }

  if (!isPresentNode(itemsNode) || !isSeq(itemsNode)) {
    throw manifestError(
      "Manifest items are required and must be a list",
      resolvedManifest,
      nodeLine(itemsNode, lineCounter)
    );
  }
  if (itemsNode.items.length === 0) {
    throw manifestError("Manifest items must not be empty", resolvedManifest, null);
  }

  const items: ManifestItemSpec[] = [];
  const seenIds = new Set<string>();
  const manifestDir = path.dirname(resolvedManifest);
  for (const itemNode of itemsNode.items) {
    const itemLine = nodeLine(itemNode as Node, lineCounter);
    if (!isMap(itemNode)) {
      throw manifestError(
        "Manifest item must be a mapping with keys id, ref, and optional points",
        resolvedManifest,
        itemLine
      );
    }
    const itemAllowed = new Set(["id", "ref", "points"]);
    let idNode: Node | null = null;
    let refNode: Node | null = null;
    let pointsNode: Node | null = null;
    for (const pair of itemNode.items) {
      const keyNode = pair.key as Node | null;
      const key = isScalar(keyNode) ? String(keyNode.value) : String(keyNode);
      const keyLine = nodeLine(keyNode, lineCounter);
      if (!itemAllowed.has(key)) {
        throw manifestError(`Unknown manifest item field: ${key}`, resolvedManifest, keyLine);
      }
      if (key === "id") idNode = pair.value as Node | null;
      else if (key === "ref") refNode = pair.value as Node | null;
      else pointsNode = pair.value as Node | null;
    }

    const id = scalarString(idNode);
    if (id === null || !MANIFEST_ID_PATTERN.test(id)) {
      throw manifestError(
        "Manifest item id is required and must match /^[A-Za-z0-9][A-Za-z0-9_.:-]*$/",
        resolvedManifest,
        nodeLine(idNode ?? (itemNode as Node), lineCounter)
      );
    }
    if (seenIds.has(id)) {
      throw manifestError(
        `Duplicate manifest item id: ${id}`,
        resolvedManifest,
        nodeLine(idNode, lineCounter)
      );
    }

    const ref = scalarString(refNode);
    const refLine = nodeLine(refNode ?? (itemNode as Node), lineCounter);
    if (ref === null || ref.trim() === "") {
      throw manifestError(
        "Manifest item ref is required and must be a non-empty string",
        resolvedManifest,
        refLine
      );
    }
    if (ref.startsWith("/") || ref.startsWith("\\") || WINDOWS_DRIVE_PATTERN.test(ref)) {
      throw manifestError(
        `Manifest item ref must be a relative path, not absolute: ${ref}`,
        resolvedManifest,
        refLine
      );
    }
    if (URL_SCHEME_PATTERN.test(ref)) {
      throw manifestError(
        `Manifest item ref must be a local path, not a URL: ${ref}`,
        resolvedManifest,
        refLine
      );
    }

    const points = parseManifestPoints(pointsNode, resolvedManifest, lineCounter);

    const resolvedPath = path.resolve(manifestDir, ref);
    if (!existsSync(resolvedPath)) {
      throw manifestError(
        `Manifest item ref not found: ${resolvedPath}`,
        resolvedManifest,
        refLine
      );
    }

    seenIds.add(id);
    items.push({ id, ref, points, resolvedPath });
  }

  return { title, timeLimitSeconds, items };
}

function parseManifestPoints(
  pointsNode: Node | null,
  resolvedManifest: string,
  lineCounter: LineCounter
): number[] | null {
  if (!isPresentNode(pointsNode)) {
    return null;
  }
  const pointsLine = nodeLine(pointsNode, lineCounter);
  if (!isSeq(pointsNode)) {
    throw manifestError(
      "Manifest item points must be a list of positive integers",
      resolvedManifest,
      pointsLine
    );
  }
  const result: number[] = [];
  for (const element of pointsNode.items) {
    if (
      !isScalar(element) ||
      typeof element.value !== "number" ||
      !Number.isInteger(element.value) ||
      element.value <= 0
    ) {
      throw manifestError(
        "Manifest item points must be positive integers",
        resolvedManifest,
        nodeLine(element as Node, lineCounter)
      );
    }
    result.push(element.value);
  }
  return result;
}

function isPresentNode(node: Node | null): node is Node {
  return node !== null && node !== undefined;
}

function scalarString(node: Node | null): string | null {
  if (isScalar(node) && typeof node.value === "string") {
    return node.value;
  }
  return null;
}

function positiveInteger(
  node: Node | null,
  fieldName: string,
  resolvedManifest: string,
  lineCounter: LineCounter
): number {
  if (
    isScalar(node) &&
    typeof node.value === "number" &&
    Number.isInteger(node.value) &&
    node.value > 0
  ) {
    return node.value;
  }
  throw manifestError(
    `${fieldName} must be a positive integer`,
    resolvedManifest,
    nodeLine(node, lineCounter)
  );
}

function nodeLine(node: Node | null, lineCounter: LineCounter): number | null {
  const range = node?.range;
  if (range) {
    return lineCounter.linePos(range[0]).line;
  }
  return null;
}

function offsetLine(offset: number | undefined, lineCounter: LineCounter): number | null {
  if (offset === undefined) {
    return null;
  }
  return lineCounter.linePos(offset).line;
}

function manifestError(message: string, resolvedManifest: string, line: number | null): Error {
  const suffix = line !== null ? `${resolvedManifest}:${line}` : resolvedManifest;
  return new Error(`${message} (${suffix})`);
}

function resolveInputs(
  paths: string[],
  error: NodeJS.WritableStream
): ConversionInputSource[] | null {
  const resolved: ConversionInputSource[] = [];
  for (const inputPath of paths) {
    if (inputPath === "-") {
      resolved.push({
        identifier: "stdin",
        displayName: "stdin",
        path: null,
        readText: () => readFileSync(0, "utf8")
      });
      continue;
    }
    if (!existsSync(inputPath)) {
      writeln(error, `Input not found: ${path.resolve(inputPath)}`);
      return null;
    }
    if (statSync(inputPath).isDirectory()) {
      for (const entry of readdirSync(inputPath)) {
        const candidate = path.join(inputPath, entry);
        if (statSync(candidate).isFile() && path.extname(candidate).toLowerCase() === ".md") {
          resolved.push({
            identifier: fileNameWithoutExtension(candidate),
            displayName: path.resolve(candidate),
            path: candidate,
            readText: () => readFileSync(candidate, "utf8")
          });
        }
      }
    } else {
      resolved.push({
        identifier: fileNameWithoutExtension(inputPath),
        displayName: path.resolve(inputPath),
        path: inputPath,
        readText: () => readFileSync(inputPath, "utf8")
      });
    }
  }
  if (resolved.length === 0) {
    writeln(error, "No Markdown files found in inputs.");
    return null;
  }
  return resolved;
}

function copyLocalImages(images: LocalImage[], outputDir: string): string[] {
  const copied: string[] = [];
  const resolvedOutputDir = path.resolve(outputDir);
  for (const image of images) {
    const destination = path.resolve(resolvedOutputDir, image.outputRelativePath);
    if (!isPathInsideDirectory(destination, resolvedOutputDir)) {
      throw new Error(`Image output path escapes output directory: ${image.outputRelativePath}`);
    }
    mkdirSync(path.dirname(destination), { recursive: true });
    copyFileSync(image.sourcePath, destination);
    copied.push(destination);
  }
  return copied;
}

function isPathInsideDirectory(candidatePath: string, directoryPath: string): boolean {
  const relative = path.relative(directoryPath, candidatePath);
  return relative === "" || (!relative.startsWith("..") && !path.isAbsolute(relative));
}

function printUsage(stream: NodeJS.WritableStream): void {
  writeln(
    stream,
    "Usage: markdown-to-qti --manifest <path> | --input <path> [--input <path> ...] --test-title <title> [--output-dir <dir>] [--validate-only] [--dry-run] [--verbose] [--version] [--json]"
  );
  writeln(stream, "Options:");
  writeln(stream, "  --manifest <path>  Manifest YAML file for the canonical package conversion.");
  writeln(
    stream,
    "  --input <path>      Markdown file or directory (directories scan for *.md). Use '-' for stdin."
  );
  writeln(stream, "  --test-title <title> Assessment test title (required).");
  writeln(
    stream,
    "  --output-dir <dir>  Output directory for .qti.xml files. Defaults to qti-out under each input file directory."
  );
  writeln(stream, "  --validate-only     Parse and validate XML without writing files.");
  writeln(stream, "  --dry-run           Alias for --validate-only.");
  writeln(stream, "  --verbose           Log processed files.");
  writeln(stream, "  --json              Output machine-readable JSON summary to stdout.");
  writeln(stream, "  --version, -V       Show version.");
  writeln(stream, "  --help, -h          Show help.");
}

function defaultOutputDirFor(inputPath: string): string {
  return path.join(path.dirname(path.resolve(inputPath)), "qti-out");
}

function registerAssessmentItem(
  map: Map<string, AssessmentItemRef[]>,
  outputDir: string,
  identifier: string
): void {
  const items = map.get(outputDir) ?? [];
  items.push({ identifier, href: `${identifier}.qti.xml` });
  map.set(outputDir, items);
}

function writeAssessmentTests(
  assessmentItemsByOutputDir: Map<string, AssessmentItemRef[]>,
  testTitle: string,
  timeLimitSeconds: number | null,
  output: NodeJS.WritableStream,
  verbose: boolean
): string[] {
  const written: string[] = [];
  for (const [outputDir, items] of assessmentItemsByOutputDir.entries()) {
    if (items.length === 0) continue;
    const xml = buildAssessmentTest(items, testTitle, timeLimitSeconds);
    const testFile = path.join(outputDir, "assessment-test.qti.xml");
    writeFileSync(testFile, xml, "utf8");
    written.push(testFile);
    if (verbose) writeln(output, `Wrote: ${path.resolve(testFile)}`);
  }
  return written;
}

export function buildAssessmentTest(
  items: AssessmentItemRef[],
  testTitle: string,
  timeLimitSeconds: number | null
): string {
  let builder = '<?xml version="1.0" encoding="UTF-8"?>\n';
  builder +=
    "<qti-assessment-test\n" +
    '    xmlns="http://www.imsglobal.org/xsd/imsqti_v3p0"\n' +
    '    identifier="assessment-test"\n' +
    `    title="${escapeXml(testTitle)}">\n`;
  builder +=
    '  <qti-test-part identifier="part-1" navigation-mode="linear" submission-mode="individual">\n';
  if (timeLimitSeconds !== null)
    builder += `    <qti-time-limits max-time="${timeLimitSeconds}"/>\n`;
  builder +=
    '    <qti-assessment-section identifier="section-1" title="Section 1" visible="true">\n';
  for (const item of items) {
    builder += `      <qti-assessment-item-ref identifier="${escapeXml(item.identifier)}" href="${escapeXml(item.href)}"/>\n`;
  }
  builder += "    </qti-assessment-section>\n";
  builder += "  </qti-test-part>\n";
  builder += "</qti-assessment-test>\n";
  return builder;
}

function writeJsonSummary(output: NodeJS.WritableStream, generatedFiles: string[]): void {
  const lines = ["{", `  \"version\": \"${VERSION}\",`, '  "generatedFiles": ['];
  generatedFiles.forEach((file, index) => {
    const comma = index < generatedFiles.length - 1 ? "," : "";
    lines.push(`    \"${file.replaceAll("\\", "\\\\").replaceAll('"', '\\"')}\"${comma}`);
  });
  lines.push("  ]", "}");
  writeln(output, lines.join("\n"));
}

function fileNameWithoutExtension(value: string): string {
  const filename = path.basename(value);
  return filename.includes(".") ? filename.slice(0, filename.lastIndexOf(".")) : filename;
}

function writeln(stream: NodeJS.WritableStream, message: string): void {
  stream.write(`${message}\n`);
}

const thisFile = fileURLToPath(import.meta.url);
if (path.resolve(process.argv[1] ?? "") === thisFile) {
  process.exit(runCli(process.argv.slice(2)));
}
