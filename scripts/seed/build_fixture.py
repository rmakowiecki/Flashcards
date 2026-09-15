#!/usr/bin/env python3
"""Stage 1 of Firestore seeding: capture inboxes -> local temp fixture JSON.

Reads the migrated capture inboxes (`~/.claude/flashcards/<cat>/<sub>/inbox.jsonl`),
projects each card into the Firestore shape, derives the taxonomy from the slugs,
and writes a single fixture file to a gitignored temp dir (default `scripts/seed/.tmp/fixture.json`).

The fixture is intentionally NOT committed — it is a throwaway hand-off to
`seed_firestore.py`. Re-run any time; output is deterministic.

Schema (see ADR-0007, ADR-0037):
  categories/{categoryId}                        → { name, order, subcategoryCount, iconSvg, color,
                                                     featuredSubcategoryNames[] }
  subcategories/{categoryId-subSlug}             → { name, nameLower, categoryId, categoryName,
                                                     order, cardCount }
  subcategories/{categoryId-subSlug}/shards/{n}  → { flashcards: { "<cardId>": { id, question,
                                                     answer, tags[], createdAt, ... }, ... } }
  onboarding/subcategories                       → { subcategories: { "{categoryId-subSlug}": {
                                                     order, categoryId, categoryName, subcategoryId,
                                                     subcategoryName, iconSvg }, ... } } — one
                                                     document, denormalized+trimmed for the
                                                     admin-curated id allowlist in
                                                     curated_onboarding_topics.py (publicly readable,
                                                     see docs/design/firestore-schema.md)

Cards are packed into byte-budgeted shard docs instead of one Firestore document per card
(ADR-0037) — each Subcategory's cards are sorted by id, then greedily accumulated into a shard
until the next card would cross SHARD_BYTE_BUDGET, at which point a new shard starts. Shard
membership is pure derived data: fully recomputed every run, never hand-curated, never sticky.
`flashcards` is a map keyed by card id, not an array, so a future admin curation-fix tool can
patch one card's field via a Firestore dot-path update without touching any other card in the
shard — see ADR-0037.

Subcategory IDs are namespaced "{categoryId}-{subSlug}" (e.g. "android-compose") to guarantee
uniqueness across parent categories in the flat subcategories/ collection.

Projection (inbox field -> Firestore field):
  question/answer                -> question/answer
  difficulty                     -> difficulty                   (mandatory — hard-fail if absent)
  question_code/answer_code      -> questionCode/answerCode      (omitted if absent)
  question_spoken/answer_spoken  -> questionSpoken/answerSpoken  (omitted if absent)
  extended_context               -> extendedContext              (omitted if absent)
  tags[]                         -> tags[]
  created_at                     -> createdAt
  id                             -> doc id (idempotency key)
Routing: subcategoryId is carried in the fixture as a path-routing field only —
         it is NOT written as a Firestore document field (encoded in the collection path).
Dropped: subcategoryId field on card, source (PII), status (capture lifecycle).
"""
from __future__ import annotations
import argparse, glob, json, os, random, sys
from collections import Counter, defaultdict
from curated_onboarding_topics import CURATED_ONBOARDING_SUBCATEGORY_IDS

DEFAULT_SRC = os.path.expanduser("~/.claude/flashcards")
DEFAULT_OUT = os.path.join(os.path.dirname(__file__), ".tmp", "fixture.json")
DEFAULT_ICON_ASSETS = os.path.join(os.path.dirname(__file__), "assets", "category-icons")

# How many Subcategory display names each Category denormalizes into `featuredSubcategoryNames`, the
# field behind the Browse screen's topic-summary chip line. Five is what the chip line can hold
# before width truncation; see docs/design/category-search.md.
TOP_SUBCATEGORY_NAMES_LIMIT = 5

# Target ceiling per shard doc, in UTF-8 bytes of its serialized `flashcards[]` payload. Kept
# well under Firestore's 1 MiB/doc hard cap — see ADR-0037 for the `android/compose` sizing
# check (1180 cards, ~1.5MB raw, needs multiple shards) that ruled out a flat "one doc per
# subcategory" rule.
SHARD_BYTE_BUDGET = 700_000

# Display-name overrides for slugs that do not titlecase nicely. Admin can extend.
NAME_OVERRIDES = {
    "ui": "UI",
    "aaos": "AAOS",
    "claude-code": "Claude Code",
    "android-features": "Android Features",
    "dependency-injection": "Dependency Injection",
    "app-distribution": "App Distribution",
    "build-system": "Build System",
}

# Icon/color cannot be derived from a slug like NAME_OVERRIDES can. Unlike NAME_OVERRIDES, these
# are NOT hand-maintained Python dict literals — they're real, versioned files under
# scripts/seed/assets/category-icons/{slug}.svg (plain SVG, rendered via androidsvg on-device) and
# {slug}.color (plain-text hex string), one pair per category slug. Both fields are optional on
# Category (nullable, see
# docs/design/category-icon-color.md), so a missing file is a warning, not a build failure — the
# seed pipeline can legitimately auto-create a category before its icon/color art exists.


def read_icon_asset(slug: str, ext: str, icon_assets_dir: str) -> str | None:
    path = os.path.join(icon_assets_dir, f"{slug}.{ext}")
    if not os.path.isfile(path):
        print(f"warning: no {path} — category '{slug}' will omit its {ext} field", file=sys.stderr)
        return None
    with open(path, encoding="utf-8") as f:
        content = f.read().strip()
    if not content:
        print(f"warning: {path} is empty — category '{slug}' will omit its {ext} field", file=sys.stderr)
        return None
    return content


def display_name(slug: str) -> str:
    if slug in NAME_OVERRIDES:
        return NAME_OVERRIDES[slug]
    return slug.replace("-", " ").replace("_", " ").title()


def make_sub_id(cat_slug: str, sub_slug: str) -> str:
    return f"{cat_slug}-{sub_slug}"


def assert_valid_shard_map_key(card_id: str) -> None:
    """Card ids double as Firestore map keys in each shard's `flashcards` field (ADR-0037), and
    as the key targeted by a future admin dot-path patch (`flashcards.<id>.<field>`). `.` would be
    misread as a nesting separator in that dot-path, `/` is invalid in a map key, and a leading
    `__` collides with Firestore's own reserved field convention.
    """
    if "." in card_id or "/" in card_id or card_id.startswith("__"):
        sys.exit(f"card id {card_id!r} is not a valid shard map key (no '.', '/', or leading '__')")


def project_card(d: dict, subcategory_id: str) -> dict:
    if "difficulty" not in d:
        sys.exit(f"card {d.get('id')} is missing mandatory field 'difficulty'")
    if not isinstance(d["difficulty"], int) or not (1 <= d["difficulty"] <= 10):
        sys.exit(f"card {d.get('id')} has invalid difficulty {d['difficulty']!r} (must be int 1–10)")
    assert_valid_shard_map_key(d["id"])
    out = {
        "id": d["id"],
        "subcategoryId": subcategory_id,  # fixture routing only — stripped before Firestore write
        "question": d["question"],
        "answer": d["answer"],
        "tags": d.get("tags") or [],
        "createdAt": d.get("created_at"),
        "difficulty": d["difficulty"],
    }
    # optional additive fields — omit entirely when absent (never write null)
    for src, dst in (
        ("question_code", "questionCode"),
        ("answer_code", "answerCode"),
        ("question_spoken", "questionSpoken"),
        ("answer_spoken", "answerSpoken"),
        ("extended_context", "extendedContext"),
    ):
        if d.get(src):
            out[dst] = d[src]
    return out


def card_byte_size(card: dict) -> int:
    return len(json.dumps(card, ensure_ascii=False).encode("utf-8"))


def pack_shards(cards: list[dict], budget: int = SHARD_BYTE_BUDGET) -> list[dict]:
    """Bin-pack cards into byte-budgeted shard docs, one bin-pack run per subcategoryId (ADR-0037).

    Cards are grouped by their `subcategoryId` routing field, sorted by id for deterministic
    output, then greedily accumulated into a shard until the next card would cross `budget`.
    Each shard's `flashcards` is a map keyed by card id (not an array) — ADR-0037 chose this
    specifically so a future admin curation-fix tool can patch one card's field via a Firestore
    dot-path update (`update({f"flashcards.{cardId}.answer": fix})`) without touching any other
    card in the same shard; Firestore has no equivalent partial-update into one array element.
    `id` is also kept inside each value as a cross-check against its own map key — the two must
    always agree, since the key is authoritative for any targeted access.

    Each card's contribution to the budget is measured as its whole `{cardId: payload}` map
    entry — not just the raw payload — so the key itself and its wrapping counts too. A card
    whose entry alone exceeds `budget` can never be packed into any shard (with or without
    company) and fails loudly here instead of surfacing later as a cryptic Firestore upload
    error against the 1MiB/doc hard cap.

    Returns a flat list of shard dicts: {"id": "<shardIndex>", "subcategoryId": ..., "flashcards": {cardId: {...}, ...}},
    where `subcategoryId` is routing-only (stripped before the Firestore write, same convention
    as a card's own `subcategoryId` in `project_card`).
    """
    by_sub: dict[str, list[dict]] = defaultdict(list)
    for card in cards:
        by_sub[card["subcategoryId"]].append(card)

    shards: list[dict] = []
    for sid in sorted(by_sub):
        sub_cards = sorted(by_sub[sid], key=lambda c: c["id"])
        current: dict[str, dict] = {}
        current_bytes = 0
        shard_index = 0
        for card in sub_cards:
            payload = {k: v for k, v in card.items() if k != "subcategoryId"}
            entry_size = card_byte_size({card["id"]: payload})
            if entry_size > budget:
                sys.exit(
                    f"card {card['id']!r} is {entry_size} bytes on its own, over the "
                    f"{budget}-byte shard budget — cannot be packed into any shard"
                )
            if current and current_bytes + entry_size > budget:
                shards.append({"id": str(shard_index), "subcategoryId": sid, "flashcards": current})
                shard_index += 1
                current = {}
                current_bytes = 0
            current[card["id"]] = payload
            current_bytes += entry_size
        if current:
            shards.append({"id": str(shard_index), "subcategoryId": sid, "flashcards": current})
    return shards


def build(
    src_root: str,
    sample: int | None = None,
    seed: int | None = None,
    icon_assets_dir: str = DEFAULT_ICON_ASSETS,
):
    files = sorted(glob.glob(os.path.join(src_root, "*", "*", "inbox.jsonl")))
    if not files:
        sys.exit(f"no inbox files under {src_root}")

    cards: list[dict] = []
    cat_counts: Counter = Counter()
    sub_counts: Counter = Counter()
    sub_parent: dict[str, str] = {}     # namespaced sub_id -> cat_slug
    sub_display: dict[str, str] = {}    # namespaced sub_id -> bare sub_slug (for display_name)
    seen_ids: set[str] = set()

    for path in files:
        parts = path.split(os.sep)
        cat_slug, sub_slug = parts[-3], parts[-2]
        sid = make_sub_id(cat_slug, sub_slug)
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                d = json.loads(line)
                # integrity: card's own fields must agree with its directory
                if d.get("category") != cat_slug or d.get("subcategory") != sub_slug:
                    sys.exit(f"card {d.get('id')} fields ({d.get('category')}/"
                             f"{d.get('subcategory')}) disagree with path {cat_slug}/{sub_slug}")
                cid = d["id"]
                if cid in seen_ids:
                    sys.exit(f"duplicate card id across corpus: {cid}")
                seen_ids.add(cid)
                sub_parent[sid] = cat_slug
                sub_display[sid] = sub_slug
                cat_counts[cat_slug] += 1
                sub_counts[sid] += 1
                cards.append(project_card(d, sid))

    if sample is not None:
        rng = random.Random(seed)
        by_sub: dict[str, list[dict]] = defaultdict(list)
        for card in cards:
            by_sub[card["subcategoryId"]].append(card)
        sampled: list[dict] = []
        for sub_cards in by_sub.values():
            sampled.extend(sub_cards if len(sub_cards) <= sample else rng.sample(sub_cards, sample))
        cards = sampled
        cat_counts = Counter()
        sub_counts = Counter()
        for card in cards:
            sid = card["subcategoryId"]
            sub_counts[sid] += 1
            cat_counts[sub_parent[sid]] += 1

    # subcategory count per category (for subcategoryCount field)
    cat_sub_counts: Counter = Counter(sub_parent.values())

    # Subcategory ids per parent, ranked by card volume desc then id. One prominence ranking feeds
    # two fields: each Subcategory's own `order`, and its parent's `featuredSubcategoryNames` prefix.
    by_parent: dict[str, list[str]] = defaultdict(list)
    for sid, parent in sub_parent.items():
        by_parent[parent].append(sid)
    for parent in by_parent:
        by_parent[parent].sort(key=lambda s: (-sub_counts[s], s))

    # categories: ordered by card volume desc, then slug
    categories = []
    for order, slug in enumerate(sorted(cat_counts, key=lambda s: (-cat_counts[s], s))):
        category = {
            "id": slug,
            "name": display_name(slug),
            "order": order,
            "subcategoryCount": cat_sub_counts[slug],
            # Pure derived data with no manual curation, so unlike iconSvg/color this is NOT a
            # sticky field — every reseed overwrites it as card counts shift the ranking.
            "featuredSubcategoryNames": [
                display_name(sub_display[sid])
                for sid in by_parent[slug][:TOP_SUBCATEGORY_NAMES_LIMIT]
            ],
        }
        icon_svg = read_icon_asset(slug, "svg", icon_assets_dir)
        if icon_svg is not None:
            category["iconSvg"] = icon_svg
        color = read_icon_asset(slug, "color", icon_assets_dir)
        if color is not None:
            category["color"] = color
        categories.append(category)

    # subcategories: flat collection, namespaced id, ordered per-parent by card volume
    subcategories = []
    for parent in sorted(by_parent):
        for order, sid in enumerate(by_parent[parent]):
            name = display_name(sub_display[sid])
            subcategories.append({
                "id": sid,
                "name": name,
                # Exists only to make Firestore's case-sensitive prefix-range search queries
                # case-insensitive — never surfaces in the app's domain model.
                "nameLower": name.lower(),
                "categoryId": parent,
                "categoryName": display_name(parent),
                "order": order,
                "cardCount": sub_counts[sid],
            })

    # onboarding/subcategories: a small, admin-curated subset of `subcategories`
    # (curated_onboarding_topics.py, docs/design/firestore-schema.md), denormalized and trimmed
    # down to exactly what onboarding needs, packed into ONE document's `subcategories` map so the
    # whole curated list costs a single Firestore read. `iconSvg` is joined in from each entry's
    # parent category — onboarding never reads `categories` itself. `order` is this curated list's
    # own fresh sequential order, not each subcategory's order within its real parent category. A
    # curated id that no longer exists in the built taxonomy fails the run loudly rather than
    # silently seeding a shorter list than intended.
    subcategories_by_id = {sub["id"]: sub for sub in subcategories}
    categories_by_id = {category["id"]: category for category in categories}
    missing_curated_ids = [
        sid for sid in CURATED_ONBOARDING_SUBCATEGORY_IDS if sid not in subcategories_by_id
    ]
    if missing_curated_ids:
        sys.exit(
            "curated_onboarding_topics.py lists subcategory ids that no longer exist in the "
            f"built taxonomy: {missing_curated_ids} — update the allowlist"
        )
    onboarding_subcategories_doc = {
        "subcategories": {
            sid: {
                "order": order,
                "categoryId": subcategories_by_id[sid]["categoryId"],
                "categoryName": subcategories_by_id[sid]["categoryName"],
                "subcategoryId": sid,
                "subcategoryName": subcategories_by_id[sid]["name"],
                "iconSvg": categories_by_id[subcategories_by_id[sid]["categoryId"]].get("iconSvg"),
            }
            for order, sid in enumerate(CURATED_ONBOARDING_SUBCATEGORY_IDS)
        }
    }

    shards = pack_shards(cards)

    return {
        "categories": categories,
        "subcategories": subcategories,
        "onboardingSubcategories": onboarding_subcategories_doc,
        "shards": shards,
    }, cat_counts, sub_counts


def main():
    ap = argparse.ArgumentParser(description="Build a temp Firestore seed fixture from capture inboxes.")
    ap.add_argument("--src", default=DEFAULT_SRC, help=f"inbox root (default {DEFAULT_SRC})")
    ap.add_argument("--out", default=DEFAULT_OUT, help=f"fixture output path (default {DEFAULT_OUT})")
    ap.add_argument("--sample", type=int, default=None, metavar="N",
                    help="keep at most N cards per subcategory (random sample)")
    ap.add_argument("--seed", type=int, default=None,
                    help="random seed for reproducible sampling (only with --sample)")
    ap.add_argument("--icon-assets", default=DEFAULT_ICON_ASSETS,
                    help=f"dir of {{slug}}.svg/{{slug}}.color files (default {DEFAULT_ICON_ASSETS})")
    args = ap.parse_args()

    fixture, cat_counts, sub_counts = build(
        args.src, sample=args.sample, seed=args.seed, icon_assets_dir=args.icon_assets
    )
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(fixture, f, ensure_ascii=False, indent=2)
        f.write("\n")

    total_cards = sum(cat_counts.values())
    print(f"wrote {args.out}")
    print(f"  categories:    {len(fixture['categories'])}")
    print(f"  subcategories: {len(fixture['subcategories'])}")
    print(f"  onboarding:    {len(fixture['onboardingSubcategories']['subcategories'])}")
    print(f"  cards:         {total_cards}")
    print(f"  shards:        {len(fixture['shards'])}"
          + (f" ({total_cards / len(fixture['shards']):.1f} cards/shard avg)" if fixture['shards'] else ""))
    print("  cards per category: " +
          ", ".join(f"{c}={cat_counts[c]}" for c in sorted(cat_counts)))
    if args.sample is not None:
        suffix = f", seed={args.seed}" if args.seed is not None else ", seed=random"
        print(f"  (sampled: up to {args.sample} per subcategory{suffix})")


if __name__ == "__main__":
    main()
