# Deferred Work

## Deferred from: code review of 11-b-1-reduce-speakerworkflowstate-enum-to-8-states (2026-05-15)

- **No empty-string / whitespace / very-long-value validation for required `String` fields in `SpeakerPromotedToReadyEvent`** [`shared-kernel/src/main/java/ch/batbern/shared/events/SpeakerPromotedToReadyEvent.java:73-104`] — only null is rejected; empty username/email could reach Phase E Cognito mailer. Consistent with sibling events today; revisit as a cross-cutting domain-event validation policy.
- **`Instant.MIN`/`MAX`/stale-date values accepted unchecked for `promotedAt`** [`SpeakerPromotedToReadyEvent.java:102`] — no time-range guard. No sibling event has one either.
- **`Instant` serialisation format not pinned by JSON test** [`SpeakerPromotedToReadyEventTest.java:85`] — test asserts key presence only, not ISO-8601 vs epoch numeric. Test-style concern; downstream consumers will pin when they parse.
- **`eventType` wire-format inconsistency between sibling speaker events** — `SpeakerInvitationSentEvent` uses `"SpeakerInvitationSent"` (no suffix) while `SpeakerResponseReceivedEvent` and the new `SpeakerPromotedToReadyEvent` use the full class name with `Event` suffix. New event matches its cited precedent; outlier is in unmodified code. Sweep cleanup belongs to a future Phase-B/D story before Phase E consumes routing.
- **PII (`email`, `username`) in domain event payload without redaction-on-log policy** [`SpeakerPromotedToReadyEvent.java:62-65`] — cross-cutting policy decision affecting all domain events; not a 11.B.1 regression.
- **JSON serialisation test uses substring-contains rather than structural assertions** [`SpeakerPromotedToReadyEventTest.java:80-87`] — project pattern across all event tests.
- **AC9 self-reference gap: allow-list does not enumerate the story file itself, yet the story file is `MM` in git status (Tasks/Subtasks + Dev Agent Record updates)** — spec template tweak, not a code defect.

## Deferred from: code review of 11-a-1-align-speaker-workflow-documentation-to-adr-009 (2026-05-15)

- **Mermaid `<br/>` inside stateDiagram-v2 edge labels may not render** [`docs/architecture/06a-workflow-state-machines.md:994-1012`] — needs visual smoke test in the docs site; older Mermaid renderers may emit `<br/>` literally inside edge labels. Cosmetic.
- **Speaker-coordination-service ownership ambiguity** — `/api/v1/speaker-portal/**` host vs. `speaker_pool` table vs. workflow ownership is not spelled out in one place across `04-api-design.md`, `04-api-speaker-coordination.md`, and `06b-user-lifecycle-sync.md` (Pattern N). Architectural question to resolve during Phase B implementation.
- **`session_speakers` narrative mixes target-tense and current-tense** [`docs/architecture/03-data-architecture.md:303`, `docs/architecture/06a-workflow-state-machines.md:1112`] — "There is no `session_speakers` junction table" reads as already-deleted, but Phase A is doc-only. Minor cosmetic; revisit when Phase B physically drops the table.
- **26 `ADR-009 §0.x` citations point to nonexistent sections** — ADR-009 has no `§0` numbered subsections (only Decisions 1, 2, 3). The story's AC text introduced the §0.1/§0.2/§0.3/§0.5/§0.7 convention to disambiguate target-model topics (states/auth/etc.), and the dev consistently used these placeholders across 26 hits. Either (a) restructure ADR-009 to add a `§0 Target Model` section with numbered subsections matching the citations, or (b) sweep-replace each citation with actual ADR-009 section references. Follow-up story under Phase A cleanup or as part of Phase B doc tidy.
