# markdown-to-qti

## Overview

This repository will implement a tool that converts Markdown question files into
IMS QTI 3.0. The authoring format is defined in:

- [docs/markdown-question-spec.md](docs/markdown-question-spec.md)
- [docs/qti-mapping.md](docs/qti-mapping.md)

## Setup

- Install JDK 23 for Gradle/Kotlin builds.
- Install Gradle (CLI).

## Toolchain

If you need to pin Gradle to a specific JDK, copy `gradle.properties.example` to
`gradle.properties` and update `org.gradle.java.home` for your environment.

## Development Commands

- Run tests: `gradle test`

## CLI Usage

Generate QTI XML from one or more Markdown files:

- `gradle run --args="--input path/to/question.md --test-title \"Example Test\""`

Validate inputs without writing output:

- `gradle run --args="--input path/to/question.md --test-title \"Example Test\" --validate-only"`

Non-ASCII test titles:

- Use `--test-title` or `--test-title-file` to avoid shell quoting issues.

Options:

- `--input <path>`: Markdown file or directory (directories scan for `*.md`).
- `--test-title <title>`: Assessment test title (required).
- `--test-title-file <path>`: UTF-8 text file containing the assessment test title (required when `--test-title` is not set).
- `--output-dir <dir>`: Output directory for `.qti.xml` files. Defaults to `qti-out` under each input file directory.
- `--validate-only`: Parse and validate XML without writing files.
- `--verbose`: Log processed files.

### CLI Details

- When `--input` is a directory, all `*.md` files inside it are processed.
- Output files are written as `<input-file>.qti.xml` under `--output-dir` or `<input-dir>/qti-out` when omitted.
- An `assessment-test.qti.xml` file is written alongside outputs, referencing all generated items in that directory.
- `--validate-only` performs XML well-formedness checks without writing files.
- Local image files referenced in Markdown are copied to the output directory, preserving
  the relative paths.
- Errors include the input path when possible and return a non-zero exit code.

## Markdown Support

Prompt, options, and explanation content are parsed as CommonMark with GFM-style
tables, strikethrough, and task lists enabled. The supported constructs are
mapped to QTI elements as described in `docs/qti-mapping.md`.

Raw HTML blocks/inline HTML are not supported and will raise an error.

## Configuration / Environment Variables

None yet.

## Release / Deployment

Not applicable yet.
