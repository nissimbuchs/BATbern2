# Story 8.1: Partner Attendance Dashboard

Status: in-progress

## Story

As a **partner**,
I want to see how many of my company's employees attended each BATbern event,
so that I can justify our sponsorship internally.

## Acceptance Criteria

1. **AC1 - Attendance Table**: Dashboard displays a table with one row per event — columns: event name, date, company attendees, total attendees, percentage. Sorted by date descending.

2. **AC2 - Time Range**: Default view shows the last 5 years (~15 events). A toggle allows extending to full history (up to 20 years / ~60 events).

3. **AC3 - Cost Per Attendee**: Displays a single computed value: total partnership cost ÷ total company attendees over the selected period.

4. **AC4 - Export**: Download the attendance table as Excel (XLSX).

5. **AC5 - Data Freshness**: Data reflects current DB state. Results cached 15 minutes — no real-time requirement, no nightly job needed.

6. **AC6 - Role-Based Access**: Partners see only their own company's data. Enforced at API level.

7. **AC7 - Performance**: Page loads in <5 seconds (P95).

8. **AC8 - i18n**: All UI text in German (primary) and English (secondary).

## Prerequisites

**Story 8.0 (Partner Portal Shell) must be complete before this story.**

Story 8.0 creates:
- `PartnerPortalLayout` with the **Analytics** nav tab linking to `/partners/analytics`
- `PartnerAnalyticsPlaceholder` at `/partners/analytics` — this story deletes that file and replaces it

## Architecture Decision

**No materialized views. No nightly batch job. No local analytics storage.**

`partner-coordination-service` calls a new lightweight endpoint on `event-management-service` on demand. Results are Caffeine-cached for 15 minutes. That's the entire data pipeline.

```
Partner opens dashboard
       │
       ▼
partner-coordination-service
  PartnerAnalyticsController
       │
       ├─ HTTP GET event-management-service:
       │    /api/v1/events/attendance-summary
       │      ?companyName={name}&fromYear={year}
       │
       │  (event-management-service queries its own
       │   registrations table — no cross-DB access)
       │
       ▼
  Returns: [{eventCode, eventDate, totalAttendees, companyAttendees}]
  + cost-per-attendee computed from partners.partnership_cost
       │
       ▼
  Cached 15 min (Caffeine), returned to frontend
```

`attendee-experience-service` shell remains untouched — preserved for Epic 7.

## Tasks / Subtasks

### Task 1: New endpoint on event-management-service (AC: 1, 2, 5)

- [x] Add `GET /api/v1/events/attendance-summary` to `EventController.java`
  - Query params: `companyName` (String, required), `fromYear` (int, default = current year - 5)
  - Queries `registrations` table: `WHERE attendee_company_name = :companyName AND event_date >= :fromDate AND status = 'confirmed'`
  - Groups by event, returns list of `{eventCode, eventDate, totalAttendees, companyAttendees}`
  - Add to OpenAPI spec: `docs/api/events-api.openapi.yml`
  - Used `attendee_company_id` (stores company name string per ADR-003, confirmed via V35 migration)

- [x] Create `AttendanceSummaryDTO.java` (eventCode, eventDate, totalAttendees, companyAttendees)

- [x] Add security: endpoint accessible to PARTNER and ORGANIZER roles

- [x] Write integration test: `EventAttendanceSummaryIntegrationTest.java` — 7 tests, ALL PASS
  - Test with `fromYear` filtering
  - Test returns only confirmed registrations
  - Test role-based access (PARTNER and ORGANIZER can access, unauthenticated gets 401)

### Task 2: EventManagementClient in partner-coordination-service (AC: 1, 2, 5)

- [x] Create `EventManagementClient.java` interface + `EventManagementClientImpl.java`
  - Method: `getAttendanceSummary(String companyName, int fromYear): List<AttendanceSummaryDTO>`
  - Uses existing JWT propagation pattern (same as `UserServiceClient`, `CompanyServiceClient`)
  - `@Cacheable(value = "partnerAttendanceCache", key = "#companyName + '-' + #fromYear")` — 15-min TTL via Caffeine
  - Added `partnerAttendanceCache` to `CacheConfig.java`
  - Added `event-management-service.base-url` to `application.yml` for all profiles
  - Created `AttendanceSummaryDTO.java` in `client/dto` package

### Task 3: PartnerAnalyticsService (AC: 1, 2, 3)

- [x] Create `PartnerAnalyticsService.java`
  - `getAttendanceDashboard(String companyName, int fromYear)`:
    - Calls `EventManagementClient.getAttendanceSummary()`
    - Fetches `partners.partnership_cost` from local partners table
    - Computes `costPerAttendee = partnershipCost / sum(companyAttendees)`
    - Returns `PartnerDashboardDTO` containing the list + costPerAttendee
  - Handle edge case: zero attendees → costPerAttendee = null (display as N/A)
  - Added `V3__add_partnership_cost.sql` migration (NUMERIC(10,2), nullable)
  - Updated `Partner.java` entity with `partnershipCost` field (BigDecimal)

- [x] Create `PartnerDashboardDTO.java` (attendanceSummary: List, costPerAttendee: BigDecimal)

### Task 4: PartnerAnalyticsController (AC: 1–7)

- [ ] Create `PartnerAnalyticsController.java`
  - `GET /api/v1/partners/{companyName}/analytics/dashboard?fromYear={year}`
  - `GET /api/v1/partners/{companyName}/analytics/export` → returns XLSX file
  - `@PreAuthorize("hasRole('PARTNER') and #companyName == authentication.principal.companyName")`
  - Add to OpenAPI spec: `docs/api/partner-analytics-api.openapi.yml`

- [ ] Update `SecurityConfig.java`: add `/api/v1/partners/*/analytics/**` → PARTNER or ORGANIZER

### Task 5: Excel Export Service (AC: 4)

- [ ] Create `PartnerAttendanceExportService.java`
  - Uses Apache POI `SXSSFWorkbook`
  - Sheet columns: Event, Date, Your Attendees, Total Attendees, Percentage
  - Footer row: Totals + Cost Per Attendee
  - Returns as `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`

### Task 6: i18n Keys (AC: 8)

- [ ] Add keys to `public/locales/de/partner.json` and `en/partner.json`
  - `partner.analytics.title`, `partner.analytics.table.*` (6 column headers)
  - `partner.analytics.kpi.costPerAttendee`, `partner.analytics.kpi.attendanceRate`
  - `partner.analytics.range.last5years`, `partner.analytics.range.allHistory`
  - `partner.analytics.export.button`, `partner.analytics.noData`

### Task 7: Frontend — Wire into Partner Portal (AC: 1, 2, 3, 7, 8)

- [ ] **Delete** `src/pages/PartnerAnalyticsPlaceholder.tsx` (created by Story 8.0)
- [ ] In `App.tsx`, replace the `PartnerAnalyticsPlaceholder` import + element with `PartnerAttendanceDashboard`:
  ```tsx
  // BEFORE (Story 8.0 placeholder)
  <Route path="analytics" element={<PartnerAnalyticsPlaceholder />} />
  // AFTER (this story)
  <Route path="analytics" element={<PartnerAttendanceDashboard />} />
  ```
- [ ] `PartnerAttendanceDashboard` reads `companyName` from `useAuth()` — same pattern as `PartnerCompanyPage` (Story 8.0)

### Task 7b: Frontend — Dashboard Page (AC: 1, 2, 3, 7, 8)

- [ ] Create `src/components/partner/PartnerAttendanceDashboard.tsx`
  - Two KPI cards at top: **Overall Attendance Rate** (avg % across events) | **Cost Per Attendee** (CHF)
  - Toggle below: `[ Last 5 years ] [ All history ]` — switches `fromYear` query param
  - MUI `Table` with columns: Event, Date, Your Attendees, Total, %
  - Loading skeleton (MUI `Skeleton`) while fetching
  - Empty state: "No attendance data found for the selected period"
  - Desktop layout only — no mobile breakpoints

### Task 8: Frontend — Export Button (AC: 4)


- [ ] Create `src/components/partner/AttendanceExportButton.tsx`
  - Single button: "Export Excel"
  - On click: triggers `GET /analytics/export`, browser downloads the XLSX
  - Loading spinner while download prepares

### Task 9: Frontend — API Client (AC: ALL)

- [ ] Create `src/services/api/partnerAnalyticsApi.ts`
  - `getAttendanceDashboard(companyName, fromYear?)` — React Query, staleTime 15 min
  - `exportAttendanceReport(companyName)` — triggers file download

### Task 10: Backend Integration Tests (AC: 1, 2, 3, 6)

- [ ] `PartnerAnalyticsControllerIntegrationTest.java` (extends `AbstractIntegrationTest`)
  - PARTNER role sees own company data, gets 403 for another company's endpoint
  - ORGANIZER role can access any company's analytics
  - `fromYear=5` returns only last 5 years of events
  - `fromYear=20` returns full history
  - Cost per attendee computed correctly
  - Zero attendees → costPerAttendee is null

### Task 11: Frontend Tests (AC: 7, 8)

- [ ] `PartnerAttendanceDashboard.test.tsx`
  - Renders table with mocked data
  - 5yr/all-history toggle fires correct API call
  - Loading and empty states render correctly
  - i18n: both DE and EN strings render

### Task 12: E2E Test (AC: 1, 4, 6)

- [ ] `e2e/partner/analytics-dashboard.spec.ts`
  - Partner logs in → dashboard loads → table has correct row count
  - Export button → XLSX file downloads
  - Partner cannot access another partner's analytics URL (403)

## Dev Notes

### What was deliberately cut (SM decision 2026-02-21)

| Removed | Reason |
|---|---|
| Materialized views | Not needed — query on demand with 15-min cache |
| Nightly batch job | Not needed for this data volume (max ~60 events ever) |
| QuickSight / AWS analytics | Massively over-engineered for 3 events/year |
| Charts (Recharts) | A table is sufficient — partners need a number, not a viz |
| Comparative analysis vs other partners | Out of scope for now |
| Department breakdown | No department data tracked |
| Engagement / content interaction | Not relevant to partner value prop |
| Individual attendee tracking | Not allowed |
| Mobile responsive layout | Desktop only |

### Registrations companyName field

Verify the exact field name in `registrations` table (event-management-service):
- If `attendee_company_name` exists as a denormalized column → use directly in query
- If only `attendee_company_id` (UUID) exists → event-management-service needs to resolve `companyName → companyId` by calling company-user-management-service once at request time (cache result)

Check: `/services/event-management-service/src/main/resources/db/migration/V2__Create_events_schema.sql`

### Cost per attendee data source

`partner_coordination_service.partners.partnership_cost` — this field must exist (or be added via migration) to support AC3. If not yet present, add:
```sql
-- V8.1.1__add_partnership_cost.sql
ALTER TABLE partners ADD COLUMN partnership_cost NUMERIC(10,2);
```

### ADR Compliance

- **ADR-003**: endpoint uses `companyName` as path param (not UUID)
- **ADR-006**: OpenAPI spec created before implementation for both new endpoints
- **ADR-008**: SecurityConfig updated — backend controls access

### Performance

| Metric | Target |
|---|---|
| Dashboard load (P95) | <5s |
| API response (cached) | <50ms |
| API response (cold) | <500ms |
| Excel export | <5s |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.1]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/ADR-006-openapi-contract-first.md]
- [Source: services/event-management-service — registrations table schema]
- [Source: services/partner-coordination-service — existing HTTP client patterns]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- YAML parse error in `events-api.openapi.yml`: `description: Earliest year to include (default: current year - 5)` caused `mapping values are not allowed here` — fixed by writing "minus 5" instead of "- 5"
- Compile error `EventWorkflowState.COMPLETED` → correct value is `EventWorkflowState.EVENT_COMPLETED`
- `registrations.attendee_company_id` stores company name string (not UUID) — confirmed via V35 migration and RegistrationService code; direct string comparison with companyName parameter works correctly

### Completion Notes List

- Tasks 1–3 complete (backend event-management-service endpoint + partner-coordination-service client + analytics service)
- `partner-coordination-service` production `SecurityConfig` does NOT have `@EnableMethodSecurity` — must add before `@PreAuthorize` can work (to be done in Task 4)

### File List

**New files:**
- `services/event-management-service/src/main/java/ch/batbern/events/dto/AttendanceSummaryDTO.java`
- `services/event-management-service/src/test/java/ch/batbern/events/controller/EventAttendanceSummaryIntegrationTest.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/client/dto/AttendanceSummaryDTO.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/client/EventManagementClient.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/client/impl/EventManagementClientImpl.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/dto/PartnerDashboardDTO.java`
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/service/PartnerAnalyticsService.java`
- `services/partner-coordination-service/src/main/resources/db/migration/V3__add_partnership_cost.sql`

**Modified files:**
- `services/event-management-service/src/main/java/ch/batbern/events/controller/EventController.java` (added attendance-summary endpoint)
- `services/event-management-service/src/main/java/ch/batbern/events/repository/RegistrationRepository.java` (added findAttendanceSummary JPQL query)
- `docs/api/events-api.openapi.yml` (added /events/attendance-summary path + AttendanceSummaryDTO schema)
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/config/CacheConfig.java` (added partnerAttendanceCache)
- `services/partner-coordination-service/src/main/java/ch/batbern/partners/domain/Partner.java` (added partnershipCost field)
- `services/partner-coordination-service/src/main/resources/application.yml` (added event-management-service.base-url)
