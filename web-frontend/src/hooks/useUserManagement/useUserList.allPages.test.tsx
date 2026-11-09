/**
 * `allPages` of useUserList and useCompanies (17.0c1): the batch-import duplicate checks read
 * every user or company page by page at the spec maximum (`limit=100`) and merge the pages. Real
 * hooks and services; the MSW harness answers and rejects any `limit` above the spec maximum.
 */
import { describe, it, expect } from 'vitest';
import React from 'react';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { api } from '@/test/msw/api';
import { useUserList } from './useUserList';
import { useCompanies } from '@/hooks/useCompanies/useCompanies';

const wrapper = ({ children }: { children: React.ReactNode }) =>
  React.createElement(
    QueryClientProvider,
    { client: new QueryClient({ defaultOptions: { queries: { retry: false } } }) },
    children
  );

const pagination = (page: number, totalPages: number) => ({
  page,
  limit: 100,
  totalItems: totalPages * 100,
  totalPages,
  hasNext: page < totalPages,
  hasPrev: page > 1,
});

describe('useUserList with allPages', () => {
  it('requests page 1, 2, 3 at limit=100 and merges them', async () => {
    api.on('listUsers', ({ query }) => {
      const page = Number(query.get('page'));
      return {
        data: [
          {
            id: `user.p${page}`,
            email: `user.p${page}@example.com`,
            firstName: 'Anna',
            lastName: 'Muster',
            roles: ['SPEAKER' as const],
            createdAt: '2026-05-14T16:00:00Z',
            updatedAt: '2026-05-14T16:00:00Z',
          },
        ],
        pagination: pagination(page, 3),
      };
    });

    const { result } = renderHook(
      () =>
        useUserList({
          filters: { role: ['SPEAKER'] },
          pagination: { page: 1, limit: 100 },
          allPages: true,
        }),
      { wrapper }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.map((u) => u.id)).toEqual(['user.p1', 'user.p2', 'user.p3']);
    const calls = api.calls('listUsers');
    expect(calls.map((c) => [c.query.get('page'), c.query.get('limit')])).toEqual([
      ['1', '100'],
      ['2', '100'],
      ['3', '100'],
    ]);
  });
});

describe('useCompanies with allPages', () => {
  it('requests page 1, 2 at limit=100 and merges them', async () => {
    api.on('listCompanies', ({ query }) => {
      const page = Number(query.get('page'));
      return {
        data: [
          {
            name: `co${page}`,
            isVerified: true,
            createdAt: '2026-05-14T16:00:00Z',
            updatedAt: '2026-05-14T16:00:00Z',
          },
        ],
        pagination: pagination(page, 2),
      };
    });

    const { result } = renderHook(
      () => useCompanies({ page: 1, limit: 100 }, {}, { expand: [], allPages: true }),
      { wrapper }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.data.map((c) => c.name)).toEqual(['co1', 'co2']);
    expect(
      api.calls('listCompanies').map((c) => [c.query.get('page'), c.query.get('limit')])
    ).toEqual([
      ['1', '100'],
      ['2', '100'],
    ]);
  });
});
