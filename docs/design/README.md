# Design docs: rules

Rules for writing and editing everything under `docs/design/` and `docs/design/planned/`. Readers looking for a topic start at [INDEX.md](INDEX.md), not here.

## What a design doc is

A design doc describes the **current design of one built feature or subsystem**, in present tense, so a reader learns what it does and why it is shaped that way. It is not a decision log, a changelog, a plan or a prototype brief.

Where each kind of fact lives:

| Kind of fact | Home |
|---|---|
| Definitions and vocabulary | `CONTEXT.md` |
| Decisions and rejected alternatives | `docs/adr/` |
| Firestore data shape (paths, fields) | `docs/design/firestore-schema.md` |
| Feature mechanics and behavior | `docs/design/*` |
| Per-screen action flows (taps, decisions, entry points) | `docs/user-flow/` |
| Structural map of the screens and the routing rules | `docs/design/navigation.md` |

Every fact has one home. A design doc that needs a fact owned elsewhere links to it instead of restating it.

## Where a doc goes

- `docs/design/` holds **built** features only.
- `docs/design/planned/` holds designs for features that are not built.
- A partly built feature gets two docs with the same name: the built part in `docs/design/`, the unbuilt remainder in `planned/`. Never mix built and unbuilt behavior in one file.
- When a planned feature ships, rewrite its doc as a current-state doc in `docs/design/`, delete the `planned/` file, and update [INDEX.md](INDEX.md) and inbound links.

## Skeleton

1. Title.
2. One-paragraph purpose.
3. Body sections, free-form.
4. Closing `Related` list of links.

There is no status line. Where the file lives (`design/` or `planned/`) is the status. A planned doc may say in its purpose paragraph which parts are not built, but only to separate them from built behavior or to point to the built half. A bare "not built" sentence is redundant with the folder.

## Banned content

- History of any kind: creation or revision dates, "as built", "implementation history", "formerly", "moved up from", "decided against", correction banners that supersede text lower in the doc.
- Planning scaffolding: "Problem", "Design brief", "Open items", "Next step", "Doc reconciliation", follow-up lists, prototype axes. Open design questions are allowed only in `planned/`, where no code exists to be the truth.
- Code and code identifiers: Kotlin or TypeScript blocks, class, function, DTO and mapper names, source file paths, line numbers, library call sequences.
- Tunable values: XP amounts, defaults, level-curve constants, limits that live in configuration or settings. Name the rule or the source of truth instead ("XP amounts come from the XP configuration").
- UI literals: dp values, colours, literal copy that lives in `strings.xml`. Describe layout structure and behavior. Naming a control, tab or screen by its label so a reader can find it is allowed; quoting message, hint or error wording is not.
- Ephemeral planning references (spec or ticket numbers, `docs/temp` paths). See AGENTS.md.

## Allowed content

- Firestore collection, document and field names, in `firestore-schema.md` only.
- Module names where they explain a layering constraint or serve as a "read when" hint.
- Domain vocabulary exactly as `CONTEXT.md` defines it.
- Product invariants that are not tunable (for example the 10% Mastery Defense floor, difficulty 1 to 10).
- Short "why" lines where the shape would otherwise look arbitrary, with a link to the ADR that records the decision.
- Conceptual diagrams (state machines, flows). Prefer Mermaid. No call graphs.

## Granularity

One doc per feature or subsystem a reader would look up by name. A doc over about 250 lines, or covering two independent features, is a candidate for a split. A doc under about 40 lines with one dependent feature is a candidate for a merge. Splits and merges are agreed with the maintainers before they happen.

## Truth order

Code and tests win over ADRs, ADRs win over docs. If code contradicts an ADR, `CONTEXT.md` or the evident intent of the doc, it may be a bug: raise it with the maintainers instead of writing it down.

## Index

[INDEX.md](INDEX.md) has one entry per doc, in two sections, Built and Planned. Each section is alphabetical by file name, and every entry ends with a "Read when" line.
