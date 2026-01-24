# TODO Cleanup Plan - Post-Epic 5 MVP

**Generated**: 2026-01-18
**Status**: Epics 1-5 Complete (MVP Ready)
**Total TODOs Found**: 81 items (27 backend, 54 frontend)

## Executive Summary

With Epics 1-5 complete and MVP ready for production, this plan categorizes all remaining TODOs by priority and provides actionable cleanup recommendations. The majority (68%) are documented future features (Epic 6+), while 32% require attention before production launch.

### Priority Breakdown
- **🔴 Critical (Pre-Production)**: 8 items - Must fix before launch
- **🟡 High (Post-Launch Optimization)**: 12 items - Address within first month
- **🟢 Medium (Future Epics)**: 43 items - Documented Epic 6+ features
- **⚪ Low (Technical Debt)**: 18 items - Clean up when convenient

---

## 🔴 Critical Priority (Pre-Production) - 8 Items

**Timeline**: Complete before production deployment

### Backend Security & Authentication

#### 1. Security Context Integration (3 items)
**Impact**: Security vulnerability - hardcoded usernames bypass authentication

| File | Line | Issue | Fix |
|------|------|-------|-----|
| SlotAssignmentController.java | 205 | Hardcoded organizer username | Extract from SecurityContext |
| SlotAssignmentController.java | 305 | Hardcoded username (clear timing) | Extract from SecurityContext |
| SlotAssignmentController.java | 329 | Hardcoded username (auto-assign) | Extract from SecurityContext |

**Action**:
```java
// Replace hardcoded "organizer1" with:
@AuthenticationPrincipal CustomUserDetails userDetails
String username = userDetails.getUsername();
```

**Acceptance Criteria**:
- [ ] All 3 instances use SecurityContext
- [ ] Integration tests verify authenticated user
- [ ] Unauthorized requests return 401

#### 2. Disabled Integration Tests (3 items)
**Impact**: False confidence - tests disabled without proper resolution

| File | Line | Issue |
|------|------|-------|
| PublishingEngineControllerIntegrationTest.java | 394 | Change log tracking - 500 error |
| EventControllerIntegrationTest.java | 1731 | Intermittent 500 error - null safety |
| RegistrationCleanupServiceIntegrationTest.java | 160, 198 | Test isolation - JPA transaction issue |

**Action**:
- **Change log test**: Implement diff logic or remove feature from MVP
- **500 error test**: Debug null pointer exception, add null checks
- **Cleanup tests**: Fix transaction isolation or mark as @Transactional

**Acceptance Criteria**:
- [ ] All tests re-enabled or feature removed from scope
- [ ] 100% test pass rate in CI/CD
- [ ] No @Disabled annotations in integration tests

### Frontend Error Handling & Monitoring

#### 3. Error Tracking Integration (2 items)
**Impact**: No production error visibility - blind to user issues

| File | Line | TODO |
|------|------|------|
| ErrorBoundary.tsx | 37 | Send to monitoring service (Sentry, DataDog) |
| shared/ErrorBoundary/ErrorBoundary.tsx | 65 | Integrate with Sentry or error tracking |

**Action**:
```typescript
// Install Sentry
npm install @sentry/react @sentry/tracing

// Configure in main.tsx
Sentry.init({
  dsn: import.meta.env.VITE_SENTRY_DSN,
  environment: import.meta.env.MODE,
  tracesSampleRate: 0.1,
});
```

**Acceptance Criteria**:
- [ ] Sentry integration configured for staging & production
- [ ] Error boundaries send exceptions to Sentry
- [ ] Source maps uploaded for readable stack traces
- [ ] Alert rules configured for critical errors

#### 4. Analytics Integration (2 items)
**Impact**: No production usage metrics - can't measure success

| File | Line | TODO |
|------|------|------|
| reportWebVitals.ts | 78, 82 | Integrate with analytics service |

**Action**:
```typescript
// Install Google Analytics 4
npm install react-ga4

// Configure in App.tsx
ReactGA.initialize(import.meta.env.VITE_GA_MEASUREMENT_ID);
ReactGA.send({ hitType: "pageview", page: window.location.pathname });
```

**Acceptance Criteria**:
- [ ] GA4 configured for production
- [ ] Web Vitals (CLS, FID, FCP, LCP) tracked
- [ ] Custom events for key user actions
- [ ] GDPR-compliant cookie consent

---

## 🟡 High Priority (Post-Launch Optimization) - 12 Items

**Timeline**: Address within first month of production

### Backend API Completeness

#### 5. Cross-Service Integration (3 items)
**Impact**: Incomplete data enrichment - degraded UX

| File | Line | TODO | Epic |
|------|------|------|------|
| EventController.java | 418 | Venue data expansion placeholder | 2.x |
| CompanyQueryService.java | 212-214 | Company statistics (events/speakers/partners) | 2.x |
| UserService.java | 679 | Company details for user profiles | 2.x |

**Action**: Implement REST client calls to Company Management Service
- EventController: Call `/api/companies/{id}` for venue enrichment
- CompanyQueryService: Call Event/Speaker/Partner services for counts
- UserService: Call `/api/companies/{id}` for profile enrichment

**Acceptance Criteria**:
- [ ] Cross-service calls use JWT propagation pattern
- [ ] Circuit breaker configured (fail gracefully if service down)
- [ ] Cache company data (Caffeine, 5 min TTL)

#### 6. Frontend API Stubs (5 items)
**Impact**: Users see "Coming Soon" placeholders

| Feature | Files Affected | Backend Status |
|---------|----------------|----------------|
| User /me endpoint | userAccountApi.ts (4 TODOs) | Needs implementation |
| Critical tasks endpoint | useEvents.ts, useEvents.test.tsx | Needs implementation |
| Team activity endpoint | useEvents.ts, useEvents.test.tsx | Needs implementation |
| Notifications API | useNotifications.ts | Deferred to Story 1.15a.10 |

**Action**:
- **Priority 1**: Implement `/api/users/me` endpoint (remove fallback)
- **Priority 2**: Implement `/api/events/critical-tasks?organizerUsername={username}`
- **Priority 3**: Implement `/api/events/team-activity?organizerUsername={username}`
- **Defer**: Notifications API to Epic 6

**Acceptance Criteria**:
- [ ] All stub comments removed
- [ ] Frontend tests unskipped
- [ ] Integration tests cover new endpoints

### Testing Quality

#### 7. Fix Flaky Tests (4 items)
**Impact**: CI/CD reliability issues

| File | Line | Issue |
|------|------|-------|
| useCompanyMutations.test.tsx | 265 | Race condition in CI |
| PartnerCreateEditModal.test.tsx | 276 | Loading state button not found |
| Accessibility.test.tsx | 57 | Keyboard/screen reader setup |
| ResponsiveDesign.test.tsx | 75 | Viewport mocking issues |

**Action**:
- Race condition: Add `waitFor()` or increase timeout
- Loading state: Verify button renders or adjust test expectation
- Accessibility: Use `@testing-library/jest-dom` matchers
- Responsive: Use `window.resizeTo()` or CSS media query mocks

**Acceptance Criteria**:
- [ ] All tests pass 10 consecutive times locally
- [ ] CI/CD pass rate > 99%
- [ ] No `.skip()` or `.only()` in committed tests

---

## 🟢 Medium Priority (Future Epics) - 43 Items

**Timeline**: Documented in Epic 6+ roadmap, not blocking production

### Epic 6: Event-Driven Architecture

#### 8. Domain Event Publishing (5 items)
**Current**: Event stubs with logging only
**Future**: Publish to AWS EventBridge for cross-service coordination

| Service | Events | Files |
|---------|--------|-------|
| Event Management | SpeakerContactedEvent, SpeakerWorkflowStateChangeEvent, SpeakerConfirmedEvent, OverflowDetectedEvent | SpeakerOutreachService.java:112, SpeakerWorkflowService.java:97/205, OverflowManagementService.java:100 |
| Company-User Management | UserRoleChangedEvent | RoleService.java:5/39/203 |

**Defer Reason**: MVP uses synchronous REST calls; events optimize for scale (>5000 users)

### Epic 8: Advanced Partner Management

#### 9. Partner Engagement Features (15 items)
**Current**: UI placeholders showing "Coming Soon"
**Future**: Full partner analytics, meetings, notes

| Feature | Frontend Files | Backend Status |
|---------|----------------|----------------|
| Meetings API | PartnerMeetingsTab.tsx, usePartnerMeetings.ts, partnerApi.ts | Not implemented |
| Activity API | PartnerActivityTab.tsx, usePartnerActivity.ts, partnerApi.ts | Not implemented |
| Notes API | usePartnerNotes.ts, partnerApi.ts | Not implemented |
| Previous Tier Tracking | PartnerOverviewTab.tsx:64/98 | Backend field missing |
| Company Details (swissUid, taxStatus) | PartnerOverviewTab.tsx:124 | Backend field missing |
| Auto-Renewal Settings | PartnerSettingsTab.tsx:32 | Backend field missing |

**Defer Reason**: MVP focuses on basic partner CRUD; analytics deferred to post-launch

### Epic 9: Workflow Validation Enhancements

#### 10. Advanced State Validations (2 items)

| File | Line | TODO | Story |
|------|------|------|-------|
| EventWorkflowStateMachine.java | 184 | Replace with actual speaker count validation | 5.3 |
| EventWorkflowStateMachine.java | 204 | Replace with actual content submission validation | 5.6 |

**Current**: Placeholder validations that always fail (for TDD)
**Future**: Real validation logic checking speaker counts and content submissions

**Defer Reason**: Stories 5.3/5.6 explicitly marked as future work in Epic 5 planning

### Epic 10: External API Integrations

#### 11. Swiss Business Registry API (2 items)

| File | Line | TODO |
|------|------|------|
| SwissUIDValidationService.java | 64 | Integrate with Swiss Business Registry API (AC12.3) |
| SwissUIDValidationService.java | 76 | Implement API integration |

**Current**: Format validation only (checksum verification)
**Future**: Real-time validation against Swiss government registry

**Defer Reason**: Format validation sufficient for MVP; real-time checks add cost/latency

### Epic 11: User Management Enhancements

#### 12. User Lifecycle Features (7 items)

| Feature | File | Lines | Status |
|---------|------|-------|--------|
| User Invitation System | UserService.java | 569 | Token generation, email, registration flow |
| User Deletion Business Rules | UserService.java | 462-464 | Last organizer check, cascade delete |
| Cognito Email Sync | UserService.java | 114 | Pre-token-generation hook |
| Audit Trail (previousValues) | UserService.java | 141/210 | Event enrichment |
| User Sync Panel (AWS Credentials) | UserList.tsx | 50/187 | STS SDK role assumption |

**Defer Reason**: MVP supports basic CRUD; advanced lifecycle deferred

---

## ⚪ Low Priority (Technical Debt) - 18 Items

**Timeline**: Address during refactoring sprints

### Code Quality Improvements

#### 13. Email Templates (1 item)
**File**: NotificationService.java:196
**TODO**: Add Thymeleaf templates for richer formatting
**Current**: Plain text emails
**Future**: HTML templates with branding

#### 14. Agenda Finalization Tracking (1 item)
**File**: PublishingService.java:585
**TODO**: Add agendaFinalizedAt/agendaFinalizedBy to Event entity
**Current**: No audit trail for agenda finalization
**Future**: Track who finalized and when

#### 15. Upload ID Validation (1 item)
**File**: SessionMaterialsServiceTest.java:191
**TODO**: Revisit if validation needed against GenericLogoService
**Current**: Test placeholder comment
**Action**: Remove comment or implement validation

### Frontend Technical Debt

#### 16. Hardcoded Values & Placeholders (8 items)

| File | Line | Issue | Fix |
|------|------|-------|-----|
| userAccountApi.ts | 224 | Language hardcoded to 'de' | Get from i18n context |
| userAccountApi.ts | 230-231 | quietHours undefined | Add to DTO type |
| userAccountApi.ts | 263-265 | allowCalendarSync, timezone, 2FA | Add to DTO type |
| userAccountApi.ts | 421/494 | SHA256 checksum empty | Implement checksum |
| publishingService.ts | 168 | Phase parameter not used | Wait for backend |
| EventOverviewTab.tsx | 127 | Notification modal placeholder | Implement modal |

**Action**: Create tech debt tickets, prioritize by user impact

#### 17. Test Improvements & Documentation (7 items)

| Category | Files | Count |
|----------|-------|-------|
| Missing test-id attributes | event-type-selection.spec.ts, topic-selection.spec.ts | 2 |
| Missing error handling tests | speaker-status-tracking.spec.ts:413 | 1 |
| Missing advanced features | ConflictDetectionAlert.test.tsx, SpeakerPreferencePanel.test.tsx | 5 |
| Missing ARIA labels/live regions | Multiple test files | 4 |

**Action**: Create accessibility improvement epic

---

## Cleanup Workflow

### Phase 1: Pre-Production (Week 1-2)
```bash
# 1. Fix critical security issues
git checkout -b fix/security-context-integration
# Implement SecurityContext in SlotAssignmentController (3 files)
# Run tests: ./gradlew :services:event-management-service:test
# Commit: fix(event-management): replace hardcoded usernames with SecurityContext

# 2. Integrate error tracking
git checkout -b feat/sentry-integration
npm install @sentry/react @sentry/tracing
# Configure Sentry in ErrorBoundary
# Test: trigger error and verify in Sentry dashboard
# Commit: feat(monitoring): integrate Sentry error tracking

# 3. Integrate analytics
git checkout -b feat/google-analytics
npm install react-ga4
# Configure GA4, add Web Vitals tracking
# Test: verify events in GA4 dashboard
# Commit: feat(analytics): integrate Google Analytics 4

# 4. Fix disabled tests
git checkout -b fix/disabled-integration-tests
# Debug and re-enable 3 disabled tests
# Commit: test(integration): fix disabled tests
```

### Phase 2: Post-Launch Optimization (Week 3-6)
```bash
# 5. Implement missing backend endpoints
git checkout -b feat/user-me-endpoint
# Implement /api/users/me
# Remove frontend fallback logic
# Commit: feat(api): implement /users/me endpoint

# 6. Fix flaky tests
git checkout -b fix/flaky-tests
# Address race conditions, viewport mocking
# Commit: test(frontend): fix flaky accessibility and responsive tests
```

### Phase 3: Epic 6+ Planning (Post-Launch)
```bash
# 7. Create GitHub Issues for future work
# - Epic 6: Event-Driven Architecture (5 domain events)
# - Epic 8: Partner Engagement (15 features)
# - Epic 9: Workflow Validations (2 stories)
# - Epic 10: Swiss Registry API (1 integration)
# - Epic 11: User Management (7 features)

# 8. Clean up TODO comments
git checkout -b chore/cleanup-documented-todos
# Replace Epic 6+ TODOs with GitHub issue links
# Example: // TODO Epic 6: Publish to EventBridge -> // See GitHub Issue #123
# Commit: chore: replace TODO comments with GitHub issue references
```

---

## Success Criteria

### Pre-Production Checklist
- [ ] All 8 critical TODOs resolved
- [ ] Sentry error tracking operational
- [ ] Google Analytics tracking operational
- [ ] 0 disabled integration tests
- [ ] 0 hardcoded security context usernames
- [ ] CI/CD pass rate > 99%

### Post-Launch Checklist (Month 1)
- [ ] All 12 high-priority TODOs resolved
- [ ] Flaky test pass rate > 99%
- [ ] Cross-service integration complete
- [ ] User /me endpoint live
- [ ] All frontend stub APIs removed

### Epic 6+ Planning
- [ ] 43 medium-priority TODOs converted to GitHub Issues
- [ ] Epic 6-11 roadmap published
- [ ] Tech debt backlog prioritized
- [ ] Weekly tech debt allocation (20% sprint capacity)

---

## Appendix: Full TODO Inventory

### Backend TODOs (27 items)

#### Event Management Service (17)
1. NotificationService.java:196 - Thymeleaf templates
2. SpeakerOutreachService.java:112 - SpeakerContactedEvent
3. EventWorkflowStateMachine.java:184 - Speaker count validation
4. EventWorkflowStateMachine.java:204 - Content submission validation
5. OverflowManagementService.java:100 - OverflowDetectedEvent
6. SpeakerWorkflowService.java:97 - SpeakerWorkflowStateChangeEvent
7. SpeakerWorkflowService.java:205 - SpeakerConfirmedEvent
8. PublishingService.java:585 - Agenda finalization tracking
9. SessionMaterialsServiceTest.java:191 - Upload ID validation
10. EventController.java:418 - Venue data expansion
11. SlotAssignmentController.java:205 - Security context (organizer)
12. SlotAssignmentController.java:305 - Security context (clear timing)
13. SlotAssignmentController.java:329 - Security context (auto-assign)
14. PublishingEngineControllerIntegrationTest.java:394 - Change log tracking
15. EventControllerIntegrationTest.java:1731 - 500 error debugging
16. RegistrationCleanupServiceIntegrationTest.java:160 - Test isolation
17. RegistrationCleanupServiceIntegrationTest.java:198 - Test isolation

#### Company-User Management Service (10)
1. CompanyQueryService.java:212 - Event count integration
2. CompanyQueryService.java:213 - Speaker count integration
3. CompanyQueryService.java:214 - Partner count integration
4. RoleService.java:5 - UserRoleChangedEvent
5. RoleService.java:39 - DomainEventPublisher unused warning
6. RoleService.java:203 - UserRoleChangedEvent stub
7. SwissUIDValidationService.java:64 - Swiss Registry API
8. SwissUIDValidationService.java:76 - Swiss Registry API implementation
9. UserService.java:114 - Cognito email sync
10. UserService.java:120 - Audit trail tracking
11. UserService.java:141/210 - previousValues placeholder
12. UserService.java:462-464 - User deletion business rules
13. UserService.java:569 - Invitation system
14. UserService.java:679 - Company details fetch

### Frontend TODOs (54 locations)

#### API Services (10)
1. eventService.ts:10 - Task 5b implementation
2-10. userAccountApi.ts - /users/me fallback, preferences, checksum (9 items)
11-15. partnerApi.ts - Future features (meetings, activity, notes)

#### Components & Pages (15)
16. App.tsx:147 - onBackToLogin callback refactor
17-18. HomePage.tsx - Topic preview support
19. EventOverviewTab.tsx:127 - Notification modal
20-27. Partner Management components - Epic 8 features (8 items)
28-30. Publishing components - Version control, timeline features

#### Error Handling & Monitoring (4)
31. ErrorBoundary.tsx:37 - Monitoring service
32-33. reportWebVitals.ts - Analytics integration
34. errorMessages.ts:43 - i18n translation
35. shared/ErrorBoundary.tsx:65 - Sentry integration

#### Testing (20)
36-37. Accessibility tests - Screen reader, high contrast
38. navigation.spec.ts - Login flow
39-40. E2E tests - testid attributes needed
41. speaker-status-tracking.spec.ts - Error handling
42-54. Various component tests - Disabled/skipped tests (13 items)

#### User & Company Management (3)
55-56. UserList.tsx - User sync panel (AWS credentials)
57. CompanyDetailView.tsx - User linking/unlinking

#### Hooks & Services (2)
58. useNotifications.ts:87 - Notifications API
59-60. useEvents.ts - organizerUsername filter support

---

## Notes

**Document Maintenance**:
- Update this plan weekly during pre-production phase
- Mark items as ✅ when completed
- Move completed items to `docs/changelog/todo-cleanup-log.md`

**GitHub Issue Tracking**:
- Create labels: `todo-critical`, `todo-high`, `todo-epic-6+`
- Link issues to this plan for traceability
- Update Epic 6+ roadmap when prioritizing medium-priority TODOs

**Team Communication**:
- Share this plan in weekly standup
- Assign critical TODOs to team members
- Review high-priority TODOs in sprint planning
