# Playwright E2E Testing - Quick Start Guide

## ✅ What's Been Set Up

Playwright is now fully configured for E2E testing with accessibility validation!

### Installed Packages

- `@playwright/test` v1.56.0 - Core test runner
- `@axe-core/playwright` v4.10.2 - Accessibility testing

### Browsers Installed

- ✅ Chromium (Desktop Chrome)
- ✅ Firefox (Desktop Firefox)
- ✅ WebKit (Desktop Safari)
- ✅ Mobile Chrome (Pixel 5)
- ✅ Mobile Safari (iPhone 12)

### Test Files Created

```
e2e/accessibility/
├── navigation.spec.ts      # Navigation component accessibility (9 tests)
├── layout.spec.ts          # Layout & focus management (10 tests)
└── screen-reader.spec.ts   # Screen reader announcements (11 tests)
```

**Total: 30 accessibility tests covering WCAG 2.1 AA compliance**

### Configuration Files

- ✅ `playwright.config.ts` - Main configuration
- ✅ Updated `package.json` with test scripts
- ✅ Updated `e2e/README.md` with documentation

## 🚀 Running Tests

### Quick Commands

```bash
# Run all E2E tests
npm run test:e2e

# Run accessibility tests only
npm run test:e2e:accessibility

# Run with UI mode (recommended for development)
npm run test:e2e:ui

# Run in headed mode (see browser)
npm run test:e2e:headed

# Debug specific test
npm run test:e2e:debug

# View test report
npm run test:e2e:report
```

### First Test Run

1. **Start dev server** (in one terminal):

   ```bash
   npm run dev
   ```

2. **Run accessibility tests** (in another terminal):
   ```bash
   npm run test:e2e:accessibility
   ```

## 📋 What the Tests Cover

### Navigation Accessibility (navigation.spec.ts)

- ✅ No axe-core violations
- ✅ Keyboard navigation through menu items
- ✅ Screen reader announcements for notifications
- ✅ ARIA attributes (aria-label, aria-expanded, aria-haspopup)
- ✅ Semantic HTML (header, nav, main)
- ✅ Skip to main content link
- ✅ Color contrast (WCAG AA 4.5:1)
- ✅ Mobile drawer accessibility
- ✅ Focus trap in dropdowns

### Layout Accessibility (layout.spec.ts)

- ✅ No axe-core violations on base layout
- ✅ Visible focus indicators (outline, box-shadow)
- ✅ 4.5:1 contrast ratio validation
- ✅ Responsive accessibility (mobile/tablet/desktop)
- ✅ Proper heading hierarchy (h1 → h2 → h3)
- ✅ Descriptive page titles
- ✅ Focus restoration after modal close
- ✅ 200% zoom without horizontal scroll
- ✅ Proper lang attribute

### Screen Reader Accessibility (screen-reader.spec.ts)

- ✅ ARIA live regions (polite/assertive)
- ✅ Form error announcements
- ✅ Descriptive labels (aria-label/for)
- ✅ Loading state announcements
- ✅ Icon button labels
- ✅ Table accessibility (caption, scope)
- ✅ Notification announcements
- ✅ Route change announcements
- ✅ No ARIA violations
- ✅ High contrast mode support

## 🔧 Troubleshooting

### "Cannot find module '@playwright/test'"

```bash
npm install
```

### Browsers not installed

```bash
npx playwright install
```

### Dev server not running

```bash
# Terminal 1
npm run dev

# Terminal 2
npm run test:e2e
```

### Tests failing with authentication errors

The tests include TODO comments for authentication setup. You'll need to:

1. Implement login flow in `beforeEach` hooks
2. Add test user credentials
3. Mock authentication for different roles

## 📊 CI/CD Integration

Playwright is configured for CI with:

- ✅ Retry on failure (2 retries)
- ✅ Single worker for stability
- ✅ JUnit XML reports
- ✅ Screenshots on failure
- ✅ Video on failure
- ✅ HTML test reports

## 🎯 Next Steps

### 1. Update Authentication Flow

Add proper login in test files:

```typescript
test.beforeEach(async ({ page }) => {
  // Replace with actual login flow
  await page.goto('/login');
  await page.fill('[name="email"]', 'test@batbern.ch');
  await page.fill('[name="password"]', 'Test123!');
  await page.click('button[type="submit"]');
  await page.waitForURL('/dashboard');
});
```

### 2. Add More Test Coverage

- Event management workflows
- Speaker coordination flows
- Partner analytics dashboards
- Attendee experience journeys

### 3. Fix Existing Issues

Address Quinn's TEST-001 recommendation:

- Replace failing jsdom accessibility tests
- Run Playwright tests in CI
- Ensure all tests pass before deployment

## 📚 Resources

- [Playwright Docs](https://playwright.dev)
- [axe-core Rules](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md)
- [WCAG 2.1 Quick Reference](https://www.w3.org/WAI/WCAG21/quickref/)
- [WAI-ARIA Practices](https://www.w3.org/WAI/ARIA/apg/)

## ✨ Summary

Playwright is ready to use! The accessibility tests validate WCAG 2.1 AA compliance in real browsers (Chromium, Firefox, WebKit), addressing Quinn's TEST-001 issue about jsdom limitations.

Run `npm run test:e2e:ui` to start testing interactively! 🎉
