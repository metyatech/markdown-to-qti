# Markdown to QTI Mapping

This document describes how the Markdown authoring format maps to IMS QTI 3.0
output in this repository. The authoritative authoring rules are in
[markdown-question-spec.md](markdown-question-spec.md).

## Common Output

Each question is emitted as a `qti-assessment-item` with:

- `identifier`: derived from the input file name (without extension).
- `title`: the `# <title>` heading.
- `adaptive="false"`, `time-dependent="false"`.

The `qti-item-body` always contains the prompt as `qti-p`.

### Inline Images

Markdown image syntax embedded in `## Prompt`, `## Options`, or `## Explanation` is
converted into `qti-img` elements inside the surrounding QTI text container.

- `src` is the original image path.
- `alt` uses the Markdown alt text (empty alt is allowed).
- `title` is emitted when a Markdown image title is present.

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
