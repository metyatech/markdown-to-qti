# Markdown Question Format

This repository is the canonical Markdown parser/compiler for authored question
content. Human-edited source consists of Markdown question files plus a manifest.
The shared intermediate representation is the generated IMS QTI package.

## Question File

One question is authored in one UTF-8 Markdown file. YAML frontmatter is
required.

```markdown
---
question_type: choice
time_budget_seconds: 60
---

# Prime Number

## Prompt

Which number is prime?

## Options

- [ ] 9
- [x] 11
- [ ] 21
```

Required frontmatter:

- `question_type`: one of `descriptive`, `choice`, or `cloze`.

Optional frontmatter:

- `time_budget_seconds`: when present, a positive integer number of seconds.
  It is used to resolve the manifest time limit only when the manifest does not
  provide `time_limit_seconds`.

Additional authoring metadata:

- Any other top-level frontmatter keys are allowed for authoring metadata
  (for example `資料`, `metadata`, `tags`) and are ignored by QTI generation.
- Additional keys MAY be nested YAML mappings. The converter only reads
  `question_type` and `time_budget_seconds` for QTI generation; unknown keys
  are silently ignored regardless of their YAML shape (scalar, mapping, list).

  ```yaml
  資料:
    repo: metyatech/javascript-course-docs
    path: content/docs/basics/pre-function-review/index.mdx
  ```

Removed frontmatter/metadata:

- `time_estimate_seconds` is not supported.
- `multi`, `order`, and `match` question types are not supported.

## Body Sections

Required:

- `# <title>`: exactly one top-level title.
- `## Prompt`: question prompt.

Optional:

- `## Scoring`: flat rubric list using `- <criterion>`. Criterion text only;
  point values are not written in the question file. When `## Scoring` is
  present, the manifest item MUST supply a matching `points` array (one positive
  integer per criterion). Writing a point value inline (for example
  `- 2: <criterion>`) is rejected.
- `## Explanation`: learner-facing explanation.

Choice-only:

- `## Options`: required for `question_type: choice`; not allowed otherwise.
- Options use a flat Markdown task list.
- Exactly one option must be checked with `- [x]`.

Deprecated:

- `## Type` is not part of the canonical format. The CLI may keep temporary
  compatibility for old files, but new files must use frontmatter.

Section headings are case-sensitive. Content inside sections may use Markdown
headings at `###` or deeper; `#` and `##` are reserved for the file title and
section delimiters.

## Cloze Blanks

Cloze blanks are written directly in `## Prompt`:

- `{{answer}}`: exact-answer blank.
- `{{/regex/}}`: regex blank. The QTI response declaration is marked with
  `interpretation="regex"` so downstream adapters can distinguish it from exact
  blanks.

`${...}` is not supported in the canonical format. To include literal braces,
escape them as `\{{` or `\}}`.

## Manifest

A manifest defines the package title, item order, per-item identifiers, and
optional total time limit. It is parsed as strict YAML. Item `ref` paths are
resolved relative to the manifest file and item order is preserved.

```yaml
title: 2026 JavaScript II Final Exam
time_limit_seconds: 1200
items:
  - id: q1
    ref: questions/q1.md
    points: [2, 1]
  - id: q2
    ref: questions/q2.md
```

Required:

- `title`: non-empty string.
- `items`: non-empty list of item mappings.

Each `items` entry is a mapping (a bare string item is rejected) with exactly
these keys:

- `id` (required): item identifier matching
  `/^[A-Za-z0-9][A-Za-z0-9_.:-]*$/`. It becomes the generated QTI item
  `identifier` and the `<id>.qti.xml` output file name. Ids must be unique
  within the manifest.
- `ref` (required): relative path to the question Markdown file. Absolute paths,
  Windows drive paths, and URLs are rejected, and the target file must exist.
- `points` (optional): list of positive integers, one per `## Scoring`
  criterion in the referenced question. It is required when the question has a
  `## Scoring` section and must be omitted when it does not; the count must match
  the number of criteria.

Unknown manifest keys, unknown item keys, and the removed `type` key are
rejected. Error messages include the manifest path and line number.

Optional:

- `time_limit_seconds`: positive integer seconds. If omitted, the resolved time
  limit is the sum of all item `time_budget_seconds` values when every item
  specifies one. When every item omits `time_budget_seconds`, the generated
  QTI assessment test omits `qti-time-limits`. A mix of present and omitted
  item time budgets without `time_limit_seconds` is rejected. An explicit
  `time_limit_seconds` takes precedence over all item time budgets. A generated
  time limit is emitted as the integer number of seconds in `qti-time-limits`
  `max-time`, for example `300` seconds becomes `max-time="300"`.

The generated assessment items keep `time-dependent="false"`. That item
attribute describes whether scoring is time dependent; it is not an individual
question time limit. Package timing is represented only by the assessment test
`qti-time-limits` element.

Removed:

- `type: quiz | exam` is not accepted.

## Markdown Support

`## Prompt`, `## Options`, `## Scoring`, and `## Explanation` content is parsed
as CommonMark with these extensions:

- Strikethrough
- GFM tables
- GFM task lists

HTML comments are allowed as source-only authoring notes and are omitted from
generated QTI. Other raw HTML blocks and inline HTML are rejected. Local images
must use paths relative to the Markdown file. During generation, local images
are copied to the output directory preserving relative paths.
