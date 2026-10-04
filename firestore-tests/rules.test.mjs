// Firestore Security Rules tests, covering the server-authoritative client-read-only rules (ADR-0049),
// the recents projection, the server-owned XP configuration and the callable-only Bug Reports.
// Small and standalone on purpose — this is a guard against a specific class of production-only failure
// (there is no other way to verify a rule without deploying it), not a second test framework for the
// project. Run via `npm test` in this directory, which starts the Firestore emulator (see
// ../firebase.json) and runs this file under Node's built-in test runner.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { after, afterEach, before, describe, it } from 'node:test';
import { fileURLToPath } from 'node:url';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc } from 'firebase/firestore';

const __dirname = dirname(fileURLToPath(import.meta.url));
const PROJECT_ID = 'flashcards-rules-test';
const OWNER_UID = 'owner-uid';
const OTHER_UID = 'other-uid';

/** A minimal, syntactically valid session document (ADR-0014) — rules don't inspect its shape. */
const sessionDoc = { sessionId: 'session-1', studyMode: 'Rated' };

/** A minimal, syntactically valid progress document (ADR-0016) — rules don't inspect its shape. */
const progressDoc = { categoryId: 'cat-1', cards: {} };

/** A minimal, syntactically valid progress-summary document (ADR-0016) — rules don't inspect its shape. */
const progressSummaryDoc = { subcategories: {} };

/** A minimal XP configuration document — rules don't inspect its shape. */
const xpConfigDoc = { cardMastered: 100 };

/** A minimal, syntactically valid scoring-state document — rules don't inspect its shape. */
const scoringStateDoc = { xp: 0, level: 1, xpIntoCurrentLevel: 0, currentStreak: 0, bestStreak: 0, lastStudyDate: '', goalMetDate: '' };

const BUG_REPORT_PATH = 'bugReports/report-1';

/** A minimal Bug Report document — rules don't inspect its shape. */
const bugReportDoc = { uid: OWNER_UID, description: 'The study session froze.', severity: 'minor', status: 'new' };

/** A minimal recents document — rules don't inspect its shape. */
const recentsStateDoc = { entries: {} };

let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules: readFileSync(join(__dirname, '..', 'firestore.rules'), 'utf8'),
      host: '127.0.0.1',
      port: 8080,
    },
  });
});

after(async () => {
  await testEnv.cleanup();
});

afterEach(async () => {
  await testEnv.clearFirestore();
});

/**
 * Seeds a document straight past security rules, the way `submitStudySession`'s Admin SDK context
 * would — every document below is client-read-only (ADR-0049), so a client `setDoc` can no longer be
 * used to arrange fixtures for the read/foreign-user/unauthenticated assertions.
 */
async function seedAsAdmin(path, data) {
  await testEnv.withSecurityRulesDisabled(async (adminContext) => {
    await setDoc(doc(adminContext.firestore(), path), data);
  });
}

describe('users/{uid}/sessions/{sessionId} (client-read-only, ADR-0049)', () => {
  it('the owning user can read their own session', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/sessions/session-1`, sessionDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(ownerDb, `users/${OWNER_UID}/sessions/session-1`)));
  });

  it('the owning user cannot create, update or delete their own session', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/sessions/session-1`, sessionDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
    const ownRef = doc(ownerDb, `users/${OWNER_UID}/sessions/session-1`);

    await assertFails(setDoc(doc(ownerDb, `users/${OWNER_UID}/sessions/session-2`), sessionDoc));
    await assertFails(setDoc(ownRef, { ...sessionDoc, studyMode: 'Fast' }));
    await assertFails(deleteDoc(ownRef));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/sessions/session-1`, sessionDoc);
    const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
    const foreignRef = doc(otherDb, `users/${OWNER_UID}/sessions/session-1`);

    await assertFails(getDoc(foreignRef));
    await assertFails(setDoc(foreignRef, sessionDoc));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/sessions/session-1`, sessionDoc);
    const anonDb = testEnv.unauthenticatedContext().firestore();
    const anonRef = doc(anonDb, `users/${OWNER_UID}/sessions/session-1`);

    await assertFails(getDoc(anonRef));
    await assertFails(setDoc(anonRef, sessionDoc));
  });
});

describe('users/{uid}/progress/details/subcategories/{subcategoryId} (client-read-only, ADR-0049)', () => {
  it('the owning user can read their own progress document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/details/subcategories/sub-1`, progressDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(ownerDb, `users/${OWNER_UID}/progress/details/subcategories/sub-1`)));
  });

  it('the owning user cannot write their own progress document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/details/subcategories/sub-1`, progressDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
    const ownRef = doc(ownerDb, `users/${OWNER_UID}/progress/details/subcategories/sub-1`);

    await assertFails(setDoc(ownRef, progressDoc));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/details/subcategories/sub-1`, progressDoc);
    const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
    const foreignRef = doc(otherDb, `users/${OWNER_UID}/progress/details/subcategories/sub-1`);

    await assertFails(getDoc(foreignRef));
    await assertFails(setDoc(foreignRef, progressDoc));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/details/subcategories/sub-1`, progressDoc);
    const anonDb = testEnv.unauthenticatedContext().firestore();
    const anonRef = doc(anonDb, `users/${OWNER_UID}/progress/details/subcategories/sub-1`);

    await assertFails(getDoc(anonRef));
    await assertFails(setDoc(anonRef, progressDoc));
  });
});

describe('users/{uid}/progress/{docId} (client-read-only, ADR-0049)', () => {
  it('the owning user can read their own progress-summary document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/summary`, progressSummaryDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(ownerDb, `users/${OWNER_UID}/progress/summary`)));
  });

  it('the owning user cannot write their own progress-summary document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/summary`, progressSummaryDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
    const ownRef = doc(ownerDb, `users/${OWNER_UID}/progress/summary`);

    await assertFails(setDoc(ownRef, progressSummaryDoc));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/summary`, progressSummaryDoc);
    const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
    const foreignRef = doc(otherDb, `users/${OWNER_UID}/progress/summary`);

    await assertFails(getDoc(foreignRef));
    await assertFails(setDoc(foreignRef, progressSummaryDoc));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/summary`, progressSummaryDoc);
    const anonDb = testEnv.unauthenticatedContext().firestore();
    const anonRef = doc(anonDb, `users/${OWNER_UID}/progress/summary`);

    await assertFails(getDoc(anonRef));
    await assertFails(setDoc(anonRef, progressSummaryDoc));
  });
});

describe('users/{uid}/progress/user-stats (scoring state, client-read-only, ADR-0049)', () => {
  it('the owning user can read their own scoring-state document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/user-stats`, scoringStateDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(ownerDb, `users/${OWNER_UID}/progress/user-stats`)));
  });

  it('the owning user cannot write their own scoring-state document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/user-stats`, scoringStateDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
    const ownRef = doc(ownerDb, `users/${OWNER_UID}/progress/user-stats`);

    await assertFails(setDoc(ownRef, scoringStateDoc));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/user-stats`, scoringStateDoc);
    const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
    const foreignRef = doc(otherDb, `users/${OWNER_UID}/progress/user-stats`);

    await assertFails(getDoc(foreignRef));
    await assertFails(setDoc(foreignRef, scoringStateDoc));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/progress/user-stats`, scoringStateDoc);
    const anonDb = testEnv.unauthenticatedContext().firestore();
    const anonRef = doc(anonDb, `users/${OWNER_UID}/progress/user-stats`);

    await assertFails(getDoc(anonRef));
    await assertFails(setDoc(anonRef, scoringStateDoc));
  });
});

describe('users/{uid}/recents/state (client-read-only projection)', () => {
  it('the owning user can read their own recents document', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/recents/state`, recentsStateDoc);
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(ownerDb, `users/${OWNER_UID}/recents/state`)));
  });

  it('the owning user cannot create, update or delete their own recents document', async () => {
    const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
    const ownRef = doc(ownerDb, `users/${OWNER_UID}/recents/state`);

    await assertFails(setDoc(ownRef, recentsStateDoc));

    await seedAsAdmin(`users/${OWNER_UID}/recents/state`, recentsStateDoc);
    await assertFails(setDoc(ownRef, { entries: { forged: {} } }));
    await assertFails(deleteDoc(ownRef));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/recents/state`, recentsStateDoc);
    const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
    const foreignRef = doc(otherDb, `users/${OWNER_UID}/recents/state`);

    await assertFails(getDoc(foreignRef));
    await assertFails(setDoc(foreignRef, recentsStateDoc));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(`users/${OWNER_UID}/recents/state`, recentsStateDoc);
    const anonDb = testEnv.unauthenticatedContext().firestore();
    const anonRef = doc(anonDb, `users/${OWNER_UID}/recents/state`);

    await assertFails(getDoc(anonRef));
    await assertFails(setDoc(anonRef, recentsStateDoc));
  });
});

describe('config/xp (server-owned XP configuration, client-read-only)', () => {
  it('an authenticated user can read it', async () => {
    await seedAsAdmin('config/xp', xpConfigDoc);
    const userDb = testEnv.authenticatedContext(OWNER_UID).firestore();

    await assertSucceeds(getDoc(doc(userDb, 'config/xp')));
  });

  it('a Guest (anonymous-auth user) can read it', async () => {
    await seedAsAdmin('config/xp', xpConfigDoc);
    const guestDb = testEnv.authenticatedContext(OTHER_UID, { firebase: { sign_in_provider: 'anonymous' } }).firestore();

    await assertSucceeds(getDoc(doc(guestDb, 'config/xp')));
  });

  it('an unauthenticated request cannot read it', async () => {
    await seedAsAdmin('config/xp', xpConfigDoc);
    const unauthDb = testEnv.unauthenticatedContext().firestore();

    await assertFails(getDoc(doc(unauthDb, 'config/xp')));
  });

  it('no client can create, update or delete it', async () => {
    const ownerRef = doc(testEnv.authenticatedContext(OWNER_UID).firestore(), 'config/xp');
    const otherRef = doc(testEnv.authenticatedContext(OTHER_UID).firestore(), 'config/xp');
    const unauthRef = doc(testEnv.unauthenticatedContext().firestore(), 'config/xp');

    await assertFails(setDoc(ownerRef, xpConfigDoc));
    await assertFails(setDoc(unauthRef, xpConfigDoc));

    await seedAsAdmin('config/xp', xpConfigDoc);
    await assertFails(setDoc(ownerRef, { ...xpConfigDoc, cardMastered: 1000 }));
    await assertFails(setDoc(otherRef, { ...xpConfigDoc, cardMastered: 1000 }));
    await assertFails(deleteDoc(ownerRef));
    await assertFails(deleteDoc(unauthRef));
  });
});

describe('bugReports/{reportId} (written only by submitBugReport, no client access)', () => {
  it('the reporting user cannot create, read, update or delete their own report', async () => {
    const ownerRef = doc(testEnv.authenticatedContext(OWNER_UID).firestore(), BUG_REPORT_PATH);

    await assertFails(setDoc(ownerRef, bugReportDoc));

    await seedAsAdmin(BUG_REPORT_PATH, bugReportDoc);
    await assertFails(getDoc(ownerRef));
    await assertFails(setDoc(ownerRef, { ...bugReportDoc, status: 'fixed' }));
    await assertFails(deleteDoc(ownerRef));
  });

  it('a different authenticated user cannot read or write it', async () => {
    await seedAsAdmin(BUG_REPORT_PATH, bugReportDoc);
    const otherRef = doc(testEnv.authenticatedContext(OTHER_UID).firestore(), BUG_REPORT_PATH);

    await assertFails(getDoc(otherRef));
    await assertFails(setDoc(otherRef, bugReportDoc));
    await assertFails(deleteDoc(otherRef));
  });

  it('an unauthenticated request cannot read or write it', async () => {
    await seedAsAdmin(BUG_REPORT_PATH, bugReportDoc);
    const unauthRef = doc(testEnv.unauthenticatedContext().firestore(), BUG_REPORT_PATH);

    await assertFails(getDoc(unauthRef));
    await assertFails(setDoc(unauthRef, bugReportDoc));
    await assertFails(deleteDoc(unauthRef));
  });
});
