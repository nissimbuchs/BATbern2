/**
 * Story 17.0d: the fixture-backed defaults of the MSW harness. One test per row of the I/O
 * matrix. The calls go through the real `apiClient` and the real service functions.
 */
import { describe, expect, it } from 'vitest';
import apiClient from '@/services/api/apiClient';
import { listUsers, searchUsers } from '@/services/api/userManagementApi';
import { namesakes, people, REGISTRATION_STATUSES, shapes, toSearchResult } from '@/test/fixtures';
import { api } from './api';
import { searchFold } from './fixtureDefaults';

describe('MSW harness fixture defaults', () => {
  it('answers GET /users page 2 from the fixture people, with real paging', async () => {
    const page1 = await listUsers({}, { page: 1, limit: 20 });
    const page2 = await listUsers({}, { page: 2, limit: 20 });

    expect(page1.pagination).toMatchObject({
      page: 1,
      limit: 20,
      totalItems: people.length,
      hasNext: true,
      hasPrev: false,
    });
    expect(page1.pagination.totalItems).toBeGreaterThan(20);
    expect(page2.pagination).toMatchObject({ page: 2, hasNext: false, hasPrev: true });
    expect(page2.data.map((u) => u.id)).toEqual(people.slice(20, 40).map((u) => u.id));
    expect(api.calls('listUsers')[1].url.search).toBe('?page=2&limit=20');
  });

  it('searches folded: case, accents and ü = ue', async () => {
    const umlaut = await searchUsers('Stürmer', 20);
    const spelled = await searchUsers('stuermer', 20);
    const listed = await listUsers({ searchQuery: 'STÜRMER' }, { page: 1, limit: 20 });

    expect(umlaut.map((u) => u.id)).toEqual([shapes.stuermer.id]);
    expect(spelled).toEqual(umlaut);
    expect(listed.data.map((u) => u.id)).toEqual([shapes.stuermer.id]);
    expect(listed.pagination.totalItems).toBe(1);
  });

  it('caps a namesake search at the limit while more exist', async () => {
    const result = await searchUsers('Matthias', 20);

    expect(namesakes.length).toBeGreaterThan(20);
    expect(result).toHaveLength(20);
    expect(api.calls('searchUsers')[0].query.get('limit')).toBe('20');
  });

  it('requires every word of the query, in any order', async () => {
    expect((await searchUsers('Keller Matthias', 20)).map((u) => u.id)).toEqual([
      'matthias.keller',
      'matthias.keller.2',
    ]);
    expect(await searchUsers('Keller Nobody', 20)).toEqual([]);
  });

  it('applies the role of a search on the server side', async () => {
    const result = await searchUsers('Keller', 20, 'SPEAKER');

    expect(result.map((u) => u.id)).toEqual(['matthias.keller.2']);
  });

  it('lets an override win over the fixtures', async () => {
    api.on('searchUsers', () => [toSearchResult(shapes.cjk)]);

    expect((await searchUsers('Stürmer', 20)).map((u) => u.id)).toEqual([shapes.cjk.id]);
  });

  it('answers GET /events with past and future events, archived ones only on request', async () => {
    const current = (await apiClient.get('/events')).data;
    const all = (await apiClient.get('/events', { params: { includeArchived: true } })).data;

    const now = Date.now();
    expect(current.data.some((e: { date: string }) => Date.parse(e.date) > now)).toBe(true);
    expect(
      current.data.some((e: { workflowState: string }) => e.workflowState === 'ARCHIVED')
    ).toBe(false);
    expect(all.pagination.totalItems).toBeGreaterThan(current.pagination.totalItems);
  });

  it('answers GET /events/{code}/registrations with every status, for that event', async () => {
    const { data } = (await apiClient.get('/events/BATbern57/registrations')).data;

    expect(data.map((r: { status: string }) => r.status)).toEqual(
      expect.arrayContaining(REGISTRATION_STATUSES)
    );
    expect(data.every((r: { eventCode: string }) => r.eventCode === 'BATbern57')).toBe(true);
  });

  it('folds like batbern_fold', () => {
    expect(searchFold('  Stürmer ')).toBe('sturmer');
    expect(searchFold('Stuermer')).toBe('sturmer');
    expect(searchFold('Jean-Luc O’Neill')).toBe('jean luc o neill');
    expect(searchFold('Hông Ân'.normalize('NFD'))).toBe('hong an');
  });
});
