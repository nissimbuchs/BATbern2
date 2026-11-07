/**
 * Story 17.4: the spec-diff similarity report. In-memory spec documents, plus one run against
 * this repository's git history.
 */
import { describe, expect, it } from 'vitest';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { SpecDocument } from '../src/test/contract/specMatcher.ts';
import {
  MARKER,
  NO_ADDED,
  addedOperations,
  listOperations,
  loadSpecDocumentsAt,
  nearest,
  operationKey,
  renderMarkdown,
  similarity,
} from './api-similarity.ts';

const op = (summary: string, operationId = 'op') => ({ summary, operationId, responses: {} });

const BASE: SpecDocument[] = [
  {
    file: 'events-core-api.openapi.yml',
    doc: {
      paths: {
        '/events': { get: op('List events') },
        '/events/{eventCode}': { get: op('Get an event'), patch: op('Update an event') },
      },
    },
  },
  {
    file: 'event-speakers-api.openapi.yml',
    doc: {
      paths: {
        '/events/{eventCode}/foos': { get: op('List the foos of an event') },
        '/events/{eventCode}/bars': { get: op('List the bars of an event') },
        '/events/{eventCode}/speakers': { get: op('List speakers of an event') },
      },
    },
  },
  {
    file: 'users-api.openapi.yml',
    doc: { paths: { '/users/{username}': { get: op('Get a user') } } },
  },
];

const withAdded = (extra: Record<string, unknown>): SpecDocument[] => [
  ...BASE.slice(0, 1),
  {
    file: BASE[1].file,
    doc: { paths: { ...BASE[1].doc.paths, ...extra } },
  },
  ...BASE.slice(2),
];

describe('operationKey', () => {
  it('blanks parameter names, so a renamed parameter is no new operation', () => {
    expect(operationKey('get', '/events/{code}/x')).toBe(
      operationKey('GET', '/events/{eventCode}/x')
    );
  });
});

describe('addedOperations', () => {
  it('is head minus base by method + normalized path', () => {
    const head = listOperations(
      withAdded({
        '/events/{code}/foos': { get: op('Same, parameter renamed'), post: op('Create a foo') },
      })
    );
    const added = addedOperations(listOperations(BASE), head);
    expect(added.map((o) => `${o.method} ${o.path}`)).toEqual(['POST /events/{code}/foos']);
  });

  it('is empty when nothing was added', () => {
    expect(addedOperations(listOperations(BASE), listOperations(BASE))).toEqual([]);
  });
});

describe('similarity', () => {
  it('ranks a sibling under the same parent above an unrelated path', () => {
    const [fooBars] = addedOperations(
      listOperations(BASE),
      listOperations(
        withAdded({ '/events/{eventCode}/foo-bars': { get: op('List the foo bars of an event') } })
      )
    );
    const base = listOperations(BASE);
    const foos = base.find((o) => o.path === '/events/{eventCode}/foos')!;
    const user = base.find((o) => o.path === '/users/{username}')!;
    expect(similarity(fooBars, foos)).toBeGreaterThan(similarity(fooBars, user));
  });
});

describe('similarity range', () => {
  it('is 1 for an identical operation and never above it', () => {
    const [a] = listOperations(BASE);
    expect(similarity(a, a)).toBe(1);
  });
});

describe('the report for a PR adding GET /events/{eventCode}/foo-bars', () => {
  const base = listOperations(BASE);
  const added = addedOperations(
    base,
    listOperations(
      withAdded({ '/events/{eventCode}/foo-bars': { get: op('List the foo bars of an event') } })
    )
  );

  it('lists the 3 nearest existing operations', () => {
    expect(added).toHaveLength(1);
    const near = nearest(added[0], base).map((n) => `${n.op.method} ${n.op.path}`);
    expect(near).toHaveLength(3);
    expect(near.slice(0, 2).sort()).toEqual([
      'GET /events/{eventCode}/bars',
      'GET /events/{eventCode}/foos',
    ]);
    expect(near).not.toContain('GET /users/{username}');
  });

  it('renders one marked Markdown comment', () => {
    const md = renderMarkdown(added, base);
    expect(md.startsWith(MARKER)).toBe(true);
    expect(md).toContain(
      '#### `GET /events/{eventCode}/foo-bars` (event-speakers-api.openapi.yml)'
    );
    expect(md.match(/^\| \d\.\d\d \| `/gm)).toHaveLength(3);
  });

  it('says so when nothing was added', () => {
    const md = renderMarkdown([], base);
    expect(md.startsWith(MARKER)).toBe(true);
    expect(md).toContain(NO_ADDED);
    expect(NO_ADDED).toContain('no added operations');
  });
});

describe('loadSpecDocumentsAt', () => {
  it('reads the public specs of a git ref, without _shared', () => {
    const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
    const docs = loadSpecDocumentsAt(repoRoot, 'HEAD');
    expect(docs.length).toBeGreaterThan(10);
    expect(docs.some((d) => d.file === 'events-core-api.openapi.yml')).toBe(true);
    expect(docs.some((d) => d.file.startsWith('_'))).toBe(false);
    expect(listOperations(docs).length).toBeGreaterThan(100);
  });
});
