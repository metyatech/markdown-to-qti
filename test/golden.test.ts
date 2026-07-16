import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { convertMarkdownToQti, convertMarkdownToQtiWithAssets } from "../src/convert.js";

const fixturesDir = path.resolve("src/test/resources/fixtures");

// Fixtures with a ## Scoring section need manifest-supplied points; the map
// captures the points the QTI golden output was generated with.
const scoringPointsByFixture: Record<string, number[]> = {
  "choice-with-scoring": [2, 1],
  "cloze-with-scoring": [1],
  "descriptive-with-scoring": [2, 1]
};

const markdownFixtures = readdirSync(fixturesDir)
  .filter((name) => name.endsWith(".md"))
  .sort();

for (const markdownFixture of markdownFixtures) {
  test(`matches Kotlin golden fixture: ${markdownFixture}`, () => {
    const fixtureId = markdownFixture.slice(0, -".md".length);
    const markdownPath = path.join(fixturesDir, markdownFixture);
    const markdown = readFileSync(markdownPath, "utf8");
    const expected = readFileSync(path.join(fixturesDir, `${fixtureId}.qti.xml`), "utf8");

    const scoringPoints = scoringPointsByFixture[fixtureId];
    const actual =
      scoringPoints === undefined
        ? convertMarkdownToQti(markdown, fixtureId)
        : convertMarkdownToQtiWithAssets(markdown, fixtureId, markdownPath, { scoringPoints })
            .qtiXml;

    assert.equal(normalizeXml(actual), normalizeXml(expected));
  });
}

test("allows nested authoring metadata in frontmatter", () => {
  const sourcePath = path.resolve("src/test/resources/fixtures/nested-metadata.md");
  const result = convertMarkdownToQtiWithAssets(
    [
      "---",
      "question_type: choice",
      "time_budget_seconds: 60",
      "資料:",
      "  repo: metyatech/javascript-course-docs",
      "  path: content/docs/basics/pre-function-review/index.mdx",
      "---",
      "",
      "# Nested Metadata",
      "",
      "## Prompt",
      "",
      "Choose the correct answer.",
      "",
      "## Options",
      "",
      "- [x] Correct",
      "- [ ] Incorrect",
      "",
      "## Explanation",
      "",
      "The nested authoring metadata is ignored by QTI generation."
    ].join("\n"),
    "nested-metadata",
    sourcePath
  );

  assert.equal(result.timeBudgetSeconds, 60);
  assert.match(result.qtiXml, /title="Nested Metadata"/u);
  assert.match(result.qtiXml, /Correct/u);
});

test("allows frontmatter without time_budget_seconds", () => {
  const result = convertMarkdownToQtiWithAssets(
    [
      "---",
      "question_type: descriptive",
      "---",
      "# Untimed descriptive question",
      "",
      "## Prompt",
      "Describe the result.",
      ""
    ].join("\n"),
    "untimed-descriptive",
    path.resolve("untimed-descriptive.q.md")
  );

  assert.equal(result.timeBudgetSeconds, null);
  assert.match(result.qtiXml, /title="Untimed descriptive question"/u);
});

test("rejects non-positive or non-integer time_budget_seconds", () => {
  for (const value of ["0", "-1", "1.5"]) {
    assert.throws(
      () =>
        convertMarkdownToQti(
          [
            "---",
            "question_type: descriptive",
            `time_budget_seconds: ${value}`,
            "---",
            "# Invalid time",
            "",
            "## Prompt",
            "Describe the result.",
            ""
          ].join("\n"),
          `invalid-time-${value}`
        ),
      /time_budget_seconds must be a positive integer/u
    );
  }
});

test("rejects non-comment raw HTML blocks", () => {
  assert.throws(
    () =>
      convertMarkdownToQti(
        [
          "---",
          "question_type: descriptive",
          "time_budget_seconds: 60",
          "---",
          "# Raw HTML",
          "",
          "## Prompt",
          "<div>Raw HTML is still unsupported.</div>",
          ""
        ].join("\n"),
        "raw-html"
      ),
    /Raw HTML blocks are not supported in QTI output/u
  );
});

test("rejects non-comment raw inline HTML", () => {
  assert.throws(
    () =>
      convertMarkdownToQti(
        [
          "---",
          "question_type: descriptive",
          "time_budget_seconds: 60",
          "---",
          "# Raw Inline HTML",
          "",
          "## Prompt",
          "This has <span>raw HTML</span> inline.",
          ""
        ].join("\n"),
        "raw-inline-html"
      ),
    /Raw HTML is not supported in QTI output/u
  );
});

function normalizeXml(xml: string): string {
  return xml.replaceAll("\r\n", "\n").replaceAll(/^\s+</gmu, "<");
}
