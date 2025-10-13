# E2E Tests for BATbern Web Frontend

## Overview

End-to-end tests for critical user workflows using Playwright Test framework.

## Prerequisites

### 1. Install Playwright

```bash
npm install -D @playwright/test @axe-core/playwright
npx playwright install
```

### 2. Infrastructure Requirements

For **forgot password tests** to work, you need:

- ✅ AWS Cognito User Pool deployed
- ✅ AWS SES email templates configured (German & English)
- ✅ MailHog email testing server running (dev environment)
- ✅ Test user accounts created in Cognito

### 3. Environment Configuration

Create `.env.test` file in the project root:

```bash
# Application URLs
E2E_BASE_URL=http://localhost:5173
E2E_API_URL=http://localhost:8080

# Email Testing (MailHog)
MAILHOG_URL=http://localhost:8025

# Test User Credentials
E2E_TEST_EMAIL=test@batbern.ch
E2E_TEST_PASSWORD=Test123!@#

# AWS Configuration (for deployed tests)
E2E_AWS_REGION=eu-central-1
E2E_COGNITO_USER_POOL_ID=eu-central-1_XXXXXX
E2E_COGNITO_CLIENT_ID=XXXXXXXXXXXXXXXXX
```

### 4. MailHog Setup (Email Testing)

Install and run MailHog for local email testing:

```bash
# macOS (Homebrew)
brew install mailhog
mailhog

# Docker
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

# Access MailHog UI: http://localhost:8025
```

### 5. Playwright Configuration

Create `playwright.config.ts` in the project root:

```typescript
import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';

dotenv.config({ path: '.env.test' });

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',

  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
  ],

  webServer: {
    command: 'npm run dev',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
  },
});
```

## Running Tests

### Run all E2E tests

```bash
npx playwright test
```

### Run specific test file

```bash
npx playwright test e2e/auth/forgot-password.spec.ts
```

### Run tests in UI mode

```bash
npx playwright test --ui
```

### Run tests in headed mode (see browser)

```bash
npx playwright test --headed
```

### Run tests in specific browser

```bash
npx playwright test --project=chromium
npx playwright test --project=firefox
npx playwright test --project=webkit
```

### Debug tests

```bash
npx playwright test --debug
```

### View test report

```bash
npx playwright show-report
```

## Test Structure

```
e2e/
├── accessibility/               # WCAG 2.1 AA compliance (Story 1.17)
│   ├── navigation.spec.ts      # Navigation accessibility
│   ├── layout.spec.ts          # Layout & focus management
│   └── screen-reader.spec.ts   # Screen reader announcements
├── auth/
│   ├── forgot-password.spec.ts # Story 1.2.2
│   ├── login.spec.ts           # Story 1.2
│   └── reset-password.spec.ts  # Future
├── fixtures/
│   └── test-users.ts           # Test user data
└── helpers/
    ├── email-helpers.ts        # MailHog utilities
    └── auth-helpers.ts         # Authentication utilities
```

## Test Coverage

### Accessibility Tests (Story 1.17 - WCAG 2.1 AA)

**Purpose**: Replace jsdom-based accessibility tests with real browser validation using Playwright and axe-core.

#### Navigation Accessibility (`navigation.spec.ts`)

- No accessibility violations on navigation
- Keyboard navigation through menu items
- Screen reader announcements for notifications
- ARIA attributes on navigation elements
- Semantic HTML landmarks
- Skip to main content link
- Color contrast requirements
- Mobile drawer accessibility
- Focus trap in dropdowns

#### Layout Accessibility (`layout.spec.ts`)

- No accessibility violations on base layout
- Visible focus indicators on all interactive elements
- 4.5:1 contrast ratio for normal text
- Responsive layout accessibility (mobile/tablet/desktop)
- Proper heading hierarchy
- Descriptive page titles
- Focus restoration after modal close
- 200% zoom support without horizontal scroll
- Proper lang attribute

#### Screen Reader Accessibility (`screen-reader.spec.ts`)

- ARIA live regions for dynamic content
- Form error announcements
- Descriptive labels for form inputs
- Loading state announcements
- Screen reader text for icons
- Table accessibility
- Notification announcements
- Route change announcements
- No ARIA violations
- High contrast mode support

### Forgot Password Flow (Story 1.2.2)

- ✅ **Basic Flow**: Form rendering, validation, submission
- ✅ **Email Delivery**: German/English templates, reset links
- ✅ **Resend Functionality**: Cooldown timer, duplicate emails
- ✅ **Security**: Email enumeration prevention, rate limiting
- ✅ **Error Handling**: Network errors, retry functionality
- ✅ **Accessibility**: WCAG 2.1 AA compliance
- ✅ **i18n**: German/English language support

### Test Scenarios (50+ tests)

1. **Basic Flow** (6 tests)
   - Page rendering
   - Email validation
   - Form submission
   - Confirmation screen

2. **Email Delivery** (4 tests)
   - German email template
   - English email template
   - Reset link format
   - Expiration notice

3. **Resend Functionality** (4 tests)
   - Resend button availability
   - Cooldown timer
   - Multiple emails sent

4. **Security** (2 tests)
   - Email enumeration prevention
   - Audit logging

5. **Rate Limiting** (2 tests)
   - 3 requests per hour limit
   - Countdown timer display

6. **Error Handling** (2 tests)
   - Network error display
   - Retry functionality

7. **Accessibility** (3 tests)
   - No violations (axe-core)
   - Keyboard navigation
   - Screen reader support

8. **i18n** (2 tests)
   - German translations
   - English translations

## CI/CD Integration

Add to `.github/workflows/e2e-tests.yml`:

```yaml
name: E2E Tests

on:
  pull_request:
    branches: [main, develop]
  push:
    branches: [main, develop]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '20'

      - name: Install dependencies
        run: npm ci

      - name: Install Playwright
        run: npx playwright install --with-deps

      - name: Start MailHog
        run: docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog

      - name: Run E2E tests
        run: npx playwright test

      - name: Upload test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: playwright-report
          path: playwright-report/
```

## Troubleshooting

### Tests fail with "baseURL not set"

- Ensure `.env.test` exists with `E2E_BASE_URL`
- Or start dev server: `npm run dev`

### Email tests fail

- Verify MailHog is running: `curl http://localhost:8025`
- Check SMTP port 1025 is available
- Review MailHog logs

### Rate limit tests fail

- Clear rate limit cache between test runs
- Ensure tests run sequentially (not in parallel)

### Timeout errors

- Increase timeout in `playwright.config.ts`
- Check if dev server started successfully
- Verify network connectivity

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Data Cleanup**: Reset state after each test
3. **Selectors**: Use data-testid or role-based selectors
4. **Waits**: Use explicit waits, avoid fixed timeouts
5. **Screenshots**: Capture on failure for debugging
6. **Parallel Execution**: Design tests to run in parallel

## Future Enhancements

- [ ] Visual regression testing (Percy, Chromatic)
- [ ] Performance testing (Lighthouse CI)
- [ ] Cross-browser testing (BrowserStack)
- [ ] Mobile responsive testing
- [ ] API mocking for offline testing
