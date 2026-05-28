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
- `time_budget_seconds`: positive integer. Used to resolve the manifest time
  limit when the manifest does not provide `time_limit_seconds`.

Removed frontmatter/metadata:

- `time_estimate_seconds` is not supported.
- `multi`, `order`, and `match` question types are not supported.

## Body Sections

Required:

- `# <title>`: exactly one top-level title.
- `## Prompt`: question prompt.

Optional:

- `## Scoring`: flat rubric list using `- <points>: <criterion>`.
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

A manifest defines the package title, item order, and optional total time limit.
Item paths are resolved relative to the manifest file and order is preserved.

```yaml
title: 2026 JavaScript II Final Exam
time_limit_seconds: 1200
items:
  - q1.q.md
  - q2.q.md
```

Required:

- `title`
- `items`

Optional:

- `time_limit_seconds`: positive integer. If omitted, the resolved time limit is
  the sum of all item `time_budget_seconds` values.

Removed:

- `type: quiz | exam` is not accepted.

## Markdown Support

`## Prompt`, `## Options`, `## Scoring`, and `## Explanation` content is parsed
as CommonMark with these extensions:

- Strikethrough
- GFM tables
- GFM task lists

Raw HTML blocks and inline HTML are rejected. Local images must use paths
relative to the Markdown file. During generation, local images are copied to the
output directory preserving relative paths.
