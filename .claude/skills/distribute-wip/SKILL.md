---
name: distribute-wip
description: |
  Build the current, possibly-uncommitted working tree as a debug (default) or profiling APK and ship it to Firebase App Distribution's "wip-debug" / "wip-profiling" group, without blocking the session -- for checking WIP progress or real-world performance remotely (e.g. from a phone-connected session) mid-feature, no PR/review/CI involved. Use when the user invokes /distribute-wip, or asks to "ship a dirty build", "distribute WIP", "send current progress to my phone", "send a profiling build", or similar.
---

# distribute-wip

Thin wrapper -- all build/upload logic lives in `scripts/distribute-wip.sh`.
This skill only launches it, reads its result, and relays it. No business
logic here by design.

## Variant

- `debug` (default): everyday WIP check. Use unless the user asks otherwise.
- `profiling`: R8-optimized, non-debuggable build for judging performance
  ("profiling build", "release-like build", "check jank/perf on my phone").
  Pass `--variant profiling`.

## Flow

1. Launch in background so this session is never blocked on the
   multi-minute Gradle build:

   ```
   Bash(command: "scripts/distribute-wip.sh", run_in_background: true)
   ```

   For a profiling build, append `--variant profiling` to the command.

2. Tell the user it's running in the background, continue the conversation
   -- don't poll or wait synchronously.

3. When the harness re-invokes you on process exit, the script's output
   always ends with exactly one plain-English outcome line:
   - `SUCCESS: wip-<variant> build <timestamp> uploaded`
   - `FAILED: <stage> -- see output above` (stage is `preflight` /
     `compile` / `assemble` / `upload`)

   Read that last line off the tail of the background output.

4. Send the notification, verbatim, no reformatting:

   ```
   PushNotification(message: <that last line>)
   ```

5. Report the same line in the session. On failure, the actual
   compiler/gradle/firebase output is right above it in the same log --
   no separate status file or logfile to go look for.

## Notes

- The script never reads `firebase-app-distribution-service-account.json`
  (repo root, gitignored) -- only points `GOOGLE_APPLICATION_CREDENTIALS`
  at its path. Don't Read/cat/grep that file either; it's covered by a
  repo-wide deny rule for a reason.
- Script is fully standalone: no skill dependency. Runs the same from a
  bare terminal or cron -- this skill is just one caller of it.
- Both variants install as the same `.debug`-suffixed package, so either
  overwrites the other on the test device -- by design, latest WIP always
  wins, no side-by-side install slots. The launcher label ("Flashcards
  Debug" / "Flashcards Profiling") and the Debug tab's build header tell
  them apart.
- After installing a profiling build, force its baseline profile to compile
  before judging performance:
  `adb shell cmd package compile -f -m speed-profile com.rossomak.flashcards.debug`
