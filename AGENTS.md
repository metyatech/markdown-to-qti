<!-- markdownlint-disable MD025 -->
# Tool Rules (compose-agentsmd)
- Before starting any work, run `compose-agentsmd` from the project root.
- To update shared rules, run `compose-agentsmd edit-rules`, edit the workspace rules, then run `compose-agentsmd apply-rules`.
- Do not edit `AGENTS.md` directly; update the source rules and regenerate.
- These tool rules live in tools/tool-rules.md in the compose-agentsmd repository; do not duplicate them in global rule modules.
- When updating rules, include a colorized diff-style summary in the final response. Use `git diff --stat` first, then include the raw ANSI-colored output of `git diff --color=always` (no sanitizing or reformatting), and limit the output to the rule files that changed.
- Also provide a short, copy-pasteable command the user can run to view the diff in the same format. Use absolute paths so it works regardless of the current working directory, and scope it to the changed rule files.
- If a diff is provided, a separate detailed summary is not required. If a diff is not possible, include a detailed summary of what changed (added/removed/modified items).

Source: github:metyatech/agent-rules@HEAD/rules/global/agent-rules-composition.md

# Rule composition and maintenance

## Scope and composition

- AGENTS.md is self-contained; do not rely on parent/child AGENTS for inheritance or precedence.
- Maintain shared rules centrally and compose per project; use project-local rules only for truly local policies.
- Place AGENTS.md at the project root; only add another AGENTS.md for nested independent projects.

## Update policy

- Never edit AGENTS.md directly; update source rules and regenerate AGENTS.md.
- A request to "update rules" means: update the appropriate rule module and ruleset, then regenerate AGENTS.md.
- If the user gives a persistent instruction (e.g., "always", "must"), encode it in the appropriate module (global vs local).
- When acknowledging a new persistent instruction, update the rule module in the same change set and regenerate AGENTS.md.
- When creating a new repository, set up rule files (e.g., agent-ruleset.json and any local rules) so compose-agentsmd can run.
- When updating rules, infer the core intent; if it is a global policy, record it in global rules rather than project-local rules.
- If a task requires domain rules not listed in agent-ruleset.json, update the ruleset to include them and regenerate AGENTS.md before proceeding.
- When rule changes produce a diff, include it in the final response unless the user explicitly asks to omit it.

## Editing standards

- Keep rules MECE, concise, and non-redundant.
- Use short, action-oriented bullets; avoid numbered lists unless order matters.
- Prefer the most general applicable rule to avoid duplication.

Source: github:metyatech/agent-rules@HEAD/rules/global/autonomous-operations.md

# Autonomous operations

- Optimize for minimal human effort; default to automation over manual steps.
- Drive work from the desired outcome: infer acceptance criteria, choose the shortest safe path, and execute end-to-end.
- Assume end-to-end autonomy for repository operations (issue triage, PRs, direct pushes to main/master, merges, releases, repo admin) only within repositories under the user's control (e.g., owned by metyatech or where the user has explicit maintainer/push authority), unless the user restricts scope; for third-party repos, require explicit user request before any of these operations.
- Do not preserve backward compatibility unless explicitly requested; avoid legacy aliases and compatibility shims by default.
- When work reveals rule gaps, redundancy, or misplacement, proactively update rule modules/rulesets (including moves/renames) and regenerate AGENTS.md without waiting for explicit user requests.
- After each task, run a brief retrospective; if you notice avoidable mistakes, missing checks, or recurring back-and-forth, encode the fix as a rule update and regenerate AGENTS.md.
- Treat these rules as the source of truth; do not override them with repository conventions. If a repo conflicts, update the repo to comply or update the rules to encode the exception; do not make undocumented exceptions.
- When something is unclear, investigate to resolve it; do not proceed with unresolved material uncertainty. If still unclear, ask and include what you checked.
- Do not proceed based on assumptions or guesses without explicit user approval; hypotheses may be discussed but must not drive action.
- Ask only blocking questions; for non-material ambiguities, pick the lowest-risk option, state the assumption, and proceed.
- Make decisions explicit when they affect scope, risk, cost, or irreversibility.
- Prefer asynchronous, low-friction control channels (GitHub Issues/PR comments) unless a repository mandates another.
- Design autonomous workflows for high volume: queue requests, set concurrency limits, and auto-throttle to prevent overload.

Source: github:metyatech/agent-rules@HEAD/rules/global/command-execution.md

# Workflow and command execution

- Do not add wrappers or pipes to commands unless the user explicitly asks.
- Prefer repository-standard scripts/commands (package.json scripts, README instructions).
- Reproduce reported command issues by running the same command (or closest equivalent) before proposing fixes.
- Avoid interactive git prompts by using --no-edit or setting GIT_EDITOR=true.
- If elevated privileges are required, use sudo where available; otherwise run as Administrator.
- Keep changes scoped to affected repositories; when shared modules change, update consumers and verify at least one.
- If no branch is specified, work on the current branch; direct commits to main/master are allowed.
- After addressing PR comments, resolve related conversations; after completing a PR, merge it, sync the target branch, and delete the PR branch locally and remotely.

Source: github:metyatech/agent-rules@HEAD/rules/global/implementation-and-coding-standards.md

# Engineering and implementation standards

- Prefer official/standard approaches recommended by the framework or tooling.
- Prefer well-maintained external dependencies; build in-house only when no suitable option exists.
- If functionality appears reusable, assess reuse first and propose a shared module/repo; prefer remote dependencies (never local filesystem paths).
- Maintainability > testability > extensibility > readability.
- Single responsibility; keep modules narrowly scoped and prefer composition over inheritance.
- Keep dependency direction clean and swappable; avoid global mutable state.
- Avoid deep nesting; use guard clauses and small functions.
- Use clear, intention-revealing naming; avoid "Utils" dumping grounds.
- Prefer configuration/constants over hardcoding; consolidate change points.
- Keep everything DRY across code, specs, docs, tests, configs, and scripts; proactively refactor repeated procedures into shared configs/scripts with small, local overrides.
- Fix root causes; remove obsolete/unused code, branches, comments, and helpers.
- Externalize large embedded strings/templates/rules when possible.
- Do not commit build artifacts (follow the repo's .gitignore).
- Align file/folder names with their contents and keep naming conventions consistent.
- Do not assume machine-specific environments (fixed workspace directories, drive letters, per-PC paths). Prefer repo-relative paths and explicit configuration so workflows work in arbitrary clone locations.

Source: github:metyatech/agent-rules@HEAD/rules/global/linting-formatting-and-static-analysis.md

# Linters, formatters, and static analysis

## General policy

- Every code repo must have a formatter and a linter/static analyzer for its primary languages.
- Prefer one formatter and one linter per language; avoid overlapping tools that fight each other.
- Follow the standard toolchains below. If a repo conflicts, migrate it to comply unless the user explicitly restricts scope.
- If you believe an exception is needed, encode it as a rule update and regenerate AGENTS.md before proceeding.
- Enforce in CI: run formatting checks (verify-no-changes) and linting on pull requests and require them for merges.
- Treat warnings as errors in CI; when a tool cannot, use its strictest available setting so warnings fail CI.
- Do not disable rules globally; keep suppressions narrow, justified, and time-bounded.
- Pin tool versions (lockfiles/manifests) for reproducible CI.

## Security baseline

- Require dependency vulnerability scanning appropriate to the ecosystem (SCA) for merges. If you cannot enable it, report the limitation and get explicit user approval before proceeding without it.
- Enable GitHub secret scanning and remediate findings; never commit secrets. If it is unavailable, add a repo-local secret scanner and require it for merges.
- Enable CodeQL code scanning for supported languages. If it cannot be enabled, report the limitation and use the best available alternative for that ecosystem.

## Default toolchain by language

### JavaScript / TypeScript (incl. React/Next)

- Format+lint: ESLint + Prettier.
- Typecheck: `tsc` with strict settings for TS projects.
- Dependency scan: `osv-scanner`. If unsupported, use the package manager's audit tooling.

### Python

- Format+lint: Ruff.
- Typecheck: Pyright.
- Dependency scan: pip-audit.

### Go

- Format: gofmt.
- Lint/static analysis: golangci-lint (includes staticcheck).
- Dependency scan: govulncheck.

### Rust

- Format: cargo fmt.
- Lint/static analysis: cargo clippy with warnings as errors.
- Dependency scan: cargo audit.

### Java

- Format: Spotless + google-java-format.
- Lint/static analysis: Checkstyle + SpotBugs.
- Dependency scan: OWASP Dependency-Check.

### Kotlin

- Format: Spotless + ktlint.
- Lint/static analysis: detekt.
- Compiler: enable warnings-as-errors in CI; if impractical, get explicit user approval before relaxing.

### C#

- Format: dotnet format (verify-no-changes in CI).
- Lint/static analysis: enable .NET analyzers; treat warnings as errors; enable nullable reference types.
- Dependency scan: `dotnet list package --vulnerable`.

### C++

- Format: clang-format.
- Lint/static analysis: clang-tidy.
- Build: enable strong warnings and treat as errors; run sanitizers (ASan/UBSan) in CI where supported.

### PowerShell

- Format+lint: PSScriptAnalyzer (Invoke-Formatter + Invoke-ScriptAnalyzer).
- Runtime: Set-StrictMode -Version Latest; fail fast on errors.
- Tests: Pester when tests exist.

### Shell (sh/bash)

- Format: shfmt.
- Lint: shellcheck.

### Dockerfile

- Lint: hadolint.

### Terraform

- Format: terraform fmt -check.
- Validate: terraform validate.
- Lint: tflint.
- Security scan: trivy config.

### YAML

- Lint: yamllint.

### Markdown

- Lint: markdownlint.

Source: github:metyatech/agent-rules@HEAD/rules/global/observability-and-diagnostics.md

# Observability and diagnostics

## General policy

- Design for debuggability: make failures diagnosable from logs/metrics/traces without reproducing locally.
- Add observability in the same change set as behavior changes that affect runtime behavior, performance, or reliability.

## Logging

- Prefer structured logs for services; keep field names stable (e.g., level, message, component, request_id/trace_id, version).
- Include actionable context in errors (what failed, which input/state, what to do next) without logging secrets/PII.
- Log at the right level; avoid noisy logs in hot paths.

## Metrics

- Instrument the golden signals (latency, traffic, errors, saturation) for each service and critical dependency; define concrete SLIs/SLOs for user-facing flows.
- Use OpenTelemetry Metrics for instrumentation and OTLP for export; using vendor-specific metrics SDKs directly is an exception and requires explicit user approval.
- Use the right metric types (counters for monotonic totals, histograms for latencies/sizes, gauges for current values) and include explicit units in names.
- Keep metric names and label keys stable; use a consistent namespace and Prometheus-style `snake_case` naming with base-unit suffixes (e.g., `http_server_request_duration_seconds`).
- Constrain label cardinality: labels must come from small bounded sets; never use user identifiers, raw URLs, request bodies, or other unbounded values as labels.
- Ensure correlation: when supported, record exemplars or identifiers that let you jump from a metric spike to representative traces/logs.
- Treat missing/incorrect metrics as a defect when they block verification, incident response, or SLO evaluation; add/adjust dashboards and alerts with behavior changes that impact reliability/performance.

## Alerting

- Alerting is part of the definition of done for reliability/performance changes: update dashboards, alerts, and runbooks in the same change set.
- Define alert severity and routing explicitly; paging alerts must correspond to user-impacting SLO/error-budget burn, not “interesting” internal signals.
- Use multi-window burn-rate alerting to reduce flapping; page only on sustained burn and use ticket-level alerts for slower burn or early-warning signals.
- Every alert must be actionable and owned: include service/team ownership labels and a runbook link that lists diagnosis steps, mitigation steps, and rollback/feature-flag options.
- Every alert must include a dashboard link and relevant identifiers (service, environment, region/cluster) so responders can triage quickly.
- Reduce noise aggressively: delete or downgrade alerts that page without clear user impact; treat alert fatigue and stale/non-actionable alerts as defects.
- Alert rules must be managed as code and reviewed with code changes; manual, ad-hoc changes in vendor UIs are prohibited.
- Alert rules must be automatically validated and tested in CI; for Prometheus-compatible rules this means `promtool check rules` and `promtool test rules`.
- If constraints make “alerts as code” or CI validation impractical, treat it as an exception and require explicit user approval with documented rationale.

## Tracing

- For multi-service or async flows, use OpenTelemetry and propagate context across boundaries (HTTP/gRPC/queues).
- Correlate logs and traces via trace_id/request_id.

## Health and self-checks

- Services must have readiness and liveness checks; fail fast when dependencies are unavailable.
- CLIs should provide a verbose mode and clear error output; add a self-check command when it reduces support burden.

Source: github:metyatech/agent-rules@HEAD/rules/global/quality-testing-and-errors.md

# Quality, testing, and error handling

## Quality priority

- Quality (correctness, safety, robustness, verifiability) takes priority over speed or convenience.

## Definition of done

- Do not claim "fixed"/"done" unless it is verified by reproducing the issue and/or running the relevant checks.
- For code changes, treat "relevant checks" as the repo's full lint/typecheck/test/build suite (prefer CI results).
- Prefer a green baseline: if relevant checks fail before you change anything, report it and get explicit user approval before proceeding.
- If you cannot reproduce/verify, do not guess a fix; request missing info or create a failing regression test.
- Always report verification: list the exact commands/steps run and their outcome; if anything is unverified, state why and how to verify.

## Verification

- Run the repo's full lint/typecheck/test/build checks using repo-standard commands.
- If you are unsure what constitutes the full suite, run the repo's default verify/CI commands rather than guessing.
- Before committing code changes, run the full suite; if a relevant check is missing and feasible to add, add it in the same change set.
- Enforce via CI: run the full suite on pull requests and on pushes to the default branch; if no CI harness exists, add one using repo-standard commands.
- Configure required status checks on the default branch when you have permission; otherwise report the limitation.
- Do not rely on smoke-only gating or scheduled-only full runs for correctness; merges must require the full suite.
- Ensure commit-time automation (pre-commit or repo-native) runs the full suite when feasible.
- If required checks cannot be run, treat it as an exception: explain why, provide exact commands/steps, and get explicit user approval before proceeding.
- Never disable checks, weaken assertions, loosen types, or add retries solely to make checks pass.

## Tests (behavior changes)

- Follow test-first: add/update tests, observe failure, implement the fix, then observe pass.
- For bug fixes, add a regression test that fails before the fix at the level where the bug occurs (unit/integration/E2E).
- Add/update automated tests for behavior changes and regression coverage.
- Cover success, failure, boundary, invalid input, and key state transitions (including first-run/cold-start vs subsequent-run behavior when relevant); include representative concurrency/retry/recovery when relevant.
- Keep tests deterministic; minimize time/random/external I/O; inject when needed.
- For deterministic output files, use full-content snapshot/golden tests.
- Prefer making nondeterministic failures reproducible over adding sleeps/retries; do not mask flakiness.
- For integration boundaries (network/DB/external services/UI flows), add an integration/E2E/contract test that exercises the boundary; avoid unit-only coverage for integration bugs.
- For non-trivial changes, create a small test matrix (scenarios × inputs × states) and cover the highest-risk combinations; document intentional gaps.

## Feedback loops and root causes

- Treat time-to-detect and time-to-fix as quality attributes; shorten the feedback loop with automation and observability rather than relying on manual QA.
- For any defect fix or incident remediation, perform a brief root-cause classification: implementation mistake, design deficit, and/or ambiguous/incorrect requirements.
- Feed the root cause upstream in the same change set: add or tighten tests/checks/alerts, update specs/acceptance criteria, and update design docs/ADRs when applicable.
- If the failure should have been detected earlier, add a gate at the earliest reliable point (lint/typecheck/tests/CI required checks or runtime alerts/health checks); skipping this requires explicit user approval.
- Record the prevention mechanism (what will catch it next time) in the PR description or issue comment; avoid “fixed” without a concrete feedback-loop improvement.

## Exceptions

- If required tests are impractical, document the coverage gap, provide a manual verification plan, and get explicit user approval before skipping.

## Error handling and validation

- Never swallow errors; fail fast or return early with explicit errors.
- Error messages must reflect actual state and include relevant input context.
- Validate config and external inputs at boundaries; fail with actionable guidance.
- Log minimally but with diagnostic context; never log secrets or personal data.
- Remove temporary debugging/instrumentation before the final patch.

Source: github:metyatech/agent-rules@HEAD/rules/global/user-identity-and-accounts.md

# User identity and accounts

- The user's name is "metyatech".
- Any external reference using "metyatech" (GitHub org/user, npm scope, repos) is under the user's control.
- The user has GitHub and npm accounts.
- Use the gh CLI to verify GitHub details when needed.
- When publishing, cloning, adding submodules, or splitting repos, prefer the user's "metyatech" ownership unless explicitly instructed otherwise.

Source: github:metyatech/agent-rules@HEAD/rules/global/writing-and-documentation.md

# Writing and documentation

## User responses

- Respond in Japanese unless the user requests otherwise.
- Always report whether you committed and whether you pushed; include repo(s), branch(es), and commit hash(es) when applicable.
- After completing a response, emit the Windows SystemSounds.Asterisk sound via PowerShell when possible.

## Developer-facing writing

- Write developer documentation, code comments, and commit messages in English.
- Rule modules are written in English.

## README and docs

- Every repository must include README.md covering overview/purpose, setup, dev commands (build/test/lint), required env/config, and release/deploy steps if applicable.
- For any code change, assess README impact and update it in the same change set when needed.
- If a README update is not needed, explain why in the final response.
- CLI examples in docs must include required parameters.
- Do not include user-specific local paths, fixed workspace directories, drive letters, or personal data in doc examples. Prefer repo-relative paths and placeholders so instructions work in arbitrary environments.

## Markdown linking

- When a Markdown document links to a local file, use a path relative to the Markdown file.

Source: github:metyatech/agent-rules@HEAD/rules/domains/cli/cli-standards.md

# CLI standards

- Provide --help/-h with clear usage, options, and examples; include required parameters in examples.
- Provide --version (use -V); reserve -v for --verbose.
- Support stdin/stdout piping; allow output redirection (e.g., --output for file creation).
- Offer machine-readable output (e.g., --json) when emitting structured data.
- For modifying/deleting actions, provide --dry-run and an explicit bypass (--yes/--force).
- Provide controllable logging (--quiet, --verbose, or --trace).
- Use deterministic exit codes (0 success, non-zero failure) and avoid silent fallbacks.
- For JSON configuration, define/update a JSON Schema and validate config on load.
- For interactive CLI prompts, provide required context before asking; for yes/no prompts, Enter means "Yes" and "n" means "No".

Source: github:metyatech/agent-rules@HEAD/rules/domains/exam/exam-markdown-format.md

# Exam Markdown

- When creating or editing exam Markdown, follow the format in markdown-to-qti/markdown-question-spec.md.

Source: agent-rules-local/rules/markdown-to-qti.md

# Project Rules: markdown-to-qti (Kotlin)

## Scope

- This repository will implement a tool that converts Markdown content into IMS QTI 3.0.
- Prioritize correctness of QTI 3.0 output and a clean internal data model.

## Kotlin / Gradle conventions

- Prefer Kotlin (JVM) with Gradle.
- Keep the entrypoint small; put logic into testable functions/classes.
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

## CLI / UX

- Provide a simple CLI with:
  - input path(s)
  - output directory
  - validation mode (validate-only)
  - verbose logging
- Error messages must include the source location when possible (file + line/column).
