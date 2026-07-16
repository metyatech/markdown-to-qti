# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- `time_budget_seconds` is now optional in question frontmatter and remains a
  positive integer when present. Manifest `time_limit_seconds` takes
  precedence; otherwise all specified item budgets are summed, all-omitted
  budgets omit QTI time limits, and mixed presence is rejected.
- Manifests are parsed as strict YAML. Each item is a mapping with `id`, `ref`,
  and optional `points`; the item `id` sets the QTI item identifier and output
  file name. Bare string items and the removed `type` key are rejected.
- Scoring points are supplied by the manifest item `points` array instead of
  inline `- <points>: <criterion>` syntax. `## Scoring` lists criterion text
  only, and inline point values are rejected. A `## Scoring` section requires a
  matching `points` array of equal length.

### Added

- Initial project structure.
- Markdown to QTI 3.0 conversion for descriptive, choice, and cloze question types.
- Image support.
- CLI for processing files and directories.
- Assessment test generation.
- CI workflow with GitHub Actions.
- TypeScript npm package with the `markdown-to-qti` bin.
- Prettier formatting and ESLint static analysis.
- Security policy, contributing guidelines, and code of conduct.
