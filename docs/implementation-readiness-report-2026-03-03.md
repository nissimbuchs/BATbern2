---
stepsCompleted: [step-01-document-discovery, step-02-prd-analysis, step-03-epic-coverage-validation, step-04-ux-alignment, step-05-epic-quality-review, step-06-final-assessment]
documentsInventoried:
  prd: docs/prd/epic-10-additional-stories.md
  architecture:
    - docs/architecture/04-api-event-management.md
    - docs/architecture/05-frontend-architecture.md
    - docs/architecture/06-backend-architecture.md
    - docs/architecture/03-data-architecture.md
  story: _bmad-output/implementation-artifacts/10-22-event-teaser-image.md
  ux: none
scope: Story 10.22 — Event Teaser Images on Moderator Presentation Page
---

# Implementation Readiness Assessment Report

**Date:** 2026-03-03
**Project:** BATbern
**Scope:** Story 10.22 — Event Teaser Images on Moderator Presentation Page

---

## PRD Analysis

**Source:** `docs/prd/epic-10-additional-stories.md` (lines 1562–1622)

### Functional Requirements

FR1: Organizer uploads a teaser image via presigned URL from the event detail page (Settings tab)
FR2: Uploaded teaser image thumbnail is shown in the Settings tab after confirmation
FR3: Organizer can remove the teaser image; DB columns cleared, S3 object deleted
FR4: Presentation page shows a full-screen teaser image slide between the topic-intro slide and the first agenda slide when `teaserImageUrl` is set
FR5: When no teaser image is set, the presentation page renders identically to pre-story behaviour — no empty/blank slide inserted
FR6: OpenAPI spec committed before any implementation code (ADR-006)
FR7: i18n keys `teaserImage.*` added to `de/events.json` and `en/events.json`
FR8: TDD — `EventTeaserImageServiceTest` covers upload-confirm and delete; integration test verifies DB state

**Total FRs: 8**

### Non-Functional Requirements

NFR1: Presigned URL expiry 900s (15 min) — consistent with SpeakerProfilePhotoService
NFR2: Allowed content types: `image/jpeg`, `image/png`, `image/webp`
NFR3: Max file size: 10 MB
NFR4: Endpoints restricted to `ORGANIZER` role (RBAC — no API gateway changes needed)
NFR5: S3 key pattern: `events/{eventCode}/teaser/{uuid}.{ext}`
NFR6: Type-check must pass after type regen; Checkstyle must pass

**Total NFRs: 6**

### Additional Requirements / Constraints

- **Prerequisite**: Story 10.8a (PresentationPage must exist) — already complete
- **Flyway version**: PRD specifies V76; actual version depends on merge order of 10.21 (story warns to check at dev time)
- **No drag-to-reorder** in scope (Story 10.21 sets this precedent; same principle applies here implicitly)
- ADR-006: OpenAPI-first — spec committed before any implementation code

### PRD Completeness Assessment

The PRD describes a **single-image** design: two nullable columns (`teaser_image_s3_key`, `teaser_image_url`) on the `events` table, with a single upload/confirm/delete lifecycle.

⚠️ **SCOPE DIVERGENCE DETECTED**: The story file (`10-22-event-teaser-image.md`) has been **updated** beyond the PRD definition to support **multiple images** via a separate `event_teaser_images` child table, with each image becoming its own slide in the presentation. This expansion is intentional (SM + organizer decision) but the PRD has **not been updated** to reflect it.

---

## Epic Coverage Validation

**Source story:** `_bmad-output/implementation-artifacts/10-22-event-teaser-image.md`

### Coverage Matrix

| FR | PRD Requirement | Story Coverage | Status |
|----|----------------|----------------|--------|
| FR1 | Organizer uploads teaser image via presigned URL from event detail page | Phase 1 (OpenAPI spec), Phase 5 (Controller: upload-url + confirm endpoints) — expanded to N images | ✅ Covered (expanded) |
| FR2 | Uploaded thumbnail shown in Settings tab after confirmation | Phase 8 (EventSettingsTab gallery: thumbnails per image with remove buttons) | ✅ Covered (expanded to gallery) |
| FR3 | Organizer removes teaser image; DB cleared, S3 object deleted | Phase 5 (DELETE `/teaser-images/{imageId}`), Phase 8 (per-thumbnail remove button), Phase 4 (service: DeleteObject + row delete) | ✅ Covered (expanded to per-image delete by UUID) |
| FR4 | Full-screen teaser image slide between topic-intro and first agenda slide when image is set | Phase 7 (`usePresentationSections` loop: one `teaser-image` section per image, in displayOrder) | ✅ Covered (expanded to N slides) |
| FR5 | When no teaser image, slide omitted; no empty slide | Phase 7 (`(event.teaserImages ?? []).forEach(...)` — empty array → zero sections pushed) | ✅ Covered |
| FR6 | OpenAPI spec committed before implementation (ADR-006) | Phase 1 explicitly marked as commit-first | ✅ Covered |
| FR7 | i18n keys `teaserImage.*` in de/en | Phase 9 (both locales, plural-aware keys including `emptyHint`) | ✅ Covered |
| FR8 | TDD: `EventTeaserImageServiceTest` + integration test verifying DB state | Phase 2 (RED — full unit + integration test list), Phase 10 (GREEN + `usePresentationSections.test.ts`) | ✅ Covered |

### Missing Requirements

**No PRD FRs are missing from the story.** All 8 FRs have clear implementation paths.

One item to note: FR3 requires S3 object deletion on remove. The story service layer calls `S3Client.deleteObject(...)` using the `s3Key` retrieved from the `EventTeaserImage` row before deleting the row. The integration test mocks `S3Client` — unit test covers actual deletion call. ✅ Traceable.

### Scope Expansions (Beyond PRD — intentional)

The story deliberately exceeds the PRD scope in the following ways. These are not gaps — they are approved additions — but they must be tracked:

| Expansion | PRD Assumption | Story Implementation |
|-----------|----------------|---------------------|
| Data model | 2 nullable columns on `events` table | Separate `event_teaser_images` child table (id, event_code, s3_key, image_url, display_order) |
| Cardinality | 1 image per event | N images per event |
| Delete endpoint | `DELETE /events/{eventCode}/teaser-image` (no imageId) | `DELETE /events/{eventCode}/teaser-images/{imageId}` |
| EventResponse field | `teaserImageUrl: String` (nullable) | `teaserImages: TeaserImageItem[]` (array) |
| PresentationSection type | No change | New optional `imageUrl?: string` field |
| New files | EventTeaserImageService + Controller | + EventTeaserImage entity + EventTeaserImageRepository |
| Settings UI | Single thumbnail + Upload/Remove | Gallery of N thumbnails, each with own remove button |
| Presentation slides | 1 optional slide | N optional slides (one per image) |

⚠️ **PRD Update Needed**: `docs/prd/epic-10-additional-stories.md` Story 10.22 section should be updated to reflect the multi-image design before this story ships, to keep the PRD as the source of truth.

### Coverage Statistics

- Total PRD FRs: 8
- FRs covered in story: 8
- Coverage percentage: **100%**
- Scope expansions beyond PRD: 8 intentional additions (documented above)

---

## UX Alignment Assessment

### UX Document Status

**Not Found** — No dedicated UX/wireframe document exists for Story 10.22.

Search confirmed: no file matching `docs/wireframes/**/*10-22*` or `docs/**/*ux*.md` is relevant to this story. The watch-app UX document (`docs/watch-app/ux-design-specification.md`) is unrelated.

### UX Implied by PRD?

**Yes.** The PRD explicitly describes two UI surfaces:
1. **EventSettingsTab** — upload button, thumbnail preview, remove button (organizer admin screen)
2. **PresentationPage** — new full-screen teaser image slide (moderator-facing)

### Architecture Support for Implied UX

| UI Need | Architecture Support | Status |
|---------|---------------------|--------|
| MUI Paper section with thumbnails in Settings tab | MUI + React patterns established in frontend architecture | ✅ |
| Hidden `<input type="file">` for upload | Existing `useFileUpload` hook pattern | ✅ |
| S3 presigned PUT from browser | Established in SpeakerProfilePhotoService + 6.x stories | ✅ |
| React Query invalidation on upload/delete | `useQueryClient()` pattern throughout codebase | ✅ |
| Full-screen `<img>` slide with `object-fit: cover` | PresentationPage slide pattern (10.8a) | ✅ |
| Per-thumbnail remove button (icon button overlay) | MUI IconButton pattern | ✅ |

### Warnings

⚠️ **W1 — No wireframe for gallery layout**: The story specifies thumbnail dimensions (`width: 160, height: 90, objectFit: cover`) but does not specify how many columns, wrapping behaviour, or empty-state presentation beyond text. Dev must make layout calls independently. Risk: low (organizer-internal screen, no public design constraints).

⚠️ **W2 — No maximum image count defined**: The PRD and story place no upper bound on the number of teaser images. An organizer could upload 20+ images. No validation, UI warning, or hard limit is specified. Recommendation: consider adding a soft cap (e.g., 10) with a UI hint, or document "no limit" as an explicit decision.

⚠️ **W3 — No image reorder UX**: `displayOrder` is set by insertion order only (no drag-to-reorder). This matches the 10.21 precedent (drag-to-reorder explicitly out of scope there). The story does not state this limitation explicitly in user-facing terms — the organizer cannot rearrange images after upload without delete-and-re-upload.

---

## Epic Quality Review

### Story Structure Validation

**User Value Focus:** ✅ PASS
- Story title: "Event Teaser Images on Moderator Presentation Page"
- User story: "As an organizer, I want to upload one or more teaser images... giving the audience a visual mood-setter sequence"
- Delivers organizer-facing value independently ✅
- The slide experience is immediately visible to moderator at next presentation ✅

**Story Independence:** ✅ PASS
- Single stated prerequisite: Story 10.8a (PresentationPage) — already **complete** ✅
- Story 10.21 (event-photos-gallery) is a **peer** story, not a hard dependency — only a Flyway version coordination concern, handled by the "check at dev time" instruction ✅
- No story in Epic 10 depends on 10.22 being complete first ✅

**Database Creation Timing:** ✅ PASS
- Migration creates only the table this story needs (`event_teaser_images`)
- No big-bang schema creation ✅

---

### Acceptance Criteria Review

| AC | Testable? | Specific? | Error Cases? | Assessment |
|----|-----------|-----------|--------------|------------|
| AC1: Migration runs cleanly, existing events unaffected | ✅ | ✅ | N/A | ✅ Good |
| AC2: Upload N images via presigned URL; gallery shown after each confirm | ✅ | ✅ | ❌ No upload-failure scenario | 🟡 Minor gap |
| AC3: Remove individual image by ID; DB row deleted, S3 deleted | ✅ | ✅ | ❌ No not-found scenario in AC | 🟡 Minor gap |
| AC4: One full-screen slide per image (in displayOrder), between topic-reveal and agenda-preview | ✅ | ✅ | N/A | ✅ Good |
| AC5: Empty teaserImages → no slide inserted | ✅ | ✅ | N/A | ✅ Good |
| AC6: TDD unit + integration tests | ✅ | ✅ (names given) | N/A | ✅ Good |
| AC7: OpenAPI spec committed first | ✅ | ✅ | N/A | ✅ Good |
| AC8: i18n de/en; type-check; Checkstyle | ✅ | ✅ | N/A | ✅ Good |

**AC note**: AC2 mentions "via presigned URL" — this is an implementation detail embedded in an AC. Acceptable for this project's dev-story conventions, not a violation.

---

### Dependency Analysis

| Type | Finding | Status |
|------|---------|--------|
| Forward dependency | None found | ✅ |
| Backward dependency (10.8a) | Complete | ✅ |
| Peer conflict (10.21 Flyway version) | Handled by dev-time check instruction | ✅ |
| Cross-service dependencies | None — single service (event-management-service) | ✅ |
| API Gateway changes | Explicitly noted as NOT needed | ✅ |

---

### Best Practices Compliance Checklist

- [x] Story delivers user value (organizer visual tool, audience experience)
- [x] Story completable independently (prereq 10.8a is done)
- [x] Stories appropriately sized (10 phases, all backend+frontend, dense but single deployment unit)
- [x] No forward dependencies
- [x] Database table created only when needed (this migration, not before)
- [x] Acceptance criteria are specific and testable
- [x] Traceability to PRD FRs maintained (all 8 covered)
- [x] TDD mandated (RED phase before GREEN)
- [x] OpenAPI-first (ADR-006 compliant)

---

### Quality Violations by Severity

#### 🔴 Critical Violations

**None.**

#### 🟠 Major Issues

**M1 — PRD not updated for multi-image scope expansion**
The PRD (`docs/prd/epic-10-additional-stories.md`, lines 1562–1622) still describes the original single-image design (columns on events table, single upload/delete endpoints, `teaserImageUrl: String` on EventResponse). The story now implements a fundamentally different data model and API contract. The PRD is the source of truth for the project — this divergence is a risk for:
- Future dev agents who read the PRD before the story file
- Any architecture review that uses PRD as baseline
- **Recommendation**: Update the PRD Story 10.22 section to reflect the multi-image design before development begins.

#### 🟡 Minor Concerns

**m1 — No error-state ACs**
Upload failure (S3 unavailable, network timeout) and remove failure are handled by i18n keys (`uploadError`, `removeError`) and `teaserError` local state, but no AC explicitly verifies the user-visible error behaviour. Unit test (`confirmUpload_whenS3ObjectMissing_throws`) covers backend error, but no AC covers what the UI renders when upload fails.
- **Recommendation**: Add AC: "If upload or removal fails, an inline error message is displayed and the gallery state is not changed."

**m2 — No max-image-count guard**
No upper bound on `event_teaser_images` rows per event. An organizer uploading 30 images would generate 30 slides in the presentation — likely unintended. No technical guard, no UI warning.
- **Recommendation**: Decide and document an explicit limit (suggest: 10 images max), enforce in service layer (`if (repository.countByEventCode(eventCode) >= MAX_IMAGES) throw ...`), and surface in the UI.

**m3 — N+1 query risk on event list endpoints**
The story recommends populating `teaserImages` in `EventService.toEventResponse()` via `eventTeaserImageRepository.findByEventCodeOrderByDisplayOrderAsc(eventCode)`. For event list endpoints that return many events, this fires one additional query per event. The risk is low for BATbern (small dataset), but worth noting.
- **Recommendation**: For now, acceptable. If list performance degrades, use a `JOIN FETCH` or batch-load approach. Document the known trade-off in the Dev Notes.

**m4 — displayOrder gaps not documented**
When image at position 1 of [0,1,2] is deleted, the remaining orders are [0,2] — a gap. The story does not explicitly state whether gaps are acceptable (they are, for ordered display). A future developer could be confused.
- **Recommendation**: Add one sentence to Dev Notes: "displayOrder gaps after deletion are acceptable; the array is sorted by value, not expected to be contiguous."

---

## Summary and Recommendations

### Overall Readiness Status

**🟡 READY WITH MINOR FIXES RECOMMENDED**

Story 10.22 is well-structured and implementation-ready. All 8 PRD functional requirements are fully covered. TDD mandate is explicit. Phasing is correct (OpenAPI-first, RED-before-GREEN). No forward dependencies. No critical violations.

One major issue (PRD misalignment) and four minor concerns are identified — none block development, but the PRD update should happen before or immediately after dev begins.

---

### Issues Summary

| ID | Severity | Issue | Blocks Dev? |
|----|----------|-------|-------------|
| M1 | 🟠 Major | PRD Story 10.22 still describes single-image design; story now implements multi-image child table | No — but fix soon |
| m1 | 🟡 Minor | No AC covering upload/remove error-state user experience | No |
| m2 | 🟡 Minor | No max-image-count guard (unlimited images allowed) | No |
| m3 | 🟡 Minor | N+1 query risk on event list endpoints when populating `teaserImages` | No (low data volume) |
| m4 | 🟡 Minor | `displayOrder` gap behaviour after deletion not documented in Dev Notes | No |
| W1 | ⚠️ Warning | No wireframe for gallery layout — dev must make layout decisions | No |
| W2 | ⚠️ Warning | No max-image-count UX guidance | No (same as m2) |
| W3 | ⚠️ Warning | No image reorder UX — insert-order only, no drag-to-reorder | No (by design) |

**Total issues: 1 major, 4 minor, 3 warnings | Critical: 0**

---

### Recommended Next Steps

1. **[Before dev] Update PRD** — Update `docs/prd/epic-10-additional-stories.md` Story 10.22 section to reflect the multi-image design: child table, array response, per-image delete endpoint, gallery UI. This keeps the PRD as the authoritative source of truth.

2. **[Story fix] Add error-state AC** — Add to Acceptance Criteria: "If upload or removal fails, an inline error message is displayed; gallery state is unchanged." This ensures the `teaserError` state and i18n error keys are tested/verified, not just implemented silently.

3. **[Story fix] Define max-image-count** — Decide on a limit (suggest 10). Add service-layer guard and a UI hint when the limit is reached. Add to Dev Notes if choosing "no limit is intentional."

4. **[Story fix — cosmetic] Add displayOrder gap note** — One sentence in Dev Notes: "Gaps in displayOrder after deletion are acceptable; ordering is by value, not by contiguity."

5. **[Dev-time] Verify Flyway version** — Run `ls services/event-management-service/src/main/resources/db/migration/V*.sql | sort -V | tail -5` before writing migration. Do not assume V79.

6. **[Dev-time] Verify EventService.toDto() approach** — Confirm how the existing list endpoint (`GET /events`) fetches events. If it calls `toEventResponse()` for each event, add a `TODO` comment noting the known N+1 for `teaserImages`. Acceptable at current scale.

---

### Final Note

This assessment identified **8 issues** across **4 categories** (PRD alignment, acceptance criteria, UX specification, story documentation). **Zero critical violations** were found. The story's phasing, TDD mandate, OpenAPI-first requirement, and dependency management are all sound. The primary action item is updating the PRD to match the expanded scope — this is a documentation hygiene issue, not a design flaw.

**The story is cleared for development with the above recommendations noted.**

---

---

## Post-Assessment Addendum (2026-03-03)

### ADR-002 Compliance — Resolved

During follow-up review, `docs/architecture/ADR-002-generic-file-upload-service.md` was identified as not having been checked during the initial IR. That ADR mandates a Generic File Upload Service (`GenericLogoService`) for all entity-type uploads.

**Finding**: Story 10.22 uses entity-specific `S3Presigner` + `S3Client` directly (same as `SpeakerProfilePhotoService`), bypassing `GenericLogoService`.

**Resolution — Option C selected**: ADR-002 was **amended** (2026-03-03) to clarify its scope. The generic service applies only to *entity-creation-with-file* scenarios (circular dependency). Uploading to an *already-existing entity* (event, speaker) uses the entity-specific presigned URL pattern — this is now explicitly documented as compliant.

**Actions taken**:
1. `docs/architecture/ADR-002-generic-file-upload-service.md` — "Scope Clarification" section added
2. `_bmad-output/implementation-artifacts/10-22-event-teaser-image.md` — ADR-002 compliance note added to Dev Notes

**Status: ✅ Resolved — story pattern is architecturally sound and documented**

---

*Assessment generated: 2026-03-03*
*Assessor: Winston (Architect Agent) — BATbern Project*
*Scope: Story 10.22 — Event Teaser Images on Moderator Presentation Page*
