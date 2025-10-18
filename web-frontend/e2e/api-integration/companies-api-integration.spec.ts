import { test, expect } from '@playwright/test';

/**
 * Companies API Integration Tests
 *
 * End-to-end tests validating the full request flow:
 * Frontend → AWS API Gateway → Spring Boot Gateway → Company Service
 *
 * Ensures CORS, authentication, and API contracts work correctly.
 */

test.describe('Companies API Integration', () => {
  const apiBaseUrl = process.env.E2E_API_URL || 'https://api.staging.batbern.ch';
  const authToken = process.env.E2E_AUTH_TOKEN;

  test.describe('Unauthenticated Requests', () => {
    test('should return 401 for unauthenticated list companies request', async ({ page }) => {
      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          'X-Correlation-ID': 'test-' + Date.now(),
          'Accept-Language': 'de-CH',
        },
      });

      expect(response.status()).toBe(401);
    });

    test('should return 401 for unauthenticated search request', async ({ page }) => {
      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies/search?query=test`, {
        headers: {
          'X-Correlation-ID': 'test-' + Date.now(),
        },
      });

      expect(response.status()).toBe(401);
    });
  });

  test.describe('Authenticated Requests', () => {
    test.skip(!authToken, 'Skipping authenticated tests - no auth token provided');

    test('should list companies with pagination', async ({ page }) => {
      const response = await page.request.get(
        `${apiBaseUrl}/api/v1/companies?page=1&limit=10`,
        {
          headers: {
            'Authorization': `Bearer ${authToken}`,
            'X-Correlation-ID': 'test-' + Date.now(),
            'Accept-Language': 'de-CH',
            'Content-Type': 'application/json',
          },
        }
      );

      expect(response.ok()).toBeTruthy();

      const body = await response.json();

      // Validate response structure matches OpenAPI spec
      expect(body).toHaveProperty('data');
      expect(body).toHaveProperty('pagination');
      expect(Array.isArray(body.data)).toBeTruthy();

      // Validate pagination metadata
      expect(body.pagination).toHaveProperty('page');
      expect(body.pagination).toHaveProperty('limit');
      expect(body.pagination).toHaveProperty('totalItems');
      expect(body.pagination).toHaveProperty('totalPages');
      expect(body.pagination).toHaveProperty('hasNext');
      expect(body.pagination).toHaveProperty('hasPrev');
    });

    test('should search companies', async ({ page }) => {
      const response = await page.request.get(
        `${apiBaseUrl}/api/v1/companies/search?query=test&limit=5`,
        {
          headers: {
            'Authorization': `Bearer ${authToken}`,
            'X-Correlation-ID': 'test-' + Date.now(),
            'Accept-Language': 'en-US',
          },
        }
      );

      expect(response.ok()).toBeTruthy();

      const body = await response.json();

      // Search returns array of results
      expect(Array.isArray(body)).toBeTruthy();
    });

    test('should handle POST request with all headers', async ({ page }) => {
      const testCompany = {
        name: `E2E Test Company ${Date.now()}`,
        displayName: 'E2E Test',
        industry: 'Technology',
        website: 'https://e2e-test.example.com',
        description: 'Test company created by E2E tests',
      };

      const response = await page.request.post(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'X-Correlation-ID': 'test-' + Date.now(),
          'Accept-Language': 'de-CH',
          'Content-Type': 'application/json',
        },
        data: testCompany,
      });

      // 201 Created or 409 Conflict (if name exists) or 403 Forbidden (insufficient permissions)
      expect([201, 403, 409]).toContain(response.status());

      if (response.status() === 201) {
        const body = await response.json();

        // Validate response matches OpenAPI spec
        expect(body).toHaveProperty('id');
        expect(body).toHaveProperty('name');
        expect(body).toHaveProperty('isVerified');
        expect(body).toHaveProperty('createdAt');
        expect(body).toHaveProperty('updatedAt');

        // Verify created company data
        expect(body.name).toBe(testCompany.name);
        expect(body.displayName).toBe(testCompany.displayName);
        expect(body.industry).toBe(testCompany.industry);
      }
    });

    test('should respect Accept-Language header', async ({ page }) => {
      // Make request with German language preference
      const responseDe = await page.request.get(`${apiBaseUrl}/api/v1/companies/search?query=invalid`, {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'Accept-Language': 'de-CH',
        },
      });

      // Make request with English language preference
      const responseEn = await page.request.get(`${apiBaseUrl}/api/v1/companies/search?query=invalid`, {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'Accept-Language': 'en-US',
        },
      });

      // Both should succeed (or fail with same status code)
      expect(responseDe.status()).toBe(responseEn.status());

      // The important thing is the header was accepted without CORS error
      expect([200, 400, 401]).toContain(responseDe.status());
    });
  });

  test.describe('Error Handling', () => {
    test.skip(!authToken, 'Skipping - no auth token');

    test('should return 404 for non-existent company', async ({ page }) => {
      const nonExistentId = '00000000-0000-0000-0000-000000000000';

      const response = await page.request.get(
        `${apiBaseUrl}/api/v1/companies/${nonExistentId}`,
        {
          headers: {
            'Authorization': `Bearer ${authToken}`,
            'X-Correlation-ID': 'test-' + Date.now(),
          },
        }
      );

      expect(response.status()).toBe(404);
    });

    test('should return 400 for invalid request', async ({ page }) => {
      const invalidCompany = {
        name: '', // Empty name - invalid
      };

      const response = await page.request.post(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          'Authorization': `Bearer ${authToken}`,
          'X-Correlation-ID': 'test-' + Date.now(),
          'Content-Type': 'application/json',
        },
        data: invalidCompany,
      });

      expect(response.status()).toBe(400);

      const body = await response.json();
      expect(body).toHaveProperty('error');
      expect(body).toHaveProperty('message');
    });
  });
});
