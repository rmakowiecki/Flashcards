# Home

The Home tab, which is the first tab of the app. It shows the User's Level card, their Favorites carousel and their Recently studied list, and covers how each loads, renders when empty and fails.

## Layout

Home is one vertically scrolling page. The Level card comes first in every state, including the first-session and error states, and scrolls away with the content instead of staying pinned. The Favorites carousel follows it, then the Recently studied list. The first-session and error states are centered in the space the Level card leaves. Each state scrolls instead of clipping when its content is taller than that space, for example at large font sizes.

## Level card

The card shows the User's avatar and name, their Level number, an XP bar and an XP readout. It is not tappable.

- **It follows the live Level and XP**, so it counts Pending Sessions and updates while Home stays open.
- **It is independent of the sections below.** It never waits for them, and they never wait for it.
- **It never shows a false Level.** Until both the Level and the signed-in User have arrived, it shows a skeleton of the same height that shimmers. There is no time limit, so on a fresh install with no connection the skeleton keeps shimmering.
- **A failed read of the User** still gives the figures, without a name.
- **A Level stream that fails or ends before its first value** shows the same skeleton without the shimmer, and the failure is logged. Retry relaunches the card, but Retry is offered only in the error state below. Next to a section with content, the card stays like this until Home is created again.
- **The bar does not animate when the card first appears.** Later changes pour into it the same way as on the Session Summary, including a rise across Levels, while a lower Level snaps into place ([session-summary.md](session-summary.md#animation-sequence)).

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
- the Study Mode as one icon (a star for Rated, a bolt for Fast), then how long ago the session started, on the Category's line at the top right. A session more than seven days old shows its date in English, as "6 Oct" or, for another year, "6 Oct 2025".

Rules for the list:

- **A Pending Session shows at once**, with preview XP, until the server records it ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)). The list is the same length offline as online. On delivery the row can blink briefly while the server's copy replaces the device's copy.
- **A stored entry that cannot be read is skipped**, not shown. This covers an entry with a required field missing or malformed, its names included, and an entry whose Subcategory ids and names differ in count.
- **Tapping a row opens Preview to replay the session.** A single-subcategory row replays the same Subcategory, a Quick row samples the Category afresh, and a Custom row sends its stored Subcategories. Preview starts with the past Study Mode and delivery. A Recent is the one row with no play button, because it has nothing to browse ([ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md)). Replay is described in [preview-study-session.md](preview-study-session.md).

## Loading and reveal

Favorites and Recents each load as one of four states:

- **Loading**;
- **Empty**, when the User has none;
- **Failed**, when the section could not be loaded;
- **Content**.

"Could not be loaded" is never read as "none". Favorites whose items could not all be fetched count as Failed. So does a section whose stream fails or ends before its first result. A failure after a section has loaded keeps what is already shown.

The two sections reveal together once both have resolved. A short ceiling stops one hung read from leaving Home blank. A section still loading when the ceiling runs out is left out, and it joins in place when it arrives.

## Empty states

- **First session** (both sections Empty): a prompt to start a first session, whose button switches to the Study tab.
- **Favorites hint** (no Favorites, some Recents): the Favorites heading with a Hide button on its line, then a banner explaining that bookmarking a Category or Topic pins it here, above the Recents.
  - Hiding is permanent on this device. It is a local preference and is never synced.
  - Hiding applies only to this hint. Once a Favorite exists the carousel shows as usual, and removing every Favorite does not bring the hint back.
  - If saving the preference fails, the hint returns on a later launch.
  - Home waits for the preference only when the hint could show, so a hidden hint never flashes.
- **Recents placeholder** (some Favorites, no Recents): the carousel, then the Recently studied heading with a line saying where sessions will appear.

A section that Failed shows neither the hint nor the placeholder.

## Failure and Retry

- **No section has content and at least one Failed:** an error with Retry, centered below the Level card. Retry reloads only the failed sections, restarts the reveal ceiling, and relaunches the Level card when it has no content. A section that arrives later with content replaces the error.
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
