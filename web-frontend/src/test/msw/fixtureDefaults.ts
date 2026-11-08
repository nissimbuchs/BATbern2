/**
 * Fixture-backed default answers of the MSW harness (Story 17.0d).
 *
 * Without an `api.on` override, the people, event and registration lists answer from the
 * production-shaped fixture set (`src/test/fixtures/`) instead of the spec example, with real
 * paging from `page`/`limit`, so every test sees unicode names, namesakes and more rows than one
 * page. Every other operation keeps its spec example (`server.ts`).
 *
 * The search is a stand-in for the backend's, not a re-implementation of its ranking: both sides
 * folded approximately like the SQL `batbern_fold` (lower case, accents removed, `ae/oe/ue` as `a/o/u`, `-`, `'`
 * and `_` as a space), every word must occur in one field (users-api `listUsers.search`). A test
 * that depends on ranking or on a filter this stand-in ignores sets `api.on`.
 */
import { people, toSearchResult } from '../fixtures/people.ts';
import type { UserResponse } from '../fixtures/people.ts';
import { events } from '../fixtures/events.ts';
import { registrations } from '../fixtures/registrations.ts';
import type { OperationKey, ResponseBody } from './api.ts';

type Pagination = NonNullable<ResponseBody<'listUsers', 200>['pagination']>;

/**
 * Approximates SQL `batbern_fold` (shared-kernel `db/search/batbern_fold.sql`): lower case, accents
 * removed like Postgres `unaccent` (including the letters NFD does not decompose: ß, ø, æ, œ, đ, ł,
 * þ), `-`, `'`, `’` and `_` as a space, then `ae/oe/ue` as `a/o/u`. Letters outside these that
 * `unaccent` maps but NFD does not are not covered.
 */
const UNACCENT_EXTRA: Array<[RegExp, string]> = [
  [/ß/g, 'ss'],
  [/ø/g, 'o'],
  [/æ/g, 'ae'],
  [/œ/g, 'oe'],
  [/đ/g, 'd'],
  [/ł/g, 'l'],
  [/þ/g, 'th'],
];

export function searchFold(value: string): string {
  let folded = value.toLowerCase();
  for (const [from, to] of UNACCENT_EXTRA) folded = folded.replace(from, to);
  return folded
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/[-'’_]/g, ' ')
    .replace(/ae/g, 'a')
    .replace(/oe/g, 'o')
    .replace(/ue/g, 'u')
    .replace(/\s+/g, ' ')
    .trim();
}

/** True when every word of `query` occurs in one of the user's searchable fields. */
export function matchesUserSearch(user: UserResponse, query: string): boolean {
  const terms = searchFold(query).split(' ').filter(Boolean);
  const fields = [
    user.id,
    user.firstName,
    user.lastName,
    user.email,
    user.companyId ?? '',
    ...(user.additionalEmails ?? []).map((e) => e.email),
  ].map(searchFold);
  return terms.every((term) => fields.some((field) => field.includes(term)));
}

function positiveInt(raw: string | null, fallback: number): number {
  const n = raw === null ? NaN : Number(raw);
  return Number.isInteger(n) && n > 0 ? n : fallback;
}

/** One page of `rows`, with the backend's 1-based pagination metadata. */
export function paginate<T>(
  rows: readonly T[],
  query: URLSearchParams,
  defaultLimit = 20
): { data: T[]; pagination: Pagination } {
  const page = positiveInt(query.get('page'), 1);
  const limit = Math.min(positiveInt(query.get('limit'), defaultLimit), 100);
  const totalItems = rows.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / limit));
  return {
    data: rows.slice((page - 1) * limit, page * limit),
    pagination: {
      page,
      limit,
      totalItems,
      totalPages,
      hasNext: page < totalPages,
      hasPrev: page > 1,
    },
  };
}

function roleFilter(query: URLSearchParams): string | undefined {
  const raw = query.get('filter');
  if (!raw) return undefined;
  try {
    const parsed = JSON.parse(raw) as { role?: unknown };
    return typeof parsed.role === 'string' ? parsed.role : undefined;
  } catch {
    return undefined;
  }
}

function listUsers(query: URLSearchParams): ResponseBody<'listUsers', 200> {
  const search = query.get('search')?.trim();
  const role = roleFilter(query);
  const rows = people
    .filter((u) => !search || matchesUserSearch(u, search))
    .filter((u) => !role || u.roles.includes(role as UserResponse['roles'][number]));
  return paginate(rows, query);
}

function searchUsers(query: URLSearchParams): ResponseBody<'searchUsers', 200> {
  const q = query.get('query') ?? '';
  const role = query.get('role');
  const limit = Math.min(positiveInt(query.get('limit'), 20), 100);
  return people
    .filter((u) => matchesUserSearch(u, q))
    .filter((u) => !role || u.roles.includes(role as UserResponse['roles'][number]))
    .slice(0, limit)
    .map(toSearchResult);
}

function listEvents(query: URLSearchParams): ResponseBody<'listEvents', 200> {
  const includeArchived = query.get('includeArchived') === 'true';
  const rows = events().filter((e) => includeArchived || e.workflowState !== 'ARCHIVED');
  return paginate(rows, query);
}

function listRegistrations(
  query: URLSearchParams,
  params: Record<string, string>
): ResponseBody<'listRegistrations', 200> {
  return paginate(registrations(params.eventCode), query);
}

const DEFAULTS: Partial<
  Record<OperationKey, (query: URLSearchParams, params: Record<string, string>) => unknown>
> = {
  listUsers,
  searchUsers,
  listEvents,
  listRegistrations,
};

/** The operations that answer from the fixture set by default. */
export const FIXTURE_BACKED_OPERATIONS = Object.keys(DEFAULTS) as OperationKey[];

/**
 * The fixture-backed default body of operation `key`, or `undefined` when the operation answers
 * with its spec example.
 */
export function fixtureDefault(
  key: string,
  query: URLSearchParams,
  params: Record<string, string>
): { body: unknown } | undefined {
  const build = DEFAULTS[key as OperationKey];
  return build ? { body: build(query, params) } : undefined;
}
