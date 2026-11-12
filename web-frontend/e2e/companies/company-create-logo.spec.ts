/**
 * E2E: create a company with a logo through the upload contract (Story 17.11).
 *
 * Local dev only (TEST_ENV=development): it uploads a real file to MinIO through the presigned URL.
 * The flow under test: `POST /uploads/company-logo` → PUT to the presigned URL → `POST
 * /uploads/company-logo/{uploadId}/confirm` (no target) → `POST /companies` with `logoUploadId`.
 * The network log may show no `/logos/` call: the six `/logos/*` operations are gone.
 *
 * Cleanup: the company is deleted by name in afterEach (`cleanupById`). Its ASSOCIATED upload row
 * stays until the `BRUNOTESTCO%` prefix sweep (global teardown) removes it; the S3 objects (the
 * final logo key) stay in MinIO.
 */

import { test, expect, type Request } from '@playwright/test';
import * as factory from '../helpers/test-data-factory';
import { companySlug, readOrganizerToken } from '../helpers/event-fixture';
import { cleanupById } from '../helpers/test-fixtures-cleanup';
import { stripPresignedAuthHeader } from '../helpers/strip-presigned-auth';

const API_BASE_URL = process.env.E2E_API_URL || 'http://localhost:8000';

/** A 1x1 PNG. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
  'base64'
);

test.describe('Company create with logo (Story 17.11)', () => {
  test.skip(
    (process.env.TEST_ENV || 'development') !== 'development',
    'uploads a real file: local dev only'
  );

  let token: string;
  const createdNames: string[] = [];

  test.beforeAll(() => {
    token = readOrganizerToken();
  });

  test.afterEach(async () => {
    while (createdNames.length > 0) {
      const name = createdNames.pop();
      if (name) {
        await cleanupById(token, 'companies', name);
      }
    }
  });

  test('should_attachLogo_when_companyCreatedWithUploadedLogo', async ({ page, request }) => {
    const apiCalls: string[] = [];
    page.on('request', (r: Request) => {
      const url = new URL(r.url());
      if (url.pathname.startsWith('/api/')) {
        apiCalls.push(`${r.method()} ${url.pathname}`);
      }
    });

    const name = factory.companyName();
    createdNames.push(companySlug(name));
    // The test context sends the organizer token on every request; S3 refuses a second credential
    await stripPresignedAuthHeader(page);

    await page.goto('/organizer/companies');
    await page.getByTestId('create-company-button').click();
    await expect(page.getByTestId('company-form-dialog')).toBeVisible();
    await page.getByTestId('company-name-field').fill(name);

    const confirmed = page.waitForResponse(
      (r) => /\/uploads\/company-logo\/[^/]+\/confirm$/.test(new URL(r.url()).pathname) && r.ok()
    );
    await page
      .getByTestId('file-dropzone')
      .locator('input[type="file"]')
      .setInputFiles({ name: 'logo.png', mimeType: 'image/png', buffer: PNG });
    await confirmed;
    // The preview shows the chosen file (a local object URL; no hard-coded bucket any more)
    await expect(
      page.getByTestId('company-form-dialog').locator('img[src^="blob:"]')
    ).toBeVisible();

    await page.getByTestId('submit-company-button').click();
    await expect(page.getByTestId('company-form-dialog')).toBeHidden({ timeout: 15_000 });

    // The company has its logo at the final key, and the CDN (MinIO locally) serves it
    const company = await request.get(
      `${API_BASE_URL}/api/v1/companies/${companySlug(name)}?include=logo`,
      { headers: { Authorization: `Bearer ${token}` } }
    );
    expect(company.ok()).toBe(true);
    const logoUrl: string = (await company.json()).logo.url;
    expect(logoUrl).toMatch(
      new RegExp(`/logos/\\d{4}/companies/${companySlug(name)}/logo-.+\\.png$`)
    );
    expect((await request.get(logoUrl)).ok()).toBe(true);

    // Only the upload contract was used for the file
    expect(apiCalls.filter((c) => c.includes('/logos'))).toEqual([]);
    expect(apiCalls).toEqual(
      expect.arrayContaining([
        'POST /api/v1/uploads/company-logo',
        expect.stringMatching(/^POST \/api\/v1\/uploads\/company-logo\/[^/]+\/confirm$/),
        'POST /api/v1/companies',
      ])
    );
  });
});
