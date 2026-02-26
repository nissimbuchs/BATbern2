# Implementation Readiness Assessment Report

**Date:** 2026-02-25
**Project:** BATbern
**Scope:** Story 10.5 — Analytics Dashboard
**Assessor:** Winston (Architect Agent)

---

## Document Inventory

| Type | File | Status |
|------|------|--------|
| PRD / Story | `docs/prd/epic-10-additional-stories.md` | ✅ Present |
| Story Detail | `_bmad-output/implementation-artifacts/10-5-analytics-dashboard.md` | ✅ Present |
| Brainstorm | `_bmad-output/brainstorming/brainstorming-session-2026-02-25.md` | ✅ Present |
| Frontend Architecture | `docs/architecture/05-frontend-architecture.md` | ✅ Present |
| Backend Architecture | `docs/architecture/06-backend-architecture.md` | ✅ Present |
| Data Architecture | `docs/architecture/03-data-architecture.md` | ✅ Present |
| UX Wireframes | None scoped to Story 10.5 | ⚠️ Missing |

---

## PRD Analysis

### Functional Requirements Extracted

| FR | Requirement |
|----|------------|
| FR1 | Replace stub at `/organizer/analytics` with 4-tab functional page (Overview / Attendance / Topics / Companies) |
| FR2 | Global time range filter (All Time / Last 5 Years / Last 2 Years); default = All Time; cascades to all time-sensitive charts |
| FR3 | Overview — 4 KPI cards (Total Events, Total Attendees, Companies Represented, Total Sessions) — always all-time values |
| FR4 | Overview — Event cadence timeline (all events as colored bars, X=date, colored by topic category) |
| FR5 | Attendance — Attendees per event: ComposedChart (bars + trend line + X-axis label toggle: Title / Category / Both) |
| FR6 | Attendance — Returning vs. New attendees: stacked bar chart per event |
| FR7 | Topics — Events per category: horizontal bar chart sorted descending |
| FR8 | Topics — Topic popularity vs. attendee count: scatter plot (X=event count, Y=avg attendees) |
| FR9 | Companies — Logged-in partner's own company pinned first and highlighted across all 3 charts |
| FR10 | Companies — Top N toggle (5 / 10 / All, default 10); own company always shown |
| FR11 | Companies — Attendees per company over time: stacked bar chart by year |
| FR12 | Companies — Sessions per company: bar chart with unique speaker count label |
| FR13 | Companies — Attendee distribution per company: pie chart with per-event filter dropdown |
| FR14 | Backend: `GET /api/v1/analytics/overview` — KPI totals + timeline data |
| FR15 | Backend: `GET /api/v1/analytics/attendance?fromYear` — per-event totals with returning/new breakdown |
| FR16 | Backend: `GET /api/v1/analytics/topics?fromYear` — events per category + topic scatter data |
| FR17 | Backend: `GET /api/v1/analytics/companies?fromYear` — attendance over time + sessions + distribution |
| FR18 | Backend: `GET /api/v1/analytics/companies/distribution?eventCode` — distribution for a single event |
| FR19 | All endpoints: ORGANIZER + PARTNER roles; aggregate data only (no individual names) |
| FR20 | OpenAPI spec committed before any backend implementation (ADR-006) |
| FR21 | Empty state per chart: "No data available for this period" |
| FR22 | Collapsible data table below each chart (MUI Collapse), sortable by column header |
| FR23 | i18n: all strings in `analytics.*` namespace in `en/` + `de/` organizer locale files |

**Total FRs: 23**

### Non-Functional Requirements Extracted

| NFR | Requirement |
|-----|------------|
| NFR1 | TDD: integration tests written before implementation; extend `AbstractIntegrationTest` (Testcontainers PostgreSQL) |
| NFR2 | Security: aggregate data only — no individual attendee data exposed at any endpoint |
| NFR3 | Performance: returning/new computation in-memory Java (max ~3,000 rows); no correlated SQL subqueries |
| NFR4 | Chart library: Recharts `^3.5.0` (already installed — no new library installs) |
| NFR5 | Color palette: BATbern brand colors from `theme.ts` (`swissColors`) — consistent across all charts |
| NFR6 | Frontend service layer: never call `apiClient` directly from components; use `analyticsService.ts` |
| NFR7 | TypeScript: strict compliance; no type errors |
| NFR8 | ADR-006: OpenAPI spec before backend implementation |
| NFR9 | ADR-003: meaningful resource IDs (`eventCode` as path/query param, not UUID) |
| NFR10 | No Flyway migration: no new DB tables required; queries against existing tables only |

**Total NFRs: 10**

---

## Epic Coverage Validation

All 23 FRs map directly to Acceptance Criteria in Story 10.5:

| FR | Coverage | AC |
|----|----------|----|
| FR1 | ✅ Covered | AC1 |
| FR2 | ✅ Covered | AC1 |
| FR3 | ✅ Covered | AC2 |
| FR4 | ✅ Covered | AC2 |
| FR5 | ✅ Covered | AC3 |
| FR6 | ✅ Covered | AC3 |
| FR7 | ✅ Covered | AC4 |
| FR8 | ✅ Covered | AC4 |
| FR9 | ✅ Covered | AC5 |
| FR10 | ✅ Covered | AC5 |
| FR11 | ✅ Covered | AC5 |
| FR12 | ✅ Covered | AC5 |
| FR13 | ✅ Covered | AC5 |
| FR14 | ✅ Covered | AC6 |
| FR15 | ✅ Covered | AC6 |
| FR16 | ✅ Covered | AC6 |
| FR17 | ✅ Covered | AC6 |
| FR18 | ✅ Covered | AC6 |
| FR19 | ✅ Covered | AC6 |
| FR20 | ✅ Covered | AC7 |
| FR21 | ✅ Covered | AC8 |
| FR22 | ✅ Covered | AC9 |
| FR23 | ✅ Covered | AC10 |

**Coverage: 23/23 = 100%**

---

## UX Alignment Assessment

### UX Document Status: Not Found (scoped to Story 10.5)

UX implied: YES — this is a fully user-facing web page with 10 charts.

**Assessment:** The absence of formal wireframes is acceptable here because the Acceptance Criteria in the story are unusually prescriptive — specifying exact chart types (ComposedChart, ScatterChart, PieChart), interaction patterns (label toggle, Top N toggle, per-event filter), visual treatments (stacked bars, partner highlight color), and layout (chart + collapsible table). The brainstorming session (`2026-02-25.md`) also documents design rationale.

**Warning:** No formal wireframes means there is no single visual source of truth. The dev agent will make layout micro-decisions independently. The risk is low given AC specificity, but worth acknowledging.

Frontend architecture explicitly shows `components/organizer/Analytics/` as a planned folder — confirming structural alignment. ✅

---

## Architecture Compliance Review

### Backend Architecture Alignment

| Check | Status | Notes |
|-------|--------|-------|
| Service layer pattern (Controller → Service → Repository) | ✅ | Story correctly specifies all three layers |
| `@PreAuthorize` method security | ✅ | `@EnableMethodSecurity(prePostEnabled = true)` already active |
| SecurityConfig explicit matcher for `/api/v1/analytics/**` | ℹ️ | Not needed — `@PreAuthorize` at controller handles it; `.anyRequest().authenticated()` fallback is safe |
| GlobalExceptionHandler coverage | ✅ | No new exception types needed; standard 404/400/500 handling applies |
| Testcontainers PostgreSQL for integration tests | ✅ | `AbstractIntegrationTest` pattern correctly specified |
| No Flyway migration required | ✅ | Confirmed — all queries use existing tables |
| ADR-003 resource IDs | ✅ | `eventCode` used as identifier, not UUIDs |
| ADR-006 OpenAPI-first | ✅ | Explicitly in AC7 and Task 1 |

### Frontend Architecture Alignment

| Check | Status | Notes |
|-------|--------|-------|
| Component folder: `components/organizer/Analytics/` | ✅ | Matches architecture doc |
| React Query hooks pattern | ✅ | Specified in `useAnalytics.ts` |
| Service layer via `analyticsService.ts` | ✅ | Correctly follows `partnerAnalyticsApi.ts` pattern |
| Recharts already installed | ✅ | `^3.5.0` in `package.json` |
| MUI components for shell | ✅ | Tabs, ToggleButtonGroup, Collapse, Table all standard |
| i18n via locale files | ✅ | Pattern matches existing organizer.json structure |

---

## Epic Quality Review

### Story Structure

| Check | Status |
|-------|--------|
| Delivers user value (not a technical milestone) | ✅ — "Analytics page for partner meetings" is clear user value |
| Independent (no forward dependencies) | ✅ — "Prerequisite: None" |
| Story appropriately sized | ⚠️ — Story is large (10 ACs, 12 task groups) but all ACs are cohesive; splitting would lose the unified UX |
| Acceptance criteria testable | ✅ — Each AC specifies exact chart types, behaviors, and constraints |
| BDD format | ⚠️ — ACs are specification-style, not Given/When/Then — acceptable for frontend-heavy stories |
| Traceability maintained | ✅ — References brainstorm + epic PRD |

---

## Gaps and Issues Found

### 🔴 Critical: None

### 🟠 Major Issues

**M1 — Wrong field name for partner company in CompaniesTab**

- **Location**: Story Dev Notes → "Partner Company Auto-Highlight" section
- **Issue**: The story says `user?.role === 'PARTNER' ? user.company : null` but the actual field on the user object is `user.companyName` (confirmed from `PartnerCompanyPage.tsx` line 15: `user?.companyName`)
- **Impact**: Dev agent will write `user.company` which is `undefined` — partner highlight will silently not work
- **Fix**: Change `user.company` → `user.companyName` in the story code snippet

**M2 — SpeakerWorkflowState enum values for session counting unspecified**

- **Location**: Story Dev Notes → "SpeakerPool entity" section
- **Issue**: Story says "check `SpeakerWorkflowState` enum for exact names". Confirmed values: `ACCEPTED` (speaker accepted invitation) and `CONFIRMED` (locked in final agenda). Story leaves this to dev agent to discover.
- **Impact**: Dev agent may guess wrong states and undercount sessions
- **Fix**: Specify explicitly: `status IN (SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.CONFIRMED)` for session counting

### 🟡 Minor Concerns

**m1 — UX: No wireframes**
- Acceptable given AC specificity, but noted for completeness.

**m2 — Top N "All" option performance concern unaddressed**
- When "All" is selected on the Companies tab and there are 100+ companies in the stacked bar chart, Recharts may render poorly (100+ legend entries, very thin bars)
- Story has no guidance on this edge case
- Recommend: Cap "All" at a practical limit (e.g., 50) or add a note in Dev Notes

**m3 — Loading states not specified**
- Story specifies empty states (no data) but not loading states (data fetching in progress)
- The React Query pattern naturally provides `isLoading` state — dev agent should show a skeleton/spinner while fetching
- Minor because the pattern is established in the codebase, but worth noting

**m4 — Pie chart data source for "all-time" mode**
- AC5 says the pie chart should "show all-time distribution (respect global time range)" when no event is selected
- AC6 shows the main `companies` endpoint returns distribution data
- The `distribution?eventCode` endpoint is for event-specific scope
- This is correctly designed but the story could be more explicit: "when no eventCode selected, use the distribution array from `GET /api/v1/analytics/companies?fromYear`; when eventCode selected, use `GET /api/v1/analytics/companies/distribution?eventCode`"

---

## Summary and Recommendations

### Overall Readiness Status: ✅ READY (with 2 story fixes before dev begins)

### Critical Issues Requiring Immediate Action

1. **Fix `user.company` → `user.companyName`** in the story Dev Notes code snippet. This is a silent runtime bug that will make partner highlight non-functional.

2. **Specify `SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.CONFIRMED`** in the story Dev Notes SpeakerPool section. Removes ambiguity for the dev agent.

### Recommended Next Steps

1. Apply the 2 story fixes above to `10-5-analytics-dashboard.md` (takes 2 minutes)
2. Proceed to `dev-story 10-5` — the story is otherwise comprehensive and implementation-ready
3. Dev agent should implement Task 1 (OpenAPI spec) first and commit before touching backend code
4. After implementation, run `bmad-bmm-code-review` before marking done

### Final Note

This assessment identified **6 issues** (0 critical, 2 major, 4 minor) across 4 categories. The 2 major issues are concrete factual errors in the story's dev notes that will cause silent bugs if not fixed. All other issues are low-risk. The story's FR coverage is 100%, architecture alignment is strong, and no blockers exist.

**The story is ready for implementation once the 2 major issues are corrected.**
