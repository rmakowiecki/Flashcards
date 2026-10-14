# Browse

The Study tab's Category list, Category Details and Subcategory Details: how a User browses Categories, Topics and Flashcards, and how a Study Session starts from them. Search on the Study tab has its own doc, [category-search.md](category-search.md).

## Category list

The Study tab opens on a search bar above the list of every Category, in the taxonomy's fixed order. Each row shows:

- the Category's icon tile in its color ([category-icon-color.md](category-icon-color.md));
- its name;
- a chip line naming its most prominent Subcategories ([category-search.md](category-search.md#chip-lines));
- its Topic count;
- a bookmark badge when the Category is a Favorite.

Tapping a row opens Category Details. A Category row has no play button, because a Category is not a study unit.

While the list loads, a spinner shows, and loaded content fades in as one block ([ADR-0063](../adr/0063-loaded-content-reveals-as-one-block.md)). A failed load and an empty list read the same way: an error with Retry. The taxonomy always has Categories, so an empty list can only mean the read went wrong.

## Category Details

Category Details lists every Subcategory of the Category, labeled **Topics** in the UI. An overline gives the Topic count. Each row shows:

- a progress ring;
- the Subcategory name;
- its card count, and how many cards the User has Studied once that is more than zero;
- a bookmark badge for a Favorite;
- a play button and a chevron.

The row and the play button do different things ([ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md)):

- **Tapping the row** opens Subcategory Details and starts nothing.
- **The play button** opens the Preview Study Session Screen for that one Subcategory.

The top app bar carries the following:

- back navigation;
- a bookmark action that adds or removes the Category as a Favorite;
- an action that pins a launcher shortcut to the Category ([launcher-shortcuts.md](launcher-shortcuts.md)).

The bookmark and the shortcut action move into an overflow menu only when the bar has no room. A successful bookmark change shows a snackbar with Undo. Undo restores the value the change moved away from, so it never flips a later change.

A failed load, or a read that returns no Subcategories, shows an error with Retry. A Category always contains at least one Subcategory.

### Starting a session

The bottom toolbar starts a session in one of two ways (see **Study Creation** in [CONTEXT.md](../../CONTEXT.md)). Both of its controls stay disabled until the list has loaded.

- **Quick Session**: the toolbar's trailing button opens Preview with the Category's whole Subcategory list. Preview samples the session's Subcategories from that list ([flashcard-selection.md](flashcard-selection.md)).
- **Custom**: the toolbar's Selection Mode toggle, or a long press on any row, enters **Selection Mode**. A long press also selects that row. The Custom session button opens Preview with exactly the selected Subcategories:
  - they are sent in list order, not in the order they were tapped;
  - several selected make a Custom session;
  - one selected makes a single-subcategory session.

### Selection Mode

In Selection Mode:

- each row swaps its play button and chevron for a checkbox and keeps its ring;
- a selected row is tinted;
- the overline counts the selected Topics out of all of them;
- the subtitle gives the total card count of the selection;
- the toolbar offers an exit control in the toggle's place, plus a select-all control. Select-all selects everything unless everything is already selected, in which case it clears the selection;
- the trailing button becomes the Custom session button, with the selected count. It is enabled once at least one Topic is selected.

The exit control, the top app bar's back arrow and system back all leave Selection Mode, not the screen. Leaving discards the selection. The scroll position is kept across the switch, so a long press halfway down the list does not jump back to the top.

## Progress rings

The ring measures the share of the Subcategory's Flashcards the User has **Studied**. It appears on:

- each Topic row on Category Details;
- each Topic row in Browse search results;
- the Home screen's Favorite Subcategory cards, which show the same measure.

All of them read the User's progress summary, which is one document for every Subcategory. A screen draws all its rings from that one read, however many Topics it lists. The summary counts Pending Sessions as soon as they are queued ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)), and it updates live while the screen is open.

- **The list never waits on the summary.** Rows appear first, and the rings fill in when it arrives.
- **Unknown is not zero.** Until the summary arrives, a ring shows only its track, with no percentage. A Subcategory the User has never studied shows an empty ring at zero percent.
- **A failed read** leaves the rings unknown and is logged. It never affects the list. Bookmark badges follow the same rule for a failed Favorites read: they stay off.
- **The denominator** is the Subcategory's card count from the taxonomy, which excludes Private Flashcards. The card count shown beside a ring is the same figure.
- **The ring is capped at full**, because Pending Sessions counted on the device can briefly add up to more Studied Flashcards than the Subcategory holds.

The summary also carries each Subcategory's Mastered count, but no ring shows it ([ADR-0016](../adr/0016-card-progress-model.md)).

## Subcategory Details

Subcategory Details lists every Flashcard of the Subcategory. Each card is a collapsible item:
- collapsed, it shows the question, the Difficulty and the card's Tags;
- expanded, it adds the answer.

Expanded cards stay expanded across a trip to Preview and back, rotation and process death. The top app bar carries:
- the Subcategory name, with the Category as its subtitle;
- back navigation;
- a bookmark action, which behaves as on Category Details, Undo snackbar included;
- the action that pins a launcher shortcut to the Subcategory.

An overline under the bar gives the card count. While filters narrow the list, it gives the visible count out of the total instead.

### Bottom toolbar

The floating bottom toolbar has a Filter control, a Sort control and a trailing Start session button ([ADR-0022](../adr/0022-subcategory-details-filter-sort-toolbar.md)). Filter and Sort are disabled while the list is loading or failed.

- **Filter** opens one dialog with two facets:
  - **Tags**: chips derived from the Tags on this Subcategory's Flashcards, with Select all and Deselect all. Several Tags combine with OR. Every Tag starts selected, and at least one must stay selected to confirm.
  - **Difficulty**: a range within 1 to 10, starting at the full range.

  The two facets combine with AND. Edits are a draft that applies only on Confirm, and dismissing the dialog discards them. Filter shows a badge while the filters narrow the pool. Filters are never a saved default ([ADR-0030](../adr/0030-preview-session-settings-sheet.md)), so they start cleared on every visit.
- **Sort** opens a dialog offering the three **Sort Order** values. The order starts from the User's saved Sort Order. A choice applies to this screen and carries into the session it starts. "Keep as default" also saves it as the preference ([ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md)). Sort only reorders the list and never changes which cards are shown. It has no badge: the order is seeded from a preference, so a "not Default" badge would stay lit for good for anyone whose saved order isn't Default.
- **Start session** opens Preview as a single-subcategory session. It carries the selected Tags, the Difficulty range and the Sort Order ([preview-study-session.md](preview-study-session.md)). While filters are active, the button shows how many cards the filtered pool holds. Preview may still trim that number to the session Length. The button is disabled unless cards are listed.

The saved Sort Order is read once when the screen opens. A change made elsewhere does not re-sort a list already on the back stack, and this screen's own "keep as default" does not feed back into it.

A change of filters or order scrolls the list back to the top. The collapsed top bar stays collapsed.

### Empty and failed states

- **No matches.** A Subcategory always holds at least one Flashcard, so an empty filtered list can only mean the filters excluded everything. The screen then shows a no-matches state with a Reset filters action. Reset filters restores every Tag and the full Difficulty range. It leaves the Sort Order alone, because Sort cannot empty the list.
- **Failed load.** A failed load shows an error with Retry.

## Related

- [ADR-0041](../adr/0041-topic-row-tap-browses-play-button-studies.md): the row browses and the play button studies.
- [ADR-0022](../adr/0022-subcategory-details-filter-sort-toolbar.md): the Subcategory Details toolbar, Filter dialog and empty filtered state.
- [ADR-0038](../adr/0038-one-sort-order-and-flashcard-selection-seam.md): one Sort Order and the shared filter rule.
- [ADR-0016](../adr/0016-card-progress-model.md): Card Progress and the progress summary.
- [ADR-0003](../adr/0003-category-and-subcategory-details-are-root-routes-above-the-tab-shell.md): Details screens open full-screen above the tab shell.
- [category-search.md](category-search.md): search on the Study tab and the chip line.
- [flashcard-selection.md](flashcard-selection.md): how Preview draws the session from what these screens send.
- [preview-study-session.md](preview-study-session.md): what each entry point sends to Preview.
- [launcher-shortcuts.md](launcher-shortcuts.md): the pin-shortcut action.
- [planned/private-flashcards.md](planned/private-flashcards.md): adding, editing and deleting Private Flashcards and the Private only filter, not built.
- [docs/user-flow/study.md](../user-flow/study.md): the navigation diagram.
