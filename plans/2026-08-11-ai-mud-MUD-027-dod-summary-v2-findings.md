# MUD-027 Plan — dod-summary v2 + stable finding codes

**Ticket:** MUD-027 · **Worker:** grok · **Phase:** plan_review  
**Impl = fresh session** after Astra/Jason approve. Do **not** resume this plan session for impl.  
**Plan path:** `plans/2026-08-11-ai-mud-MUD-027-dod-summary-v2-findings.md`  
**Worker mirror:** `tmp/workers/MUD-027/PLAN.md`  
**Post-impl verify:** `./tools/verify_mud.sh --core`  
**Depends:** MUD-026 (done)

---

## 1. Goal / acceptance mapping

| # | Acceptance | Impl delivers |
|---|------------|---------------|
| 1 | Schema doc or `config/quality/dod_summary.schema.json` (v2) | **Create** JSON Schema (Draft 2020-12 or plain descriptive JSON Schema) under `config/quality/` describing top-level + `findings[]` |
| 2 | `tmp/dod-summary.json` optional `findings[]` `{code,path,metric,limit,remediation}` | Bump emit to `schema_version: 2`; always write `"findings": []` today (empty = no agent scrape debt); helper ready for MUD-028+ append |
| 3 | Existing gates still pass/fail/skipped + durations | Keep `gates.{compile,tests,detekt,konsist,test_lock,pitest}` shape; optional `note` / `mutation_score` unchanged |
| 4 | Light shape validation; hard fail if invalid after real verify | Post-write `validate_dod_summary`; on fail → `EXIT_CODE=1` + note; skip only when no summary written (`VERIFY_STARTED=0`) |
| 5 | AGENTS closeout still cites dod-summary path | Keep existing DoD/Verification bullets; **one line** if needed: v2 + optional `findings[]` |
| 6 | `./tools/verify_mud.sh --core` exit 0 | Green core lane after change (empty findings still valid) |
| 7 | Short docs pointer — no novel | ≤½-screen `docs/DOD_SUMMARY.md` **or** surgical pointer in `docs/AGENT_QUALITY_GATES_DESIGN.md` §5 → schema path; prefer dedicated short doc so DESIGN stays policy |

---

## 2. Current inventory (how dod-summary is written today)

| Piece | Truth |
|-------|--------|
| Writer | `tools/verify_mud.sh` → `write_dod_summary()` (~L467–581); pure bash `printf`, **no jq** |
| Path | `tmp/dod-summary.json` or `$MUD_DOD_SUMMARY` |
| Schema | **`schema_version`: 1** — `tool`, `lane`, `result`, `exit_code`, `generated_at`, `duration_s`, optional `dry_run`, `gates{}`, `quarantine_count`, `steps[]` |
| Gates | Associative arrays `GATE_SEEN/STATUS/DURATION/NOTE` + `record_gate`; fixed keys compile/tests/detekt/konsist/test_lock/pitest |
| Timing | Wall-clock s per gate; total `duration_s` from `SCRIPT_START_S` |
| Write policy | Always when `VERIFY_STARTED=1` and `DOD_WRITTEN=0`; trap `on_exit` + explicit end call; dry-run still emits |
| Findings | **None** — agents scrape Gradle logs (problem statement) |
| Schema file | **Missing** — no `config/quality/` yet (only `config/detekt/`) |
| Validation | **None** — any broken emit is silent |
| AGENTS | Already cites `tmp/dod-summary.json` / `$MUD_DOD_SUMMARY` in DoD + Verification |
| Design sketch | `docs/AGENT_QUALITY_GATES_DESIGN.md` §5: v2 + top-level `findings[]` with stable codes (e.g. `TOKEN_FILE_E`) |

Sample live emit (v1): `result` PASS/FAIL/DRY_RUN, six gates, no `findings`.

---

## 3. Design / recommended approach (minimal diff)

**Choice:** extend existing bash emitter + light post-write validator. No Gradle plugin, no jq dependency, no product Kotlin.

### Schema v2 (emit)
- Set `"schema_version": 2`.
- Keep all v1 fields (backward-compatible additive except version bump — intentional Wave Q; no external consumers of v1).
- Always emit `"findings": [ … ]` (empty array OK). Ticket “optional” = may be empty / no finding rows, not omit key (omitting makes validators flaky).
- Finding object fields (all required when a row exists):
  - `code` string — stable enum-ish ID (see below)
  - `path` string — repo-relative path or `""` if N/A
  - `metric` number or `null`
  - `limit` number or `null`
  - `remediation` string — short agent-actionable fix
- **Do not** require per-gate `findings` count (design sketch optional; out of minimal).
- **Do not** add `warn` gate status yet (today: pass|fail|skipped only).

### Stable codes (document now; emit none until MUD-028+)
Reserve in schema `description` / short docs (not hard-enforced enum in validator unless free):

| Code | Future owner | Meaning |
|------|--------------|---------|
| `TOKEN_FILE_W` / `TOKEN_FILE_E` | MUD-028/031 | file token budget warn/err |
| `TOKEN_FN_W` / `TOKEN_FN_E` | MUD-028/031 | function token budget |
| `STRUCTURE_*` | MUD-028+ | structure ceilings (LOC/cyclo) when wired |
| `DETEKT_NEW` | optional later | unbaselined detekt (not this ticket) |
| `TEST_LOCK` | optional later | lock mismatch (gate already fails; finding optional) |
| `DOD_SCHEMA` | this ticket validator | summary itself invalid (emit only if useful; else note + exit) |

MUD-027 **does not** populate token/structure findings — only the pipe + empty array + codes documented.

### Emit plumbing
- `FINDINGS_JSON_PARTS=()` or parallel arrays; `append_finding code path metric limit remediation` builds escaped JSON objects.
- `write_dod_summary`: after `gates` block, before/after `quarantine_count`/`steps`, print `"findings": [ … ]` (trailing-comma discipline).
- Header comment: mention MUD-027 v2.

### Light validation (fail closed)
- Function `validate_dod_summary` after successful write inside `write_dod_summary` (or immediately after both call sites — prefer **inside** so trap path covered).
- Prefer **python3** one-shot (stdlib `json` only): load file; assert `schema_version == 2`; `gates` is object; each known gate has `status` ∈ {pass,fail,skipped} and numeric `duration_s`; `findings` is list; each finding has string `code`,`path`,`remediation` and `metric`/`limit` number-or-null.
- If `python3` missing: ultra-light bash checks (`grep -q '"schema_version": 2'` + `"findings"` present + file non-empty) — still fail closed on miss.
- On invalid: `EXIT_CODE=1`, `note "dod-summary schema invalid"`, keep bad file for debug (do not delete).
- Dry-run: still validate shape (writer must produce legal v2).
- Early usage/`--help` before `VERIFY_STARTED`: no write → no validate.

### Docs / AGENTS
- **Create** `docs/DOD_SUMMARY.md` (~30–40 lines): path, env override, v2 fields, findings shape, code table pointer, link schema file + DESIGN §5. No novel architecture.
- **AGENTS Verification:** one clause — summary is **schema_version 2** with optional `findings[]` (path citation already exists).
- Optional one-liner in DESIGN §5: “runtime schema: `config/quality/dod_summary.schema.json` (MUD-027)”.

### Schema file style
- JSON Schema object: `required` top-level keys; `findings.items.required` = five fields; `additionalProperties` tolerant on gates for `note`/`mutation_score`.
- Inspiration only from jam `builder_dod_schema.json` if present elsewhere — **do not** copy Godot paths/fields.

---

## 4. Files to create/touch

| Path | Action |
|------|--------|
| `config/quality/dod_summary.schema.json` | **Create** — v2 schema |
| `tools/verify_mud.sh` | **Edit** — v2 emit, `findings[]`, `append_finding` stub, `validate_dod_summary`, help/header one-liners |
| `docs/DOD_SUMMARY.md` | **Create** — short pointer doc |
| `docs/AGENT_QUALITY_GATES_DESIGN.md` | **Optional surgical** — §5 link to schema path (1–2 lines) |
| `AGENTS.md` | **Edit** — one Verification line (v2 / findings); keep path citation |
| Ticket / BOARD | Plan turn: `plan_review`; impl turn: closeout |

**No** `*.kt`, detekt baseline, test-lock manifest, token checker, git commit/push.

---

## 5. Non-goals

- Token/structure checker (MUD-028)
- Hard token fail / hard-on-touched (MUD-030/031)
- Detekt baseline regen or policy change
- Populating real findings from Gradle/detekt logs this ticket
- Per-gate `findings` count field
- `warn` gate status / full JSON Schema CLI dependency
- Product/game logic; jq requirement; force-push / commit

---

## 6. How impl confirms acceptance (checklist)

- [ ] `config/quality/dod_summary.schema.json` exists; documents `schema_version: 2` + findings item shape
- [ ] `./tools/verify_mud.sh --core` → exit 0; `tmp/dod-summary.json` has `"schema_version": 2` and `"findings": []` (or valid rows)
- [ ] Gates still show pass/fail/skipped + `duration_s` for all six keys
- [ ] Corrupt summary path test: force bad JSON (manual or env to temp path + unit-ish shell check) → non-zero exit when validation runs
- [ ] `--dry-run` still emits parseable v2
- [ ] AGENTS still tells closeout to cite dod-summary path; v2 note present if edited
- [ ] `docs/DOD_SUMMARY.md` (or DESIGN pointer) short; no novel
- [ ] No token checker / no detekt baseline / no product kt diffs
- [ ] Closeout: paths, verify result, residual risk, `tmp/dod-summary.json` cite

---

## 7. Ordered impl steps

1. Create `config/quality/dod_summary.schema.json` (v2 + findings item + gate status enum).
2. Create `docs/DOD_SUMMARY.md` (path, fields, codes table, links).
3. Edit `verify_mud.sh`:
   - findings array plumbing + `append_finding`
   - `write_dod_summary` → schema 2 + `findings` JSON
   - `validate_dod_summary` (python3 preferred) → fail closed
   - help/header MUD-027 note
4. Surgical AGENTS Verification clause (v2 / findings); optional DESIGN §5 pointer.
5. Smoke: `./tools/verify_mud.sh --dry-run` then `./tools/verify_mud.sh --core`; inspect JSON.
6. Negative smoke: write deliberately invalid summary to temp via small inline test or temporarily break validator inputs — confirm exit 1 path works (document in closeout; no permanent broken code).
7. Ticket closeout + BOARD done (impl session only).

---

## 8. Risks

| Risk | Mitigation |
|------|------------|
| `python3` absent on some hosts | Bash fallback shape checks; document python3 preferred |
| Trailing-comma / empty findings JSON break | Always emit `"findings": []`; review printf commas carefully |
| Trap double-write races with validation | Keep `DOD_WRITTEN` guard; validate once after write |
| Consumers assumed v1 forever | Internal-only agent artifact; version bump intentional; note in docs |
| Over-scope into token findings | Empty array only; codes documented, not emitted |
| Validator false-fail on optional fields (`dry_run`, pitest `mutation_score`) | Require only stable core keys; allow extra gate properties |
| AGENTS bloat | One line max |

**Residual after green:** MUD-028 fills `findings[]`; until then agents still use gate pass/fail primarily.


---
Status: APPROVED by Astra 2026-08-11 22:38 MST
