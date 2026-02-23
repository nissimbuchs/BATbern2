# Story 8.4: Partner Notes

Status: review

## Story

As an **organizer**,
I want to record and manage private notes about a partner company,
so that I can track relationship history, important discussions, and internal context without that information being visible to the partner.

## Context

`PartnerNotesTab.tsx` is already fully built with CRUD UI (add/edit/delete dialogs, role-based visibility). `usePartnerNotes.ts` is a stub returning an empty array. No backend exists for notes. The user requirement is explicit: **partners must not see notes at all** (stronger than the current read-only placeholder in Story 8.0 — the Notes tab must be completely hidden for PARTNER users).

### Current state (pre-story)

| Layer | State |
|-------|-------|
| `PartnerNotesTab.tsx` | ✅ Fully implemented UI (CRUD dialogs, role guard hides edit/delete for PARTNER) |
| `usePartnerNotes.ts` | ⚠️ Stub — returns `[]`, all mutations are no-ops |
| `partnerNotesApi.ts` | ❌ Does not exist |
| Backend DB | ❌ No `partner_notes` table |
| Backend service/controller | ❌ Does not exist |
| `PartnerTabNavigation.tsx` | ⚠️ Only filters **Settings** for PARTNER — Notes tab still visible |
| `PartnerDetailScreen.tsx` | ⚠️ `PARTNER_MAX_TAB = 4` allows reaching Notes (tab 4) for PARTNER |

### Type mismatch to fix

`PartnerNotesTab` sends `createNote({ title, content })` and `updateNote({ noteId, title, content })`. The existing stub `UpdateNoteRequest` only declares `content?: string` (missing `title`). The real DTOs must include both fields. The hook types need updating when wiring.

## Acceptance Criteria

1. **AC1 — ORGANIZER-only backend**: All notes endpoints require `ROLE_ORGANIZER`. A PARTNER JWT or unauthenticated request returns 403. Enforced by `SecurityConfig` + `@PreAuthorize`.

2. **AC2 — List notes**: `GET /api/v1/partners/{companyName}/notes` returns all notes sorted by `created_at` descending. Returns `[]` if none exist. Returns 404 if `companyName` is unknown.

3. **AC3 — Create note**: `POST /api/v1/partners/{companyName}/notes` creates a note. `title` and `content` are required. `author_username` is captured from the authenticated user's JWT (`custom:username` claim, via `SecurityContextHelper.getCurrentUsername()`). Returns 201 with the created `PartnerNoteDTO`.

4. **AC4 — Update note**: `PATCH /api/v1/partners/{companyName}/notes/{noteId}` updates `title` and/or `content` (partial update — only provided non-null fields are changed). Returns 200 with updated `PartnerNoteDTO`. Returns 404 if note not found.

5. **AC5 — Delete note**: `DELETE /api/v1/partners/{companyName}/notes/{noteId}` deletes the note. Returns 204 No Content. Returns 404 if not found.

6. **AC6 — Frontend wired**: `PartnerNotesTab` renders notes from the real API. Create, edit, and delete call the real backend. Loading and error states render correctly.

7. **AC7 — Notes tab hidden for PARTNER**: `PartnerTabNavigation` completely hides the Notes tab when `role === 'PARTNER'`. `PartnerDetailScreen.PARTNER_MAX_TAB` updated to 3 (Analytics). Defensive render guard added to the Notes tab panel (`currentUser.role !== 'PARTNER'`), consistent with the Settings tab guard from Story 8.0 CR H2.

8. **AC8 — Existing tests green**: All previously passing tests in `PartnerNotesTab.test.tsx`, `PartnerDetailScreen.test.tsx`, and `PartnerTabNavigation.test.tsx` remain green after this story.

9. **AC9 — i18n**: All UI strings exist in both `public/locales/en/partners.json` and `de/partners.json`. Most already exist from Story 2.8.2. Add any missing keys.

## What is deliberately cut

| Removed | Reason |
|---------|--------|
| Note pinning / priority ranking | Not needed |
| Mention / @-tagging other organizers | Not needed |
| Attachments on notes | Not needed |
| Note categories / tags | Not needed |
| Audit trail / change history | Not needed |
| Full-text search across notes | Not needed |
| Partner-visible notes (separate tier) | Out of scope — organizer-internal only |

## Tasks / Subtasks

### Task 1: DB Migration (AC: 2, 3, 4, 5)

- [x] Create `services/partner-coordination-service/src/main/resources/db/migration/V7__create_partner_notes_table.sql`:

```sql
CREATE TABLE IF NOT EXISTS partner_notes (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id      UUID         NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
    title           VARCHAR(500) NOT NULL,
    content         TEXT         NOT NULL,
    author_username VARCHAR(100) NOT NULL,  -- ADR-003: organizer username (not UUID)
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_partner_notes_partner_id ON partner_notes(partner_id);
CREATE INDEX IF NOT EXISTS idx_partner_notes_created_at ON partner_notes(created_at DESC);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'update_partner_notes_updated_at') THEN
        CREATE TRIGGER update_partner_notes_updated_at
            BEFORE UPDATE ON partner_notes
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;
```

### Task 2: OpenAPI Specification (ADR-006 — spec before code)

- [x] Create `docs/api/partner-notes-api.openapi.yml` (separate file, same pattern as `partner-meetings-api.openapi.yml`)
- [x] Endpoints:
  - `GET  /partners/{companyName}/notes` — list notes sorted by `createdAt` desc (ORGANIZER)
  - `POST /partners/{companyName}/notes` — create note, returns 201 (ORGANIZER)
  - `PATCH /partners/{companyName}/notes/{noteId}` — partial update, returns 200 (ORGANIZER)
  - `DELETE /partners/{companyName}/notes/{noteId}` — delete, returns 204 (ORGANIZER)
- [x] Schemas:
  - `PartnerNoteDTO`: `id` (UUID), `title` (string), `content` (string), `authorUsername` (string), `createdAt` (datetime), `updatedAt` (datetime)
  - `CreateNoteRequest`: `title` (required, string, max 500), `content` (required, string)
  - `UpdateNoteRequest`: `title` (optional, string, max 500), `content` (optional, string)
- [x] Generate TypeScript types: `cd web-frontend && npm run generate:api-types`
  - Produces `src/types/generated/partner-notes-api.types.ts`

### Task 3: Domain Entity + Repository (AC: 2–5)

- [x] Create `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/PartnerNote.java`:
  - `@Entity @Table(name = "partner_notes")`
  - Fields: `id` (UUID), `partnerId` (UUID FK), `title` (String), `content` (String), `authorUsername` (String), `createdAt`, `updatedAt`
  - `@PreUpdate` to update `updatedAt` (or rely on DB trigger — prefer DB trigger, consistent with other entities)

- [x] Create `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/PartnerNoteRepository.java`:
  - Extends `JpaRepository<PartnerNote, UUID>`
  - `List<PartnerNote> findByPartnerIdOrderByCreatedAtDesc(UUID partnerId)`

### Task 4: DTOs (AC: 2–5)

- [x] Create `PartnerNoteDTO.java` — `id`, `title`, `content`, `authorUsername`, `createdAt`, `updatedAt`
- [x] Create `CreateNoteRequest.java` — `title` (`@NotBlank`), `content` (`@NotBlank`)
- [x] Create `UpdateNoteRequest.java` — `title` (nullable String), `content` (nullable String)

Note: DO NOT implement the generated `partner-notes-api` OpenAPI interface on the controller. Use standalone controller (same pattern as `PartnerMeetingController`). The OpenAPI spec is for documentation and TypeScript type generation only.

### Task 5: PartnerNoteService (AC: 2–5)

- [x] Create `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerNoteService.java`:
  - `getNotes(String companyName): List<PartnerNoteDTO>` — look up `Partner` by `companyName`, fetch notes by `partnerId`, map to DTO. Throw `PartnerNotFoundException` if company not found.
  - `createNote(String companyName, CreateNoteRequest req, String authorUsername): PartnerNoteDTO` — look up partner, create `PartnerNote`, persist, return DTO.
  - `updateNote(UUID noteId, UpdateNoteRequest req): PartnerNoteDTO` — load note by ID (throw `PartnerNoteNotFoundException` if absent), apply non-null fields only, save, return DTO.
  - `deleteNote(UUID noteId)` — load note by ID, delete. Throw `PartnerNoteNotFoundException` if not found.

- [x] Create `services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/PartnerNoteNotFoundException.java` (extends `RuntimeException`, analogous to `PartnerNotFoundException`)

- [x] Register `PartnerNoteNotFoundException` in `GlobalExceptionHandler.java` → 404 response.

### Task 6: PartnerNoteController + SecurityConfig (AC: 1–5)

- [x] Create `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/PartnerNoteController.java`:
  - `@RestController @RequestMapping("/api/v1/partners/{companyName}/notes")`
  - All methods: `@PreAuthorize("hasRole('ORGANIZER')")`
  - `GET /` → 200 + `List<PartnerNoteDTO>`
  - `POST /` → 201 + `PartnerNoteDTO`. Capture author via `securityContextHelper.getCurrentUsername()`
  - `PATCH /{noteId}` → 200 + `PartnerNoteDTO`
  - `DELETE /{noteId}` → 204 No Content

- [x] Add to `SecurityConfig.productionFilterChain` (after the partner-meetings line):
  ```java
  // Story 8.4: Partner Notes (ORGANIZER only — partners must not see notes)
  .requestMatchers("/api/v1/partners/*/notes/**").hasRole("ORGANIZER")
  ```

### Task 7: Frontend API Client (AC: 6)

- [x] Create `web-frontend/src/services/api/partnerNotesApi.ts`:
  ```typescript
  import apiClient from '@/services/api/apiClient';
  import type { components } from '@/types/generated/partner-notes-api.types';

  export type PartnerNoteDTO = components['schemas']['PartnerNoteDTO'];
  export type CreateNoteRequest = components['schemas']['CreateNoteRequest'];
  export type UpdateNoteRequest = components['schemas']['UpdateNoteRequest'];

  export const getPartnerNotes = async (companyName: string): Promise<PartnerNoteDTO[]> => {
    const response = await apiClient.get<PartnerNoteDTO[]>(`/partners/${companyName}/notes`);
    return response.data;
  };

  export const createPartnerNote = async (
    companyName: string,
    req: CreateNoteRequest
  ): Promise<PartnerNoteDTO> => {
    const response = await apiClient.post<PartnerNoteDTO>(`/partners/${companyName}/notes`, req);
    return response.data;
  };

  export const updatePartnerNote = async (
    companyName: string,
    noteId: string,
    req: UpdateNoteRequest
  ): Promise<PartnerNoteDTO> => {
    const response = await apiClient.patch<PartnerNoteDTO>(
      `/partners/${companyName}/notes/${noteId}`,
      req
    );
    return response.data;
  };

  export const deletePartnerNote = async (
    companyName: string,
    noteId: string
  ): Promise<void> => {
    await apiClient.delete(`/partners/${companyName}/notes/${noteId}`);
  };
  ```

### Task 8: Wire usePartnerNotes Hook (AC: 6)

Replace all stubs in `web-frontend/src/hooks/usePartnerNotes.ts` with real API calls.

- [x] Remove local stub interface definitions; import types from `partnerNotesApi`:
  ```typescript
  import {
    getPartnerNotes,
    createPartnerNote,
    updatePartnerNote,
    deletePartnerNote,
  } from '@/services/api/partnerNotesApi';
  import type { PartnerNoteDTO, CreateNoteRequest, UpdateNoteRequest } from '@/services/api/partnerNotesApi';
  ```

- [x] Update `UsePartnerNotesReturn.data` type to `PartnerNoteDTO[] | undefined`

- [x] Update `UpdateNoteRequest` usage — the UI calls `updateNote({ noteId, title, content })`. The hook's `updateNote` must destructure `noteId` and pass `{ title, content }` as the `UpdateNoteRequest`:
  ```typescript
  mutationFn: async ({ noteId, ...req }: { noteId: string } & UpdateNoteRequest) =>
    updatePartnerNote(companyName, noteId, req),
  ```

- [x] Wire query: `queryFn: () => getPartnerNotes(companyName)`

- [x] Wire create mutation: `mutationFn: (note: CreateNoteRequest) => createPartnerNote(companyName, note)`

- [x] Wire delete mutation: `mutationFn: (noteId: string) => deletePartnerNote(companyName, noteId)`

- [x] Keep `staleTime: 2 * 60 * 1000` (2 minutes) and `queryKey: ['partner', companyName, 'notes']`

### Task 9: Hide Notes Tab for PARTNER (AC: 7)

**`PartnerTabNavigation.tsx`** — update `getVisibleTabs`:
```typescript
const getVisibleTabs = (role?: UserRole) => {
  if (role === 'PARTNER') {
    // Story 8.4: Notes are organizer-internal — hidden completely from PARTNER
    return ALL_TAB_LABELS.filter((label) => label !== 'Settings' && label !== 'Notes');
  }
  return ALL_TAB_LABELS;
};
```

**`PartnerDetailScreen.tsx`** — two changes:

1. Update clamping constant (PARTNER now has max 3 visible tabs = Analytics at index 3):
```typescript
// Story 8.4: Notes (4) and Settings (5) are not visible for PARTNER
const PARTNER_MAX_TAB = 3; // was 4
```

2. Add defensive render guard on Notes panel (consistent with Settings tab guard, Story 8.0 CR H2):
```typescript
{/* Notes Tab — Story 8.4: hidden for PARTNER (organizer-internal notes) */}
{effectiveTab === 4 && currentUser.role !== 'PARTNER' && (
  <PartnerNotesTab companyName={partner.companyName} role={currentUser.role} />
)}
```

### Task 10: i18n Check (AC: 9)

- [x] Verify all keys used in `PartnerNotesTab.tsx` exist in both locale files:
  - `detail.notesTab.noNotes`, `detail.notesTab.addNote`, `detail.notesTab.title`
  - `detail.notesTab.editNote`, `detail.notesTab.titleLabel`, `detail.notesTab.contentLabel`
  - `detail.notesTab.htmlSupported`, `detail.notesTab.deleteNoteConfirmTitle`
  - `detail.notesTab.deleteNoteConfirmMessage`, `modal.actions.cancel`, `modal.actions.save`, `modal.actions.delete`
- [x] Add any missing keys to `public/locales/en/partners.json` and `de/partners.json`

### Task 11: Backend Integration Tests (AC: 1–5)

- [x] Create `PartnerNoteControllerIntegrationTest.java` (extends `AbstractIntegrationTest`):
  - `should_listNotes_when_organizerRequests` → 200 + empty array (no notes yet)
  - `should_createNote_when_validRequest` → 201, body has `title`, `content`, `authorUsername`
  - `should_returnNotesSortedByCreatedAtDesc_when_multipleNotesExist`
  - `should_updateNote_when_patchRequest` → 200, only changed fields updated
  - `should_deleteNote_when_deleteRequest` → 204, note no longer returned on GET
  - `should_return404_when_companyNameUnknown` — GET list for unknown company → 404
  - `should_return404_when_noteIdUnknown` — PATCH/DELETE unknown noteId → 404

Note: Integration test profile permits all requests (see `testFilterChain`). `@PreAuthorize` is tested separately via unit test or documented as ADR-008 compliance in SecurityConfig.

### Task 12: Frontend Tests (AC: 6, 7, 8)

**`usePartnerNotes.test.ts`** — update for real API:
- [x] Mock `partnerNotesApi` module
- [x] `should_fetchNotes_when_usePartnerNotesCalled` → mock returns `[note1, note2]`, verify `data === [note1, note2]` (remove the `expect([]).toEqual([])` assertion that only passed because of the stub)
- [x] `should_createNote_when_createNoteCalled` → call `result.current.createNote(...)`, verify `createPartnerNote` was called with correct args
- [x] `should_deleteNote_when_deleteNoteCalled` → verify `deletePartnerNote` called
- [x] Keep `should_notFetch_when_companyNameUndefined` and `should_haveCorrectQueryKey_when_hookCalled`

**`PartnerTabNavigation.test.tsx`** (update or create if not exists):
- [x] `should_hideNotesTab_when_partnerRole` → render with `role="PARTNER"`, verify "Notes" tab absent
- [x] `should_showNotesTab_when_organizerRole` → render with `role="ORGANIZER"`, verify "Notes" tab present
- [x] Regression: `should_hideSettingsTab_when_partnerRole` still passes

**`PartnerDetailScreen.test.tsx`** — add:
- [x] `should_notRenderNotesTab_when_partnerRoleAndEffectiveTabFour` → verify Notes panel does not render for PARTNER even if `activeTab` happens to be 4 (clamped to 3, but also check the defensive guard)
- [x] Existing tests for `hideAddNoteButton_partner` and `showAddNoteButton_organizer` must still pass (no regression from Task 9 changes)

**`PartnerNotesTab.test.tsx`** — fix pre-existing failures:
- [x] Two pre-existing test failures noted in Story 8.3 completion notes. Verify if they are now fixed (MUI Dialog confirm was already applied in Story 8.0 CR M4). If still failing, fix them. All 12 tests must be green.

## Dev Agent Record

**Agent:** Amelia (Dev Agent)
**Completed:** 2026-02-23
**Status:** All 12 tasks complete — all tests green

### Implementation Summary

All implementation done TDD (tests written first). Full end-to-end implementation:

- **Backend**: V7 Flyway migration, `PartnerNote` entity + repository, DTOs (`PartnerNoteDTO`, `CreateNoteRequest`, `UpdateNoteRequest`), `PartnerNoteService`, `PartnerNoteController`, `PartnerNoteNotFoundException`, `SecurityConfig` URL pattern, `GlobalExceptionHandler` 404 handler. 13/13 integration tests pass.
- **Frontend**: `partner-notes-api.openapi.yml` spec + TypeScript type generation, `partnerNotesApi.ts` API client, `usePartnerNotes.ts` rewritten (stub → real API), `PartnerTabNavigation.tsx` Notes hidden for PARTNER, `PartnerDetailScreen.tsx` PARTNER_MAX_TAB 4→3 + defensive guard.
- **Tests**: `usePartnerNotes.test.ts` (5 tests), `PartnerTabNavigation.test.tsx` (3 new tests), `PartnerDetailScreen.test.tsx` (1 new test), `PartnerNotesTab.test.tsx` (12 tests all green).

### Design Decisions

- `PartnerNoteController` does not implement generated OpenAPI interface — same standalone pattern as `PartnerMeetingController` to avoid regeneration churn on existing `PartnersApi`.
- `author_username` captured from `custom:username` JWT claim, falling back to `sub` then `"system"` — consistent with ADR-003 (username as meaningful string ID).
- DB trigger used for `updated_at` (no `@PreUpdate`) — consistent with other entities in service.
- `PartnerNoteNotFoundException` extends `RuntimeException` (not shared-kernel `NotFoundException`) — service-local exception, consistent with `PartnerNotFoundException`.

### File List

**New files:**
- `services/partner-coordination-service/src/main/resources/db/migration/V7__create_partner_notes_table.sql`
- `docs/api/partner-notes-api.openapi.yml`
- `web-frontend/src/types/generated/partner-notes-api.types.ts`
- `web-frontend/src/services/api/partnerNotesApi.ts`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/PartnerNote.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/repository/PartnerNoteRepository.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/PartnerNoteDTO.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/CreateNoteRequest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/UpdateNoteRequest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerNoteService.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/PartnerNoteNotFoundException.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/PartnerNoteController.java`
- `services/partner-coordination-service/src/test/java/ch/batbern/partners/controller/PartnerNoteControllerIntegrationTest.java`

**Modified files:**
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/config/SecurityConfig.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/GlobalExceptionHandler.java`
- `web-frontend/src/hooks/usePartnerNotes.ts`
- `web-frontend/src/components/organizer/PartnerManagement/PartnerTabNavigation.tsx`
- `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailScreen.tsx`
- `web-frontend/src/hooks/usePartnerNotes.test.ts`
- `web-frontend/src/components/organizer/PartnerManagement/PartnerTabNavigation.test.tsx`
- `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailScreen.test.tsx`
- `web-frontend/package.json` (added `generate:api-types:partner-notes` script)

## Dev Notes

### Why a separate `partner-notes-api.openapi.yml` (not added to `partners-api.openapi.yml`)?

`PartnerController` implements the generated `PartnersApi` interface from `partners-api.openapi.yml`. Adding notes endpoints there would require regenerating and modifying that interface + controller, risking regression in all existing partner CRUD tests. Following the Epic 8 pattern (separate spec per feature), `PartnerNoteController` is standalone and imports no generated interface.

### author_username capture

`SecurityContextHelper.getCurrentUsername()` reads `custom:username` from the JWT claim (set by Cognito PreTokenGeneration Lambda). In local dev (profile `local`), all requests are permitted with no JWT, so consider using `getCurrentUserIdOrSystem()` as a safe fallback — or ensure local dev always passes a JWT via the auth headers (existing standard).

### Tab index arithmetic

ALL_TAB_LABELS = `['Overview'(0), 'Contacts'(1), 'Meetings'(2), 'Analytics'(3), 'Notes'(4), 'Settings'(5)]`

For PARTNER after this story, visible tabs = `['Overview', 'Contacts', 'Meetings', 'Analytics']` — indices 0–3 visually. Since all hidden tabs (Notes=4, Settings=5) are at the end of the array, the visual index exactly matches the ALL_TAB_LABELS index for all visible PARTNER tabs. No remapping needed.

### PartnerNotesTab defensive guard (read-only mode)

`PartnerNotesTab` still has `const isPartner = role === 'PARTNER'` which hides CRUD controls for partners. After this story, a PARTNER can never reach the Notes tab, making that code dead for PARTNER role. **Do not remove it** — it provides belt-and-suspenders defence and costs nothing.

### ADR Compliance

- **ADR-003**: `partner_notes.author_username` (String, not UUID) — consistent with `partner_meetings.created_by`
- **ADR-006**: OpenAPI spec (`partner-notes-api.openapi.yml`) created before backend implementation
- **ADR-008**: Backend `SecurityConfig` controls access — `hasRole('ORGANIZER')` on URL pattern + `@PreAuthorize` on controller methods

### Project Structure (new files)

```
services/partner-coordination-service/
├── domain/
│   └── PartnerNote.java                             ← new
├── repository/
│   └── PartnerNoteRepository.java                   ← new
├── dto/
│   ├── PartnerNoteDTO.java                          ← new
│   ├── CreateNoteRequest.java                       ← new
│   └── UpdateNoteRequest.java                       ← new
├── service/
│   └── PartnerNoteService.java                      ← new
├── controller/
│   └── PartnerNoteController.java                   ← new
├── exception/
│   └── PartnerNoteNotFoundException.java            ← new
├── config/
│   └── SecurityConfig.java                          ← modified (add notes URL pattern)
├── exception/
│   └── GlobalExceptionHandler.java                  ← modified (add PartnerNoteNotFoundException → 404)
└── resources/db/migration/
    └── V7__create_partner_notes_table.sql           ← new

docs/api/
└── partner-notes-api.openapi.yml                    ← new

web-frontend/src/
├── types/generated/
│   └── partner-notes-api.types.ts                  ← generated (npm run generate:api-types)
├── services/api/
│   └── partnerNotesApi.ts                          ← new
└── hooks/
    └── usePartnerNotes.ts                           ← modified (stub → real API)
```

**Modified existing files:**
- `web-frontend/src/components/organizer/PartnerManagement/PartnerTabNavigation.tsx` — filter Notes for PARTNER
- `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailScreen.tsx` — PARTNER_MAX_TAB 4→3, defensive Notes guard
- `web-frontend/src/hooks/usePartnerNotes.test.ts` — update for real API
- `web-frontend/src/components/organizer/PartnerManagement/PartnerDetailScreen.test.tsx` — new tab clamping tests
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/exception/GlobalExceptionHandler.java` — PartnerNoteNotFoundException handler

### Performance Target

| Metric | Target |
|--------|--------|
| GET notes list (cached) | <200ms |
| GET notes list (cold, ≤50 notes) | <500ms |
| Create / update / delete note | <500ms |

No cross-service calls are needed — notes live entirely in `partner-coordination-service`.

### References

- [Source: web-frontend/src/components/organizer/PartnerManagement/PartnerNotesTab.tsx — existing UI]
- [Source: web-frontend/src/hooks/usePartnerNotes.ts — stub to replace]
- [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/security/SecurityContextHelper.java — getCurrentUsername()]
- [Source: services/partner-coordination-service/src/main/java/ch/batbern/partners/controller/PartnerMeetingController.java — standalone controller pattern]
- [Source: Story 8.0 CR H2 — PARTNER_MAX_TAB clamping pattern]
- [Source: docs/api/partner-meetings-api.openapi.yml — separate spec pattern for Epic 8 features]
- [Source: services/partner-coordination-service/src/main/resources/db/migration/V6__drop_partner_contacts_table.sql — last migration, next is V7]
