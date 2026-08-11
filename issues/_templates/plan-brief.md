# Task: {{ID}} PLAN ONLY — {{TITLE}}

You are a builder in a **fresh** session (Turn 1 — plan phase).

<!-- Fill: copy to tmp/workers/{{ID}}/PLAN_BRIEF.md; replace all {{…}} tokens. Target filled ≤~80 lines. -->

## Phase
**PLAN ONLY.** Do **not** implement product or ship code this turn. Bookkeeping + plan files only.

This brief is **not** implement/ship approval. Handoff = plan file for a **later fresh impl session**.

## Ticket
`{{TICKET_PATH}}`

## Repo / cwd
`{{REPO}}`

## Depends / verify
- depends_on: `{{DEPENDS}}`
- verify (post-impl): `{{VERIFY}}`

## Read first (lean ≤~6k)
- Full ticket (acceptance + problem only if short)
- `AGENTS.md` — Workflow / DoD / serial-builder headings only
- `issues/ORCHESTRATION.md` if ops/tooling ticket
- Ticket-relevant files only (paths from acceptance / inventory)
- `{{READ_EXTRA}}`

**NEVER full-read by default:** `CLAUDE.md`, `CODEX.md`, `CLAUDE_GUIDELINES.md`, whole `BOARD.md` as pack, DIGEST bodies, full worker logs, game_jam ORCHESTRATION novels.

## Intent (binding)
{{INTENT}}

## Non-goals
{{NON_GOALS}}

## Deliverable
Write plan to **both**:
1. `{{PLAN_PATH}}` (under `plans/`; use date `{{DATE}}` in filename)
2. `{{WORKER_OUT}}/PLAN.md` (mirror; worker out = `tmp/workers/{{ID}}`)

**Plan size ≤~2k tok.** Dense bullets.

Required plan sections:
1. Goal / acceptance mapping
2. Current inventory
3. Design / recommended approach
4. Files to create/touch
5. Non-goals
6. How impl confirms acceptance (checklist)
7. Ordered impl steps
8. Risks

Worker artifacts live under `{{WORKER_OUT}}/` (`PLAN_BRIEF`, `PLAN`, logs, later `IMPL_BRIEF` / `CLOSEOUT`).

## After plan written
1. Ticket frontmatter: `status: plan_review`, `updated: {{DATE}}`, `plan: {{PLAN_PATH}}`, `worker_out_dir: {{WORKER_OUT}}`, `phase: planning` (or `plan_review`)
2. BOARD → plan_review for `{{ID}}` (surgical only)
3. **STOP. No implementation.**

## Constraints
- Serial **one live builder per tree**
- No secrets in plans/logs; no force-push / history rewrite
- No product/`*.kt` unless ticket says so
- No git commit/push unless Jason asks
- Do not resume this session for impl — Astra/Jason approve, then **fresh** impl brief
