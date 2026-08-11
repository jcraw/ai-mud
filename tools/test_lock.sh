#!/usr/bin/env bash
# tools/test_lock.sh — SHA-256 manifest lock for tracked */src/test/** files
# Ticket: MUD-012. Fail-closed anti test-gaming gate for verify_mud.
# See docs/TEST_LOCK.md
#
# Usage:
#   ./tools/test_lock.sh              # same as --check
#   ./tools/test_lock.sh --check      # rehash vs baseline; exit 1 on drift
#   ./tools/test_lock.sh --write      # rewrite baseline (requires allow env)
#
# Allow env for --write (either):
#   MUD_ALLOW_TEST_CHANGES=1
#   ALLOW_TEST_CHANGES=1
#
# Exit codes:
#   0  check green / write ok
#   1  lock violation, missing baseline, write refused, or usage error

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

BASELINE="${ROOT_DIR}/tools/test-lock/manifest.sha256"
MODE="check"

usage() {
  cat <<'EOF'
Usage: ./tools/test_lock.sh [--check|--write] [-h|--help]

  --check   Rehash tracked */src/test/** vs tools/test-lock/manifest.sha256
            (default). Fails on content drift, new tracked test paths,
            missing baseline paths, untracked src/test porcelain, or
            missing baseline file.
  --write   Rewrite baseline from current tracked test files.
            Requires MUD_ALLOW_TEST_CHANGES=1 or ALLOW_TEST_CHANGES=1.
  -h|--help This help

Manifest format: sha256sum-style lines "HASH  path" (sorted by path).
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

# Prefer sha256sum; fall back to shasum -a 256 (macOS).
hash_file() {
  local path="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    # sha256sum prints "HASH  path" (two spaces for text mode)
    sha256sum -- "${path}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 -- "${path}" | awk '{print $1}'
  else
    die "neither sha256sum nor shasum found"
  fi
}

# List tracked files under any module's src/test tree (repo-relative paths).
list_tracked_test_files() {
  # git ls-files pathspec: match src/test anywhere under the repo
  git -C "${ROOT_DIR}" ls-files -- '*/src/test/*' 'src/test/*' | LC_ALL=C sort -u
}

# Untracked (and ignored-excluded) porcelain paths matching src/test/
list_untracked_test_paths() {
  # porcelain: XY path  (or "?? path" for untracked)
  # Also handle "?? path with spaces" and rename "R  old -> new" lightly:
  # we only care about untracked (??) and added-untracked-ish.
  git -C "${ROOT_DIR}" status --porcelain -u --untracked-files=all 2>/dev/null \
    | awk '
      # untracked
      /^\?\?/ {
        line = $0
        sub(/^\?\? /, "", line)
        # strip surrounding quotes if git quoted the path
        if (line ~ /^".*"$/) {
          gsub(/^"/, "", line)
          gsub(/"$/, "", line)
          gsub(/\\"/, "\"", line)
          gsub(/\\n/, "\n", line)
        }
        if (line ~ /(^|\/)src\/test(\/|$)/) print line
      }
    ' | LC_ALL=C sort -u
}

allow_test_changes() {
  [[ "${MUD_ALLOW_TEST_CHANGES:-}" == "1" || "${ALLOW_TEST_CHANGES:-}" == "1" ]]
}

write_manifest() {
  if ! allow_test_changes; then
    echo "error: --write refused: set MUD_ALLOW_TEST_CHANGES=1 or ALLOW_TEST_CHANGES=1" >&2
    echo "hint: only when a ticket explicitly authorizes test edits; then commit the new baseline." >&2
    exit 1
  fi

  mkdir -p "$(dirname "${BASELINE}")"
  local tmp
  tmp="$(mktemp)"
  local count=0
  local path hash
  while IFS= read -r path; do
    [[ -z "${path}" ]] && continue
    if [[ ! -f "${ROOT_DIR}/${path}" ]]; then
      # rare: deleted but still listed mid-race; skip for write of current tree
      continue
    fi
    hash="$(hash_file "${ROOT_DIR}/${path}")"
    # two spaces: sha256sum text-mode convention
    printf '%s  %s\n' "${hash}" "${path}" >> "${tmp}"
    count=$((count + 1))
  done < <(list_tracked_test_files)

  # already sorted by list_tracked_test_files
  mv "${tmp}" "${BASELINE}"
  echo "wrote ${BASELINE} (${count} files)"
}

check_manifest() {
  if [[ ! -f "${BASELINE}" ]]; then
    echo "error: test-lock baseline missing: tools/test-lock/manifest.sha256" >&2
    echo "hint: authorized regen: MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write" >&2
    exit 1
  fi

  local -a failures=()
  local path hash expected actual

  # 1) Untracked src/test paths — agents cannot bypass by leaving tests untracked
  local untracked
  untracked="$(list_untracked_test_paths || true)"
  if [[ -n "${untracked}" ]]; then
    while IFS= read -r path; do
      [[ -z "${path}" ]] && continue
      failures+=("UNTRACKED test path (git add + regen baseline, or remove): ${path}")
    done <<< "${untracked}"
  fi

  # 2) Load baseline into assoc map path -> hash
  declare -A baseline_hash=()
  declare -A baseline_seen=()
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" ]] && continue
    [[ "${line}" =~ ^# ]] && continue
    # Format: HASH  path  (two spaces preferred; tolerate one+)
    hash="${line%% *}"
    path="${line#"${hash}"}"
    path="${path#"${path%%[![:space:]]*}"}"  # ltrim
    [[ -z "${hash}" || -z "${path}" ]] && continue
    baseline_hash["${path}"]="${hash}"
    baseline_seen["${path}"]=0
  done < "${BASELINE}"

  # 3) Current tracked test files
  declare -A current=()
  while IFS= read -r path; do
    [[ -z "${path}" ]] && continue
    current["${path}"]=1
    if [[ ! -f "${ROOT_DIR}/${path}" ]]; then
      failures+=("MISSING on disk (listed by git): ${path}")
      continue
    fi
    actual="$(hash_file "${ROOT_DIR}/${path}")"
    if [[ -z "${baseline_hash[${path}]+x}" ]]; then
      failures+=("NEW tracked test file (not in baseline): ${path}")
    else
      expected="${baseline_hash[${path}]}"
      baseline_seen["${path}"]=1
      if [[ "${actual}" != "${expected}" ]]; then
        failures+=("CONTENT changed: ${path}")
      fi
    fi
  done < <(list_tracked_test_files)

  # 4) Paths in baseline but no longer tracked / present
  for path in "${!baseline_hash[@]}"; do
    if [[ -z "${current[${path}]+x}" ]]; then
      failures+=("REMOVED from git index (still in baseline): ${path}")
    fi
  done

  if [[ ${#failures[@]} -gt 0 ]]; then
    echo "error: test-file lock violated (${#failures[@]} issue(s)):" >&2
    local f
    for f in "${failures[@]}"; do
      echo "  - ${f}" >&2
    done
    echo "hint: authorized test edits only. Regen: MUD_ALLOW_TEST_CHANGES=1 ./tools/test_lock.sh --write" >&2
    echo "docs: docs/TEST_LOCK.md" >&2
    exit 1
  fi

  local n
  n="$(wc -l < "${BASELINE}" | tr -d ' ')"
  echo "test-lock OK (${n} files match tools/test-lock/manifest.sha256)"
}

# --- arg parse ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --check)
      MODE="check"
      shift
      ;;
    --write)
      MODE="write"
      shift
      ;;
    *)
      die "unknown arg: $1 (try --help)"
      ;;
  esac
done

case "${MODE}" in
  check) check_manifest ;;
  write) write_manifest ;;
  *) die "internal: bad mode ${MODE}" ;;
esac
