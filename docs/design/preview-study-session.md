# Preview Study Session

The full-screen preview shown before every Study Session. It receives a session's scope from an entry point, draws the session's Flashcards, shows what the session will be, lets the user adjust the session's settings, and starts the session in the chosen Study Mode. It is the only place Study Mode, and Voice Answering, is chosen for a concrete session; onboarding and the Settings tab only set defaults ([settings.md](settings.md)). How the Flashcards are drawn is in [flashcard-selection.md](flashcard-selection.md).

## Entry points

Every way of starting a session opens this screen; none bypasses it ([ADR-0004](../adr/0004-preview-study-session-screen-owns-card-selection.md)). Each entry point sends the Category, a **source type** (single-subcategory, Quick or Custom) and, where it has them, the Subcategories:

- **A Subcategory's play button**, on a Category Details row, a Topic search result on the Study tab, or a Favorite Subcategory card on Home: a single-subcategory session for that Subcategory. The row itself browses and never opens Preview ([ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md)).
- **Start session on Subcategory Details**: a single-subcategory session that also carries the Tags, Difficulty range and Sort Order active on that screen, so the session matches the list the user was looking at ([ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md)). It is the only entry point that sends filters or a Sort Order.
- **Quick Session on Category Details**: a Quick session carrying the Category's whole Subcategory list, which Preview samples from ([ADR-0040](../adr/0040-quick-session-subcategory-sampling.md)).
- **A Favorite Category's Quick session button on Home**: a Quick session carrying only the Category. Preview fetches the Subcategory list itself ([ADR-0056](../adr/0056-preview-resolves-quick-session-candidate-pool.md)).
- **Custom session from Selection Mode on Category Details**: the selected Subcategories, in the list's order rather than the order they were tapped. A selection of one becomes a single-subcategory session.
- **Replay, from a Recent on Home or Study Again on the Session Summary**: the past session's Category, source type, Study Mode and that mode's delivery (Voice Answering for Rated, read-aloud for Fast). A single-subcategory or Custom replay sends its stored Subcategories; a Quick replay sends none, so Preview samples the Category afresh. A replay carries no filters, Length or Sort Order. Study Again also removes the Summary from the back stack, so leaving Preview returns to the tab the session started from.

A single-subcategory or Custom session must arrive with its Subcategories, and Preview uses exactly those. The source type is recorded on the session as given: a Quick session that happens to sample one Subcategory is still Quick.

## Starting values

When the screen opens it reads the user's saved defaults once (Study Mode, Voice Answering, read-aloud, Attempts, Partial re-queueing, Length, Sort Order, voice, Quick topic count range) and only then makes the first draw, so the card count never flashes from one value to another. Values the entry point sent win over the defaults, for this session only: a Sort Order from Subcategory Details, and a replay's Study Mode and delivery. None of them is saved as a default.

The defaults are a snapshot, not watched. A "keep as default" save made on this screen therefore never overwrites the other settings already adjusted here.

## Layout

The top bar holds a close button and a title naming the Category with either the Subcategory, for a single-subcategory session, or the Quick or Custom kind of session. Close leaves the screen and starts nothing.

The **hero** describes the session:

- The Category's glyph in a circle, left out until the Category has resolved ([category-icon-color.md](category-icon-color.md)).
- A sentence giving the card count and naming the Subcategories in scope. A long Custom list is shortened to a count of the rest; a Quick list is capped by its sampling range and is always named in full.
- Scope badges: the estimated duration, the card count and, for a session with more than one Subcategory, the topic count.
- A second row of badges for the Study Mode and its delivery. The delivery badge always names what will happen (voice, auto-play or manual), never "off".

The badges are shortcuts into the settings: the card count opens Length; the topic count opens the topic count range for a Quick session, or just the settings sheet for a Custom one; the mode and delivery badges open their own dialogs. A badge first opens the settings sheet, then its dialog a moment later, so the sheet reads as its own step. The estimated duration is read-only.

Pinned to the bottom are **Start session** and a settings button that opens and closes the settings sheet. A Quick session also gets **Reshuffle**, which samples new Subcategories and redraws ([flashcard-selection.md](flashcard-selection.md)). Reshuffle is offered for Quick only: a single-subcategory or Custom session's Subcategories were chosen by the user, so there is nothing to sample.

## Settings sheet

The adjustable settings live in a bottom sheet with no scrim, hidden when the screen opens and opened from the settings button or a badge. It is dismissed by the settings button, a swipe or back ([ADR-0030](../adr/0030-preview-session-settings-sheet.md), [ADR-0043](../adr/0043-bottom-sheet-on-m3-standard-sheet.md)). A redraw does not close or reopen it. Each row shows its current value and opens a focused dialog:

| Row | Shown |
|---|---|
| Mode: Rated or Fast | always |
| Voice answering | Rated |
| Attempts per card | Rated |
| Partial re-queueing | Rated |
| Read aloud | Fast |
| Voice: speaking voice and speed | Fast with read-aloud on, or Rated with Voice Answering on |
| Length | always |
| Topic count range | Quick |
| Filters: Tags and Difficulty | always; the Tag part only for a session with one Subcategory |
| Sort | always |

The Rated-only and Fast-only rows are not offered outside their mode, rather than offered and ignored ([ADR-0025](../adr/0025-voice-answering-rated-mode-only.md)). Switching to Fast turns Voice Answering off for the session. The Filters row summarizes the Tags (all, or how many) beside the Difficulty range.

Confirming a dialog applies its value to this session; dismissing it discards the edit. Each dialog except Filters carries a **keep as default** checkbox that also saves the value as the user's default, the same value the Settings tab edits. Filters are always session-scoped: a Tag set belongs to one Subcategory and cannot carry to another. Each edit in the Voice dialog plays a sample, and dismissing the dialog stops it.

What a confirm does to the drawn cards is in [flashcard-selection.md](flashcard-selection.md#when-the-draw-runs-again). A new topic count range takes effect at the next Reshuffle or the next time the screen opens, not on confirm, so confirming it never changes the Subcategories on screen ([ADR-0040](../adr/0040-quick-session-subcategory-sampling.md)).

## Hero states

- **Loading**: a spinner while the first draw, a Retry after a failure or a Quick pool fetch runs. A redraw keeps the hero on screen, describing the previous draw, until the new draw lands. The loaded hero fades in as one block ([ADR-0063](../adr/0063-loaded-content-reveals-as-one-block.md)).
- **Load failed**: any read failed, including a Quick pool fetch that failed or came back empty. A connection error with **Retry** replaces the screen's body. For a Quick session whose pool never loaded, Retry fetches the pool again before drawing.
- **Empty draw**: the filters left no Flashcards. An empty state with **Reset filters** replaces the hero, restoring the filters the entry point sent. Start stays visible but disabled, and Reshuffle is hidden. The settings sheet stays reachable.
- **Microphone blocked**: see below. An empty draw wins over it, since there is no session to fix the microphone for.

## Microphone

A Rated session with Voice Answering needs the microphone, and this screen secures it on the Start tap, before the session opens:

1. The first time ever, a privacy notice explains Voice Answering. Confirming it continues the Start; dismissing it does not, but either way it is not shown again.
2. The system microphone request follows. When it is already granted, nothing is shown.
3. Only a granted microphone starts the session. Anything else keeps the user here with Voice Answering still selected.

The microphone status is read again every time the screen resumes, so a change made in system settings is picked up on return. When it is permanently denied, the hero is replaced by an empty state offering **Open settings** and **Switch to manual**. Switch to manual turns Voice Answering off for this session only and leaves the saved default alone. Start stays enabled and asks again: the request either shows the system prompt or confirms the refusal. A refusal that was already permanent shows no prompt, so a snackbar points to system settings instead. The Study Mode and the Voice Answering choice never change on their own.

## Starting the session

Start is enabled only when cards are drawn and no load has failed. It stays enabled during a redraw so a quick draw does not make it blink, but a tap while a draw runs does nothing. A second tap does nothing either, while the microphone request is open or once the session is opening, so coming back from a session never starts another one on its own. The confirmed Study Mode picks the destination: Rated and Fast each open their own session screen, carrying the drawn cards in Preview's order and only the settings that mode uses ([ADR-0045](../adr/0045-separate-fast-and-rated-session-screens.md)). The session's delivery is fixed from then on.

Preview stays on the back stack under the session. A session that ends with nothing Studied, or whose cards fail to load, returns here with the same draw and settings, so Start plays the same cards again unless the user changes something. A session that reaches the Summary removes itself and Preview from the back stack.

## Related

- [flashcard-selection.md](flashcard-selection.md): the Quick sample, filters, the draw, its order, when it reruns and the duration estimate.
- [settings.md](settings.md): the saved defaults this screen starts from and can update.
- [browse.md](browse.md): Category Details, Selection Mode and Subcategory Details, where most sessions start.
- [home.md](home.md): Favorites and Recents.
- [session-summary.md](session-summary.md): Study Again.
- [voice-answering.md](voice-answering.md): what Voice Answering does once the session starts.
- [navigation.md](navigation.md): the back stack around sessions.
