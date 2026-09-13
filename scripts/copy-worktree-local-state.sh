#!/usr/bin/env bash
# Copies/links gitignored local state -- secrets (local.properties,
# google-services.json, service-account/cred JSONs, signing keystores) and
# the graphify-out/ knowledge graph -- from this checkout into a freshly
# created worktree, so the app builds there and graphify queries work
# without re-fetching credentials or rebuilding the graph by hand.
#
# Usage:
#   scripts/copy-worktree-local-state.sh <path-to-worktree>
#   scripts/copy-worktree-local-state.sh -f <path-to-worktree>   # overwrite existing files
#
# Never prints secret file contents -- only paths copied/skipped/linked. Safe
# to run from an agent session; it does not require reading secret contents.
#
# graphify-out/ is symlinked (absolute path), not copied -- one shared graph
# across all worktrees, kept current via `graphify update .` from whichever
# worktree you're using. If the destination already has a real graphify-out/
# directory (not a symlink), this refuses to touch it even with -f -- that's
# 144M+ of that worktree's own graph state, not a copyable secret.

set -euo pipefail

FORCE=0
if [[ "${1:-}" == "-f" ]]; then
  FORCE=1
  shift
fi

DST="${1:-}"
if [[ -z "$DST" ]]; then
  echo "usage: $0 [-f] <path-to-worktree>" >&2
  exit 1
fi

SRC="$(git rev-parse --show-toplevel)"
DST="$(cd "$DST" 2>/dev/null && pwd || true)"
if [[ -z "$DST" ]]; then
  echo "error: destination worktree path does not exist: ${1}" >&2
  exit 1
fi
if [[ "$DST" == "$SRC" ]]; then
  echo "error: destination is same as source checkout ($SRC)" >&2
  exit 1
fi

copy_one() {
  local rel="$1"
  local src_file="$SRC/$rel"
  local dst_file="$DST/$rel"
  [[ -f "$src_file" ]] || return 0
  if [[ -f "$dst_file" && "$FORCE" -ne 1 ]]; then
    echo "skip (exists): $rel"
    return 0
  fi
  mkdir -p "$(dirname "$dst_file")"
  cp "$src_file" "$dst_file"
  echo "copied: $rel"
}

# --- fixed gitignored files ---
copy_one "local.properties"
copy_one "app/google-services.json"

# --- pattern-matched gitignored files (service-account / cred JSONs) ---
while IFS= read -r -d '' f; do
  rel="${f#"$SRC"/}"
  copy_one "$rel"
done < <(find "$SRC" \( -path "$SRC/.git" -o -name ".gradle" -o -name "build" \) -prune -o \
  \( -name "*service-account*.json" -o -name "*.cred.json" \) -print0)

# --- keystore files referenced from local.properties ---
if [[ -f "$SRC/local.properties" ]]; then
  for key in DEBUG_STORE_FILE RELEASE_STORE_FILE; do
    value="$(grep -E "^${key}=" "$SRC/local.properties" 2>/dev/null | head -1 | cut -d'=' -f2- || true)"
    [[ -z "$value" ]] && continue
    if [[ "$value" = /* ]]; then
      # absolute path: copy alongside itself outside the repo tree is not our
      # job -- just report it so the human/agent knows it must exist at DST too.
      echo "note: $key points outside repo ($value) -- verify it's reachable from $DST too"
    else
      copy_one "$value"
    fi
  done
fi

# --- shared graphify knowledge graph (symlink, not copy) ---
link_graphify() {
  local src_dir="$SRC/graphify-out"
  local dst_dir="$DST/graphify-out"
  [[ -d "$src_dir" ]] || return 0

  if [[ -e "$dst_dir" || -L "$dst_dir" ]]; then
    if [[ -L "$dst_dir" ]]; then
      local current_target
      current_target="$(readlink "$dst_dir")"
      if [[ "$current_target" == "$src_dir" ]]; then
        echo "skip (already linked): graphify-out"
        return 0
      fi
      if [[ "$FORCE" -eq 1 ]]; then
        rm "$dst_dir"
        ln -s "$src_dir" "$dst_dir"
        echo "relinked: graphify-out -> $src_dir"
        return 0
      fi
      echo "skip (linked elsewhere, use -f to relink): graphify-out -> $current_target"
      return 0
    fi
    echo "refuse: graphify-out exists as a real directory at $dst_dir -- not touching it (even with -f). Resolve manually."
    return 0
  fi

  ln -s "$src_dir" "$dst_dir"
  echo "linked: graphify-out -> $src_dir"
}
link_graphify

echo "done."
