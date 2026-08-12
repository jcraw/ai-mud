#!/usr/bin/env bash
# tools/verify_mud.sh — thin Gradle lane wrapper for AI MUD
# Ticket: MUD-004. Honest defaults; no full-green theater.
# Detekt (MUD-010) + Konsist arch (MUD-011) + test-lock (MUD-012) are real on
# default/fast/core/full. PIT (MUD-014): --pitest lane only (core measured >45s → not on full).
# MUD-013: per-gate status + durations → tmp/dod-summary.json (or $MUD_DOD_SUMMARY).
# MUD-027: schema_version 2 + findings[] (empty until MUD-028+); post-write validate.
# MUD-031: token/structure hard-on-touched default on default/fast/core/full (scoped
#   git-diff *_E). Soft opt-out: MUD_TOKEN_SOFT=1 or --token-soft. MUD_TOKEN_HARD /
#   --token-hard still accepted (redundant hard). Full-repo scope always soft.
#   Skip quarantine/pitest. Checker always exit 0; verify owns hard policy.
# MUD-032: no_live_llm_unit hard on default/fast/core/full/pitest (static rg; skip
#   quarantine). Fail-closed if checker/rg missing. See docs/NO_LIVE_LLM_UNIT.md.
# MUD-033: optional --preflight PATH (builder plan/brief token only; not on default
#   lanes). Checker exit 2 → fail; exit 1 warn → pass+note; 0 → pass.
#   See docs/BUILDER_PREFLIGHT.md.
# fast ≡ default (bare = compile smoke + hard gates; no auto --core suite).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${ROOT_DIR}"

GRADLEW="${ROOT_DIR}/gradlew"
TEST_LOCK="${ROOT_DIR}/tools/test_lock.sh"
NO_LIVE_LLM_CHECKER="${ROOT_DIR}/tools/quality/check_no_live_llm_unit.sh"
TOKEN_CHECKER="${ROOT_DIR}/tools/quality/check_token_budget_kt.py"
TOKEN_JSON_OUT="${ROOT_DIR}/tmp/token_budget_kt_verify.json"
PREFLIGHT_CHECKER="${ROOT_DIR}/tools/quality/check_builder_preflight.py"
DOD_SUMMARY_PATH="${MUD_DOD_SUMMARY:-${ROOT_DIR}/tmp/dod-summary.json}"
LANE="default"
DRY_RUN=0
# MUD-031: hard-on-touched default. Soft opt-out via --token-soft / MUD_TOKEN_SOFT.
# --token-hard / MUD_TOKEN_HARD remain accepted (redundant hard).
TOKEN_SOFT_CLI=0
TOKEN_HARD_CLI=0
# MUD-033: set by --preflight PATH (required); LANE becomes preflight.
PREFLIGHT_PATH=""
MODULES=()
NOTES=()
STEPS=()
EXIT_CODE=0
VERIFY_STARTED=0
DOD_WRITTEN=0
SCRIPT_START_S="$(date +%s)"

# Soft mutation threshold (day-one). Hard fail only when MUD_PITEST_HARD=1 or -Pmud.pitestHard=true.
PITEST_SOFT_THRESHOLD=60
# Measured 2026-08-11: :core:pitest wall ~130s (PIT analysis ~125s) → full never runs PIT.
# Nightly / local deep gate: --pitest only. See docs/PIT.md.
PITEST_IN_FULL_LANE=0

# Cap token findings merged into dod-summary (MUD-030/031).
TOKEN_FINDINGS_CAP=50
# Cap live-LLM unit findings merged into dod-summary (MUD-032).
NO_LIVE_LLM_FINDINGS_CAP=50

# Gate records (MUD-013): status pass|fail|skipped, wall-clock seconds, optional note
declare -A GATE_SEEN=()
declare -A GATE_STATUS=()
declare -A GATE_DURATION=()
declare -A GATE_NOTE=()
# Optional numeric mutation score (min of modules) when pitest gate ran (MUD-014).
GATE_MUTATION_SCORE=""

# Findings rows for dod-summary v2 (MUD-027 / MUD-030 token merge).
# Each element is a full JSON object string (no trailing commas).
FINDINGS_JSON_PARTS=()

usage() {
  cat <<'EOF'
Usage: ./tools/verify_mud.sh [lane|flag] [module…] [--dry-run] [--token-soft] [--token-hard]
                             | --preflight <path>

Lanes (pick one; default if omitted):
  default | fast | --fast     fast ≡ default. Compile smoke: :core:compileKotlin
                              With module args: :<m>:compileKotlin (+ :<m>:test if src/test exists)
                              Then hard detekt + Konsist arch + test-lock + no_live_llm_unit
                              + token (hard-on-touched). Bare run does NOT auto-run --core/--full.
                              PIT never runs (use --pitest).
  core    | --core            :core:test :perception:test :memory:test :reasoning:test
                              (default excludeTags quarantine; honest green)
                              + detekt + Konsist arch + test-lock + no_live_llm_unit
                              + token (hard-on-touched)
                              PIT never runs (ticket drain stays free of PIT wall-time).
  full    | --full            Stable green set: core/perception/memory/reasoning tests +
                              compile-only action/llm/config. Default excludeTags quarantine.
                              + detekt + Konsist arch + test-lock + no_live_llm_unit
                              + token (hard-on-touched)
                              PIT: skipped (core PIT >45s); use --pitest nightly — docs/PIT.md
  pitest  | --pitest          PIT mutation on pure modules only:
                              :core:pitest :perception:pitest :memory:pitest
                              + detekt + Konsist arch + test-lock + no_live_llm_unit
                              Soft 60% (pass + note if below); hard fail if MUD_PITEST_HARD=1
                              Token budget: skipped (not in pitest lane).
  quarantine | --quarantine   :reasoning:test -Pmud.quarantineOnly=true (known debt; hard-fail OK)
                              (no detekt / no Konsist / no test-lock / no no_live_llm_unit /
                               no PIT / no token — debt only)
  --preflight <path>          Builder plan/brief token only (MUD-033). Required path.
                              Not on default/fast/core/full/pitest/quarantine.
                              Checker exit 2 → fail; 1 warn → pass+note; 0 → pass.
                              See docs/BUILDER_PREFLIGHT.md.

Flags:
  --dry-run                   Print intended Gradle commands; do not run
  --token-soft                Soft token gate (same as MUD_TOKEN_SOFT=1): report-only;
                              never fail verify from token alone (escape hatch).
  --token-hard                Force hard token gate (same as MUD_TOKEN_HARD=1); redundant
                              under MUD-031 default hard-on-touched (still accepted).
  -h | --help                 This help

DoD summary (MUD-013 / MUD-027 v2 / MUD-031 / MUD-032):
  Always writes compact JSON (pass/fail/skipped per gate, durations, quarantine_count)
  to tmp/dod-summary.json (override with MUD_DOD_SUMMARY). schema_version 2 + findings[]
  (token/structure + live-LLM rows when run; see docs/DOD_SUMMARY.md).
  gates.token_budget on default/fast/core/full (skipped quarantine/pitest).
  gates.no_live_llm_unit on default/fast/core/full/pitest (skipped quarantine).
  Post-write light shape validation (hard fail if invalid). Human == verify_mud == kept.
  When --pitest runs: gates.pitest.mutation_score = min of three modules.

Token budget (MUD-031 hard-on-touched; docs/TOKEN_BUDGET_KT.md):
  Hard default on default/fast/core/full: --git-diff vs MUD_TOKEN_GIT_BASE (origin/master);
  fail closed on *_E in that touch set (never full-repo hard). *_W never hard-fails.
  Soft opt-out: MUD_TOKEN_SOFT=1 or --token-soft (report-only → findings[]).
  MUD_TOKEN_HARD=1 / --token-hard still accepted (redundant hard under default).
  MUD_TOKEN_SCOPE=full: soft full-repo inventory only; hard+full forces scoped + note.
  Overrides in config require burn-down ticket; new/Added files cannot use overrides;
  override caps may only lower over time. Quarantine and pitest skip token.
  Checker always exit 0; verify owns hard policy.

No live LLM in unit tests (MUD-032; docs/NO_LIVE_LLM_UNIT.md):
  Hard static rg gate on default/fast/core/full/pitest: forbids OpenAIClient(, OPENAI_API_KEY,
  openai.api.key under */src/test/**/*.kt. Hard-excludes testbot/**. Empty allowlist v1.
  Fail-closed if checker or rg missing. Skip quarantine.

Builder preflight (MUD-033; docs/BUILDER_PREFLIGHT.md):
  Optional only via --preflight <path>. Plan 2k/3.5k, brief 1.2k/2k tok (ceil chars/4).
  Standalone: python3 tools/quality/check_builder_preflight.py <path>
  Not forced on default/fast/core/full (historical plans often warn-band).

Exit codes:
  0  all hard steps green (or dry-run)
  1  usage / unknown lane, or a hard step failed

Examples:
  ./tools/verify_mud.sh
  ./tools/verify_mud.sh --fast
  ./tools/verify_mud.sh --core
  ./tools/verify_mud.sh default perception
  ./tools/verify_mud.sh --full --dry-run
  ./tools/verify_mud.sh --pitest
  ./tools/verify_mud.sh --dry-run --pitest
  ./tools/verify_mud.sh --quarantine
  ./tools/verify_mud.sh --preflight plans/YYYY-MM-DD-….md
  ./tools/verify_mud.sh --preflight plans/….md --dry-run
  MUD_TOKEN_SOFT=1 ./tools/verify_mud.sh --fast
  ./tools/verify_mud.sh --core --token-soft
  MUD_TOKEN_SCOPE=full ./tools/verify_mud.sh --fast

Requires Java 17 and ./gradlew at repo root.
See docs/TEST_LOCK.md for unauthorized src/test edit policy.
See docs/PIT.md for mutation testing (pure modules).
See docs/TOKEN_BUDGET_KT.md for token/structure hard-on-touched.
See docs/NO_LIVE_LLM_UNIT.md for unit-test live-LLM policy.
See docs/BUILDER_PREFLIGHT.md for plan/brief token preflight.
EOF
}

die_usage() {
  echo "error: $*" >&2
  usage >&2
  EXIT_CODE=1
  exit 1
}

note() {
  NOTES+=("$*")
}

add_step() {
  STEPS+=("$*")
}

# Aggregate gate: sum durations; first fail wins; keep first non-empty note.
record_gate() {
  local name="$1"
  local status="$2"
  local duration="${3:-0}"
  local gnote="${4:-}"

  if [[ -n "${GATE_SEEN[$name]:-}" ]]; then
    GATE_DURATION[$name]=$(( ${GATE_DURATION[$name]:-0} + duration ))
    if [[ "${GATE_STATUS[$name]}" != "fail" && "${status}" == "fail" ]]; then
      GATE_STATUS[$name]="fail"
    elif [[ "${GATE_STATUS[$name]}" == "skipped" && "${status}" == "pass" ]]; then
      GATE_STATUS[$name]="pass"
    fi
    if [[ -z "${GATE_NOTE[$name]:-}" && -n "${gnote}" ]]; then
      GATE_NOTE[$name]="${gnote}"
    fi
  else
    GATE_SEEN[$name]=1
    GATE_STATUS[$name]="${status}"
    GATE_DURATION[$name]="${duration}"
    GATE_NOTE[$name]="${gnote}"
  fi
}

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  s="${s//$'\n'/\\n}"
  s="${s//$'\r'/\\r}"
  s="${s//$'\t'/\\t}"
  printf '%s' "${s}"
}

# Append one finding object for dod-summary findings[] (MUD-027; used by MUD-028+).
# Usage: append_finding CODE PATH METRIC LIMIT REMEDIATION
# METRIC/LIMIT: number string or empty/null → JSON null.
append_finding() {
  local code="$1"
  local fpath="$2"
  local metric="${3:-}"
  local limit="${4:-}"
  local remediation="${5:-}"
  local metric_json limit_json

  if [[ -z "${metric}" || "${metric}" == "null" ]]; then
    metric_json="null"
  else
    metric_json="${metric}"
  fi
  if [[ -z "${limit}" || "${limit}" == "null" ]]; then
    limit_json="null"
  else
    limit_json="${limit}"
  fi

  FINDINGS_JSON_PARTS+=(
    "$(printf '{ "code": "%s", "path": "%s", "metric": %s, "limit": %s, "remediation": "%s" }' \
      "$(json_escape "${code}")" \
      "$(json_escape "${fpath}")" \
      "${metric_json}" \
      "${limit_json}" \
      "$(json_escape "${remediation}")")"
  )
}

# Light post-write shape validation for dod-summary v2. Fail closed → EXIT_CODE=1.
# Prefer python3 stdlib json; bash fallback if python missing.
validate_dod_summary() {
  local path="$1"
  local ok=0
  local err=""

  if [[ ! -s "${path}" ]]; then
    EXIT_CODE=1
    note "dod-summary schema invalid (missing or empty: ${path})"
    return 1
  fi

  if command -v python3 >/dev/null 2>&1; then
    if err="$(python3 - "${path}" <<'PY'
import json, sys
path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
except Exception as e:
    print(f"json load: {e}")
    sys.exit(2)

if data.get("schema_version") != 2:
    print(f"schema_version want 2 got {data.get('schema_version')!r}")
    sys.exit(2)

gates = data.get("gates")
if not isinstance(gates, dict):
    print("gates must be object")
    sys.exit(2)

known = ("compile", "tests", "detekt", "konsist", "test_lock", "pitest")
ok_status = {"pass", "fail", "skipped"}
for g in known:
    if g not in gates:
        print(f"missing gate {g}")
        sys.exit(2)
    entry = gates[g]
    if not isinstance(entry, dict):
        print(f"gate {g} not object")
        sys.exit(2)
    st = entry.get("status")
    if st not in ok_status:
        print(f"gate {g} bad status {st!r}")
        sys.exit(2)
    dur = entry.get("duration_s")
    if not isinstance(dur, (int, float)) or isinstance(dur, bool):
        print(f"gate {g} bad duration_s {dur!r}")
        sys.exit(2)

findings = data.get("findings")
if not isinstance(findings, list):
    print("findings must be array")
    sys.exit(2)
for i, row in enumerate(findings):
    if not isinstance(row, dict):
        print(f"findings[{i}] not object")
        sys.exit(2)
    for key in ("code", "path", "remediation"):
        if not isinstance(row.get(key), str):
            print(f"findings[{i}].{key} must be string")
            sys.exit(2)
    for key in ("metric", "limit"):
        v = row.get(key)
        if v is not None and (not isinstance(v, (int, float)) or isinstance(v, bool)):
            print(f"findings[{i}].{key} must be number or null")
            sys.exit(2)
sys.exit(0)
PY
)"; then
      ok=1
    else
      EXIT_CODE=1
      note "dod-summary schema invalid${err:+: ${err}}"
      return 1
    fi
  else
    # Bash fallback: minimal presence checks (no full JSON parse)
    if grep -q '"schema_version": 2' "${path}" \
      && grep -q '"findings"' "${path}" \
      && grep -q '"gates"' "${path}"; then
      ok=1
    else
      EXIT_CODE=1
      note "dod-summary schema invalid (bash fallback; install python3 for full check)"
      return 1
    fi
  fi

  [[ "${ok}" -eq 1 ]] || return 1
  return 0
}

# Cheap quarantine count: live @Tag scan → doc fallback. Never runs quarantine suite.
count_quarantine_tags() {
  local n=""
  if command -v rg >/dev/null 2>&1; then
    n="$(rg --glob '*.kt' -c '@Tag\("quarantine"\)' "${ROOT_DIR}" 2>/dev/null \
      | awk -F: '{ s += $2 } END { print s + 0 }' || true)"
  else
    n="$(grep -r --include='*.kt' -c '@Tag("quarantine")' "${ROOT_DIR}" 2>/dev/null \
      | awk -F: '{ s += $2 } END { print s + 0 }' || true)"
  fi
  # Only fall back when scan produced nothing usable (empty), not when count is 0.
  if [[ -z "${n}" ]]; then
    if [[ -f "${ROOT_DIR}/docs/TEST_QUARANTINE.md" ]]; then
      n="$(sed -n 's/.*Quarantine count:[[:space:]]*\*\*\([0-9][0-9]*\)\*\*.*/\1/p' \
        "${ROOT_DIR}/docs/TEST_QUARANTINE.md" | head -n1 || true)"
    fi
  fi
  printf '%s' "${n}"
}

# Run a hard gradle step under a named gate (or print in dry-run). Failures set EXIT_CODE.
# Usage: run_gradle <gate> <gradle-args...>
run_gradle() {
  local gate="$1"
  shift
  local -a args=("$@")
  local cmd_display="./gradlew ${args[*]}"
  local t0 t1 dur rc

  add_step "${cmd_display}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    return 0
  fi
  if [[ ! -x "${GRADLEW}" ]]; then
    echo "error: gradlew not found or not executable at ${GRADLEW}" >&2
    EXIT_CODE=1
    record_gate "${gate}" "fail" 0 "gradlew missing"
    return 1
  fi
  echo ">> ${cmd_display}"
  t0="$(date +%s)"
  set +e
  "${GRADLEW}" "${args[@]}"
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))
  if [[ ${rc} -ne 0 ]]; then
    EXIT_CODE=1
    record_gate "${gate}" "fail" "${dur}"
    return 1
  fi
  record_gate "${gate}" "pass" "${dur}"
  return 0
}

# Parse mutation coverage % from a module PIT report (timestampedReports=false path).
# Prefers mutations.xml detected counts (stable); HTML second coverage_legend is Mutation Coverage
# (first=line, second=mutation, third=test strength) as fallback.
# Prints one decimal on stdout; empty on failure.
parse_pitest_module_score() {
  local mod="$1"
  local report_dir="${ROOT_DIR}/${mod}/build/reports/pitest"
  local html="${report_dir}/index.html"
  local xml="${report_dir}/mutations.xml"
  local score="" killed total

  if [[ -f "${xml}" ]]; then
    # XML attributes use single quotes: detected='true'
    total="$(grep -c '<mutation ' "${xml}" 2>/dev/null || true)"
    killed="$(grep -c "detected='true'" "${xml}" 2>/dev/null || true)"
    total="${total:-0}"
    killed="${killed:-0}"
    if [[ "${total}" -gt 0 ]]; then
      score="$(awk -v k="${killed}" -v t="${total}" 'BEGIN { printf "%.1f", (k * 100.0) / t }')"
    fi
  fi

  if [[ -z "${score}" && -f "${html}" ]]; then
    # Second coverage_legend is Mutation Coverage (killed/total).
    score="$(
      sed -n 's/.*coverage_legend">\([0-9][0-9]*\)\/\([0-9][0-9]*\)<.*/\1 \2/p' "${html}" 2>/dev/null \
        | sed -n '2p' \
        | {
            read -r killed total || true
            if [[ -n "${killed:-}" && -n "${total:-}" && "${total}" -gt 0 ]]; then
              awk -v k="${killed}" -v t="${total}" 'BEGIN { printf "%.1f", (k * 100.0) / t }'
            fi
          }
    )"
  fi

  printf '%s' "${score}"
}

# True when hard mutation threshold is requested (env or -Pmud.pitestHard=true in env GRADLE_OPTS not required).
pitest_hard_mode() {
  if [[ "${MUD_PITEST_HARD:-0}" == "1" || "${MUD_PITEST_HARD:-}" == "true" ]]; then
    return 0
  fi
  # Gradle-style property passed through env for CI convenience
  if [[ "${MUD_PITEST_HARD:-}" == "yes" ]]; then
    return 0
  fi
  # Also honor project property if present in shell env from caller
  if [[ "${mud_pitestHard:-}" == "true" ]]; then
    return 0
  fi
  return 1
}

# Run pure-module PIT (:core :perception :memory). Fail-closed on task error / unparseable / 0 mutations.
# Soft 60%: pass + note if min score below; hard mode (MUD_PITEST_HARD=1) fails if min < 60.
run_pitest() {
  local cmd_display="./gradlew :core:pitest :perception:pitest :memory:pitest"
  local t0 t1 dur rc
  local score_core score_perc score_mem min_score
  local note_msg hard_note
  local s c p m

  add_step "${cmd_display}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    note "pitest dry-run (would run pure modules; soft threshold ${PITEST_SOFT_THRESHOLD}%)"
    return 0
  fi
  if [[ ! -x "${GRADLEW}" ]]; then
    echo "error: gradlew not found or not executable at ${GRADLEW}" >&2
    EXIT_CODE=1
    record_gate "pitest" "fail" 0 "gradlew missing"
    return 1
  fi

  echo ">> ${cmd_display}"
  t0="$(date +%s)"
  set +e
  "${GRADLEW}" :core:pitest :perception:pitest :memory:pitest
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))

  if [[ ${rc} -ne 0 ]]; then
    EXIT_CODE=1
    record_gate "pitest" "fail" "${dur}" "gradle pitest tasks failed (exit ${rc})"
    return 1
  fi

  score_core="$(parse_pitest_module_score core)"
  score_perc="$(parse_pitest_module_score perception)"
  score_mem="$(parse_pitest_module_score memory)"

  if [[ -z "${score_core}" || -z "${score_perc}" || -z "${score_mem}" ]]; then
    EXIT_CODE=1
    record_gate "pitest" "fail" "${dur}" \
      "unparseable or missing PIT report (core=${score_core:-?} perception=${score_perc:-?} memory=${score_mem:-?})"
    return 1
  fi

  # Min of three (conservative). awk for float compare / min.
  min_score="$(
    awk -v a="${score_core}" -v b="${score_perc}" -v c="${score_mem}" \
      'BEGIN {
        m = a + 0
        if (b + 0 < m) m = b + 0
        if (c + 0 < m) m = c + 0
        printf "%.1f", m
      }'
  )"

  if awk -v s="${min_score}" 'BEGIN { exit !(s + 0 == 0) }'; then
    # Exactly 0.0 → treat as fail (0 mutations / total failure mode)
    EXIT_CODE=1
    GATE_MUTATION_SCORE="${min_score}"
    record_gate "pitest" "fail" "${dur}" \
      "mutation_score=0 (core=${score_core} perception=${score_perc} memory=${score_mem})"
    return 1
  fi

  GATE_MUTATION_SCORE="${min_score}"
  note_msg="core=${score_core} perception=${score_perc} memory=${score_mem} (min); soft threshold ${PITEST_SOFT_THRESHOLD}%"

  if awk -v s="${min_score}" -v t="${PITEST_SOFT_THRESHOLD}" 'BEGIN { exit !(s + 0 < t) }'; then
    note_msg="${note_msg}; below ${PITEST_SOFT_THRESHOLD}% soft threshold"
    if pitest_hard_mode; then
      EXIT_CODE=1
      record_gate "pitest" "fail" "${dur}" "${note_msg}; MUD_PITEST_HARD=1"
      note "PIT hard fail: min mutation_score ${min_score} < ${PITEST_SOFT_THRESHOLD}"
      return 1
    fi
    note "PIT soft: min mutation_score ${min_score} below ${PITEST_SOFT_THRESHOLD}% (not failing)"
  else
    note "PIT min mutation_score ${min_score} (core/perception/memory)"
  fi

  record_gate "pitest" "pass" "${dur}" "${note_msg}"
  return 0
}

# Honest skip when lane does not run PIT (never a permanent placeholder ticket note).
skip_pitest() {
  local reason="${1:-not in lane}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] SKIP pitest (${reason})"
  else
    echo "SKIP pitest (${reason})"
  fi
  note "SKIP pitest (${reason})"
  record_gate "pitest" "skipped" 0 "${reason}"
}

# Hard test-file lock (MUD-012). Content hash of tracked */src/test/** vs baseline.
# Fail-closed on drift, missing baseline, or untracked src/test paths. See docs/TEST_LOCK.md.
run_test_lock() {
  local cmd_display="./tools/test_lock.sh --check"
  local t0 t1 dur rc

  add_step "${cmd_display}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    return 0
  fi
  if [[ ! -x "${TEST_LOCK}" ]]; then
    echo "error: test_lock.sh not found or not executable at ${TEST_LOCK}" >&2
    EXIT_CODE=1
    record_gate "test_lock" "fail" 0 "test_lock.sh missing"
    return 1
  fi
  echo ">> ${cmd_display}"
  t0="$(date +%s)"
  set +e
  "${TEST_LOCK}" --check
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))
  if [[ ${rc} -ne 0 ]]; then
    EXIT_CODE=1
    record_gate "test_lock" "fail" "${dur}"
    return 1
  fi
  record_gate "test_lock" "pass" "${dur}"
  return 0
}

# Hard no-live-LLM unit gate (MUD-032). Static rg of */src/test/**; excludes testbot.
# Fail-closed on missing checker/rg or any forbidden pattern. See docs/NO_LIVE_LLM_UNIT.md.
run_no_live_llm_unit() {
  local cmd_display="./tools/quality/check_no_live_llm_unit.sh"
  local t0 t1 dur rc
  local out_file line rel code rem
  local hit_count=0

  add_step "${cmd_display}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    note "no_live_llm_unit dry-run (would run hard static rg)"
    return 0
  fi
  if [[ ! -x "${NO_LIVE_LLM_CHECKER}" ]]; then
    echo "error: check_no_live_llm_unit.sh not found or not executable at ${NO_LIVE_LLM_CHECKER}" >&2
    EXIT_CODE=1
    record_gate "no_live_llm_unit" "fail" 0 "checker missing"
    note "no_live_llm_unit hard fail: checker missing"
    return 1
  fi
  if ! command -v rg >/dev/null 2>&1; then
    echo "error: rg (ripgrep) required for no_live_llm_unit gate" >&2
    EXIT_CODE=1
    record_gate "no_live_llm_unit" "fail" 0 "rg missing"
    note "no_live_llm_unit hard fail: rg missing"
    return 1
  fi

  echo ">> ${cmd_display}"
  out_file="$(mktemp)"
  t0="$(date +%s)"
  set +e
  "${NO_LIVE_LLM_CHECKER}" >"${out_file}" 2>&1
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))
  # Always show checker output (PASS line or hit list)
  cat "${out_file}" || true

  if [[ ${rc} -ne 0 ]]; then
    # Parse hit lines: path:line: CODE  snippet → findings[]
    while IFS= read -r line || [[ -n "${line}" ]]; do
      # Match: rel/path.kt:12: LIVE_LLM_OPENAI_CLIENT  snippet
      if [[ "${line}" =~ ^([^:]+):([0-9]+):[[:space:]]+(LIVE_LLM_[A-Z_]+)[[:space:]]+(.*)$ ]]; then
        rel="${BASH_REMATCH[1]}"
        code="${BASH_REMATCH[3]}"
        rem="${BASH_REMATCH[4]}"
        if [[ "${hit_count}" -lt "${NO_LIVE_LLM_FINDINGS_CAP}" ]]; then
          append_finding "${code}" "${rel}" "null" "null" \
            "Remove live OpenAI from unit tests; mock LLMClient. ${rem}"
        fi
        hit_count=$((hit_count + 1))
      fi
    done < "${out_file}"
    rm -f "${out_file}"
    EXIT_CODE=1
    record_gate "no_live_llm_unit" "fail" "${dur}" "hits=${hit_count}"
    note "no_live_llm_unit HARD fail: ${hit_count} hit(s) (or checker error)"
    return 1
  fi
  rm -f "${out_file}"
  record_gate "no_live_llm_unit" "pass" "${dur}"
  return 0
}

skip_no_live_llm_unit() {
  local reason="$1"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] SKIP no_live_llm_unit (${reason})"
  else
    echo "SKIP no_live_llm_unit (${reason})"
  fi
  note "SKIP no_live_llm_unit (${reason})"
  record_gate "no_live_llm_unit" "skipped" 0 "${reason}"
}

# Soft opt-out (MUD-031): --token-soft or MUD_TOKEN_SOFT=1 → report-only.
token_soft_mode() {
  if [[ "${TOKEN_SOFT_CLI}" -eq 1 ]]; then
    return 0
  fi
  if [[ "${MUD_TOKEN_SOFT:-0}" == "1" || "${MUD_TOKEN_SOFT:-}" == "true" || "${MUD_TOKEN_SOFT:-}" == "yes" ]]; then
    return 0
  fi
  return 1
}

# Explicit hard force (redundant under MUD-031 default; kept for 030 back-compat).
token_hard_force() {
  if [[ "${TOKEN_HARD_CLI}" -eq 1 ]]; then
    return 0
  fi
  if [[ "${MUD_TOKEN_HARD:-0}" == "1" || "${MUD_TOKEN_HARD:-}" == "true" || "${MUD_TOKEN_HARD:-}" == "yes" ]]; then
    return 0
  fi
  return 1
}

# Hard-on-touched default (MUD-031). Soft only when soft opt-out (or full-repo scope path).
# Soft opt-out wins over hard force if both set (escape hatch).
token_hard_mode() {
  if token_soft_mode; then
    return 1
  fi
  # Default hard for lane runs; force flags are redundant but explicit.
  return 0
}

# Token/structure (MUD-031). Hard-on-touched default; soft opt-out; never full-repo hard.
# Checker always exits 0 — verify owns fail policy. See docs/TOKEN_BUDGET_KT.md.
run_token_budget() {
  local scope_mode="${MUD_TOKEN_SCOPE:-touched}"
  local git_base="${MUD_TOKEN_GIT_BASE:-origin/master}"
  local hard=0
  local scope_label="touched"
  local cmd_display
  local t0 t1 dur rc
  local merge_note="" e_count=0 w_count=0 merged=0 truncated=0
  local line code fpath metric limit remediation
  local note_msg
  local -a checker_args

  # Resolve hard vs soft + scope (MUD-031):
  # - Soft opt-out → report-only (keep requested scope).
  # - SCOPE=full → soft full-repo inventory (never hard full-repo).
  # - Explicit hard force + SCOPE=full → force scoped + note (030/031).
  # - Else hard-on-touched default.
  if token_soft_mode; then
    hard=0
  elif [[ "${scope_mode}" == "full" ]]; then
    if token_hard_force; then
      note "MUD_TOKEN_SCOPE=full ignored under hard force (scoped git-diff only; avoid god-file cliff)"
      scope_mode="touched"
      hard=1
    else
      hard=0
    fi
  else
    hard=1
  fi

  # Safety: never hard-fail full-repo inventory.
  if [[ "${hard}" -eq 1 && "${scope_mode}" == "full" ]]; then
    note "MUD_TOKEN_SCOPE=full ignored under hard mode (scoped git-diff only; avoid god-file cliff)"
    scope_mode="touched"
  fi

  checker_args=(--root "${ROOT_DIR}" --quiet-stdout --json-out "${TOKEN_JSON_OUT}")
  if [[ "${scope_mode}" == "full" ]]; then
    scope_label="full"
    hard=0
    cmd_display="python3 tools/quality/check_token_budget_kt.py --root . --quiet-stdout --json-out tmp/token_budget_kt_verify.json"
  else
    scope_label="touched"
    checker_args+=(--git-diff --git-base "${git_base}")
    cmd_display="python3 tools/quality/check_token_budget_kt.py --root . --git-diff --git-base ${git_base} --quiet-stdout --json-out tmp/token_budget_kt_verify.json"
  fi

  add_step "${cmd_display}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    if [[ "${hard}" -eq 1 ]]; then
      note "token_budget dry-run (would run hard-on-touched default)"
    else
      note "token_budget dry-run (would run soft report-only)"
    fi
    return 0
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    if [[ "${hard}" -eq 1 ]]; then
      EXIT_CODE=1
      record_gate "token_budget" "fail" 0 "python3 missing (hard mode)"
      note "token_budget hard fail: python3 missing"
      return 1
    fi
    record_gate "token_budget" "skipped" 0 "python3 missing"
    note "token_budget skipped: python3 missing"
    return 0
  fi
  if [[ ! -f "${TOKEN_CHECKER}" ]]; then
    if [[ "${hard}" -eq 1 ]]; then
      EXIT_CODE=1
      record_gate "token_budget" "fail" 0 "checker missing (hard mode)"
      note "token_budget hard fail: checker missing at tools/quality/check_token_budget_kt.py"
      return 1
    fi
    record_gate "token_budget" "skipped" 0 "checker missing"
    note "token_budget skipped: checker missing"
    return 0
  fi

  mkdir -p "$(dirname "${TOKEN_JSON_OUT}")"
  echo ">> ${cmd_display}"
  t0="$(date +%s)"
  set +e
  python3 "${TOKEN_CHECKER}" "${checker_args[@]}"
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))

  # Checker contract is always 0; non-zero or missing JSON = crash / tooling break.
  if [[ ${rc} -ne 0 || ! -s "${TOKEN_JSON_OUT}" ]]; then
    if [[ "${hard}" -eq 1 ]]; then
      EXIT_CODE=1
      record_gate "token_budget" "fail" "${dur}" "checker crash or empty JSON (exit ${rc})"
      note "token_budget hard fail: checker exit ${rc} or empty report"
      return 1
    fi
    record_gate "token_budget" "pass" "${dur}" "checker unavailable (exit ${rc}); report-only soft"
    note "token_budget soft: checker exit ${rc} or empty JSON — no findings merged"
    return 0
  fi

  # Merge findings into dod-summary (cap TOKEN_FINDINGS_CAP). Count E/W for gate note.
  # Fields: code, path, metric, limit, remediation (tab-separated; remediation may be empty).
  while IFS=$'\t' read -r code fpath metric limit remediation || [[ -n "${code:-}" ]]; do
    [[ -z "${code:-}" ]] && continue
    if [[ "${code}" == "__META__" ]]; then
      e_count="${fpath}"
      w_count="${metric}"
      merged="${limit}"
      truncated="${remediation}"
      continue
    fi
    append_finding "${code}" "${fpath}" "${metric}" "${limit}" "${remediation}"
  done < <(
    python3 - "${TOKEN_JSON_OUT}" "${TOKEN_FINDINGS_CAP}" <<'PY'
import json, sys
path, cap = sys.argv[1], int(sys.argv[2])
with open(path, encoding="utf-8") as f:
    data = json.load(f)
findings = data.get("findings") or []
e_count = sum(1 for r in findings if str(r.get("code", "")).endswith("_E"))
w_count = sum(1 for r in findings if str(r.get("code", "")).endswith("_W"))
rows = findings[:cap]
truncated = 1 if len(findings) > cap else 0
for r in rows:
    code = str(r.get("code", ""))
    fpath = str(r.get("path", ""))
    metric = r.get("metric")
    limit = r.get("limit")
    rem = str(r.get("remediation", "")).replace("\t", " ").replace("\n", " ")
    m = "" if metric is None else str(metric)
    lim = "" if limit is None else str(limit)
    print(f"{code}\t{fpath}\t{m}\t{lim}\t{rem}")
print(f"__META__\t{e_count}\t{w_count}\t{len(rows)}\t{truncated}")
PY
  )

  if [[ "${truncated}" -eq 1 ]]; then
    merge_note="; findings truncated at ${TOKEN_FINDINGS_CAP}"
    note "token_budget findings truncated at ${TOKEN_FINDINGS_CAP}"
  fi

  if [[ "${hard}" -eq 1 ]]; then
    if [[ "${e_count}" -gt 0 ]]; then
      EXIT_CODE=1
      note_msg="E=${e_count} W=${w_count} scope=${scope_label} hard-on-touched${merge_note}"
      record_gate "token_budget" "fail" "${dur}" "${note_msg}"
      note "token_budget HARD fail: ${e_count} error-tier finding(s) in ${scope_label} scope"
      return 1
    fi
    note_msg="E=0 W=${w_count} scope=${scope_label} hard-on-touched; no *_E${merge_note}"
    record_gate "token_budget" "pass" "${dur}" "${note_msg}"
    note "token_budget hard pass: E=0 W=${w_count} scope=${scope_label}"
    return 0
  fi

  # Soft: always pass; never set EXIT_CODE from token alone.
  note_msg="E=${e_count} W=${w_count} scope=${scope_label} report-only${merge_note}"
  record_gate "token_budget" "pass" "${dur}" "${note_msg}"
  note "token_budget soft: E=${e_count} W=${w_count} scope=${scope_label} (report-only)"
  return 0
}

skip_token_budget() {
  local reason="$1"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] SKIP token_budget (${reason})"
  else
    echo "SKIP token_budget (${reason})"
  fi
  note "SKIP token_budget (${reason})"
  record_gate "token_budget" "skipped" 0 "${reason}"
}

# Builder plan/brief token preflight (MUD-033). Optional --preflight PATH only.
# Checker exit: 0 → pass; 1 warn → pass+note; 2 fail → verify fail.
# See docs/BUILDER_PREFLIGHT.md.
run_builder_preflight() {
  local path="${1:-}"
  local t0 t1 dur rc=0
  local cmd_display="python3 tools/quality/check_builder_preflight.py ${path}"

  if [[ -z "${path}" ]]; then
    echo "error: --preflight requires a path" >&2
    record_gate "builder_preflight" "fail" 0 "path required"
    EXIT_CODE=1
    return 1
  fi

  add_step "builder_preflight ${path}"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "[dry-run] ${cmd_display}"
    note "builder_preflight dry-run (would check ${path})"
    record_gate "builder_preflight" "skipped" 0 "dry-run"
    return 0
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    echo "error: python3 missing (builder_preflight)" >&2
    record_gate "builder_preflight" "fail" 0 "python3 missing"
    EXIT_CODE=1
    return 1
  fi
  if [[ ! -f "${PREFLIGHT_CHECKER}" ]]; then
    echo "error: checker missing: ${PREFLIGHT_CHECKER}" >&2
    record_gate "builder_preflight" "fail" 0 "checker missing"
    EXIT_CODE=1
    return 1
  fi

  t0="$(date +%s)"
  set +e
  python3 "${PREFLIGHT_CHECKER}" --root "${ROOT_DIR}" "${path}"
  rc=$?
  set -e
  t1="$(date +%s)"
  dur=$((t1 - t0))

  if [[ ${rc} -eq 0 ]]; then
    record_gate "builder_preflight" "pass" "${dur}" "clear"
    return 0
  fi
  if [[ ${rc} -eq 1 ]]; then
    # warn-only: verify passes with note (plan §3)
    note "builder_preflight warn-only (tok over warn, under fail) for ${path}"
    record_gate "builder_preflight" "pass" "${dur}" "warn-only"
    return 0
  fi
  # rc 2+ hard fail
  echo "error: builder_preflight hard fail (exit ${rc}) for ${path}" >&2
  record_gate "builder_preflight" "fail" "${dur}" "hard fail exit ${rc}"
  EXIT_CODE=1
  return 1
}

module_has_tests() {
  local mod="$1"
  [[ -d "${ROOT_DIR}/${mod}/src/test" ]]
}

normalize_module() {
  # strip leading : if present
  local m="$1"
  m="${m#:}"
  printf '%s' "${m}"
}

# Fill unset gates with skipped + notes (honest inventory).
finalize_gates() {
  local g

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    for g in compile tests detekt konsist test_lock pitest token_budget no_live_llm_unit; do
      GATE_SEEN[$g]=1
      GATE_STATUS[$g]="skipped"
      GATE_DURATION[$g]=0
      case "$g" in
        pitest)
          if [[ "${LANE}" == "pitest" ]]; then
            GATE_NOTE[$g]="dry-run"
          elif [[ "${LANE}" == "full" && "${PITEST_IN_FULL_LANE}" -eq 0 ]]; then
            GATE_NOTE[$g]="nightly via --pitest (core PIT >45s)"
          else
            GATE_NOTE[$g]="not in lane"
          fi
          ;;
        token_budget)
          if [[ "${LANE}" == "quarantine" || "${LANE}" == "preflight" ]]; then
            GATE_NOTE[$g]="${LANE} lane"
          elif [[ "${LANE}" == "pitest" ]]; then
            GATE_NOTE[$g]="not in pitest lane"
          else
            GATE_NOTE[$g]="dry-run"
          fi
          ;;
        no_live_llm_unit)
          if [[ "${LANE}" == "quarantine" || "${LANE}" == "preflight" ]]; then
            GATE_NOTE[$g]="${LANE} lane"
          else
            GATE_NOTE[$g]="dry-run"
          fi
          ;;
        *)
          if [[ "${LANE}" == "preflight" ]]; then
            GATE_NOTE[$g]="preflight lane"
          else
            GATE_NOTE[$g]="dry-run"
          fi
          ;;
      esac
    done
    # builder_preflight only meaningful on preflight lane; dry-run may already record it
    if [[ "${LANE}" == "preflight" && -z "${GATE_SEEN[builder_preflight]:-}" ]]; then
      GATE_SEEN[builder_preflight]=1
      GATE_STATUS[builder_preflight]="skipped"
      GATE_DURATION[builder_preflight]=0
      GATE_NOTE[builder_preflight]="dry-run"
    fi
    return 0
  fi

  if [[ -z "${GATE_SEEN[compile]:-}" ]]; then
    if [[ "${LANE}" == "core" || "${LANE}" == "full" || "${LANE}" == "quarantine" || "${LANE}" == "pitest" ]]; then
      record_gate "compile" "skipped" 0 "via test tasks / not separate"
    else
      record_gate "compile" "skipped" 0
    fi
  fi

  if [[ -z "${GATE_SEEN[tests]:-}" ]]; then
    if [[ "${LANE}" == "default" && ${#MODULES[@]} -eq 0 ]]; then
      record_gate "tests" "skipped" 0 "no module tests; pass modules or use --core/--full"
    elif [[ "${LANE}" == "pitest" ]]; then
      record_gate "tests" "skipped" 0 "pitest lane (mutation via pitest tasks)"
    else
      record_gate "tests" "skipped" 0
    fi
  fi

  for g in detekt konsist test_lock; do
    if [[ -z "${GATE_SEEN[$g]:-}" ]]; then
      if [[ "${LANE}" == "quarantine" ]]; then
        record_gate "$g" "skipped" 0 "quarantine lane"
      else
        record_gate "$g" "skipped" 0
      fi
    fi
  done

  if [[ -z "${GATE_SEEN[pitest]:-}" ]]; then
    if [[ "${LANE}" == "full" && "${PITEST_IN_FULL_LANE}" -eq 0 ]]; then
      record_gate "pitest" "skipped" 0 "nightly via --pitest (core PIT >45s)"
    else
      record_gate "pitest" "skipped" 0 "not in lane"
    fi
  fi

  # token_budget (MUD-030): optional gate; fill skipped when lane never ran it
  if [[ -z "${GATE_SEEN[token_budget]:-}" ]]; then
    if [[ "${LANE}" == "quarantine" ]]; then
      record_gate "token_budget" "skipped" 0 "quarantine lane"
    elif [[ "${LANE}" == "pitest" ]]; then
      record_gate "token_budget" "skipped" 0 "not in pitest lane"
    else
      record_gate "token_budget" "skipped" 0 "not run"
    fi
  fi

  # no_live_llm_unit (MUD-032): optional gate; skip quarantine only
  if [[ -z "${GATE_SEEN[no_live_llm_unit]:-}" ]]; then
    if [[ "${LANE}" == "quarantine" ]]; then
      record_gate "no_live_llm_unit" "skipped" 0 "quarantine lane"
    elif [[ "${LANE}" == "preflight" ]]; then
      record_gate "no_live_llm_unit" "skipped" 0 "preflight lane"
    else
      record_gate "no_live_llm_unit" "skipped" 0 "not run"
    fi
  fi

  # builder_preflight (MUD-033): optional gate; only on --preflight lane
  if [[ -z "${GATE_SEEN[builder_preflight]:-}" ]]; then
    if [[ "${LANE}" == "preflight" ]]; then
      record_gate "builder_preflight" "skipped" 0 "not run"
    else
      # not in default inventory — omit from dod-summary (additionalProperties OK)
      :
    fi
  fi
}

# Emit compact dod-summary.json (pure bash; no jq required). schema_version 2 + findings[] (MUD-027).
write_dod_summary() {
  local result result_json duration_s generated_at qcount
  local steps_json="" findings_json="" i s
  local out_dir

  [[ "${VERIFY_STARTED}" -eq 1 ]] || return 0
  [[ "${DOD_WRITTEN}" -eq 0 ]] || return 0
  DOD_WRITTEN=1

  finalize_gates

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    result="DRY_RUN"
  elif [[ ${EXIT_CODE} -ne 0 ]]; then
    result="FAIL"
  else
    result="PASS"
  fi

  duration_s=$(( $(date +%s) - SCRIPT_START_S ))
  generated_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u +"%Y-%m-%dT%H:%M:%SZ")"
  qcount="$(count_quarantine_tags)"

  # Cap steps list (token / size pressure)
  if [[ ${#STEPS[@]} -gt 0 ]]; then
    steps_json="\"$(json_escape "${STEPS[0]}")\""
    i=1
    while [[ ${i} -lt ${#STEPS[@]} && ${i} -lt 12 ]]; do
      steps_json="${steps_json}, \"$(json_escape "${STEPS[${i}]}")\""
      i=$((i + 1))
    done
  fi

  # findings[] always present (empty OK until MUD-028+)
  if [[ ${#FINDINGS_JSON_PARTS[@]} -gt 0 ]]; then
    findings_json="${FINDINGS_JSON_PARTS[0]}"
    i=1
    while [[ ${i} -lt ${#FINDINGS_JSON_PARTS[@]} ]]; do
      findings_json="${findings_json}, ${FINDINGS_JSON_PARTS[${i}]}"
      i=$((i + 1))
    done
  fi

  out_dir="$(dirname "${DOD_SUMMARY_PATH}")"
  mkdir -p "${out_dir}"

  {
    printf '{\n'
    printf '  "schema_version": 2,\n'
    printf '  "tool": "verify_mud",\n'
    printf '  "lane": "%s",\n' "$(json_escape "${LANE}")"
    printf '  "result": "%s",\n' "$(json_escape "${result}")"
    printf '  "exit_code": %s,\n' "${EXIT_CODE}"
    printf '  "generated_at": "%s",\n' "$(json_escape "${generated_at}")"
    printf '  "duration_s": %s,\n' "${duration_s}"
    if [[ "${DRY_RUN}" -eq 1 ]]; then
      printf '  "dry_run": true,\n'
    fi
    printf '  "gates": {\n'

    # compile
    printf '    "compile": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[compile]:-skipped}" "${GATE_DURATION[compile]:-0}"
    if [[ -n "${GATE_NOTE[compile]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[compile]}")"
    fi
    printf ' },\n'

    # tests
    printf '    "tests": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[tests]:-skipped}" "${GATE_DURATION[tests]:-0}"
    if [[ -n "${GATE_NOTE[tests]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[tests]}")"
    fi
    printf ' },\n'

    # detekt
    printf '    "detekt": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[detekt]:-skipped}" "${GATE_DURATION[detekt]:-0}"
    if [[ -n "${GATE_NOTE[detekt]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[detekt]}")"
    fi
    printf ' },\n'

    # konsist
    printf '    "konsist": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[konsist]:-skipped}" "${GATE_DURATION[konsist]:-0}"
    if [[ -n "${GATE_NOTE[konsist]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[konsist]}")"
    fi
    printf ' },\n'

    # test_lock
    printf '    "test_lock": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[test_lock]:-skipped}" "${GATE_DURATION[test_lock]:-0}"
    if [[ -n "${GATE_NOTE[test_lock]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[test_lock]}")"
    fi
    printf ' },\n'

    # pitest (mutation_score only when run — MUD-014)
    printf '    "pitest": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[pitest]:-skipped}" "${GATE_DURATION[pitest]:-0}"
    if [[ -n "${GATE_MUTATION_SCORE}" ]]; then
      printf ', "mutation_score": %s' "${GATE_MUTATION_SCORE}"
    fi
    if [[ -n "${GATE_NOTE[pitest]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[pitest]}")"
    fi
    printf ' },\n'

    # token_budget (MUD-030 pilot; optional via additionalProperties)
    printf '    "token_budget": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[token_budget]:-skipped}" "${GATE_DURATION[token_budget]:-0}"
    if [[ -n "${GATE_NOTE[token_budget]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[token_budget]}")"
    fi
    printf ' },\n'

    # no_live_llm_unit (MUD-032; optional via additionalProperties — not in required known tuple)
    printf '    "no_live_llm_unit": { "status": "%s", "duration_s": %s' \
      "${GATE_STATUS[no_live_llm_unit]:-skipped}" "${GATE_DURATION[no_live_llm_unit]:-0}"
    if [[ -n "${GATE_NOTE[no_live_llm_unit]:-}" ]]; then
      printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[no_live_llm_unit]}")"
    fi
    # builder_preflight (MUD-033) only when recorded (preflight lane); optional additionalProperties
    if [[ -n "${GATE_SEEN[builder_preflight]:-}" ]]; then
      printf ' },\n'
      printf '    "builder_preflight": { "status": "%s", "duration_s": %s' \
        "${GATE_STATUS[builder_preflight]:-skipped}" "${GATE_DURATION[builder_preflight]:-0}"
      if [[ -n "${GATE_NOTE[builder_preflight]:-}" ]]; then
        printf ', "note": "%s"' "$(json_escape "${GATE_NOTE[builder_preflight]}")"
      fi
      printf ' }\n'
    else
      printf ' }\n'
    fi

    printf '  },\n'
    if [[ -n "${qcount}" ]]; then
      printf '  "quarantine_count": %s,\n' "${qcount}"
    else
      printf '  "quarantine_count": null,\n'
    fi
    if [[ -n "${steps_json}" ]]; then
      printf '  "steps": [%s],\n' "${steps_json}"
    else
      printf '  "steps": [],\n'
    fi
    # Always emit findings key (empty array valid) — MUD-027
    printf '  "findings": [%s]\n' "${findings_json}"
    printf '}\n'
  } > "${DOD_SUMMARY_PATH}"

  # Fail closed on invalid summary shape (python3 preferred; bash fallback)
  validate_dod_summary "${DOD_SUMMARY_PATH}" || true
}

print_human_summary() {
  local RESULT steps_line notes_line local_i
  local rel_dod

  if [[ "${DRY_RUN}" -eq 1 ]]; then
    RESULT="DRY_RUN"
  elif [[ ${EXIT_CODE} -ne 0 ]]; then
    RESULT="FAIL"
  else
    RESULT="PASS"
  fi

  steps_line=""
  if [[ ${#STEPS[@]} -gt 0 ]]; then
    steps_line="${STEPS[0]}"
    local_i=1
    while [[ ${local_i} -lt ${#STEPS[@]} ]]; do
      steps_line="${steps_line}; ${STEPS[${local_i}]}"
      local_i=$((local_i + 1))
    done
  else
    steps_line="(none)"
  fi

  notes_line=""
  if [[ ${#NOTES[@]} -gt 0 ]]; then
    notes_line="${NOTES[0]}"
    local_i=1
    while [[ ${local_i} -lt ${#NOTES[@]} ]]; do
      notes_line="${notes_line}; ${NOTES[${local_i}]}"
      local_i=$((local_i + 1))
    done
  else
    notes_line="(none)"
  fi

  # Prefer repo-relative path in summary when under ROOT_DIR
  rel_dod="${DOD_SUMMARY_PATH}"
  case "${DOD_SUMMARY_PATH}" in
    "${ROOT_DIR}"/*) rel_dod="${DOD_SUMMARY_PATH#"${ROOT_DIR}"/}" ;;
  esac

  echo ""
  echo "== verify_mud =="
  echo "lane: ${LANE}"
  echo "steps: ${steps_line}"
  echo "result: ${RESULT} (exit ${EXIT_CODE})"
  echo "notes: ${notes_line}"
  echo "dod_summary: ${rel_dod}"
}

# Always attempt JSON write on exit (PASS/FAIL/mid-script die after start).
on_exit() {
  local ec=$?
  if [[ "${VERIFY_STARTED}" -eq 1 ]]; then
    # Prefer script EXIT_CODE over trap ec when set by hard steps
    write_dod_summary || true
  fi
  return 0
}
trap on_exit EXIT

# --- arg parse ---
while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --token-hard)
      TOKEN_HARD_CLI=1
      shift
      ;;
    --token-soft)
      TOKEN_SOFT_CLI=1
      shift
      ;;
    --preflight)
      if [[ $# -lt 2 || -z "${2:-}" || "${2:0:1}" == "-" ]]; then
        die_usage "--preflight requires a path argument"
      fi
      LANE="preflight"
      PREFLIGHT_PATH="$2"
      shift 2
      ;;
    default|fast|--fast)
      LANE="default"
      shift
      ;;
    core|--core)
      LANE="core"
      shift
      ;;
    full|--full)
      LANE="full"
      shift
      ;;
    pitest|--pitest)
      LANE="pitest"
      shift
      ;;
    quarantine|--quarantine)
      LANE="quarantine"
      shift
      ;;
    -*)
      die_usage "unknown flag: $1"
      ;;
    *)
      # positional: module name or lane synonym already handled
      MODULES+=("$(normalize_module "$1")")
      shift
      ;;
  esac
done

VERIFY_STARTED=1

# --- lane body ---
case "${LANE}" in
  preflight)
    if [[ ${#MODULES[@]} -gt 0 ]]; then
      die_usage "preflight lane does not take module args (got: ${MODULES[*]})"
    fi
    if [[ -z "${PREFLIGHT_PATH}" ]]; then
      die_usage "--preflight requires a path"
    fi
    note "builder preflight only (MUD-033); not a product lane"
    run_builder_preflight "${PREFLIGHT_PATH}" || true
    # Skip all product gates: mark standard inventory as skipped for this lane.
    record_gate "compile" "skipped" 0 "preflight lane"
    record_gate "tests" "skipped" 0 "preflight lane"
    record_gate "detekt" "skipped" 0 "preflight lane"
    record_gate "konsist" "skipped" 0 "preflight lane"
    record_gate "test_lock" "skipped" 0 "preflight lane"
    record_gate "pitest" "skipped" 0 "preflight lane"
    record_gate "token_budget" "skipped" 0 "preflight lane"
    record_gate "no_live_llm_unit" "skipped" 0 "preflight lane"
    # Write summary and exit early (do not run gradle / hard product gates).
    write_dod_summary
    print_human_summary
    exit "${EXIT_CODE}"
    ;;
  default)
    if [[ ${#MODULES[@]} -eq 0 ]]; then
      run_gradle compile :core:compileKotlin || true
    else
      for mod in "${MODULES[@]}"; do
        if module_has_tests "${mod}"; then
          # compile via test task deps; explicit compile then test for clarity
          run_gradle compile ":${mod}:compileKotlin" || true
          if [[ ${EXIT_CODE} -eq 0 ]]; then
            run_gradle tests ":${mod}:test" || true
          fi
        else
          run_gradle compile ":${mod}:compileKotlin" || true
        fi
        # continue modules after failure to surface all; exit non-zero still
      done
    fi
    ;;
  core)
    if [[ ${#MODULES[@]} -gt 0 ]]; then
      die_usage "core lane does not take module args (got: ${MODULES[*]})"
    fi
    # Default JUnit excludeTags("quarantine") — green reasoning only. Debt: --quarantine / MUD-017.
    note ":reasoning included under default excludeTags(quarantine); debt via --quarantine (MUD-017)"
    run_gradle tests :core:test :perception:test :memory:test :reasoning:test || true
    ;;
  full)
    if [[ ${#MODULES[@]} -gt 0 ]]; then
      die_usage "full lane does not take module args (got: ${MODULES[*]})"
    fi
    note ":reasoning included under default excludeTags(quarantine); use --quarantine for debt"
    note "testbot excluded from full (slow/integration)"
    # Stable green: unit-test modules + compile-only leaf modules (record under tests)
    run_gradle tests \
      :core:test \
      :perception:test \
      :memory:test \
      :reasoning:test \
      :action:compileKotlin \
      :llm:compileKotlin \
      :config:compileKotlin \
      || true
    # client left out of full (Compose can be slow); compile-only optional later
    ;;
  quarantine)
    if [[ ${#MODULES[@]} -gt 0 ]]; then
      die_usage "quarantine lane does not take module args (got: ${MODULES[*]})"
    fi
    note "quarantine may fail: @Tag(quarantine) :reasoning debt (MUD-017) — hard-fail, not soft-pass"
    run_gradle tests :reasoning:test -Pmud.quarantineOnly=true || true
    ;;
  pitest)
    if [[ ${#MODULES[@]} -gt 0 ]]; then
      die_usage "pitest lane does not take module args (got: ${MODULES[*]})"
    fi
    note "PIT pure modules only (core/perception/memory); STRONGER mutators; soft ${PITEST_SOFT_THRESHOLD}%"
    run_pitest || true
    ;;
  *)
    die_usage "unknown lane: ${LANE}"
    ;;
esac

# Detekt hard gate (MUD-010) on default/fast/core/full/pitest — not quarantine debt lane.
# New smells fail; legacy soft via config/detekt/baseline.xml. See docs/DETEKT.md.
if [[ "${LANE}" != "quarantine" ]]; then
  run_gradle detekt detekt || true
fi

# Konsist architecture gate (MUD-011) on default/fast/core/full/pitest — not quarantine.
# Filtered :core:test so the arch suite is visible in the summary on every hard lane.
# Recorded under konsist (not general tests). See docs/KONSIST.md.
if [[ "${LANE}" != "quarantine" ]]; then
  run_gradle konsist :core:test --tests 'com.jcraw.mud.architecture.*' || true
fi

# Test-file lock (MUD-012) on default/fast/core/full/pitest — not quarantine debt lane.
# Unauthorized src/test content drift / untracked tests fail closed. See docs/TEST_LOCK.md.
if [[ "${LANE}" != "quarantine" ]]; then
  run_test_lock || true
fi

# No live LLM in unit tests (MUD-032) on default/fast/core/full/pitest — not quarantine.
# Static rg; hard-excludes testbot/**. Fail-closed if checker/rg missing.
# Placement: after test-lock, before token_budget. See docs/NO_LIVE_LLM_UNIT.md.
if [[ "${LANE}" != "quarantine" ]]; then
  run_no_live_llm_unit || true
else
  skip_no_live_llm_unit "quarantine lane"
fi

# Token/structure (MUD-031) on default/fast/core/full — hard-on-touched default.
# Soft opt-out: MUD_TOKEN_SOFT=1 / --token-soft. Skip quarantine + pitest.
# Placement: after test-lock / no_live_llm_unit, before PIT. See docs/TOKEN_BUDGET_KT.md.
case "${LANE}" in
  default|core|full)
    run_token_budget || true
    ;;
  quarantine)
    skip_token_budget "quarantine lane"
    ;;
  pitest)
    skip_token_budget "not in pitest lane"
    ;;
  *)
    skip_token_budget "not in lane"
    ;;
esac

# PIT (MUD-014): never on default/fast/core; full only if measured ≤45s (currently off).
# --pitest lane already ran run_pitest above. Honest skip notes — never eternal stub.
if [[ -z "${GATE_SEEN[pitest]:-}" ]]; then
  case "${LANE}" in
    full)
      if [[ "${PITEST_IN_FULL_LANE}" -eq 1 ]]; then
        run_pitest || true
      else
        skip_pitest "nightly via --pitest (core PIT >45s)"
      fi
      ;;
    quarantine)
      # quarantine never runs PIT; finalize_gates fills if needed
      skip_pitest "quarantine lane"
      ;;
    pitest)
      # should already be recorded; if dry-run left it unset, finalize handles
      :
      ;;
    *)
      skip_pitest "not in lane"
      ;;
  esac
fi

# --- summary (JSON via trap + explicit write; human always) ---
write_dod_summary
print_human_summary

exit "${EXIT_CODE}"
