# Morpheus is an external library, built from a git submodule

> Status: accepted

## Context

The animated graph background began as a prototype inside `:debug-tools`. It is meant to outlive this
app: the hosted web pages and marketing material will draw it too, on Android, desktop JVM and the
browser. Kept as modules in this repository, it would carry Flashcards' build setup with it and its
history would be mixed with the app's.

## Decision

The library lives in its own public repository, [Morpheus](https://github.com/rmakowiecki/Morpheus),
with its own build, CI, convention plugins and design ADR. Flashcards consumes it from source:

- the repository is checked out as the `morpheus` git submodule, pinned to a release tag (`vX.Y.Z`).
  Morpheus's `main` holds released commits only and `develop` holds unreleased work, so the pin never
  points at `develop`, and `.gitmodules` tracks `main`;
- `settings.gradle.kts` includes it as a composite build (`includeBuild("morpheus")`), substituting
  `com.rossomak:morpheus` and `com.rossomak:morpheus-tuner` with the included projects;
- consuming modules depend on those coordinates, never on a project path.

Once Morpheus is published to Maven Central, the `includeBuild` block and the submodule go away and the
same coordinates get a version; no consuming module changes.

Morpheus never imports `com.rossomak.flashcards..`. Branding stays in the consumer:
`:debug-tools`'s `flashcardsPlaygroundStyle` maps `FlashcardsComponentStyle` to playground styles, and
`FlashcardsMorpheusGraphPresets` holds the brand-indigo variant and the onboarding decks.

Both builds stay on the same Kotlin and AGP versions, and on Compose Multiplatform within a patch
release, so the composite build resolves one of each. Konsist reads only Flashcards' own sources; the submodule follows Morpheus's rules.

## Considered options

- **`lib/` modules in this repository.** Rejected: the library would ship with Flashcards' build logic
  and history, and every other consumer would need this repository.
- **A composite build pointing at a sibling checkout (`../Morpheus`).** Rejected: it only works on a
  machine with that checkout, so CI could not build the app.
- **Publishing to Maven Central now.** Deferred: releases are tagged, but 0.x still breaks its API,
  and building from source keeps a change to Morpheus one submodule bump away from the app.

## Working with the submodule

- Clone with `git clone --recurse-submodules`, or run `git submodule update --init` in an existing
  checkout.
- Bump Morpheus by checking out the wanted release tag (`git -C morpheus checkout vX.Y.Z`) and
  committing the new pointer, together with any change the release's API needs in the app.
