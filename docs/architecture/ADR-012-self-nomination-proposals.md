# ADR-012: Pre-READY Speaker Proposals Live in `session_proposals`, Never on `speaker_pool`

**Status**: Accepted
**Date**: 2026-06-11
**Decision Makers**: Winston (architect), Nissim (PO)
**Related ADRs**: ADR-009 (Unified Speaker Workflow), ADR-003 (Meaningful Identifiers), ADR-004 (Factor User Fields from Domain Entities)
**Related Story**: Story 7.2 "I Could Speak on That" (`_bmad-output/implementation-artifacts/7-2-i-could-speak-on-that.md`)
**Supersedes**: the V109 schema shape (4 columns added to `speaker_pool`) — see Consequences.

## Context

Story 7.2 lets a logged-in `ATTENDEE` self-nominate as a speaker. A self-nomination enters
the speaker workflow at `IDENTIFIED` (ADR-009: `READY` is the provisioning gate, organizer-only)
and carries a **proposed talk** — `sessionTitle` + `abstract` — that the attendee wrote.

The first implementation (Flyway **V109**) stored that proposal by adding four columns to
`speaker_pool`: `source`, `proposed_by_username`, `proposed_session_title`, `proposed_abstract`.

This collides head-on with the normalization established in **Epic 11.E.8 / ADR-009**:

- `speaker_pool` is the **workflow state machine only**. Content — title, abstract, materials —
  lives in `session_users` + `content_submissions` / `session_content_history`.
- V103 deliberately **dropped** `speaker_pool.username` / `.email` to enforce that line;
  canonical identity post-READY is `PrimarySpeakerResolver.resolve(pool)`.

So `proposed_session_title` / `proposed_abstract` are **content back on the state table** — the
exact thing 11.E.8 removed. They are also NULL for ~99% of rows (every organizer-added candidate),
i.e. a sparse content annex bolted onto the state machine. The risk is concrete: the next
contributor sees content columns on `speaker_pool` and treats it as a sanctioned place to hang
more content, unwinding the normalization.

The competing constraint: a self-nomination is **not yet a session**. `sessions` + `session_users`
rows are created only at the `CONTACTED → READY` hook. Creating a real session per nomination
would flood the canonical tables with unvetted applicants. So the proposal genuinely has no
home in the canonical post-READY tables until an organizer accepts it.

## Decision

Model a self-nomination for what it is — an **application / pitch**, distinct from both the
speaker (workflow) and the session (content) — and give it its own table.

### Schema

```
session_proposals
  id                   uuid          PK
  speaker_pool_id      uuid          FK → speaker_pool(id)   -- pointer; intra-service, no cross-service FK
  event_id             uuid                                  -- intra-service
  proposed_by_username varchar(100)  -- ADR-003 meaningful id (who pitched)
  proposed_title       varchar(255)
  proposed_abstract    text
  created_at           timestamptz   NOT NULL
  UNIQUE (event_id, proposed_by_username)                    -- one self-nomination per attendee per event
```

`speaker_pool` keeps **only `source`** (`organizer_added` | `self_nomination`) — provenance
metadata, not content; it lets the kanban badge/filter without a join. It does **not** keep
`proposed_by_username`, `proposed_session_title`, or `proposed_abstract`. The FK points *at*
`speaker_pool`, so `speaker_pool` gains nothing content-shaped.

### Three rules that keep this stable

1. **No second state machine.** `session_proposals` carries **no status of its own.** Acceptance /
   rejection is read from the linked `speaker_pool` row (`IDENTIFIED → … → READY` = accepted;
   `DECLINED` = rejected). The single source of workflow truth remains
   `SpeakerWorkflowService.transition(...)`.
2. **Promote is the hand-off point.** At `CONTACTED → READY`, after the session is created, the
   READY hook (`SpeakerWorkflowService.provisionSessionAndPrimarySpeaker`) looks up
   `session_proposals` by `speaker_pool_id`; if present it seeds `sessions.title` =
   `proposed_title` and the first content submission = `proposed_abstract`. From that instant the
   **session is canonical**; the proposal row becomes **immutable audit** ("what was pitched",
   distinct from what the session became). No competing source of truth post-READY.
3. **No content on `speaker_pool`, ever.** Pre-READY pitches live in `session_proposals`;
   post-READY content lives in `session_users` / `content_submissions`. `speaker_pool` is state
   (+ the `source` provenance flag) only.

### Guard against re-denormalization

A code comment will not stop the next agent. Two durable guards:

- **This ADR + a line in `_bmad-output/project-context.md`** stating the rule in rule 3 above.
- **A schema-fitness test** asserting `speaker_pool` has no content columns (title / abstract /
  materials). It fails in CI, not in review, if someone re-adds them.

## Consequences

- **V109 is rewritten, not patched forward.** Nothing is deployed (branch
  `feat/epic-7-attendee-contribution` is unpushed; staging/prod never saw V109). As a
  **dev-only exception** to the "never edit an applied migration" rule, the dev DB's V109 is
  rolled back and V109 is re-authored to (a) add only `source` to `speaker_pool` and (b) create
  `session_proposals`. This is permissible **only** because V109 exists nowhere but one dev
  database. Once this branch is pushed, V109's content is frozen forever per the standard rule.
- The self-nominate write path creates a `session_proposals` row + the `speaker_pool` row
  (IDENTIFIED, `source='self_nomination'`); the one-per-event uniqueness moves to the
  `session_proposals` unique constraint.
- The organizer kanban reads the proposed talk via a left join `speaker_pool → session_proposals`.
- The promote picker must allow selecting a non-SPEAKER user (the self-nominee is an `ATTENDEE`);
  `provisionUserWithRole` already grants SPEAKER to an existing user idempotently by email, so no
  duplicate user is created. (Tracked in Story 7.2's amendment, not this ADR.)

## Alternatives considered

- **A — keep `proposed_*` on `speaker_pool`, carry into the session on promote.** Cheapest now,
  but reverses the 11.E.8 normalization and leaves the content-on-state-table landmine. The
  carry-into-session step is required either way, so A's only saving over this decision is "no new
  table" — which, with nothing deployed, is negligible. Rejected.
- **Rule of Three / YAGNI (don't add a table yet).** Doesn't apply: the separation is justified by
  the *existing* normalization invariant, not by speculative reuse. We're not abstracting ahead of
  need; we're refusing to violate a line already drawn.
