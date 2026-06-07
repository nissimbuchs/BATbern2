/**
 * Companies API Integration — slice 2 / companies (plan §C)
 * docs/plans/playwright-staging-hardening.md
 *
 * End-to-end validation of the full request flow:
 *   Frontend → AWS API Gateway → Spring Boot Gateway → Company Service
 * (CORS, authentication, public-vs-authenticated access, API contract shape).
 *
 * Hardened 2026-05-30 to the quality bar. THE prod-residue fix this slice exists for: the
 * old "should handle POST request with all headers" created `E2E Test Company ${Date.now()}`
 * — a NON-canonical name (not swept by `BRUNOTESTCO%`) — and NEVER deleted it, leaking one
 * company per run onto staging (= prod). The exact residue the Bruno audit found. Now the
 * POST uses `factory.companyName()` (`BRUNOTESTCO<ts>`) and the created row is explicit-
 * deleted in afterEach (`cleanupById`), with the canonical global-teardown sweep as backstop.
 *
 * These are read-only contract checks except the single POST (mutating, cleaned up). Tagged
 * `@gate`; the slice's mutating `@smoke` is the UI create flow in company-creation.spec.ts.
 */

import { test, expect } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { cleanupById } from '../helpers/test-fixtures-cleanup';

test.describe('Companies API Integration', { tag: '@gate' }, () => {
  const apiBaseUrl = process.env.E2E_API_URL || 'https://api.batbern.ch';
  const authToken = process.env.AUTH_TOKEN;

  // Companies created by the POST test this run — explicit-deleted in afterEach.
  const createdNames: string[] = [];

  test.afterEach(async () => {
    while (createdNames.length > 0) {
      const name = createdNames.pop();
      if (name) {
        await cleanupById(authToken || '', 'companies', name);
      }
    }
  });

  test.describe('Unauthenticated Requests', () => {
    test('should allow unauthenticated list companies request (public endpoint)', async ({
      page,
    }) => {
      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          'X-Correlation-ID': 'test-' + Date.now(),
          'Accept-Language': 'de-CH',
        },
      });

      expect(response.status()).toBe(200);

      const data = await response.json();
      expect(data).toHaveProperty('data');
      expect(data).toHaveProperty('pagination');
      expect(Array.isArray(data.data)).toBe(true);
    });

    test('should allow unauthenticated search request (public endpoint)', async ({ page }) => {
      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies/search?query=test`, {
        headers: {
          'X-Correlation-ID': 'test-' + Date.now(),
        },
      });

      expect(response.status()).toBe(200);

      const data = await response.json();
      expect(data).toBeDefined();
      expect(Array.isArray(data)).toBe(true);
    });
  });

  test.describe('Authenticated Requests', () => {
    test.skip(!authToken, 'Skipping authenticated tests - no auth token provided');

    test('should list companies with pagination', async ({ page }) => {
      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies?page=1&limit=10`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'X-Correlation-ID': 'test-' + Date.now(),
          'Accept-Language': 'de-CH',
          'Content-Type': 'application/json',
        },
      });

      expect(response.ok()).toBeTruthy();

      const body = await response.json();

      expect(body).toHaveProperty('data');
      expect(body).toHaveProperty('pagination');
      expect(Array.isArray(body.data)).toBeTruthy();

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
            Authorization: `Bearer ${authToken}`,
            'X-Correlation-ID': 'test-' + Date.now(),
            'Accept-Language': 'en-US',
          },
        }
      );

      expect(response.ok()).toBeTruthy();

      const body = await response.json();
      expect(Array.isArray(body)).toBeTruthy();
    });

    test('should create a company with all headers (and clean it up)', async ({ page }) => {
      const name = factory.companyName(); // BRUNOTESTCO<ts> — swept by BRUNOTESTCO%
      const testCompany = {
        name,
        displayName: 'BAT PW E2E',
        industry: 'Technology',
        website: 'https://e2e-test.example.com',
        description: 'Playwright slice-2 company fixture — auto-deleted in afterEach.',
      };

      const response = await page.request.post(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'X-Correlation-ID': 'test-' + Date.now(),
          'Accept-Language': 'de-CH',
          'Content-Type': 'application/json',
        },
        data: testCompany,
      });

      // 201 Created, or 409 Conflict (name already exists), or 403 (insufficient permissions).
      expect([201, 403, 409]).toContain(response.status());

      if (response.status() === 201) {
        createdNames.push(name); // ensure teardown deletes the real row we just created
        const body = await response.json();

        expect(body).toHaveProperty('name');
        expect(body).toHaveProperty('isVerified');
        expect(body).toHaveProperty('createdAt');
        expect(body).toHaveProperty('updatedAt');
        expect(body).toHaveProperty('createdBy');

        expect(body.name).toBe(testCompany.name);
        expect(body.displayName).toBe(testCompany.displayName);
        expect(body.industry).toBe(testCompany.industry);
      }
    });

    test('should respect Accept-Language header', async ({ page }) => {
      const responseDe = await page.request.get(
        `${apiBaseUrl}/api/v1/companies/search?query=invalid`,
        {
          headers: {
            Authorization: `Bearer ${authToken}`,
            'Accept-Language': 'de-CH',
          },
        }
      );

      const responseEn = await page.request.get(
        `${apiBaseUrl}/api/v1/companies/search?query=invalid`,
        {
          headers: {
            Authorization: `Bearer ${authToken}`,
            'Accept-Language': 'en-US',
          },
        }
      );

      expect(responseDe.status()).toBe(responseEn.status());
      expect([200, 400, 401]).toContain(responseDe.status());
    });
  });

  test.describe('Error Handling', () => {
    test.skip(!authToken, 'Skipping - no auth token');

    test('should return 404 for non-existent company', async ({ page }) => {
      const nonExistentId = '00000000-0000-0000-0000-000000000000';

      const response = await page.request.get(`${apiBaseUrl}/api/v1/companies/${nonExistentId}`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
          'X-Correlation-ID': 'test-' + Date.now(),
        },
      });

      expect(response.status()).toBe(404);
    });

    test('should return 400 for invalid request', async ({ page }) => {
      const invalidCompany = {
        name: '', // Empty name - invalid (no row created)
      };

      const response = await page.request.post(`${apiBaseUrl}/api/v1/companies`, {
        headers: {
          Authorization: `Bearer ${authToken}`,
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
