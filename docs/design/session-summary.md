# Session Summary

The screen that ends every Study Session that Studied at least one Flashcard, of either Study Mode, completed or abandoned. It hands the session over for delivery, shows the XP it earned and the User's Level moving, and offers the next actions. It shows only the session that just ended: no screen reopens a past session.

## Arrival and submission

The session screen hands the Summary its whole **Session Result** and is removed from the back stack, so the Summary sits directly on top of the tab the session was started from ([study-session.md](study-session.md#session-termination)). A session with no Studied Flashcards never reaches the Summary.

On arrival the Summary reads the User's current Daily Goal and seals it into the result ([ADR-0048](../adr/0048-streak-and-daily-goal-ride-the-session-payload.md)). It then queues the result as a **Pending Session** and waits a short while for the server's score. The Summary writes nothing to the server itself: the server scores and records the session in one transaction ([session-submission.md](session-submission.md)). Leaving the Summary, at any moment, never loses a session that reached it.

What the Summary shows depends on how the wait ends ([ADR-0055](../adr/0055-pending-sessions-and-local-progress-projection.md)):

- **Server score.** The server recorded the session in time, so the Summary shows the server's score.
- **Local preview.** The device is offline, or the server rejected the session, or the delivery run ended without its score, or the wait ran out. The Summary scores the session on the device with the same rules and the cached XP configuration. It scores against the User's prior Card Progress and scoring state as the device projects them, the User's other Pending Sessions included and this session excluded. Whether the session is delivered later depends on why there was no server score. After a network or server failure, or when the wait ran out, the session stays queued and is delivered later. After a permanent rejection it is moved to the diagnostics file and stops being a Pending Session, so the previewed score is never recorded ([session-submission.md](session-submission.md)).
- **No score.** The preview's reads failed. The session is still queued, and a snackbar says it is saved but its XP could not be calculated.
- **Not saved.** The session could not be queued at all. Nothing holds it, so it earns nothing, and a snackbar says it could not be saved.

The Summary takes the first answer and keeps it. A server score that arrives after a local preview never replaces it on screen, so a preview can differ from what the server records, for example when the server holds a newer XP configuration. A score, once shown, survives a rotation and a restore. A Summary restored without a score submits again, and the queue keeps one entry per session.

## Screen

While the score loads, only a spinner shows.

Behind everything, over the brand gradient, a slow animated network graph gathers around the middle of the screen while the score loads and the total counts up. It morphs once, during the animation sequence, out to two opposite corners, clear of the settled content ([Animation sequence](#animation-sequence)). It is the same mesh the session draws ([ADR-0069](../adr/0069-morpheus-is-an-external-library-built-from-a-submodule.md)).

Once it settles, the screen holds, from top to bottom:

- an app bar with a close button and the screen title;
- the Level card ([Level card](#level-card));
- a headline, the XP total and the stat pills, centred in the space between the Level card and the sheet;
- a bottom sheet that cannot be dismissed, with a tip for the Study Mode, **Back to Home** and **Study Again**.

The stack scrolls when it does not fit.

**Headline.** An encouraging headline for an abandoned session or a negative XP total. A celebratory "perfect" headline for a Rated session in which every Studied Flashcard ended Mastered. Otherwise a plain congratulation.

**XP total.** The session's signed total, with an info button beside it that opens the XP breakdown dialog ([XP breakdown](#xp-breakdown)). Without a score, the total and its button are left out.

**Stat pills.** The duration in whole minutes, with a short session shown as under a minute. The number of Studied Flashcards. In a Rated session only, the number of Flashcards that ended Mastered.

Fast and Rated differ only in the mastered pill, the tip and the Rated-only breakdown lines.

## XP breakdown

The dialog itemizes the score: one row per XP source, each with its icon, its label, the count times the rate or a caption for a source that is not a multiplication, and the amount, then the total. Every figure is the score's own amount, never recomputed on screen. XP amounts come from the XP configuration ([xp-leveling-system.md](xp-leveling-system.md)).

- A line worth no XP is left out. An abandoned session has no session-completed line, and a Fast session has no Rated-only lines.
- A loss is shown in the error tone.
- Lines appear in a fixed order: new cards, card mastery, Partial, mastery defense, de-mastery, Streak, session completed, Daily Goal.

## Level card

The Summary uses the same Level card as Home ([home.md](home.md)). Its source depends on the score:

- **Scored**, from the server or the local preview: the card shows the score's Level and XP before the session, then the position after it. Like Home's card, it waits for the auth user, and it shows its figures without a name if that read fails.
- **No score**: the card follows the same live Level and XP stream as Home. It shows a shimmering skeleton until the stream and the auth user have both emitted. It shows the same skeleton without the shimmer if the stream fails or ends before its first value.

A scored card can differ from Home's live one, for the same reasons a local preview can differ from the server's record.

## Animation sequence

The animation is a motion layer over the settled screen. It plays once, when the score arrives. A restore, a rotation during or after the animation, reduced motion or a missing score show the settled screen directly. A rotation while the score is still loading does not stop it, because nothing has played yet.

1. The total sits under the app bar at zero, with a Skip button in the app bar. The title and close button are not shown yet.
2. The breakdown lines are revealed as tiles one at a time beneath it. Each shows its count times its rate, or a caption, and adds its amount to the total, which counts up for a gain and down for a loss. The list scrolls to keep the newest tile in view.
3. After the last tile and a short pause, confetti bursts from the total if the total is positive, and the tile list fades out. A zero or negative total gets no confetti.
4. The Level card drops in as the list fades, showing where the User stood before the session. The card and the headline push the total down, the stat pills fade in, and the headline, total and pills glide to the middle of the space between the card and the sheet.
5. The card switches to the User's position after the session, and the earned XP pours into the Level bar from the starting position. Every Level crossed fills the bar, resets it to empty and pulses the Level number. A Level crossed in the middle shows only the bar and the number, because its threshold is not known; the XP-within-Level count runs only for the first and last Level. A negative total drains the bar within the same Level.
6. The bottom sheet rises and the app bar's title and close button fade in, overlapping the end of the pour. Skip disappears and the breakdown button appears.

The background graph morphs out of the middle as the tile list fades in step 3, taking one second, so it lands just after the sheet starts to rise. A skip morphs it over the same second while everything else finishes with a short transition, and a screen shown settled draws it in the corners from the start.

Skip, or the first system back, jumps to the end. Everything still moving finishes with a short transition, and the Level card shows the after position without a pour. Confetti already under way finishes, and a skip before the burst means no burst. The total stays on the screen's centre line throughout, and the breakdown button sits beside it without moving it. Until the screen settles, the Level card and the sheet are hidden from accessibility services; TalkBack then announces the total and any Level-up once.

**The pour is the Level card's own animation**, not the Summary's. The Summary only shows the card's before position and then its after one, and its timeline never waits for the pour to end. The same card pours on Home whenever the live Level moves while Home is open. It pours a rise across Levels and any change within one Level. A lower Level, which means another User, and a system with animations off snap to the new figures.

## Next actions

- **Back to Home**: returns to the main screen on the tab the session was started from. The close button and system back, once the animation has settled or been skipped, do the same.
- **Study Again**: opens the Preview Study Session Screen for the same thing, the way tapping this session's **Recent** on Home would ([preview-study-session.md](preview-study-session.md)). Preview starts with this session's Study Mode and delivery, and draws fresh Flashcards. The Summary leaves the back stack, so back from Preview returns to the starting tab. A second tap is ignored.

## Related

- [session-submission.md](session-submission.md): the Pending Session queue, delivery and the server's commit.
- [xp-leveling-system.md](xp-leveling-system.md): XP sources, the XP configuration and the Level curve.
- [study-session.md](study-session.md): how a session terminates and what it hands over.
- [home.md](home.md): the Level card on Home.
- [preview-study-session.md](preview-study-session.md): where Study Again lands.
