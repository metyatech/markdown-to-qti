# Markdown to QTI Mapping

This document describes how the Markdown authoring format maps to IMS QTI 3.0
output in this repository. The authoritative authoring rules are in
[markdown-question-spec.md](markdown-question-spec.md).

## Common Output

Each question is emitted as a `qti-assessment-item` with:

- `identifier`: derived from the input file name (without extension).
- `title`: the `# <title>` heading.
- `adaptive="false"`, `time-dependent="false"`.

The `qti-item-body` contains the prompt rendered as QTI flow content. Paragraphs
map to `qti-p`, and other block elements map to their QTI equivalents.

## Assessment Test Output

When generating files, the CLI also writes an `assessment-test.qti.xml` file to
the output directory. It contains a single test part and section with item
references for all generated `qti-assessment-item` files in that directory.

### Markdown to QTI Elements

CommonMark constructs are mapped to QTI elements as follows:

- Paragraphs → `qti-p`
- Headings (`###`+) → `qti-h3` ... `qti-h6`
- Emphasis → `qti-em`
- Strong → `qti-strong`
- Strikethrough → `qti-del`
- Links → `qti-a` (uses `href` and optional `title`)
- Inline code → `qti-code`
- Code blocks → `qti-pre` + `qti-code`
- Blockquotes → `qti-blockquote`
- Bullet lists → `qti-ul` / `qti-li`
- Ordered lists → `qti-ol` / `qti-li` (uses `start` when needed)
- Task lists → `qti-ul` / `qti-li` with `[ ]` or `[x]` prefix text
- Tables → `qti-table` / `qti-thead` / `qti-tbody` / `qti-tr` / `qti-th` / `qti-td`
- Horizontal rules → `qti-hr`

Raw HTML blocks/inline HTML are rejected.

### Inline Images

Markdown image syntax embedded in `## Prompt`, `## Options`, or `## Explanation` is
converted into `qti-img` elements inside the surrounding QTI text container.

- `src` is the original image path.
- `alt` uses the Markdown alt text (empty alt is allowed).
- `title` is emitted when a Markdown image title is present.

### Scoring rubric output

When `## Scoring` is present, the output uses `qti-rubric-block view="scorer"`
with one `qti-p` per criterion. Each rubric line is formatted as:

```
[<points>] <criterion>
```

`<points>` is the numeric value from the Markdown list item (`<points>: <criterion>`),
and `<criterion>` is the criterion text.

Example output:

```xml
<qti-rubric-block view="scorer">
  <qti-p>[2] Identifies chlorophyll as a light-absorbing pigment</qti-p>
  <qti-p>[1] Mentions conversion of light energy to chemical energy</qti-p>
</qti-rubric-block>
```

## Type: descriptive

Markdown:

```markdown
## Prompt
<text>
```

QTI:

- `qti-response-declaration` with `base-type="string"`, `cardinality="single"`.
- `qti-extended-text-interaction` with `response-identifier="RESPONSE"`.

Optional:

- `## Explanation` → `qti-rubric-block view="candidate"` containing a `qti-p`.
- `## Scoring` → `qti-rubric-block view="scorer"` with one `qti-p` per criterion.

## Type: choice

Markdown:

```markdown
## Options
- [ ] Option A
- [x] Option B
```

QTI:

- `qti-response-declaration` with `base-type="identifier"`, `cardinality="single"`.
- `qti-correct-response` contains `CHOICE_<n>` for the checked option.
- `qti-choice-interaction max-choices="1"` with `qti-simple-choice` entries.

Optional:

- `## Explanation` → `qti-rubric-block view="candidate"`.
- `## Scoring` → `qti-rubric-block view="scorer"`.

## Type: cloze

Markdown:

```markdown
## Prompt
Text with a {{blank}}.
```

QTI:

- `qti-response-declaration` with `base-type="string"`, `cardinality="single"`.
- `qti-text-entry-interaction` inlined where each `{{...}}` appears.
- The correct answer uses the text inside `{{...}}`.
- `{{...}}` markers inside inline code or code blocks are treated as blanks and
  rendered as `qti-text-entry-interaction` alongside `qti-code`.

Optional:

- `## Explanation` → `qti-rubric-block view="candidate"`.
- `## Scoring` → `qti-rubric-block view="scorer"`.

## Examples

See the fixtures used by the golden tests:

- Descriptive with explanation:
  - Markdown: [../src/test/resources/fixtures/descriptive-with-explanation.md](../src/test/resources/fixtures/descriptive-with-explanation.md)
  - QTI: [../src/test/resources/fixtures/descriptive-with-explanation.qti.xml](../src/test/resources/fixtures/descriptive-with-explanation.qti.xml)
- Descriptive with scoring:
  - Markdown: [../src/test/resources/fixtures/descriptive-with-scoring.md](../src/test/resources/fixtures/descriptive-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/descriptive-with-scoring.qti.xml](../src/test/resources/fixtures/descriptive-with-scoring.qti.xml)
- Choice with scoring:
  - Markdown: [../src/test/resources/fixtures/choice-with-scoring.md](../src/test/resources/fixtures/choice-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/choice-with-scoring.qti.xml](../src/test/resources/fixtures/choice-with-scoring.qti.xml)
- Cloze with scoring:
  - Markdown: [../src/test/resources/fixtures/cloze-with-scoring.md](../src/test/resources/fixtures/cloze-with-scoring.md)
  - QTI: [../src/test/resources/fixtures/cloze-with-scoring.qti.xml](../src/test/resources/fixtures/cloze-with-scoring.qti.xml)
