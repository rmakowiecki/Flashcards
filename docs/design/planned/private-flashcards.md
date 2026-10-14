# Private Flashcards

Private Flashcards are Flashcards that a User writes for themselves in a Subcategory. The User creates, edits and deletes them from Subcategory Details, and they are studied alongside the Subcategory's global Flashcards. Submitting a Private Flashcard to the global pool is a separate future feature that this doc does not design.

## Managing Private Flashcards

- **Add** is an icon in the Subcategory Details bottom toolbar, between Sort and Start session ([ADR-0022](../../adr/0022-subcategory-details-filter-sort-toolbar.md)). It opens a creation dialog.
- **Edit and Delete** are actions on an expanded Private Flashcard in the list. Delete asks for confirmation first.
- **Every field is editable after creation**, Difficulty included. The immutability rule in [ADR-0010](../../adr/0010-difficulty-field-design.md) protects the admin-curated corpus, not a User's own cards.

### Fields

- **Question** and **Answer**: multiline plain text. Both must be non-blank. A length limit applies, enforced by the client and by the security rule.
- **Difficulty**: a discrete slider from 1 to 10. It is mandatory, so no Private Flashcard exists without one ([ADR-0010](../../adr/0010-difficulty-field-design.md)).
- **Tags**: a multi-select over the Tags already present on the Subcategory's Flashcards. It opens with every Tag unchecked, whatever filters are active on Subcategory Details. The General Tag is not offered, and a card saved with no Tag checked is given General. That is deliberate friction against unclassified cards. There is no private Tag: the Private flag is implicit in where the card is stored ([CONTEXT.md](../../../CONTEXT.md)).

A Private Flashcard is plain text only. It has no code blocks, no spoken version and no Extended Context, so read-aloud falls back to the written text. Tags are stored by name. If a later reseed renames or removes a global Tag, a Private Flashcard keeps the old name, and the Tag chip still appears while a Private Flashcard carries it, because the chip set is derived from the cards present.

There is no cap on how many Private Flashcards a User can hold.

## Storage

One document per card, in a per-User collection grouped by Subcategory. Private Flashcards are written one at a time and never seeded in batch, so they are not packed into shards like global Flashcards ([ADR-0037](../../adr/0037-flashcard-content-sharded-by-byte-budget.md)). Each card carries a status that follows the lifecycle in `CONTEXT.md`. This feature only ever writes the first value, `private`.

When this ships, the shape moves into [firestore-schema.md](../firestore-schema.md) and a security rule is added for it. Account Deletion already removes everything under the User's document, so Private Flashcards need no extra deletion step. The Delete account confirmation should also name them among what is deleted ([account.md](../account.md)).

## In Subcategory Details

- **Order.** Under the Default Sort Order, global Flashcards come first in source order, then Private Flashcards by creation time. Easiest first and Hardest first treat every card alike.
- **Marker.** A Private Flashcard carries a small Private marker in the list. A session shows none.
- **Private only filter.** The Filter dialog gains a "Private only" toggle. It is off by default and is shown only when the Subcategory holds at least one Private Flashcard. It is not a Tag chip, so the rule that at least one Tag must stay selected is unaffected. It combines by AND with the Tags and Difficulty filters. It carries into Preview, which offers it only for a single-subcategory session, as it does for Tags ([flashcard-selection.md](../flashcard-selection.md)).

## In a Study Session

- **Pool.** Private Flashcards are part of the pool of every session whose scope includes their Subcategory, whether single-subcategory, Custom or Quick, unless Private only narrows it. They count toward the session length like any other Flashcard ([mastery-defense.md](mastery-defense.md)).
- **No progress.** A Private Flashcard never gets Card Progress, never becomes **Studied** or **Mastered**, and never earns card-level XP. It is never a Mastery Defense candidate and never carries the shield. It never appears in a Session Result's per-card results.
- **No Curation Request.** The flag icon is hidden for a Private Flashcard, because a Curation Request is a fix directive for the admin-curated corpus.
- **Voice Answering.** A Private Flashcard's question and expected answer are sent to the grading function exactly like a global card's ([voice-answering.md](../voice-answering.md)). The privacy policy has to say so when this ships.

### A session that studies only Private Flashcards

Because a Private Flashcard is never Studied, today's rule that a session with no Studied Flashcards records nothing would give a Private-only session no time XP, no completion, no Streak and no Daily Goal credit. Session-level XP is meant to be unaffected by Private Flashcards ([ADR-0016](../../adr/0016-card-progress-model.md)). So a session is recorded when the User completes any Flashcard, a Private one included. Its per-card results list only the global Flashcards, and the server accepts a session whose list is empty. The wording about "no Studied Flashcards" in `CONTEXT.md`, [study-session.md](../study-session.md) and [session-submission.md](../session-submission.md) changes when this ships.

## Loading

Reading a Subcategory also reads the User's Private Flashcards for it, which adds one read to every Subcategory open, including for Users who have none. The read uses Firestore's offline-persisted default and sits outside the shared cache generation, so a seed bump never touches it ([flashcard-cache.md](../flashcard-cache.md)). A failed Private read fails the load with Retry, the same as a failed global read, because a Private Flashcard silently missing from a session is worse than an error. The persisted cache makes offline failures rare.

## Related

- [browse.md](../browse.md): Subcategory Details, whose toolbar and Filter dialog this extends.
- [flashcard-selection.md](../flashcard-selection.md): the pool, the filters and the draw.
- [mastery-defense.md](mastery-defense.md): Mastery Defense, which excludes Private Flashcards.
- [account.md](../account.md): Account Deletion.
