<!-- markdownlint-disable MD025 -->
# Tool Rules (compose-agentsmd)

- **Session gate**: before starting substantive work for each externally supplied human/operator instruction, run `compose-agentsmd` once from the project root. AGENTS.md contains the rules you operate under; stale rules cause rule violations. Do not rerun this gate within the same instruction after tool results, retries, generated continuations, or resumed execution. If you discover you skipped this step mid-session, stop, run it immediately, re-read the diff, and adjust your behavior before continuing.
- `compose-agentsmd` intentionally regenerates `AGENTS.md`; any resulting `AGENTS.md` diff is expected and must not be treated as an unexpected external change.
- If `compose-agentsmd` is not available, run it via `npx compose-agentsmd`. If `npx` is unavailable or cannot fetch the package, install it via npm with an environment-appropriate method such as `npm install -g compose-agentsmd` when global installs are permitted, or a user-local npm prefix when global installs are not permitted.
- To update shared/global rules, use `compose-agentsmd edit-rules` to locate the writable rules workspace, make changes only in that workspace, then run `compose-agentsmd apply-rules` (do not manually clone or edit the rules source repo outside this workflow).
- If you find an existing clone of the rules source repo elsewhere, do not assume it is the correct rules workspace; always treat `compose-agentsmd edit-rules` output as the source of truth.
- `compose-agentsmd apply-rules` pushes each GitHub source workspace when its workspace is clean, then regenerates instruction files with refreshed rules.
- Do not edit `AGENTS.md` directly; update the source rules and regenerate.
- `tools/tool-rules.md` is the shared rule source for all repositories that use compose-agentsmd.
- Before applying any rule updates, present the planned changes first with an ANSI-colored diff-style preview, ask for explicit approval, then make the edits.
- These tool rules live in tools/tool-rules.md in the compose-agentsmd repository; do not duplicate them in other rule modules.

Source: github:metyatech/agent-rules@HEAD/rules/domains/node/module-system.md

# Node module system (ESM)

- Default to TypeScript (.ts/.tsx); use JavaScript only for tool-required config
  files.
- Always set "type": "module" in package.json.
- Prefer ESM with .js extensions for JavaScript config/scripts (e.g.,
  next.config.js as ESM).

Source: github:metyatech/agent-rules@HEAD/rules/domains/node/npm-packages.md

# Node package publishing

- For scoped npm packages, set publishConfig.access = "public".
- Set files to constrain the published contents.
- If a clean npm install is insufficient, use prepare (or equivalent) to build.

## Verification

- Use npm pack --dry-run to inspect the package contents.
- Run npm test when tests exist.

Source: github:metyatech/agent-rules@HEAD/rules/domains/agent-tooling/composition.md

# Agent Tooling Composition

- Agent tooling repositories MUST keep generated instruction files reproducible from `agent-ruleset.json` and the selected `profile`.
- A consuming repository's `agent-ruleset.json` MUST declare the complete ordered `sources` list and `profile` needed by that repository.
- Profiles in `agent-profiles.json` MUST select the complete set of `rules/domains/*` domains needed by each repository type.
- Rule source changes MUST be made in `rules/global/`, `rules/domains/`, `agent-profiles.json`, or other canonical source files selected by the rules source.
- Generated `AGENTS.md` and `CLAUDE.md` diffs MUST be reviewed as generated instruction diffs, not hand-edited.
- If a generated instruction file is stale, regenerate it with `compose-agentsmd` or the repository's canonical compose command before reporting completion.
- Consuming repositories MUST NOT use legacy `source`, `domains`, or `extra` keys, and MUST NOT compensate for missing shared rules by adding repo-local extras or `agent-rules-local` files.

Source: github:metyatech/agent-rules@HEAD/rules/domains/markdown-to-qti/project.md

# Markdown-to-QTI Project Rules

## Scope

- This repository MUST implement an npm-installable TypeScript CLI that converts Markdown content into IMS QTI 3.0.
- Prioritize correctness of QTI 3.0 output and a clean internal data model.

## TypeScript / npm conventions

- Use TypeScript with a Node.js ESM npm package and expose the `markdown-to-qti` bin through `package.json`.
- Do not require Java, Gradle, a JDK, or JVM launchers for normal install or runtime use.
- Keep the CLI entrypoint small; put conversion logic into testable modules.
- Favor immutable data, explicit types at boundaries, and clear error types.

## QTI 3.0 output rules

- Output must be valid QTI 3.0 XML: well-formed and schema-aligned.
- Keep identifiers stable and deterministic.
- Avoid random IDs unless explicitly required.
- When behavior is ambiguous, prefer standards-compliant conservative output and document the decision.

## Markdown and HTML presentation conversion

- CommonMark/GFM and normal raw HTML MUST share one structured conversion pipeline: MDAST to HAST, parsed raw HTML merged into that HAST, then XML-safe QTI serialization.
- Raw HTML is first-class authored input, not opaque text. Do not introduce a parallel string-replacement presentation pipeline.
- Ordinary presentation MUST use standard HTML element names where representable by the QTI 3 content model. Reserve `qti-*` names for actual QTI-specific structures and interactions.
- Retired qti-prefixed presentation aliases are FORBIDDEN: `qti-p`, `qti-h1`–`qti-h6`, `qti-div`, `qti-em`, `qti-strong`, `qti-del`, `qti-a`, `qti-blockquote`, `qti-ul`, `qti-ol`, `qti-li`, `qti-pre`, `qti-code`, `qti-table`, `qti-thead`, `qti-tbody`, `qti-tfoot`, `qti-tr`, `qti-th`, `qti-td`, `qti-img`, `qti-br`, and `qti-hr`. Do not restore or support compatibility shims for these retired aliases; `qti-*` remains reserved for actual QTI-specific elements and interactions.
- Preserve representable authored hierarchy, nesting, whitespace, and attributes, including `style`, `class`, `id`, `title`, `aria-*`, and `data-*`, subject to QTI/XML/content-model constraints.
- HTML comments are source-only authoring notes and MUST be omitted from QTI output.
- Markdown fenced code MUST retain literal Markdown-code semantics: HTML-looking source is escaped, not interpreted as raw HTML.
- Authored raw `<pre><code>` MAY contain intentional nested rich HTML. Preserve that structure and preformatted whitespace through QTI conversion.
- Choice `## Options` MUST be a flat Markdown task list. Checked state determines correctness, task-list checkbox syntax is structural metadata and MUST NOT render inside `qti-simple-choice`, rich option content MUST remain structured HTML, and list order determines choice order.
- Cloze processing MUST preserve the surrounding rich presentation tree and MUST NOT flatten rich HTML to Markdown or plain text to locate blanks.
- A scoring rubric MUST serialize as `qti-rubric-block view="scorer"` containing ordinary standard HTML `p` criteria; `qti-p` is FORBIDDEN.
- This trusted internal authoring pipeline MUST NOT sanitize away authored presentation HTML. Reject only content that cannot be represented as valid QTI/XML/content-model output; QTI validity remains authoritative.

## Testing expectations

- Add unit tests for parsing and mapping rules.
- Add golden tests for QTI XML output using normalized XML comparison.
- Include end-to-end fixtures for Markdown input to QTI output under a dedicated test folder.
- Golden tests MUST protect the current canonical presentation contract.

## CLI / UX

- Provide a simple CLI with input path support, output directory support, validation mode, and verbose logging.
- Provide `--help` / `-h`, `--version` / `-V`, and `--json` for first-run discoverability and machine-readable use.
- Error messages MUST include source location when possible: file, line, and column.
