/**
 * Company list search: the list's search box goes to GET /companies?search=, which
 * filters every company before paging, so totalItems and page 2 are the backend's. The former
 * wrapper called the autocomplete and faked one page. The MSW harness (src/test/msw) answers the
 * real HTTP call and checks it against the spec.
 */
import { describe, it, expect } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { companyApiClient } from './companyApi';

const answerPageTwo = () =>
  api.on('listCompanies', () => ({
    data: [
      {
        name: 'bundesamtfurx',
        displayName: 'Bundesamt für X',
        isVerified: false,
        createdAt: '2026-05-14T16:00:00Z',
        updatedAt: '2026-05-14T16:00:00Z',
      },
    ],
    pagination: {
      page: 2,
      limit: 20,
      totalItems: 41,
      totalPages: 3,
      hasNext: true,
      hasPrev: true,
    },
  }));

describe('companyApiClient.getCompanies with a search', () => {
  it('sends search, page and limit to GET /companies and returns the backend pagination', async () => {
    answerPageTwo();
    // The autocomplete must not be used for the list any more.
    api.on('searchCompanies', () => HttpResponse.json([], { status: 500 }));

    const result = await companyApiClient.getCompanies(
      { page: 2, limit: 20 },
      { searchQuery: '  bundes für ' }
    );

    const calls = api.calls('listCompanies');
    expect(calls).toHaveLength(1);
    expect(api.calls('searchCompanies')).toHaveLength(0);
    expect(calls[0].query.get('search')).toBe('bundes für');
    expect(calls[0].query.get('page')).toBe('2');
    expect(calls[0].query.get('limit')).toBe('20');
    expect(result.pagination.totalItems).toBe(41);
    expect(result.pagination.hasNext).toBe(true);
  });

  it('combines the search with the other filters', async () => {
    answerPageTwo();

    await companyApiClient.getCompanies(
      { page: 1, limit: 20 },
      { searchQuery: 'ag', isVerified: true },
      { expand: ['logo'] }
    );

    const { query } = api.calls('listCompanies')[0];
    expect(query.get('search')).toBe('ag');
    expect(query.get('filter')).toBe('{"isVerified":true}');
    expect(query.get('include')).toBe('logo');
  });

  it('sends no search for a blank query', async () => {
    await companyApiClient.getCompanies({ page: 1, limit: 20 }, { searchQuery: '   ' });

    expect(api.calls('listCompanies')[0].query.has('search')).toBe(false);
  });
});
