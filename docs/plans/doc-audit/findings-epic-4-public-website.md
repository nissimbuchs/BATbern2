# Doc Audit Findings — Public Website & Content Discovery (Epic 4)
**Audited:** 2026-03-09
**Doc:** `docs/prd/epic-4-public-website-content-discovery.md`
**Tests searched:** `services/event-management-service/src/test/java`, `web-frontend/src`

## Summary
- VALIDATED: 12
- MISMATCH: 3
- UNTESTED: 7
- UNDOCUMENTED: 5

---

## MISMATCH

### M1 — Cancellation endpoint URL includes event code in path (frontend) but not in doc
**Doc claims:** "Public Cancellation Endpoint: POST `/api/v1/registrations/cancel?token={jwt}` (no authentication required)" (Story 4.1.5d)
**Test asserts:** `eventApiClient.mocked.test.ts#cancelRegistration` — calls `cancelRegistration('BATbern142', 'jwt-token')`, and `eventApiClient.ts` line 351 uses path `/events/${eventCode}/registrations/cancel?token={token}` (event code embedded in URL path)
**Action:** Update doc to say the endpoint is `POST /api/v1/events/{eventCode}/registrations/cancel?token={jwt}` or confirm the backend route

### M2 — Cancellation produces a status change, not permanent deletion
**Doc claims:** "Permanent Deletion: Registration permanently deleted from database (not just status change)" (Story 4.1.5d)
**Test asserts:** `DeregistrationControllerIntegrationTest#deregisterByToken_validToken_returns200AndCancelsRegistration` — `assertThat(updated.getStatus()).isEqualTo("cancelled")`. The record remains in the DB with status `cancelled`; the test even retrieves it post-deregistration via `findByDeregistrationToken`.
**Action:** Update doc: "Cancellation sets registration status to `cancelled` (record is retained). Permanent deletion language is inaccurate."

### M3 — Cancellation token is UUID-based in the implemented deregistration flow, not JWT
**Doc claims:** "Separate JWT cancellation token (48-hour validity, type: 'registration-cancellation')" and "Token Type Validation: Cancellation tokens explicitly marked with `type: 'registration-cancellation'`" (Story 4.1.5d)
**Test asserts:** `DeregistrationControllerIntegrationTest` uses `UUID deregistrationToken` (stored as a plain UUID column, not a JWT). `ConfirmationTokenServiceTest` has no test for `generateCancellationToken` or `validateCancellationToken` with type `"registration-cancellation"`. The story 10.12 deregistration endpoints entirely replace the JWT mechanism.
**Action:** Update Story 4.1.5d to reflect that the implemented token mechanism is a UUID `deregistrationToken` (not a JWT). The 48-hour expiry and type-validation claims no longer apply to the current implementation.

---

## UNTESTED

### U1 — Archive-style layout: specific fields hidden after event
**Doc claims:** "timetable and speaker list are visible, but registration, logistics, and venue blocks are hidden" (Post-Event 14-Day Display Rule)
**Risk:** medium — tests verify that EVENT_COMPLETED events within 14 days are returned by `/api/v1/events/current`, but no test validates which specific response fields or UI sections are suppressed in archive-style mode.

### U2 — 1-minute cache TTL for event data
**Doc claims:** "Caffeine in-memory cache for event data (1-minute TTL)" (Story 4.1, Architecture Integration)
**Risk:** low — `CaffeineCacheConfigTest` only mentions and tests the 15-minute TTL (for archive browsing). There is no assertion on a 1-minute TTL for the current-event or single-event endpoints.

### U3 — 5-minute cache TTL for search results
**Doc claims:** "Caffeine in-memory cache for search results (5-minute TTL)" (Story 4.3, Architecture Integration)
**Risk:** low — no test in either directory verifies a 5-minute Caffeine TTL for any search cache.

### U4 — Infinite scroll triggers within 400px of page bottom
**Doc claims:** "Automatic loading when scrolling within 400px of bottom (20 events per page)" (Story 4.2 AC3)
**Risk:** low — `useInfiniteEvents.test.tsx` validates the page size (limit: 20) and pagination, but does not assert the 400px IntersectionObserver `rootMargin` threshold.

### U5 — Search debounce 300ms
**Doc claims:** "Search Bar: Full-text search across event titles, topics, speakers (debounced 300ms)" (Story 4.2 AC9, Story 4.3 AC2)
**Risk:** low — no test in either directory asserts the 300ms debounce timing on the archive search or content discovery search inputs.

### U6 — Email confirmations sent within 1 minute
**Doc claims:** "Email confirmations sent within 1 minute" (Story 4.1 Deliverables)
**Risk:** low — `RegistrationEmailServiceTest` validates that emails are sent and contain correct content, but no test measures or bounds the delivery latency.

### U7 — Story 4.3 (content search) entirely untested at service layer
**Doc claims:** Full-text search returns results in <500ms, PostgreSQL GIN indexes on searchable fields, Caffeine 5-minute cache for search results, autocomplete suggestions debounced 300ms, faceted filtering (Story 4.3 ACs 1–22)
**Risk:** high — no test in `services/event-management-service/src/test/java` or `web-frontend/src` directly exercises the content-search endpoint, GIN index usage, or search cache behaviour described for Story 4.3.

---

## UNDOCUMENTED

### N1 — Deregistration via UUID token (Story 10.12 — new endpoints not in Epic 4 doc)
**Test:** `DeregistrationControllerIntegrationTest` — asserts:
- `GET /api/v1/registrations/deregister/verify?token={uuid}` → 200 with registration info, 404 when token unknown/already-cancelled
- `POST /api/v1/registrations/deregister` with UUID token → 200 and status `cancelled`; 409 on second call; 404 for unknown token
- `POST /api/v1/registrations/deregister/by-email` → always 200 (anti-enumeration)
**Action:** Add a section to Epic 4 doc (or link to Story 10.12) describing the UUID-based deregistration endpoints that supersede the JWT cancellation mechanism from Story 4.1.5d.

### N2 — `/api/v1/events/current` Phase 1 requires `currentPublishedPhase != null`
**Test:** `EventControllerIntegrationTest#should_showRecentlyCompletedEvent_when_upcomingEventIsUnpublished` — an upcoming event in state `SPEAKER_IDENTIFICATION` (no published phase) does NOT qualify for Phase 1 and must not block the Phase 2 (recently-completed) fallback. Comment: "Rule: only events with currentPublishedPhase != null qualify for Phase 1 (upcoming)."
**Action:** Add to the Post-Event 14-Day Display Rule section: Phase 1 only considers events that have `currentPublishedPhase` set; unpublished future events are excluded.

### N3 — Unconfirmed registration cleanup after 48 hours
**Test:** `RegistrationCleanupServiceTest#shouldDeleteUnconfirmedRegistrations_whenOlderThan48Hours` and `RegistrationCleanupServiceIntegrationTest#shouldDeleteOldUnconfirmedRegistrations` — unconfirmed registrations older than 48 hours are permanently deleted by a scheduled cleanup job.
**Action:** Add to Story 4.1 Technical Requirements or 4.1.5c: "Unconfirmed registrations (status = `registered` / unconfirmed) are permanently deleted after 48 hours by a cleanup job."

### N4 — Anti-enumeration: deregister/by-email always returns 200
**Test:** `DeregistrationControllerIntegrationTest#deregisterByEmail_anyInput_alwaysReturns200` — the `POST /api/v1/registrations/deregister/by-email` endpoint returns HTTP 200 for any email, whether registered or not, to prevent email enumeration.
**Action:** Add to Story 4.1.5d (or linked Story 10.12): "The by-email deregistration endpoint returns HTTP 200 unconditionally to prevent user-enumeration attacks."

### N5 — Waitlist promotion triggered on deregistration
**Test:** `DeregistrationControllerIntegrationTest#deregisterByToken_whenWaitlistExists_waitlistRegistrationPromoted` — when a registered attendee deregisters and a waitlisted attendee exists, the first waitlisted registration is automatically promoted to `registered` status and their `waitlistPosition` cleared.
**Action:** Add to Story 4.1 or 4.1.5d Acceptance Criteria: "On successful deregistration, the first waitlisted registration (if any) is automatically promoted to confirmed."

---

## VALIDATED
- "After an event finishes, the homepage continues to show it for 14 days" → `EventWorkflowScheduledServiceIntegrationTest#should_archiveCompletedEvent_when_olderThan14Days` + `should_notArchiveCompletedEvent_when_within14Days`
- Exclusive boundary: exactly-14-days stays in EVENT_COMPLETED → `EventWorkflowScheduledServiceIntegrationTest#should_notArchiveCompletedEvent_when_exactlyAt14DayBoundary`
- `/api/v1/events/current` returns recently completed event (within 14 days) → `EventControllerIntegrationTest#should_returnCompletedEvent_when_completedEventDateWasYesterday`
- `/api/v1/events/current` returns 404 when no active or recent event → `EventControllerIntegrationTest#should_return404_when_noCurrentOrRecentEvent`
- `/api/v1/events/current` prefers upcoming over recently completed → `EventControllerIntegrationTest#should_preferUpcomingEvent_when_bothUpcomingAndRecentlyCompletedExist`
- "48-hour validity" of JWT confirmation token → `ConfirmationTokenServiceTest#should_have48HourExpiry_when_tokenGenerated`
- Confirmation token type is "email-confirmation" → `ConfirmationTokenServiceTest#should_returnClaims_when_tokenIsValid`
- `registrationCode` not returned in registration API response (Story 4.1.5c security) → `AnonymousRegistrationE2ETest#should_completeAnonymousRegistrationFlow_when_userRegistersWithoutAuth`
- Registration code format `{eventCode}-reg-{6alphanumeric}` → `AnonymousRegistrationE2ETest`
- Archive browsing page size is 20 events → `useInfiniteEvents.test.tsx` (limit: 20 in pagination mock)
- Archive events cache (`archiveEvents`) exists in CacheManager → `CaffeineCacheConfigTest#should_haveArchiveEventsCache_when_cacheManagerConfigured`
- Archive browsing sort by date asc/desc → `ArchiveBrowsingIntegrationTest#should_sortByDateDescending_when_sortMinusDateProvided` + `should_sortByDateAscending_when_sortPlusDateProvided`
