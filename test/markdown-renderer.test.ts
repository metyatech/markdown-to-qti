import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";
import assert from "node:assert/strict";
import { convertMarkdownToQti, convertMarkdownToQtiWithAssets } from "../src/convert.js";

test("preserves inline raw HTML attributes in the presentation", () => {
  const xml = convertMarkdownToQti(
    question(
      "descriptive",
      'before <span style="display:inline-block;min-width:4em;border:1px solid #000;text-align:center;background:transparent;" class="answer" id="answer-a" title="Answer" data-slot="a" aria-label="answer">A</span> after'
    ),
    "inline-raw"
  );

  assert.match(
    xml,
    /<p>before <span style="display:inline-block;min-width:4em;border:1px solid #000;text-align:center;background:transparent;" class="answer" id="answer-a" title="Answer" data-slot="a" aria-label="answer">A<\/span> after<\/p>/u
  );
});

test("preserves block raw HTML and mixed Markdown structure", () => {
  const xml = convertMarkdownToQti(
    question(
      "descriptive",
      [
        "Markdown **strong** before.",
        "",
        '<div class="panel" id="panel-1"><p>Raw <em>block</em>.</p><ul><li>One</li></ul></div>',
        "",
        "Markdown after."
      ].join("\n")
    ),
    "block-raw"
  );

  assert.match(xml, /<p>Markdown <strong>strong<\/strong> before\.<\/p>/u);
  assert.match(
    xml,
    /<div class="panel" id="panel-1"><p>Raw <em>block<\/em>\.<\/p><ul><li>One<\/li><\/ul><\/div>/u
  );
  assert.match(xml, /<p>Markdown after\.<\/p>/u);
});

test("renders Markdown presentation with bare HTML tags", () => {
  const xml = convertMarkdownToQti(
    question(
      "descriptive",
      [
        "### This heading is content",
        "",
        "**bold** *emphasis* ~~deleted~~ [link](https://example.com)",
        "",
        "- one",
        "- two",
        "",
        "```text",
        "<span>escaped</span>",
        "```",
        "",
        "---"
      ].join("\n")
    ),
    "bare-html"
  );

  assert.match(xml, /<h3>This heading is content<\/h3>/u);
  assert.match(
    xml,
    /<p><strong>bold<\/strong> <em>emphasis<\/em> <del>deleted<\/del> <a href="https:\/\/example\.com">link<\/a><\/p>/u
  );
  assert.match(xml, /<ul>[\s\S]*<li>one<\/li>[\s\S]*<\/ul>/u);
  assert.match(
    xml,
    /<pre><code class="language-text">&lt;span>escaped&lt;\/span>\n<\/code><\/pre>/u
  );
  assert.match(xml, /<hr \/>/u);
  assert.doesNotMatch(
    xml,
    /<qti-(?:p|pre|code|img|table|em|strong|del|a|ul|ol|li|hr|blockquote|h[1-6])\b/u
  );
});

test("preserves authored rich pre/code HTML and exact text newlines", () => {
  const xml = convertMarkdownToQti(
    question(
      "descriptive",
      [
        "<pre><code>&lt;html&gt;",
        'foo <span style="display:inline-block;min-width:4em;border:1px solid #000;text-align:center;background:transparent;">A</span> bar',
        "&lt;/html&gt;</code></pre>"
      ].join("\n")
    ),
    "rich-code"
  );

  assert.match(
    xml,
    /<pre><code>&lt;html>\nfoo <span style="display:inline-block;min-width:4em;border:1px solid #000;text-align:center;background:transparent;">A<\/span> bar\n&lt;\/html><\/code><\/pre>/u
  );
});

test("keeps cloze interactions natural inside paragraphs and code", () => {
  const xml = convertMarkdownToQti(
    question(
      "cloze",
      ["Before {{answer}}.", "", "<pre><code>before {{code-answer}} after</code></pre>"].join("\n")
    ),
    "cloze-html"
  );

  assert.match(
    xml,
    /<p>Before <qti-text-entry-interaction response-identifier="RESPONSE_1"><\/qti-text-entry-interaction>\.<\/p>/u
  );
  assert.match(
    xml,
    /<pre><code>before <qti-text-entry-interaction response-identifier="RESPONSE_2"><\/qti-text-entry-interaction> after<\/code><\/pre>/u
  );
  assert.match(xml, /<qti-value>answer<\/qti-value>/u);
  assert.match(xml, /<qti-value>code-answer<\/qti-value>/u);
});

test("preserves rich HTML choice option structure without a checkbox", () => {
  const xml = convertMarkdownToQti(
    [
      "---",
      "question_type: choice",
      "---",
      "# Rich option",
      "",
      "## Prompt",
      "Choose one.",
      "",
      "## Options",
      "",
      '- [x] <div class="choice-card"><p>Correct <span data-kind="answer">answer</span></p><p>Details</p></div>',
      "- [ ] Incorrect"
    ].join("\n"),
    "rich-choice"
  );

  assert.match(
    xml,
    /<qti-simple-choice identifier="CHOICE_1">[\s\S]*<div class="choice-card"><p>Correct <span data-kind="answer">answer<\/span><\/p><p>Details<\/p><\/div>[\s\S]*<\/qti-simple-choice>/u
  );
  assert.doesNotMatch(xml, /<input\b/u);
  assert.doesNotMatch(xml, /\[(?:x| )\]/u);
});

test("collects local images from raw HTML and Markdown HAST images", () => {
  const tempDir = mkdtempSync(path.join(tmpdir(), "markdown-to-qti-images-"));
  try {
    const imagesDir = path.join(tempDir, "images");
    mkdirSync(imagesDir);
    writeFileSync(path.join(imagesDir, "raw.png"), "raw");
    writeFileSync(path.join(imagesDir, "markdown.png"), "markdown");
    const sourcePath = path.join(tempDir, "raw-images.md");
    const result = convertMarkdownToQtiWithAssets(
      question(
        "descriptive",
        [
          '<img src="images/raw.png" alt="Raw image">',
          "",
          "![Markdown image](images/markdown.png)"
        ].join("\n")
      ),
      "raw-images",
      sourcePath
    );

    assert.deepEqual(result.localImages.map((image) => image.outputRelativePath).sort(), [
      path.join("images", "markdown.png"),
      path.join("images", "raw.png")
    ]);
    assert.match(result.qtiXml, /<img src="images\/raw\.png" alt="Raw image" \/>/u);
    assert.match(result.qtiXml, /<img src="images\/markdown\.png" alt="Markdown image" \/>/u);
  } finally {
    rmSync(tempDir, { force: true, recursive: true });
  }
});

test("omits HTML comments from generated QTI", () => {
  const xml = convertMarkdownToQti(
    question("descriptive", "Visible <!-- source-only note --> content."),
    "comments"
  );

  assert.match(xml, /<p>Visible  content\.<\/p>/u);
  assert.doesNotMatch(xml, /source-only note/u);
  assert.doesNotMatch(xml, /<!--/u);
});

function question(type: "descriptive" | "choice" | "cloze", prompt: string): string {
  return [
    "---",
    `question_type: ${type}`,
    "---",
    "# Test question",
    "",
    "## Prompt",
    prompt,
    ""
  ].join("\n");
}
