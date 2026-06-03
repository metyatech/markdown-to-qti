import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import test from "node:test";

import { convertMarkdownToQti } from "../src/convert.js";

const fixturesDir = path.resolve("src/test/resources/fixtures");

const markdownFixtures = readdirSync(fixturesDir)
  .filter((name) => name.endsWith(".md"))
  .sort();

for (const markdownFixture of markdownFixtures) {
  test(`matches Kotlin golden fixture: ${markdownFixture}`, () => {
    const fixtureId = markdownFixture.slice(0, -".md".length);
    const markdown = readFileSync(path.join(fixturesDir, markdownFixture), "utf8");
    const expected = readFileSync(path.join(fixturesDir, `${fixtureId}.qti.xml`), "utf8");

    assert.equal(normalizeXml(convertMarkdownToQti(markdown, fixtureId)), normalizeXml(expected));
  });
}

function normalizeXml(xml: string): string {
  return xml.replaceAll("\r\n", "\n").replaceAll(/^\s+</gmu, "<");
}
