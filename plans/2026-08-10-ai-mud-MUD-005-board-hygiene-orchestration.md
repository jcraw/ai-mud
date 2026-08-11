# MUD-005 Plan — Board hygiene + short ORCHESTRATION

**APPROVED by Astra** · 2026-08-10 ~19:35 AZ · common-sense OK (template fields, short ORCHESTRATION, surgical BOARD pointer; no novel/product scope creep).

**Ticket:** `issues/MUD-005-board-hygiene-orchestration.md`  
**Phase:** plan approved → fresh IMPL session (do not resume plan session).  
**Planner:** grok · **worker_out_dir:** `tmp/workers/MUD-005`  
**Date:** 2026-08-10

---

## 1. Goal / acceptance mapping

| Acceptance | Impl action |
|------------|-------------|
| Template has live fields (`agent_eligible`, `eligibility`, `needs_jason`, `phase`, `plan`, `report`, `depends_on`, `worker_out_dir`, session fields) | Expand `issues/_templates/ticket.md` frontmatter to match MUD-001/003/004/005 usage |
| Keep `verify: "./tools/verify_mud.sh"` default | Retain MUD-004 default in template |
| `issues/ORCHESTRATION.md` ≤ ~2 screens | Create short ops note (serial builder, plan→approve→fresh impl, human_gated, drain posture, worker dirs, pointers) |
| BOARD “How agents use this” → ORCHESTRATION + AGENTS | Surgical edit of that section only |
| No game_jam-length novel | Hard cap ORCHESTRATION ~80–120 lines / ≤2 screens |

---

## 2. Current inventory

**Template gaps** (`issues/_templates/ticket.md` today):
- Has: `id`, `area`, `title`, `status`, `priority`, `created`, `updated`, `source`, `labels`, `grok_session`, `codex_session`, `verify`
- **Missing:** `assignee`, `worker`, `phase`, `agent_eligible`, `eligibility`, `needs_jason`, `depends_on`, `plan`, `report`, `plan_session`, `worker_out_dir`, `worker_pid`, (optional) `impl_session`/`brief` as used on MUD-001

**Live field sample (MUD-003/004 done; MUD-005 in flight; MUD-001 spike extras):**
- Core ops: `worker`, `phase`, `agent_eligible`, `eligibility`, `depends_on`, `verify`, `plan`, `plan_session`, `grok_session`, `codex_session`, `worker_out_dir`, `worker_pid`
- Spike/report: `report`, `needs_jason` (MUD-009: `action`), `eligibility: human_gated|agent_eligible|done`
- Optional live-only (document as optional, not all required on every ticket): `assignee`, `brief`, `impl_session`, `spike`, `handoff_to`, `prior_failure`

**Missing file:** `issues/ORCHESTRATION.md` — does not exist. AGENTS explicitly defers orchestration novel here (MUD-005).

**BOARD “How agents use this” (lines ~27–33):** serial + plan→approve→impl + human_gated already stated; **no** pointer to ORCHESTRATION; AGENTS mentioned as “once it exists” (stale — AGENTS exists post MUD-003).

**Inventory dirs:** `issues/` = BOARD + MUD-001…018 + OVERNIGHT_HANDOFF + `_templates/`; `tmp/workers/` = MUD-001, 003, 004, 005. No product code in scope.

---

## 3. Recommended template frontmatter (final)

```yaml
---
id: MUD-000
area: engine          # tooling | engine | client | docs | chore | spike
title: Short title
status: open          # open | scheduled | in_progress | plan_review | done | blocked | wontfix
priority: med         # low | med | high
created: YYYY-MM-DD
updated: YYYY-MM-DD
source: jason
labels: []
assignee: ""
worker: ""            # grok | codex | claude | cursor | ""
phase: backlog        # backlog | planning | plan_review | impl | done | …
agent_eligible: true
eligibility: agent_eligible   # agent_eligible | human_gated | done
needs_jason: ""       # "" | playtest | opinion | action
depends_on: []
verify: "./tools/verify_mud.sh"
plan: ""
report: ""
plan_session: ""
grok_session: ""
codex_session: ""
worker_out_dir: ""    # tmp/workers/MUD-NNN when active
worker_pid: ""
---
```

**Body scaffold (keep lean):** Problem / Acceptance / Notes / Builder / Resolution — same shape as today.  
**Optional fields** (add only when needed; comment in template Notes, not required keys): `brief`, `impl_session`, `spike`, `handoff_to`, `prior_failure`.

---

## 4. ORCHESTRATION outline (target ≤2 screens / ~80–120 lines)

**File:** `issues/ORCHESTRATION.md`

1. **Purpose** (3–5 lines) — short ops note; not product architecture; AGENTS = contract, BOARD = queue, this = drain mechanics.
2. **Posture** — spare-capacity only; slow drip; one problem per ticket (link BOARD posture).
3. **Serial builder** — one live Grok/Codex (etc.) per working tree; queue others; no parallel product impl in same checkout.
4. **Plan → approve → fresh impl** — substantial work: plan under `plans/YYYY-MM-DD-…` → Astra or Jason stamp APPROVED → **new** impl session; **never** resume plan session for product edits unless ticket explicitly allows.
5. **human_gated ≠ done** — spikes/opinion/playtest/`needs_jason: action|playtest|opinion` stay gated; do not mark done without real deliverable.
6. **Worker dirs** — `tmp/workers/<ID>/` for briefs, PLAN.md, logs, pids; not committed product.
7. **Verify** — ticket `verify:` default `./tools/verify_mud.sh` (MUD-004); DoD in AGENTS.
8. **Pointers only** — `AGENTS.md`, `issues/BOARD.md`, `tools/verify_mud.sh`, ticket template; no game_jam novel, no launcher internals essay.
9. **Out of scope here** — git hygiene (MUD-009), brief templates (MUD-006), AGENTS rewrite, CI.

**Tone:** neutral agent parity (Grok/Codex/Claude/Cursor). Bullet-heavy.

---

## 5. Files to create / touch

| Path | Action |
|------|--------|
| `issues/_templates/ticket.md` | Expand frontmatter + tiny field comments if useful |
| `issues/ORCHESTRATION.md` | **Create** (short) |
| `issues/BOARD.md` | Surgical: “How agents use this” → point at `ORCHESTRATION.md` + `AGENTS.md`; fix “once it exists” |
| Ticket MUD-005 + BOARD sections | Bookkeeping only (status, resolution note on done in **impl** session) |
| `plans/…` + `tmp/workers/MUD-005/` | This plan (done in plan phase) |

**No:** `AGENTS.md` rewrite, `tools/verify_mud.sh`, any `*.kt`, MUD-006 templates, git commit/push.

---

## 6. Non-goals

- No AGENTS.md content rewrite (pointer targets only from BOARD/ORCHESTRATION).
- No verify script / Gradle / product / client changes.
- No MUD-006 plan/impl brief template work.
- No MUD-009 git hygiene policy.
- No backfill rewrite of all existing tickets’ frontmatter (template is forward default; optional note only).
- No game_jam ORCHESTRATION novel copy-paste.
- No git commit/push this ticket’s plan turn; impl turn also no push unless drain policy says otherwise later.

---

## 7. How impl confirms acceptance

Checklist (docs-only; `verify:` may be no-op / skip product tests):

- [ ] `rg -n 'agent_eligible|eligibility|needs_jason|phase|plan:|report:|depends_on|worker_out_dir|plan_session|grok_session|codex_session|worker_pid' issues/_templates/ticket.md` — all present
- [ ] Template still has `verify: "./tools/verify_mud.sh"`
- [ ] `issues/ORCHESTRATION.md` exists; line count roughly ≤120; covers serial, plan→approve→fresh, human_gated, spare-capacity, `tmp/workers/<ID>/`, pointers
- [ ] BOARD “How agents use this” links/mentions `issues/ORCHESTRATION.md` and `AGENTS.md`
- [ ] No `*.kt` diffs; no AGENTS body rewrite; no verify script change
- [ ] Ticket → `done` + BOARD → Recently done (impl session bookkeeping)
- [ ] Closeout note: paths changed, residual risk (template optional fields drift)

---

## 8. Ordered impl steps (fresh session)

1. Read this plan + ticket acceptance only (skip CLAUDE/CODEX full packs).
2. Expand `issues/_templates/ticket.md` frontmatter to §3 block; keep body sections lean.
3. Write `issues/ORCHESTRATION.md` per §4; enforce ≤2 screens.
4. Edit BOARD “How agents use this” only: point to ORCHESTRATION + AGENTS; keep drain order untouched.
5. Acceptance grep/line-count self-check (§7).
6. Ticket: `status: done`, `phase: done`, `updated`, short Resolution bullets; clear `worker_pid` if set.
7. BOARD: move MUD-005 to Recently done; clear Plan review / In progress as appropriate.
8. Optional: copy closeout to `tmp/workers/MUD-005/CLOSEOUT.md`. **STOP** — no push, no MUD-006.

---

## 9. Risks

| Risk | Mitigation |
|------|------------|
| ORCHESTRATION grows into novel | Hard length budget; bullets only; link out |
| Template over-spec (every optional field required) | Required = live core; optional listed in Notes |
| Stale BOARD wording (“AGENTS once exists”) | Fix in same surgical edit |
| Drift vs AGENTS wording | ORCHESTRATION restates mechanics; AGENTS remains DoD source of truth |
| Accidental product/AGENTS rewrite | Scope lock §5–6; docs-only paths |
| Impl continues plan session | Process rule: fresh session only after approve |

---

## Handoff

- **Approve:** Astra/Jason stamp this plan.  
- **Impl:** fresh builder; do not resume this plan session for product/docs edits.  
- **Next in Wave A after done:** MUD-006 (brief templates; deps 005).
