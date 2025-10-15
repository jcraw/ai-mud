# CODEX.md

Guidance for the Codex CLI / ChatGPT agent when contributing to this repository.

## How To Use This Doc
- The canonical overview of the system (modules, features, architecture) lives in `CLAUDE.md`. Review that first for shared context and current status.
- `CLAUDE_GUIDELINES.md` contains the house style and engineering principles. They apply equally to Codex—follow them unless the user explicitly overrides something.
- This file adds Codex-specific expectations so we can coexist cleanly with Claude without stepping on each other.

## Collaboration Agreements
- **Never edit `CLAUDE.md` or `CLAUDE_GUIDELINES.md`** unless asked. Those remain Claude-authored sources of truth.
- Keep shared assets (code, docs, configs) neutral so either agent can pick up the work seamlessly.
- When adding automation, scripts, or instructions that reference an agent, prefer neutral wording or list both agents.
- The user runs only one agent at a time, so you do not need to detect or coordinate with Claude at runtime—just avoid assumptions that Claude already performed a step.

## Runtime Integration
- The code already targets OpenAI APIs through `llm/`'s `OpenAIClient`. No additional wiring is necessary for ChatGPT-based reasoning or tooling.
- Respect existing toggles: if `OPENAI_API_KEY` is unset, stay in fallback mode exactly as the current code does.
- If you introduce new LLM calls, expose them through the shared `LLMClient` abstraction so Claude and Codex stay compatible.

## Codex Agent Practices
- Match the direct, technical communication tone expected by the project (see `CLAUDE_GUIDELINES.md`).
- Default to GPT-4o-mini (or the repo’s documented cost-saving model) unless a different model is requested.
- Prefer deterministic behavior where possible—mock LLM interactions in tests and gate high-variance work behind explicit user prompts.
- Leave clear commit-ready diffs: focused changes, descriptive comments only where genuinely helpful, and updated docs/tests alongside feature work.

## When In Doubt
- Re-read `CLAUDE.md` for system design context.
- Ask the user for clarification rather than speculating; do not assume Claude’s historical knowledge unless it is documented.
- Document any Codex-specific caveats (e.g., temporary workarounds) in this file so both agents understand the state of collaboration.
