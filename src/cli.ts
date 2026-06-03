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

import { convertMarkdownToQtiWithAssets, parsePositiveInt, type LocalImage } from "./convert.js";
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

interface ManifestSpec {
  title: string;
  timeLimitSeconds: number | null;
  itemPaths: string[];
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
  let summedTimeBudget = 0;
  if (!parsed.validateOnly) mkdirSync(outputDir, { recursive: true });

  for (const itemPath of manifest.itemPaths) {
    if (!existsSync(itemPath)) {
      writeln(error, `Manifest item not found: ${path.resolve(itemPath)}`);
      return 1;
    }
    const identifier = fileNameWithoutExtension(itemPath);
    let conversion;
    try {
      conversion = convertMarkdownToQtiWithAssets(
        readFileSync(itemPath, "utf8"),
        identifier,
        itemPath
      );
    } catch (exception) {
      writeln(error, exception instanceof Error ? exception.message : String(exception));
      return 1;
    }
    summedTimeBudget += conversion.timeBudgetSeconds ?? 0;
    if (parsed.validateOnly) {
      if (parsed.verbose) writeln(output, `Validated: ${path.resolve(itemPath)}`);
    } else {
      const outputFile = path.join(outputDir, `${identifier}.qti.xml`);
      writeFileSync(outputFile, conversion.qtiXml, "utf8");
      generatedFiles.push(path.resolve(outputFile));
      for (const copied of copyLocalImages(conversion.localImages, outputDir)) {
        generatedFiles.push(path.resolve(copied));
      }
      if (parsed.verbose) writeln(output, `Wrote: ${path.resolve(outputFile)}`);
    }
    itemRefs.push({ identifier, href: `${identifier}.qti.xml` });
  }
  const resolvedTimeLimit = manifest.timeLimitSeconds ?? summedTimeBudget;
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

function parseManifest(manifestPath: string): ManifestSpec {
  const lines = readFileSync(manifestPath, "utf8").replace(/\r\n/gu, "\n").split("\n");
  let title: string | null = null;
  let timeLimitSeconds: number | null = null;
  const items: string[] = [];
  let inItems = false;
  for (let index = 0; index < lines.length; index += 1) {
    const rawLine = lines[index] ?? "";
    const lineNumber = index + 1;
    const line = rawLine.trim();
    if (line === "" || line.startsWith("#")) continue;
    if (inItems && rawLine.startsWith("  - ")) {
      const item = trimQuotes(rawLine.slice(4).trim());
      if (item === "")
        throw new Error(
          `Manifest item must not be empty (${path.resolve(manifestPath)}:${lineNumber})`
        );
      items.push(path.normalize(path.resolve(path.dirname(path.resolve(manifestPath)), item)));
      continue;
    }
    inItems = false;
    if (line.startsWith("title:")) {
      title = trimQuotes(line.slice("title:".length).trim());
    } else if (line.startsWith("time_limit_seconds:")) {
      timeLimitSeconds = parsePositiveInt(
        line.slice("time_limit_seconds:".length).trim(),
        "time_limit_seconds",
        manifestPath,
        lineNumber
      );
    } else if (line === "items:") {
      inItems = true;
    } else if (line.startsWith("type:")) {
      throw new Error("Manifest field 'type' is deprecated and not accepted");
    } else {
      throw new Error(
        `Unknown manifest field (${path.resolve(manifestPath)}:${lineNumber}): ${line}`
      );
    }
  }
  const resolvedTitle = title;
  if (resolvedTitle === null || resolvedTitle.trim() === "")
    throw new Error(`Manifest title is required (${path.resolve(manifestPath)})`);
  if (items.length === 0)
    throw new Error(`Manifest items are required (${path.resolve(manifestPath)})`);
  return { title: resolvedTitle, timeLimitSeconds, itemPaths: items };
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

function trimQuotes(value: string): string {
  let start = 0;
  let end = value.length;
  while (start < end && isQuoteCode(value.charCodeAt(start))) start += 1;
  while (end > start && isQuoteCode(value.charCodeAt(end - 1))) end -= 1;
  return value.slice(start, end);
}

function isQuoteCode(code: number): boolean {
  return code === 34 || code === 39;
}

function writeln(stream: NodeJS.WritableStream, message: string): void {
  stream.write(`${message}\n`);
}

const thisFile = fileURLToPath(import.meta.url);
if (path.resolve(process.argv[1] ?? "") === thisFile) {
  process.exit(runCli(process.argv.slice(2)));
}
