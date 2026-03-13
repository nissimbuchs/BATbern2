# Story 10.28: Newsletter Subscriber Management Page

Status: done

## Story

As an **organizer**, I want a dedicated page in the admin portal where I can see all newsletter subscribers in a paginated, searchable, sortable list, so that I can audit the subscriber base and take management actions (unsubscribe, re-subscribe, delete) without having to go to the database directly.

---

## Acceptance Criteria

### AC1 — Top-level navigation item (organizer only)

- A new top-level nav item **"Newsletter Subscribers"** is added to `web-frontend/src/config/navigationConfig.ts`
- `roles: ['organizer']` — invisible to all other roles
- Icon: `PeopleAltOutlined` (or `MailOutlined` if preferred — pick whichever matches the existing nav aesthetics)
- Path: `/organizer/newsletter-subscribers`
- i18n key: `navigation.newsletterSubscribers`
- Appears after the existing "Users" nav item

### AC2 — Backend: paginated, searchable, sortable subscriber list endpoint

- The existing `GET /api/v1/newsletter/subscribers` endpoint is **replaced** with a paginated version (ADR-006: OpenAPI spec updated first)
- Uses the **shared-kernel pagination pattern** (`PaginationUtils`, `PaginatedResponse<T>`, `PaginationMetadata`) — do NOT use Spring Data `Page`/`Pageable`
- Query parameters:
  - `page` (**1-based**, default `1`) — parsed via `PaginationUtils.parseParams()`
  - `limit` (default `20`, max `100`) — capped automatically by `PaginationUtils`
  - `search` (optional — case-insensitive partial match against `email` OR `first_name`)
  - `status` (optional — `active` | `unsubscribed` | `all`; default `all`)
  - `sortBy` (optional — one of: `email`, `firstName`, `subscribedAt`, `unsubscribedAt`, `source`, `language`; default `subscribedAt`) — whitelisted server-side to prevent injection
  - `sortDir` (optional — `asc` | `desc`; default `desc`)
- Response body (`PaginatedResponse<SubscriberResponse>`):
  ```json
  {
    "data": [ /* SubscriberResponse[] */ ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "totalItems": 234,
      "totalPages": 12,
      "hasNext": true,
      "hasPrev": false
    }
  }
  ```
- `SubscriberResponse` gets a new field: `unsubscribedAt` (null if active) — add to existing DTO
- **ORGANIZER role only** (`@PreAuthorize("hasRole('ORGANIZER')")`)
- The count-only `GET /api/v1/newsletter/subscribers/count` endpoint remains untouched (used by `EventNewsletterTab`)

### AC3 — Backend: organizer-triggered subscriber actions

Three new endpoints, all **ORGANIZER only**:

- `POST /api/v1/newsletter/subscribers/{id}/unsubscribe`
  - Sets `unsubscribed_at = NOW()` on the subscriber with `{id}`
  - Returns `200 OK` with updated `SubscriberResponse`, `404` if not found, `409` if already unsubscribed
- `POST /api/v1/newsletter/subscribers/{id}/resubscribe`
  - Clears `unsubscribed_at` (sets to null), preserves original `unsubscribe_token`
  - Returns `200 OK` with updated `SubscriberResponse`, `404` if not found, `409` if already active
- `DELETE /api/v1/newsletter/subscribers/{id}`
  - Hard deletes the subscriber record
  - Returns `204 No Content`, `404` if not found

### AC4 — OpenAPI spec updated first (ADR-006)

- All changes in AC2 and AC3 are added to `docs/api/events.openapi.yml` **before** any backend implementation
- Paginated response references the shared-kernel shape: `data: SubscriberResponse[]` + `pagination: PaginationMetadata`
- `SubscriberResponse` schema updated to include `unsubscribedAt`
- New endpoints fully documented with request params, response schemas, and error codes
- After updating spec: run `npm run generate:api-types` and commit generated types

### AC5 — Backend service layer

- `NewsletterSubscriberService` is extended with:
  - `List<NewsletterSubscriber> findSubscribers(String search, String status, String sortBy, String sortDir, PaginationParams pagination)`
  - `long countSubscribers(String search, String status)`
  - `NewsletterSubscriber unsubscribeById(UUID id)` — sets `unsubscribed_at`
  - `NewsletterSubscriber resubscribeById(UUID id)` — clears `unsubscribed_at`
  - `void deleteById(UUID id)` — hard delete
- `SubscriberResponse.java` DTO: add `unsubscribedAt` field (type `Instant`, nullable)
- Existing `subscribe()` / `unsubscribeByToken()` methods are **not changed**

### AC6 — Frontend: Newsletter Subscribers page

Mirrors the UserManagement feature structure exactly (`UserManagement.tsx` / `UserList.tsx` / `UserTable.tsx` / `UserFilters.tsx`).

**File layout** (`components/organizer/NewsletterSubscribers/`):
- `NewsletterSubscribers.tsx` — route container (analogous to `UserManagement.tsx`)
- `NewsletterSubscriberList.tsx` — main list container (analogous to `UserList.tsx`)
- `NewsletterSubscriberTable.tsx` — table with backend-driven sort (analogous to `UserTable.tsx`)
- `NewsletterSubscriberFilters.tsx` — filters panel (analogous to `UserFilters.tsx`)
- `UnsubscribeDialog.tsx` — confirmation dialog
- `ResubscribeDialog.tsx` — confirmation dialog
- `DeleteSubscriberDialog.tsx` — destructive confirmation (analogous to `DeleteUserDialog.tsx`)
- `index.ts` — barrel export

Route: `/organizer/newsletter-subscribers` → `<NewsletterSubscribers />`

**`NewsletterSubscriberFilters.tsx`** (mirrors `UserFilters.tsx`):
- `Paper` wrapper `p: 2, mb: 3`
- `TextField` for search — debounced 300 ms, writes to store via `setSearchQuery`
- `RadioGroup` for status: `All` / `Active` / `Unsubscribed`
- "Clear Filters" button — resets search + status + sort + page

**`NewsletterSubscriberTable.tsx`** (mirrors `UserTable.tsx`):
- **Backend-driven sort**: column header clicks update `sortBy`/`sortDir` in store → triggers new fetch
- No in-memory sort — render `subscribers` array as-is (already sorted by backend)
- `MoreVert` icon button per row opening a MUI `Menu`:
  - Active subscriber: "Unsubscribe" + "Delete"
  - Unsubscribed subscriber: "Re-subscribe" + "Delete"
- Table columns: Email, First Name, Language (chip), Source (chip), Subscribed At, Status (chip), Actions
- Empty state: `Paper` with centered icon + `{t('table.empty')}`

**`NewsletterSubscriberList.tsx`** (mirrors `UserList.tsx`):
- No view mode toggle (grid/card) — table only
- Loading: `BATbernLoader` centered
- Error: MUI `Alert` + retry `Button`
- Pagination: reuse `UserPagination` component directly

### AC7 — Zustand store: `newsletterSubscriberStore.ts`

New `web-frontend/src/stores/newsletterSubscriberStore.ts` (mirrors `userManagementStore.ts`):

```typescript
interface NewsletterSubscriberFilters {
  searchQuery?: string;
  status?: 'active' | 'unsubscribed' | 'all';
  sortBy?: string;   // default: 'subscribedAt'
  sortDir?: 'asc' | 'desc';  // default: 'desc'
}
// Actions: setFilters, setSearchQuery, setSort, setPage, setLimit, resetFilters, reset
// setFilters / setSearchQuery / setSort / setLimit all reset page → 1
// Store name: 'newsletter-subscriber-store'
```

### AC8 — React Query hook: `useNewsletterSubscriberList`

`web-frontend/src/hooks/useNewsletterSubscribers/useNewsletterSubscriberList.ts` (mirrors `useUserList.ts`):
- `placeholderData: keepPreviousData` (import from `@tanstack/react-query`)
- `staleTime: 5 * 60 * 1000`, `gcTime: 30 * 60 * 1000`
- Query key includes `filters` (which contains `sortBy`/`sortDir`) so sort changes trigger new fetch
- Prefetch next page when `pagination.hasNext`
- `index.ts` barrel at `hooks/useNewsletterSubscribers/index.ts`

### AC9 — API service: `newsletterApi.ts`

New `web-frontend/src/services/api/newsletterApi.ts` (mirrors `userManagementApi.ts`):
- Separate from existing `services/newsletterService.ts` (public/user endpoints stay there)
- Organizer-only functions: `listNewsletterSubscribers`, `unsubscribeNewsletterSubscriber`, `resubscribeNewsletterSubscriber`, `deleteNewsletterSubscriber`
- Pass filters as individual query params (not JSON filter object): `search`, `status`, `sortBy`, `sortDir`
- Response type `PagedNewsletterSubscribersResponse` = `{ data: SubscriberResponse[], pagination: PaginationMetadata }` (generated from OpenAPI)

### AC10 — i18n

New namespace `newsletterSubscribers` in both `en.json` and `de.json`:
```json
"navigation": {
  "newsletterSubscribers": "Newsletter Subscribers"
},
"newsletterSubscribers": {
  "title": "Newsletter Subscribers",
  "loading": { "subscribers": "Loading subscribers…" },
  "error": { "loadFailed": "Failed to load subscribers." },
  "search": { "placeholder": "Search by email or name…" },
  "filters": {
    "status": { "label": "Status", "all": "All", "active": "Active", "unsubscribed": "Unsubscribed" },
    "clearAll": "Clear Filters"
  },
  "table": {
    "empty": "No subscribers found.",
    "headers": { "email": "Email", "firstName": "First Name", "language": "Language",
      "source": "Source", "subscribedAt": "Subscribed", "status": "Status", "actions": "Actions" }
  },
  "status": { "active": "Active", "unsubscribed": "Unsubscribed" },
  "actions": { "openMenu": "Open actions", "unsubscribe": "Unsubscribe", "resubscribe": "Re-subscribe", "delete": "Delete" },
  "pagination": { "itemsPerPage": "Items per page" },
  "dialogs": {
    "unsubscribe": { "title": "Unsubscribe subscriber", "message": "Unsubscribe {{email}}? They will stop receiving newsletters but their record will be kept." },
    "resubscribe": { "title": "Re-subscribe subscriber", "message": "Re-subscribe {{email}}? They will receive future newsletters again." },
    "delete": { "title": "Delete subscriber", "message": "Permanently delete {{email}}? This cannot be undone." }
  },
  "toast": {
    "unsubscribeSuccess": "{{email}} has been unsubscribed.",
    "resubscribeSuccess": "{{email}} has been re-subscribed.",
    "deleteSuccess": "{{email}} has been deleted."
  }
}
```

### AC11 — Tests (TDD: Red-Green-Refactor)

**Backend**:
- `NewsletterSubscriberServiceTest` (unit) — extend existing:
  - `should_unsubscribeSubscriber_when_idExists()`
  - `should_throwConflict_when_subscriberAlreadyUnsubscribed()`
  - `should_resubscribeSubscriber_when_subscriberIsUnsubscribed()`
  - `should_throwConflict_when_subscriberAlreadyActive()`
  - `should_filterBySearch_when_searchParamProvided()`
  - `should_filterByStatus_when_statusIsActive()`
  - `should_filterByStatus_when_statusIsUnsubscribed()`
  - `should_paginateCorrectly_when_pageAndLimitProvided()`
  - `should_sortByEmail_when_sortByEmailRequested()`
- `NewsletterControllerIntegrationTest` (extends `AbstractIntegrationTest`) — extend existing:
  - `should_returnPaginatedResponse_when_organizerListsSubscribers()`
  - `should_filterBySearch_when_searchParamProvided()`
  - `should_filterByStatus_when_statusActive()`
  - `should_sortDescBySubscribedAt_when_defaultSort()`
  - `should_unsubscribeSubscriber_when_organizerRequests()`
  - `should_return409_when_alreadyUnsubscribed()`
  - `should_resubscribeSubscriber_when_organizerRequests()`
  - `should_return409_when_alreadyActive()`
  - `should_deleteSubscriber_when_organizerRequests()`
  - `should_return403_when_nonOrganizerAccesses()`

**Frontend**:
- `newsletterSubscriberStore.test.ts`: `should_resetPage_when_searchQueryChanged()`, `should_resetPage_when_sortChanged()`, `should_resetPage_when_limitChanged()`, `should_clearAllFilters_when_resetFiltersCalled()`
- `NewsletterSubscriberList.test.tsx`: loading state, error state with retry, table render
- `NewsletterSubscriberFilters.test.tsx`: debounced search, status radio, clear filters
- `NewsletterSubscriberTable.test.tsx`: columns render, MoreVert menu correct items per status, sort header click calls `onSortChange`
- `UnsubscribeDialog.test.tsx`, `ResubscribeDialog.test.tsx`, `DeleteSubscriberDialog.test.tsx`: renders email, confirm calls API, success triggers `onSuccess`

---

## Tasks / Subtasks

### Phase 1: OpenAPI spec (AC4)
- [x] Add paginated `GET /newsletter/subscribers` to `docs/api/events.openapi.yml` with `page`, `limit`, `search`, `status`, `sortBy`, `sortDir` params and `PagedNewsletterSubscribers` response schema
- [x] Add `unsubscribedAt` to `SubscriberResponse` schema
- [x] Add `POST /newsletter/subscribers/{id}/unsubscribe` endpoint
- [x] Add `POST /newsletter/subscribers/{id}/resubscribe` endpoint
- [x] Add `DELETE /newsletter/subscribers/{id}` endpoint
- [x] Run `npm run generate:api-types` and commit generated types

### Phase 2: Backend — TDD Red-Green-Refactor (AC2, AC3, AC5)
- [x] **RED**: Write failing tests for all new service methods in `NewsletterSubscriberServiceTest`
- [x] **RED**: Write failing integration tests in `NewsletterControllerIntegrationTest`
- [x] **GREEN**: Add `unsubscribedAt` field to `SubscriberResponse.java` DTO + `toResponse()` mapping
- [x] **GREEN**: Add `findSubscribers()` + `countSubscribers()` to `NewsletterSubscriberService` (JPQL with LIMIT/OFFSET + whitelist sort)
- [x] **GREEN**: Add `unsubscribeById()`, `resubscribeById()`, `deleteById()` to `NewsletterSubscriberService`
- [x] **GREEN**: Replace `listSubscribers()` in `NewsletterController` with paginated version using `PaginationUtils`
- [x] **GREEN**: Add 3 new action endpoints to `NewsletterController`
- [x] **REFACTOR**: Ensure all tests pass, checkstyle clean

### Phase 3: Frontend — TDD Red-Green-Refactor (AC1, AC6–AC10)
- [x] **RED**: Write failing store tests in `newsletterSubscriberStore.test.ts` (14 tests)
- [x] **RED**: Write failing component tests (35 tests across 5 files)
- [x] **GREEN**: Create `newsletterSubscriberStore.ts`
- [x] **GREEN**: Create `services/api/newsletterApi.ts` with 4 functions
- [x] **GREEN**: Create `hooks/useNewsletterSubscribers/` (list hook + mutations hook + index.ts)
- [x] **GREEN**: Create `components/organizer/NewsletterSubscribers/` (all 8 files)
- [x] **GREEN**: Add nav item to `navigationConfig.ts` + add route
- [x] **GREEN**: Add i18n keys to `en.json` and `de.json` (+ all 10 locales registered in i18n config)
- [x] **REFACTOR**: All 49 tests green, ESLint clean, type-check passes

### Phase 4: Tech debt — UserTable backend sort
- [x] Add `sortBy`/`sortDir` to `userManagementStore.ts` + `setSort` action
- [x] Pass sort params from `useUserList` → `listUsers` API call
- [x] Add `sortBy`/`sortDir` query params to `GET /api/v1/users` backend with whitelist
- [x] Update `UserTable.tsx`: remove in-memory sort; accept `sortBy`/`sortDir` + `onSortChange` props

---

## Dev Notes

### CRITICAL: What already exists (do NOT reinvent)

| What | Location | Notes |
|------|----------|-------|
| Newsletter controller | `controller/NewsletterController.java` | **Extend** — do not replace |
| Subscriber service | `service/NewsletterSubscriberService.java` | **Extend** — existing methods unchanged |
| Subscriber repository | `repository/NewsletterSubscriberRepository.java` | **Extend** — has `findByUnsubscribedAtIsNull(Pageable)` already |
| Subscriber DTO | `dto/SubscriberResponse.java` | **Add** `unsubscribedAt` field only |
| DB table | V67 migration | **No migration needed** — table already has `unsubscribed_at` column |
| Newsletter HTTP client (frontend) | `services/newsletterService.ts` | **Keep** — public/user endpoints stay here; new organizer functions go in `services/api/newsletterApi.ts` |
| `UserPagination.tsx` | `components/organizer/UserManagement/UserPagination.tsx` | **Reuse directly** — already generic |

### Backend: shared-kernel pagination classes

```
ch.batbern.shared.api.PaginationUtils   → parseParams(Integer page, Integer limit)
ch.batbern.shared.api.PaginationParams  → getPage(), getLimit(), getOffset()
ch.batbern.shared.api.PaginationMetadata → page(1-based), limit, totalItems, totalPages, hasNext, hasPrev
ch.batbern.shared.dto.PaginatedResponse<T> → data: List<T>, pagination: PaginationMetadata
```

### Backend: controller method signature

```java
@GetMapping("/newsletter/subscribers")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<PaginatedResponse<SubscriberResponse>> listSubscribers(
        @RequestParam(required = false) Integer page,
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) String search,
        @RequestParam(required = false, defaultValue = "all") String status,
        @RequestParam(required = false, defaultValue = "subscribedAt") String sortBy,
        @RequestParam(required = false, defaultValue = "desc") String sortDir) {

    PaginationParams params = PaginationUtils.parseParams(page, limit);
    List<SubscriberResponse> data = subscriberService
        .findSubscribers(search, status, sortBy, sortDir, params)
        .stream().map(subscriberService::toResponse).toList();
    long total = subscriberService.countSubscribers(search, status);
    PaginationMetadata meta = PaginationUtils.generateMetadata(params, total);
    return ResponseEntity.ok(new PaginatedResponse<>(data, meta));
}
```

### Backend: sortBy whitelist + JPQL query

```java
private static final Set<String> ALLOWED_SORT_FIELDS =
    Set.of("email", "firstName", "subscribedAt", "unsubscribedAt", "source", "language");

// In service:
String safeSortBy = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "subscribedAt";
Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;

// Convert PaginationParams offset → Spring Data page index for the LIMIT/OFFSET call
int springPage = params.getOffset() / params.getLimit();
Pageable pageable = PageRequest.of(springPage, params.getLimit(), Sort.by(direction, safeSortBy));
```

Repository JPQL (dynamic ORDER BY comes from Spring Data Sort):
```java
@Query("""
    SELECT s FROM NewsletterSubscriber s
    WHERE (:search IS NULL
           OR LOWER(s.email) LIKE :searchLike
           OR LOWER(s.firstName) LIKE :searchLike)
      AND (:status = 'all'
           OR (:status = 'active'       AND s.unsubscribedAt IS NULL)
           OR (:status = 'unsubscribed' AND s.unsubscribedAt IS NOT NULL))
    """)
List<NewsletterSubscriber> findFiltered(
    @Param("search") String search,
    @Param("searchLike") String searchLike,
    @Param("status") String status,
    Pageable pageable);

@Query("""
    SELECT COUNT(s) FROM NewsletterSubscriber s
    WHERE (:search IS NULL
           OR LOWER(s.email) LIKE :searchLike
           OR LOWER(s.firstName) LIKE :searchLike)
      AND (:status = 'all'
           OR (:status = 'active'       AND s.unsubscribedAt IS NULL)
           OR (:status = 'unsubscribed' AND s.unsubscribedAt IS NOT NULL))
    """)
long countFiltered(
    @Param("search") String search,
    @Param("searchLike") String searchLike,
    @Param("status") String status);
```

Call pattern in service (pass `"%" + search.toLowerCase() + "%"` as `searchLike`, or `null`/`"%"` when search is blank):
```java
String searchParam = (search != null && !search.isBlank()) ? search.toLowerCase() : null;
String searchLikeParam = (searchParam != null) ? "%" + searchParam + "%" : "%";
```

### Backend: action endpoint signatures

```java
@PostMapping("/newsletter/subscribers/{id}/unsubscribe")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<SubscriberResponse> unsubscribeSubscriber(@PathVariable UUID id) { ... }

@PostMapping("/newsletter/subscribers/{id}/resubscribe")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<SubscriberResponse> resubscribeSubscriber(@PathVariable UUID id) { ... }

@DeleteMapping("/newsletter/subscribers/{id}")
@PreAuthorize("hasRole('ORGANIZER')")
public ResponseEntity<Void> deleteSubscriber(@PathVariable UUID id) { ... }
```

Throw `EntityNotFoundException` (404) and `DuplicateSubscriberException` (409) — already handled by global exception handler.

### Frontend: API service pattern

```typescript
// services/api/newsletterApi.ts
import apiClient from './apiClient';

const NEWSLETTER_API_PATH = '/newsletter';

export const listNewsletterSubscribers = async (
  filters: NewsletterSubscriberFilters,
  pagination: { page: number; limit: number }
): Promise<PagedNewsletterSubscribersResponse> => {
  const params: Record<string, string | number> = {
    page: pagination.page,
    limit: pagination.limit,
  };
  if (filters.searchQuery?.trim()) params.search = filters.searchQuery;
  if (filters.status && filters.status !== 'all') params.status = filters.status;
  if (filters.sortBy) params.sortBy = filters.sortBy;
  if (filters.sortDir) params.sortDir = filters.sortDir;

  const response = await apiClient.get(`${NEWSLETTER_API_PATH}/subscribers`, { params });
  return response.data;
};
```

### Frontend: backend-driven sort in table

```typescript
// Props interface
interface NewsletterSubscriberTableProps {
  subscribers: SubscriberResponse[];
  sortBy: string;
  sortDir: 'asc' | 'desc';
  onSortChange: (field: string, dir: 'asc' | 'desc') => void;
  onAction: (action: 'unsubscribe' | 'resubscribe' | 'delete', subscriber: SubscriberResponse) => void;
}

// Toggle direction on active column; reset to 'asc' for new column
const handleSort = (field: string) => {
  if (field === sortBy) {
    onSortChange(field, sortDir === 'asc' ? 'desc' : 'asc');
  } else {
    onSortChange(field, 'asc');
  }
};

// NO sortedSubscribers = [...subscribers].sort(...) — render array as-is
```

### Frontend: dialog pattern (mirror `DeleteUserDialog.tsx`)

```typescript
// All 3 dialogs follow this structure:
<Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
  <DialogTitle>
    <Box display="flex" justifyContent="space-between" alignItems="center">
      <Box display="flex" alignItems="center" gap={1}>
        <WarningIcon color="warning" />  {/* or error for delete */}
        <Typography>{t('dialogs.unsubscribe.title')}</Typography>
      </Box>
      <IconButton onClick={onClose} aria-label={t('common:actions.close')}>
        <CloseIcon />
      </IconButton>
    </Box>
  </DialogTitle>
  <DialogContent dividers>
    <Typography>{t('dialogs.unsubscribe.message', { email: subscriber?.email })}</Typography>
    {mutation.isError && <Alert severity="error" sx={{ mt: 2 }}>{t('common:errors.unexpected')}</Alert>}
  </DialogContent>
  <DialogActions>
    <Button onClick={onClose} variant="outlined">{t('common:actions.cancel')}</Button>
    <Button onClick={handleConfirm} variant="contained" color="error" disabled={mutation.isPending}>
      {mutation.isPending ? <CircularProgress size={20} /> : t('actions.unsubscribe')}
    </Button>
  </DialogActions>
</Dialog>
```

### Frontend: `UserPagination` i18n key

`UserPagination.tsx` uses key `pagination.itemsPerPage` from the **`userManagement`** namespace. To reuse it cleanly, add `pagination.itemsPerPage` to the `newsletterSubscribers` namespace too and pass the namespace to `useTranslation`:
```typescript
// In UserPagination, add optional namespace prop, or just duplicate the key — simplest approach
```

### Architecture compliance checklist

- ✅ Integration tests MUST extend `AbstractIntegrationTest` (PostgreSQL via Testcontainers, never H2)
- ✅ Test naming: `should_expectedBehavior_when_condition`
- ✅ ALL user-facing strings via `useTranslation()` — no hardcoded text
- ✅ Use service layer (`services/api/newsletterApi.ts`) — never direct `fetch`/`axios` in components
- ✅ Import types from `src/types/generated/` (after `npm run generate:api-types`) — never duplicate
- ✅ NEVER access `process.env` directly — use `config` objects
- ✅ Role check server-side (`@PreAuthorize`) AND client-side (`roles: ['organizer']` in nav)
- ✅ `data-testid` attributes on interactive elements for test targeting
- ✅ `sortBy` whitelist enforced before any DB query (injection prevention)
- ✅ `@Transactional(readOnly = true)` on read service methods

### Tech debt task: migrate UserTable to backend sorting

`UserTable.tsx` currently sorts the fetched page in-memory — this only sorts within the current page, giving misleading results when paginated.

**Changes required:**
1. `userManagementStore.ts`: add `sortBy: string` + `sortDir: 'asc' | 'desc'` to filters (defaults: `name`, `asc`); add `setSort(field, dir)` action that resets page → 1
2. `useUserList.ts`: include `sortBy`/`sortDir` in query key + pass to `listUsers()`
3. `userManagementApi.ts`: add `sortBy`/`sortDir` to `listUsers()` params
4. `GET /api/v1/users` backend: add `sortBy`/`sortDir` query params with whitelist
5. `UserTable.tsx`: remove `sortedUsers = [...users].sort(...)`, accept `sortBy`/`sortDir` + `onSortChange` props, call `store.setSort` on header click

Can be done in same PR as Story 10.28 or as a separate follow-up story.

### Note on company search

`newsletter_subscribers` has `email`, `first_name`, `username` but no company field. Search is limited to email + firstName. Company search would require a JOIN via `username → user_profiles → companies` and should be a separate story if needed.

### No Flyway migration needed

Story 10.7 (V67) already created `newsletter_subscribers` with `unsubscribed_at` column. No schema changes.

### No new email templates needed

Management UI only — no emails sent from the new endpoints.

---

## Dev Agent Record

### Agent Model Used

claude-opus-4-6

### Debug Log References

- `/tmp/backend-tests-10-28.log` — 55 tests, 1 failed (old test expects `$.totalActive` — fixed, rerun needed)
- `/tmp/user-store-red.log` — RED phase: 4 setSort tests failing as expected
- `/tmp/user-store-green.log` — GREEN phase: 31/31 store tests pass
- `/tmp/user-service-tests.log` — Backend: BUILD SUCCESSFUL, all tests pass with sort params
- `/tmp/user-frontend-tests.log` — All UserTable + store tests pass (42 tests)
- `/tmp/user-list-tests.log` — All UserList + useUserList tests pass (14 tests)

### Completion Notes List

- Phase 1: OpenAPI spec updated with paginated GET, 3 action endpoints, `unsubscribedAt` on SubscriberResponse. Types generated.
- Phase 2: Backend TDD complete — 55 tests all GREEN. Unit tests (16 new) + integration tests (10 new). Service, controller, repository all implemented.
- Phase 3: Frontend complete — 49 tests all GREEN. Store (25), components (35). All 8 components, nav config, route, i18n (en+de+8 locales).
- Bonus: Cloud badge icon on subscribers with a registered user account (`username` field). Not in AC — added for organizer visibility. i18n key: `table.registeredUser`.
- Phase 4: UserTable backend sort migration complete — store (`sortBy`/`sortDir` + `setSort`), API service, useUserList hook, backend controller+service with whitelist, UserTable refactored to remove in-memory sort. All existing tests pass (no regressions).
- Code Review (Amelia): Fixed 5 issues — H1: UserPagination now accepts `namespace` prop (decoupled from userManagement i18n), H2: dialog mutation error state reset on reopen via `mutation.reset()`, M1: removed 2 duplicate integration tests, M2: documented Cloud badge scope, M3: added missing `user-api.types.ts` to File List. L1: removed unnecessary defaultValue fallback. All 131 frontend tests pass.

### File List

- `docs/api/events-api.openapi.yml` — paginated subscriber list + 3 action endpoints + unsubscribedAt field
- `web-frontend/src/types/generated/events-api.types.ts` — regenerated from OpenAPI
- `web-frontend/src/types/generated/user-api.types.ts` — regenerated from OpenAPI (Phase 4 sort migration)
- `services/event-management-service/src/main/java/ch/batbern/events/dto/SubscriberResponse.java` — added unsubscribedAt
- `services/event-management-service/src/main/java/ch/batbern/events/service/NewsletterSubscriberService.java` — findSubscribers, countSubscribers, unsubscribeById, resubscribeById, deleteById, updated toResponse
- `services/event-management-service/src/main/java/ch/batbern/events/repository/NewsletterSubscriberRepository.java` — findFiltered, countFiltered JPQL
- `services/event-management-service/src/main/java/ch/batbern/events/controller/NewsletterController.java` — paginated list + 3 action endpoints
- `services/event-management-service/src/test/java/ch/batbern/events/service/NewsletterSubscriberServiceTest.java` — 16 new unit tests
- `services/event-management-service/src/test/java/ch/batbern/events/controller/NewsletterControllerIntegrationTest.java` — 10 new integration tests + 1 fixed
- `web-frontend/src/stores/newsletterSubscriberStore.ts` — Zustand store with filters, pagination, sort
- `web-frontend/src/services/api/newsletterApi.ts` — 4 organizer API functions
- `web-frontend/src/hooks/useNewsletterSubscribers/useNewsletterSubscriberList.ts` — React Query list hook with prefetch
- `web-frontend/src/hooks/useNewsletterSubscribers/useNewsletterSubscriberMutations.ts` — 3 mutation hooks
- `web-frontend/src/hooks/useNewsletterSubscribers/index.ts` — barrel export
- `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscribers.tsx` — route container
- `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberList.tsx` — main list container
- `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberTable.tsx` — table with backend sort
- `web-frontend/src/components/organizer/NewsletterSubscribers/NewsletterSubscriberFilters.tsx` — filters panel
- `web-frontend/src/components/organizer/NewsletterSubscribers/UnsubscribeDialog.tsx` — confirmation dialog
- `web-frontend/src/components/organizer/NewsletterSubscribers/ResubscribeDialog.tsx` — confirmation dialog
- `web-frontend/src/components/organizer/NewsletterSubscribers/DeleteSubscriberDialog.tsx` — destructive dialog
- `web-frontend/src/components/organizer/NewsletterSubscribers/index.ts` — barrel export
- `web-frontend/src/config/navigationConfig.ts` — added newsletter subscribers nav item
- `web-frontend/src/App.tsx` — added route for newsletter subscribers
- `web-frontend/public/locales/en/common.json` — added navigation.newsletterSubscribers key
- `web-frontend/public/locales/de/common.json` — added navigation.newsletterSubscribers key
- `web-frontend/public/locales/en/newsletterSubscribers.json` — full EN i18n namespace
- `web-frontend/public/locales/de/newsletterSubscribers.json` — full DE i18n namespace
- `web-frontend/src/stores/userManagementStore.ts` — added sortBy, sortDir state + setSort action
- `web-frontend/src/stores/userManagementStore.test.ts` — added 4 setSort tests
- `web-frontend/src/services/api/userManagementApi.ts` — added sortBy, sortDir params to listUsers
- `web-frontend/src/hooks/useUserManagement/useUserList.ts` — pass sortBy/sortDir to API and query key
- `web-frontend/src/components/organizer/UserManagement/UserList.tsx` — pass sort props to UserTable
- `web-frontend/src/components/organizer/UserManagement/UserTable.tsx` — removed in-memory sort, accept sortBy/sortDir/onSortChange props
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/controller/UserController.java` — added sortBy/sortDir params
- `services/company-user-management-service/src/main/java/ch/batbern/companyuser/service/UserService.java` — added sort whitelist + Sort.by with field mapping
- `docs/api/users-api.openapi.yml` — added sortBy/sortDir query params to GET /users
- `web-frontend/src/components/shared/Navigation/NavigationMenu.test.tsx` — fixed organizer nav tests (removed stale "Speakers" references, updated to match actual nav config)
- `web-frontend/src/components/shared/Navigation/AppHeader.test.tsx` — fixed organizer nav test + added missing i18n translations
- `web-frontend/src/components/shared/Navigation/MobileDrawer.test.tsx` — fixed organizer nav test
- `web-frontend/src/components/shared/Layout/ResponsiveLayout.test.tsx` — fixed desktop nav test + added missing i18n translations
- `web-frontend/src/hooks/useUserManagement/useUserManagement.test.ts` — updated listUsers mock assertion for new sort params
