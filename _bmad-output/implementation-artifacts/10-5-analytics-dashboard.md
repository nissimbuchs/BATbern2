# Story 10.5: Analytics Dashboard

Status: done

## Story

As an **organizer or partner**,
I want a dedicated Analytics page with rich, tabbed visualizations of BATbern event statistics,
so that during partner meetings I can showcase community growth, speaker contributions, and company engagement with live, data-driven charts — without any manual slide preparation.

## Acceptance Criteria

**AC1 — Page shell and navigation:**
- The existing stub at `/organizer/analytics` is replaced with a fully functional page
- The page has a global **time range selector** (top-right): "All Time" / "Last 5 Years" / "Last 2 Years"; default = "All Time"
- The page has 4 tabs: **Overview** (default) · **Attendance** · **Topics** · **Companies**
- All time-sensitive charts respond to the global time range selector without page reload
- Charts use the BATbern brand color palette (see Dev Notes for exact hex values)
- Chart library: **Recharts** (already installed at `^3.5.0` — do NOT install another chart library)

**AC2 — Overview tab (default):**
- 4 KPI cards displayed horizontally: Total Events · Total Attendees · Companies Represented · Total Sessions
- Values are all-time totals (unaffected by the global time range filter — they always show totals)
- Event cadence timeline: horizontal scrollable chart showing all events as colored bars on a time axis; each bar colored by topic category; tooltip shows event title, date, attendee count, category
- Timeline is not time-range filtered — always shows all events for the "wow" opener story

**AC3 — Attendance tab:**
- **Chart A — Attendees per event**: `ComposedChart` (Recharts) with bars per event + a `Line` trend line overlay; X-axis = event (chronological); Y-axis = attendee count; label toggle buttons above the chart: [Event Title] [Category] [Both] (controls the X-axis tick labels); collapsible data table below (`▼ Show data table`)
- **Chart B — Returning vs. New attendees per event**: stacked bar chart; bottom segment = returning attendees (warm color `#E67E22`); top segment = new attendees (cool color `#4A90B8`); tooltip per event shows both counts; collapsible data table below

**AC4 — Topics tab:**
- **Chart A — Events per category**: horizontal bar chart; one bar per category; sorted descending by count; category colors from BATbern palette; collapsible data table
- **Chart B — Topic popularity vs. attendee count**: scatter plot (`ScatterChart`); X-axis = number of events on this topic; Y-axis = average attendee count for events with this topic; each dot = one topic, tooltip shows topic title, category, event count, avg attendees; reveals under/over-performing topics; collapsible data table

**AC5 — Companies tab:**
- **Partner auto-highlight**: logged-in partner's company (from `user.company` JWT claim or user profile) is pinned as the first entry in all three charts and highlighted with a distinct visual treatment (e.g., bold label + accent color `#E67E22` border or fill)
- Organizers see all companies without highlight
- **Top N toggle** (above Charts A and B): [Top 5] [Top 10] [All]; default = Top 10; partner's own company always shown even if outside Top N
- **Chart A — Attendees per company over time**: stacked bar chart; X-axis = year; stacked segments = companies (Top N + own); only years within the global time range filter are shown; collapsible data table
- **Chart B — Sessions per company**: bar chart; each bar = one company (sorted descending by session count); secondary indicator = unique speaker count shown as a number label inside/above bar; collapsible data table; affected by Top N toggle
- **Chart C — Attendee distribution per company**: `PieChart`; each slice = one company's share of attendees; a per-event filter dropdown above the chart lets the user scope to a single event; when no event is selected, shows all-time distribution (respect global time range); own company slice highlighted with accent color

**AC6 — Backend analytics API (event-management-service):**
- New `AnalyticsController` at `/api/v1/analytics/`
- **`GET /api/v1/analytics/overview`** — returns KPI totals + event timeline data; no time range param (always all-time)
- **`GET /api/v1/analytics/attendance?fromYear={year}`** — returns per-event attendance with returning/new breakdown; `fromYear` optional (no param = all time)
- **`GET /api/v1/analytics/topics?fromYear={year}`** — returns events-per-category counts + topic scatter data
- **`GET /api/v1/analytics/companies?fromYear={year}`** — returns attendance-over-time by company + sessions per company + overall distribution; all time-filtered
- **`GET /api/v1/analytics/companies/distribution?eventCode={code}`** — returns attendee company distribution for a single event (for the pie chart per-event filter)
- All endpoints: accessible to ORGANIZER and PARTNER roles; no individual user data returned (aggregates only); `status IN ('confirmed', 'attended')` filter on registrations

**AC7 — OpenAPI spec first (ADR-006):**
- All 5 analytics endpoints must be added to `docs/api/events.openapi.yml` BEFORE any backend implementation
- Response DTOs derived from spec (via OpenAPI Generator if configured, or manual match)

**AC8 — Empty states:**
- Each chart has a graceful "No data available for this period" empty state (MUI `Box` + icon + text) when the dataset is empty for the selected time range
- No broken/empty Recharts renders

**AC9 — Collapsible data table:**
- Each chart widget includes a `▼ Show data table` / `▲ Hide data table` toggle (MUI `Collapse`)
- Table columns match the chart axes/dimensions
- Table data is sortable by clicking column headers (use MUI `Table` with sort state)

**AC10 — i18n:**
- All visible strings use i18n keys in `public/locales/en/organizer.json` and `public/locales/de/organizer.json`
- Key namespace: `analytics.*` (e.g., `analytics.tabs.overview`, `analytics.kpi.totalEvents`, `analytics.charts.attendeesPerEvent`, etc.)

## Tasks / Subtasks

- [x] Task 1 — OpenAPI spec (AC7)
  - [x] Add 5 analytics endpoints to `docs/api/events-api.openapi.yml`
  - [x] Define response schemas: `AnalyticsOverviewResponse`, `AnalyticsAttendanceResponse`, `AnalyticsTopicsResponse`, `AnalyticsCompaniesResponse`, `CompanyDistributionResponse` + 4 item schemas
  - [x] Commit spec before any implementation begins

- [x] Task 2 — Backend: Analytics repository queries (AC6)
  - [x] Add `AnalyticsRepository` interface with native/JPQL queries
  - [x] Overview query: total events, total registrations (confirmed/attended), distinct companies, total session_users count
  - [x] Timeline query: all events with date, eventNumber, title, category (join Event→Topic), attendee count
  - [x] Attendance query: per-event totals + all-attendances-for-returning/new algorithm
  - [x] Topics query: GROUP BY category for event count; native SQL scatter data (topic → events → avg attendees)
  - [x] Companies query: attendance by year+company (native SQL); sessions per company (native SQL: session_users → user_profiles ARCH BREAK); distribution (GROUP BY attendeeCompanyId)
  - [x] Distribution per-event query: GROUP BY attendeeCompanyId WHERE eventCode = :code

- [x] Task 3 — Backend: Service + Controller + DTOs (AC6)
  - [x] `AnalyticsService.java` — orchestrates repository calls, returning/new algorithm
  - [x] `AnalyticsController.java` — 5 GET endpoints; `@PreAuthorize` ORGANIZER or PARTNER
  - [x] DTOs: generated from OpenAPI spec via OpenAPI Generator (`ch.batbern.events.dto.generated`)
  - [x] Integration tests (TDD — tests written first): `AnalyticsControllerIntegrationTest extends AbstractIntegrationTest`
  - [x] All 18 integration tests passing — fixed via: V100 user_profiles stub migration, JdbcTemplate setUp() inserts, hasItem() matcher fix

- [x] Task 4 — Frontend: Service layer (AC1)
  - [x] `web-frontend/src/services/analyticsService.ts` — 5 typed async functions wrapping `apiClient`
  - [x] TypeScript types re-exported from generated events-api.types.ts

- [x] Task 5 — Frontend: Hooks (AC1)
  - [x] `web-frontend/src/hooks/useAnalytics.ts` — 5 hooks using React Query

- [x] Task 6 — Frontend: Shared chart components (AC8, AC9)
  - [x] `ChartCard.tsx`, `DataTable.tsx`, `EmptyChartState.tsx`, `CHART_COLORS.ts`

- [x] Task 7 — Frontend: Overview tab (AC2)
  - [x] `KpiCard.tsx`, `EventCadenceTimeline.tsx`, `OverviewTab.tsx`

- [x] Task 8 — Frontend: Attendance tab (AC3)
  - [x] `AttendeesPerEventChart.tsx`, `ReturningVsNewChart.tsx`, `AttendanceTab.tsx`

- [x] Task 9 — Frontend: Topics tab (AC4)
  - [x] `EventsPerCategoryChart.tsx`, `TopicScatterChart.tsx`, `TopicsTab.tsx`

- [x] Task 10 — Frontend: Companies tab (AC5)
  - [x] `CompanyAttendanceOverTimeChart.tsx`, `SessionsPerCompanyChart.tsx`
  - [x] `CompanyDistributionPieChart.tsx`, `CompaniesTab.tsx`

- [x] Task 11 — Frontend: Page assembly + i18n (AC1, AC10)
  - [x] Replace stub `OrganizerAnalyticsPage.tsx` with full tab layout
  - [x] Global time range selector (top-right, MUI `ToggleButtonGroup`)
  - [x] i18n keys in `en/organizer.json` + `de/organizer.json` under `analytics.*` namespace

- [x] Task 12 — Tests
  - [x] Backend: `AnalyticsControllerIntegrationTest` — 18/18 passing
  - [x] Frontend: unit tests for `ChartCard` (10 tests), `EmptyChartState` (3 tests), `DataTable` (6 tests) — 19/19 passing

## Dev Notes

### Architecture — Where Things Live

- **Backend controller**: `services/event-management-service/src/main/java/ch/batbern/events/controller/AnalyticsController.java`
- **Service**: `services/event-management-service/src/main/java/ch/batbern/events/service/AnalyticsService.java`
- **Repository**: `services/event-management-service/src/main/java/ch/batbern/events/repository/AnalyticsRepository.java` (new) — or extend `RegistrationRepository`/`EventRepository`
- **Frontend page**: `web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx` ← REPLACE the stub (do NOT create a new file)
- **Frontend components**: `web-frontend/src/components/organizer/Analytics/` (new folder)
- **Service**: `web-frontend/src/services/analyticsService.ts`
- **Hooks**: `web-frontend/src/hooks/useAnalytics.ts`

### Route Already Exists — Do NOT Modify App.tsx

```tsx
// App.tsx line ~561 — already wired:
path="/organizer/analytics"
element={<OrganizerAnalyticsPage />}
```

The stub page at `OrganizerAnalyticsPage.tsx` shows a "coming soon" placeholder. Replace its entire content. Do NOT add a new route.

### BATbern Brand Color Palette for Charts

Source: `web-frontend/src/theme/theme.ts` (`swissColors`)

```typescript
// Use these constants in a shared CHART_COLORS file:
export const CHART_COLORS = {
  primary:    '#2C5F7C',  // organizer blue — main bars, primary series
  light:      '#4A90B8',  // new attendees, secondary series
  dark:       '#1A3A4D',  // trend lines, borders
  partner:    '#E67E22',  // partner accent — returning attendees, own-company highlight
  success:    '#27AE60',  // positive metrics
  error:      '#E74C3C',  // warnings, out-of-bounds
  info:       '#3498DB',  // info/attendee
  purple:     '#9B59B6',  // additional series
  grey:       '#95A5A6',  // disabled/secondary
};

// Category color mapping (align with blob selector clusters from Story 10.4):
export const CATEGORY_COLORS: Record<string, string> = {
  ARCHITECTURE:   '#2C5F7C',
  SECURITY:       '#E74C3C',
  DATA:           '#27AE60',
  CLOUD_INFRA:    '#4A90B8',
  AI_ML:          '#9B59B6',
  MOBILE:         '#E67E22',
  BUSINESS_OTHER: '#95A5A6',
  // Fallback for unknown categories:
  DEFAULT:        '#3498DB',
};
```

### Recharts 3.5.0 — Already Installed

**Do NOT run `npm install recharts`** — it is already in `package.json` at `^3.5.0`.

Key Recharts patterns to use:
```tsx
import {
  ComposedChart, BarChart, Bar, Line, ScatterChart, Scatter,
  PieChart, Pie, Cell, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, ReferenceLine, LabelList
} from 'recharts';

// Always wrap in ResponsiveContainer for full-width responsiveness:
<ResponsiveContainer width="100%" height={350}>
  <BarChart data={data}>...</BarChart>
</ResponsiveContainer>

// Stacked bars: use stackId="stack" on each Bar
<Bar dataKey="returning" stackId="a" fill={CHART_COLORS.partner} />
<Bar dataKey="new"       stackId="a" fill={CHART_COLORS.light} />
```

### Data Model — Key Facts for Queries

**Registration entity** (`registrations` table):
- `attendee_username` — links to user (string, external reference)
- `attendee_company_id` — **denormalized company name** (indexed: `idx_attendee_company_id`)
- `status` — use `IN ('confirmed', 'attended')` for all analytics queries
- `event_id` — UUID FK to events

**SessionUser entity** (`session_users` table):
- `username` — the speaker's username (String); no company field
- `speakerRole` — enum: `PRIMARY_SPEAKER`, `CO_SPEAKER`, `MODERATOR`, `PANELIST`
- `isConfirmed` — boolean; a speaker in `session_users` IS an accepted speaker — no workflow state check needed
- `session_id` — FK to `sessions` table

**Session entity** (`sessions` table):
- `event_id` — UUID FK to events
- `event_code` — String (e.g., `"BATbern57"`)
- `speaker_pool_id` — UUID FK to speaker_pool (not needed for analytics)

**⚠️ Important — getting company from a speaker username:**
`session_users` has NO company field. Company must be resolved via `user_profiles.company_id` using a native SQL join — this is the same **"INTENTIONAL ARCHITECTURE BREAK"** pattern already established in `SessionUserRepository.findUserPortraitsByUsernames()`.

Sessions-per-company query (native SQL):
```sql
SELECT up.company_id AS companyName,
       COUNT(su.id) AS sessionCount,
       COUNT(DISTINCT su.username) AS uniqueSpeakers
FROM session_users su
JOIN sessions s ON su.session_id = s.id
JOIN user_profiles up ON su.username = up.username
JOIN events e ON s.event_id = e.id
WHERE (:fromDate IS NULL OR e.event_date >= :fromDate)
GROUP BY up.company_id
ORDER BY sessionCount DESC
```

Note: `user_profiles` belongs to the company-user-management-service but both services share the same PostgreSQL database. This is a documented and accepted pattern in this codebase. Do NOT add a new HTTP call to company-user-management-service to resolve companies.

**Event entity** (`events` table):
- `topic_code` — FK to topics (String, nullable — some events may not have a topic)
- `event_date` — Instant (stored as UTC timestamp)
- `event_number` — Integer (chronological event number, e.g., 57)
- `title` — String

**Topic entity** (`topics` table):
- `topic_code` — PK (String)
- `category` — String (e.g., `ARCHITECTURE`, `SECURITY`, `DATA`, etc.)
- `title` — human-readable topic title

**JOIN pattern** for event→category:
```java
// JPQL:
SELECT e.eventCode, e.title, e.date, e.eventNumber, t.category
FROM Event e LEFT JOIN Topic t ON e.topicCode = t.topicCode
```
Note: Use `LEFT JOIN` because some events may not have a topic assigned. Null category → treat as `"OTHER"` in frontend.

**fromYear parameter** maps to: `e.date >= :fromDate` where `fromDate = LocalDate.of(fromYear, 1, 1).atStartOfDay(ZoneId.of("Europe/Zurich")).toInstant()`

### Returning vs. New Attendees Algorithm

**Do NOT use a correlated subquery** (performance risk). Instead, load all event registrations and compute in Java:

```java
// In AnalyticsService:
List<Object[]> rows = registrationRepository.findAllEventAttendances();
// Query: SELECT r.attendeeUsername, e.id, e.date FROM Registration r JOIN Event e ON r.eventId = e.id
//        WHERE r.status IN ('confirmed','attended') ORDER BY e.date ASC

// Group by username → sort their events chronologically → first event = "new", rest = "returning"
Map<String, LocalDate> firstEventByUser = new HashMap<>();
Map<UUID, AttendanceBreakdown> breakdown = new LinkedHashMap<>();

for (Object[] row : rows) {
    String username = (String) row[0];
    UUID eventId = (UUID) row[1];
    Instant date = (Instant) row[2];
    LocalDate eventDate = date.atZone(ZoneId.of("Europe/Zurich")).toLocalDate();

    breakdown.computeIfAbsent(eventId, id -> new AttendanceBreakdown()).total++;

    if (!firstEventByUser.containsKey(username)) {
        firstEventByUser.put(username, eventDate);
        breakdown.get(eventId).newCount++;
    } else {
        breakdown.get(eventId).returningCount++;
    }
}
```

This loads at most ~3,000 rows (58 events × ~50 attendees) — entirely acceptable. Do not over-engineer with complex SQL.

### Pattern: Existing Attendance Query (Reference)

The `RegistrationRepository.findAttendanceSummary()` query (Story 8.1) is a good reference for the JOIN + GROUP BY pattern:

```java
@Query("""
    SELECT new ch.batbern.events.dto.AttendanceSummaryDTO(
        e.eventCode, e.date,
        COUNT(r.id),
        SUM(CASE WHEN r.attendeeCompanyId = :companyId THEN 1L ELSE 0L END)
    )
    FROM Event e
    LEFT JOIN Registration r ON r.eventId = e.id AND r.status IN ('confirmed', 'attended')
    WHERE e.date >= :fromDate
    GROUP BY e.id, e.eventCode, e.date
    ORDER BY e.date DESC
    """)
```

Follow this DTO-projection pattern for all analytics queries.

### Loading States — Use BATbernLoader

When React Query `isLoading` is true, render `BATbernLoader` (the BATbern rotating-arrows spinner) — **not** MUI `CircularProgress` or any other spinner.

```tsx
import { BATbernLoader } from '@/components/shared/BATbernLoader';

// Inside ChartCard (or each chart component):
if (isLoading) {
  return (
    <Box display="flex" justifyContent="center" alignItems="center" height={350}>
      <BATbernLoader size={48} speed="normal" />
    </Box>
  );
}
```

`BATbernLoader` props: `size` (px, default 40), `speed` ('slow' | 'normal' | 'fast', default 'normal'), `className`, `data-testid`.

### Pie Chart — Which Endpoint to Call

The attendee distribution pie chart (Companies tab, Chart C) has two modes — use different endpoints for each:

```typescript
// In CompanyDistributionPieChart.tsx:
// - No event selected (default) → use the `distribution` array from the companies endpoint
//   GET /api/v1/analytics/companies?fromYear={year}  → response.distribution
//
// - Event selected via dropdown → call the dedicated per-event endpoint
//   GET /api/v1/analytics/companies/distribution?eventCode={code}

const { selectedEventCode } = ...; // local state from the filter dropdown

const { data } = selectedEventCode
  ? useCompanyDistribution(selectedEventCode)          // dedicated per-event hook
  : useAnalyticsCompanies(fromYear);                   // reuse the companies hook, read .distribution
```

Do NOT always call `companies/distribution` — that endpoint requires an `eventCode` and has no `fromYear` support.

### Frontend Service Layer Pattern

**ALWAYS** use the service layer — never call `apiClient` directly from components.

```typescript
// web-frontend/src/services/analyticsService.ts
import apiClient from '@/services/api/apiClient';

export const getAnalyticsOverview = async (): Promise<AnalyticsOverviewResponse> => {
  const response = await apiClient.get<AnalyticsOverviewResponse>('/analytics/overview');
  return response.data;
};

export const getAnalyticsAttendance = async (fromYear?: number): Promise<AnalyticsAttendanceResponse> => {
  const params = fromYear ? { fromYear } : {};
  const response = await apiClient.get<AnalyticsAttendanceResponse>('/analytics/attendance', { params });
  return response.data;
};
// ... similarly for topics, companies, distribution
```

### Global Time Range → fromYear Mapping

```typescript
// In OrganizerAnalyticsPage.tsx:
type TimeRange = 'ALL' | '5Y' | '2Y';

const fromYearFromRange = (range: TimeRange): number | undefined => {
  const currentYear = new Date().getFullYear();
  if (range === '5Y') return currentYear - 5;
  if (range === '2Y') return currentYear - 2;
  return undefined; // ALL TIME → no fromYear param
};
```

### Partner Company Auto-Highlight

The logged-in user's company is available from the auth context. Check how `PartnerCompanyPage` or the partner portal accesses it. The JWT contains company info or it's fetched via user profile.

```typescript
// In CompaniesTab.tsx:
const { user } = useAuth(); // existing auth hook
// IR-fix: field is `companyName` not `company` — confirmed from PartnerCompanyPage.tsx
const partnerCompany = user?.role === 'PARTNER' ? user.companyName : null;

// Pin own company: sort data so partnerCompany is always first
const sortedData = useMemo(() => {
  if (!partnerCompany) return data;
  return [
    data.find(d => d.companyName === partnerCompany),
    ...data.filter(d => d.companyName !== partnerCompany)
  ].filter(Boolean);
}, [data, partnerCompany]);
```

### Security — Controller Authorization

```java
@RestController
@RequestMapping("/api/v1/analytics")
@PreAuthorize("hasAnyRole('ORGANIZER', 'PARTNER')")
public class AnalyticsController {
    // All endpoints under this controller are accessible to both roles
    // No individual attendee data returned — only aggregates
}
```

### Integration Tests

All backend tests extend `AbstractIntegrationTest` (Testcontainers PostgreSQL):

```java
@Transactional
class AnalyticsControllerIntegrationTest extends AbstractIntegrationTest {
    @Test
    void should_returnOverviewStats_when_eventsExist() { ... }

    @Test
    void should_returnEmptyAttendance_when_noEventsInTimeRange() { ... }
    // TDD: write these tests BEFORE implementation
}
```

### i18n Key Structure

Add to `public/locales/en/organizer.json` under `"analytics"`:
```json
{
  "analytics": {
    "title": "Analytics",
    "timeRange": {
      "all": "All Time",
      "5y": "Last 5 Years",
      "2y": "Last 2 Years"
    },
    "tabs": {
      "overview": "Overview",
      "attendance": "Attendance",
      "topics": "Topics",
      "companies": "Companies"
    },
    "kpi": {
      "totalEvents": "Total Events",
      "totalAttendees": "Total Attendees",
      "companiesRepresented": "Companies",
      "totalSessions": "Speaker Sessions"
    },
    "charts": {
      "eventCadenceTimeline": "Event History",
      "attendeesPerEvent": "Attendees per Event",
      "returningVsNew": "Returning vs. New Attendees",
      "eventsPerCategory": "Events per Category",
      "topicPopularity": "Topic Popularity",
      "attendanceOverTime": "Attendance by Company over Time",
      "sessionsPerCompany": "Sessions per Company",
      "attendeeDistribution": "Attendee Distribution by Company"
    },
    "labels": {
      "returning": "Returning",
      "new": "New",
      "showDataTable": "Show data table",
      "hideDataTable": "Hide data table",
      "noData": "No data available for this period",
      "topN": {
        "top5": "Top 5",
        "top10": "Top 10",
        "all": "All"
      },
      "labelToggle": {
        "title": "Title",
        "category": "Category",
        "both": "Both"
      },
      "filterByEvent": "Filter by event",
      "allEvents": "All events",
      "sessions": "sessions",
      "speakers": "speakers",
      "yourCompany": "Your Company"
    }
  }
}
```

Mirror keys with German translations in `de/organizer.json`.

### Project Structure Notes

**New folder** (create if missing):
```
web-frontend/src/components/organizer/Analytics/
  ChartCard.tsx
  DataTable.tsx
  EmptyChartState.tsx
  KpiCard.tsx
  CHART_COLORS.ts
  OverviewTab.tsx
  EventCadenceTimeline.tsx
  AttendanceTab.tsx
  AttendeesPerEventChart.tsx
  ReturningVsNewChart.tsx
  TopicsTab.tsx
  EventsPerCategoryChart.tsx
  TopicScatterChart.tsx
  CompaniesTab.tsx
  CompanyAttendanceOverTimeChart.tsx
  SessionsPerCompanyChart.tsx
  CompanyDistributionPieChart.tsx
```

**Do NOT touch** these files (not in scope):
- `PartnerCompanyPage.tsx` and `PartnerAttendanceDashboard` — that's the partner-scoped view (Story 8.1), separate from this org-wide analytics
- `partnerAnalyticsApi.ts` — Story 8.1 API, leave unchanged
- Any blob topic selector files (Story 10.4)

### References

- [Source: web-frontend/src/theme/theme.ts] — BATbern color palette
- [Source: web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx] — stub to replace
- [Source: web-frontend/src/services/api/partnerAnalyticsApi.ts] — apiClient pattern to follow
- [Source: services/event-management-service/.../repository/RegistrationRepository.java#findAttendanceSummary] — DTO projection query pattern
- [Source: services/event-management-service/.../domain/Registration.java] — `attendeeCompanyId` field
- [Source: services/event-management-service/.../domain/SessionUser.java] — `username` field (no company — resolved via user_profiles join)
- [Source: services/event-management-service/.../domain/Session.java] — `eventCode`, `eventId` FK
- [Source: services/event-management-service/.../repository/SessionUserRepository.java#findUserPortraitsByUsernames] — native SQL join to `user_profiles` pattern ("INTENTIONAL ARCHITECTURE BREAK")
- [Source: services/event-management-service/.../domain/Topic.java] — `category` field
- [Source: docs/prd/epic-10-additional-stories.md] — epic context
- [Source: _bmad-output/brainstorming/brainstorming-session-2026-02-25.md] — full design decisions

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

**Session 1 (2026-02-25) — Backend in progress:**
- Task 1 ✅: 5 endpoints + 9 schemas added to `docs/api/events-api.openapi.yml`
- Task 2 ✅: `AnalyticsRepository.java` with all JPQL + native SQL queries. Key issue fixed: PostgreSQL cannot infer NULL parameter type for `:fromDate IS NULL OR e.date >= :fromDate` — solution: always pass non-null Instant (use `Instant.EPOCH` for "all-time")
- Task 3 in progress: `AnalyticsService.java`, `AnalyticsController.java` written; integration tests written; 14/18 tests still failing due to `Instant.EPOCH` fix in service not yet verified
- Categories in DB `topics` table: `technical`, `management`, `soft_skills`, `industry_trends`, `tools_platforms` (NOT ARCHITECTURE/SECURITY etc. — story's CHART_COLORS are frontend-only mappings)
- `AnalyticsController` is standalone `@RestController` (does NOT implement `AnalyticsApi` — avoids conflict with `EventController` which already handles `getAttendanceSummary` and `getEventAnalytics`)

### Completion Notes List

**Session 2 (2026-02-25) — Tasks 3-10 complete:**
- Task 3: Fixed 4 integration test failures. Root cause: `user_profiles` table missing in EMS Testcontainers. Fix: V100 test-only migration creates stub table; setUp() inserts speaker profiles via JdbcTemplate; `hasItem(greaterThan(0))` for JSONPath array assertions.
- Tasks 4-5: `analyticsService.ts` (5 service functions) + `useAnalytics.ts` (5 React Query hooks). Types re-exported from generated `events-api.types.ts`.
- Tasks 6-10: All chart components created in `web-frontend/src/components/organizer/Analytics/`. Partner highlight via `user.companyName` (confirmed field name). `ChartCard` uses `BATbernLoader` per spec. Collapsible `DataTable` with MUI `Collapse`.

**Session 3 (2026-02-25) — Tasks 11-12 complete — STORY DONE:**
- Task 11: `OrganizerAnalyticsPage.tsx` stub replaced with 4-tab layout + global time range selector (ToggleButtonGroup, top-right, hidden on Overview tab). i18n keys added under `analytics.*` in both `en/organizer.json` and `de/organizer.json`.
- Task 12: 19/19 unit tests passing — `ChartCard` (10), `EmptyChartState` (3), `DataTable` (6). Fixed MUI Collapse unmountOnExit race using `waitFor()` (known pattern from MEMORY.md).
- TypeScript fixes: removed unused `Box` imports (AttendeesPerEventChart, CompanyDistributionPieChart), fixed `UserRole` comparison to lowercase `'partner'`, fixed `ColumnDef<Row>` type mismatches by removing `as unknown as Record<string,unknown>[]` casts, used `props: any` for Recharts shape/label callbacks where types are too narrow.
- Full frontend test suite: 3650 passing, 2 pre-existing failures in PartnerDetailScreen (unrelated to analytics).

### File List

**Backend:**
- `docs/api/events-api.openapi.yml` — 5 new analytics paths + 9 new schemas
- `services/event-management-service/src/main/java/ch/batbern/events/repository/AnalyticsRepository.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/service/AnalyticsService.java` (NEW)
- `services/event-management-service/src/main/java/ch/batbern/events/controller/AnalyticsController.java` (NEW)
- `services/event-management-service/src/test/java/ch/batbern/events/controller/AnalyticsControllerIntegrationTest.java` (NEW)
- `services/event-management-service/src/test/resources/application-test.properties` (updated: added `classpath:db/testmigration`)
- `services/event-management-service/src/test/resources/db/testmigration/V100__create_user_profiles_stub.sql` (NEW)
- `web-frontend/src/types/generated/events-api.types.ts` (regenerated — analytics types added)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (updated: 10-5 → in-progress)

**Frontend (Tasks 4-10):**
- `web-frontend/src/services/analyticsService.ts` (NEW)
- `web-frontend/src/hooks/useAnalytics.ts` (NEW)
- `web-frontend/src/components/organizer/Analytics/CHART_COLORS.ts` (NEW)
- `web-frontend/src/components/organizer/Analytics/EmptyChartState.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/DataTable.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/ChartCard.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/KpiCard.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/EventCadenceTimeline.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/OverviewTab.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/AttendeesPerEventChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/ReturningVsNewChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/AttendanceTab.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/EventsPerCategoryChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/TopicScatterChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/TopicsTab.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/CompanyAttendanceOverTimeChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/SessionsPerCompanyChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/CompanyDistributionPieChart.tsx` (NEW)
- `web-frontend/src/components/organizer/Analytics/CompaniesTab.tsx` (NEW)

**Frontend (Tasks 11-12):**
- `web-frontend/src/pages/organizer/OrganizerAnalyticsPage.tsx` (REPLACED — stub → full page)
- `web-frontend/public/locales/en/organizer.json` (updated — analytics.* keys added)
- `web-frontend/public/locales/de/organizer.json` (updated — analytics.* keys added DE)
- `web-frontend/src/components/organizer/Analytics/ChartCard.test.tsx` (NEW — 10 tests)
- `web-frontend/src/components/organizer/Analytics/EmptyChartState.test.tsx` (NEW — 3 tests)
- `web-frontend/src/components/organizer/Analytics/DataTable.test.tsx` (NEW — 6 tests)
