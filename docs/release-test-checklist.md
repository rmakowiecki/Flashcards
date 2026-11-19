# Release test checklist

The manual pass every release candidate gets before it is promoted to production. Run it on a release-signed build installed from the Play closed track, never a debug build: R8, Play App Signing and App Check behave differently there. The flow around it: [release-flow.md](release-flow.md).

A failure that crashes the app, loses data or blocks sign-in blocks the release and is fixed on the release branch. For any other failure, the release owner decides whether it is fixed on the release branch now or filed for the next patch release, and writes that decision in the [record](#record).

## Before you start

- Note the candidate's `versionName`, `versionCode` and commit (App version row on the Account screen shows the first two).
- Install from the closed track, not from Firebase App Distribution. The two builds are signed with different keys and cannot update over each other, so switching a device between them needs an uninstall, which drops local data and queued Pending Sessions.
- Devices: one phone on API 26, one on API 36, and a tablet emulator for the smoke pass.
- Keep a throwaway account for the Account Deletion test. Never delete a real tester's account.
- Open the Firebase console's Crashlytics page and the Play Console pre-launch report for the build in another tab.

## Launch and sign-in

- [ ] First run, no signed-in user: Splash goes to Onboarding, then Login.
- [ ] Onboarding with no Favorite picked: ends at Login, no Guest session starts.
- [ ] Onboarding with at least one Favorite: the Favorite survives sign-in and shows on Home.
- [ ] Onboarding Skip lands on the last page, the recap shows the defaults, and its button commits.
- [ ] Onboarding offline with a Favorite picked: the last page does not hang, and the flow shows again on the next launch.
- [ ] Sign in with Google, from a Play install: succeeds.
- [ ] Sign in with GitHub, from a Play install: succeeds.
- [ ] A Guest linking a provider that already belongs to another user: the Guest and its Favorites are discarded and the existing user signs in.
- [ ] Sign out from the Account screen: lands on Login, back does not return to Main.
- [ ] A returning user on a fresh install: skips nothing it should not, finds their history, XP, Streak and Favorites.
- [ ] Kill the app and relaunch while signed in: goes straight to Main.

## Home

- [ ] Level card, Streak and Daily Goal row show real values, and reveal together with Favorites and Recently studied.
- [ ] Favorites carousel: a Category card's body opens Category Details and its Quick session button starts a Quick Session. A Subcategory card's body opens Subcategory Details and its play button starts a session.
- [ ] Recently studied: tapping a row opens Preview for the same session. At most 15 rows.
- [ ] A new account shows the empty states and the Favorites hint.
- [ ] Airplane mode, then a cold start: Home shows cached content or a Retry, and does not crash.

## Study tab

- [ ] Category list loads with icons, colors and progress rings.
- [ ] Browse search: a prefix finds Topics and Categories, clearing it restores the list.
- [ ] Category Details: Quick Session starts Preview with a sample of Topics.
- [ ] Selection Mode: the bottom-toolbar toggle and a long-press both enter it, and the long-pressed row is selected. Leaving it discards the selection.
- [ ] Custom session from one selected Topic and from several: both reach Preview with the right Topics.
- [ ] Subcategory Details: Filter dialog (Tags and Difficulty) and Sort dialog change the list. A filter that matches nothing shows the empty state.
- [ ] Favorite and unfavorite a Category and a Subcategory: Home reflects it.
- [ ] Pin a shortcut from a Details screen, and confirm the launcher shortcut opens the right screen.

## Preview

- [ ] Every entry point opens Preview with the right scope: Topic play button, Subcategory Details toolbar, Quick, Custom, Recent, Home Favorite, Study Again.
- [ ] Open every settings dialog (Mode, Voice answering, Attempts, Partial re-queueing, Read aloud, Voice, Length, Topic count, Filters, Sort) and confirm and cancel each. The rows shown match the mode.
- [ ] Confirming a dialog applies it to this session only. The "keep as default" checkbox updates Settings.
- [ ] Reshuffle on a Quick session redraws the Topics.
- [ ] Voice Answering: the privacy notice shows once ever, then the microphone permission request. Denying it keeps the user on Preview. Permanently denying it shows Open settings and Switch to manual.
- [ ] Start works with no connection for a manual Rated or Fast session.

## Rated session, manual

- [ ] Show answer reveals the answer, then Failed, Partial and Correct appear.
- [ ] Correct ends the card. Failed and Partial bring it back a few cards later, and the Attempt slots fill.
- [ ] A card that uses all its Attempts ends. With Partial re-queueing off, Partial ends the card on the spot.
- [ ] Report a problem: the flag opens the dialog, Submit sends, a second submit of the same actions is skipped.
- [ ] Close and system back both ask for confirmation, and confirming ends the session early and opens Summary.
- [ ] A session ended with no card studied returns to Preview and records nothing.

## Rated session, Voice Answering

- [ ] The question is read aloud, the microphone indicator animates, a spoken answer is graded, and the feedback is spoken before the next card.
- [ ] Staying silent re-queues the card without using an Attempt, with a notice.
- [ ] Three silences in a row pause the session with a notice, and Play resumes.
- [ ] No connection during grading: a notice names it, and the card comes back.
- [ ] Revoking the microphone permission mid-session ends the session.
- [ ] The transcript is shown on screen but never spoken.

## Fast session

- [ ] Tap-through: tap reveals the answer, tap advances.
- [ ] Read-aloud: the question, a pause, the answer, then it advances on its own. Previous, play-pause and next work. Jumping to a card works.
- [ ] The voice and speed from the voice dialog take effect.

## Spoken sessions, system behavior

Run each on a Rated Voice Answering session and a Fast read-aloud session.

- [ ] Screen off: the session keeps playing.
- [ ] App in the background: the session keeps playing, and the notification shows the Topic and progress.
- [ ] Notification, lock-screen card and headset buttons: play, pause, next and previous act like the in-app buttons. A Rated session offers no previous.
- [ ] An incoming call: the session pauses, no control is offered during the call, and the user is told once.
- [ ] A short notification sound: the session carries on.
- [ ] The Assistant or an alarm: the session pauses and resumes by itself if it ends quickly.
- [ ] Another media app taking over: the session pauses and stays paused.
- [ ] Disconnecting a headset: the session pauses and does not resume on the speaker.
- [ ] Text-to-speech engine disabled in system settings: the session pauses with a link to the settings, and Play restarts the card.
- [ ] Tapping the notification returns to the session.

## Summary and submission

- [ ] Summary shows the headline, stat pills, XP breakdown and the Level card pour. Skipping the animation works.
- [ ] Back to Home returns to the tab the session started from. Study Again opens Preview with the same mode and fresh cards, and back from there returns to the starting tab.
- [ ] Back during the Summary's loading does not lose the session: it still shows in Recently studied and counts toward XP.
- [ ] A session that crosses a Level fills and resets the bar and pulses the Level.
- [ ] Streak: the first session of a day advances it, a second the same day does not.
- [ ] Daily Goal: meeting it awards XP once per day.

## Offline

- [ ] Start a session offline, finish it offline: Summary shows the local score, and Recently studied, XP, Streak and Card Progress update on the device.
- [ ] Go back online: the Pending Session is delivered, and the figures stay the same or settle to the server's.
- [ ] Kill the app while a session is pending, relaunch online: it still delivers.

## Settings and Account

- [ ] Every Settings row opens its dialog, saves, and shows the new value. A save that fails shows a message.
- [ ] Account screen: Contact support opens an email draft with the support details. Privacy policy opens in an in-app tab matching the app theme. App version copies on tap. Open-source licenses opens.
- [ ] Report a bug: Send stays disabled until a severity and a long enough description exist. A sent report shows a confirmation and closes. Close with text asks to discard. Offline shows the no-connection message and keeps the text.
- [ ] Account Deletion on the throwaway account: offline shows a snackbar and changes nothing. Online shows the blocking overlay, then Login. Signing in again with the same provider account starts with no history.

## Launcher shortcuts

- [ ] A shortcut tapped while the app is not running opens its Details on top of Main, and back returns to Main.
- [ ] A shortcut tapped while the app is running opens its Details on top of the current screen, and back returns to that screen.
- [ ] A shortcut tapped during a session leaves the session running.
- [ ] A shortcut for a removed Topic lands on Main.
- [ ] The dynamic shortcuts mirror the Favorites after adding and removing one.

## Appearance and accessibility

- [ ] Dark mode on Home, Study, Preview, both session screens, Summary, Settings and Account.
- [ ] Font scale at 200% on the same screens: nothing is clipped or unreachable.
- [ ] System Remove animations on: no screen breaks. The network graph backgrounds may keep moving.
- [ ] TalkBack through one full manual Rated session: every control is reachable and announced, and the transcript is not read.
- [ ] Tablet emulator, portrait and landscape, every screen: note only clipped or unreachable UI.

## Release health

- [ ] The Crashlytics console has the mapping file for this `versionCode`, and every crash or non-fatal reported on this build shows deobfuscated. The release build has no crash trigger; to force one, crash a profiling build of the same commit from its Debug tab.
- [ ] No unexpected crashes or non-fatals from the testers on this build in the last day.
- [ ] The Play pre-launch report for this build shows no crash or install failure.
- [ ] The backend runs this candidate's commit: the record's "Backend deployed from" entry names that commit with both the functions and the rules deploys successful, and nothing was deployed since. In the Firebase console, no function's last deploy and no Firestore rules release is later than the recorded deploy. If either check fails, redeploy (see [release-flow.md](release-flow.md#3-stabilize)), update the record and rerun the checklist.

## Record

For each run, note the candidate, the devices, the date, and every failure with its severity and the release owner's decision for it: fixed on the release branch or deferred to the next patch. A run passes when nothing blocking is open and every other failure has a decision.

| Candidate (`versionName` / `versionCode`) | Backend deployed from (commit, time, functions and rules succeeded) | Devices | Date | Result | Failures and decisions |
|---|---|---|---|---|---|
