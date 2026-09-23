#!/usr/bin/env bash
# Builds the current, possibly-uncommitted working tree as a debug or profiling
# APK and uploads it straight to Firebase App Distribution's "wip-<variant>"
# group -- dev-only convenience path, no CI, no static analysis, no code review.
#
# Variants:
#   debug      (default) debuggable build, the everyday WIP check
#   profiling  R8-optimized, non-debuggable build for judging real performance;
#              same package and Firebase app as debug, so it installs over it
#
# Fails fast: runs a Kotlin-compile-only pass first so a broken mid-edit
# tree doesn't waste time on a full assemble.
#
# Standalone-runnable: bare terminal, cron, or the distribute-wip skill --
# same invocation. All build/upload output goes to stdout, and the very last
# line is always a plain-English outcome:
#   SUCCESS: wip-<variant> build <timestamp> uploaded
#   FAILED: <stage> -- see output above
# A caller (skill or human) reads that one line; there's no separate
# status file or logfile to go looking for.
#
# Never reads firebase-app-distribution-service-account.json -- only points
# GOOGLE_APPLICATION_CREDENTIALS at its path.
#
# Usage:
#   scripts/distribute-wip.sh [--variant debug|profiling]

set -euo pipefail

fail() {
  local stage="$1"
  echo "FAILED: $stage -- see output above" >&2
  exit 1
}

VARIANT="debug"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --variant)
      VARIANT="${2:-}"
      shift 2 || { echo "--variant needs a value (debug|profiling)" >&2; fail "preflight"; }
      ;;
    *)
      echo "unknown argument: $1 (usage: scripts/distribute-wip.sh [--variant debug|profiling])" >&2
      fail "preflight"
      ;;
  esac
done

case "$VARIANT" in
  debug)
    VARIANT_TASK_NAME="Debug"
    ;;
  profiling)
    VARIANT_TASK_NAME="Profiling"
    ;;
  *)
    echo "unknown variant: $VARIANT (expected debug or profiling)" >&2
    fail "preflight"
    ;;
esac

SRC="$(git rev-parse --show-toplevel)"
cd "$SRC"

# Both variants use the .debug package, so both upload to the debug app (same as deploy-internal).
FIREBASE_APP_ID="1:1044553396320:android:f113802dc50e178f445305"
GROUP="wip-${VARIANT}"
SERVICE_ACCOUNT="$SRC/firebase-app-distribution-service-account.json"
APK_PATH="$SRC/app/build/outputs/apk/${VARIANT}/app-${VARIANT}.apk"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

if ! command -v firebase >/dev/null 2>&1; then
  echo "firebase CLI not installed (npm i -g firebase-tools)" >&2
  fail "preflight"
fi

if [[ ! -f "$SERVICE_ACCOUNT" ]]; then
  echo "missing $SERVICE_ACCOUNT (copy-worktree-secrets.sh should have placed it)" >&2
  fail "preflight"
fi

echo "== compile check (compile${VARIANT_TASK_NAME}Kotlin) =="
if ! ./gradlew "compile${VARIANT_TASK_NAME}Kotlin"; then
  fail "compile"
fi

echo "== assembling ${VARIANT} APK (dirty tree) =="
if ! ./gradlew "assemble${VARIANT_TASK_NAME}"; then
  fail "assemble"
fi

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found at $APK_PATH" >&2
  fail "assemble"
fi

BRANCH_NAME="$(git rev-parse --abbrev-ref HEAD)"
BUILT_FROM="built from ${BRANCH_NAME}"
if [[ -n "$(git status --porcelain)" ]]; then
  BUILT_FROM="${BUILT_FROM} with uncommitted changes"
fi

RELEASE_NOTES="wip ${VARIANT} build ${TIMESTAMP}
$(git rev-parse --short HEAD)
${BUILT_FROM}"

echo "== uploading to Firebase App Distribution (${GROUP}) =="
if ! GOOGLE_APPLICATION_CREDENTIALS="$SERVICE_ACCOUNT" firebase appdistribution:distribute \
  "$APK_PATH" \
  --app "$FIREBASE_APP_ID" \
  --groups "$GROUP" \
  --release-notes "$RELEASE_NOTES"; then
  fail "upload"
fi

echo "SUCCESS: ${GROUP} build ${TIMESTAMP} uploaded"
