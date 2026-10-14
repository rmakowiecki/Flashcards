# Mastery Defense

A guaranteed floor of previously mastered Flashcards in every Study Session's draw, so mastery is periodically re-tested rather than assumed, and a shield that marks those Flashcards in a Rated session. Selection of the floor and the shield are not built. The XP side of defending mastery is built and lives in [xp-leveling-system.md](../xp-leveling-system.md).

## What exists today

A mastered Flashcard is never removed from the session pool, so the draw can contain one by chance, with no marker. When a Rated session ends, a previously mastered Flashcard that ends Mastered earns the defense award, one that ends Failed is de-mastered, and one that ends Partial keeps its mastery with neither effect ([xp-leveling-system.md](../xp-leveling-system.md), [ADR-0044](../../adr/0044-three-valued-terminal-state.md)). Nothing guarantees that a session contains any.

## Selection

Selection happens on the Preview Study Session Screen, which owns all card selection ([ADR-0004](../../adr/0004-preview-study-session-screen-owns-card-selection.md)). The floor uses the same logic for Rated and Fast sessions. A Fast session re-presents mastered Flashcards as repetition, and its Card Progress is unchanged by it, since Fast never overwrites an existing record ([ADR-0016](../../adr/0016-card-progress-model.md)). Because the draw does not depend on Study Mode, switching mode on Preview does not redraw.

Rules:
- Mastered Flashcards stay in the pool and are as eligible as any other.
- Only Flashcards in the session's scope, passing the session's Difficulty and Tag filters, are candidates. A mastered Flashcard the filters exclude cannot be swapped in, so the floor shrinks when too few mastered Flashcards pass.
- Only global Flashcards count as mastered. Private Flashcards hold no progress and are never defense candidates.
- The floor is **10% of the session's resolved length** (the configured Length, or the filtered pool size when that is smaller), rounded to the nearest whole card with a half rounding up. Every card in the session counts toward the length, Private Flashcards included. For a session of 1 to 4 cards the floor is 0, and mastered Flashcards can still appear by chance.
- If the natural draw already holds that many mastered Flashcards or more, nothing is added.
- If it holds fewer, mastered Flashcards not yet drawn are chosen uniformly at random and each replaces an unmastered drawn Flashcard, also chosen uniformly at random, taking that Flashcard's place in the draw order. This continues until the floor is met or the candidate mastered Flashcards run out.
- **Every mastered Flashcard in the final selection is a Defense Flashcard**, not only those swapped in. Two identical mastered Flashcards in one session must not behave differently depending on how they were drawn.
- The session is always exactly its resolved length, so the card count and the estimated duration on Preview stay truthful.
- The Sort Order then applies as usual ([flashcard-selection.md](../flashcard-selection.md)).
- Preview does not show how many Defense Flashcards were drawn.

Because mastered Flashcards are never excluded, a User who has mastered most of a Subcategory still gets a full-length session, mostly of re-testing.

Which Flashcards are mastered comes from the User's Card Progress, read for every Subcategory in scope through the same path the session uses at start, so it includes sessions that are finished but not yet delivered to the server ([ADR-0055](../../adr/0055-pending-sessions-and-local-progress-projection.md)). If that read fails, the draw proceeds with a floor of 0 and Preview shows no error, matching a session that starts without prior progress ([study-session.md](../study-session.md)).

A future per-card progress filter (Mastered, Studied, Unseen chips) is the only thing that would remove mastered Flashcards from the pool. When a User filters them out, the floor is necessarily 0, which is a normal outcome and not an error.

## Shield

In a Rated session a Defense Flashcard shows a small shield next to its question. A Fast session shows none, since nothing about mastery is at stake there.

- Whether a Flashcard is a Defense Flashcard in the session comes from the session's own start-of-session read of Card Progress, the same flag that drives its XP, so the shield and the XP can never disagree. Preview's draw may have read older progress.
- The shield stays on the Flashcard for the whole session, including after a Failed attempt re-inserts it into the queue.
- The shield is the only signal: no label, no count and no explanation in the session UI. The XP breakdown on the Session Summary implicitly confirms that defense was in play.
- The shield carries a content description for screen readers. It is never spoken by the read-aloud or Voice Answering engine.
- The final visual treatment is decided at implementation time.

## Related

- [xp-leveling-system.md](../xp-leveling-system.md): the built defense award and de-mastery loss.
- [flashcard-selection.md](../flashcard-selection.md): the draw this floor extends.
- [preview-study-session.md](../preview-study-session.md): the screen that runs the draw.
- [rated-mode.md](../rated-mode.md): the session that shows the shield.
- [ADR-0016](../../adr/0016-card-progress-model.md): Card Progress and the Study Mode rules for it.
