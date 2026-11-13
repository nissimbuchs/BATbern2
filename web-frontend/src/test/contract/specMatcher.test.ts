/**
 * Story 17.3: the spec matcher. Fixture specs only, except the one smoke test at the end that
 * loads the real `docs/api/`.
 */
import { describe, expect, it } from 'vitest';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadSpecIndex, matchOperation } from './specMatcher.ts';

const here = dirname(fileURLToPath(import.meta.url));
const index = loadSpecIndex(join(here, '__fixtures__', 'specs'));

describe('specMatcher', () => {
  it('skips the _-prefixed shared specs', () => {
    expect(index.operations.map((o) => o.operationId)).not.toContain('neverLoaded');
    expect(matchOperation(index, 'GET', '/shared/never').ok).toBe(false);
  });

  it('matches a parameterized path and names file and operation', () => {
    expect(matchOperation(index, 'GET', '/events/BATbern1/registrations')).toEqual({
      ok: true,
      file: 'events.openapi.yml',
      operationId: 'listRegistrations',
      path: '/events/{eventCode}/registrations',
    });
  });

  it('matches a literal me or _global in a parameter position', () => {
    expect(matchOperation(index, 'GET', '/users/me')).toMatchObject({ operationId: 'getUser' });
    expect(matchOperation(index, 'GET', '/events/_global')).toMatchObject({
      operationId: 'getEvent',
    });
  });

  it('prefers the literal path over a parameter', () => {
    expect(matchOperation(index, 'GET', '/events/current')).toMatchObject({
      operationId: 'getCurrentEvent',
    });
    expect(matchOperation(index, 'GET', '/users/me/settings')).toMatchObject({
      operationId: 'getMySettings',
    });
  });

  it('strips a leading /api/v1, an origin and a trailing slash', () => {
    expect(matchOperation(index, 'GET', '/api/v1/events/BATbern1/registrations/').ok).toBe(true);
    expect(matchOperation(index, 'GET', 'http://localhost/api/v1/users/me').ok).toBe(true);
  });

  it('strips a bare /api only with bareApiPrefix (the MSW harness, 17.0c1)', () => {
    const bare = { bareApiPrefix: true };
    expect(matchOperation(index, 'GET', '/api/users/me')).toMatchObject({
      ok: false,
      kind: 'path',
    });
    expect(matchOperation(index, 'GET', '/api/users/me', bare)).toMatchObject({
      ok: true,
      operationId: 'getUser',
    });
    expect(matchOperation(index, 'GET', 'http://localhost:3000/api/users/me', bare).ok).toBe(true);
    expect(matchOperation(index, 'GET', '/api/v1/users/me', bare).ok).toBe(true);
    // Only a whole segment: `/apiary` is not `/api`.
    expect(matchOperation(index, 'GET', '/apiary/users/me', bare).ok).toBe(false);
  });

  it('treats {*} as an unresolved value', () => {
    expect(matchOperation(index, 'GET', '/events/{*}/registrations')).toMatchObject({ ok: true });
    expect(matchOperation(index, 'GET', '/events/BAT{*}/registrations')).toMatchObject({
      ok: true,
    });
    expect(matchOperation(index, 'GET', '/events/{*}/registrations?limit={*}&{*}=x')).toMatchObject(
      { ok: true }
    );
  });

  it('is method-aware: a wrong method fails and the reason names it', () => {
    const r = matchOperation(index, 'DELETE', '/events/BATbern1/publish/schedule');
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.kind).toBe('method');
      expect(r.reason).toContain('DELETE');
      expect(r.reason).toContain('GET');
    }
  });

  it('fails an unknown path', () => {
    expect(matchOperation(index, 'GET', '/nowhere')).toMatchObject({ ok: false, kind: 'path' });
  });

  it('fails the getPublishPreview repro', () => {
    expect(matchOperation(index, 'GET', '/events/{*}/publish/{*}/preview')).toMatchObject({
      ok: false,
      kind: 'path',
    });
  });

  it('fails an unknown literal query parameter and names it', () => {
    const r = matchOperation(index, 'GET', '/events/BATbern1/registrations?foo=1');
    expect(r).toMatchObject({ ok: false, kind: 'query' });
    if (!r.ok) expect(r.reason).toContain("'foo'");
  });

  it('fails a value above the declared maximum (#1072) and below the minimum', () => {
    expect(matchOperation(index, 'GET', '/events/BATbern1/registrations?limit=1000')).toMatchObject(
      { ok: false, kind: 'query' }
    );
    expect(matchOperation(index, 'GET', '/events/BATbern1/registrations?limit=0')).toMatchObject({
      ok: false,
      kind: 'query',
    });
    expect(matchOperation(index, 'GET', '/events/BATbern1/registrations?limit=100').ok).toBe(true);
  });

  it('checks enum values, also through a $ref and a comma list', () => {
    expect(
      matchOperation(index, 'GET', '/events/BATbern1/registrations?status=CONFIRMED,REGISTERED').ok
    ).toBe(true);
    expect(
      matchOperation(index, 'GET', '/events/BATbern1/registrations?status=BOGUS')
    ).toMatchObject({ ok: false, kind: 'query' });
  });

  it('leaves the query unconstrained when a parameter is a cross-file $ref', () => {
    expect(matchOperation(index, 'GET', '/users/export?format=csv').ok).toBe(true);
  });

  it('does not split a comma value of a scalar enum parameter', () => {
    expect(matchOperation(index, 'GET', '/users/sorted?order=asc').ok).toBe(true);
    expect(matchOperation(index, 'GET', '/users/sorted?order=asc,desc')).toMatchObject({
      ok: false,
      kind: 'query',
    });
  });

  it('accepts any name for a free-form object parameter', () => {
    expect(matchOperation(index, 'GET', '/users/search?anything=1').ok).toBe(true);
  });
});

describe('specMatcher on the real docs/api (smoke)', () => {
  it('loads the public operations (a floor, not a count)', () => {
    const real = loadSpecIndex(join(here, '..', '..', '..', '..', 'docs', 'api'));
    // 287 on 2026-11-11 after Story 4.10c folded batch_registrations, enroll-stakeholders and
    // the /newsletter/my-subscription pair into their collections. 278 on 2026-11-13 after Story
    // 17.11a removed the three profile picture upload operations (uploads-api took them over).
    // 270 on 2026-11-13 after Stories 17.11b-d removed the photo, teaser and material uploads.
    expect(real.operations.length).toBeGreaterThan(260);
    expect(matchOperation(real, 'GET', '/users/me').ok).toBe(true);
  });
});
