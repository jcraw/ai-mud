# ORCHESTRATION — short ops note

**Not product architecture.** Drain mechanics for spare-capacity work.

| Doc | Role |
|-----|------|
| `AGENTS.md` | Ops contract + Definition of Done |
| `issues/BOARD.md` | Queue, drain order, ticket status |
| **This file** | How builders move tickets through the pipeline |
| `tools/verify_mud.sh` | Default `verify:` command (MUD-004) |
| `issues/_templates/ticket.md` | Ticket frontmatter scaffold |
| `issues/_templates/plan-brief.md` · `implement-brief.md` | Plan/impl session briefs (fill → `tmp/workers/<ID>/`) |

---

## Posture

- **Spare capacity only** — background / low-stakes; do not compete with jam/job/money work.
- **Slow drip** — clear the backlog over time; no rush.
- **One problem per ticket** — no drive-by refactors or scope creep.
- See BOARD “Posture” for Jason’s standing notes.

---

## Serial builder (one live per tree)

- **One** live Grok / Codex / Claude / Cursor implementer per working tree.
- Queue others; do **not** run parallel product (or overlapping docs) impl in the same checkout.
- One builder session per ticket when possible.

---

## Plan → approve → fresh impl

For **substantial** work:

1. Fill `issues/_templates/plan-brief.md` → `tmp/workers/<ID>/PLAN_BRIEF.md`; write plan under `plans/YYYY-MM-DD-…` (mirror `tmp/workers/<ID>/PLAN.md`).
2. **Astra or Jason** stamps **APPROVED**.
3. Fill `issues/_templates/implement-brief.md` → `tmp/workers/<ID>/IMPL_BRIEF.md`; start a **fresh** impl session — do **not** resume the plan session for product/docs edits unless the ticket explicitly allows it.

Trivial one-shot docs/tooling may skip formal plan when the ticket says so.

---

## human_gated ≠ done

- Spikes, opinion, playtest, and `needs_jason: action|playtest|opinion` stay **gated**.
- Do **not** mark `done` without a real deliverable (or explicit Jason close).
- `eligibility: human_gated` is not a fake green.

---

## Worker dirs

- Active work: `tmp/workers/<ID>/` — briefs, `PLAN.md`, logs, pids, closeout notes.
- Not committed product; safe to wipe between tickets when done.

---

## Verify

- Ticket `verify:` defaults to `./tools/verify_mud.sh`.
- Full DoD and lane table: **`AGENTS.md`**.
- Docs-only tickets: greps / line checks may suffice; product test run optional when ticket says so.

---

## Agent parity

Grok, Codex, Claude, Cursor — same contract, same board, same verify expectations. Prefer neutral wording in new tickets and notes.

---

## Out of scope here

- Git dirty-tree hygiene → **MUD-009**
- `AGENTS.md` rewrites (unless a ticket owns that)
- CI / launcher internals essays
