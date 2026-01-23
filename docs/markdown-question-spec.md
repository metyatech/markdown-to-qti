# Markdown Question Format (Draft)

This document defines the Markdown format for a single question file. One file
represents one question. Front matter is not used. The target output is IMS QTI
3.0.

## Goals

- Keep authoring simple and readable in plain Markdown.
- Use headings to separate sections.
- Support three question types: descriptive, choice, cloze.

## File Structure

A question file MUST follow this structure:

- `# <title>`: Required. The question title.
- `## Type`: Required. One of:
  - `descriptive`
  - `choice`
  - `cloze`
- `## Prompt`: Required. The question text.
- `## Scoring`: Optional. Scoring rubric criteria.

Headings are case-sensitive and must match exactly as written above. The `Type`
value must be a single word on the line immediately after the `## Type` heading.

## Type: descriptive

The prompt is free-form text. Provide an optional reference answer.

Required sections:

- `## Prompt`

Optional sections:

- `## Answer`: A reference answer or expected points to cover.
- `## Scoring`: A list of scoring criteria and point values.

## Type: choice

Single-correct multiple choice. Exactly one option MUST be marked as correct.

Required sections:

- `## Prompt`
- `## Options`

Options are written as a Markdown task list:

- Correct option: `- [x] Option text`
- Incorrect option: `- [ ] Option text`

Constraints:

- Exactly one `- [x]` MUST appear.
- Options MUST be a single flat list (no nesting).

Optional sections:

- `## Scoring`: A list of scoring criteria and point values.

## Type: cloze

Fill-in-the-blank. Blanks are defined inline within the prompt using a marker.

Required sections:

- `## Prompt`

Blank marker syntax:

- `{{answer}}` creates a blank whose correct answer is `answer`.

Constraints:

- Each `{{...}}` marker defines a blank in order of appearance.
- To include a literal `{{` or `}}`, escape with a backslash (`\{{` or `\}}`).

Optional sections:

- `## Scoring`: A list of scoring criteria and point values.

## Scoring

The `## Scoring` section defines a rubric as a flat Markdown list. Each list
item assigns points to a criterion.

Syntax per list item:

- `<points>: <criterion>`

Examples:

- `2: Identifies chlorophyll as a light-absorbing pigment`
- `1.5: Mentions conversion of light energy to chemical energy`

Constraints:

- `<points>` MUST be a number (integer or decimal).
- The list MUST be a single flat list (no nesting).
- If present, `## Scoring` MUST appear after `## Prompt` and after `## Options`
  (when the question type is `choice`).

## Common Rules

- One question per file.
- No front matter.
- UTF-8 encoded text.
- Use a single top-level `#` heading.
- Do not repeat section headings.

## Examples

### Descriptive

```markdown
# Photosynthesis Basics

## Type
descriptive

## Prompt
Explain the role of chlorophyll in photosynthesis.

## Scoring
- 2: Identifies chlorophyll as a light-absorbing pigment
- 1: Mentions conversion of light energy to chemical energy

## Answer
Chlorophyll absorbs light energy and helps convert it into chemical energy.
```

### Choice

```markdown
# Prime Number

## Type
choice

## Prompt
Which number is prime?

## Options
- [ ] 9
- [x] 11
- [ ] 21
```

### Cloze

```markdown
# Water Formula

## Type
cloze

## Prompt
The chemical formula for water is {{H2O}}.
```
