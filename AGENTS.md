<!-- markdownlint-disable MD025 -->
# Tool Rules (compose-agentsmd)

- **Session gate**: before starting substantive work for each externally supplied human/operator instruction, run `compose-agentsmd` once from the project root. AGENTS.md contains the rules you operate under; stale rules cause rule violations. Do not rerun this gate within the same instruction after tool results, retries, generated continuations, or resumed execution. If you discover you skipped this step mid-session, stop, run it immediately, re-read the diff, and adjust your behavior before continuing.
- `compose-agentsmd` intentionally regenerates `AGENTS.md`; any resulting `AGENTS.md` diff is expected and must not be treated as an unexpected external change.
- If `compose-agentsmd` is not available, run it via `npx compose-agentsmd`. If `npx` is unavailable or cannot fetch the package, install it via npm with an environment-appropriate method such as `npm install -g compose-agentsmd` when global installs are permitted, or a user-local npm prefix when global installs are not permitted.
- To update shared/global rules, use `compose-agentsmd edit-rules` to locate the writable rules workspace, make changes only in that workspace, then run `compose-agentsmd apply-rules` (do not manually clone or edit the rules source repo outside this workflow).
- If you find an existing clone of the rules source repo elsewhere, do not assume it is the correct rules workspace; always treat `compose-agentsmd edit-rules` output as the source of truth.
- `compose-agentsmd apply-rules` pushes the rules workspace when `source` is GitHub (if the workspace is clean), then regenerates `AGENTS.md` with refreshed rules.
- Do not edit `AGENTS.md` directly; update the source rules and regenerate.
- `tools/tool-rules.md` is the shared rule source for all repositories that use compose-agentsmd.
- Before applying any rule updates, present the planned changes first with an ANSI-colored diff-style preview, ask for explicit approval, then make the edits.
- These tool rules live in tools/tool-rules.md in the compose-agentsmd repository; do not duplicate them in other rule modules.

Source: github:metyatech/agent-rules@HEAD/rules/domains/exam/exam-markdown-format.md

# Exam Markdown

## Common question format

- When creating or editing Markdown questions, quizzes, exams, or preparation
  question sets, use the common Markdown question format below. Small quizzes,
  exams, and preparation question collections share this format.
- markdown-to-qti is the only supported Markdown parser/compiler for question
  Markdown.
- The authoritative human/AI-edited sources are Markdown question files plus a
  manifest. The shared intermediate representation is the generated QTI package.
- Track publish workflows should treat the QTI package as the source artifact
  when publishing support is available.

```md
---
question_type: choice
time_budget_seconds: 90
---

# Problem title

## Prompt

...

## Options

- [x] Correct answer
- [ ] Distractor

## Scoring

- 2: ...

## Explanation

...
```

## Question frontmatter and sections

- `question_type` MUST be one of `descriptive`, `choice`, or `cloze`.
- `time_budget_seconds` MUST be present and MUST be a positive integer.
- `time_estimate_seconds` MUST NOT be used.
- `## Type` MUST NOT be used. Put the type in `question_type` frontmatter
  instead.
- Cloze answers MUST use `{{answer}}` for exact answers or `{{/regex/}}` for
  regular-expression answers.
- `${...}` MUST NOT be used for cloze answers or any other answer placeholder.
- Keep educational quality rules in the education domain separate from this
  operational Markdown/QTI format rule.

## Preparation and regular exam pairing

- When creating a preparation question set for a regular exam, pair each
  preparation question with the corresponding regular exam question one-to-one.
  - Example: `1preparation/q1.q.md` corresponds to `2regular` question 1.
  - Example: `1preparation/q2.q.md` corresponds to `2regular` question 2.
  - When the user explicitly requests a different structure, follow the user's
    requested structure.
- When using a note such as `本試験では`, write it for the paired regular exam
  question and clearly describe what changes in that paired regular exam
  question.
- Write preparation questions as surface-varied versions of the paired regular
  exam questions when students may view preparation materials during the exam.
  Change surface details such as values, strings, prompt text, conditions,
  ranges, examples, or output text while keeping the same assessed skill.
- Use exactly the same `## Scoring` text for each paired preparation and regular
  exam question.
- When the same scoring text needs to apply to both a preparation question and a
  regular exam question, write the scoring text abstractly enough to apply to
  both.
  - Use wording such as `指定された値`, `指定された文字列`,
    `要求通りの出力`, `下限値以上`, and `上限値以下`.
- Put the question intent at the beginning of `## Explanation`.
  - Example: `出題意図: ...`

## Manifest format

- Manifests MUST contain `title` and `items`.
- Manifests MAY contain `time_limit_seconds`.
- Manifest `type: quiz` and `type: exam` MUST NOT be used. Distinguish quiz,
  exam, and preparation-set behavior outside the common manifest schema.
- When `time_limit_seconds` is present, it is the time limit for the entire
  question set.
- When `time_limit_seconds` is absent, the set time budget is the sum of each
  item's `time_budget_seconds`.
- Do not apply weekly quiz-specific fixed-window rules such as
  `DEFAULT_WINDOW_SECONDS` checks to the common format. Keep weekly quiz
  scheduling/publication policy in weekly quiz-specific rules only.

## Legacy tooling

- `convert-exam-md-to-html` is legacy/deprecated. Do not use it for new
  Markdown question compilation, validation, preview, or publication flows.
- Migrate existing workflows toward `markdown-to-qti` output and QTI packages
  instead of HTML generated from exam Markdown.

Source: agent-rules-local/rules/markdown-to-qti.md

# Project Rules: markdown-to-qti

## Scope

- This repository MUST implement an npm-installable TypeScript CLI that converts Markdown content into IMS QTI 3.0.
- Prioritize correctness of QTI 3.0 output and a clean internal data model.

## TypeScript / npm conventions

- Use TypeScript with a Node.js ESM npm package and expose the `markdown-to-qti` bin through `package.json`.
- Do not require Java, Gradle, a JDK, or JVM launchers for normal install or runtime use.
- Keep the CLI entrypoint small; put conversion logic into testable modules.
- Favor immutable data, explicit types at boundaries, and clear error types.

## QTI 3.0 output rules

- Output must be valid QTI 3.0 XML (well-formed, schema-aligned).
- Keep identifiers stable and deterministic (avoid random IDs unless explicitly required).
- When behavior is ambiguous, prefer standards-compliant conservative output and document the decision.

## Markdown conversion behavior

- Define supported Markdown features explicitly (e.g., headings, lists, code blocks, inline formatting).
- If a requested Markdown construct cannot be represented in QTI, do not implement it and explicitly state this in the response.
- Unsupported constructs should fail fast with actionable messages, or be safely downgraded with clear warnings.
- Preserve exact formatting as much as possible when mapping to QTI.

## Testing expectations

- Add unit tests for parsing/mapping rules.
- Add golden tests for QTI XML output (compare normalized XML).
- Include a few end-to-end fixtures (Markdown input -> QTI output) under a dedicated test folder.
- Golden tests MUST preserve parity with the historical Kotlin fixture outputs unless an intentional format change is documented.

## CLI / UX

- Provide a simple CLI with:
  - input path(s)
  - output directory
  - validation mode (validate-only)
  - verbose logging
- Provide `--help`/`-h`, `--version`/`-V`, and `--json` for first-run discoverability and machine-readable use.
- Error messages must include the source location when possible (file + line/column).
