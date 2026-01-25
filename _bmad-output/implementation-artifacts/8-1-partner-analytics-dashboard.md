# Story 8.1: Partner Analytics Dashboard

Status: ready-for-dev

## Story

As a **partner**,
I want to view comprehensive analytics about employee attendance and engagement,
so that I can demonstrate sponsorship ROI internally.

## Acceptance Criteria

1. **AC1 - Attendance Metrics**: Dashboard displays employee attendance by event (count, percentage) with real-time accuracy
2. **AC2 - Trend Analysis**: Historical attendance patterns displayed over 12/24 month periods with interactive charts
3. **AC3 - Department Breakdown**: Attendance segmented by department with visual breakdown charts
4. **AC4 - Engagement Score**: Content interaction metrics (downloads, feedback) calculated and displayed
5. **AC5 - Comparative Analysis**: Anonymized benchmark comparison vs other partners (percentile ranking)
6. **AC6 - Export Capability**: Download reports as PDF and Excel formats
7. **AC7 - Individual Tracking**: Employee-level participation tracking visible to partner admins
8. **AC8 - Content Interaction**: Topics/sessions employee engagement data displayed
9. **AC9 - ROI Calculation**: Cost per attendee and engagement value metrics computed and shown
10. **AC10 - Real-time Updates**: Data refreshes daily (overnight batch job at 2 AM)
11. **AC11 - Role-Based Access**: Partners see ONLY their own company data (enforced at API and QuickSight level)
12. **AC12 - Performance**: Dashboard loads in <3 seconds (P95)
13. **AC13 - Mobile Responsive**: Analytics accessible and usable on mobile devices
14. **AC14 - i18n Support**: All UI text translated (German primary, English secondary)

## Tasks / Subtasks

### Backend Tasks

- [ ] **Task 1: Create OpenAPI Specification** (AC: ALL - contract-first per ADR-006)
  - [ ] Create `docs/api/partner-analytics-api.openapi.yml`
  - [ ] Define `/api/v1/partners/{companyName}/analytics/attendance` endpoint
  - [ ] Define `/api/v1/partners/{companyName}/analytics/engagement` endpoint
  - [ ] Define `/api/v1/partners/{companyName}/analytics/trends` endpoint
  - [ ] Define `/api/v1/partners/{companyName}/analytics/export` endpoint
  - [ ] Generate TypeScript types: `npm run generate:api-types:partners`

- [ ] **Task 2: Database Materialized Views** (AC: 1, 2, 3, 10)
  - [ ] Create Flyway migration for `mv_partner_attendance` materialized view
  - [ ] Create Flyway migration for `mv_partner_engagement` materialized view
  - [ ] Create Flyway migration for `mv_partner_trends` materialized view
  - [ ] Create EventBridge rule for nightly refresh at 2 AM

- [ ] **Task 3: Partner Analytics Service** (AC: 1, 2, 3, 4, 5, 7, 8, 9)
  - [ ] Create `PartnerAnalyticsService.java` in partner-coordination-service
  - [ ] Implement `getAttendanceMetrics(String companyName)` method
  - [ ] Implement `getEngagementScore(String companyName)` method
  - [ ] Implement `getTrendAnalysis(String companyName, int months)` method
  - [ ] Implement `getComparativeAnalysis(String companyName)` method (anonymized)
  - [ ] Add Caffeine caching (15-minute TTL) for analytics queries

- [ ] **Task 4: HTTP Enrichment for User Data** (AC: 7, 8 - ADR-004)
  - [ ] Create `UserServiceClient.java` for cross-service user data access
  - [ ] Implement employee username lookup by companyName
  - [ ] Add caching layer for user enrichment (80-90% hit rate expected)

- [ ] **Task 5: REST Controller** (AC: 11, 12)
  - [ ] Create `PartnerAnalyticsController.java`
  - [ ] Implement all endpoints from OpenAPI spec
  - [ ] Add `@PreAuthorize("hasRole('PARTNER') and #companyName == authentication.principal.companyName")` for row-level security
  - [ ] Add request timing metrics with Micrometer

- [ ] **Task 6: Export Service** (AC: 6)
  - [ ] Create `PartnerReportExportService.java`
  - [ ] Implement PDF generation using Apache PDFBox
  - [ ] Implement Excel generation using Apache POI
  - [ ] Return presigned S3 URL for download

- [ ] **Task 7: SecurityConfig Update** (AC: 11 - ADR-008)
  - [ ] Add `/api/v1/partners/*/analytics/**` to SecurityConfig
  - [ ] Require PARTNER or ORGANIZER role for analytics endpoints

### Frontend Tasks

- [ ] **Task 8: i18n Translation Keys** (AC: 14)
  - [ ] Add ~50 translation keys to `public/locales/de/partner.json`
  - [ ] Add ~50 translation keys to `public/locales/en/partner.json`
  - [ ] Keys include: `partner.analytics.dashboard.*`, `partner.analytics.attendance.*`, etc.

- [ ] **Task 9: Analytics Dashboard Component** (AC: 1, 2, 3, 4, 12, 13)
  - [ ] Create `src/components/partner/PartnerAnalyticsDashboard.tsx`
  - [ ] Use Recharts for trend visualization (LineChart, BarChart)
  - [ ] Implement responsive grid layout with Material-UI
  - [ ] Add loading states with Skeleton components

- [ ] **Task 10: Attendance Metrics Card** (AC: 1, 3)
  - [ ] Create `src/components/partner/AttendanceMetricsCard.tsx`
  - [ ] Display employee count, percentage, department breakdown
  - [ ] Use Recharts PieChart for department visualization

- [ ] **Task 11: Engagement Score Card** (AC: 4, 8)
  - [ ] Create `src/components/partner/EngagementScoreCard.tsx`
  - [ ] Display engagement score with gauge chart
  - [ ] Show content interaction breakdown

- [ ] **Task 12: Trend Analysis Chart** (AC: 2)
  - [ ] Create `src/components/partner/TrendAnalysisChart.tsx`
  - [ ] Interactive LineChart with 12/24 month toggle
  - [ ] Tooltips showing event details

- [ ] **Task 13: Comparative Analysis Card** (AC: 5)
  - [ ] Create `src/components/partner/ComparativeAnalysisCard.tsx`
  - [ ] Display percentile ranking vs anonymized partners
  - [ ] Use BarChart for visual comparison

- [ ] **Task 14: Export Controls** (AC: 6)
  - [ ] Create `src/components/partner/AnalyticsExportMenu.tsx`
  - [ ] PDF export button with loading state
  - [ ] Excel export button with loading state
  - [ ] Download via presigned S3 URL

- [ ] **Task 15: API Client Integration** (AC: ALL)
  - [ ] Create `src/services/api/partnerAnalyticsApi.ts`
  - [ ] Implement `getAttendanceMetrics(companyName)`
  - [ ] Implement `getEngagementScore(companyName)`
  - [ ] Implement `getTrendAnalysis(companyName, months)`
  - [ ] Implement `exportReport(companyName, format)`
  - [ ] Use React Query for caching and background refresh

### Testing Tasks

- [ ] **Task 16: Backend Integration Tests** (AC: ALL)
  - [ ] Create `PartnerAnalyticsControllerIntegrationTest.java`
  - [ ] Test role-based access (partner sees own data only)
  - [ ] Test performance (<200ms API response)
  - [ ] Use PostgreSQL via Testcontainers

- [ ] **Task 17: Frontend Component Tests** (AC: 12, 13, 14)
  - [ ] Create `PartnerAnalyticsDashboard.test.tsx`
  - [ ] Test loading states and error handling
  - [ ] Test responsive behavior
  - [ ] Test i18n language switching

- [ ] **Task 18: E2E Tests** (AC: 1, 6, 11)
  - [ ] Create `e2e/partner/analytics-dashboard.spec.ts`
  - [ ] Test partner login → dashboard → view metrics
  - [ ] Test export PDF/Excel functionality
  - [ ] Test role-based access (partner cannot see other partner data)

## Dev Notes

### Architecture Compliance

**ADR-003 (Meaningful Identifiers):**
- All API endpoints use `companyName` (e.g., `/api/v1/partners/{companyName}/analytics`)
- Database stores `company_name VARCHAR(12)`, NOT UUID foreign keys
- Cross-service references use `username`, NOT `userId`

**ADR-004 (HTTP Enrichment):**
```java
// ✅ CORRECT: HTTP client for user data
@Service
public class PartnerAnalyticsService {
    private final UserServiceClient userServiceClient;

    @Cacheable(value = "partner-employees", key = "#companyName")
    public List<EmployeeEngagementDTO> getEmployeeEngagement(String companyName) {
        List<String> usernames = registrationRepository.findUsernamesByCompanyName(companyName);
        return usernames.stream()
            .map(username -> userServiceClient.getUser(username))
            .map(this::buildEngagementDTO)
            .collect(toList());
    }
}
```

**ADR-006 (OpenAPI Contract-First):**
- Create `docs/api/partner-analytics-api.openapi.yml` BEFORE implementation
- Generate TypeScript types: `npm run generate:api-types:partners`
- Generate Java DTOs via OpenAPI Generator Gradle task

**ADR-008 (Backend Controls Routing):**
```java
// SecurityConfig.java
.requestMatchers("/api/v1/partners/*/analytics/**")
    .hasAnyRole("PARTNER", "ORGANIZER")
```

### Project Structure Notes

**Backend Files:**
```
services/partner-coordination-service/src/main/java/ch/batbern/partners/
├── controller/
│   └── PartnerAnalyticsController.java
├── service/
│   ├── PartnerAnalyticsService.java
│   └── PartnerReportExportService.java
├── client/
│   └── UserServiceClient.java
├── dto/
│   ├── AttendanceMetricsDTO.java
│   ├── EngagementScoreDTO.java
│   ├── TrendAnalysisDTO.java
│   └── ComparativeAnalysisDTO.java
└── repository/
    └── PartnerAnalyticsRepository.java
```

**Frontend Files:**
```
web-frontend/src/
├── components/partner/
│   ├── PartnerAnalyticsDashboard.tsx
│   ├── AttendanceMetricsCard.tsx
│   ├── EngagementScoreCard.tsx
│   ├── TrendAnalysisChart.tsx
│   ├── ComparativeAnalysisCard.tsx
│   └── AnalyticsExportMenu.tsx
├── services/api/
│   └── partnerAnalyticsApi.ts
└── types/generated/
    └── partner-analytics-api.types.ts
```

**Database Migrations:**
```
services/partner-coordination-service/src/main/resources/db/migration/
├── V8.1.1__create_partner_analytics_views.sql
├── V8.1.2__create_partner_engagement_views.sql
└── V8.1.3__create_partner_trends_views.sql
```

### Technical Requirements

**Recharts for Data Visualization:**
```typescript
import { LineChart, Line, BarChart, Bar, PieChart, Pie, ResponsiveContainer, Tooltip } from 'recharts';

// Use ResponsiveContainer for mobile-responsive charts
<ResponsiveContainer width="100%" height={400}>
  <LineChart data={trendData}>
    <Line type="monotone" dataKey="attendance" stroke="#8884d8" />
    <Tooltip content={<CustomTooltip />} />
  </LineChart>
</ResponsiveContainer>
```

**Caffeine Caching Configuration:**
```java
@Configuration
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(15, TimeUnit.MINUTES));
        return manager;
    }
}
```

**i18n Translation Pattern:**
```typescript
const { t } = useTranslation('partner');

// Usage
<Typography>{t('analytics.dashboard.title')}</Typography>
<Typography>{t('analytics.attendance.count', { count: 42 })}</Typography>
```

### Performance Requirements

| Metric | Target | Measurement |
|--------|--------|-------------|
| API Response (P95) | <200ms | Micrometer timer |
| Dashboard Load (P95) | <3s | Web Vitals LCP |
| Database Query | <50ms | PostgreSQL EXPLAIN |
| Cache Hit Rate | >80% | Caffeine stats |

### References

- [Source: docs/prd/epic-8-partner-coordination.md#Story-8.1]
- [Source: docs/architecture/05-frontend-architecture.md#i18n]
- [Source: docs/architecture/ADR-003-meaningful-identifiers.md]
- [Source: docs/architecture/ADR-004-factor-user-fields.md]
- [Source: docs/architecture/ADR-006-openapi-contract-first.md]
- [Source: docs/architecture/coding-standards.md#TDD-Workflow]
- [Source: docs/architecture/tech-stack.md#Recharts]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

