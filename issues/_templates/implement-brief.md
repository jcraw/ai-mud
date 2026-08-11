# {{ID}} — IMPLEMENT (fresh session)

**Fresh session.** Do not assume prior CLI memory.
**Plan already APPROVED** at:
- `{{PLAN_PATH}}`
- mirror: `{{WORKER_OUT}}/PLAN.md`

This message **is** plan approval + implement authorization.

<!-- Fill: copy to tmp/workers/{{ID}}/IMPL_BRIEF.md; replace all {{…}} tokens. Target filled ≤~40 lines. -->

## Do this turn
1. Read the full approved plan + ticket `{{TICKET_PATH}}` acceptance only (not full `CLAUDE.md` / `CODEX` packs).
2. Execute the plan:
{{DO_THIS}}
3. Acceptance self-check (plan acceptance checklist / greps).
4. Closeout: check ticket acceptance boxes; set ticket `status: done`, `phase: done`, clear `worker_pid`; move BOARD `{{ID}}` → Recently done (surgical). Optional `{{WORKER_OUT}}/CLOSEOUT.md`.

## Rules
- Follow approved plan only. No redesign. No re-plan unless blocked — then stop and set `plan_review` with note.
- Serial **one live builder per tree**. No secrets; no force-push.
- No git commit/push unless Jason asks.
- Worker out: `{{WORKER_OUT}}/` (`tmp/workers/{{ID}}`) — briefs, PLAN, logs, CLOSEOUT.
- Console + GUI parity when ticket touches engine/client handlers (skip if docs/ops only).
- Do not wait for further chat approval. **STOP when done.**

## Ticket
`{{TICKET_PATH}}`

## Non-goals
{{NON_GOALS}}

## verify (post-impl)
- `{{VERIFY}}`
- Plan checklist greps / line checks as written in the approved plan
