#!/usr/bin/env bash
# Builds the current, possibly-uncommitted working tree as a debug APK and
# uploads it straight to Firebase App Distribution's "wip-debug" group --
# dev-only convenience path, no CI, no static analysis, no code review.
#
# Fails fast: runs a Kotlin-compile-only pass first so a broken mid-edit
# tree doesn't waste time on a full assemble.
#
# Standalone-runnable: bare terminal, cron, or the distribute-wip-debug
# skill -- same invocation, zero args. All build/upload output goes to
# stdout, and the very last line is always a plain-English outcome:
#   SUCCESS: wip-debug build <timestamp> uploaded
#   FAILED: <stage> -- see output above
# A caller (skill or human) reads that one line; there's no separate
# status file or logfile to go looking for.
#
# Never reads firebase-app-distribution-service-account.json -- only points
# GOOGLE_APPLICATION_CREDENTIALS at its path.
#
# Usage:
#   scripts/distribute-wip-debug.sh

set -euo pipefail

SRC="$(git rev-parse --show-toplevel)"
cd "$SRC"

FIREBASE_APP_ID="1:1044553396320:android:f113802dc50e178f445305" # debug app, same as deploy-internal
GROUP="wip-debug"
SERVICE_ACCOUNT="$SRC/firebase-app-distribution-service-account.json"
APK_PATH="$SRC/app/build/outputs/apk/debug/app-debug.apk"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

fail() {
  local stage="$1"
  echo "FAILED: $stage -- see output above" >&2
  exit 1
}

if ! command -v firebase >/dev/null 2>&1; then
  echo "firebase CLI not installed (npm i -g firebase-tools)" >&2
  fail "preflight"
fi

if [[ ! -f "$SERVICE_ACCOUNT" ]]; then
  echo "missing $SERVICE_ACCOUNT (copy-worktree-secrets.sh should have placed it)" >&2
  fail "preflight"
fi

echo "== compile check (compileDebugKotlin) =="
if ! ./gradlew compileDebugKotlin; then
  fail "compile"
fi

echo "== assembling debug APK (dirty tree) =="
if ! ./gradlew assembleDebug; then
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

RELEASE_NOTES="wip build ${TIMESTAMP}
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

echo "SUCCESS: wip-debug build ${TIMESTAMP} uploaded"
