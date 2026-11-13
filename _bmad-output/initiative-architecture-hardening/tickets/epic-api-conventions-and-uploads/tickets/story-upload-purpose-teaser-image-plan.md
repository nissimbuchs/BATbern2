---
title: 'Upload purpose teaser-image'
type: 'refactor'
ticket: '11c'
created: '2026-11-13'
status: 'ready-for-dev'
baseline_revision: ''
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

**Problem:** Teaser images (per event and global) use their own presign and `POST /events/{eventCode}/teaser-images/confirm` in `EventTeaserImageService`. The client sends back the S3 key, and there is no size limit and no pending state.

**Approach:** Breaking, no aliases. EMS gets purpose `teaser-image` on the 17.11 contract, with target `eventCode` (required). `_global` names the global set, as the existing paths do. Organizers only. Confirm with the target creates the `event_teaser_images` row, and the limit of 10 per scope is checked at reserve time and again on attach. List, PATCH and DELETE stay. The old presign and confirm are deleted along with their frontend callers. The 11b target binding refuses a confirm against another event.

## Boundaries & Constraints

**Always:** Types stay jpeg/png/webp/svg. A size limit is new, because presign signs a size: 10 MB, the photo limit (see Design Notes). Final keys stay `events/{ec}/teaser/{uploadId}.{ext}` and `global/teaser/{uploadId}.{ext}`, and `imageUrl` is built via `CloudFrontUrlBuilder` as today. Event cache eviction as today. The 11a rules apply.

**Never:** No change to existing rows, PATCH/DELETE or the presentation order.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected | Error |
|---|---|---|---|
| Event teaser | organizer, target `{eventCode}` | row, shown on the event page | type: 400; > 10 MB: 400 |
| Global teaser | target `{eventCode:"_global"}` | row with null event code | — |
| Limit | 10 images in scope already | — | 409 at reserve (and at attach) with the existing limit error |
| Other event | reserved for A, confirmed for B | — | 404 |
| Old paths | presign, confirm | pinned status | — |

</frozen-after-approval>

## Code Map

- `docs/api/events-core-api.openapi.yml` -- delete L1760 `generateTeaserImageUploadUrl` and L1799 `confirmTeaserImageUpload`. Keep L1725 list and L1845 PATCH/DELETE.
- `docs/api/uploads-api.openapi.yml` -- `teaser-image`; the `eventCode` description mentions `_global`.
- EMS `controller/EventTeaserImageController.java` (`GLOBAL_CODE` L43, `resolveEventCode`) -- remove L70/L80. Move `GLOBAL_CODE`/`resolveEventCode` somewhere the handler can share. `service/EventTeaserImageService.java` -- remove presign/confirm (L82-132). Keep the limit check as a method the handler calls (`TeaserImageLimitExceededException`, keep its status).
- New EMS `service/TeaserImageUploadHandler.java` -- `requiresTarget`, ORGANIZER, rules (types above, 10 MB), `checkTarget` (event exists or `_global`; limit), `finalKey`, `attach` (row + evict `EVENT_WITH_INCLUDES_CACHE`).
- Frontend `hooks/useEventTeaserImages.ts` L39 + `eventApiClient` teaser presign/confirm (~L465-499) → `uploadTarget('teaser-image', {eventCode})`. Callers `EventSettingsTab.tsx` and `Admin/GlobalImagesTab.tsx`. Tests: `useEventTeaserImages.test` (axios-mock-adapter → MSW), `EventSettingsTab.test.tsx` (ratchet L443 `vi.mock(eventApiClient)` → MSW).
- Tests: `EventTeaserImageControllerIntegrationTest` (ratchet L101 class-level `@Transactional`: remove while touching), `EventTeaserImageServiceTest`. Playwright `admin-global-images-crud.spec.ts` + `helpers/global-image-fixture.ts` (`/events/_global/teaser-images`) move to the contract. Bruno: teaser reserve/old path in the 11b dev-only media collection.
- Baselines: Spectral line 25 (N3).

## Tasks & Acceptance

**Execution:**
- [ ] Specs + types.
- [ ] EMS RED IT `TeaserImageUploadIntegrationTest` (matrix), handler, removals, IT de-`@Transactional`.
- [ ] Gateway map `teaser-image` → EMS.
- [ ] Frontend hook, client, tests on MSW.
- [ ] Bruno, Playwright (global CRUD, plus one event teaser).
- [ ] `lint:api --update` (−1), remove N3, ratchet update.

**Acceptance Criteria:**
- Given local dev, when an organizer adds an event teaser and a global teaser, then both show (event page, global images tab) after reload, and the 11th in one scope is refused with the existing message.

## Implementation Notes

## Plan Change Log

## Review Triage Log

## Design Notes

**Architecture note.** Modules: EMS event core (teasers, L2), gateway, frontend. New dependencies: none. New endpoints: none (purpose value). Reused: the 11b handler pattern and target binding. Deviation: none.

**10 MB.** Today a teaser has no limit, because the presign signs no size. The contract signs one, so a limit is needed. 10 MB matches photos, and no production teaser near that has been measured (not checked; the IT pins the limit).

**Cleaner.** Removed: 2 operations and the client-supplied key. Gained: a size limit, pending state and expiry, and one ratchet mock and one transactional IT fewer. Spectral −1.

## Verification

**Commands:**
- `./gradlew :services:event-management-service:test --max-workers=4` -- green
- frontend `type-check`, Vitest, `lint:api`, `check:api-paths`; `make check-test-ratchet` -- green
- Playwright `admin-global-images-crud.spec.ts` against local dev -- green
