---
title: 'The rest of #1223: event number, placeholder page, stray chip, portraits, tier'
type: 'bugfix'
ticket: '9'
created: '2026-11-18'
status: 'in-progress'
baseline_revision: '908d8cbd01d4ed2179549372e6fdf9283721accb'
route: 'full'
route_source: 'auto'
risk: 'low'
review: ''
review_source: ''
lenses_ran: []
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** #1223 items seen in the running app: (7) Create New Event pre-fills Event Number "1" instead of the next number; (8) `/organizer/speakers` is a placeholder page ("Speaker coordination interface"); (10) a stray user chip at the right edge of the speaker portal and attendee dashboard ("BS success…", "BP batb…"); (11) speaker portraits on the public archive show alt text (possibly local-only); (12) a partner sees "Change tier" on My Company.

**Approach:** Trace and fix each at the source with one test per item. (7) pre-fill the next free event number (highest existing + 1, from the API the form already can reach; extend a spec only if nothing gives it). (8) redirect `/organizer/speakers` to where organizers manage speakers today (find the real speaker pages) and remove the placeholder and its nav entry if any. (10) remove or fix the chip. (11) check production first with a read-only HTTP probe of a few archive portrait URLs on www.batbern.ch / cdn.batbern.ch; if they load, the item is local-only (no code change, recorded); if not, fix. (12) partners see the tier read-only, without "Change tier" (owner 2026-11-15).

## Boundaries & Constraints

**Always:** Real i18next, auth helper, MSW harness in tests; touched tests leave own-code mocks (C6). Text changes in 10 locales. Authorization for item 12 also server-side: verify the endpoint that changes a tier is organizer-only (`@PreAuthorize`); if a partner can change it via the API, fix that too with an IT.

**Never:** Write to production. Change the tier model.

</frozen-after-approval>

## Code Map

- (7) the create-event form (grep "eventNumber" in `web-frontend/src/components/organizer/EventManagement/`), events API list/next-number (`docs/api/events-core-api.openapi.yml`).
- (8) `web-frontend/src/App.tsx` route `/organizer/speakers`, the placeholder component, navigation config.
- (10) speaker portal and attendee dashboard layouts (`pages/speaker*`, `pages/attendee*`, `components/shared/Layout`), look for a chip/Avatar rendered outside the header.
- (11) public archive speaker images (`components/public/archive*`), their URL source; probe with `curl -sI`.
- (12) partner "My Company" page (grep "Change tier"/"changeTier"), partner/company tier endpoint in `services/partner-coordination-service` or CUMS and its `@PreAuthorize`.

## Tasks & Acceptance

**Execution:**
- [ ] RED tests per item (item 11 only if production is broken)
- [ ] fixes; backend IT for item 12 if the API lets a partner change the tier

**Acceptance Criteria:**
- Each item has a test that failed before its fix, or (item 11) the probe result recorded in Implementation Notes.

## Design Notes

Architecture note: frontend (event form, routing, layouts, partner page), possibly PCS/CUMS authorization. New endpoint only if no existing one yields the next event number (state the nearest one considered). Deviations: none.

## Verification

**Commands:**
- `cd web-frontend && npm run type-check && npm run lint && npx vitest run --maxWorkers=4` over touched folders -- green.
- Any touched service: `./gradlew --max-workers=4 :services:<svc>:test` + checkstyle, tee'd -- green.
- `make check-test-ratchet`, `make check-i18n-keys` -- clean.

**Rack rules:** one heavy build at a time, Gradle `--max-workers=4`, Vitest `--maxWorkers=4`, output via `set -o pipefail; ... | tee ~/.cache/batbern-test-logs/<name>.log`. Do not commit or push. A bug found on the way is fixed too and reported (initiative C9).
