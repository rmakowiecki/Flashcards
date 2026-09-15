#!/usr/bin/env python3
"""Stage 2 of Firestore seeding: temp fixture JSON -> Firestore.

Globs the gitignored temp dir (default `scripts/seed/.tmp/*.json`) for fixtures
produced by `build_fixture.py` and upserts them via the Firebase Admin SDK.

Schema written (see ADR-0007, ADR-0037):
  categories/{categoryId}                               → { name, order, subcategoryCount, iconSvg, color, featuredSubcategoryNames[] }
  subcategories/{categoryId-subSlug}                    → { name, nameLower, categoryId, categoryName, order, cardCount }
  subcategories/{categoryId-subSlug}/shards/{n}         → { flashcards: { "<cardId>": { id, question, answer, tags[], createdAt, ... }, ... } }
  onboarding/subcategories                              → { subcategories: { "{categoryId-subSlug}": { order, categoryId, categoryName,
                                                            subcategoryId, subcategoryName, iconSvg }, ... } } — one singleton document,
                                                            fully overwritten every run (see write_onboarding_subcategories)

The `subcategoryId` field carried in the fixture is used to route each shard into the correct
subcollection path — it is NOT written as a Firestore document field.

`categories` gets its own write path (see `upsert_categories`), separate from the generic
`upsert()` used by `subcategories`: `iconSvg`/`color` are optional, hand-curated fields
(see docs/design/category-icon-color.md) that this pipeline must never clobber — once Firestore
holds a non-empty value for either (including one set by a manual console edit), that value wins
over whatever the checked-in fixture has, and the write uses `set(merge=True)` so any field
omitted from the payload is left untouched rather than deleted.

`shards` also gets its own write path (see `upsert_shards`), independent of --skip-existing/
--overwrite: shard membership is pure derived data (ADR-0037) with nothing hand-curated inside
it, so every run overwrites every shard doc in full, and any shard left over from a previous run
whose index no longer exists in the freshly-packed set is deleted — otherwise a subcategory that
shrinks or repacks differently would leave stale card content sitting in an orphaned shard doc.
Orphan scanning uses the fixture's complete subcategory-id set, not just the ones with at least
one shard — a subcategory that drops to zero cards still needs its old shards found and deleted.

`subcategories.cardCount` also gets its own always-on refresh (see `refresh_card_counts`),
independent of --skip-existing/--overwrite, for the same reason as `shards`: it's pure derived
data describing content that `shards` — always fully rewritten — already keeps authoritative and
fresh. Left unrefreshed, `cardCount` would silently drift from the real card count the moment a
subcategory's size changes after its doc already exists. This is narrower than the `shards`
write path: only `cardCount` is touched (via `set(merge=True)`), not `name`/`order`/`nameLower`
— `order` in particular may be hand-adjusted in the Firebase console (see Notes below), so it
keeps today's --skip-existing semantics rather than being force-refreshed like `cardCount`.

`meta/seed.value` is bumped by 1 (see `bump_cache_seed`) as the very last write of every run, once
every other write above has committed — it is the freshness signal the app's cache-generation
invalidation compares against (ADR-0039), so a run that fails partway through must never bump it
for content that did not actually land.

Idempotency:
  --skip-existing (DEFAULT)  subcategories: write a doc only if its id is absent, skip if
                             present (except `cardCount`, always refreshed regardless — see
                             above). categories, shards: not applicable — always written (see
                             `upsert_categories`/`upsert_shards` above).
  --overwrite                subcategories: set() every doc unconditionally (clobbers console
                             edits). categories: also clobbers `iconSvg`/`color` sticky fields,
                             bypassing the preserve-existing-value check — though the write is
                             still `set(merge=True)`, so a field the fixture omits (an uncurated
                             icon) is left alone rather than deleted. shards, cardCount refresh:
                             no effect, already always-overwrite.

                             Required when a NEW denormalized field is added to the
                             --skip-existing `subcategories` collection: --skip-existing writes
                             only absent doc ids, so a field like `subcategories.nameLower` never
                             reaches docs that already exist, and search silently matches
                             nothing. Dry-run first. Does NOT apply to `categories`/`shards` —
                             both are always fully written regardless of this flag.
  --dry-run                  report planned writes/skips without touching Firestore. Still reads
                             existing docs (categories: to evaluate sticky fields; shards: to
                             find orphans to delete; subcategories: to determine skip/write), it
                             just skips the write call itself.

Credentials: Application Default Credentials. Point GOOGLE_APPLICATION_CREDENTIALS
at a service-account JSON, or pass --cred <path>. The project id is taken from the
credential. The service-account JSON must never be committed.
"""
from __future__ import annotations
import argparse, glob, json, os, sys
from collections import defaultdict

DEFAULT_FIXTURES = os.path.join(os.path.dirname(__file__), ".tmp")
BATCH_LIMIT = 400  # Firestore batched-write cap is 500; stay under it.

# Firestore's commit RPC also caps total request/transaction size; shard docs run far larger
# than the tiny per-card docs BATCH_LIMIT was sized for, so upsert_shards() flushes on this byte
# budget too. A batched write is accounted as a transaction internally (index entries etc. count
# against the limit, not just raw payload bytes), which errors ("Transaction too big") at a
# noticeably smaller effective size than the ~10MiB request-size cap alone would suggest — kept
# well under that observed threshold.
SHARD_BATCH_BYTE_LIMIT = 1_000_000


def load_fixtures(fixtures_dir: str):
    paths = sorted(glob.glob(os.path.join(fixtures_dir, "*.json")))
    if not paths:
        sys.exit(f"no *.json fixtures in {fixtures_dir} — run build_fixture.py first")
    categories, subcategories, shards = [], [], []
    onboarding_subcategories_doc: dict = {"subcategories": {}}
    # Shard doc ids ("0", "1", ...) are only unique within their subcategory, so the collision
    # key is the (subcategoryId, id) pair, not the bare shard id. Every real run consumes a
    # single fixture file (build_fixture.py always writes one canonical .tmp/fixture.json), so
    # this only matters if multiple fixture files are ever glued together by hand — in which case
    # a collision means two independently-packed shard sets disagree about what shard "0" (etc.)
    # for that subcategory contains, and silently keeping one would drop the other's cards with
    # no signal. Fail loudly instead.
    seen_shard_keys: set[tuple[str, str]] = set()
    for p in paths:
        with open(p, encoding="utf-8") as f:
            data = json.load(f)
        categories += data.get("categories", [])
        subcategories += data.get("subcategories", [])
        onboarding_subcategories_doc["subcategories"].update(
            data.get("onboardingSubcategories", {}).get("subcategories", {})
        )
        for s in data.get("shards", []):
            key = (s["subcategoryId"], s["id"])
            if key in seen_shard_keys:
                sys.exit(
                    f"duplicate shard id across fixtures: subcategory {s['subcategoryId']!r} "
                    f"shard {s['id']!r} — these must not be combined blindly"
                )
            seen_shard_keys.add(key)
            shards.append(s)
    return paths, categories, subcategories, onboarding_subcategories_doc, shards


def init_db(cred_path: str | None):
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
    except ImportError:
        sys.exit("firebase-admin not installed — `pip install -r requirements.txt`")
    if cred_path:
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = cred_path
    if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS") and not cred_path:
        sys.exit("no credentials: set GOOGLE_APPLICATION_CREDENTIALS or pass --cred")
    firebase_admin.initialize_app(credentials.ApplicationDefault())
    return firestore.client()


def existing_ids(db, collection: str) -> set[str]:
    return {ref.id for ref in db.collection(collection).list_documents()}


def existing_shard_ids(db, sub_ids: set[str]) -> dict[str, set[str]]:
    """Existing `shards/{n}` doc ids per subcategoryId, used to find orphans to delete."""
    return {
        sid: {
            ref.id
            for ref in (db.collection("subcategories")
                          .document(sid)
                          .collection("shards")
                          .list_documents())
        }
        for sid in sub_ids
    }


def upsert(db, collection: str, docs: list[dict], *, overwrite: bool, dry_run: bool):
    """Upsert flat-collection docs keyed on their 'id' field. Returns (written, skipped)."""
    present: set[str] = set()
    if not overwrite or dry_run:
        present = existing_ids(db, collection)

    to_write = [d for d in docs if overwrite or d["id"] not in present]
    skipped = len(docs) - len(to_write)

    if dry_run:
        return len(to_write), skipped

    written = 0
    batch = db.batch()
    n = 0
    for d in to_write:
        payload = {k: v for k, v in d.items() if k != "id"}
        batch.set(db.collection(collection).document(d["id"]), payload)
        n += 1
        written += 1
        if n >= BATCH_LIMIT:
            batch.commit()
            batch = db.batch()
            n = 0
    if n:
        batch.commit()
    return written, skipped


def upsert_categories(db, categories: list[dict], *, overwrite: bool, dry_run: bool):
    """Bespoke category write path — see module docstring for why this isn't the generic
    upsert(). Every category is always written (merge=True), but `iconSvg`/`color` are dropped
    from the payload whenever Firestore already holds a non-empty value for that field, so a
    manually-curated or previously-seeded value is never clobbered. Returns (written, skipped).
    """
    written = 0
    for category in categories:
        doc_ref = db.collection("categories").document(category["id"])
        # get() then set() below are not atomic: a console edit to iconSvg/color landing in this
        # window would get clobbered. Accepted risk — this is a solo, manually-run pipeline, not
        # a concurrent/scheduled one; revisit with db.transaction() if that ever changes.
        existing = {} if overwrite else (doc_ref.get().to_dict() or {})
        payload = {k: v for k, v in category.items() if k != "id"}
        for sticky_field in ("iconSvg", "color"):
            if not overwrite and existing.get(sticky_field) and sticky_field in payload:
                del payload[sticky_field]
        if not dry_run:
            doc_ref.set(payload, merge=True)
        written += 1
    return written, 0


def upsert_shards(db, shards: list[dict], all_subcategory_ids: set[str], *, dry_run: bool):
    """Upsert subcategories/{subcategoryId}/shards/{n} docs.

    subcategoryId is a fixture routing field — stripped from the Firestore payload. Unlike
    upsert()/upsert_categories(), there is no --skip-existing/--overwrite distinction here: shard
    membership is pure derived data (ADR-0037), so every run writes every shard doc in full. Any
    existing shard doc whose index falls outside the freshly-packed set for its subcategory is
    deleted — otherwise a subcategory that shrinks, or repacks its cards across a different
    number of shards, would leave stale card content sitting in an orphaned doc.

    `all_subcategory_ids` (the fixture's full subcategory id set, not just the ones with shards)
    drives orphan scanning — a subcategory that now has zero cards contributes no shards at all,
    so deriving sub_ids from `shards` alone would skip it entirely and leave its stale shards
    (from before it emptied out) live and readable forever.

    Returns (written, deleted).
    """
    existing = existing_shard_ids(db, all_subcategory_ids) if all_subcategory_ids else {}

    new_ids_by_sub: dict[str, set[str]] = defaultdict(set)
    for s in shards:
        new_ids_by_sub[s["subcategoryId"]].add(s["id"])

    to_delete = [
        (sid, shard_id)
        for sid, existing_ids in existing.items()
        for shard_id in existing_ids - new_ids_by_sub.get(sid, set())
    ]

    if dry_run:
        return len(shards), len(to_delete)

    written = 0
    batch = db.batch()
    n = 0
    batch_bytes = 0
    for s in shards:
        payload = {k: v for k, v in s.items() if k not in ("id", "subcategoryId")}
        # Shard docs run up to SHARD_BYTE_BUDGET (~700KB) each, unlike the tiny per-doc payloads
        # BATCH_LIMIT was sized for — a handful of them can already blow past Firestore's ~10MiB
        # commit-request cap, so flush on byte budget too, not just doc count.
        size = len(json.dumps(payload, ensure_ascii=False).encode("utf-8"))
        if n and batch_bytes + size > SHARD_BATCH_BYTE_LIMIT:
            batch.commit()
            batch = db.batch()
            n = 0
            batch_bytes = 0
        ref = (db.collection("subcategories")
               .document(s["subcategoryId"])
               .collection("shards")
               .document(s["id"]))
        batch.set(ref, payload)
        n += 1
        batch_bytes += size
        written += 1
        if n >= BATCH_LIMIT:
            batch.commit()
            batch = db.batch()
            n = 0
            batch_bytes = 0
    for sid, shard_id in to_delete:
        ref = (db.collection("subcategories")
               .document(sid)
               .collection("shards")
               .document(shard_id))
        batch.delete(ref)
        n += 1
        if n >= BATCH_LIMIT:
            batch.commit()
            batch = db.batch()
            n = 0
            batch_bytes = 0
    if n:
        batch.commit()
    return written, len(to_delete)


def refresh_card_counts(db, subcategories: list[dict], *, dry_run: bool) -> int:
    """Refresh `cardCount` on every subcategory doc, independent of upsert()'s --skip-existing
    default above. `shards` are always fully rewritten on every run (ADR-0037) — `cardCount` must
    track that same content or it silently drifts from reality once a subcategory's card count
    changes after its doc already exists (see PR #51 review). `order`/`name`/`nameLower` are left
    alone here; unlike `cardCount`, `order` may be hand-adjusted in the Firebase console per this
    file's docstring, so this only merge-writes the one field known to be pure derived data.
    Returns the number of subcategories refreshed (all of them — this is not conditional).
    """
    if dry_run:
        return len(subcategories)

    written = 0
    batch = db.batch()
    n = 0
    for sub in subcategories:
        ref = db.collection("subcategories").document(sub["id"])
        batch.set(ref, {"cardCount": sub["cardCount"]}, merge=True)
        n += 1
        written += 1
        if n >= BATCH_LIMIT:
            batch.commit()
            batch = db.batch()
            n = 0
    if n:
        batch.commit()
    return written


def write_onboarding_subcategories(db, doc: dict, *, dry_run: bool) -> int:
    """Overwrites the singleton `onboarding/subcategories` document in full every run — same
    get-then-set write shape as `bump_cache_seed`'s `meta/seed` doc (same accepted non-atomic risk:
    a solo, manually-run pipeline). Unlike `categories.iconSvg`/`color`, nothing in this doc is
    ever hand-curated directly in Firestore, so there is no sticky-field check to make: the fixture
    is the single source of truth and a reseed always fully replaces it. Returns the number of
    curated entries written (or, under `--dry-run`, the number that would have been written).
    """
    if not dry_run:
        db.collection("onboarding").document("subcategories").set(doc)
    return len(doc.get("subcategories", {}))


def bump_cache_seed(db, *, dry_run: bool) -> int:
    """Increments `meta/seed.value` by 1, creating the doc at 1 if absent (ADR-0039). Must be
    called only after every other write in this run has committed — see the module docstring —
    so a run that fails partway through never bumps the seed for content that did not land.
    Returns the value written (or, under `--dry-run`, the value that would have been written).

    get() then set() below are not atomic — same accepted risk as upsert_categories()'s sticky-field
    check: a solo, manually-run pipeline, not a concurrent/scheduled one. Two overlapping runs could
    each read the same value and net only one increment. Revisit with db.transaction() if that ever
    changes.

    `value` is validated as an int before the increment: a corrupt or hand-edited doc (e.g.
    `{"value": null}`) must fail loudly here rather than raise a bare TypeError after every other
    write in the run has already committed.
    """
    ref = db.collection("meta").document("seed")
    current = ref.get().to_dict() or {}
    current_value = current.get("value", 0)
    if not isinstance(current_value, int):
        raise ValueError(f"meta/seed.value is {current_value!r}, expected an int")
    next_value = current_value + 1
    if not dry_run:
        ref.set({"value": next_value})
    return next_value


def main():
    ap = argparse.ArgumentParser(description="Upsert temp fixtures into Firestore.")
    ap.add_argument("--fixtures-dir", default=DEFAULT_FIXTURES,
                    help=f"dir of *.json fixtures (default {DEFAULT_FIXTURES})")
    ap.add_argument("--cred", help="path to service-account JSON (else GOOGLE_APPLICATION_CREDENTIALS)")
    mode = ap.add_mutually_exclusive_group()
    mode.add_argument("--skip-existing", action="store_true", default=True,
                      help="write only absent docs (default)")
    mode.add_argument("--overwrite", action="store_true",
                      help="set() every doc unconditionally")
    ap.add_argument("--dry-run", action="store_true", help="report only, no writes")
    args = ap.parse_args()
    overwrite = bool(args.overwrite)

    paths, categories, subcategories, onboarding_subcategories_doc, shards = load_fixtures(args.fixtures_dir)
    total_cards = sum(len(s["flashcards"]) for s in shards)
    print(f"fixtures: {', '.join(os.path.relpath(p) for p in paths)}")
    print(f"  categories={len(categories)} subcategories={len(subcategories)} "
          f"onboardingSubcategories={len(onboarding_subcategories_doc['subcategories'])} "
          f"shards={len(shards)} cards={total_cards}")
    print(f"mode: {'OVERWRITE' if overwrite else 'skip-existing'} (shards always overwrite)"
          f"{' (DRY-RUN)' if args.dry_run else ''}\n")

    db = init_db(args.cred)

    cw, cs = upsert_categories(db, categories, overwrite=overwrite, dry_run=args.dry_run)
    sw, ss = upsert(db, "subcategories", subcategories, overwrite=overwrite, dry_run=args.dry_run)
    cc = refresh_card_counts(db, subcategories, dry_run=args.dry_run)
    hw, hd = upsert_shards(db, shards, {s["id"] for s in subcategories}, dry_run=args.dry_run)
    oc = write_onboarding_subcategories(db, onboarding_subcategories_doc, dry_run=args.dry_run)
    # Last write of the run, deliberately — see bump_cache_seed's docstring.
    seed_value = bump_cache_seed(db, dry_run=args.dry_run)

    verb = "would write" if args.dry_run else "wrote"
    del_verb = "would delete" if args.dry_run else "deleted"
    print(f"categories/                    {verb} {cw}, skipped {cs}")
    print(f"subcategories/                 {verb} {sw}, skipped {ss}")
    print(f"subcategories/.cardCount       refreshed {cc}")
    print(f"subcategories/*/shards/        {verb} {hw}, {del_verb} {hd} orphaned")
    print(f"onboarding/subcategories       {verb} 1 doc, {oc} entries")
    print(f"meta/seed                      {verb} value={seed_value}")
    if args.dry_run:
        print("\n(dry-run — nothing written)")


if __name__ == "__main__":
    main()
