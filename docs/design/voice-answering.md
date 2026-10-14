# Voice Answering

Voice Answering is the hands-free delivery of a Rated Study Session. The app reads each question aloud and listens for the User's spoken answer. It obfuscates the voice on the device, then has the answer transcribed and graded on the server, and the grade becomes the Flashcard's **Rating**. It works with the screen on, where the User sees the recognized answer and the grade, and with the screen off, where everything is heard ([ADR-0028](../adr/0028-voice-answering-not-background-only-streamed-transcript-then-grade.md)).

Voice Answering exists only in Rated sessions ([ADR-0025](../adr/0025-voice-answering-rated-mode-only.md)). It is chosen on the Preview Study Session Screen, which also asks for the microphone, and it is fixed for the session ([preview-study-session.md](preview-study-session.md)). What a Rating does to the queue, including the re-insertion after a silence or a grading failure, is in [rated-mode.md](rated-mode.md). The playback service, the system controls, audio interruptions and voice engine failure, shared with Fast read-aloud, are in [study-session.md](study-session.md#spoken-sessions).

## The round

Each card on screen goes through one round:

1. **Question.** The question is read aloud through the same speech engine Fast mode uses, with the card's spoken version when it has one. The reading stops after the question, and the answer is never read ([ADR-0031](../adr/0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md)).
2. **Listening.** Only once the question has finished does the app open the microphone, so it never hears its own voice. A short cue plays when the microphone really records. If no speech starts within the silence timeout, the round counts as a silence. Once speech starts, the app listens until the User stops speaking.
3. **Grading.** When the User stops speaking, the card's answer is revealed, so the User can check what they missed while grading runs ([ADR-0026](../adr/0026-voice-grade-unifies-with-rating-reveal-tied-to-speech-end.md)). The answer, once obfuscated, is sent to the server. The recognized answer arrives first, and shows until the grade does, for at least a moment so it can be read ([ADR-0053](../adr/0053-voice-answer-sheet-states-and-live-input-level.md)).
4. **Feedback.** The grade is applied as the card's Rating at once, exactly as a manual tap would. The card stays on screen while its feedback is spoken: the Rating's name, then the grader's rationale. A Failed or Partial rationale says what was missed and includes the full expected answer, so this is where the User hears the right answer. A Correct one is a short affirmation. The grade's percentage is never shown or spoken.
5. **Next card.** After the feedback and a short pause, the session reaches its auto-advance point and reads the next question. An open dialog holds the session there ([study-session.md](study-session.md#dialogs)).

A grade that arrives for a card that is no longer on screen keeps no Rating, but its feedback is still spoken.

## On screen

The bottom panel shows one thing at a time:

- the transport row and the voice settings button, while a question is read or the round is paused;
- a live microphone indicator while listening;
- a progress disc while the microphone is being prepared, while grading runs without a transcript, and during any short notice;
- the progress disc beside the recognized answer once it arrives;
- the Rating and the grader's rationale while the feedback is spoken.

Voice settings can be changed only from the transport row, never mid-round. The indicator follows the real microphone level, of any sound and not only speech, to show that the microphone hears the User. The level is computed on the device and is never logged, stored or uploaded. How the level is measured, shaped and drawn is in [voice-capture-indicator.md](voice-capture-indicator.md).

The recognized answer is the User's, not the Flashcard's. It shows only in the panel, only until the grade, and it is never read aloud or exposed to TalkBack. TalkBack hears one announcement per step: listening, grading, then the Rating's name.

## Notices and pauses

Short notices are spoken by a second, separate voice and, for a User watching, also shown as a snackbar. Each one finishes even if the User pauses.

- **Silence.** Nothing was heard in time. A notice says so, and the card is re-inserted unanswered, using no Attempt.
- **Grading failure.** The answer could not be graded, either because there was no connection or because the service had a problem, and the notice names which. The card is re-inserted the same way. Its answer stays revealed through the notice, because the round reached grading.
- **Three in a row.** The third silence in a row, or the third grading failure in a row, pauses voice answering with a notice instead. The two counts are separate, and a graded answer or a resume resets them.
- **Microphone failure.** The microphone could not be prepared or did not start recording, or it stopped working. Voice answering pauses on the current card without re-inserting it.

A paused voice round offers only play. Play resumes voice answering from the current card's question. If the microphone permission is gone by then, the session ends instead ([study-session.md](study-session.md#session-termination)).

## Transport by phase

The session decides which commands are offered at each moment, for the in-app row and the system controls alike ([study-session.md](study-session.md#spoken-sessions)). A Rated session never offers going back one card or jumping to one.

- **At the question**, playing or paused: next, previous, and play or pause. Next re-inserts the card unanswered, as after a silence. Previous reads the question again in place.
- **While listening, grading or a short notice plays**: pause only.
- **While the feedback plays**: pause and next. Next skips the rest of the feedback and moves on.
- **Paused after the feedback, or at the auto-advance point**: play and next. Play after a paused feedback reads it again from the start, and play at the auto-advance point moves on. Next moves on.

A pause while listening closes the microphone, and play reads the question again with nothing counted. A pause while grading lets grading finish: the grade is applied, and its feedback waits for play.

## Capture

Capture runs only inside the session's foreground playback service, never outside a session.

**Detecting the answer.** The microphone records small frames, and an on-device neural voice activity detector marks where speech starts and ends. One answer is the audio from speech start until the speaker has been silent for a moment. The app listens by voice activity alone, with no button, because the phone may be in a pocket.

**Routing.** When a Bluetooth headset with a microphone is connected, capture must use that microphone, preferring LE Audio and falling back to classic Bluetooth. A headset without a microphone leaves the phone's microphone in use. The headset's microphone link is opened only for the listening window and closed right after. The rest of the time the headset stays on its media link, so its buttons work and playback keeps full quality. A capture that expects the headset's microphone never falls back to the phone's microphone, which would be muffled in a pocket. If the headset's microphone is not ready in time, that is a microphone failure. A headset that connects or drops mid-session takes effect at the next answer, never mid-recording. Routing goes by device type, so no Bluetooth permission is needed ([ADR-0027](../adr/0027-bluetooth-mic-capture-le-audio-first-sco-fallback-bt-strict-screen-off.md)).

## Privacy

- **Obfuscated on the device.** Before an answer leaves the phone, its pitch and formants are shifted together by a random factor, redrawn each time the microphone opens for listening. Its timing is then restored, so the speech stays intelligible to transcription. The raw recording is wiped as soon as the obfuscated copy exists. This deters casual re-identification and voice cloning. It is not strong anonymization. The transform and its math are in [voice-obfuscation.md](voice-obfuscation.md).
- **Never stored.** The obfuscated audio is sent once per answer and kept nowhere, on the device or the server. The transcript and the grade exist only during the round. A session records only the resulting Ratings ([session-submission.md](session-submission.md)).
- **No keys in the app.** The transcription and model keys stay on the server, which is why grading needs a backend at all ([ADR-0024](../adr/0024-voice-grading-cloud-function-proxy.md)).

## Backend

Each answer is one call to a streaming Firebase callable function, which carries the signed-in User's identity ([ADR-0029](../adr/0029-voice-grading-two-callables-payload-inferred-debug-mode.md)). The function:

1. rejects a caller without premium entitlement ([Entitlement](#entitlement)) and an empty or oversized recording;
2. transcribes the audio with ElevenLabs Scribe;
3. has a language model clean the transcript: filler words and repetitions are normalized, and personal details the User happened to say are removed;
4. streams that cleaned transcript back at once, so the app can show it before grading ends;
5. has a language model grade the cleaned answer against the question and the expected answer, as a percentage and a one-sentence rationale, and returns them.

Both model steps use Gemini on Vertex AI. The app maps the percentage to a Rating: below 40 is Failed, 40 to 79 is Partial, 80 and above is Correct ([ADR-0031](../adr/0031-voice-answering-shared-tts-engine-silence-timeout-grade-bands.md)).

The whole call runs within one time budget, with no retry, because a retry would upload the answer again inside the same budget. A call that runs out of time, or fails for lack of a connection, is a no-connection failure. The server gives up just before the app does, with its own error, so a slow service reads as a service problem. Every other failure, including a missing entitlement, is a service problem.

Called without a question and an expected answer, the same function only transcribes and cleans. The debug build's voice tab uses this mode. That tab tests each stage separately: voice activity detection, raw capture, obfuscation, transcription, grading and the entitlement check.

## Entitlement

Grading is a premium capability, checked on the server for every call and never trusted from the app. The record lives with the User's data and only Cloud Functions can read it ([firestore-schema.md](firestore-schema.md)). A separate callable reports it to the app. The record is set by hand today, and syncing it from purchases is planned ([planned/voice-answering.md](planned/voice-answering.md)).

The app does not check entitlement before offering Voice Answering. A User without it can start a session, but every answer then fails as a service problem. Each failure re-inserts the card, and the third in a row pauses voice answering.

## Related

- [rated-mode.md](rated-mode.md): Ratings, Attempts, the queue and re-insertion.
- [study-session.md](study-session.md): the shared screen, spoken sessions, interruptions and termination.
- [preview-study-session.md](preview-study-session.md): choosing Voice Answering and the microphone handshake.
- [planned/voice-answering.md](planned/voice-answering.md): entitlement sync from purchases.
- [firestore-schema.md](firestore-schema.md): the entitlement record.
- [voice-obfuscation.md](voice-obfuscation.md): how the answer's voice is transformed before it leaves the phone.
- [voice-capture-indicator.md](voice-capture-indicator.md): the live microphone indicator.
