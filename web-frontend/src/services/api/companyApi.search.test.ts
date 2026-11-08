/**
 * Company list search: the list's search box goes to GET /companies?search=, which
 * filters every company before paging, so totalItems and page 2 are the backend's. The former
 * wrapper called the autocomplete and faked one page. MSW answers the real HTTP call.
 */
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { companyApiClient } from './companyApi';

let requests: URL[] = [];

const server = setupServer(
  http.get('*/companies', ({ request }) => {
    requests.push(new URL(request.url));
    return HttpResponse.json({
      data: [{ name: 'bundesamtfurx', displayName: 'Bundesamt für X' }],
      pagination: {
        page: 2,
        limit: 20,
        totalItems: 41,
        totalPages: 3,
        hasNext: true,
        hasPrev: true,
      },
    });
  }),
  http.get('*/companies/search', () => HttpResponse.json([], { status: 500 }))
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  requests = [];
});
afterAll(() => server.close());

describe('companyApiClient.getCompanies with a search', () => {
  it('sends search, page and limit to GET /companies and returns the backend pagination', async () => {
    const result = await companyApiClient.getCompanies(
      { page: 2, limit: 20 },
      { searchQuery: '  bundes für ' }
    );

    expect(requests).toHaveLength(1);
    expect(requests[0].pathname).toMatch(/\/companies$/);
    expect(requests[0].searchParams.get('search')).toBe('bundes für');
    expect(requests[0].searchParams.get('page')).toBe('2');
    expect(requests[0].searchParams.get('limit')).toBe('20');
    expect(result.pagination.totalItems).toBe(41);
    expect(result.pagination.hasNext).toBe(true);
  });

  it('combines the search with the other filters', async () => {
    await companyApiClient.getCompanies(
      { page: 1, limit: 20 },
      { searchQuery: 'ag', isVerified: true },
      { expand: ['logo'] }
    );

    expect(requests[0].searchParams.get('search')).toBe('ag');
    expect(requests[0].searchParams.get('filter')).toBe('{"isVerified":true}');
    expect(requests[0].searchParams.get('include')).toBe('logo');
  });

  it('sends no search for a blank query', async () => {
    await companyApiClient.getCompanies({ page: 1, limit: 20 }, { searchQuery: '   ' });

    expect(requests[0].searchParams.has('search')).toBe(false);
  });
});
