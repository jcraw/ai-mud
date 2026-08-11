---
id: MUD-002
area: tooling
title: Research — Kotlin AI-native quality gates + strong unit tests for AI MUD
status: done
priority: high
created: 2026-08-08
updated: 2026-08-09
source: jason
labels: [research, quality-gates, unit-tests, spike]
spike: true
assignee: jason
worker: ""
phase: done
agent_eligible: false
eligibility: done
needs_jason: ""
depends_on: [MUD-001]
ready_prompt: docs/research/2026-08-08-ai-mud-kotlin-quality-gates-unit-tests_gemini_deep_research_prompt.READY.txt
library_prompt: /run/media/j/M2MegaStore/Code/Ai/library_of_craw/prompts/2026-08-08-ai-mud-kotlin-quality-gates-unit-tests.READY.txt
report: /run/media/j/M2MegaStore/Code/Ai/library_of_craw/digests/DIGEST-025-ai-mud-kotlin-quality-gates-unit-tests.md
digest: DIGEST-025
note: NOTE-025
evidence: EV-025
library_status: draft
---

# MUD-002 — Research: Kotlin quality gates + strong unit tests

## Status
**Done 2026-08-09** — research accepted as north star; implement via MUD-010…015 (and 017). DIGEST-025 remains draft until citation audit (non-blocking for scaffolding).

## Deliverables
- DIGEST-025 / NOTE-025 / EV-025 in library_of_craw
- Product pointer: `docs/research/DIGEST-025-kotlin-quality-gates-POINTER.md`
- READY prompt on disk (used)

## Decision snapshot (draft)
- No coverage theater; PIT mutation on pure modules; test locks; Konsist arch; Detekt; quarantine `:reasoning` reds; tiered verify + DoD JSON
- 30d foundation → 60d mutation/PBT → 90d quarantine clear

## Next (after Jason OK)
File implement tickets from DIGEST-025 30/60/90 (renumber into MUD-NNN; align with MUD-001 tooling wave).

## Acceptance
- [x] READY on disk
- [x] Browser staged + Jason ran research
- [x] Raw captured + library filed draft
- [ ] Jason promotes draft / picks implement order
