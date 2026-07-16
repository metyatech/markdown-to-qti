# Markdown to QTI Mapping

The canonical authoring rules are defined in
[markdown-question-spec.md](markdown-question-spec.md). This document records
how that format maps to IMS QTI 3.0 XML.

## Package Output

`--manifest <path>` is the canonical package conversion mode. The CLI writes:

- One `<identifier>.qti.xml` assessment item per manifest item.
- One `assessment-test.qti.xml` referencing those items in manifest order.
- Local image files copied into the output directory.

The resolved time limit is:

- `manifest.time_limit_seconds`, when present.
- Otherwise the sum of each item `time_budget_seconds`, when every item
  specifies one.
- Otherwise absent when every item omits `time_budget_seconds`.

A manifest without `time_limit_seconds` cannot mix items with and without
`time_budget_seconds`; conversion fails in that case. An explicit manifest time
limit takes precedence over all item time budgets.

When resolved, the value is emitted as a `qti-time-limits` child of
`qti-test-part`. Authoring values are positive integer seconds, and the
canonical `max-time` representation is the integer number of seconds:

```xml
<qti-test-part identifier="part-1" navigation-mode="linear" submission-mode="individual">
  <qti-time-limits max-time="1200"/>
  ...
</qti-test-part>
```

Items remain `time-dependent="false"`. Item-level `time-dependent` is not an
individual question time limit; it is for time-dependent scoring behavior and
is independent from the test-part time limit.

## Assessment Items

Each question file maps to a `qti-assessment-item` with:

- `identifier`: the manifest item `id`. In `--input` compatibility mode it is
  the input filename without the final extension.
- `title`: the `# <title>` heading.
- `adaptive="false"`.
- `time-dependent="false"`.

The prompt is rendered in `qti-item-body`.

## Markdown Elements

- Paragraphs -> `qti-p`
- Headings (`###` to `######`) -> `qti-h3` to `qti-h6`
- Emphasis -> `qti-em`
- Strong -> `qti-strong`
- Strikethrough -> `qti-del`
- Links -> `qti-a`
- Inline code -> `qti-code`
- Code blocks -> `qti-pre` with `qti-code`
- Blockquotes -> `qti-blockquote`
- Bullet and ordered lists -> `qti-ul` / `qti-ol` with `qti-li`
- Tables -> `qti-table`
- Images -> `qti-img`
- Horizontal rules -> `qti-hr`

HTML comments are treated as source-only authoring notes and omitted from QTI.
Other raw HTML is rejected.

## Question Types

### descriptive

- Response declaration: `RESPONSE`, `cardinality="single"`,
  `base-type="string"`.
- Interaction: `qti-extended-text-interaction`.

### choice

- Response declaration: `RESPONSE`, `cardinality="single"`,
  `base-type="identifier"`.
- The checked task-list item becomes the single `qti-correct-response` value.
- Options become `qti-simple-choice` children of `qti-choice-interaction`.

### cloze

- Each blank becomes a `qti-text-entry-interaction`.
- One blank uses response identifier `RESPONSE`; multiple blanks use
  `RESPONSE_1`, `RESPONSE_2`, and so on.
- `{{answer}}` emits a normal string correct response.
- `{{/regex/}}` emits the regex pattern as the correct response and sets
  `interpretation="regex"` on the response declaration. This preserves the
  exact/regex distinction for downstream adapters.

## Scoring And Explanation

`## Scoring` lists criterion text only. The point value for each criterion comes
from the manifest item `points` array, matched positionally to the criteria. A
question with a `## Scoring` section must have a manifest item `points` array of
equal length; a question without one must not have `points`.

Given criteria plus manifest points, `## Scoring` maps to:

- `qti-outcome-declaration identifier="SCORE"`, `cardinality="single"`,
  `base-type="float"`.
- `qti-outcome-declaration identifier="MAXSCORE"`, `cardinality="single"`,
  `base-type="float"`, with a default `qti-value` equal to the sum of the
  manifest point values.
- `qti-rubric-block view="scorer"` with one `qti-p` per criterion, formatted as
  `[<points>] <criterion>`, where `<points>` is the manifest point value.

Scoring response-processing is deferred and is not emitted. Generated items do
not set `SCORE` from `## Scoring` yet.

`## Explanation` maps to post-response feedback:

- `qti-outcome-declaration identifier="FEEDBACK"`
- `qti-response-processing` setting `FEEDBACK` to `EXPLANATION`
- `qti-modal-feedback` containing the rendered explanation
