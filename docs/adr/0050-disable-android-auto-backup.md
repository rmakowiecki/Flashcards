---
status: accepted
---

# Disable Android Auto Backup entirely

## Context

`allowBackup="true"` (Android default) backs up app SharedPreferences, DataStore files, and Firestore's local offline-persistence cache to the user's Google Drive account, and restores them silently on a fresh install to any device signed into that same Google account.

This surfaced a real bug: a device already running the app as User A (signed in via Google, favorites and Firestore data present) got a fresh install for a User B test — new Google account, never used the app before. Firebase Auth genuinely created a new uid for User B (confirmed in the Firebase console). But Auto Backup had restored User A's Firestore offline cache and Firebase Auth session files into the "fresh" install before the app even started. Onboarding ran (DataStore's `hasSeenOnboarding` was already excluded from backup, so it correctly looked unseen), User B signed in and picked favorites, but the home screen rendered User A's cached profile/greeting, and no Firestore user document was created for User B's new uid — whatever local-existence check gates that write saw User A's restored data and concluded the device was already initialized.

We'd already tried a narrower fix earlier in this branch: excluding just the DataStore preferences file (`datastore/user_preferences.preferences_pb`) from `backup_rules.xml` / `data_extraction_rules.xml`, on the theory that `hasSeenOnboarding` was the only backup-sensitive state. That fix was necessary but not sufficient — it missed Firebase Auth's own session storage and Firestore's offline persistence cache, neither of which we control the storage paths for.

## Decision

Set `android:allowBackup="false"` in the manifest, disabling cloud backup and device-to-device transfer entirely. Deleted `backup_rules.xml` and `data_extraction_rules.xml` (now unreferenced).

## Considered Options

- **Widen the exclude list** to also cover Firebase Auth's SharedPreferences and Firestore's local persistence directory. Rejected: these are internal SDK storage paths, not part of our contract with Firebase — they can change across SDK versions without notice, silently reopening this bug. We'd be maintaining an exclude-list arms race against a dependency we don't control.
- **First-run marker + explicit wipe**: keep backup on, but on startup check an excluded-from-backup marker; if absent, force `FirebaseAuth.signOut()` and `Firestore.clearPersistence()` before anything else runs. More robust than a path exclude-list, but adds a startup-ordering dependency and doesn't remove the underlying hazard — any other local state we forget to scope by uid is still exposed to the same class of bug.
- **Disable Auto Backup entirely (chosen)**: Firestore is the durable source of truth for all data that matters; everything stored only locally (DataStore prefs like daily goal minutes, voice-answer consent, Firestore's offline cache) is either trivial to lose (user resets a preference once) or actively dangerous to keep across a reinstall (a stale auth session or user cache masquerading as a fresh device). Removing backup removes the entire hazard class permanently instead of patching one instance of it, at the cost of losing offline-queued-write preservation across device transfers — an edge case we accept.
