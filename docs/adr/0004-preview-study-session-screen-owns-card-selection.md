# Preview Study Session Screen owns card selection

The Preview Study Session Screen receives `categoryId` and `subcategoryIds` and is solely responsible for selecting and randomizing the Flashcard pool before the session begins. `StudySession` receives the already-resolved `cardIds` list and does not perform selection itself.

We considered having each entry point (a Subcategory's play button, Quick Session, Custom session, Subcategory Details Start Session, Home's Favorites and Recents, Study Again) resolve the card list before navigating. Rejected because it would scatter selection logic across multiple ViewModels (Browse, CategoryDetails, SubcategoryDetails, Home, Summary) and prevent the Preview Study Session Screen from offering re-randomization or future parameter controls (card count sliders, difficulty weighting) — those controls only make sense on the screen that owns selection. `StudySession` stays a pure playback screen with no knowledge of how cards were chosen.

Consequence: `StudySession` route carries `cardIds: List<String>` (always pre-resolved). No entry point bypasses `PreviewStudySessionScreen`: Study Again replays the finished session through it, so selection runs fresh.

`PreviewStudySessionScreen` also receives `filterTagIds: List<String>` (default empty). When non-empty, selection filters to cards carrying any of those Tags before the shuffle. Only populated by Subcategory Details "Start Session" when the user has active Tag chips. Algorithm order: fetch all cards for scope → filter by Tags and difficulty → shuffle → take the session Length → apply Sort to the drawn cards.
