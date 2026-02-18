<!-- markdownlint-disable MD025 -->
# Tool Rules (compose-agentsmd)
- Before starting any work, run `compose-agentsmd` from the project root.
- `compose-agentsmd` intentionally regenerates `AGENTS.md`; any resulting `AGENTS.md` diff is expected and must not be treated as an unexpected external change.
- If `compose-agentsmd` is not available, install it via npm: `npm install -g compose-agentsmd`.
- To update shared rules, run `compose-agentsmd edit-rules`, edit the workspace rules, then run `compose-agentsmd apply-rules`.
- `compose-agentsmd apply-rules` pushes the rules workspace when `source` is GitHub (if the workspace is clean), then regenerates `AGENTS.md` with refreshed rules.
- Do not edit `AGENTS.md` directly; update the source rules and regenerate.
- `tools/tool-rules.md` is the shared rule source for all repositories that use compose-agentsmd.
- Before applying any rule updates, present the planned changes first (prefer a colorized diff-style preview), ask for explicit approval, then make the edits.
- These tool rules live in tools/tool-rules.md in the compose-agentsmd repository; do not duplicate them in global rule modules.
- When updating rules, include a colorized diff-style summary in the final response. Use `git diff --stat` first, then include the raw ANSI-colored output of `git diff --color=always` (no sanitizing or reformatting), and limit the output to the rule files that changed.
- Also provide a short, copy-pasteable command the user can run to view the diff in the same format. Use absolute paths so it works regardless of the current working directory, and scope it to the changed rule files.
- If a diff is provided, a separate detailed summary is not required. If a diff is not possible, include a detailed summary of what changed (added/removed/modified items).

Source: github:metyatech/agent-rules@HEAD/rules/global/00-delivery-hard-gates.md

# Delivery hard gates

These are non-negotiable completion gates for any state-changing work and for any response that claims "done", "fixed", "working", or "passing".

## Acceptance criteria (AC)

- Before state-changing work, list Acceptance Criteria (AC) as binary, testable statements.
- For read-only tasks, AC may be deliverables/questions answered; keep them checkable.
- If AC are ambiguous or not testable, ask blocking questions before proceeding.

## Evidence and verification

- For each AC, define verification evidence (automated test preferred; otherwise a deterministic manual procedure).
- Maintain an explicit mapping: `AC -> evidence (tests/commands/manual steps)`.
- For code or runtime-behavior changes, automated tests are required unless the requester explicitly approves skipping.
- Bugfixes MUST include a regression test that fails before the fix and passes after.
- Run the repo's full verification suite (lint/format/typecheck/test/build) using a single repo-standard `verify` command when available; if missing, add it.
- Enforce verification locally via commit-time hooks (pre-commit or repo-native) and in CI; skipping requires explicit requester approval.
- For non-code changes, run the relevant subset and justify.
- If required checks cannot be run, stop and ask for explicit approval to proceed with partial verification, and provide an exact manual verification plan.

## Final response (MUST include)

- AC list.
- `AC -> evidence` mapping with outcomes (PASS/FAIL/NOT RUN/N/A) and brief notes where needed.
- The exact verification commands executed and their outcomes.

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
- Drive work from the desired outcome: infer acceptance criteria, choose the highest-quality safe path that satisfies the requested quality/ideal bar, and execute end-to-end.
- Treat speed as a secondary optimization; never trade down correctness, safety, robustness, or verifiability unless the requester explicitly approves that tradeoff.
- Assume end-to-end autonomy for repository operations (issue triage, PRs, direct pushes to main/master, merges, releases, repo admin) only within repositories under the user's control (e.g., owned by metyatech or where the user has explicit maintainer/push authority), unless the user restricts scope; for third-party repos, require explicit user request before any of these operations.
- Do not preserve backward compatibility unless explicitly requested; avoid legacy aliases and compatibility shims by default.
- When work reveals rule gaps, redundancy, or misplacement, proactively update rule modules/rulesets (including moves/renames) and regenerate AGENTS.md without waiting for explicit user requests.
- After each task, run a brief retrospective; if you notice avoidable mistakes, missing checks, or recurring back-and-forth, encode the fix as a rule update and regenerate AGENTS.md.
- If you state a persistent workflow change (e.g., "from now on", "I'll always"), immediately propose the corresponding rule update and request approval in the same task; do not leave it as an unrecorded promise.
- Because session memory resets between tasks, treat rule files as persistent memory; when any issue or avoidable mistake occurs, update rules in the same task to prevent recurrence.
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
- After addressing PR review feedback, resolve the corresponding review thread(s) before concluding; if you lack permission, state it explicitly.
- After pushing fixes for PR review feedback, re-request review only from reviewer(s) who posted the addressed feedback in the current round.
- Do not re-request review from reviewers (including AI reviewers) who did not post addressed feedback, or who already indicated no actionable issues.
- If no applicable reviewer remains, ask who should review next.
- When Codex and/or Copilot review bots are configured for the repo, trigger re-review only for the bot(s) that posted addressed feedback.
- For Codex re-review (only when applicable): comment `@codex review` on the PR.
- For Copilot re-review (only when applicable): use `gh api` to remove+re-request the bot reviewer `copilot-pull-request-reviewer[bot]` (do not rely on `gh pr edit --add-reviewer Copilot`).
  - Remove: `gh api --method DELETE /repos/{owner}/{repo}/pulls/{pr}/requested_reviewers -f "reviewers[]=copilot-pull-request-reviewer[bot]"`
  - Add: `gh api --method POST /repos/{owner}/{repo}/pulls/{pr}/requested_reviewers -f "reviewers[]=copilot-pull-request-reviewer[bot]"`
- After completing a PR, merge it, sync the target branch, and delete the PR branch locally and remotely.

Source: github:metyatech/agent-rules@HEAD/rules/global/implementation-and-coding-standards.md

# Engineering and implementation standards

- Prefer official/standard approaches recommended by the framework or tooling.
- Prefer well-maintained external dependencies; build in-house only when no suitable option exists.
- Prefer third-party tools/services over custom implementations when they can meet the requirements; prefer free options (OSS/free-tier) when feasible and call out limitations/tradeoffs.
- PowerShell: `\` is a literal character (not an escape). Do not cargo-cult `\\` escaping patterns from other languages; validate APIs that require names like `Local\...` (e.g., named mutexes).
- PowerShell: avoid assigning to or shadowing automatic/read-only variables (e.g., `$args`, `$PID`); use different names for locals.
- PowerShell: when invoking PowerShell from PowerShell, avoid double-quoted `-Command` strings that allow the outer shell to expand `$...`; prefer `-File`, single quotes, or here-strings to control expansion.
- If functionality appears reusable, assess reuse first and propose a shared module/repo; prefer remote dependencies (never local filesystem paths).
- Maintainability > testability > extensibility > readability.
- Single responsibility; keep modules narrowly scoped and prefer composition over inheritance.
- Keep dependency direction clean and swappable; avoid global mutable state.
- Avoid deep nesting; use guard clauses and small functions.
- Use clear, intention-revealing naming; avoid "Utils" dumping grounds.
- Prefer configuration/constants over hardcoding; consolidate change points.
- For GUI changes, prioritize ergonomics and discoverability so first-time users can complete core flows without external documents.
- Every user-facing GUI component (inputs, actions, status indicators, lists, and dialog controls) must include an in-app explanation (for example tooltip, context help panel, or equivalent).
- Do not rely on README-only guidance for GUI operation; critical usage guidance must be available inside the GUI itself.
- Keep everything DRY across code, specs, docs, tests, configs, and scripts; proactively refactor repeated procedures into shared configs/scripts with small, local overrides.
- Persist durable runtime/domain data in a database with a fully normalized schema (3NF/BCNF target): store each fact once with keys/constraints, and compute derived statuses/views at read time instead of duplicating them.
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

## Design and visual accessibility automation

- For any design/UI styling change in any project, enforce automated visual accessibility checks as part of the repo-standard `verify` command and CI.
- Do not rely on per-page/manual test maintenance; use route discovery (for example sitemap, generated route lists, or framework route manifests) so newly added pages are automatically included.
- Validate both light and dark themes when theme switching is supported.
- Validate at least default, hover, and focus states for interactive elements.
- Enforce non-text boundary contrast checks across all visible UI elements that present boundaries (including interactive controls and container-like elements), not only predefined component classes.
- Do not hardcode a narrow selector allowlist for boundary checks; use broad DOM discovery with only minimal technical exclusions (for example hidden/zero-size/non-rendered nodes).
- Fail CI on violations; do not silently ignore design regressions.
- If temporary exclusions are unavoidable, keep them narrowly scoped, documented with rationale, and remove them promptly.

## Security baseline

- Require dependency vulnerability scanning appropriate to the ecosystem (SCA) for merges. If you cannot enable it, report the limitation and get explicit user approval before proceeding without it.
- Enable GitHub secret scanning and remediate findings; never commit secrets. If it is unavailable, add a repo-local secret scanner and require it for merges.
- Enable CodeQL code scanning for supported languages. If it cannot be enabled, report the limitation and use the best available alternative for that ecosystem.

## Default toolchain by language

### JavaScript / TypeScript (incl. React/Next)

- Format+lint: ESLint + Prettier.
- When configuring Prettier, always add and maintain `.prettierignore` so generated/build outputs and composed files are not formatted/linted as source (e.g., `dist/`, build artifacts, and `AGENTS.md` when generated by compose-agentsmd).
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
- Enforce PSScriptAnalyzer via the repo’s standard `verify` command/script when PowerShell is used; treat findings as errors.

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

## Performance investigations

- For performance/latency issues, measure first: establish a baseline, then use profiling/instrumentation to identify hotspots; do not implement "optimizations" based on guesswork.
- Record before/after numbers and the measurement method in the change set (tests, benchmark output, logs, or deterministic manual steps).
- Prefer automated performance regression tests/benchmarks when feasible; otherwise provide deterministic manual measurement steps.

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

Source: github:metyatech/agent-rules@HEAD/rules/global/planning-and-approval-gate.md

# Planning and approval gate

- Default to a two-phase workflow: clarify goal + plan first, execute after explicit requester approval.
- If a request may require any state-changing work, you MUST first dialogue with the requester to clarify details and make the goal explicit. Do not proceed while the goal is ambiguous.
- Allowed before approval:
  - Clarifying questions and read-only inspection (reading files, searching, and `git status` / `git diff` / `git log`).
  - Any unavoidable automated work triggered as a side-effect of those read-only commands.
  - Any command execution that must not adversely affect program behavior or external systems (including changes made by tooling), such as:
    - Installing/restoring dependencies using repo-standard tooling (lockfile changes are allowed).
    - Running formatters/linters/typecheck/tests/builds (including auto-fix/formatting that modifies files).
    - Running code generation/build steps that are deterministic and repo-scoped.
    - Running these from clean → dirty → clean is acceptable; publishing/deploying/migrating is not.
- Before any other state-changing execution (e.g., writing or modifying files by hand, changing runtime behavior, or running git commands beyond status/diff/log), do all of the following:
  - Restate the request as Acceptance Criteria (AC) and verification methods, following "Delivery hard gates".
  - Produce a written plan (use your planning tool when available) focused on the goal, approach, and verification checkpoints (do not enumerate per-file implementation details or exact commands unless the requester asks).
  - Confirm the plan with the requester, ask for approval explicitly, and wait for a clear “yes” before executing.
  - Once the requester has approved a plan, proceed within that plan without re-requesting approval; re-request approval only when you change or expand the plan.
  - Do not treat the original task request as plan approval; approval must be an explicit response to the presented plan.
- If state-changing execution starts without the required post-plan “yes”, stop immediately, report the gate miss, add/update a prevention rule, regenerate AGENTS.md, and then restart from the approval gate.
- No other exceptions: even if the user requests immediate execution (e.g., “skip planning”, “just do it”), treat that as a request to move quickly through this gate, not to bypass it.

Source: github:metyatech/agent-rules@HEAD/rules/global/quality-testing-and-errors.md

# Quality, testing, and error handling

## Quality priority

- Quality (correctness, safety, robustness, verifiability) takes priority over speed or convenience.

## Definition of done

- Do not claim "fixed"/"done" unless it is verified by reproducing the issue and/or running the relevant checks.
- For code changes, treat "relevant checks" as the repo's full lint/typecheck/test/build suite (prefer CI results).
- Prefer a green baseline: if relevant checks fail before you change anything, report it and get explicit user approval before proceeding.
- If you cannot reproduce/verify, do not guess a fix; request missing info or create a failing regression test.
- Follow "Delivery hard gates" for Acceptance Criteria, verification evidence, and final reporting; if anything is unverified, state why and how to verify.

## Verification

- Follow "Delivery hard gates" for running and reporting verification.
- If you are unsure what constitutes the full suite, run the repo's default verify/CI commands rather than guessing.
- Before committing code changes, run the full suite; if a relevant check is missing and feasible to add, add it in the same change set.
- Enforce via CI: run the full suite on pull requests and on pushes to the default branch, and make it a required status check for merges; if no CI harness exists, add one using repo-standard commands.
- Configure required status checks on the default branch when you have permission; otherwise report the limitation.
- Do not rely on smoke-only gating or scheduled-only full runs for correctness; merges must require the full suite.
- Ensure commit-time automation (pre-commit or repo-native) runs the full suite and blocks commits.
- Never disable checks, weaken assertions, loosen types, or add retries solely to make checks pass.

## Tests (behavior changes)

- Follow test-first: add/update tests, observe failure, implement the fix, then observe pass.
- For bugfixes, follow "Delivery hard gates" (regression test: fail-before/pass-after).
- Add/update automated tests for behavior changes and regression coverage.
- Cover success, failure, boundary, invalid input, and key state transitions (including first-run/cold-start vs subsequent-run behavior when relevant); include representative concurrency/retry/recovery when relevant.
- Keep tests deterministic; minimize time/random/external I/O; inject when needed.
- For deterministic output files, use full-content snapshot/golden tests.
- Prefer making nondeterministic failures reproducible over adding sleeps/retries; do not mask flakiness.
- For timing/order/race issues, prefer deterministic synchronization (events, versioned state, acks/handshakes) over fixed sleeps.
- If a heuristic wait is unavoidable, it MUST be condition-based with a hard deadline and diagnostics, and requires explicit requester approval.
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

Source: github:metyatech/agent-rules@HEAD/rules/global/superpowers-integration.md

# Superpowers integration

- If Superpowers skills are available in the current agent environment, use them to drive *how* you work (design, planning, debugging, TDD, review) instead of inventing an ad-hoc process.
- Do not duplicate Superpowers installation/usage instructions in this ruleset; follow Superpowers’ own guidance for loading/invoking skills.
- The hard gates in this ruleset still apply when using Superpowers workflows:
  - Before any state-changing work: present AC + AC->evidence + a plan, then wait for an explicit “yes”.
  - After changes: report AC -> evidence outcomes and the exact verification commands executed.
- When a Superpowers workflow asks for writing docs / commits / pushes, treat those as state-changing steps: include them in the plan and require explicit requester approval before doing them.
- If Superpowers skills are unavailable, proceed with these rules as the fallback.

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

- Every repository must include README.md covering overview/purpose, supported environments/compatibility, install/setup, usage examples, dev commands (build/test/lint/format), required env/config, release/deploy steps if applicable, and links to SECURITY.md / CONTRIBUTING.md / LICENSE / CHANGELOG.md when they exist.
- For any change, assess documentation impact and update all affected docs in the same change set so docs match behavior (README, docs/, examples, comments, templates, ADRs/specs, diagrams).
- If no documentation updates are needed, explain why in the final response.
- For CLIs, document every parameter (required and optional) with a description and at least one example; also include at least one end-to-end example command.
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
