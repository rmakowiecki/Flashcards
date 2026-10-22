# Home

The Home tab, which is the first tab of the app. It shows the User's Level card, their Streak and Daily Goal, their Favorites carousel and their Recently studied list, and covers how each loads, renders when empty and fails.

## Layout

Home is one vertically scrolling page. The Level card comes first in every state, including the first-session and error states, and scrolls away with the content instead of staying pinned. The Streak and Daily Goal row follows it, then the Favorites carousel, then the Recently studied list. The first-session and error states are centered in the space the Level card leaves. Each state scrolls instead of clipping when its content is taller than that space, for example at large font sizes.

## Level card

The card shows the User's avatar and name, their Level number, an XP bar and an XP readout. It is not tappable.

- **It follows the live Level and XP**, so it counts Pending Sessions and updates while Home stays open.
- **It is independent of the sections below.** It never waits for them, and they never wait for it.
- **It never shows a false Level.** Until both the Level and the signed-in User have arrived, it shows a skeleton of the same height that shimmers. There is no time limit, so on a fresh install with no connection the skeleton keeps shimmering.
- **A failed read of the User** still gives the figures, without a name.
- **A Level stream that fails or ends before its first value** shows the same skeleton without the shimmer, and the failure is logged. Retry relaunches the card, but Retry is offered only in the error state below. Next to a section with content, the card stays like this until Home is created again.
- **The bar does not animate when the card first appears.** Later changes pour into it the same way as on the Session Summary, including a rise across Levels, while a lower Level snaps into place ([session-summary.md](session-summary.md#animation-sequence)).

## Streak and Daily Goal

Two cards side by side, Streak on the left and Daily Goal on the right, at equal height. They stack into one column when the screen is too narrow for both, decided from width only, never from font size. Neither card is tappable: the Daily Goal is edited in Settings and onboarding only ([settings.md](settings.md)).

- **The Streak card** shows the Streak in days, Best Streak below it, and a flame. The flame dims while the Streak is at risk.
- **The Daily Goal card** shows a ring of today's minutes against the goal, with the minutes and the goal inside it.

Where the figures come from:

- **The Streak, Best Streak and today's minutes** come from the User's scoring state, the same stream as the Level card, so they count Pending Sessions and update while Home stays open, offline too ([session-submission.md](session-submission.md#scoring-state)). One listener feeds both, so the Level card and the row never disagree.
- **The Daily Goal** is a device preference ([settings.md](settings.md)). If reading it fails, the row keeps the last goal it read, or the default Daily Goal when it has none, and the failure is logged. A change in Settings updates the row in place.

Rules about today, read against the device's local date:

- **The Streak reads 0** once the last study day is before yesterday. **It is at risk** when the last study day is yesterday: it is alive, but today does not count yet.
- **Best Streak** is never derived from today. It is kept when the Streak reads 0.
- **Today's minutes read 0** unless the last study day is today. Otherwise they are the day's total seconds, rounded up to whole minutes once, on the total, so two 20-second sessions read 1 minute.
- **The ring is capped at full**, while the text shows the real minutes. A full ring never means the goal was met: the server rounds the minutes down when it awards Daily Goal XP, so a day 40 seconds short of the goal rounds up to it and fills the ring without the XP ([session-submission.md](session-submission.md#daily-goal)). The cards have no "goal met" visual for this reason.
- **Nothing rolls over at midnight.** The figures are read against today only when the scoring state changes, so a Home left open over midnight keeps yesterday's view until the next change, a new Home or a relaunch.
- **Under-counts are accepted.** The device's day total can be lower than the server's, in the cases the server's own day total leaves out ([session-submission.md](session-submission.md#daily-goal)). A delivery that returns a larger total corrects the ring upward. A delivery that changes nothing does not blink the row.

**A User who never studied**, Pending Sessions included, sees no row. Their first-session prompt is unchanged.

The row is animated like the Level card: the ring does not animate when the row first appears, and later changes sweep into it.

## Favorites

The carousel is one snapping row of **Favorites**, most recently favorited first, with Categories and Subcategories mixed. A Favorite whose Category or Subcategory no longer exists is left out.

- **A Category card** shows the Category's name. Its body opens Category Details. Its Quick session button opens Preview as a Quick Session in that Category, and Home sends only the Category ([ADR-0056](../adr/0056-preview-resolves-quick-session-candidate-pool.md)).
- **A Subcategory card** shows the Subcategory and Category names and how much of the Subcategory the User has **Studied**. Its body opens Subcategory Details. Its play button opens Preview for a single-subcategory session.
- **The body browses and the button studies** ([ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md)).
- **Studied progress loads on its own**, so a slow or failed progress read never holds back the cards. Until progress is known, the card keeps the space for it but shows nothing, which is never the same as zero.

## Recently studied

A vertical list below Favorites that scrolls with the page. It holds the User's latest **Recents**, newest first and capped by the Recents projection ([ADR-0057](../adr/0057-recents-state-projection.md)). Each row shows:

- the Category, by its stored name, plus its color and icon when the Category can be read;
- a title: the Subcategory for a single-subcategory session, otherwise the Quick or Custom label with the number of topics;
- the card count, minutes and XP;
- the Study Mode as one icon (a star for Rated, a bolt for Fast), then how long ago the session started, on the Category's line at the top right. A session more than seven days old shows its date in English, as "Oct 6" or, for another year, "Oct 6, 2025".

Rules for the list:

- **A Pending Session shows at once**, with preview XP, until the server records it ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)). The list is the same length offline as online. On delivery the row can blink briefly while the server's copy replaces the device's copy.
- **A stored entry that cannot be read is skipped**, not shown. This covers an entry with a required field missing or malformed, its names included, and an entry whose Subcategory ids and names differ in count.
- **Tapping a row opens Preview to replay the session.** A single-subcategory row replays the same Subcategory, a Quick row samples the Category afresh, and a Custom row sends its stored Subcategories. Preview starts with the past Study Mode and delivery. A Recent is the one row with no play button, because it has nothing to browse ([ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md)). Replay is described in [preview-study-session.md](preview-study-session.md).

## Loading and reveal

The Streak row, Favorites and Recents each load as one of four states:

- **Loading**;
- **Empty**, when the User has none (for the row: the User never studied);
- **Failed**, when the section could not be loaded;
- **Content**.

"Could not be loaded" is never read as "none". Favorites whose items could not all be fetched count as Failed. So does a section whose stream fails or ends before its first result. A failure after a section has loaded keeps what is already shown.

The row needs the scoring state and, for a User who studied, the Daily Goal. A User who never studied gets an Empty row without waiting for the Daily Goal.

The three sections reveal together once all have resolved, except that the first-session prompt shows as soon as Favorites and Recents are both Empty, without waiting for the row. A short ceiling stops one hung read from leaving Home blank. A section still loading when the ceiling runs out is left out, and it joins in place when it arrives.

## Empty states

- **First session** (Favorites and Recents both Empty): a prompt to start a first session, whose button switches to the Study tab.
- **Favorites hint** (no Favorites, some Recents): the Favorites heading with a Hide button on its line, then a banner explaining that bookmarking a Category or Topic pins it here, above the Recents.
  - Hiding is permanent on this device. It is a local preference and is never synced.
  - Hiding applies only to this hint. Once a Favorite exists the carousel shows as usual, and removing every Favorite does not bring the hint back.
  - If saving the preference fails, the hint returns on a later launch.
  - Home waits for the preference only when the hint could show, so a hidden hint never flashes.
- **Recents placeholder** (some Favorites, no Recents): the carousel, then the Recently studied heading with a line saying where sessions will appear.

A section that Failed shows neither the hint nor the placeholder.

## Failure and Retry

- **No section has content and at least one Failed:** an error with Retry, centered below the Level card. The Streak row counts as a section here. Retry reloads only the failed sections, restarts the reveal ceiling, and relaunches the Level card when it has no content. The Level card and the row share one stream, so retrying either restarts both. A section that arrives later with content replaces the error.
- **A section fails next to one with content:** the failed section is hidden and the failure is logged. No banner is shown.

## Related

- [ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md): the row or body browses and the play button studies, with Recents as the exception.
- [ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md): Pending Sessions and the local progress projection.
- [ADR-0057](../adr/0057-recents-state-projection.md): the Recents projection.
- [ADR-0056](../adr/0056-preview-resolves-quick-session-candidate-pool.md): Preview samples a Quick Session from the Category alone.
- [session-summary.md](session-summary.md): the shared Level card and its pour.
- [xp-leveling-system.md](xp-leveling-system.md): XP and Level.
- [preview-study-session.md](preview-study-session.md): what Favorites and Recents send to Preview.
- [category-icon-color.md](category-icon-color.md): Category color and icon on cards and rows.
- [firestore-schema.md](firestore-schema.md): where Favorites and Recents are stored.
- [navigation.md](navigation.md): the tab shell and the empty-state tab switch.
