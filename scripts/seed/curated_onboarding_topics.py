"""Admin-curated allowlist of Subcategory ids surfaced on onboarding's Favorites step.

Single source of truth for the single `onboarding/subcategories` Firestore document (see
docs/design/firestore-schema.md) — a small, publicly-readable, denormalized subset of the real
`subcategories` collection. `build_fixture.py` cross-references this list against the
subcategories it builds, trims each to onboarding's fields, and joins in its parent category's
`iconSvg`; it fails loudly if an id here no longer exists in the real taxonomy, rather than
silently seeding a shorter list than intended.

Admin can extend/edit this list directly — plain data, ids only, no other file needs to change.
Ids follow the real namespaced scheme ("{categoryId}-{subSlug}", e.g. "android-compose"), not the
bare slugs onboarding's old hardcoded sample list used.
"""
from __future__ import annotations

CURATED_ONBOARDING_SUBCATEGORY_IDS: list[str] = [
    "android-app-fundamentals",
    "android-permissions",
    "android-build-system",
    "kotlin-control-flow-and-exceptions",
    "kotlin-collections-operations",
    "kotlin-syntax-and-idioms",
    "python-syntax-and-idioms",
    "python-core-types",
    "python-cli-and-os-services",
    "scientific-python-jupyter",
    "scientific-python-fundamentals",
    "scientific-python-hugging-face-transformers",
    "swift-dates-and-time",
    "swift-formatting-and-units",
    "swift-numbers-and-values",
]
