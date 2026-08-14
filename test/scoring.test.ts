import assert from "node:assert/strict";
import { DOMParser } from "@xmldom/xmldom";
import test from "node:test";

import { convertMarkdownToQtiWithAssets } from "../src/convert.js";
import { assertWellFormedXml } from "./xml-assertions.js";

const scoringCases = [
  {
    type: "choice",
    prompt: "Choose one.",
    sections: ["## Options", "- [x] Correct", "- [ ] Incorrect"]
  },
  { type: "descriptive", prompt: "Explain the answer.", sections: [] },
  { type: "cloze", prompt: "The answer is {{answer}}.", sections: [] }
] as const;

test("all question types serialize scored criteria as rich HTML paragraphs", () => {
  for (const { type, prompt, sections } of scoringCases) {
    const markdown = [
      "---",
      `question_type: ${type}`,
      "---",
      "# Scored question",
      "",
      "## Prompt",
      prompt,
      ...sections,
      "",
      "## Scoring",
      "- **Correct** `code`",
      ""
    ].join("\n");
    const result = convertMarkdownToQtiWithAssets(
      markdown,
      `${type}-scoring-regression`,
      `${type}-scoring-regression.md`,
      { scoringPoints: [2] }
    );

    assertWellFormedXml(result.qtiXml);
    assert.doesNotMatch(result.qtiXml, /<qti-p\b/u);
    const document = new DOMParser().parseFromString(result.qtiXml, "application/xml");
    const rubric = document.getElementsByTagName("qti-rubric-block")[0];

    assert.ok(rubric);
    assert.equal(rubric.getAttribute("view"), "scorer");
    const directCriteria: string[] = [];
    for (let index = 0; index < rubric.childNodes.length; index += 1) {
      const child = rubric.childNodes[index];
      if (child?.nodeType === 1) directCriteria.push(child.nodeName);
    }
    assert.deepEqual(directCriteria, ["p"]);
    assert.equal(rubric.getElementsByTagName("qti-p").length, 0);
    assert.equal(rubric.getElementsByTagName("p")[0]?.textContent, "[2] Correct code");
    assert.match(result.qtiXml, /<p>\[2\] <strong>Correct<\/strong> <code>code<\/code><\/p>/u);
  }
});
