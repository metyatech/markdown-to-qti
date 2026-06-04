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

### CLI details

- `--manifest` writes item XML files and `assessment-test.qti.xml` using the
  manifest `title` and item order.
- Manifest `time_limit_seconds` is authored as an integer number of seconds and
  is emitted as the QTI test-part time limit, for example
  `<qti-time-limits max-time="300"/>`. If omitted, the limit is the sum of item
  `time_budget_seconds` values.
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
time_budget_seconds: 60
---

# Question title

## Prompt

Question prompt.
```

Supported `question_type` values are `descriptive`, `choice`, and `cloze`. The
old `## Type` section is deprecated and not part of the canonical format.

HTML comments are allowed as source-only authoring notes and omitted from
generated QTI. Other raw HTML blocks and inline HTML are not supported and raise
an error.

## Configuration and environment variables

No configuration or environment variables are required.

## Release and deployment

The package is published as `@metyatech/markdown-to-qti` with the
`markdown-to-qti` bin. Before publishing, run `npm run verify` and ensure the
package version matches the release tag.
