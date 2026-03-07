# Story 10.8c: Teaser Image Presentation Position

Status: done

## Story

As an **event moderator / organizer**,
I want to specify, for each uploaded teaser image, where it should appear in the presentation,
so that I can place sponsor logos, topic teasers, or sponsor slides at the most contextually relevant point in the evening.

## Acceptance Criteria

1. Each teaser image has a `presentationPosition` field that controls where it appears in the moderator presentation.
2. The organizer can change the position per image via a dropdown in the Event Settings tab (Teaser Images gallery).
3. Allowed positions:
   - **After Welcome slide** (`AFTER_WELCOME`) — between welcome and about
   - **After Committee slide** (`AFTER_COMMITTEE`) — between committee and topic-reveal
   - **After Topic Reveal slide** (`AFTER_TOPIC_REVEAL`) — between topic-reveal and agenda-preview (default)
   - **After Upcoming Events slide** (`AFTER_UPCOMING_EVENTS`) — between upcoming-events and apero
4. Multiple images can share the same position; they appear in ascending `displayOrder` order at that position.
5. Default position for newly uploaded images is `AFTER_TOPIC_REVEAL` (preserves existing behavior).
6. Changing position triggers a PATCH call and updates immediately (optimistic invalidation via React Query).
7. All 15 implementation steps have passing tests (backend unit + integration, frontend unit).

## Technical Notes

- New DB column: `presentation_position VARCHAR(30) NOT NULL DEFAULT 'AFTER_TOPIC_REVEAL'` with CHECK constraint
- New API: `PATCH /api/v1/events/{eventCode}/teaser-images/{imageId}` with `{ presentationPosition }`
- Frontend: position dropdown (`<Select>`) below each image thumbnail in the gallery grid
- Presentation logic: `insertTeaserImages()` helper in `usePresentationSections.ts` called at 4 insertion points

## Implementation Summary

### Files Changed

| File | Change |
|---|---|
| `services/event-management-service/src/main/resources/db/migration/V81__add_teaser_image_presentation_position.sql` | NEW — ALTER TABLE adds column |
| `docs/api/events-api.openapi.yml` | Add `TeaserImagePresentationPosition` enum, extend `TeaserImageItem`, add `TeaserImageUpdateRequest`, add PATCH operation |
| `services/event-management-service/src/main/java/ch/batbern/events/domain/EventTeaserImage.java` | Add `presentationPosition` field (default `"AFTER_TOPIC_REVEAL"`) |
| `services/event-management-service/src/main/java/ch/batbern/events/service/EventTeaserImageService.java` | Update `toItem()` to map position; add `updatePresentationPosition()` |
| `services/event-management-service/src/main/java/ch/batbern/events/controller/EventTeaserImageController.java` | Add `@PatchMapping` handler |
| `services/event-management-service/src/test/java/ch/batbern/events/service/EventTeaserImageServiceTest.java` | Add `UpdatePresentationPositionTests`; update confirm test assertion |
| `services/event-management-service/src/test/java/ch/batbern/events/controller/EventTeaserImageControllerIntegrationTest.java` | Add 3 PATCH tests; update confirm + GET event assertions |
| `web-frontend/src/types/generated/events-api.types.ts` | Regenerated — adds `TeaserImagePresentationPosition`, `TeaserImageUpdateRequest`, updates `TeaserImageItem` |
| `web-frontend/src/services/eventApiClient.ts` | Add `updateTeaserImage()` method |
| `web-frontend/src/hooks/useEventTeaserImages.ts` | Add `useUpdateTeaserImagePosition()` mutation |
| `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx` | Add position Select dropdown per image card |
| `web-frontend/public/locales/en/events.json` | Add `teaserImage.position.*` keys; update `description` |
| `web-frontend/public/locales/de/events.json` | Add `teaserImage.position.*` keys (DE); update `description` |
| `web-frontend/src/hooks/usePresentationSections.ts` | Extract `insertTeaserImages()` helper; add 4 insertion points |
| `web-frontend/src/hooks/usePresentationSections.test.ts` | Update existing + add 6 new tests covering all 4 positions |

## Dev Agent Record

**Implemented by:** Amelia (dev agent)
**Date:** 2026-03-05
**Branch:** `feature/10-8a-moderator-presentation-page`

### Implementation decisions
- Used `String` (not enum) for `presentationPosition` in the JPA entity, converting to/from the generated `TeaserImagePresentationPosition` enum in `toItem()` using `fromValue()`. Reason: `@Enumerated(EnumType.STRING)` stores the enum name (`TOPIC_REVEAL`), not the value (`AFTER_TOPIC_REVEAL`), which would conflict with the DB CHECK constraint.
- PATCH-only API (no position in confirm request) — position is logically independent of upload; users can rearrange after seeing all images.
- `insertTeaserImages()` is a module-level function (not a hook member) since it is pure: sections array + images + position string → void.
- `?? 'AFTER_TOPIC_REVEAL'` null-coalescing guard in `insertTeaserImages()` protects against missing position on legacy data.

### Tests
- Backend unit: 2 new + 1 updated (17 total pass)
- Backend integration: 3 new + 2 updated assertions (11 total pass)
- Frontend unit: 6 new + 2 updated (21 total pass)
- Type-check: ✅ clean

## File List

- `services/event-management-service/src/main/resources/db/migration/V81__add_teaser_image_presentation_position.sql`
- `docs/api/events-api.openapi.yml`
- `services/event-management-service/src/main/java/ch/batbern/events/domain/EventTeaserImage.java`
- `services/event-management-service/src/main/java/ch/batbern/events/service/EventTeaserImageService.java`
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventTeaserImageController.java`
- `services/event-management-service/src/test/java/ch/batbern/events/service/EventTeaserImageServiceTest.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/EventTeaserImageControllerIntegrationTest.java`
- `web-frontend/src/types/generated/events-api.types.ts`
- `web-frontend/src/services/eventApiClient.ts`
- `web-frontend/src/hooks/useEventTeaserImages.ts`
- `web-frontend/src/components/organizer/EventPage/EventSettingsTab.tsx`
- `web-frontend/public/locales/en/events.json`
- `web-frontend/public/locales/de/events.json`
- `web-frontend/src/hooks/usePresentationSections.ts`
- `web-frontend/src/hooks/usePresentationSections.test.ts`
