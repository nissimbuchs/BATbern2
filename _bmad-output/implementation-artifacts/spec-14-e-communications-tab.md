---
title: 'Epic 14 Phase E — Communications tab (4-audience compose surface)'
type: 'feature'
created: '2026-06-14'
baseline_commit: 'f16ec168'
status: 'done'
context:
  - '{project-root}/docs/prd/epic-14-event-detail-redesign.md'
  - '{project-root}/_bmad-output/project-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Phase A already consolidated Newsletter + Registrant Notices + Venue & Caterer into `EventCommunicationsContainer`, but as **3 internal MUI sub-tabs** — and there is **no Speakers audience**, even though `useSendReminder` exists wired to no UI (FR29–FR34). Outbound speaker reminders still can't be sent from the app.

**Approach:** Frontend-only recompose (AR9). Turn the container's 3 sub-tabs into a **4-audience switch** (📰 Newsletter subscribers · 🎟️ Event registrants · 🎤 Speakers · 🏛️ Venue & Caterer), mounting the three existing children unchanged and adding a **new Speakers audience** (`SpeakerBulkComms`) that surfaces the existing `useSendReminder` hook as a confirm-gated bulk send. No backend change (NFR9).

## Boundaries & Constraints

**Always:**
- Frontend-only; touch only `web-frontend/`. Reuse hooks/services/children unchanged (AR9). Service layer only, `@/` alias, generated types, `config` objects (NFR8).
- Every new/changed user-facing string via `useTranslation('events')` in **all 10 locales** (`de,en,fr,it,rm,es,fi,nl,ja,gsw-BE`), EN+DE first-class (NFR5).
- New surfaces (audience switch, Speakers compose) keyboard-operable + ARIA-labelled, WCAG 2.1 AA (NFR7).
- **Every send is consequential → a confirm dialog (recipient count + type + event title) opens BEFORE any send call, and the Send button is disabled while in flight** so a double-click can't double-send (NFR1). Tests use no real recipients.

**Ask First:**
- If the Speakers audience seems to need a new backend reminder type, a bulk-send endpoint, or any API/migration edit → HALT. The beta-first dual-serve constraint (NFR9) forbids it.

**Never:**
- **No backend.** The Speakers audience surfaces ONLY the `RESPONSE` / `CONTENT` reminder types the existing 1:1 `useSendReminder` supports; "logistics & arrival" and "thank-you" speaker comms (FR32 wording) need new backend reminder types + a bulk endpoint → **deferred** to `deferred-work.md`, not built here.
- Don't rewrite the Newsletter / Registrant / Venue children or extract a shared compose abstraction — FR34's "common compose form" is satisfied by their existing equivalent forms; recompose only.
- Don't move per-speaker 1:1 invitation/reminder off the speaker card (workflow-bound, stays in `SpeakerStatusLanes`/`SpeakerDetailDrawer`).
- Out of scope: the `tabBadges.ts` `COMMS_TASK_PATTERN` → first-class-category rework (epic post-impl follow-up); mobile reshape (Phase G).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Audience switch | 4 options (newsletter·registrants·speakers·venue) | newsletter/registrants/venue render the existing children unchanged; speakers renders `SpeakerBulkComms` | — |
| Speakers — reminder type RESPONSE | select RESPONSE | recipients = pool entries `status === 'INVITED'`; count shown | empty → Send disabled with reason |
| Speakers — reminder type CONTENT | select CONTENT | recipients = pool entries `status === 'ACCEPTED'`; count shown | empty → Send disabled with reason |
| Speakers — send bulk | click Send | **confirm dialog opens first** (count + reminder type + event title); on confirm loops `useSendReminder` per recipient `id`; button pending/disabled during the run | per-speaker failure → partial summary "n sent · m failed" + retry-failed; no silent/duplicate send |
| Speakers — double-click Send | rapid clicks | 2nd click is a no-op (disabled while pending) | — |
| Speakers — pool still loading / fetch error | `useSpeakerPool` loading/error | spinner / inline error + retry; Send disabled | — |

</frozen-after-approval>

## Code Map

- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx` — 3 sub-tabs → **4-audience switch**; add `'speakers'` to `CommSubView`; new audience labels/icons (📰/🎟️/🎤/🏛️) via `eventPage.communications.audiences.*`; mount `<SpeakerBulkComms eventCode eventTitle />` for the speakers audience. `data-testid="comms-subtab-speakers"`.
- `web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx` — **NEW** Speakers audience. `useSpeakerPool(eventCode)` for the list; reminder-type select (RESPONSE→INVITED / CONTENT→ACCEPTED); recipient count; confirm dialog (NFR1); bulk-loop `useSendReminder` over recipient `id`s with pending state + partial-failure summary + retry-failed. Reuse the `useBreakpoints` `fullScreen={isMobile}` dialog shape from the sibling tabs.
- `web-frontend/src/hooks/useSpeakerPool.ts` — **read only**: `useSpeakerPool` (list) + `useSendReminder({ speakerPoolId, request:{ reminderType, tier? } })`.
- `web-frontend/src/components/organizer/EventPage/__tests__/SpeakerBulkComms.test.tsx` — **NEW**.
- `web-frontend/src/components/organizer/EventPage/__tests__/EventCommunicationsContainer.test.tsx` — **NEW or extend**: 4 audiences present; switching renders the right surface.
- `web-frontend/public/locales/{10}/events.json` — `eventPage.communications.audiences.*` (4 labels) + `eventPage.speakerComms.*` (reminder-type labels, recipient-count, send, confirm title/body, partial-result, empty-reason). Reuse `common:actions.cancel`, `common.confirm`.

## Tasks & Acceptance

**Execution:**
- [x] `EventCommunicationsContainer.tsx` — added the `'speakers'` audience; relabelled the switch to the 4 redesign audiences (📰/🎟️/🎤/🏛️ + `eventPage.communications.audiences.*`), order Newsletter·Registrants·Speakers·Venue; mounts `SpeakerBulkComms`; newsletter/registrant/venue children unchanged.
- [x] `SpeakerBulkComms.tsx` (new) — reminder-type select (RESPONSE→INVITED / CONTENT→ACCEPTED), status-filtered recipients + count, confirm-gated bulk loop over `useSendReminder.mutateAsync`, disabled-while-sending, partial-failure summary + retry-failed, empty/loading/error states.
- [x] `events.json` ×10 — added `eventPage.communications.audiences.*` (4) + `eventPage.speakerComms.*` (15), EN+DE first-class, other 8 translated; reused `common:actions.cancel`/`common.confirm`.
- [x] tests — `EventCommunicationsContainer` (4 audiences present + switch routing, default newsletter); `SpeakerBulkComms` (per-type recipient filtering, confirm-opens-before-send/NFR1, bulk-loop send, partial-failure + retry-failed, empty-disabled, load-error). 9 tests green.

**Acceptance Criteria:**
- Given the Communications tab, when it renders, then it shows a 4-audience switch and the standalone Newsletter/Registrant/Venue tabs do not appear as top-level tabs (FR29; already removed in Phase A — assert it stays so).
- Given any audience, when selected, then newsletter/registrants/venue reuse their existing behaviour unchanged (FR30/FR31/FR33) and Speakers shows the bulk-reminder surface (FR32).
- Given a bulk speaker send, when Send is clicked, then a confirm dialog (count + type + event title) opens before any call, the button is disabled while sending, and a per-speaker failure yields a partial summary + retry — never a silent or duplicate send (NFR1).
- Given the diff, when reviewed, then it touches only `web-frontend/` and introduces no backend change (NFR9/AR9).
- Given new strings, when reviewed, then all are `useTranslation`-driven and present in all 10 `events.json` (NFR5).

## Design Notes

**Reminder-type constraint (the one product call to confirm):** `useSendReminder` is 1:1 (`speakerPoolId`) and supports only `RESPONSE` and `CONTENT`. Under NFR9 (no backend) the Speakers audience therefore offers exactly two reminders — **Response-deadline → `INVITED` speakers**, **Content-deadline → `ACCEPTED` speakers** — sent by looping the 1:1 hook client-side. The FR32 "logistics & arrival / thank-you" comms need new backend reminder types + a bulk endpoint and are deferred (recorded in `deferred-work.md`). Bulk loop sketch:
```ts
for (const s of recipients) {            // recipients = pool.filter(status match)
  try { await sendReminder.mutateAsync({ speakerPoolId: s.id, request: { reminderType } }); ok++; }
  catch { failed.push(s); }              // collect, report "ok sent · failed.length failed", offer retry-failed
}
```

## Verification

**Commands:**
- `cd web-frontend && npm run type-check` -- expected: no errors.
- `cd web-frontend && npx vitest run src/components/organizer/EventPage` -- expected: container + SpeakerBulkComms suites green.
- `cd web-frontend && npm run lint -- --max-warnings 50` -- expected: passes.
- new `events.json` keys present in all 10 locales (manual diff or i18n check).

**Manual checks:**
- Communications tab shows 4 audiences; 🎤 Speakers lets you pick a reminder type, shows the recipient count, requires a confirm, and reports partial failures; the other three audiences behave exactly as before.

## Suggested Review Order

**The audience switch (14.E.1/14.E.2 — design intent)**

- Entry point: 3 sub-tabs → a 4-audience switch; the three existing children mount unchanged, Speakers is the only new mount.
  [`EventCommunicationsContainer.tsx:26`](../../web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx#L26)
- The new Speakers audience is mounted in-place — Venue (14.E.2) was already here from Phase A, so this is a relabel + one addition.
  [`EventCommunicationsContainer.tsx:76`](../../web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx#L76)

**Speakers bulk reminders (14.E.3 — the real new build)**

- Recipient filtering: reminder type → target status (RESPONSE→INVITED, CONTENT→ACCEPTED) — the whole scope-under-NFR9 decision in one map.
  [`SpeakerBulkComms.tsx:52`](../../web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx#L52)
- The confirm-gated bulk loop: empty-guard, `try/finally`, per-speaker outcome collection over the 1:1 hook (NFR1).
  [`SpeakerBulkComms.tsx:80`](../../web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx#L80)
- Send is only ever invoked from the dialog's Confirm — never directly (NFR1).
  [`SpeakerBulkComms.tsx:188`](../../web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx#L188)
- Type select locked while sending (review patch) — prevents an in-flight type/target divergence.
  [`SpeakerBulkComms.tsx:153`](../../web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx#L153)
- Partial-failure result + retry-only-the-failed.
  [`SpeakerBulkComms.tsx:200`](../../web-frontend/src/components/organizer/EventPage/SpeakerBulkComms.tsx#L200)

**i18n (NFR5)**

- New audience + speaker-comms keys (EN shown; present in all 10 locales, placeholders matching the code).
  [`en/events.json:331`](../../web-frontend/public/locales/en/events.json#L331)

**Tests**

- The new Speakers audience: filtering, NFR1 confirm-gate, bulk loop, partial-failure + retry, in-flight lock, load error.
  [`SpeakerBulkComms.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/__tests__/SpeakerBulkComms.test.tsx#L1)
- The container switch: 4 audiences + routing + default.
  [`EventCommunicationsContainer.test.tsx:1`](../../web-frontend/src/components/organizer/EventPage/__tests__/EventCommunicationsContainer.test.tsx#L1)
