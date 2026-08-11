# CODEX.md

> **Status: optional deep / historical — not ops SoT.**
> - **Ops, DoD, startup reads:** `AGENTS.md` (all agents: Grok, Codex, Claude, Cursor, humans).
> - **This file:** Codex-specific runtime notes only. **Do not full-read by default.**
> - Optional deep / historical status: `CLAUDE.md` (never full-read by default) or `docs/*` when the ticket needs them.
> - Architecture detail when needed: `docs/ARCHITECTURE.md` and other `docs/*`.
> - Do **not** grow this file as the agent contract; freeze Claude/Codex-only surface growth.
> - Local Claude tool perms (`.claude/settings.local.json`) are **not** DoD — leave local; do not expand as project contract.

Guidance for the Codex CLI / ChatGPT agent when contributing to this repository.

## How To Use This Doc
- **Ops, DoD, and startup reads live in `AGENTS.md`.** Start there for every session.
- Optional deep / historical status may exist in `CLAUDE.md` or `docs/*` — load only when the ticket needs that depth; never full-read CLAUDE by default.
- `CLAUDE_GUIDELINES.md` is engineering style (KISS, testing philosophy) — useful, not a mandatory startup pack or ops SoT.
- This file adds Codex-specific runtime notes so Codex can coexist cleanly with other agents.

## Collaboration Agreements
- Prefer **neutral shared docs** (`AGENTS.md`, `docs/*`, tickets, plans). Do not treat Claude-only files as project SoT.
- Edits to `CLAUDE.md` / `CLAUDE_GUIDELINES.md` are rare and **ticket-scoped** (e.g. deprecation banners); do not grow them as the agent contract.
- Keep shared assets (code, docs, configs) neutral so any agent can pick up the work seamlessly.
- When adding automation, scripts, or instructions that reference an agent, prefer neutral wording or list all agents.
- The user runs only one agent at a time — avoid assumptions that another agent already performed a step.

## Runtime Integration
- The code already targets OpenAI APIs through `llm/`'s `OpenAIClient`. No additional wiring is necessary for ChatGPT-based reasoning or tooling.
- Respect existing toggles: if `OPENAI_API_KEY` is unset, stay in fallback mode exactly as the current code does.
- If you introduce new LLM calls, expose them through the shared `LLMClient` abstraction so all agents stay compatible.

## Codex Agent Practices
- Match the direct, technical communication tone expected by the project (see `CLAUDE_GUIDELINES.md` for style; `AGENTS.md` for DoD).
- Default to GPT-4o-mini (or the repo’s documented cost-saving model) unless a different model is requested.
- Prefer deterministic behavior where possible—mock LLM interactions in tests and gate high-variance work behind explicit user prompts.
- Leave clear commit-ready diffs: focused changes, descriptive comments only where genuinely helpful, and updated docs/tests alongside feature work.

## When In Doubt
- Re-read **`AGENTS.md`**, the **active ticket**, and **`docs/*`** as needed — not a full re-read of CLAUDE.
- Ask the user for clarification rather than speculating; do not assume another agent’s historical knowledge unless it is documented.
- Document any Codex-specific caveats (e.g., temporary workarounds) in this file so all agents understand collaboration state.
