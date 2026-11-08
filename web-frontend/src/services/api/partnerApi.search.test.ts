/**
 * Partner list search: the search term goes to the backend, which filters every
 * partner before paging. MSW answers the real HTTP call.
 */
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { listPartners } from './partnerApi';

let requests: URL[] = [];

const server = setupServer(
  http.get('*/partners', ({ request }) => {
    requests.push(new URL(request.url));
    return HttpResponse.json({
      data: [],
      metadata: {
        page: 0,
        limit: 20,
        totalItems: 0,
        totalPages: 0,
        hasNext: false,
        hasPrev: false,
      },
    });
  })
);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  requests = [];
});
afterAll(() => server.close());

const sort = { sortBy: 'name', sortOrder: 'asc' } as const;

describe('listPartners', () => {
  it('sends the trimmed search with the filters and the page', async () => {
    await listPartners({ tier: 'GOLD', status: 'active' }, sort, { page: 1, size: 20 }, ' Müller ');

    const params = requests[0].searchParams;
    expect(params.get('search')).toBe('Müller');
    expect(params.get('filter')).toBe('partnershipLevel:GOLD,isActive:true');
    expect(params.get('page')).toBe('1');
    expect(params.get('size')).toBe('20');
  });

  it('sends no search when the term is blank or absent', async () => {
    await listPartners({ tier: 'all', status: 'all' }, sort, { page: 0, size: 20 }, '  ');
    await listPartners({ tier: 'all', status: 'all' }, sort, { page: 0, size: 20 });

    expect(requests.map((r) => r.searchParams.has('search'))).toEqual([false, false]);
  });
});
