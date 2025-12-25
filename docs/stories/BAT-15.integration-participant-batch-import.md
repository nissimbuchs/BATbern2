# BAT-15: Integration - Participant Batch Import

**Linear**: [BAT-15](https://linear.app/batbern/issue/BAT-15)
**Status**: Blocked
**Epic**: Epic 3 - Historical Data Migration
**Project**: [Epic 3: Historical Data Migration](https://linear.app/batbern/project/epic-3-historical-data-migration-168670d74297)
**Created**: 2025-12-25

---

## Story

**As an** organizer,
**I want** the participant batch import to work end-to-end with the real backend,
**so that** I can successfully import all 2,307 historical participants into the production system.

**Integration Focus:** This story replaces MSW mocks with real backend APIs and validates complete end-to-end functionality with the actual historical CSV file.

---

## Dependencies

**Blocked By:**
- ⚠️ [BAT-12](https://linear.app/batbern/issue/BAT-12) (API Contract) must be Done first
- ⚠️ [BAT-13](https://linear.app/batbern/issue/BAT-13) (Frontend) must be Done first
- ⚠️ [BAT-14](https://linear.app/batbern/issue/BAT-14) (Backend) must be Done first

**Required Artifacts:**
- API contract with OpenAPI spec (from BAT-12)
- Frontend implementation with MSW mocks (from BAT-13)
- Backend service deployed and healthy (from BAT-14)
- Database migrations applied
- Environment configuration ready

---

## Integration Context

### Component Stories

**Integration Dependencies:**
- **API Contract Story**: [BAT-12](https://linear.app/batbern/issue/BAT-12) - Batch registration endpoint specification
- **Frontend Story**: [BAT-13](https://linear.app/batbern/issue/BAT-13) - CSV upload UI with MSW mocks
- **Backend Story**: [BAT-14](https://linear.app/batbern/issue/BAT-14) - Batch registration service implementation

### Integration Scope

**What needs to be integrated:**
1. **Remove MSW Mocks**
   - Disable MSW `eventBatchRegistrationHandlers.ts` in production
   - Configure MSW to only run in development/test mode
   - Update API client to use real backend URL

2. **API Client Configuration**
   - Configure backend service URL per environment
   - Add authentication headers (JWT from Cognito)
   - Configure request/response interceptors
   - Add retry logic for transient failures

3. **End-to-End Validation**
   - Test full CSV upload → parsing → batch API call → result display flow
   - Verify actual historical CSV file (`anmeldungen.csv` with 2,307 participants) imports successfully
   - Validate data integrity (all registrations created correctly)
   - Confirm performance meets requirements (<10 min for full import)

### Environment Requirements

**Dev Environment:**
- Backend API URL: `https://api-dev.batbern.ch`
- Database: PostgreSQL (dev instance)
- Cognito: Staging Cognito user pool
- Frontend URL: `https://dev.batbern.ch`

**Staging Environment:**
- Backend API URL: `https://api-staging.batbern.ch`
- Database: PostgreSQL (staging instance)
- Cognito: Staging Cognito user pool
- Frontend URL: `https://staging.batbern.ch`

**Environment Variables:**
```bash
# Frontend (.env.development.real)
REACT_APP_USE_MOCKS=false
REACT_APP_API_BASE_URL=https://api-dev.batbern.ch

# Frontend (.env.staging)
REACT_APP_USE_MOCKS=false
REACT_APP_API_BASE_URL=https://api-staging.batbern.ch

# Frontend (.env.production)
REACT_APP_USE_MOCKS=false
REACT_APP_API_BASE_URL=https://api.batbern.ch
```

### Rollback Strategy

**If integration fails:**
1. Re-enable MSW mocks via environment variable (`REACT_APP_USE_MOCKS=true`)
2. Frontend continues to work with mocked data
3. Fix backend issues without blocking frontend users
4. Deploy fixed backend
5. Disable mocks again

---

## Acceptance Criteria

1. **MSW Mocks Removed**
   - [ ] MSW handlers disabled in production build
   - [ ] MSW only runs in development mode (`REACT_APP_USE_MOCKS=true`)
   - [ ] API client configured to use real backend URL
   - [ ] Mocks still available for frontend unit tests

2. **API Integration Working**
   - [ ] Frontend successfully calls `POST /api/v1/events/batch_registrations`
   - [ ] Authentication headers included (JWT from Cognito)
   - [ ] Request payload matches API contract
   - [ ] Response handled correctly (success, partial success, error)
   - [ ] Error handling works with real backend errors

3. **Full CSV Import Validated**
   - [ ] Upload actual `anmeldungen.csv` (2,307 participants)
   - [ ] CSV parses correctly (handles BOM, German characters)
   - [ ] All participants processed (success or explicit error)
   - [ ] Import completes in <10 minutes
   - [ ] Progress bar updates accurately

4. **Data Integrity Verified**
   - [ ] All successful participants have users created
   - [ ] All event registrations created correctly
   - [ ] Duplicate registrations skipped (idempotent)
   - [ ] Synthetic emails generated correctly for missing emails
   - [ ] Database queries confirm all data present

5. **Performance Validated**
   - [ ] Average API call time <500ms
   - [ ] Full import time <10 minutes (2,307 participants)
   - [ ] No UI blocking during import
   - [ ] Progress updates smoothly

6. **E2E Tests Passing**
   - [ ] E2E test uploads sample CSV and verifies success
   - [ ] E2E test handles partial success scenario
   - [ ] E2E test handles error scenarios
   - [ ] All E2E tests run against real backend (not mocks)

---

## Tasks / Subtasks

- [ ] Task 1: Environment Configuration
  - [ ] Configure backend service URL for dev environment
  - [ ] Set up environment variables (`.env.development.real`)
  - [ ] Configure AWS Cognito settings for staging
  - [ ] Test environment connectivity (health check)

- [ ] Task 2: Remove/Disable MSW Mocks
  - [ ] Update MSW configuration to check `REACT_APP_USE_MOCKS`
  - [ ] Disable `eventBatchRegistrationHandlers` in production
  - [ ] Keep mocks available for unit tests
  - [ ] Verify mocks disabled in production build
  - [ ] Document how to re-enable mocks for development

- [ ] Task 3: API Client Configuration
  - [ ] Update `apiClient.ts` with backend base URL from env
  - [ ] Add JWT authentication interceptor
  - [ ] Add request/response logging (dev only)
  - [ ] Configure retry logic for transient failures (3 attempts)
  - [ ] Add request timeout (30 seconds)
  - [ ] Test API client with real backend

- [ ] Task 4: Write E2E Tests with Real Backend
  - [ ] Set up E2E test environment (Playwright)
  - [ ] Write E2E test: Upload sample CSV → verify participants created
  - [ ] Write E2E test: Upload CSV with errors → verify error display
  - [ ] Write E2E test: Upload duplicate data → verify idempotency
  - [ ] Configure test data cleanup after tests
  - [ ] Run E2E tests against dev backend

- [ ] Task 5: Integration Testing with Sample Data
  - [ ] Create sample CSV with 10 participants
  - [ ] Upload sample CSV via UI
  - [ ] Verify all participants imported
  - [ ] Query database to confirm registrations created
  - [ ] Test error scenarios (invalid event codes)
  - [ ] Test duplicate upload (should skip)

- [ ] Task 6: Full Historical Data Import Test
  - [ ] Upload actual `anmeldungen.csv` (2,307 participants)
  - [ ] Monitor import progress (should complete in <10 min)
  - [ ] Verify progress bar accuracy
  - [ ] Check final summary (success/failed/skipped counts)
  - [ ] Query database to verify all registrations created
  - [ ] Export any failed participants for manual review

- [ ] Task 7: Data Integrity Validation
  - [ ] Run SQL queries to count participants imported
  - [ ] Verify event registration counts match CSV
  - [ ] Check synthetic emails generated correctly
  - [ ] Verify German characters handled correctly
  - [ ] Confirm no duplicate registrations created
  - [ ] Validate registration status = "attended"

- [ ] Task 8: Performance Validation
  - [ ] Measure API response times (P50, P95, P99)
  - [ ] Measure total import time for 2,307 participants
  - [ ] Monitor backend CPU/memory during import
  - [ ] Monitor frontend responsiveness during import
  - [ ] Identify and fix performance bottlenecks
  - [ ] Verify <10 minute import requirement met

- [ ] Task 9: Error Handling Validation
  - [ ] Test with invalid CSV structure
  - [ ] Test with missing required fields
  - [ ] Test with non-existent event codes
  - [ ] Test with backend unavailable
  - [ ] Test with authentication expired
  - [ ] Verify all errors display user-friendly messages

- [ ] Task 10: Cross-Browser Testing
  - [ ] Test in Chrome (desktop)
  - [ ] Test in Firefox (desktop)
  - [ ] Test in Safari (desktop)
  - [ ] Test on mobile devices (iOS/Android)
  - [ ] Fix browser-specific issues

- [ ] Task 11: Deployment Validation
  - [ ] Deploy frontend to dev environment
  - [ ] Deploy backend to dev environment
  - [ ] Smoke test in dev
  - [ ] Deploy to staging environment
  - [ ] Full regression test in staging
  - [ ] Prepare production deployment plan

---

## Dev Notes - Integration Guide

### MSW Removal Configuration

**File**: `src/mocks/browser.ts`

```typescript
import { setupWorker } from 'msw';
import { handlers } from './handlers';

export const worker = setupWorker(...handlers);

// Only enable in development when explicitly requested
if (
  process.env.NODE_ENV === 'development' &&
  process.env.REACT_APP_USE_MOCKS === 'true'
) {
  worker.start({
    onUnhandledRequest: 'bypass',
  });
}
```

**Remove from production**:
```typescript
// src/index.tsx - Remove MSW initialization in production
if (process.env.NODE_ENV !== 'production') {
  // MSW setup only in dev/test
  const { worker } = await import('./mocks/browser');
  if (process.env.REACT_APP_USE_MOCKS === 'true') {
    worker.start();
  }
}
```

### API Client Configuration

**File**: `src/services/apiClient.ts`

```typescript
import axios from 'axios';
import { Auth } from 'aws-amplify';

const apiClient = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: Add authentication token
apiClient.interceptors.request.use(
  async (config) => {
    try {
      const session = await Auth.currentSession();
      const token = session.getIdToken().getJwtToken();
      config.headers.Authorization = `Bearer ${token}`;
    } catch (error) {
      console.error('Failed to get auth token:', error);
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Handle errors and token refresh
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    // Handle 401 Unauthorized - refresh token
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;

      try {
        const session = await Auth.currentSession();
        const token = session.getIdToken().getJwtToken();
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        // Token refresh failed - redirect to login
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }

    // Handle other errors
    return Promise.reject(error);
  }
);

export default apiClient;
```

### E2E Test Setup

**File**: `web-frontend/e2e/participant-batch-import.spec.ts`

```typescript
import { test, expect } from '@playwright/test';
import { loginAsOrganizer } from './helpers/auth';
import fs from 'fs';
import path from 'path';

test.describe('Participant Batch Import (Real Backend)', () => {
  test.beforeEach(async ({ page }) => {
    // Authenticate as organizer
    await loginAsOrganizer(page);
    await page.goto('/users');
  });

  test('should import participants from CSV', async ({ page }) => {
    // Open import modal
    await page.click('text=Import Participants');

    // Upload CSV file
    const csvPath = path.resolve(__dirname, 'fixtures/sample-participants.csv');
    const fileInput = await page.locator('input[type="file"]');
    await fileInput.setInputFiles(csvPath);

    // Wait for preview to load
    await expect(page.locator('text=participants ready to import')).toBeVisible();

    // Start import
    await page.click('text=Import');

    // Wait for completion
    await expect(page.locator('text=Import complete!')).toBeVisible({ timeout: 60000 });

    // Verify result summary
    const successText = await page.locator('[role="alert"]').textContent();
    expect(successText).toContain('created');

    // Close modal
    await page.click('text=Close');

    // Verify participants appear in user list (optional)
    // await expect(page.locator('text=adrian.buerki')).toBeVisible();
  });

  test('should handle CSV with errors', async ({ page }) => {
    // Upload CSV with invalid event codes
    await page.click('text=Import Participants');

    const csvPath = path.resolve(__dirname, 'fixtures/participants-with-errors.csv');
    const fileInput = await page.locator('input[type="file"]');
    await fileInput.setInputFiles(csvPath);

    await page.click('text=Import');

    // Wait for completion
    await expect(page.locator('text=Import complete!')).toBeVisible({ timeout: 60000 });

    // Verify error count shown
    const resultText = await page.locator('[role="alert"]').textContent();
    expect(resultText).toContain('failed');
  });

  test('should handle duplicate imports (idempotency)', async ({ page }) => {
    // Import same CSV twice
    const csvPath = path.resolve(__dirname, 'fixtures/sample-participants.csv');

    // First import
    await page.click('text=Import Participants');
    const fileInput1 = await page.locator('input[type="file"]');
    await fileInput1.setInputFiles(csvPath);
    await page.click('text=Import');
    await expect(page.locator('text=Import complete!')).toBeVisible({ timeout: 60000 });
    await page.click('text=Close');

    // Second import (should skip duplicates)
    await page.click('text=Import Participants');
    const fileInput2 = await page.locator('input[type="file"]');
    await fileInput2.setInputFiles(csvPath);
    await page.click('text=Import');
    await expect(page.locator('text=Import complete!')).toBeVisible({ timeout: 60000 });

    // Verify skipped count
    const resultText = await page.locator('[role="alert"]').textContent();
    expect(resultText).toContain('skipped');
  });
});
```

### Data Validation Queries

**SQL Queries for Validation**:

```sql
-- Count total participants imported
SELECT COUNT(DISTINCT user_id) as total_participants
FROM event_registrations
WHERE status = 'attended'
  AND created_at > '2025-12-25'; -- After import date

-- Count registrations by event
SELECT e.code, e.title, COUNT(*) as registration_count
FROM event_registrations er
JOIN events e ON e.id = er.event_id
WHERE er.status = 'attended'
  AND er.created_at > '2025-12-25'
GROUP BY e.code, e.title
ORDER BY e.code;

-- Find synthetic emails
SELECT email, first_name, last_name
FROM users
WHERE email LIKE '%@batbern.ch'
  AND created_at > '2025-12-25';

-- Verify no duplicate registrations
SELECT user_id, event_id, COUNT(*) as duplicates
FROM event_registrations
GROUP BY user_id, event_id
HAVING COUNT(*) > 1;
```

### Performance Monitoring

**CloudWatch Metrics to Monitor**:
- API Gateway request count
- API Gateway latency (P50, P95, P99)
- Backend service CPU utilization
- Backend service memory utilization
- Database connection pool usage
- Database query execution time

**Expected Performance**:
- API response time: P95 <500ms
- Total import time: <10 minutes for 2,307 participants
- API calls: ~2,307 (one per participant)
- Average throughput: ~4 participants/second

---

## Definition of Done Checklist

### Integration Complete
- [ ] MSW mocks removed/disabled in production builds
- [ ] Frontend successfully calls real backend API
- [ ] All API endpoints responding correctly
- [ ] Authentication flow works end-to-end
- [ ] Authorization enforced correctly (ORGANIZER role)
- [ ] Error handling works as designed
- [ ] Progress updates display correctly

### Testing Complete
- [ ] All E2E tests passing with real backend
- [ ] Sample CSV import test passed (10 participants)
- [ ] Full historical CSV import test passed (2,307 participants)
- [ ] Cross-browser testing completed
- [ ] Mobile responsive testing completed
- [ ] Performance tests passing
- [ ] Data integrity validation passed

### Data Integrity Validated
- [ ] All 2,307 participants imported (or explicit errors documented)
- [ ] Event registrations created correctly
- [ ] Synthetic emails generated correctly
- [ ] No duplicate registrations created
- [ ] Database queries confirm all data present
- [ ] Registration status = "attended" for all historical data

### Performance Validated
- [ ] API response time P95 <500ms
- [ ] Full import time <10 minutes
- [ ] No UI blocking during import
- [ ] Progress bar updates accurately
- [ ] No performance regressions
- [ ] Performance monitoring enabled

### Deployment Ready
- [ ] Deployed to dev environment successfully
- [ ] Deployed to staging environment successfully
- [ ] Smoke tests passing in all environments
- [ ] Environment variables configured
- [ ] CloudWatch monitoring enabled
- [ ] Rollback procedure tested
- [ ] Production deployment plan documented

### Documentation Complete
- [ ] API integration documented
- [ ] Environment setup documented
- [ ] Deployment procedure documented
- [ ] Rollback procedure documented
- [ ] Known issues documented (if any)
- [ ] Import guide for organizers created

---

## Original Story Reference

**This integration story completes the original user story:**
- **Original Epic**: 3 - Historical Data Migration
- **Original Story**: 3.2 - Participant/Attendee Batch Import
- **Component Stories**:
  - 3.2.1a: API Contract (batch registration endpoint)
  - 3.2.1b: Frontend (CSV upload UI with MSW mocks)
  - 3.2.1c: Backend (batch registration service)
  - 3.2.1d: Integration (this story - end-to-end validation)

**When this story is Done, Story 3.2 is fully complete and Epic 3 Phase 2 is 100% complete.**

---

## Change Log

| Date | Version | Description | Author |
|------|---------|-------------|--------|
| 2025-12-25 | 0.1 | Initial integration story creation | Bob (SM) |

---

## Dev Agent Record

_This section will be populated during implementation by the dev agent._

### Agent Model Used
_To be filled by dev agent_

### Integration Issues Encountered
_To be filled by dev agent:_
- Issue description
- Root cause
- Resolution
- Lessons learned

### Configuration Details
_To be filled by dev agent:_
- Environment variables used
- API endpoints configured
- Feature flags (if any)
- Performance settings

### Deployment Notes
_To be filled by dev agent:_
- Deployment date/time per environment
- Issues encountered during deployment
- Rollback triggers (if any)
- Post-deployment observations

### Actual Performance Metrics
_To be filled by dev agent:_
- Page load times
- API response times (P50, P95, P99)
- Total import time for 2,307 participants
- Error rates
- Throughput
- Comparison to baselines
