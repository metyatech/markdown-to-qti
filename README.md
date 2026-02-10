# markdown-to-qti

## Overview

This repository will implement a tool that converts Markdown question files into
IMS QTI 3.0. The authoring format is defined in:

- [docs/markdown-question-spec.md](docs/markdown-question-spec.md)
- [docs/qti-mapping.md](docs/qti-mapping.md)

## Setup

- Install Java 23. Use `tools/gradle-java23.ps1` (it auto-downloads JDK 23 if missing), or set `JAVA_HOME_23` to an existing JDK 23 installation.
- This project uses the Gradle wrapper. You can use `./gradlew` (or `gradlew.bat` on Windows) to run Gradle tasks.

## Toolchain

If you need to pin Gradle to a specific JDK, set `JAVA_HOME_23` to the JDK 23 installation directory.

## Development Commands

- Build: `.\\tools\\gradle-java23.ps1 build`
- Test: `.\\tools\\gradle-java23.ps1 test`
- Lint: `.\\tools\\gradle-java23.ps1 detekt`
- Format: `.\\tools\\gradle-java23.ps1 spotlessApply`
- Format check: `.\\tools\\gradle-java23.ps1 spotlessCheck`

## CLI Usage

Generate QTI XML from one or more Markdown files:

- `gradle run --args="--input path/to/question.md --test-title \"Example Test\""`

Recommended (avoids Gradle argument parsing issues, especially with non-ASCII titles):

- `.\tools\gradle-java23.ps1 installDist`
- `build/install/markdown-to-qti/bin/markdown-to-qti --input path/to/question.md --test-title "Example Test"`

Validate inputs without writing output:

- `.\tools\gradle-java23.ps1 run --args="--input path/to/question.md --test-title \"Example Test\" --validate-only"`

Non-ASCII test titles:

- Prefer the installed CLI (`.\tools\gradle-java23.ps1 installDist` then `build/install/markdown-to-qti/bin/markdown-to-qti`) to avoid Gradle task parsing issues.

Options:

- `--input <path>`: Markdown file or directory (directories scan for `*.md`). Use `-` for stdin.
- `--test-title <title>`: Assessment test title (required).
- `--output-dir <dir>`: Output directory for `.qti.xml` files. Defaults to `qti-out` under each input file directory.
- `--validate-only`: Parse and validate XML without writing files.
- `--dry-run`: Alias for `--validate-only`.
- `--verbose`: Log processed files.
- `--json`: Output machine-readable JSON summary to stdout.
- `--version`, `-V`: Show version.

### CLI Details

- When `--input` is a directory, all `*.md` files inside it are processed.
- When `--input` is `-`, it reads from stdin. Identifier defaults to `stdin`.
- Output files are written as `<input-file>.qti.xml` under `--output-dir` or `<input-dir>/qti-out` when omitted.
- An `assessment-test.qti.xml` file is written alongside outputs, referencing all generated items in that directory.
- `--validate-only` (or `--dry-run`) performs XML well-formedness checks without writing files.
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

