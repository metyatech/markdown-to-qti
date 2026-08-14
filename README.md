# markdown-to-qti

## Overview

`markdown-to-qti` is an npm-installable TypeScript CLI that converts Markdown
question files into IMS QTI 3.0 packages. The authoring format is defined in:

- [docs/markdown-question-spec.md](docs/markdown-question-spec.md)
- [docs/qti-mapping.md](docs/qti-mapping.md)

## Supported environment

- Node.js 20.10 or newer
- npm

Java, Gradle, and a JDK are not required.

## Setup

Install dependencies:

```powershell
npm install
```

## Development commands

- Build: `npm run build`
- Test: `npm test`
- Lint: `npm run lint`
- Format: `npm run format`
- Format check: `npm run format:check`
- Verify: `npm run verify`

## CLI usage

Generate a QTI package from a manifest:

```powershell
npx markdown-to-qti --manifest path/to/manifest.yaml --output-dir qti-out
```

The manifest is strict YAML. Each item is a mapping with an `id`, a relative
`ref` to the question file, and optional `points` (one positive integer per
`## Scoring` criterion):

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

Single-file and multi-file conversion remains available for compatibility:

```powershell
npx markdown-to-qti --input path/to/question.md --test-title "Example Test"
```

Validate inputs without writing output:

```powershell
npx markdown-to-qti --manifest path/to/manifest.yaml --validate-only
```

Show command help:

```powershell
npx markdown-to-qti --help
```

Options:

- `--manifest <path>`: Manifest YAML file. This is the canonical package input.
- `--input <path>`: Markdown file or directory. Directories scan for `*.md`.
  Use `-` for stdin.
- `--test-title <title>`: Assessment test title for `--input` compatibility mode.
- `--output-dir <dir>`: Output directory for `.qti.xml` files. Defaults to
  `qti-out` under each input file directory.
- `--validate-only`: Parse and validate XML without writing files.
- `--dry-run`: Alias for `--validate-only`.
- `--verbose`: Log processed files.
- `--json`: Output a machine-readable JSON summary to stdout.
- `--version`, `-V`: Show version.
- `--help`, `-h`: Show help.

### Question bank layout

The canonical layout keeps question Markdown files together with the manifest
that orders them and assigns identifiers and points:

```text
question-bank/
  manifest.yaml
  questions/
    q1.md
    q2.md
    images/
      diagram.png
```

Running `npx markdown-to-qti --manifest question-bank/manifest.yaml
--output-dir qti-out` writes one `<id>.qti.xml` per item (named by the manifest
item `id`), copies referenced local images, and writes
`assessment-test.qti.xml` referencing the items in manifest order.

### CLI details

- `--manifest` writes item XML files and `assessment-test.qti.xml` using the
  manifest `title` and item order. Each item's output file and QTI `identifier`
  come from the manifest item `id`. Scoring points are supplied by the item
  `points` array.
- Manifest `time_limit_seconds` is authored as an integer number of seconds and
  is emitted as the QTI test-part time limit, for example
  `<qti-time-limits max-time="300"/>`. It takes precedence over item
  `time_budget_seconds` values. Without it, all item time budgets are summed
  when every item specifies one; when every item omits one, no
  `qti-time-limits` is emitted. Mixed present and omitted item time budgets are
  rejected.
- `--manifest` cannot be combined with `--input`.
- When `--input` is a directory, all `*.md` files inside it are processed.
- When `--input` is `-`, the CLI reads from stdin. The identifier defaults to
  `stdin`.
- Output files are written as `<input-file>.qti.xml` under `--output-dir`, or
  under `<input-dir>/qti-out` when omitted.
- An `assessment-test.qti.xml` file is written alongside outputs, referencing all
  generated items in that directory.
- `--validate-only` and `--dry-run` parse inputs without writing files.
- Local image files referenced in Markdown are copied to the output directory,
  preserving relative paths.
- Errors include the input path when possible and return a non-zero exit code.

## Markdown support

Prompt, options, and explanation content are parsed as CommonMark with GFM-style
tables, strikethrough, and task lists enabled. The supported constructs are
mapped to QTI elements as described in [QTI Mapping](docs/qti-mapping.md).

Question files use required YAML frontmatter:

```markdown
---
question_type: descriptive
---

# Question title

## Prompt

Question prompt.
```

Supported `question_type` values are `descriptive`, `choice`, and `cloze`.
`time_budget_seconds` is optional; when present, it must be a positive integer
number of seconds. The old `## Type` section is deprecated and not part of the
canonical format.

Additional frontmatter keys are allowed for authoring metadata, including nested
YAML mappings. The converter reads only `question_type` and
`time_budget_seconds` for QTI generation and ignores the rest.

Markdown and normal raw HTML may be mixed in prompt, option, scoring, and
explanation content. Raw HTML is parsed as HTML structure and preserved in QTI
presentation content; HTML comments remain source-only authoring notes and are
omitted. Ordinary display elements use their HTML element names, while `qti-*`
is reserved for QTI-specific structures and interactions. Markdown code fences
retain normal Markdown code semantics, while authored `<pre><code>` may be used
when nested rich HTML is intentionally required.

## Configuration and environment variables

No configuration or environment variables are required.

## Release and deployment

The package is published as `@metyatech/markdown-to-qti` with the
`markdown-to-qti` bin. Before publishing, run `npm run verify` and ensure the
package version matches the release tag.
