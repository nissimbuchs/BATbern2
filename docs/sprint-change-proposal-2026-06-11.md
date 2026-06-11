# Sprint Change Proposal — Story 7.2 → ADR-012 (`session_proposals`)

**Date**: 2026-06-11
**Author**: Winston (architect) + Nissim (PO)
**Trigger**: Local-dev testing of Epic 7 surfaced that Story 7.2 stored the self-nomination's
proposed title/abstract as content columns on `speaker_pool` (V109), reversing the 11.E.8
normalization. Plus a live promote bug: a non-SPEAKER self-nominee can't be selected in the
promote picker.
**Scope classification**: **Moderate** — schema rewrite + write/read paths + promote flow + a
guard test, on a story currently marked `done`. Branch `feat/epic-7-attendee-contribution` is
**unpushed**; nothing deployed.
**Routed to**: Amelia (dev) for implementation.

## 1. Issue Summary

Story 7.2 ("I Could Speak on That") let a logged-in attendee self-nominate, creating a
`speaker_pool` row at `IDENTIFIED`. The proposed talk (`sessionTitle` + `abstract`) was stored
via V109 as four new columns on `speaker_pool`: `source`, `proposed_by_username`,
`proposed_session_title`, `proposed_abstract`.

Two problems:

1. **Normalization regression.** `proposed_session_title` / `proposed_abstract` are *content* on
   the *workflow-state* table — the exact thing Epic 11.E.8 / V103 removed (`username`/`email`).
   NULL for ~99% of rows; an open invitation to re-denormalize.
2. **Promote picker bug.** `PromoteSpeakerSubView.tsx` restricts the user picker to `SPEAKER`-role
   users (`role="SPEAKER"` + a `roles?.includes('SPEAKER')` prefill filter). A self-nominee is an
   `ATTENDEE`, so they can't be selected → forces "Create new speaker" → **duplicate user**.

Also: the `CONTACTED → READY` hook writes a placeholder session title (the speaker name) and drops
the abstract, so the proposed content is never carried into the canonical session — the V109
columns are currently dead parallel state.

## 2. Impact Analysis

- **Epic**: `docs/prd/epic-7-attendee-experience-enhancements.md` — FR5–FR7 unchanged in intent;
  storage mechanism corrected. No FR rewrite needed.
- **Story 7.2**: reopened from `done`; ACs 2/7/8 + Tasks 1/2/5/6 amended; new tasks for the
  proposal table, promote-picker fix, carry-into-session, and the guard test.
- **Architecture**: **ADR-012** (new) records the decision; `project-context.md` gains the
  "no content on `speaker_pool`, ever" rule.
- **Schema**: V109 rewritten (dev-only exception — unpushed, single dev DB; rolled back on dev).
- **Code**: backend (migration, self-nominate service, kanban read, READY hook) + frontend
  (`PromoteSpeakerSubView.tsx`) + a schema-fitness test.
- **No deployment / no cross-service / no API-contract break beyond the 7.2 endpoints** (the
  self-nominate request body is unchanged; only persistence + organizer-read shape moves).

## 3. Recommended Approach

**Direct Adjustment** (no rollback of unrelated work, no MVP scope change): rewrite V109 to the
ADR-012 schema, move the proposal content to `session_proposals`, fix the promote picker, and wire
the carry-into-session at READY. Effort: ~half-day dev. Risk: low (isolated to 7.2 surfaces;
nothing deployed). The promote-picker fix is independently valuable and ships in the same change.

## 4. Detailed Change Proposals

See the amended story file `_bmad-output/implementation-artifacts/7-2-i-could-speak-on-that.md`
(section "ADR-012 Amendment (2026-06-11)") for the concrete AC/task edits. Summary:

- **Migration (V109 rewrite)**: `speaker_pool` gets only `source`; new `session_proposals`
  (`id`, `speaker_pool_id` FK, `event_id`, `proposed_by_username`, `proposed_title`,
  `proposed_abstract`, `created_at`, `UNIQUE(event_id, proposed_by_username)`).
- **Self-nominate**: write a `session_proposals` row + the IDENTIFIED `speaker_pool` row;
  one-per-event uniqueness enforced on `session_proposals`.
- **Kanban read**: left join `speaker_pool → session_proposals` for the proposed talk.
- **Promote picker** (`PromoteSpeakerSubView.tsx`): drop `role="SPEAKER"` + the SPEAKER-only
  prefill filter; for `source=self_nomination`, auto-prefill from `proposed_by_username`.
- **READY hook**: seed `sessions.title` = `proposed_title` and the first content submission =
  `proposed_abstract`; proposal row becomes immutable audit.
- **Guard**: schema-fitness test — `speaker_pool` has no title/abstract/materials columns.

## 5. Implementation Handoff

- **Recipient**: Amelia (dev).
- **Success criteria**:
  - `speaker_pool` carries no content columns; `session_proposals` holds the pitch.
  - Self-nominee (ATTENDEE) selectable in the promote picker; promoting grants SPEAKER to the
    existing user (no duplicate); session title + abstract pre-populated from the proposal.
  - Schema-fitness test green; existing 7.2 backend/frontend tests updated and green;
    type-check + lint clean.
  - V109 rolled back on the dev DB and re-applied clean.
