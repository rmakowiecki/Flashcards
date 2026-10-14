# Onboarding Flow

The first-run onboarding gallery. It explains how content is organized, how cards are mastered and how the two Study Modes work, lets the User try the on-device voice transform, and captures the default Study Mode, the Daily Goal and a first set of Favorites. It runs before Login, so it delivers value before asking for a sign-in.

## Where it runs

Onboarding is shown on a device that has not finished it, before Login. When it ends it goes on to Main for a signed-in User and to Login for anyone else, a Guest included. Launch routing, including how a slow read of the onboarding flag is treated, is described in [navigation.md](navigation.md).

The onboarding flag belongs to the device, not to the User. A second account signing in on the same device skips onboarding, and the same account on a new device sees it again. This is an accepted trade-off: the preferences onboarding captures are device-scoped too ([settings.md](settings.md)).

The debug hub can clear the flag, so the next run behaves exactly like a first run, writes included. Splash checks the flag before it checks authentication, and sign-in only happens after onboarding, so clearing the flag is the only way a signed-in User reaches onboarding. That is why the flow can end at Main.

## The gallery

All steps are pages of one horizontal pager. A swipe or the bottom button moves between them, and every page can be swiped back to. The brand gradient, the top bar and the bottom button are fixed around the pager, so a swipe moves only the content.

- **Progress bar**: one segment per step between the cover and the outro. The cover and the outro show none, so the bar appears and disappears at both ends of the flow.
- **Skip**: in the top bar on every page except the last. It jumps straight to the last page without animating through the pages in between.
- **Stable layout**: the Skip and progress slots keep their height on pages that hide them, so content never shifts between pages.
- **Entrance**: the app logo is handed over from Splash. Once it settles, the cover's text rises into place, and then Skip and the button fade in. Neither can be tapped before it has appeared.

## Steps

1. **Welcome**: the cover. It introduces the tour and starts it.
2. **Structure**: the Category, Topic and Flashcard hierarchy, shown as a connected example. Subcategories are called Topics here, as everywhere in the UI.
3. **Mastery**: Ratings, Attempts and Terminal States on a sample card, and the XP a mastered card earns. The XP figure is a fixed illustration matching the default XP configuration ([xp-leveling-system.md](xp-leveling-system.md)). It does not name an Attempts limit, because that limit is a setting. This step explains a concept and asks for nothing, so it comes before the Study Mode choice that depends on it.
4. **Session modes**: Rated and Fast as two selectable cards, and the choice of default Study Mode, Rated preselected. The Rated card describes voice-graded answers and the Fast card describes read-aloud, and a shared line below names both hands-free deliveries. The text says the mode is still chosen per session and the default can be changed in Settings.
5. **Daily Goal**: a stepper for the Daily Goal, starting at its default and bounded by its limits, with a line tying the goal to XP and the Streak. An example Streak sits on its own card, labelled as an example, so a made-up figure is never mistaken for the User's own.
6. **Voice and privacy**: an optional, free preview of Voice Answering's on-device voice transform (see [Voice test](#voice-test)). A separate line says that automatic grading of a spoken answer is the premium part ([voice-answering.md](voice-answering.md)).
7. **Favorites**: a grid of Topics to favorite, each showing its icon and parent Category. The options come from an admin-curated list that can be read before sign-in ([firestore-schema.md](firestore-schema.md)). The list loads the first time the step is reached, not before. While it loads, a spinner shows, and the loaded grid fades in as one block ([ADR-0063](../adr/0063-loaded-content-reveals-as-one-block.md)). A failed load, or an empty list, shows an error with Retry, and coming back to the step does not reload a list that already loaded or failed.
8. **All set**: the outro and the flow's only exit. It greets the signed-in User by display name, falling back to email, and without a name when no one is signed in. Three badges recap the User's choices in the order they made them: the default Study Mode, the Daily Goal and the number of Favorites. Its button commits the flow.

Onboarding deliberately captures nothing else. Session length, Sort Order, Attempts, the voice and its speed, and the Voice Answering and read-aloud defaults stay in Settings, and the voice-answering privacy notice stays with the Preview Study Session Screen.

## Voice test

The voice test records the User, transforms the recording on the device and plays it back. It runs entirely on the device: nothing is uploaded, transcribed or graded, which is what makes it free. The shared microphone indicator follows the live input level while recording and the playback level while playing back. The User can stop recording early. A take with nothing usable in it, or a capture failure, shows a snackbar.

The test stops the moment the pager leaves its step or the app goes to the background, so the microphone never listens on another step or with the screen gone.

**Microphone permission.** The first tap requests the microphone through the app's shared permission flow, the same one the Preview Study Session Screen uses ([ADR-0052](../adr/0052-runtime-permission-architecture.md)).

- A grant starts the test. A soft refusal stays silent, and the next tap asks again.
- A permanent refusal, given here or earlier on Preview, replaces the test card with an explanation, a button to the app's system settings and Retry. Retry shows the real prompt when the system misreported a dismissed prompt as permanent, and otherwise shows a snackbar saying the microphone is still off.
- Coming back from system settings re-reads the permission, so a grant made there restores the normal card.

## Skip and commit

Skip never leaves the flow: it lands on the last page, and only the last page's button commits. A skipped run and a completed run therefore share one commit path. After a Skip, the recap shows the defaults about to be saved, so nothing is applied unseen. The screen holds every choice in memory until the commit, so process death mid-flow discards everything and leaves the flag unset, and the flow starts again at the cover.

The commit runs in this order:

1. If any Favorites were picked and no one is signed in, start a **Guest** session, so the Favorites have a User to belong to.
2. Write the picked Favorites under that User.
3. Save the default Study Mode and the Daily Goal.
4. Only if every step succeeded, mark onboarding as seen.
5. Navigate on.

Picking no Favorites, or skipping, never starts a Guest session. Taps on the button are ignored while a commit is running.

The flag doubles as an "everything was written" marker. If any step fails, the flag stays unset and the flow shows again on the next launch instead of silently losing the User's choices. The User is still navigated on, because nothing they can do from the last page would fix the failure, and trapping them there is worse than showing the flow again.

**Offline.** The Guest sign-in and the Favorites write are each bounded by a timeout, so an offline User is never stuck on the last page. A Guest sign-in needs a connection, so an offline first run with Favorites picked counts as failed and shows the flow again next launch. A Favorites write that the device has applied locally counts as done even before the server confirms it, and it reaches the server once the device is back online.

At Login the Guest is linked to the provider the User picks, keeping the Favorites. If that sign-in already belongs to another User, the Guest and its Favorites are discarded rather than merged. See **Guest** in `CONTEXT.md`.

## Related

- [navigation.md](navigation.md): launch routing through Splash, Onboarding and Login.
- [settings.md](settings.md): where the defaults captured here are edited later, and how they are stored.
- [home.md](home.md): where the Favorites picked here appear.
- [voice-answering.md](voice-answering.md): the full Voice Answering feature this step previews.
- [firestore-schema.md](firestore-schema.md): the curated Topic list and the Favorites document.
