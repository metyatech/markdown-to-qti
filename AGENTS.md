<!-- markdownlint-disable MD025 -->
# Tool Rules (compose-agentsmd)

- **Session gate**: before responding to ANY user message, run `compose-agentsmd` from the project root. AGENTS.md contains the rules you operate under; stale rules cause rule violations. If you discover you skipped this step mid-session, stop, run it immediately, re-read the diff, and adjust your behavior before continuing.
- `compose-agentsmd` intentionally regenerates `AGENTS.md`; any resulting `AGENTS.md` diff is expected and must not be treated as an unexpected external change.
- If `compose-agentsmd` is not available, install it via npm: `npm install -g compose-agentsmd`.
- To update shared/global rules, use `compose-agentsmd edit-rules` to locate the writable rules workspace, make changes only in that workspace, then run `compose-agentsmd apply-rules` (do not manually clone or edit the rules source repo outside this workflow).
- If you find an existing clone of the rules source repo elsewhere, do not assume it is the correct rules workspace; always treat `compose-agentsmd edit-rules` output as the source of truth.
- `compose-agentsmd apply-rules` pushes the rules workspace when `source` is GitHub (if the workspace is clean), then regenerates `AGENTS.md` with refreshed rules.
- Do not edit `AGENTS.md` directly; update the source rules and regenerate.
- `tools/tool-rules.md` is the shared rule source for all repositories that use compose-agentsmd.
- Before applying any rule updates, present the planned changes first with an ANSI-colored diff-style preview, ask for explicit approval, then make the edits.
- These tool rules live in tools/tool-rules.md in the compose-agentsmd repository; do not duplicate them in other rule modules.

Source: github:metyatech/agent-rules@HEAD/rules/global/agent-rules-composition.md

# Rule composition and maintenance

- AGENTS.md is self-contained; place at project root. Shared rules centrally; project-local only for truly local policies.
- Before work in a repo with `agent-ruleset.json`, run `compose-agentsmd` to refresh AGENTS.md.
- Pre-commit hooks must run the repo's full verification suite, then `compose-agentsmd --compose`, then `git add AGENTS.md`. Do not fail commits on drift or add freshness checks to CI.

## Update and editing

- Never edit AGENTS.md directly; update source rules and regenerate. "Update rules" = update module/ruleset, then regenerate.
- Persistent user instructions → encode in appropriate module (global vs local) in the same change set.
- New repos must meet all global rules (AGENTS.md, CI, linting, community health, docs, scanning) before reporting complete.
- Update rulesets for missing domain rules before proceeding. Omit AGENTS.md diffs unless asked.
- Treat AGENTS.md diffs produced by compose-agentsmd as intentional updates: do not discard/revert them unless the requester explicitly asks to drop them.
- When the repository is git-managed, stage those intentional AGENTS.md updates normally (git add) unless the requester explicitly says to exclude them.
- Infer core intent; prefer global over project-local. Keep rules MECE, concise, non-redundant, action-oriented ("do X", "never Z"). No hedging or numeric filename prefixes.
- Placement: based on where needed. Any-workspace → global; domain only for opt-in repos.

## Size budget

- Total ≤350 lines; per-module ≤30 (soft). Overage → extract procedural content to skills.
- **Rules** = invariants (always loaded, concise). **Skills** = procedures (on-demand, detailed).

Source: github:metyatech/agent-rules@HEAD/rules/global/autonomous-operations.md

# Autonomous operations

- Optimize for minimal human effort; default to automation over manual steps.
- Drive work from the desired outcome: choose the highest-quality safe path and execute end-to-end.
- Correctness, safety, robustness, verifiability > speed unless requester explicitly approves the tradeoff.
- Default to long-term maintainability over short-term optimization.
- End-to-end repo autonomy (issues, PRs, pushes, merges, releases, admin) within user-controlled repos; third-party repos require explicit request.
- No backward compatibility unless requested; no legacy aliases, shims, or temporary fallback behavior.
- Proactively fix rule gaps, redundancy, or misplacement; regenerate AGENTS.md without waiting.
- Self-evaluate continuously; fix rule/skill gaps immediately on discovery. In delegated mode, include improvement suggestions in the task result.
- On user-reported failures: treat as systemic — fix, update rules, check for same pattern elsewhere, in one action.
- Session memory resets; use rule files as persistent memory. Never write to platform-specific local memory files; all persistent behavioral knowledge MUST live in agent rules.
- Rules are source of truth; update conflicting repos to comply or encode the exception.
- Investigate unclear items before proceeding; no assumptions without approval. Make scope/risk/cost/irreversibility decisions explicit.

Source: github:metyatech/agent-rules@HEAD/rules/global/command-execution.md

# Workflow and command execution

## MCP server setup verification

- After adding or modifying an MCP server configuration, immediately verify connectivity using the platform's MCP health check and confirm the server is connected.
- If a configured MCP server fails to connect, diagnose and fix before proceeding. Do not silently fall back to alternative tools without reporting the degradation.
- At session start, if expected MCP tools are absent from the available tool set, verify MCP server health and report/fix connection failures before continuing.

- Do not add wrappers or pipes to commands unless the user explicitly asks.
- Prefer repository-standard scripts/commands (package.json scripts, README instructions).
- Reproduce reported command issues by running the same command (or closest equivalent) before proposing fixes.
- Avoid interactive git prompts by using --no-edit or setting GIT_EDITOR=true.
- If elevated privileges are required, use sudo directly; do not launch a separate elevated shell (e.g., Start-Process -Verb RunAs). Fall back to run as Administrator only when sudo is unavailable.
- Keep changes scoped to affected repositories; when shared modules change, update consumers and verify at least one.
- If no branch is specified, work on the current branch; direct commits to main/master are allowed.
- Do not assume agent platform capabilities beyond what is available; fail explicitly when unavailable.
- When building a CLI, follow standard conventions: --help/-h, --version/-V, stdin/stdout piping, --json output, --dry-run for mutations, deterministic exit codes, and JSON Schema config validation.

## Codex-only: Commands blocked by policy (PowerShell)

- `Remove-Item` (aliases: `rm`, `ri`, `del`, `erase`) → Use: `if ([IO.File]::Exists($p)) { [IO.File]::SetAttributes($p,[IO.FileAttributes]::Normal); [IO.File]::Delete($p) }`
- `Remove-Item -Recurse` (aliases: `rmdir`, `rd`) → Use: `if ([IO.Directory]::Exists($d)) { [IO.File]::SetAttributes($d,[IO.FileAttributes]::Normal); foreach ($e in [IO.Directory]::EnumerateFileSystemEntries($d,'*',[IO.SearchOption]::AllDirectories)) { [IO.File]::SetAttributes($e,[IO.FileAttributes]::Normal) }; [IO.Directory]::Delete($d,$true) }`

## PowerShell command chaining

- `&&` is not supported in PowerShell (it is a bash/Unix operator); use `;` to chain commands in PowerShell scripts and prompts.
- When writing PowerShell, always use `;` for sequential command chaining; never use `&&` or `||` as control-flow operators.

## Post-change deployment

After modifying code, check whether deployment steps beyond commit/push are needed before concluding.

- If the repo is globally linked (`npm ls -g` shows `->` to local path), rebuild and verify the global binary is functional.
- If the repo powers a running service, daemon, or scheduled task, rebuild, restart, and verify with deterministic evidence.
- Do not claim completion until the running instance reflects the changes.

Detection and verification procedures are in the `post-deploy` skill.

Source: github:metyatech/agent-rules@HEAD/rules/global/implementation-and-coding-standards.md

# Engineering and implementation standards

- Prefer official/standard framework/tooling approaches.
- Prefer well-maintained dependencies; build in-house only when no suitable option exists.
- Prefer third-party tools/services over custom implementations; prefer OSS/free-tier when feasible and call out tradeoffs.
- PowerShell: `\` is literal (not escape); avoid shadowing auto variables (`$args`, `$PID`); avoid double-quoted `-Command` strings (prefer `-File`, single quotes, or here-strings).
- If functionality is reusable, assess reuse first and propose shared module/repo; prefer remote dependencies (never local paths).
- Maintainability > testability > extensibility > readability.
- Single responsibility; composition over inheritance; clean dependency direction; no global mutable state.
- Avoid deep nesting; guard clauses and small functions; clear intention-revealing names; no "Utils" dumping grounds.
- Prefer config/constants over hardcoding; consolidate change points.
- For GUI: prioritize ergonomics/discoverability, include in-app guidance for all components, prefer established design systems (Material, shadcn/ui, Fluent).
- Keep DRY across code/specs/docs/tests/config/scripts; refactor repeated procedures into shared config with local overrides.
- Fix root causes; remove obsolete/unused code/branches/comments; repair user-controlled tools at source, not via workarounds.
- Ensure failure/cancellation paths tear down allocated resources; no partial state.
- Do not block inside async APIs; avoid synchronous I/O where responsiveness is expected.
- Avoid external command execution; prefer native SDKs. If unavoidable: absolute paths, safe argument handling, strict validation.
- Prefer stable public APIs; isolate/document unavoidable internal API usage.
- Externalize large embedded strings/templates/rules.
- Do not commit build artifacts (respect `.gitignore`); keep file/folder naming aligned and consistent.
- Do not assume machine-specific environments; use repo-relative paths and explicit configuration.
- Agent temp files MUST stay under OS temp unless requester approves.
- For agent-facing tools/services, design for cross-agent compatibility via standard interfaces (CLI, HTTP, stdin/stdout, MCP).
- Lifecycle install hooks (`prepare`/`preinstall`/`postinstall`) must succeed on a clean machine with no global tool assumptions; invoke required CLIs through project-local dependencies or package-manager executors (for npm, prefer `npm exec`).
- After manifest changes, regenerate and commit corresponding lock files in the same commit.

Source: github:metyatech/agent-rules@HEAD/rules/global/linting-formatting-and-static-analysis.md

# Linters, formatters, and static analysis

- Every code repo must have a formatter and a linter/static analyzer for its primary languages.
- Prefer one formatter and one linter per language; avoid overlapping tools.
- Enforce in CI: run formatting checks (verify-no-changes) and linting on pull requests and require them for merges.
- Treat warnings as errors in CI.
- Do not disable rules globally; keep suppressions narrow, justified, and time-bounded.
- Pin tool versions (lockfiles/manifests) for reproducible CI.
- For web UI projects, enforce automated visual accessibility checks in CI.
- Require dependency vulnerability scanning, secret scanning, and CodeQL for supported languages.

Source: github:metyatech/agent-rules@HEAD/rules/global/model-inventory.md

# Model inventory and routing

- Classify tasks into tiers: Free (trivial, Copilot 0x only), Light, Standard, Heavy, Large Context (>200k tokens, prefer Gemini 1M context).
- Before spawning sub-agents, run `ai-quota` to check availability.
- Always explicitly specify `model` and `effort` from the model inventory when spawning agents; never rely on defaults.
- The full model inventory with agent tables, routing principles, and quota fallback logic is maintained in the `manager` skill.
- **Orchestrator model**: When spawning an orchestrator (manager/autonomous-orchestrator role), default to `claude-sonnet-4-6` with `medium` effort; use `claude-opus-4-6` with `medium` effort when strict rule compliance is required. Research shows higher effort degrades instruction-following on multi-constraint rule sets (arXiv:2505.11423). Use `high`/`max` effort only for complex reasoning tasks, not for rule compliance.
- **Gemini sub-agent reliability**: Do NOT use Gemini (`gemini` agent type) for sub-agent delegation. Even single Gemini agents hit 429 "No capacity available" server errors frequently, making them unreliable for unattended tasks. Use Claude or Copilot instead. Gemini CLI may be used interactively by the user but not as a spawned sub-agent.

Source: github:metyatech/agent-rules@HEAD/rules/global/multi-agent-delegation.md

# Multi-agent delegation

- Every agent runs in direct mode (human requester) or delegated mode (spawned by another agent); default to direct mode.
- In delegated mode, delegation is plan approval: do not re-request human approval, respond in English, emit no notification sounds, and report AC/verification concisely. If scope must expand, fail back to the delegator.
- Delegation prompts MUST state delegated mode and approval state, include AC/verification requirements, and focus on task context (agents read repo AGENTS.md automatically).
- If a delegated agent reports read-only/no-write constraints, it MUST run a minimal reversible OS-temp probe and report the exact failure verbatim.
- Restricted operations require explicit delegation: modifying rules/rulesets, merging/closing PRs, creating/deleting repos, releasing/deploying, and force-pushing/rewriting published history.
- Delegated agents must not modify rules directly; submit rule-gap suggestions in results for delegator review.
- Delegated agents inherit delegator repository scope but must not expand it; fail explicitly if unavailable.
- Do not run concurrent agents that modify the same repository/files; different repositories may run in parallel. When conflict risk is unclear, run sequentially.

Execution discipline, agents-mcp dispatch configuration, and cost optimization details are in the `manager` skill.

Source: github:metyatech/agent-rules@HEAD/rules/global/planning-and-approval-gate.md

# Planning and approval gate

- **Always OK** (no approval needed): read-only inspection, spawning read-only agents, temp files under OS temp, dependency install, formatters/linters/typecheck/tests/builds (including auto-fix), deterministic codegen/build steps.
- **Always ask** (approval required): file/rule/config edits, dependency/tool changes, git beyond status/diff/log, external side effects (deploy/publish/API writes/account changes).
- **Uncertain impact**: request approval.
- **Default flow**: clarify goal + plan → restate as AC → confirm with requester → wait for explicit "yes" → execute. Re-request only if plan/scope changes.
- **Delegated mode**: delegation itself is plan approval; fail back on scope expansion.
- Do not treat the original task request as plan approval.
- If state-changing work starts without required "yes", stop immediately, report the gate miss, and restart from the approval gate.
- No bypass exceptions: "skip planning/just do it" means move quickly through the gate, not around it.
- **Blanket approval**: broad directives (e.g., "fix everything") cover all in-scope follow-up; re-request only for out-of-scope expansion.

Reviewer proxy approval procedures are in the `autonomous-orchestrator` skill.

Source: github:metyatech/agent-rules@HEAD/rules/global/quality-and-delivery.md

# Quality and delivery gates

Non-negotiable gates for any state-changing work or any claim of "done", "fixed", "working", or "passing".

1. **BEFORE** state-changing work: list AC as binary, testable statements (aim 1-3 items). Ask blocking questions if ambiguous.
2. **BEFORE** each `git commit`: repo's full verification suite must pass in the current working tree.
3. **WITH** each AC: define verification evidence (automated test preferred; deterministic manual procedure otherwise).
4. **FOR** code/runtime changes: automated tests required (requester may explicitly approve skipping). Bugfixes MUST include a regression test.
5. **ALWAYS**: run repo-standard `verify` command; if missing, add it. Enforce via commit-time hooks and CI.
6. **IN** final response: AC→evidence mapping with outcomes (PASS/FAIL/NOT RUN/N/A) and exact verification commands executed.

## Quality principles

- Quality (correctness, safety, robustness, verifiability) > speed/convenience.
- If full-suite scope is unclear, run repo-default verify/CI commands rather than guessing.
- CI must run the full suite on PRs and default-branch pushes; require passing checks for merges; add CI if missing.
- Commit-time hooks must run full verify and block commits; confirm hooks installed before first commit in a session.
- Never disable checks, weaken assertions/types, or add retries solely to make checks pass.
- Test-first: add/update tests, observe failure, implement fix, observe pass.
- Never swallow errors; fail fast with explicit errors reflecting actual state and input context.
- Validate config/external inputs at boundaries with actionable failure guidance.

Detailed evidence format, CI setup, test practices, and error handling procedures are in the `quality-workflow` skill.

Source: github:metyatech/agent-rules@HEAD/rules/global/release-and-publication.md

# Release and publication

- Include LICENSE in published artifacts (copyright holder: metyatech).
- Do not ship build/test artifacts or local configs; ensure a clean environment can use the product via README steps.
- Define a SemVer policy and document what counts as a breaking change.
- Keep package version and Git tag consistent.
- Run dependency security checks before release.
- Verify published packages resolve and run correctly before reporting done.
- For public repos, set GitHub Description, Topics, and Homepage. Assign topics from the standard set defined in the `release-publish` skill.
- Before reporting a publishable-package change as complete, verify the full delivery chain (commit → push → version bump → release → publish → install verify). Procedures in the `release-publish` skill.

Source: github:metyatech/agent-rules@HEAD/rules/global/skill-authoring.md

# Skill authoring standards

- Follow the Agent Skills open standard (agentskills.io/specification).
- SKILL.md frontmatter: only `name` (lowercase alphanumeric + hyphens, max 64 chars) and `description` (explain trigger conditions).
- SKILL.md body must be platform-agnostic: no platform-specific tool names. Write in terms of intent.
- Platform-specific examples belong in README.md, not SKILL.md.
- Each skill lives in its own repository with SKILL.md at root.
- Install/manage via `npx skills add <owner>/<repo> --yes --global`.
- Default to public repositories; include a LICENSE file (prefer MIT).
- Write SKILL.md and README.md in English; keep instructions concise and action-oriented.
- Do not duplicate rules already covered by AGENTS.md global rules; reference them instead.

Source: github:metyatech/agent-rules@HEAD/rules/global/task-lifecycle-tracking.md

# Task lifecycle tracking

- When an actionable task emerges during a session, immediately record it with `task-tracker add` so it persists on disk regardless of session termination.
- `task-tracker` is the persistent cross-session tracker; session-scoped task tools (e.g., TaskCreate) are supplementary. Always use `task-tracker add` first; session-scoped tools may be used in addition but never as a replacement.
- At the start of any session that may involve state-changing work, run `task-tracker check` and report findings before starting new work.
- When reporting a task as complete, state the lifecycle stage explicitly (committed/pushed/released/etc.); never claim "done" when downstream stages remain incomplete.
- If `task-tracker` is not installed, install it via `npm install -g @metyatech/task-tracker` before proceeding.
- CLI: `task-tracker add "desc"` / `check` / `list` / `done <id>` / `remove <id>` / `update <id> --stage <stage>` — use `--stage`, NOT `--status`.
- Valid stages: `pending`, `in-progress`, `committed`, `pushed`, `released`, `done`.
- The task-tracker state file (`.tasks.jsonl`) must be committed to version control; do not add it to `.gitignore`.

Source: github:metyatech/agent-rules@HEAD/rules/global/thread-inbox.md

# Thread inbox

- `thread-inbox` is the persistent cross-session conversation context tracker. Use it to preserve discussion topics, decisions, and context that span sessions.
- If `thread-inbox` is not installed, install it via `npm install -g @metyatech/thread-inbox` before proceeding.
- Store `.threads.jsonl` in the workspace root directory (use `--dir <workspace-root>`). Do not commit it to version control.
- At session start, run `thread-inbox inbox` and `thread-inbox list --status waiting` to find threads needing attention; report findings before starting new work.
- Do not create threads for tasks already tracked by `task-tracker`; threads are for context and decisions, not work items.
- CLI: `thread-inbox new "title" --dir <dir>` (must create before adding messages) / `add <id> --from user|ai "msg" --dir <dir>` / `inbox --dir <dir>` / `list --status <status> --dir <dir>`.
- If a thread captures a persistent behavioral preference, encode it as a rule and resolve the thread.
- Detailed usage procedures (status model, when to create/add messages, lifecycle) are in the `manager` skill.

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
- After completing a response, emit the Windows SystemSounds.Asterisk sound via PowerShell only when operating in direct mode (top-level agent).
- If operating in delegated mode (spawned by another agent / sub-agent), do not emit notification sounds.
- If operating as a manager/orchestrator, do not ask delegated sub-agents to emit sounds; emit at most once when the overall task is complete (direct mode only).
- When delivering a new tool, feature, or artifact to the user, explain what it is, how to use it (with example commands), and what its key capabilities are. Do not report only completion status; always include a usage guide in the same response.

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
