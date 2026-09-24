# Onboarding Flow

First-run panned gallery: one step per page, swipe (or tap Continue) to advance, explains structure/session modes/mastery, then captures daily goal, session default, and favorites — with a free voice-answering preview along the way as a premium-feature teaser.

Status: **built**. This doc is updated as decisions are made; see [As built](#as-built) for implementation notes.

## Navigation placement

```
Splash → Onboarding (8 screens) → Login → Main
```

- **Onboarding precedes Login on a first-run device** (value before ask). Captured preferences are device-scoped and need no uid; the one uid-scoped write, Screen 7's Favorites, is covered by a transient **Guest** session (see [Skip and commit](#skip-and-commit)).
- **Implemented branching** (`SplashViewModel`, `OnboardingViewModel`, `LoginViewModel`), all reading the same device-scoped flag:

  | Splash | | Onboarding (after Screen 8) | | Login (after sign-in success) | |
  |---|---|---|---|---|---|
  | onboarding not seen | → Onboarding | real signed-in user | → Main | onboarding seen | → Main |
  | seen, not authenticated | → Login | anyone else (incl. Guest) | → Login | onboarding not seen | → Onboarding |
  | seen, authenticated | → Main | | | | |

  "Authenticated" never counts a Guest (anonymous) session: sign-in stays mandatory, so a Guest is routed through Login like a signed-out user. Splash reads the auth state and the flag under 1s timeouts and falls back to *seen* if the flag read stalls — a slow local read must not hold the splash open, and wrongly re-showing onboarding to an existing user is the worse of the two failures.
- **Onboarding-seen flag**: stored in local DataStore, not Firestore. Device-scoped, not uid-scoped.
  - Consequence (accepted): same device + new account login → onboarding skipped. New device + same account → onboarding shown again.
- **Guest → User**: Login links the Guest to the Google credential (`linkWithCredential`), so the real account keeps the Guest's uid and the Favorites written under it. On a collision (the credential already belongs to another User — a returning user on a new device) the Guest session and its writes are discarded, not merged, and Login falls back to a plain sign-in.
- **Greeting**: Screen 8 greets by `displayName` (falling back to email) only when a user is already signed in, e.g. Replay onboarding on an authenticated device. A first run has no user yet, so the greeting drops the name.

## Screens

### 1. Welcome
- No progress dots — cover screens are conventionally exempt from progress chrome (showing "step 1 of 9" upfront can deter starting).
- ~~Forward-only (one-way door)~~ — **dropped as built.** The "no progress bar upfront" rationale above is about *first impression* only, and it has already expired by the time a user on Screen 3 could swipe back. Enforcing it needed a custom `NestedScrollConnection` consuming backward drags, i.e. real gesture code whose only payoff was preventing a harmless revisit. All eight pages are therefore ordinary pager pages, and `pagerState.currentPage` is the flow's only navigation state.
  - Consequence: the progress bar appears and disappears when crossing 1↔2 and 7↔8. Read as a rule the user can observe in both directions ("the cover and the outro carry no chrome"), not as a glitch.
- Subcopy fixed: *"...how sessions work, how content is organized, and what it takes to master a card."* (generic; avoids naming Category/Topic prematurely — Screen 2 handles that payoff).
- CTA: "Get started"

### 2. Structure (Category / Topic)
- Explains: Category → Topic → Flashcard hierarchy.
- Card eyebrow label "TOPIC" is **correct as designed** — confirmed against `CONTEXT.md`: Subcategory is the canonical code/docs term, but "Topic" is the intentional presentation-layer name (`CONTEXT.md` Subcategory entry, line 14). No change needed.
- CTA: "Continue"

### 3. Flashcard mastery — moved up from Screen 4, now precedes Session modes
- **Reordering**: swapped with Session modes. Two reasons: (1) this screen is pure presentation — no decision, no input, just teaches a concept — and cover-to-decision screens generally read better than decision-to-concept; (2) mastery is the natural setup for the Rated-vs-Fast choice — Rated is defined by rating cards toward mastery, so explaining mastery first makes the next screen's Rated card land immediately instead of introducing an unexplained term.
- Explains Attempts → Rating → Terminal State mechanic. Terminal State is three-valued — Mastered, Partial, Failed — decided by the best Rating the card achieved; copy should not imply a card either masters or fails. Presentation-only, no user decision — CTA is just "Continue."
- **Attempts limit is becoming Settings-customizable** (default 3, max 5 — see `CONTEXT.md` Attempt/Terminal State entries and `SYSTEMDESIGN.md` Settings Screen, both updated). Copy must not hardcode a specific attempt number (e.g. "the third") since it's no longer a fixed constant.
- **Headline**: shipped as **"Master cards and level up"** (eyebrow: GAMIFIED PROGRESS TRACKING), per the latest mockup. Supersedes the earlier "Get it right, cards graduate" round recorded here.
- **XP figure corrected**: the mockup drew "+150 XP"; `docs/design/xp-leveling-system.md` says **100 XP** per mastered card, and the illustration ships that number.
- **Subcopy, shortened and reworked** — three changes from the prior round: (1) cut the "on the first try or the third" phrasing entirely — disliked, and would've hardcoded a number that's now configurable anyway; (2) now names the three Rating values (**Failed, Partial, Correct**) since this is Rating's natural introduction point, freeing Screen 4's Rated card to drop them (see Screen 4 below); (3) dropped the explicit "next, pick how you want to run yours" bridge sentence — it was the third sentence bloating the previous draft, and the screen sequence + Continue button already implies "what's next" without spelling it out. This is a judgment call, reversible if the forward-pointer is missed in review.
  *"Rate each answer Failed, Partial, or Correct. Nail it and the card's mastered — it resurfaces less so you focus on what's shaky."*
- Sample card mock (Attempts dots + "Mastered" badge) unchanged — already terminology-correct.
- **New: XP/Level/progress teaser line**, added below the sample card, small/secondary weight (mirrors Screen 4's hands-free banner — main content stays primary, this doesn't compete visually):
  *"🏆 Every mastered card earns XP — climb Levels and track progress per topic in the Progress tab."*
  - Not a duplicate of Screen 5's XP teaser: Card Mastery is XP's single largest, most frequent source (`docs/design/xp-leveling-system.md`: 100 XP per card, Rated-only) and belongs in its own origin screen; Screen 5's line covers the separate daily-goal XP bonus. Same deliberate-split pattern as Screen 4's Rated-card/hands-free-banner split — each screen names the XP source it's actually responsible for teaching.
  - Terminology checked against `CONTEXT.md`: "XP" and "Level" used correctly (avoid-lists: Score/Points/Credits for XP; Rank/Tier/Grade for Level — none used). "Progress tab" matches the actual bottom-nav label (`docs/design/progress-dashboard.md`: "Home · Study · Progress · Settings").

### 4. Session modes (merged with former defaults screen) — moved down from Screen 3
- Explains Rated vs Fast, **and** captures the user's default Study Mode in the same screen — see rationale under Open Items history (former Screen 5 shrank to a single control once session-size and shuffle were dropped, no longer justified as its own screen).
- Eyebrow: SESSION MODES
- Headline: shipped as **"Pick your studying mode"** (eyebrow: TWO STUDY SESSION MODES), per the latest mockup; supersedes "Two ways to run a session".
- Subcopy: "Choose your mode each time you start. Pick your default below — change it anytime in Settings."
- Body: reuses the radio-card group component from the Preview Study Session Screen mockup (Rated / Fast cards, Rated pre-selected), but with **taller cards and more explanatory copy per card** — onboarding is a first-time explanation, not a quick pre-session reminder, so it can diverge from the Preview screen's terser text:
  - **Rated**: *"Reveal each answer, then rate yourself — or speak your answer and let the app grade it automatically. Progress is saved."*
  - **Fast**: *"Move through cards at your pace — tap to reveal the answer and advance, or turn on read-aloud to go hands-free."*
  - Content gap this fixed: the original Fast copy never mentioned read-aloud at all, its most distinguishing trait. Also corrected an initial assumption that Fast is TTS-driven by default — it's actually manual-first with read-aloud as an opt-in toggle (see `CONTEXT.md` Study Mode entry, updated to reflect this).
  - Rated card mentions AI-graded voice answering (describes the *behavior* — speak, auto-graded — without naming the feature) but drops both the Attempts/Mastered detail and the explicit Failed/Partial/Correct grade list — both now redundant with Screen 3 (Flashcard mastery), which covers the mechanic *and* names the three grades immediately before this screen. Removing the grade list here keeps the card generic ("rate yourself") and shortens it now that Screen 3 owns that detail.
  - The hands-free banner below supplies the feature *name* ("Voice Answering") that the Rated card's behavior description leaves unnamed — deliberate split, not duplication.
- Below the cards, one shared line, deliberately shrunk (smaller type, single line, no longer restating each mode's mechanic since the cards now own that) so it doesn't compete visually with the primary mode-choice content:
  *"🎙 Both modes can go hands-free — turn on read-aloud in Fast, or Voice Answering in Rated. Great for a walk or commute."*
  - Terminology fix carried over: previously said "Voice mode," which `CONTEXT.md` explicitly bans (Study Mode and Voice Answering entries both list "Voice mode" as _Avoid_ — reads like a third Study Mode when Voice Answering is actually a Rated-only toggle).
- CTA: "Continue" (standard onboarding pill — explicitly NOT the "Start session" gradient button from the Preview Study Session Screen; this screen sets a default preference, it doesn't launch a session).
- **Session size picker and Shuffle order toggle: removed.**
  - Shuffle order was never a real setting — the Flashcard Selection Algorithm (SYSTEMDESIGN.md) does an internal "slight shuffle" on the picked N cards, not user-configurable. Exposing it as an onboarding toggle invented a preference that doesn't exist in the data model.
  - Session size stays at its existing Settings-only default (20 cards), not surfaced during onboarding — reduces screen count, avoids onboarding bloat.

### 5. Daily goal + XP (new screen — did not exist in original 9)
- **Why added**: `docs/design/progress-dashboard.md` and `docs/design/session-stats-data-model.md` both spec Daily Goal as "set during onboarding (skippable)," but no such screen existed in the original mockup. XP/leveling (`docs/design/xp-leveling-system.md`) was also entirely absent from onboarding despite Card Mastery (Screen 3) being the #1 XP source.
- Combined into one screen rather than two — goal-setting is the functional half, XP/streak is a one-line payoff for why the goal matters.
- Position: fifth in the flow, right after Session modes (Screen 4). No longer directly adjacent to Mastery (Screen 3) after the reorder above, but the XP teaser still conceptually traces back to it — Card Mastery is the XP system's top source — so the link survives one screen removed.
- Eyebrow: YOUR GOAL
- Headline: shipped as **"Keep yourself engaged"** (eyebrow: TIME GOALS AND STREAKS), per the latest mockup; supersedes "Set your daily goal".
- Subcopy: "A few minutes a day beats a big session once a week. Change this anytime in Settings."
- Widget: Daily goal stepper, minutes/day, default 20, step 5, range 5–120 (`DailyGoal` in `:core:domain`). Shipped as the shared `FlashcardsStepper` with a "minutes per day" caption, rather than repeating the number in a second label.
- **Streak block split out and marked EXAMPLE.** The mockup drew "🔥 8 days · Best: 14d" inside the same surface as the live goal stepper. A brand-new user's real streak is zero, and a fabricated figure rendered in the same visual language as a control reads as *their* statistic. It now sits on its own card above the goal card, under an EXAMPLE overline. This is the same objection that removed the session-size recap badge on Screen 8 — illustration and live value must not share a surface unlabelled.
- XP teaser line below widget: "Hit your goal → earn XP, build your streak 🔥"
- CTA: "Continue"
- Skippable (top-right "Skip"): goal defaults to 20 min if skipped.

### 6. Voice input & privacy (Voice Answering) — formerly Screen 7
- Reworked per premium-framing decision below (Rated-only, AI-grading is the paid part; capture/on-device privacy transform is free and previewable by anyone).
- **Premium framing added.** `docs/adr/0024` / `0029` confirm Voice Answering has real, live server-side entitlement enforcement in prod (`users/{uid}/entitlement/premium`) — this is not an NYI feature like reminder notifications, so it stays in onboarding rather than being cut. But enforcement today is reactive-only (a free user can record → upload → get rejected after the fact); no proactive client paywall exists yet. Onboarding must not imply the whole feature is free.
- Split confirmed by user: **voice capture, VAD, and on-device obfuscation are free for everyone** (this is what "Test your voice" already demos — no grading happens in that preview). **Only the AI-grading step is premium.** The privacy-transform demo stays fully interactive and unrestricted, and doubles as a **trailer for the premium feature** — the demo is real and free, the payoff (AI grading) is what's gated.
- Subcopy update:
  *"Optional — use your mic to answer hands-free. Capture and privacy transform run on-device, always free."*
- New line/badge added below subcopy (or near the card), scoped specifically to grading, not the whole screen:
  *"⭐ Premium: automatic AI grading of your spoken answer."*
- "Private voice" card, its description, and the "Test your voice" button/interaction: unchanged — still a free, fully working preview of capture + on-device obfuscation only.
- **"Test your voice" as built.** The demo is on-device only: capture → `PitchShiftVoiceObfuscator` → play back. It never shows a transcript, because `TranscribeAndSanitizeUseCase` is a server callable and would contradict the screen's "runs on-device, always free" copy. The ViewModel drives it through the voice demo use cases over `VoiceDemoGateway`.
- **Microphone permission.** The tap requests the microphone through the app-wide launcher and `PermissionGateway` ([ADR-0052](../adr/0052-runtime-permission-architecture.md)), the same path the Preview Study Session Screen uses; onboarding has no launcher of its own.
  - A grant starts the demo. A soft refusal stays silent, and the next tap asks again.
  - A permanent refusal, given here or earlier on Preview, replaces the demo card's body with an "Open settings" message plus **Retry**. Retry shows the real prompt when Android 11+ misreported a dismissed prompt as permanent, and otherwise shows a "microphone is still off" snackbar.
  - Coming back from system Settings re-reads the status on resume, so a grant made there restores the normal card.
  - Onboarding never sets the voice-answering privacy-notice flag (`hasSeenVoiceAnsweringInfo`) or the persisted Voice Answering default. The privacy notice stays with the Preview screen's first voice-answering Start.

### 7. Favorites — formerly Screen 8
- Headline: shipped as **"Make it truly yours"** (eyebrow: CONVENIENT BOOKMARKS), per the latest mockup; supersedes "Favorite a few topics". The terminology note below still holds — "Starred" remains banned wherever favourites are named.
- Earlier headline fix, retained for the record: "Star a few topics" → **"Favorite a few topics"**.
  - Terminology fix: `CONTEXT.md`'s Favorite entity explicitly bans "Starred" as a synonym (line 43). The screen's own subcopy already correctly said "Favorites get pinned to your home screen" — headline contradicted it.
- Subcopy unchanged.

### 8. All set / Start studying — formerly Screen 9
- Recap badges rework: must reflect the actual decisions the user made during onboarding, in the order they made them — not a mix of real decisions and silent defaults.
  - **Before**: "20 cards/session" (never user-set — session size stays a silent Settings-only default, per Screen 4's note) + "Rated by default" + "2 favorites". Recap showed an untouched default while omitting a setting the user had just actively configured.
  - **After**: three badges, one per real decision, ordered by when the user made it — **Study mode** (Screen 4: "Rated by default" / "Fast by default") → **Daily goal** (Screen 5: e.g. "20 min/day") → **Favorites** (Screen 7: "N favorites"). Session-size badge removed entirely.
- Headline/subcopy/CTA unchanged ("You're all set, {name}!" / "Start studying"). `{name}` is `AuthUser.displayName` falling back to email — the same fallback `MainViewModel` already uses; the greeting drops the name entirely when neither exists.
- **This screen is the flow's only exit**, and the single place preferences are written. See [Skip and commit](#skip-and-commit).

## Skip and commit

- **Skip does not leave the flow — it jumps to Screen 8.** Rejected alternatives were "Skip exits to Main" (two exit paths, both of which have to remember to save) and "Skip advances one step" (not what Skip means).
- Consequences, all deliberate:
  - Exactly one code path commits, so a skipped run and a completed run cannot diverge.
  - The recap on Screen 8 needs no "was this skipped?" branch: badges read state, and after a Skip that state is the defaults the app is about to save. The user sees the assumed values rather than having them applied silently.
  - Process death mid-flow discards everything *and* leaves the flag unset, so the user restarts at Screen 1. No half-committed state exists.
- **Commit order**: if any Favorites were picked, start a **Guest** session (unless someone is already signed in) and write them under its uid → write `StudyPreferences` → only on success set `hasSeenOnboarding = true` → navigate. Skip, or picking nothing, never starts a Guest session. The flag doubles as an "everything was written" gate, so a failed write re-shows the flow next launch instead of silently losing the user's choices.
- **Navigation happens even if the write fails.** Nothing the user can do from Screen 8 fixes local storage, and trapping them on the last page of onboarding is worse than re-showing the flow.
- Repeat taps on "Start studying" are ignored while a commit is in flight.
- **Offline bound**: the Guest sign-in and the Favorites write each run under an 8s timeout. Firestore's offline persistence applies a write to the local cache immediately but leaves the returned `Task` pending until the server acks, so an unbounded await would hang an offline user on Screen 8. A timeout counts as a failed step: the flag stays unset and the flow replays next launch.

## As built

- **Module**: `:feature:onboarding` (`android-feature` convention plugin), depending on `:core:ui`, `:core:domain` and `:core:voice` (the on-device voice demo).
- **Navigation**: one `OnboardingRoute` destination hosting a `HorizontalPager` of all eight steps. Gradient, progress bar, Skip and the CTA are fixed chrome outside the pager, so a swipe moves content only. Skip uses an instant `scrollToPage`, not an animated one — animating a jump of up to seven pages would fling the user through every screen they just chose to skip.
- **Progress bar**: six segments over Screens 2–7; the two bookends show none. The Skip and progress slots keep their height on the steps that hide them, so content never shifts between steps.
- **Persistence**: a new device-scoped `user_preferences` DataStore in `:core:data` holding `hasSeenOnboarding`, `defaultStudyMode` and `dailyGoalMinutes`. It is bound under the `@UserPreferencesDataStore` qualifier. Favorites are the one exception: they live in Firestore at `users/{uid}/favorites/state` ([firestore-schema.md](firestore-schema.md)).
- **Favorites grid**: Screen 7 loads its options through `GetOnboardingSubcategoriesUseCase`, which reads the single, publicly readable `onboarding/subcategories` document: an admin-curated subset, readable before sign-in ([firestore-schema.md](firestore-schema.md#onboardings-curated-subcategory-picker)).
- **Debug entry point**: the debug hub (not Settings, which ships in release) carries a "Replay onboarding" button. It clears the flag before navigating, so the replay behaves exactly like a first run — including committing preferences again — rather than being a read-only walkthrough that behaves differently from the real thing.
- **Design-system work pulled in by this flow**:
  - `FlashcardsRatingButton` / `FlashcardsRatingButtonRow` + `RatingColors` promoted to `:core:ui` out of `StudySessionScreen`'s private copies (which carried raw hex and raw dp). A `null` `onClick` renders the read-only form Voice Answering will use to display the grade it assigned, so a grade badge cannot drift from the button the user would have tapped for the same rating.
  - `BrandColors.screenGradient` (+ `screenGradientBase`) added, and the ad-hoc `splashGradient` and `loginGradient` collapsed into it. It reuses the existing CTA gradient's two stops, so the whole brand now resolves to one colour pair: diagonal on a button, vertical full-bleed behind a screen.

## Open items (pending further grilling)

- User levels: resolved as informational-only (Screen 5 XP teaser), not a separate self-reported skill-level screen.

## Decided against (for now)

- **Voice playback (TTS) settings screen — cut entirely.** Formerly Screen 6: a "Default voice" picker + "Default speed" slider, functionally identical to the `VoiceSettingsDialog` already in Settings. Confirmed real/implemented (not a fake-feature issue like Shuffle-order), but picking a specific TTS voice before the user has ever heard the app talk is a premature decision, and it contradicted Screen 4's own precedent of keeping granular config (session size, shuffle) out of onboarding. Not crucial to convey at this point in the flow — cut, no replacement screen. Voice playback itself is still taught implicitly via Screen 4's Fast-mode card and hands-free banner; fine-tuning the voice stays a Settings-only action.
- **Reminder notifications**: no onboarding screen added. Feature is NYI — asking for a notification permission tied to a feature that does nothing yet is permission fatigue for zero payoff, and revoking + re-asking later is worse UX than asking once when the feature is real. **Revisit this decision as soon as reminder notifications land in the app in any shape** — add a permission + preferred-time screen then, likely positioned near Screen 5 (Daily goal), since "we'll remind you before your streak breaks" is the natural framing.
