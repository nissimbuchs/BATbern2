---
title: 'The rest of #1223: event number, placeholder page, stray chip, portraits, tier'
type: 'bugfix'
ticket: '9'
created: '2026-11-18'
status: 'done'
baseline_revision: '237ffd25ade526a50c58a14f95abb534850fc131'
route: 'full'
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
- [x] RED tests per item (item 11 only if production is broken)
- [x] fixes; backend IT for item 12 if the API lets a partner change the tier

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

## Implementation Notes

- Item 7: `useNextEventNumber` (useEvents.ts) reads one row of `GET /events?sort=-eventNumber&limit=1&includeArchived=true` (nearest existing endpoint, no new one); the form makes it the field default via `resetField` unless the organizer typed a number; the create-mode reset on opening starts from the cached next number (reopening showed 1, test added). New EMS IT `EventNumberSortIntegrationTest` pins the sort (highest number = oldest archived event). Found on the way (C9): eventApiClient errors carry `status`, not `response`, so auto-save never recognised a 409 ("concurrent edit") and a refused create never showed the server's reason; the client also refused a deadline on the event day that the form allows (aligned to "on or before"). EventForm.test de-mocked (ratchet -2).
- Item 8: `/organizer/speakers` redirects to `/organizer/users`, `/speakers` to `/speaker-portal/dashboard` (`src/routes/retiredRoutes.tsx`); placeholder `pages/Speakers.tsx` deleted. It had no nav entry.
- Item 10: the chip was the closed hamburger drawer's user strip (initials + email) at the right edge; the screenshot predates #1312's truncate fix. Closed drawer is now `invisible` + `inert` (also stops its links taking keyboard focus). Not checked in a browser.
- Item 11: production probe 2026-11-18, read-only: 7 archive events (BATbern20/35/45/50/55/58/59), 31 portrait URLs on cdn.batbern.ch, all 31 answer 200 both as original and with the page's `?w=160&h=160&fit=cover` (image/webp). Speakers without a URL render initials, not an img. Local-only; no code change.
- Item 12: "Change Tier" only for organizers (`canChangeTier`). The button set `partnerDetailStore.showEditModal`, read by nothing, so it did nothing for organizers either; it now opens the partner edit modal (holds the tier). Dead store flag removed. API already organizer-only (`PATCH /partners/{companyName}` `hasRole('ORGANIZER')`); PCS IT added for a partner re-levelling its own company (403, tier unchanged) - green before and after, a guard. PartnerOverviewTab.test de-mocked and out of the type-check exclusions.
- No UI text changed, so no locale files. Docs: user guide (event number, partner tier), frontend architecture route table, sitemaps.
- Review fixes: EMS refuses a taken number with 422 (BusinessValidationException), so transformError keeps the server message for 422 as for 409 and the form shows it on create and auto-save (tests use the DomainErrorResponse shape). The dead 409 "concurrent edit" branch and its `autoSave.conflict` key (10 locales) are removed. `canChangeTier` is an allow-list (`user.role === 'organizer'`; hidden while the user is unknown), tested in PartnerDetailScreen.test. Stale `showEditModal` store-mock fields removed. `/speakers` dropped from the organizer path list. `App.retiredRoutes.test.tsx` renders the real App at `/organizer/speakers`. The EMS IT deletes and reads only its own events.
- Fixed (C9): auto-save PATCHed number fields as strings (`eventNumber: "57"`), skipping the schema's `z.coerce.number`. It now runs the snapshot through the same field schemas as the save path (`coerceFormFields`; a field that fails its schema is not auto-saved), and the auto-save test asserts `{ eventNumber: 57 }`. The spec types it `integer`; EMS has no runtime spec validation (OpenApiContract is test-only) and no Jackson coercion override, so it most likely coerced "57" (Jackson default), not checked by running.

## Review Triage Log

Quick review, 8 findings: 7 patched, 1 accepted as unverified.
- medium, patched: a taken event number is a 422, not 409, and the client dropped the body; server message now kept for 409 and 422, shown on create and auto-save, tests with the real 422 body.
- low, patched: dead auto-save 409 "concurrent edit" branch and its key removed.
- low, patched: "Change Tier" was a deny-list (visible while auth loads); now organizer-only, wiring tested for organizer, partner, unknown.
- low, patched: stale `showEditModal` lines in three store mocks removed.
- low, patched: `/speakers` dropped from the organizer path list.
- low, patched: `App.retiredRoutes.test.tsx` tests the real App redirect.
- low, patched: `EventNumberSortIntegrationTest` deletes and counts only its own rows.
- maybe-false, accepted: item 10's cause (closed drawer strip) is inferred, not seen in a browser; the change (closed drawer invisible and inert) is harmless hardening.
- Found by the implementer, fixed (C9): auto-save sent number fields as strings against the integer spec; it now coerces through the form schema.
Production read-only probe (orchestrator): public events go up to BATbern62, so a new event pre-fills 63.
Verification (orchestrator): type-check, lint, Vitest 348 files / 4014 tests, ratchet, i18n keys, EMS `EventNumberSortIntegrationTest`, PCS `PartnerControllerIntegrationTest`.
