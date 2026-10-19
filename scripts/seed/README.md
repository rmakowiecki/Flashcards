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

## XP configuration

`seed_xp_config.py` writes the server-owned XP configuration document, `config/xp`, from
`testdata/xp-scoring/default-xp-config.json` at the repo root. The `submitStudySession` function scores
every session with that document, and the app fetches it for its XP preview. The script validates the
file first, then overwrites the whole document, so rerunning it resets any console edit to the defaults.

```bash
python3 seed_xp_config.py --dry-run      # validate and print the document, no writes
python3 seed_xp_config.py                # overwrite config/xp (same credentials as above)
```

To change live XP rates without an app release or a function deploy, edit `config/xp` in the Firebase
console. Every field must stay present, award fields must be integers, `cardDemastered` must be zero or
negative, `levelBaseCost` and `levelsPerCostStep` must be positive integers, and `levelCostSteps` must be a
nonempty list of positive integers. The function scores with its bundled default and logs an error while the
document is missing or invalid.

## Notes

- `.tmp/` and any `*service-account*.json` / `*.cred.json` here are gitignored.
- Display names come from titlecased slugs; acronym/multi-word fixes live in
  `NAME_OVERRIDES` in `build_fixture.py`. Category/subcategory `order` is assigned
  by card volume — adjust in the Firebase console afterward if desired.
- Schema + projection rules: see `../../docs/design/firestore-schema.md`,
  `docs/adr/0007-firestore-collection-structure.md`, and
  `docs/adr/0037-flashcard-content-sharded-by-byte-budget.md`.
- Two-stage tooling rationale: see `docs/adr/0008-two-stage-firestore-seed-tooling.md`.
- `purge_firestore.py` deletes both `shards` (current) and `flashcards` (legacy,
  pre-ADR-0037) subcollections — safe to run even after no subcategory has the legacy
  one any more.

## Behavior notes

- Supports extending existing structure without wiping *unrelated* subcategories/categories — a run only touches the subcategories present in its fixture, so third-party tools can add new subcategories incrementally. Within a subcategory a fixture *does* touch, the fixture must carry that subcategory's full card set: `shards` are fully rewritten every run (see above), so a partial fixture for an already-seeded subcategory (`--sample N`, or a third-party tool emitting only its own new cards) silently drops any existing card the fixture doesn't repeat.
- **Card `id` is a map key inside its shard's `flashcards` field** (`flashcards["<cardId>"]`), not a Firestore
  document key — a shard doc's own id is just its shard index. Card ids must still be globally unique (the
  capture skill mandates a cryptographically random hex suffix to prevent collisions — 100 historical
  collisions were repaired pre-import) since a shard doc's `flashcards` map is keyed by them.
