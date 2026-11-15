# Fix Summary — Public Website & Registration (Epic 4)
**Fixed:** 2026-03-09
**Doc:** `docs/prd/epic-4-public-website-content-discovery.md`

## Changes made

### Mismatches

- **M1**: Updated cancellation endpoint URL from `POST /api/v1/registrations/cancel?token={jwt}` to `POST /api/v1/events/{eventCode}/registrations/cancel?token={token}` throughout Story 4.1.5d (Key Functionality, Backend Details, Frontend section, Acceptance Criteria AC3). The event code is part of the URL path, not absent from it.

- **M2**: Replaced all "Permanent Deletion" / "permanently deletes record" language with "sets registration status to `cancelled` (record is retained in the database)". Updated Key Functionality item 5, Backend Details, Security Model, and AC3 accordingly.

- **M3**: Rewrote the Architecture Integration note for Story 4.1.5d to clarify that Story 10.12 superseded the JWT mechanism with a UUID `deregistrationToken`. Added a prominent note explaining the original JWT design vs. current UUID implementation. Updated Key Functionality item 1 (UUID token generation), Security Model (removed JWT-specific claims: type field validation, 48-hour expiry), and Frontend section (`cancelRegistration(eventCode, token)` signature).

### Undocumented behaviours

- **N1**: Added a new **UUID-Based Deregistration (Story 10.12)** section after Story 4.1.5d documenting the three canonical deregistration endpoints (`verify`, `deregister`, `deregister/by-email`), their HTTP semantics, and the cancellation/waitlist-promotion behaviour.

- **N2**: Added a **Phase 1 eligibility rule** paragraph to the Post-Event 14-Day Display Rule in Story 4.1: only events with `currentPublishedPhase` set qualify as upcoming (Phase 1); unpublished future events are excluded.

- **N3**: Added AC item 21 to the Technical Requirements of Story 4.1: "Unconfirmed registrations (status = `registered` / unconfirmed) are permanently deleted after 48 hours by a scheduled cleanup job."

- **N4**: Added AC item 12 to Story 4.1.5d: the by-email deregistration endpoint returns HTTP 200 unconditionally to prevent user-enumeration attacks. Also captured in the Security Model and N1 table.

- **N5**: Added AC item 11 to Story 4.1.5d: on successful deregistration, the first waitlisted registration is automatically promoted to confirmed status and their `waitlistPosition` cleared. Also captured in the N1 section.

## Skipped — needs manual decision

- **U1**: "timetable and speaker list are visible, but registration, logistics, and venue blocks are hidden" (Post-Event 14-Day Display Rule) — no test validates which specific fields/UI sections are suppressed in archive-style mode. Requires either a new test or explicit documentation that this is UI-only behaviour with no backend assertion.

- **U2**: "Caffeine in-memory cache for event data (1-minute TTL)" — `CaffeineCacheConfigTest` only covers the 15-minute archive TTL; no assertion on a 1-minute TTL for `current`/single-event endpoints. Verify the configured TTL in `CaffeineCacheConfig` and correct if needed.

- **U3**: "Caffeine in-memory cache for search results (5-minute TTL)" — no test asserts a 5-minute TTL for any search cache. Verify configuration or add a cache-config test.

- **U4**: "Automatic loading when scrolling within 400px of bottom" — `useInfiniteEvents.test.tsx` validates page size but not the IntersectionObserver `rootMargin` threshold. May need a test for the 400px value or document it as an implementation detail.

- **U5**: "Search debounced 300ms" — no test asserts the debounce timing. May need a timer-based test or accept as an undocumented implementation detail.

- **U6**: "Email confirmations sent within 1 minute" — no latency assertion exists. This is a SLA claim with no automated validation; consider either removing it or adding an async timing test.

- **U7**: Story 4.3 content-search entirely untested at service layer — no tests cover the full-text search endpoint, GIN index usage, <500ms response time, or 5-minute Caffeine cache. High risk; consider adding integration tests or marking Story 4.3 ACs as untested.
