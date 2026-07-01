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
