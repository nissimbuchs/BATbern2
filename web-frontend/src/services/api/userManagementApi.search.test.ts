/**
 * searchUsers on the MSW harness and the production-shaped people (Story 17.0d, #1034).
 *
 * The former tests used axios-mock-adapter, which compares the `params` object, not the URL on the
 * wire, with an ASCII-only query ('Anna'). That is how the double encoding of #1034 survived:
 * typing an umlaut surname sent a literal `%25C3%25BC` for `ü` and matched nobody. The harness
 * sees the real request, and answers from the fixture people (>20 "Matthias", umlauts).
 */
import { describe, expect, it } from 'vitest';
import { HttpResponse } from 'msw';
import { api } from '@/test/msw/api';
import { namesakes, shapes } from '@/test/fixtures';
import { searchUsers } from './userManagementApi';

describe('searchUsers', () => {
  it('should_findTheUmlautSurname_when_queryContainsUmlaut', async () => {
    const result = await searchUsers('Brünnimann', 20);

    const [call] = api.calls('searchUsers');
    // Encoded once by axios: 'ü' is %C3%BC on the wire, never %25C3%25BC.
    expect(call.url.search).toContain('query=Br%C3%BCnnimann');
    expect(call.url.search).not.toContain('%25');
    expect(call.query.get('query')).toBe('Brünnimann');
    expect(result.map((u) => u.id)).toEqual([shapes.bruennimann.id]);
  });

  it('should_sendTheFullNameOnce_when_queryContainsSpace', async () => {
    const result = await searchUsers('Matthias Brünnimann', 20);

    const [call] = api.calls('searchUsers');
    expect(call.url.search).not.toContain('%25');
    expect(call.query.get('query')).toBe('Matthias Brünnimann');
    expect(result.map((u) => u.id)).toEqual([shapes.bruennimann.id]);
  });

  it('should_returnAtMostTheLimit_when_moreNamesakesMatch', async () => {
    const result = await searchUsers('Matthias', 20);

    expect(namesakes.length).toBeGreaterThan(20);
    expect(result).toHaveLength(20);
    expect(api.calls('searchUsers')[0].query.get('limit')).toBe('20');
  });

  it('should_sendTheDefaultLimitOf10_when_noLimitGiven', async () => {
    await searchUsers('Keller');

    expect(api.calls('searchUsers')[0].query.get('limit')).toBe('10');
  });

  it('should_passRoleToBackend_when_roleFilterProvided', async () => {
    const result = await searchUsers('Keller', 20, 'SPEAKER');

    expect(api.calls('searchUsers')[0].query.get('role')).toBe('SPEAKER');
    expect(result.map((u) => u.id)).toEqual([shapes.identicalNames[1].id]);
  });

  it('should_omitRoleParam_when_noRoleFilterProvided', async () => {
    const result = await searchUsers('Keller', 20);

    // Positive control: without the role, the other Kellers come back too.
    expect(result.length).toBeGreaterThan(1);
    expect(api.calls('searchUsers')[0].query.has('role')).toBe(false);
  });

  it('should_returnEmptyArray_when_noResultsFound', async () => {
    expect(await searchUsers('NonExistent', 20)).toEqual([]);
  });

  it('should_reject_when_theServerFails', async () => {
    api.on('searchUsers', () => HttpResponse.json({ message: 'boom' }, { status: 500 }));

    await expect(searchUsers('Keller', 20)).rejects.toMatchObject({ response: { status: 500 } });
  });
});
