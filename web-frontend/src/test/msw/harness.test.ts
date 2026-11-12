/**
 * Story 17.0c1: the MSW harness started in `src/test/setup.ts`. One test per row of the I/O
 * matrix, positive control first. The calls go through the real `apiClient` (test baseURL `/api`)
 * or through `fetch`, never through a mock.
 */
import { describe, expect, it } from 'vitest';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { HttpResponse } from 'msw';
import apiClient from '@/services/api/apiClient';
import { api, harnessState, SpecViolationError } from './api';
import { harness } from './server';

const listCompaniesExample = () => {
  const op = [...harness().byId.values()].find((o) => o.key === 'listCompanies');
  const media = op?.success[0].media;
  return media?.example ?? Object.values<{ value: unknown }>(media?.examples ?? {})[0]?.value;
};

describe('MSW harness', () => {
  it('positive control: a spec’d GET /companies answers 200 with the spec example', async () => {
    const response = await apiClient.get('/companies');

    expect(response.status).toBe(200);
    expect(response.data).toEqual(listCompaniesExample());
    expect(response.data.data.length).toBeGreaterThan(0);
    expect(api.calls('listCompanies')).toHaveLength(1);
  });

  it('fails a request to a path no spec defines with the matcher’s message', async () => {
    await expect(apiClient.get('/no-such-thing')).rejects.toThrow();

    expect(api.takeViolations()).toEqual([
      'GET /api/no-such-thing: no spec defines the path /no-such-thing',
    ]);
  });

  it('fails a method the path does not define', async () => {
    await expect(apiClient.delete('/uploads/company-logo/import')).rejects.toThrow();

    expect(api.takeViolations()).toEqual([
      'DELETE /api/uploads/company-logo/import: method DELETE is not defined for /uploads/company-logo/import (the spec has POST)',
    ]);
  });

  it('fails a query value above the spec maximum, and an undeclared query parameter', async () => {
    await expect(apiClient.get('/companies', { params: { limit: 1000 } })).rejects.toThrow();
    await expect(apiClient.get('/companies', { params: { nosuch: 1 } })).rejects.toThrow();

    expect(api.takeViolations()).toEqual([
      "GET /api/companies?limit=1000: query parameter 'limit=1000' exceeds the maximum 100 of listCompanies",
      "GET /api/companies?nosuch=1: query parameter 'nosuch' is not declared by listCompanies",
    ]);
  });

  it('fails a request to another host unless the test adds its own handler', async () => {
    await expect(fetch('https://bucket.example/upload')).rejects.toThrow();

    expect(api.takeViolations()[0]).toContain('not an /api request');
  });

  it('makes the test fail afterwards, even when the code swallowed the error', async () => {
    await apiClient.get('/no-such-thing').catch(() => undefined);

    expect(() => harnessState.assertNoViolations()).toThrow(SpecViolationError);
    expect(api.takeViolations()).toEqual([]);
  });

  it('answers a typed override, and records the call with its query and path parameters', async () => {
    api.on('listCompanies', ({ query }) => ({
      data: [
        {
          name: 'acme',
          displayName: `Acme ${query.get('search')}`,
          isVerified: true,
          createdAt: '2026-05-14T16:00:00Z',
          updatedAt: '2026-05-14T16:00:00Z',
          createdBy: 'lead.organizer',
        },
      ],
      pagination: {
        page: 1,
        limit: 20,
        totalItems: 1,
        totalPages: 1,
        hasNext: false,
        hasPrev: false,
      },
    }));

    const response = await apiClient.get('/companies', { params: { search: 'Müller' } });

    expect(response.data.data[0].displayName).toBe('Acme Müller');
    expect(api.calls('listCompanies')[0].query.get('search')).toBe('Müller');

    await apiClient.get('/companies/acme');
    expect(api.calls('getCompany')[0].params).toEqual({ name: 'acme' });
  });

  it('answers an override that returns a Response (an error status)', async () => {
    api.on('getCompany', () => HttpResponse.json({ message: 'not found' }, { status: 404 }));

    await expect(apiClient.get('/companies/none')).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it('passes the parsed JSON request body to the override', async () => {
    api.on(
      'createCompany',
      ({ body }) => ({
        name: body?.name ?? 'missing',
        displayName: body?.displayName ?? 'missing',
        isVerified: false,
        createdAt: '2026-05-14T16:00:00Z',
        updatedAt: '2026-05-14T16:00:00Z',
        createdBy: 'lead.organizer',
      }),
      { status: 201 }
    );

    const response = await apiClient.post('/companies', { name: 'acme', displayName: 'Acme AG' });

    expect(response.status).toBe(201);
    expect(response.data).toMatchObject({ name: 'acme', displayName: 'Acme AG' });
  });

  it('answers a bodiless success with its status and no body', async () => {
    const response = await apiClient.delete('/companies/acme');

    expect(response.status).toBe(204);
    expect(response.data).toBe('');
  });

  it('resolves /api/... and /api/v1/... to the same operation', async () => {
    await fetch('http://localhost:3000/api/companies');
    await fetch('http://localhost:3000/api/v1/companies');
    await apiClient.get('/v1/companies');

    expect(api.calls('listCompanies')).toHaveLength(3);
  });

  it('qualifies an operationId two specs define by its spec', async () => {
    await apiClient.get('/topics');
    await apiClient.get('/partners/topics');

    expect(api.calls('topics-api/listTopics')).toHaveLength(1);
    expect(api.calls('partners-api/listTopics')).toHaveLength(1);
  });

  it('type-checks override bodies (npm run type-check, not this run)', () => {
    const typeOnly = () => {
      // @ts-expect-error -- 'pagination' is required in PaginatedCompanyResponse
      api.on('listCompanies', () => ({ data: [] }));
      // @ts-expect-error -- 'createdAt' and 'updatedAt' are required in CompanyResponse
      api.on('getCompany', () => ({ name: 'acme', isVerified: true }));
      // @ts-expect-error -- no such operation
      api.on('noSuchOperation', () => ({}));
    };
    expect(typeof typeOnly).toBe('function');
  });

  it('records a failure inside the harness as a violation, not a 500 the code could swallow', async () => {
    api.on('listCompanies', () => {
      throw new Error('resolver broke');
    });

    // A network error (no response), not an HTTP 500 the client could treat as an answer
    await expect(apiClient.get('/companies')).rejects.toMatchObject({ code: 'ERR_NETWORK' });

    expect(api.takeViolations()).toEqual([
      'GET http://localhost:3000/api/companies: MSW harness failed: resolver broke',
    ]);
  });
});

describe('tsconfig.test-harness.json', () => {
  const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

  function sourceFiles(dir: string): string[] {
    return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
      const path = join(dir, e.name);
      if (e.isDirectory()) return sourceFiles(path);
      return /\.tsx?$/.test(e.name) ? [path] : [];
    });
  }

  it('type-checks every file that uses the harness API (typed overrides only count when checked)', () => {
    // All of src is type-checked, minus a shrinking list of tests that do not compile yet
    // (scripts/check-test-typecheck.ts); a harness user may not be on that list.
    const config: { include: string[]; exclude: string[] } = JSON.parse(
      readFileSync(join(root, 'tsconfig.test-harness.json'), 'utf8')
    );
    const users = sourceFiles(join(root, 'src'))
      .map((f) => relative(root, f).split('\\').join('/'))
      .filter((f) => !f.startsWith('src/test/msw/'))
      .filter((f) =>
        /from '(@\/test\/msw\/api|[./]+\/test\/msw\/api)'/.test(readFileSync(join(root, f), 'utf8'))
      );

    expect(users.length).toBeGreaterThan(5); // positive control: the scan finds the users
    expect(config.include).toEqual(['src']);
    expect(users.filter((f) => config.exclude.includes(f))).toEqual([]);
  });
});
