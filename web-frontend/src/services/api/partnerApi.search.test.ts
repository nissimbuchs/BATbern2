/**
 * Partner list search: the search term goes to the backend, which filters every
 * partner before paging. The MSW harness (src/test/msw) answers the real HTTP call and checks it
 * against the spec.
 */
import { describe, it, expect, beforeEach } from 'vitest';
import { api } from '@/test/msw/api';
import { listPartners } from './partnerApi';

beforeEach(() => {
  api.on('listPartners', () => ({
    data: [],
    metadata: {
      page: 0,
      limit: 20,
      totalItems: 0,
      totalPages: 0,
      hasNext: false,
      hasPrev: false,
    },
  }));
});

const sort = { sortBy: 'name', sortOrder: 'asc' } as const;

describe('listPartners', () => {
  it('sends the trimmed search with the filters and the page', async () => {
    await listPartners({ tier: 'GOLD', status: 'active' }, sort, { page: 1, size: 20 }, ' Müller ');

    const { query } = api.calls('listPartners')[0];
    expect(query.get('search')).toBe('Müller');
    expect(query.get('filter')).toBe('partnershipLevel:GOLD,isActive:true');
    expect(query.get('page')).toBe('1');
    expect(query.get('size')).toBe('20');
  });

  it('sends no search when the term is blank or absent', async () => {
    await listPartners({ tier: 'all', status: 'all' }, sort, { page: 0, size: 20 }, '  ');
    await listPartners({ tier: 'all', status: 'all' }, sort, { page: 0, size: 20 });

    expect(api.calls('listPartners').map((c) => c.query.has('search'))).toEqual([false, false]);
  });
});
