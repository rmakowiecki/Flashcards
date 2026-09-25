---
name: compose-stability
description: |
  Regenerate the Compose compiler stability reports (every module, or selected modules) and analyze them for unstable parameters and non-skippable composables, without reading the raw report files. Use when the user invokes /compose-stability, or asks to "check Compose stability", "regenerate compose reports", "find unstable composables", "which composables can't skip", or similar.
---

# compose-stability

Thin wrapper: compiling and report parsing live in `scripts/compose-stability.py`.
This skill runs it, reads its compact output, and turns the findings into a
triaged list with proposed fixes. **Never read the raw
`build/compose_compiler/*` files** — the script's output is the whole input.
**Never edit code** from this skill; propose fixes and let the user decide.

## Arguments

Passed straight through to the script:

- none — every module;
- Gradle paths, e.g. `:feature:browse :core:ui` — only those modules; a
  module without Compose prints `skipped (no Compose)` and isn't compiled;
- `--uncertain` — also list params with no stability prefix (combines with either).

## Flow

1. Run the script:
   - **All modules**: in the background, since a full `--rerun` compile takes
     minutes:
     `Bash(command: "python3 scripts/compose-stability.py [--uncertain]", run_in_background: true)`.
     Tell the user it's running and wait for the completion notification —
     don't poll.
   - **Selected modules**: in the foreground with `timeout: 600000`:
     `python3 scripts/compose-stability.py :feature:browse [--uncertain]`.

2. Read the output. Exit code `2` means the build failed: relay the printed
   compiler errors and stop. Exit code `0` means the reports parsed; the output
   has one summary line per module, then its findings:

   ```
   :feature:browse  skippable 87/99  unstable args 24  findings 3
     :feature:browse  BrowseScreen.kt:74  BrowseScreen(viewModel: BrowseViewModel?)  unstable  <- unstable val ...
   ```

   - `unstable` — the compiler proved the param unstable; under strong
     skipping it's compared by instance (`===`), so an equal-but-new value
     recomposes. The `<-` tail lists the type's unstable fields (from any
     module's `*-classes.txt`).
   - `not skippable` — a restartable composable that can never skip.
   - The summary's `skippable X/Y` counts composable lambdas too, so its gap
     is not the number of `not skippable` findings — don't report it as such.
   - `uncertain` — no stability prefix (typically a sealed interface or a
     class holding one); still skippable, compared with `equals()`.

3. Report, grouped by severity:
   - **Fix** — `not skippable` composables, and `unstable` params on
     composables that recompose often (list rows, bars, anything under a
     frequently changing state).
   - **Consider** — other `unstable` params; `uncertain` ones only if the
     user asked for them.
   - **Expected** — one line naming the count of expected findings below,
     no further discussion.

   For each non-expected finding give the location and one proposed fix:
   make the type immutable (`val` fields, read-only or
   `kotlinx.collections.immutable` collections); narrow the param to the
   fields actually rendered; or, for an immutable type declared in a
   non-Compose module, add it to `config/compose/stability-config.conf`.

## Expected findings

- `viewModel` on `*Screen` entry points — ViewModels are mutable by nature;
  the same instance comes back every recomposition, so instance comparison
  skips correctly.
- `DebugContent(showcaseIntent: Intent?)` — debug-only screen, not worth a
  wrapper type.
- Params typed as a `FlashcardsListGroupItem` variant — the sealed list model
  is unstable by design (`key: Any?`, lambdas) and documented as
  short-static-lists-only; long or changing lists use the builder overloads.
