"""Writes the server-owned XP configuration document, `config/xp`, from the shared default file.

`testdata/xp-scoring/default-xp-config.json` at the repo root holds every XpConfig field with its
bundled default; both the Cloud Functions and the Android client test that their own bundled defaults
equal it. This script copies that file into Firestore as-is, validated first with the same rules the
`submitStudySession` function applies when it reads the document (`functions/src/lib/xpConfig.ts`), so
a bad file never reaches Firestore.

Idempotent: every run overwrites the whole document. After a console edit to the live rates, rerunning
this script resets them to the file's defaults.

    export GOOGLE_APPLICATION_CREDENTIALS=/abs/path/service-account.json
    python3 seed_xp_config.py --dry-run      # validate and print the document, no writes
    python3 seed_xp_config.py                # overwrite config/xp
    python3 seed_xp_config.py --cred /abs/path/service-account.json

With `FIRESTORE_EMULATOR_HOST` set, the script writes to that emulator instead and needs no
credentials (pass `--project` to pick the emulator project id).
"""
from __future__ import annotations
import argparse, json, math, os, sys

DEFAULT_CONFIG_FILE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "testdata", "xp-scoring", "default-xp-config.json"
)
COLLECTION = "config"
DOCUMENT = "xp"

# Mirrors functions/src/lib/xpConfig.ts: award fields and the two single-number curve fields are whole
# numbers in Int range, and the step list holds positive whole numbers in Int range.
INTEGER_FIELDS = (
    "newCardStudied", "cardMastered", "cardPartial", "masteryDefended", "cardDemastered",
    "sessionCompleted", "dailyGoalMet", "streakPerDay", "streakMaxPerDay",
)
CURVE_INTEGER_FIELDS = ("levelBaseCost", "levelsPerCostStep")
STEPS_FIELD = "levelCostSteps"
ALL_FIELDS = INTEGER_FIELDS + CURVE_INTEGER_FIELDS + (STEPS_FIELD,)
# The Android client holds award fields as Kotlin `Int`, so an award outside this range is one it cannot load.
INT_MIN, INT_MAX = -2**31, 2**31 - 1


def is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def validate(config: dict) -> list[str]:
    """Returns every problem with `config`; empty when it is a valid XP configuration."""
    problems = []
    for field in INTEGER_FIELDS + CURVE_INTEGER_FIELDS:
        value = config.get(field)
        if not is_number(value):
            problems.append(f"{field} must be a finite number, got {value!r}")
        elif value != int(value):
            problems.append(f"{field} must be an integer, got {value!r}")
        elif not INT_MIN <= value <= INT_MAX:
            problems.append(f"{field} must be between {INT_MIN} and {INT_MAX}, got {value!r}")
    steps = config.get(STEPS_FIELD)
    if not isinstance(steps, list) or not steps:
        problems.append(f"{STEPS_FIELD} must be a nonempty list, got {steps!r}")
    else:
        for step in steps:
            if not is_number(step) or step != int(step) or not 0 < step <= INT_MAX:
                problems.append(f"{STEPS_FIELD} must hold positive integers up to {INT_MAX}, got {step!r}")
    if not problems:
        if config["cardDemastered"] > 0:
            problems.append(f"cardDemastered must be zero or negative, got {config['cardDemastered']!r}")
        if config["levelBaseCost"] <= 0:
            problems.append(f"levelBaseCost must be positive, got {config['levelBaseCost']!r}")
        if config["levelsPerCostStep"] <= 0:
            problems.append(f"levelsPerCostStep must be positive, got {config['levelsPerCostStep']!r}")
    unknown = sorted(set(config) - set(ALL_FIELDS))
    if unknown:
        problems.append(f"unknown fields: {', '.join(unknown)}")
    return problems


def load_config(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        config = json.load(f)
    problems = validate(config)
    if problems:
        sys.exit(f"{path} is not a valid XP configuration:\n  " + "\n  ".join(problems))
    # Whole numbers as ints, so Firestore stores integers regardless of how the JSON spelled them.
    return {
        **{field: int(config[field]) for field in INTEGER_FIELDS + CURVE_INTEGER_FIELDS},
        STEPS_FIELD: [int(step) for step in config[STEPS_FIELD]],
    }


def init_db(cred_path: str | None, project: str | None):
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
    except ImportError:
        sys.exit("firebase-admin not installed — `pip install -r requirements.txt`")
    if os.environ.get("FIRESTORE_EMULATOR_HOST"):
        from google.auth.credentials import AnonymousCredentials
        from google.cloud import firestore as cloud_firestore
        return cloud_firestore.Client(project=project or "demo-flashcards", credentials=AnonymousCredentials())
    if cred_path:
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = cred_path
    if not os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"):
        sys.exit("no credentials: set GOOGLE_APPLICATION_CREDENTIALS or pass --cred")
    firebase_admin.initialize_app(credentials.ApplicationDefault())
    return firestore.client()


def main():
    ap = argparse.ArgumentParser(description="Overwrite config/xp from the shared default XP configuration file.")
    ap.add_argument("--config-file", default=DEFAULT_CONFIG_FILE,
                    help="XP configuration JSON (default: the shared default configuration file)")
    ap.add_argument("--cred", help="path to service-account JSON (else GOOGLE_APPLICATION_CREDENTIALS)")
    ap.add_argument("--project", help="project id, only used with FIRESTORE_EMULATOR_HOST")
    ap.add_argument("--dry-run", action="store_true", help="validate and print the document, no writes")
    args = ap.parse_args()

    document = load_config(args.config_file)
    print(f"{COLLECTION}/{DOCUMENT} from {os.path.relpath(args.config_file)}:")
    print(json.dumps(document, indent=2))
    if args.dry_run:
        print("\n(dry-run — nothing written)")
        return

    db = init_db(args.cred, args.project)
    db.collection(COLLECTION).document(DOCUMENT).set(document)
    print(f"\nwrote {COLLECTION}/{DOCUMENT}")


if __name__ == "__main__":
    main()
