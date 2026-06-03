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
    const fixtureId = "choice-with-scoring";
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
      /qti-assessment-item-ref[\s\S]*identifier="choice-with-scoring"[\s\S]*href="choice-with-scoring\.qti\.xml"/u
    );
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
});

test("cli validate-only does not write output", async () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-cli-"));
  try {
    const outputDir = path.join(tempDir, "out");
    const fixtureId = "descriptive-with-scoring";
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
    const first = "choice-with-scoring";
    const second = "descriptive-with-scoring";
    writeFileSync(path.join(tempDir, `${first}.md`), readFixture(`${first}.md`), "utf8");
    writeFileSync(path.join(tempDir, `${second}.md`), readFixture(`${second}.md`), "utf8");
    const manifestPath = path.join(tempDir, "assessment.yaml");
    writeFileSync(
      manifestPath,
      ["title: Assessment Test", "items:", `  - ./${first}.md`, `  - ./${second}.md`, ""].join(
        "\n"
      ),
      "utf8"
    );

    const exitCode = runCli(["--manifest", manifestPath, "--output-dir", outputDir]);

    assert.equal(exitCode, 0);
    assert.equal(existsSync(path.join(outputDir, `${first}.qti.xml`)), true);
    assert.equal(existsSync(path.join(outputDir, `${second}.qti.xml`)), true);
    const assessmentTest = readFileSync(path.join(outputDir, "assessment-test.qti.xml"), "utf8");
    assert.match(assessmentTest, /<qti-time-limits max-time="180"\/>/u);
    assert.match(
      assessmentTest,
      /identifier="choice-with-scoring"[\s\S]*identifier="descriptive-with-scoring"/u
    );
  } finally {
    await rm(tempDir, { force: true, recursive: true });
  }
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

function readFixture(name: string): string {
  return readFileSync(path.join(fixturesDir, name), "utf8");
}

function normalizeXml(xml: string): string {
  return xml.replaceAll("\r\n", "\n").replaceAll(/^\s+</gmu, "<");
}

function devNullStream(): NodeJS.WritableStream {
  return new Writable({
    write(_chunk, _encoding, callback) {
      callback();
    }
  });
}
