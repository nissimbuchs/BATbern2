---
title: 'Task trigger states offered are the ones the backend accepts'
type: 'bugfix'
ticket: '3'
created: '2026-11-15'
status: 'done'
baseline_revision: '0b66160de789d266fae0b8a41a581590290b671b'
route: 'oneshot'
route_source: 'auto'
risk: 'low'
review: 'quick'
review_source: 'pinned'
lenses_ran: ['quick']
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** The backend accepts a trigger only if it is a lower-case `EventWorkflowState` or `speakers_published` (`TaskTemplateService.VALID_TRIGGER_STATES`). The create-task dialog offers three invented states and misses five real ones; the edit-template dialog offers upper-case invented states, so no template can be saved with a changed trigger (#1218).

**Approach:** One shared list derived from the generated `EventWorkflowState` (+ `speakers_published`), lower case, used by both dialogs with `events:tasks.workflowStates.*` labels in 10 locales; tests compare the offered values with the spec's enum and save a changed trigger through the MSW harness.

</frozen-after-approval>

## Implementation Notes

Oneshot. Architecture note: frontend task dialogs only; new module `components/organizer/Tasks/triggerStates.ts` (a `Record<EventWorkflowState, true>` so a new spec state fails the type check); no endpoint or dependency change; no deviation.
- Labels: the 5 missing keys reuse each locale's `workflow.states` text; `speakers_published` translated by hand in 10 locales. `speaker_brainstorming` and `post_event_wrap_up` removed (no row holds them in the local prod mirror); `speaker_outreach` kept because one custom template ("Eigener") and one done task hold it.
- Legacy value: the edit dialog shows an invalid stored trigger as empty and disables Save until a valid one is picked. No data change.
- Found on the way, fixed: the edit dialog's Select labels were not linked (`labelId`), so the fields had no accessible name.
- Tests: `TaskTemplateEditModal.test.tsx` moved to real i18next and MSW (ratchet -2); new `CustomTaskModal.test.tsx`. RED measured for the create dialog (old file fails); for the edit dialog not measured separately (old values were upper case and invented).
- User guide `administration/task-templates.md`: the out-of-date warning replaced.

## Verification

- `npx vitest run src/components/organizer/Tasks src/components/organizer/Admin src/components/organizer/EventPage` -- 54 files, 523 tests green.
- type-check, eslint, `check:test-ratchet`, `make check-i18n-keys` -- clean.

## Review Triage Log

Quick review, 5 findings: 4 patched, 1 rejected.
- false, rejected: `validTrigger` missing from the effect's dependencies would fail lint. `npx eslint src/components/organizer --max-warnings 0` passes (measured).
- low, patched: a legacy trigger gave no visible reason for the disabled Save. The Select is `required` with an error state and a helper text ("Trigger state is required"); the test asserts it appears and goes away.
- low, patched: the test claimed to read the backend's set but reads the spec enum, and the helper was copied twice. One helper `src/test/triggerStates.ts` says what it reads and why it matches.
- low, patched: gsw-BE and ja `speakers_published` used other terms than their neighbours ("Redner veröffentlecht", "スピーカー公開").
- low, patched: the template list showed raw trigger values. Its chips use the same labels; `TaskTemplatesTab.test.tsx` moved to MSW, real i18n, real dialogs (ratchet -4 more).
