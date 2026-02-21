# Epic 2 Technical Debt — Fix Tasks for Amelia

**Context:** Identified during Epic 2 retrospective (2026-02-17). Fix before Epic 3 stories start.
**Assigned to:** Amelia (Dev Agent)
**Priority:** Complete all before W3.2 starts (W3.1 is already in review)

---

## Task D3 ✅ — Verify & commit W2.3 post-review fixes (GIT HYGIENE — check first)

W2.4 code review (Amelia, 2026-02-17) flagged that W2.3 post-review fix files were uncommitted:
- `apps/BATbern-watch/BATbern-watch Watch App/Data/EventSyncService.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/PortraitCache.swift`
- `apps/BATbern-watch/BATbern-watch Watch AppTests/Data/EventSyncServiceTests.swift`

**Action:**
1. `git status` — check if these are uncommitted or if they landed in the W2.4 commit
2. If uncommitted: stage + commit them separately with message `fix(watch): W2.3 post-review fixes (EventSyncService, PortraitCache)`
3. If already committed in W2.4: document that resolution in this file and move on

---

## Task D2 ✅ — Fix SpeakerRole enum mismatch (W2.3, event-management-service)

**Problem:** Domain uses `PRIMARY_SPEAKER`, but Watch expects `panelist`/`keynoteSpeaker`/`moderator`.
Documented in W2.3 completion notes — `SpeakerRole` field may display wrong or blank on Watch.

**Files:**
- `services/event-management-service/src/main/java/ch/batbern/events/watch/dto/SpeakerDetail.java`
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java`
- Watch-side: `apps/BATbern-watch/BATbern-watch Watch App/Models/WatchModels.swift` (or wherever SpeakerRole is decoded)

**Action:**
1. Find the Watch-side enum/string for `speakerRole` — check how it's displayed in `SpeakerArrivalView` / `SpeakerPortraitCell`
2. Either:
   a. Map `PRIMARY_SPEAKER` → `keynoteSpeaker` in the `WatchEventController` mapper (backend), OR
   b. Add `PRIMARY_SPEAKER` as a valid case on the Watch side
3. Add a unit test asserting the mapping works end-to-end
4. Commit: `fix(watch): map PRIMARY_SPEAKER speakerRole to Watch enum (W2.3 debt)`

---

## Task D1 ✅ — Fix companyLogoUrl always null (W2.3, event-management-service)

**Problem:** `SpeakerDetail.companyLogoUrl` is always `null` because cross-service lookup from CUMS was deferred in W2.3.
Speaker company logos don't appear on the Watch.

**Files:**
- `services/event-management-service/src/main/java/ch/batbern/events/watch/WatchEventController.java` — `mapToSessionDetail()` / speaker mapping
- May require a `UserApiClient` or `CompanyApiClient` call to CUMS, or a join if company data is available in EMS

**Action:**
1. Check if `User` entity in EMS already has `companyLogoUrl` (check `UserRepository` / User JPA entity)
2. If yes: populate it in `SpeakerDetail` — one-liner fix
3. If no: add a cross-service call via existing `UserApiClient` pattern (see `docs/guides/microservices-http-clients.md`)
4. Add an integration test asserting `companyLogoUrl` is non-null for a speaker with a company logo
5. Commit: `fix(watch): populate companyLogoUrl in active-events response (W2.3 debt)`

---

## Task U1 ✅ — Replace SF Symbol placeholder with BATbern logo (watch app)

**Problem:** `arrow.clockwise.circle.fill` SF Symbol used as a placeholder for the BATbern logo mark (noted in Epic 1 retro, carried into Epic 2).

**Action:**
1. Find all uses of `arrow.clockwise.circle.fill` in the Watch app:
   ```bash
   grep -r "arrow.clockwise.circle.fill" apps/BATbern-watch/
   ```
2. Check if a BATbern SVG/PDF asset exists in `apps/BATbern-watch/BATbern-watch Watch App/Assets.xcassets/`
3. If asset exists: replace SF Symbol references with `Image("BATbernLogo")` (or equivalent)
4. If asset doesn't exist yet: create a simple placeholder asset or use `"b.circle.fill"` as a better interim symbol — document that a real asset is still needed
5. Commit: `fix(watch): replace SF Symbol placeholder with BATbern logo asset (U1 debt)`

---

## Task U2 ✅ — Wire PortraitCache into PublicEventService (public zone auto-download)

**Problem:** `PortraitCache` exists and works for the organizer zone (EventSyncService uses it), but the public zone's `PublicEventService` does not auto-download speaker portraits. Portraits only appear after navigating to speaker bio screen (lazy load), not pre-fetched.

**Files:**
- `apps/BATbern-watch/BATbern-watch Watch App/Data/PublicEventService.swift`
- `apps/BATbern-watch/BATbern-watch Watch App/Data/PortraitCache.swift`

**Action:**
1. After `PublicEventService` finishes syncing event data, add a background portrait prefetch loop:
   ```swift
   // After successful sync, prefetch portraits in background
   Task.detached(priority: .background) {
       for speaker in allSpeakers {
           if let url = speaker.profilePictureUrl {
               try? await PortraitCache.shared.downloadAndCache(url: url)
           }
       }
   }
   ```
2. Use `Task.detached` with `.background` priority — don't block the sync or the UI
3. Check if `ImageCachePrefetcher` already does this (see `ImageCachePrefetcherTests.swift`) — if so, wire it up rather than duplicating
4. Add a unit test asserting portraits are prefetched after a successful public zone sync
5. Commit: `fix(watch): wire PortraitCache prefetch into PublicEventService (U2 debt)`

---

## Task A1 ✅ (skipped) — Fix recomputeCounter() event filter in ArrivalTracker (minor, optional)

**Problem:** `recomputeCounter()` in `ArrivalTracker.swift` fetches ALL `CachedSpeaker` rows from SwiftData regardless of event. Documented as "acceptable for single-event app" — server-authoritative counts override it anyway.

**Action (low priority — only if time permits):**
1. Assess if `CachedSpeaker` has an event code relationship (check `CachedSpeaker.swift` model)
2. If yes: add `#Predicate { $0.session.event.eventCode == currentEventCode }` to the FetchDescriptor
3. If no relationship exists: either add it (schema change, more invasive) or leave as-is and document
4. Commit: `fix(watch): filter recomputeCounter to current event speakers (A1 debt)`

---

---

## Resolution Notes (2026-02-17)

- **D3**: Already committed in W2.4 commit (`eb6c165c`). No action needed.
- **D2**: Adapted `SpeakerRole` enum raw values to match backend (`primary_speaker`, `co_speaker`, `moderator`, `panelist`). Added `EventSyncMockURLProtocol` with separate static handler to prevent cross-suite interference. Added two D2 tests. Refs fixed: TestDataFactory, LiveCountdownViewModelTests.
- **D1**: Watch-side enrichment. `EventSyncService.syncActiveEvent()` now reads existing `CachedSpeaker` records (from public zone with company populated) and copies `company` to organizer zone speakers before the stale cache is deleted. No backend changes needed — `SpeakerBioView` + `ImageCachePrefetcher` already handle logo download from company name.
- **U1**: Already done — `BATbernSymbolView.swift` uses `Image("BATbernLogo")` from Assets.xcassets. No SF Symbol placeholder found in codebase.
- **U2**: Already done — `PublicViewModel.refreshEvent()` calls `prefetcher.prefetchAll(speakers:)` which uses `ImageCachePrefetcher` to concurrently download portraits AND company logos.
- **A1**: Skipped. `CachedSpeaker` has no event relationship. Adding one would require a schema migration and is disproportionate to the impact (server-authoritative counts always override local recompute anyway).

---

## After completing all tasks

1. Run full test suite: `./gradlew :services:event-management-service:test` and `xcodebuild test`
2. Verify no regressions in W2.x functionality
3. Update this file: mark each task ✅ with a short note on what was done
4. These debt items do NOT require a new story — they are small fixes. Commit directly to the working branch.
