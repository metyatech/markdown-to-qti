import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { mkdir, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { Writable } from "node:stream";
import test from "node:test";

import { runCli } from "../src/cli.js";

const fixturesDir = path.resolve("src/test/resources/fixtures");

test("cli writes QTI output and assessment-test for direct input", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    const fixtureId = "descriptive-with-explanation";
    const inputFile = path.join(tempDir, `${fixtureId}.md`);
    writeFileSync(inputFile, readFixture(`${fixtureId}.md`), "utf8");

    const exitCode = runCli(
      ["--input", inputFile, "--test-title", "Assessment Test", "--output-dir", outputDir],
      devNullStream(),
      devNullStream()
    );

    assert.equal(exitCode, 0);
    assert.equal(
      normalizeXml(readFileSync(path.join(outputDir, `${fixtureId}.qti.xml`), "utf8")),
      normalizeXml(readFixture(`${fixtureId}.qti.xml`))
    );
    assert.match(
      readFileSync(path.join(outputDir, "assessment-test.qti.xml"), "utf8"),
      /qti-assessment-item-ref[\s\S]*identifier="descriptive-with-explanation"[\s\S]*href="descriptive-with-explanation\.qti\.xml"/u
    );
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli validate-only does not write output", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    const fixtureId = "descriptive-with-markdown";
    const inputFile = path.join(tempDir, `${fixtureId}.md`);
    writeFileSync(inputFile, readFixture(`${fixtureId}.md`), "utf8");

    const exitCode = runCli([
      "--input",
      inputFile,
      "--test-title",
      "Assessment Test",
      "--output-dir",
      outputDir,
      "--validate-only"
    ]);

    assert.equal(exitCode, 0);
    assert.equal(existsSync(outputDir), false);
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli manifest writes ordered package with summed time limit", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    writeFixtureCopy(tempDir, "choice-with-scoring");
    writeFixtureCopy(tempDir, "descriptive-with-scoring");
    const manifestPath = writeManifest(tempDir, [
      "title: Assessment Test",
      "items:",
      "  - id: q1",
      "    ref: ./choice-with-scoring.md",
      "    points: [2, 1]",
      "  - id: q2",
      "    ref: ./descriptive-with-scoring.md",
      "    points: [2, 1]"
    ]);

    const exitCode = runCli(["--manifest", manifestPath, "--output-dir", outputDir]);

    assert.equal(exitCode, 0);
    // Item output file names and identifiers come from the manifest item id.
    const firstItemXml = readFileSync(path.join(outputDir, "q1.qti.xml"), "utf8");
    const secondItemXml = readFileSync(path.join(outputDir, "q2.qti.xml"), "utf8");
    assert.match(firstItemXml, /identifier="q1"/u);
    assert.match(secondItemXml, /identifier="q2"/u);
    // Points supplied by the manifest still drive the scoring rubric.
    assert.match(firstItemXml, /<qti-p>\[2\] Selects the only prime number<\/qti-p>/u);

    const assessmentTest = readFileSync(path.join(outputDir, "assessment-test.qti.xml"), "utf8");
    assert.match(assessmentTest, /<qti-time-limits max-time="180"\/>/u);
    assert.match(
      assessmentTest,
      /identifier="q1"[\s\S]*href="q1\.qti\.xml"[\s\S]*identifier="q2"[\s\S]*href="q2\.qti\.xml"/u
    );
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli manifest reads plain object items without points", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    writeFileSync(path.join(tempDir, "plain.md"), plainQuestion("Plain"), "utf8");
    const manifestPath = writeManifest(tempDir, [
      "title: Assessment Test",
      "items:",
      "  - id: only",
      "    ref: ./plain.md"
    ]);

    const exitCode = runCli(["--manifest", manifestPath, "--output-dir", outputDir]);

    assert.equal(exitCode, 0);
    assert.equal(existsSync(path.join(outputDir, "only.qti.xml")), true);
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli manifest rejects a string item", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - ./plain.md"],
    { "plain.md": plainQuestion("Plain") },
    /must be a mapping/u
  );
});

test("cli manifest rejects an item missing id", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - ref: ./plain.md"],
    { "plain.md": plainQuestion("Plain") },
    /id is required/u
  );
});

test("cli manifest rejects an item missing ref", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1"],
    {},
    /ref is required/u
  );
});

test("cli manifest rejects a duplicate id", async () => {
  await expectManifestError(
    [
      "title: Assessment Test",
      "items:",
      "  - id: dup",
      "    ref: ./plain.md",
      "  - id: dup",
      "    ref: ./plain.md"
    ],
    { "plain.md": plainQuestion("Plain") },
    /Duplicate manifest item id: dup/u
  );
});

test("cli manifest rejects an unknown item field", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1", "    ref: ./plain.md", "    weight: 2"],
    { "plain.md": plainQuestion("Plain") },
    /Unknown manifest item field: weight/u
  );
});

test("cli manifest rejects a root type field", async () => {
  await expectManifestError(
    ["title: Assessment Test", "type: exam", "items:", "  - id: q1", "    ref: ./plain.md"],
    { "plain.md": plainQuestion("Plain") },
    /'type' is not accepted/u
  );
});

test("cli manifest rejects points that are not a positive integer array", async () => {
  const invalidPoints = ["[-1]", "[0]", "[1.5]", "5"];
  for (const points of invalidPoints) {
    await expectManifestError(
      [
        "title: Assessment Test",
        "items:",
        "  - id: q1",
        "    ref: ./scoring.md",
        `    points: ${points}`
      ],
      { "scoring.md": scoringQuestion("Scoring", ["First", "Second"]) },
      /points must be (a list of )?positive integers/u
    );
  }
});

test("cli manifest errors when scoring is present but points are missing", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1", "    ref: ./scoring.md"],
    { "scoring.md": scoringQuestion("Scoring", ["First", "Second"]) },
    /scoring criteria present, but manifest item points missing/u
  );
});

test("cli manifest errors when points count differs from scoring criteria", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1", "    ref: ./scoring.md", "    points: [2]"],
    { "scoring.md": scoringQuestion("Scoring", ["First", "Second"]) },
    /scoring criteria count \(2\) does not match manifest points count \(1\)/u
  );
});

test("cli manifest errors when points are provided without scoring criteria", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1", "    ref: ./plain.md", "    points: [2]"],
    { "plain.md": plainQuestion("Plain") },
    /scoring criteria absent in question, but manifest item specifies points/u
  );
});

test("cli manifest rejects inline points in a scoring criterion", async () => {
  await expectManifestError(
    ["title: Assessment Test", "items:", "  - id: q1", "    ref: ./scoring.md", "    points: [2]"],
    { "scoring.md": scoringQuestion("Scoring", ["2: keeps points inline"]) },
    /points must be supplied by manifest item points/u
  );
});

test("cli copies local images to output directory", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    await mkdir(path.join(tempDir, "images"));
    writeFileSync(path.join(tempDir, "images", "diagram.png"), "fake image", "utf8");
    const inputFile = path.join(tempDir, "image-prompt.md");
    writeFileSync(
      inputFile,
      [
        "---",
        "question_type: descriptive",
        "time_budget_seconds: 60",
        "---",
        "# Image Prompt",
        "",
        "## Prompt",
        "Identify this.",
        "",
        '![Alt text](images/diagram.png "Diagram")',
        ""
      ].join("\n"),
      "utf8"
    );

    const exitCode = runCli(
      ["--input", inputFile, "--test-title", "Assessment Test", "--output-dir", outputDir],
      devNullStream(),
      devNullStream()
    );

    assert.equal(exitCode, 0);
    assert.equal(existsSync(path.join(outputDir, "images", "diagram.png")), true);
    assert.match(
      readFileSync(path.join(outputDir, "image-prompt.qti.xml"), "utf8"),
      /<qti-img src="images\/diagram\.png" alt="Alt text" title="Diagram"\/>/u
    );
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli rejects local image paths that escape the output directory", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const sourceDir = path.join(tempDir, "source", "questions");
    const sourceAssetsDir = path.join(tempDir, "source", "assets");
    const outputDir = path.join(tempDir, "out");
    await mkdir(sourceDir, { recursive: true });
    await mkdir(sourceAssetsDir, { recursive: true });
    writeFileSync(path.join(sourceAssetsDir, "pic.txt"), "secret", "utf8");
    const escapedDestination = path.join(tempDir, "assets", "pic.txt");
    const inputFile = path.join(sourceDir, "image-prompt.md");
    writeFileSync(
      inputFile,
      [
        "---",
        "question_type: descriptive",
        "time_budget_seconds: 60",
        "---",
        "# Image Prompt",
        "",
        "## Prompt",
        "Do not copy this.",
        "",
        "![Alt text](../assets/pic.txt)",
        ""
      ].join("\n"),
      "utf8"
    );

    const exitCode = runCli(
      ["--input", inputFile, "--test-title", "Assessment Test", "--output-dir", outputDir],
      devNullStream(),
      devNullStream()
    );

    assert.equal(exitCode, 1);
    assert.equal(existsSync(escapedDestination), false);
    assert.equal(existsSync(path.join(outputDir, "image-prompt.qti.xml")), false);
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

async function expectManifestError(
  manifestBody: string[],
  files: Record<string, string>,
  pattern: RegExp
): Promise<void> {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    for (const [name, content] of Object.entries(files)) {
      writeFileSync(path.join(tempDir, name), content, "utf8");
    }
    const outputDir = path.join(tempDir, "out");
    const manifestPath = writeManifest(tempDir, manifestBody);
    const stderr = captureStream();

    const exitCode = runCli(
      ["--manifest", manifestPath, "--output-dir", outputDir],
      devNullStream(),
      stderr.stream
    );

    assert.equal(exitCode, 1);
    assert.match(stderr.text(), pattern);
    assert.equal(existsSync(path.join(outputDir, "q1.qti.xml")), false);
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
}

function writeManifest(dir: string, body: string[]): string {
  const manifestPath = path.join(dir, "assessment.yaml");
  writeFileSync(manifestPath, `${body.join("\n")}\n`, "utf8");
  return manifestPath;
}

function writeFixtureCopy(dir: string, fixtureId: string): void {
  writeFileSync(path.join(dir, `${fixtureId}.md`), readFixture(`${fixtureId}.md`), "utf8");
}

function plainQuestion(title: string): string {
  return [
    "---",
    "question_type: descriptive",
    "time_budget_seconds: 60",
    "---",
    `# ${title}`,
    "",
    "## Prompt",
    "Explain something.",
    ""
  ].join("\n");
}

function scoringQuestion(title: string, criteria: string[]): string {
  return [
    "---",
    "question_type: descriptive",
    "time_budget_seconds: 60",
    "---",
    `# ${title}`,
    "",
    "## Prompt",
    "Explain something.",
    "",
    "## Scoring",
    ...criteria.map((criterion) => `- ${criterion}`),
    ""
  ].join("\n");
}

function readFixture(name: string): string {
  return readFileSync(path.join(fixturesDir, name), "utf8");
}

function normalizeXml(xml: string): string {
  return xml.replaceAll("\r\n", "\n").replaceAll(/^\s+</gmu, "<");
}

function captureStream(): { stream: NodeJS.WritableStream; text: () => string } {
  const chunks: string[] = [];
  const stream = new Writable({
    write(chunk, _encoding, callback) {
      chunks.push(String(chunk));
      callback();
    }
  });
  return { stream, text: () => chunks.join("") };
}

function devNullStream(): NodeJS.WritableStream {
  return new Writable({
    write(_chunk, _encoding, callback) {
      callback();
    }
  });
}
