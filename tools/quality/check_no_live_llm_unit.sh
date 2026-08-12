#!/usr/bin/env bash
# tools/quality/check_no_live_llm_unit.sh — static gate: no live OpenAI in unit tests
# Ticket: MUD-032 (Wave Q2 · B2). Fail-closed static rg scan of */src/test/**/*.kt.
# See docs/NO_LIVE_LLM_UNIT.md
#
# Usage:
#   ./tools/quality/check_no_live_llm_unit.sh           # scan repo; exit 0/1
#   ./tools/quality/check_no_live_llm_unit.sh --help
#
# Forbidden under scanned unit tests (non-testbot):
#   1) \bOpenAIClient\s*\(   → LIVE_LLM_OPENAI_CLIENT  (real client construction)
#   2) OPENAI_API_KEY        → LIVE_LLM_API_KEY
#   3) openai.api.key        → LIVE_LLM_API_KEY
#
# Hard-exclude: testbot/** (integration/behavior lane; live LLM by design)
# Allowlist: config/quality/no_live_llm_unit_allowlist.txt (empty v1)
#
# Exit codes:
#   0  clean (no hits outside exclude/allowlist)
#   1  hits found, or rg/script prerequisites missing (fail-closed)
#
# Machine-oriented hit lines (stdout, when hits):
#   path:line: CODE  matched_snippet
# Human summary on stdout after hits or PASS line when clean.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${ROOT_DIR}"

ALLOWLIST_FILE="${ROOT_DIR}/config/quality/no_live_llm_unit_allowlist.txt"

usage() {
  cat <<'EOF'
Usage: ./tools/quality/check_no_live_llm_unit.sh [--help]

  Scan */src/test/**/*.kt for live OpenAI / API-key load patterns.
  Hard-excludes testbot/**. Optional allowlist:
    config/quality/no_live_llm_unit_allowlist.txt

  Exit 0 if clean; exit 1 on any hit or missing rg.

  Forbidden:
    OpenAIClient(          construction of real client
    OPENAI_API_KEY         env key load
    openai.api.key         properties key load

  OK (not matched): OpenAIResponse fixtures, MockLLMClient : LLMClient, etc.
  Standalone from repo root. Wired into verify_mud as gate no_live_llm_unit.
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi
if [[ $# -gt 0 ]]; then
  echo "error: unknown arg: $1" >&2
  usage >&2
  exit 1
fi

if ! command -v rg >/dev/null 2>&1; then
  die "rg (ripgrep) not found — required for no_live_llm_unit gate (fail-closed)"
fi

# Load allowlist: first whitespace-delimited field per non-comment line.
# shellcheck disable=SC2207
ALLOW_PATHS=()
if [[ -f "${ALLOWLIST_FILE}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    # strip CR
    line="${line//$'\r'/}"
    # skip blank / full-line comments
    [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
    # drop inline comment after path
    entry="${line%%#*}"
    # trim
    entry="$(printf '%s' "${entry}" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
    [[ -z "${entry}" ]] && continue
    ALLOW_PATHS+=("${entry}")
  done < "${ALLOWLIST_FILE}"
fi

path_allowlisted() {
  local rel="$1"
  local pat
  for pat in "${ALLOW_PATHS[@]+"${ALLOW_PATHS[@]}"}"; do
    # exact path
    if [[ "${rel}" == "${pat}" ]]; then
      return 0
    fi
    # simple trailing /** or /* glob (prefix match)
    if [[ "${pat}" == */** ]]; then
      local pref="${pat%/\*\*}"
      if [[ "${rel}" == "${pref}"/* || "${rel}" == "${pref}" ]]; then
        return 0
      fi
    elif [[ "${pat}" == *\* ]]; then
      # bash pathname expansion style: convert * to *
      case "${rel}" in
        ${pat}) return 0 ;;
      esac
    fi
  done
  return 1
}

# Collect hits as "path:line: CODE  snippet" (dedupe path:line:CODE).
declare -a HIT_LINES=()
declare -A HIT_SEEN=()

append_hit() {
  local rel="$1"
  local lineno="$2"
  local code="$3"
  local snippet="$4"
  local key="${rel}:${lineno}:${code}"

  if path_allowlisted "${rel}"; then
    return 0
  fi
  if [[ -n "${HIT_SEEN[$key]:-}" ]]; then
    return 0
  fi
  HIT_SEEN[$key]=1
  # collapse internal whitespace in snippet for one-line report
  snippet="$(printf '%s' "${snippet}" | tr '\t' ' ' | sed -e 's/  */ /g' -e 's/^ //' -e 's/ $//')"
  HIT_LINES+=("${rel}:${lineno}: ${code}  ${snippet}")
}

# Scan one regex; map to finding code. Paths from rg are repo-relative when -g used from ROOT.
# Exclude: testbot tree always. Also skip build/.git via glob defaults (src/test only).
run_pattern() {
  local pattern="$1"
  local code="$2"
  local line rel_path lineno rest content

  # --glob order: include test kt, exclude testbot (and common junk)
  # Use --no-heading -n for path:line:content
  while IFS= read -r line || [[ -n "${line}" ]]; do
    [[ -z "${line}" ]] && continue
    # path:line:content — path may contain ':' rarely; take first two fields carefully
    rel_path="${line%%:*}"
    rest="${line#*:}"
    lineno="${rest%%:*}"
    content="${rest#*:}"
    # strip leading space from content after :
    content="${content# }"

    # hard-exclude testbot (defense in depth if glob missed)
    case "${rel_path}" in
      testbot/*|*/testbot/*) continue ;;
    esac

    # only unit-test trees
    case "${rel_path}" in
      */src/test/*|src/test/*) ;;
      *) continue ;;
    esac

    append_hit "${rel_path}" "${lineno}" "${code}" "${content}"
  done < <(
    rg -n --no-heading \
      --glob '**/src/test/**/*.kt' \
      --glob '!testbot/**' \
      --glob '!**/testbot/**' \
      -e "${pattern}" \
      "${ROOT_DIR}" 2>/dev/null \
      | sed "s|^${ROOT_DIR}/||" \
      || true
  )
}

# 1) Real client construction (not type name alone)
run_pattern '\bOpenAIClient\s*\(' 'LIVE_LLM_OPENAI_CLIENT'
# 2) Env / properties key load
run_pattern 'OPENAI_API_KEY' 'LIVE_LLM_API_KEY'
run_pattern 'openai\.api\.key' 'LIVE_LLM_API_KEY'

hit_count=${#HIT_LINES[@]}

if [[ "${hit_count}" -eq 0 ]]; then
  echo "no_live_llm_unit: PASS (0 hits; testbot/** excluded)"
  exit 0
fi

echo "no_live_llm_unit: FAIL (${hit_count} hit(s) in unit tests)"
echo "Forbidden: OpenAIClient( construction, OPENAI_API_KEY, openai.api.key under */src/test/** (non-testbot)."
echo "Use MockLLMClient / frozen OpenAIResponse fixtures. See docs/NO_LIVE_LLM_UNIT.md"
echo ""
for h in "${HIT_LINES[@]}"; do
  echo "${h}"
done
echo ""
echo "hint: temporary carve-out → config/quality/no_live_llm_unit_allowlist.txt (ticket id + reason)"
exit 1
