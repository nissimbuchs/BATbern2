/**
 * Duplicate checks of the batch imports read every user or company. GET /users and GET /companies
 * cap `limit` at 100 (#1072 class): the modals used to ask for `limit=1000`, which the spec (and
 * CUMS) refuses. They now page through at the spec maximum. Found by the MSW harness (17.0c1).
 */
import { describe, it, expect } from 'vitest';
import { api } from '@/test/msw/api';
import { listAllUsers } from './userManagementApi';
import { companyApiClient } from './companyApi';
import { aUser } from '@/test/fixtures';

const pagination = (page: number, totalPages: number) => ({
  page,
  limit: 100,
  totalItems: totalPages * 100,
  totalPages,
  hasNext: page < totalPages,
  hasPrev: page > 1,
});

const user = (id: string) => ({
  ...aUser(),
  id,
  email: `${id}@example.com`,
  firstName: 'Anna',
  lastName: 'Muster',
  roles: ['SPEAKER' as const],
  createdAt: '2026-05-14T16:00:00Z',
  updatedAt: '2026-05-14T16:00:00Z',
  active: true,
});

const company = (name: string) => ({
  name,
  isVerified: true,
  createdAt: '2026-05-14T16:00:00Z',
  updatedAt: '2026-05-14T16:00:00Z',
  createdBy: 'lead.organizer',
});

describe('listAllUsers', () => {
  it('reads every page at the spec maximum and keeps the filter', async () => {
    api.on('listUsers', ({ query }) => {
      const page = Number(query.get('page'));
      return { data: [user(`user.p${page}`)], pagination: pagination(page, 3) };
    });

    const result = await listAllUsers({ role: ['SPEAKER'] });

    expect(result.data.map((u) => u.id)).toEqual(['user.p1', 'user.p2', 'user.p3']);
    const calls = api.calls('listUsers');
    expect(calls.map((c) => c.query.get('page'))).toEqual(['1', '2', '3']);
    expect(calls.every((c) => c.query.get('limit') === '100')).toBe(true);
    expect(calls[0].query.get('filter')).toBe('{"role":"SPEAKER"}');
  });
});

describe('paging guard', () => {
  it('stops after 100 pages when the server keeps saying hasNext', async () => {
    api.on('listUsers', () => ({ data: [user('same')], pagination: pagination(1, 9999) }));

    const result = await listAllUsers({});

    expect(api.calls('listUsers')).toHaveLength(100);
    expect(api.calls('listUsers')[99].query.get('page')).toBe('100');
    expect(result.data).toHaveLength(100);
  });
});

describe('companyApiClient.getAllCompanies', () => {
  it('reads every page at the spec maximum', async () => {
    api.on('listCompanies', ({ query }) => {
      const page = Number(query.get('page'));
      return { data: [company(`co${page}`)], pagination: pagination(page, 2) };
    });

    const result = await companyApiClient.getAllCompanies();

    expect(result.data.map((c) => c.name)).toEqual(['co1', 'co2']);
    expect(api.calls('listCompanies').map((c) => c.query.get('limit'))).toEqual(['100', '100']);
  });
});
