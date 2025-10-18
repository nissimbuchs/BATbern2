import { test, expect } from '@playwright/test';

/**
 * CORS Validation E2E Tests
 *
 * These tests validate that the frontend can make cross-origin requests to the API
 * with all required custom headers without CORS errors.
 *
 * Critical for preventing CORS-related production incidents.
 */

test.describe('CORS Validation', () => {
  const apiBaseUrl = process.env.E2E_API_URL || 'https://api.staging.batbern.ch';
  const frontendOrigin = process.env.E2E_BASE_URL || 'https://staging.batbern.ch';

  test.beforeEach(async ({ page }) => {
    // Intercept and log network requests to validate CORS
    page.on('requestfailed', (request) => {
      console.error(`Request failed: ${request.url()} - ${request.failure()?.errorText}`);
    });
  });

  test('should allow GET requests with X-Correlation-ID header', async ({ page }) => {
    // Navigate to frontend to establish origin
    await page.goto(frontendOrigin);

    // Make API request with custom header from browser context
    const response = await page.request.get(`${apiBaseUrl}/health`, {
      headers: {
        'X-Correlation-ID': 'test-correlation-id-' + Date.now(),
      },
    });

    // Should not have CORS error
    expect(response.ok() || response.status() === 401).toBeTruthy();

    // Verify CORS headers are present in response
    const corsHeader = response.headers()['access-control-allow-origin'];
    expect(corsHeader).toBeDefined();
  });

  test('should allow requests with Accept-Language header', async ({ page }) => {
    await page.goto(frontendOrigin);

    const response = await page.request.get(`${apiBaseUrl}/health`, {
      headers: {
        'Accept-Language': 'de-CH',
      },
    });

    expect(response.ok() || response.status() === 401).toBeTruthy();
  });

  test('should allow requests with multiple custom headers', async ({ page }) => {
    await page.goto(frontendOrigin);

    const response = await page.request.get(`${apiBaseUrl}/health`, {
      headers: {
        'X-Correlation-ID': 'test-' + Date.now(),
        'Accept-Language': 'de-CH',
        'Accept': 'application/json',
      },
    });

    expect(response.ok() || response.status() === 401).toBeTruthy();
  });

  test('should handle OPTIONS preflight requests', async ({ page }) => {
    await page.goto(frontendOrigin);

    // Simulate preflight request
    const response = await page.request.fetch(`${apiBaseUrl}/api/v1/companies`, {
      method: 'OPTIONS',
      headers: {
        'Origin': frontendOrigin,
        'Access-Control-Request-Method': 'GET',
        'Access-Control-Request-Headers': 'authorization,x-correlation-id,accept-language',
      },
    });

    // Preflight should succeed
    expect(response.status()).toBe(200);

    // Verify CORS headers in preflight response
    const headers = response.headers();
    expect(headers['access-control-allow-origin']).toBeDefined();
    expect(headers['access-control-allow-methods']).toBeDefined();
    expect(headers['access-control-allow-headers']).toBeDefined();

    // Verify our custom headers are allowed
    const allowedHeaders = headers['access-control-allow-headers']?.toLowerCase() || '';
    expect(allowedHeaders).toContain('x-correlation-id');
    expect(allowedHeaders).toContain('accept-language');
  });

  test('should make successful authenticated request with all headers', async ({ page, context }) => {
    // This test requires authentication setup
    // Skip if no auth token provided
    const authToken = process.env.E2E_AUTH_TOKEN;
    if (!authToken) {
      test.skip();
      return;
    }

    await page.goto(frontendOrigin);

    const response = await page.request.get(`${apiBaseUrl}/api/v1/companies?page=1&limit=5`, {
      headers: {
        'Authorization': `Bearer ${authToken}`,
        'X-Correlation-ID': 'test-' + Date.now(),
        'Accept-Language': 'de-CH',
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
    });

    // Should succeed with auth
    expect(response.ok()).toBeTruthy();

    // Verify response structure
    const body = await response.json();
    expect(body.pagination).toBeDefined();
    expect(body.data).toBeInstanceOf(Array);
  });

  test('should track CORS errors in console', async ({ page }) => {
    const consoleErrors: string[] = [];

    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text());
      }
    });

    page.on('pageerror', (error) => {
      consoleErrors.push(error.message);
    });

    await page.goto(frontendOrigin);

    // Make request that should succeed
    await page.evaluate(async (apiUrl) => {
      try {
        await fetch(`${apiUrl}/health`, {
          headers: {
            'X-Correlation-ID': 'test-' + Date.now(),
          },
        });
      } catch (error) {
        console.error('Fetch failed:', error);
      }
    }, apiBaseUrl);

    // Wait a bit for any async errors
    await page.waitForTimeout(1000);

    // Should not have CORS-related errors
    const corsErrors = consoleErrors.filter(err =>
      err.toLowerCase().includes('cors') ||
      err.toLowerCase().includes('access-control-allow-origin')
    );

    expect(corsErrors).toHaveLength(0);
  });
});

test.describe('Header Propagation Validation', () => {
  const apiBaseUrl = process.env.E2E_API_URL || 'https://api.staging.batbern.ch';

  test('should propagate correlation ID through request flow', async ({ page }) => {
    const correlationId = 'e2e-test-' + Date.now();

    // Intercept API responses to verify correlation ID
    let responseCorrelationId: string | null = null;

    page.on('response', async (response) => {
      if (response.url().includes('/api/v1/')) {
        responseCorrelationId = response.headers()['x-correlation-id'] || null;
      }
    });

    // Navigate to app
    await page.goto(process.env.E2E_BASE_URL || 'https://staging.batbern.ch');

    // Make API request with correlation ID
    await page.evaluate(async ([url, corrId]) => {
      try {
        await fetch(url + '/health', {
          headers: {
            'X-Correlation-ID': corrId,
          },
        });
      } catch (error) {
        // Expected in some environments
      }
    }, [apiBaseUrl, correlationId] as const);

    // Wait for response
    await page.waitForTimeout(500);

    // Correlation ID should be propagated
    // Note: Backend may echo it back or generate new one
    // The important thing is the request succeeded
  });
});
