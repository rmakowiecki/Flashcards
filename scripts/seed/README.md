# Firestore seeding

Two-stage import of curated flashcards from the local capture inboxes
(`~/.claude/flashcards`) into Firestore. The intermediate fixture is a **local,
gitignored temp file** — never committed.

```
capture inboxes  ──build_fixture.py──▶  .tmp/fixture.json  ──seed_firestore.py──▶  Firestore
(~/.claude/...)        (stage 1)          (gitignored)           (stage 2)
```

## Setup

```bash
cd scripts/seed
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Stage 1 — build the temp fixture

```bash
python3 build_fixture.py                 # reads ~/.claude/flashcards -> .tmp/fixture.json
python3 build_fixture.py --src <dir> --out <path>
```

Projects each inbox card into the Firestore shape, derives `categories` and
`subcategories` from the slugs (subcategory IDs are namespaced `{categoryId}-{subSlug}`),
bin-packs each subcategory's cards into byte-budgeted `shards` docs (ADR-0037), and writes
`.tmp/fixture.json`. Deterministic; re-run freely. Fails loudly on duplicate card ids.
Supports `--sample N` to keep at most N cards per subcategory (useful for test seeds);
optional `--seed` for reproducible sampling.

## Stage 2 — upsert into Firestore

Provide a service-account JSON (Firebase console → Project settings → Service
accounts). **Never commit it.**

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/abs/path/service-account.json
python3 seed_firestore.py --dry-run      # report planned writes/skips, no changes
python3 seed_firestore.py                # skip-existing (default): write only absent docs
python3 seed_firestore.py --overwrite    # set() every doc (clobbers console edits)
python3 seed_firestore.py --cred /abs/path/service-account.json   # alt to env var
```

Re-running is idempotent. `categories` and `subcategories` are keyed on capture id
(`--skip-existing` default); `shards` docs are always fully rewritten every run, since
shard membership is derived data with nothing hand-curated inside it (ADR-0037) — any
shard doc left over from a previous run that no longer has a matching index is deleted
so stale card content never lingers. Three collections are written:

- `categories/{categoryId}` — top-level category docs
- `subcategories/{categoryId-subSlug}` — subcategory docs (namespaced id, `categoryId` field)
- `subcategories/{categoryId-subSlug}/shards/{n}` — `{ flashcards: {cardId: {...}, ...} }`,
  cards bin-packed by byte size into a map keyed by card id (not an array — see ADR-0037),
  typically one shard per subcategory

## Notes

- `.tmp/` and any `*service-account*.json` / `*.cred.json` here are gitignored.
- Display names come from titlecased slugs; acronym/multi-word fixes live in
  `NAME_OVERRIDES` in `build_fixture.py`. Category/subcategory `order` is assigned
  by card volume — adjust in the Firebase console afterward if desired.
- Schema + projection rules: see `docs/design/firestore-schema.md`,
  `docs/adr/0007-firestore-collection-structure.md`, and
  `docs/adr/0037-flashcard-content-sharded-by-byte-budget.md`.
- Two-stage tooling rationale: see `docs/adr/0008-two-stage-firestore-seed-tooling.md`.
- `purge_firestore.py` deletes both `shards` (current) and `flashcards` (legacy,
  pre-ADR-0037) subcollections — safe to run even after no subcategory has the legacy
  one any more.
