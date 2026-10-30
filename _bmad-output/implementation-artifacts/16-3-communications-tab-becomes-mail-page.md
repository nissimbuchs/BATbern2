# Story 16.3: Communications tab becomes the mail page

Status: ready-for-dev

## Story

As an **organizer**,
I want **the event's Communications tab to show everything that went out for this event, newest first, with the exact mail and its delivery result**,
so that **I can answer "did the speakers get the slides mail?" in one click instead of asking the team**.

> Source: `docs/prd/epic-16-mail-center.md` → **Story 16.3**. Depends on 16.1.
> **First-wave scope:** section 1 ("Needs an answer") arrives with 16.2. This story builds sections 2 and 3 only and leaves room for section 1 at the top.

## Acceptance Criteria

1. **AC1** — GIVEN an event with a newsletter sent, WHEN the organizer opens Communications, THEN "What went out" lists it with sender, audience, recipient count, delivered and failed counts, and a click shows the mail as sent.
2. **AC2** — GIVEN a speaker or registrant send recorded by 16.1, THEN it appears in the same list with the same fields and is marked "Template" or "Own text".
3. **AC3** — "Handled automatically" is collapsed by default and shows counts only. In this story it shows failed deliveries (bounces, rejects) for this event. Accept/decline/out-of-office counts are added by 16.2.
4. **AC4** — GIVEN failed recipients on a send, WHEN the organizer expands that send, THEN the failed addresses and the reason are listed.
5. **AC5** — New read endpoint `GET /api/v1/events/{eventCode}/mail-messages` (paged, newest first) and `GET /api/v1/events/{eventCode}/mail-messages/{mailKey}` (with body and recipients), both `hasRole('ORGANIZER')`, ADR-013 conventions, in a new spec `docs/api/event-mail-api.openapi.yml`; frontend types regenerated.
6. **AC6** — Sends made before 16.1 shipped are not shown, except newsletters, which are backfilled from `newsletter_sends` by the endpoint (read-time union, no data migration).
7. **AC7** — New i18n keys in all 10 locales; EN + DE first-class.
8. **AC8** — Works on mobile width (single column, no horizontal scroll).

## Tasks / Subtasks

- [ ] **Task 1 — API** (AC: 5, 6)
  - [ ] Spec first, then controller + service in EMS `mailcenter` package; `EndpointAuthorizationGuardTest` stays green.
  - [ ] Newsletter rows: union `mail_message` with `newsletter_sends` that have no `mail_message` link, mapped to the same DTO; delivered/failed counts from `newsletter_recipients`.
  - [ ] Bruno contract tests (read-only, `docs { }` comments only), run against local dev.
- [ ] **Task 2 — Frontend** (AC: 1–4, 7, 8)
  - [ ] Replace the body of `EventCommunicationsContainer.tsx`: sections "What went out" and "Handled automatically". Keep the four existing compose components mounted behind a temporary audience switch until 16.4 lands, so sending stays possible in between (16.3 and 16.4 may ship in one PR; then drop the switch).
  - [ ] Detail view: rendered HTML in a sandboxed iframe (`sandbox` without scripts), as in the `/dev/emails` preview.
  - [ ] Remove the newsletter send-history table from `EventNewsletterTab.tsx` (now in "What went out"); keep its active-send progress polling.
  - [ ] Service layer `mailMessageService.ts` (no direct fetch), React Query hooks.
- [ ] **Task 3 — Tests** (AC: all)
  - [ ] Vitest for the list, empty state, expand-failed, mobile layout.
  - [ ] Integration test for the newsletter union and the 403 for non-organizers.
  - [ ] Playwright (organizer project) read-only spec; must not send anything (`@sends-real-email` guard rules).

## Dev Notes

- `EventCommunicationsContainer.tsx` (109 lines) holds the four audiences in local `useState`; there are no sub-tab routes, so nothing needs a redirect.
- Newsletter history today comes from `useNewsletterHistory(eventCode)` in `EventNewsletterTab.tsx`. That hook also drives "resume polling an active send"; keep that behaviour.
- The prototype v4 (scratchpad, not in the repo) shows the target layout. It contains real names, so do not copy its data.
- Rendered mail bodies are PII; the endpoint is organizer-only and never cached publicly.

### References

- `docs/prd/epic-16-mail-center.md` (Information architecture)
- `web-frontend/src/components/organizer/EventPage/EventCommunicationsContainer.tsx`
- `web-frontend/src/components/organizer/EventPage/EventNewsletterTab.tsx`
- `docs/api/event-newsletter-api.openapi.yml`
- `docs/architecture/ADR-013-rest-api-crud-conventions.md`

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-10-30: Story drafted from Epic 16 (John, PM).
