# Markdown Question Format (Draft)

This document defines the Markdown format for a single question file. One file
represents one question. Front matter is not used. The target output is IMS QTI
3.0.

## Goals

- Keep authoring simple and readable in plain Markdown.
- Use headings to separate sections.
- Support three question types: descriptive, choice, cloze.

## File Structure

A question file MUST follow this structure:

- `# <title>`: Required. The question title.
- `## Type`: Required. One of:
  - `descriptive`
  - `choice`
  - `cloze`
- `## Prompt`: Required. The question text.
- `## Scoring`: Optional. Scoring rubric criteria.

Headings are case-sensitive and must match exactly as written above. The `Type`
value must be a single word on the line immediately after the `## Type` heading.

## Markdown Rendering

Content inside `## Prompt`, `## Options`, and `## Explanation` is parsed as CommonMark.
In addition, the following CommonMark-compatible extensions are supported:

- Strikethrough (`~~text~~`)
- GFM tables
- GFM task list items (`- [ ]` / `- [x]`)

Supported constructs are converted to QTI elements (see `qti-mapping.md`). Raw
HTML blocks or inline HTML are not supported and will cause a conversion error.

Section headings use `##`, so headings within content MUST use `###` or deeper.

## Type: descriptive

The prompt is free-form text.

Required sections:

- `## Prompt`

Optional sections:

- `## Explanation`: A learner-facing explanation shown after answering.
- `## Scoring`: A list of scoring criteria and point values.

## Type: choice

Single-correct multiple choice. Exactly one option MUST be marked as correct.

Required sections:

- `## Prompt`
- `## Options`

Options are written as a Markdown task list:

- Correct option: `- [x] Option text`
- Incorrect option: `- [ ] Option text`

Constraints:

- Exactly one `- [x]` MUST appear.
- Options MUST be a single flat list (no nesting).

Optional sections:

- `## Explanation`: A learner-facing explanation shown after answering.
- `## Scoring`: A list of scoring criteria and point values.

## Type: cloze

Fill-in-the-blank. Blanks are defined inline within the prompt using a marker.

Required sections:

- `## Prompt`

Blank marker syntax:

- `{{answer}}` creates a blank whose correct answer is `answer`.

Constraints:

- Each `{{...}}` marker defines a blank in order of appearance.
- To include a literal `{{` or `}}`, escape with a backslash (`\{{` or `\}}`).
- `{{...}}` markers inside inline code or code blocks are also treated as blanks.

Optional sections:

- `## Explanation`: A learner-facing explanation shown after answering.
- `## Scoring`: A list of scoring criteria and point values.

## Scoring

The `## Scoring` section defines a rubric as a flat Markdown list. Each list
item assigns points to a criterion.

Syntax per list item:

- `<points>: <criterion>`

Examples:

- See [../src/test/resources/fixtures/descriptive-with-scoring.md](../src/test/resources/fixtures/descriptive-with-scoring.md).

Constraints:

- `<points>` MUST be a number (integer or decimal).
- The list MUST be a single flat list (no nesting).
- If present, `## Scoring` MUST appear after `## Prompt` and after `## Options`
  (when the question type is `choice`).

## Images

Images can be embedded in the following sections:

- `## Prompt`
- `## Options`
- `## Explanation`

Use standard Markdown image syntax:

- `![alt text](path "optional title")`

Local image handling:

- Image paths without a URL scheme are treated as local files.
- Local paths MUST be relative to the Markdown file location.
- When generating output (not `--validate-only`), local image files are copied to the output
  directory, preserving the relative path. The output directory is either `--output-dir` or
  `<input-directory>/qti-out` when omitted.

## Common Rules

- One question per file.
- No front matter.
- UTF-8 encoded text.
- Use a single top-level `#` heading.
- Do not repeat section headings.

## Examples

Examples are provided as test fixtures:

- Descriptive with scoring:
  - Markdown: [../src/test/resources/fixtures/descriptive-with-scoring.md](../src/test/resources/fixtures/descriptive-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/descriptive-with-scoring.qti.xml](../src/test/resources/fixtures/descriptive-with-scoring.qti.xml)
- Descriptive with explanation:
  - Markdown: [../src/test/resources/fixtures/descriptive-with-explanation.md](../src/test/resources/fixtures/descriptive-with-explanation.md)
  - QTI: [../src/test/resources/fixtures/descriptive-with-explanation.qti.xml](../src/test/resources/fixtures/descriptive-with-explanation.qti.xml)
- Descriptive with image:
  - Markdown: [../src/test/resources/fixtures/descriptive-with-image.md](../src/test/resources/fixtures/descriptive-with-image.md)
  - QTI: [../src/test/resources/fixtures/descriptive-with-image.qti.xml](../src/test/resources/fixtures/descriptive-with-image.qti.xml)
- Choice with scoring:
  - Markdown: [../src/test/resources/fixtures/choice-with-scoring.md](../src/test/resources/fixtures/choice-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/choice-with-scoring.qti.xml](../src/test/resources/fixtures/choice-with-scoring.qti.xml)
- Cloze with scoring:
  - Markdown: [../src/test/resources/fixtures/cloze-with-scoring.md](../src/test/resources/fixtures/cloze-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/cloze-with-scoring.qti.xml](../src/test/resources/fixtures/cloze-with-scoring.qti.xml)
- Cloze with code blanks:
  - Markdown: [../src/test/resources/fixtures/cloze-with-code.md](../src/test/resources/fixtures/cloze-with-code.md)
  - QTI: [../src/test/resources/fixtures/cloze-with-code.qti.xml](../src/test/resources/fixtures/cloze-with-code.qti.xml)
