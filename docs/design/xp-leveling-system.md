# XP and Leveling

The progression layer over studying: how a User earns and loses **XP**, how XP turns into a **Level**, and where the XP amounts come from. XP rewards studying and mastering Flashcards, so Rated sessions earn more than Fast ones without making Fast sessions pointless.

## XP sources

| Source | Earned for | Study Modes |
|---|---|---|
| New Flashcard studied | Each Flashcard the session adds to the User's **Studied** set. Once ever per Flashcard, and never for Private Flashcards. | Both |
| Card mastered | Each Flashcard ending **Mastered** that was not already Mastered. | Rated |
| Card Partial | Each Flashcard ending **Partial**, including one that was already Mastered. | Rated |
| Mastery defended | Each previously Mastered Flashcard ending Mastered again. It earns this instead of the mastery award, not on top of it. | Rated |
| Card de-mastered | A loss for each previously Mastered Flashcard ending **Failed**. | Rated |
| Time studied | Each whole minute of the session's duration ([session-submission.md](session-submission.md)). | Both |
| Session completed | Reaching the end of the deck. Abandoned sessions earn nothing here. | Both |
| Streak | Once per calendar day, on the first session that extends or restarts the **Streak**. The award grows with the Streak's length up to a cap. | Both |
| Daily Goal met | Once per calendar day, on the session that brings the day's studied minutes up to the **Daily Goal**. | Both |

A previously Mastered Flashcard that ends Partial keeps its mastery and earns only the Partial award: no defense award and no loss. A Fast session earns no card mastery, Partial, defense or de-mastery XP, so a User who studies only in Fast mode still sees coverage and XP grow, more slowly, without ever touching **Persistent Mastery**. How the Streak and the Daily Goal are evaluated for each session is in [session-submission.md](session-submission.md).

Each per-card award is a flat amount, whatever the Flashcard's Difficulty, so every line on the Session Summary reads as a count times a rate.

## XP configuration

Every XP amount, and both level-curve parameters, come from the XP configuration rather than from fixed rules, so the balance can be tuned without shipping a release ([ADR-0047](../adr/0047-xp-values-behind-a-config-repository.md)). The server scores each session with the configuration current when the session is delivered and stores the rates it used on the session, so a later change never rewrites a session already scored. If the server's configuration is missing or invalid, it scores with the bundled defaults. The app and the server each bundle the same defaults, and both test suites check them against one shared default configuration file.

The device fetches the server's configuration on every sign-in and keeps the last valid copy, so it can preview a session's XP offline. Until a first fetch succeeds it uses the bundled defaults. A failed or invalid fetch leaves the kept copy unchanged.

## Level curve

The XP needed to complete a Level grows super-linearly with the Level number: a base amount times the Level raised to an exponent, rounded up to the nearest thousand. The base and the exponent come from the XP configuration, and they are tuned so that early Levels come within a session or two while later ones take sustained effort.

A Level-up awards no XP of its own. On the Session Summary and on Home, the Level card's bar fills, resets and pulses the Level number for every Level crossed ([session-summary.md](session-summary.md#animation-sequence)).

## Stored XP and Level

The User's XP, Level and XP within the Level are stored as computed values in the User's scoring state, written only by the server when it records a session ([firestore-schema.md](firestore-schema.md#scoring-state), [session-submission.md](session-submission.md#scoring-state)). They are not replayed from session history, which would give a different answer once the XP configuration changes.

**Level never decreases.** De-mastery reduces both total XP and XP within the Level, but a loss that would take XP within the Level below zero is clamped at the Level's floor, and total XP drops by the same clamped amount so the two stay consistent.

## Related

- [ADR-0047](../adr/0047-xp-values-behind-a-config-repository.md): why XP amounts are configuration.
- [session-submission.md](session-submission.md): how the server scores a session, the Streak and Daily Goal rules, and Pending Sessions.
- [session-summary.md](session-summary.md): how a session's XP is presented and animated.
- [home.md](home.md): the Level card on Home.
- [firestore-schema.md](firestore-schema.md): the XP configuration document and the scoring state.
