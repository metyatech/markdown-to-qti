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

- `gradle run --args="--input path/to/question.md --output-dir path/to/out"`

Validate inputs without writing output:

- `gradle run --args="--input path/to/question.md --validate-only"`

Options:

- `--input <path>`: Markdown file or directory (directories scan for `*.md`).
- `--output-dir <dir>`: Output directory for `.qti.xml` files. Required unless `--validate-only`.
- `--validate-only`: Parse and validate XML without writing files.
- `--verbose`: Log processed files.

### CLI Details

- When `--input` is a directory, all `*.md` files inside it are processed.
- Output files are written as `<input-file>.qti.xml` under `--output-dir`.
- `--validate-only` performs XML well-formedness checks without writing files.
- Errors include the input path when possible and return a non-zero exit code.

## Configuration / Environment Variables

None yet.

## Release / Deployment

Not applicable yet.
