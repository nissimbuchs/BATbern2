/**
 * The MSW server every frontend test runs against (Story 17.0c1). Started, reset and closed in
 * `src/test/setup.ts`.
 *
 * One catch-all handler answers every request:
 *   1. The URL must be an API URL (`/api/...` or `/api/v1/...`, any origin) of an operation in
 *      `docs/api/*.openapi.yml` (not `docs/api/internal/`): the 17.3 `specMatcher` checks path,
 *      method and the declared query parameters with their enum/minimum/maximum. Anything else
 *      is a violation: the request fails as a network error and the test fails afterwards with
 *      the matcher's reason (`SpecViolationError`), so code that swallows errors cannot hide it.
 *   2. An override registered with `api.on(operationId, ...)` answers, else
 *   3. for `listUsers`, `searchUsers`, `listEvents` and `listRegistrations`, the production-shaped
 *      fixture set with real paging (`fixtureDefaults.ts`, Story 17.0d), else
 *   4. the spec example of the lowest 2xx response (an empty body when it has no JSON).
 *
 * A test that talks to something outside our API (S3 presigned URLs, a CDN) adds its own handler
 * with `server.use(http.put('https://bucket.example/...', ...))`. Never for an `/api` URL: that
 * would bypass the validation; use `api.on` instead.
 */
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { buildSpecIndex, matchOperation } from '../contract/specMatcher.ts';
import type { SpecIndex } from '../contract/specMatcher.ts';
import { listOperations, loadSpecFiles, mediaExample } from './openapi.ts';
import type { SpecOperation } from './openapi.ts';
import { harnessState } from './api.ts';
import { fixtureDefault } from './fixtureDefaults.ts';
import type { ApiCall } from './api.ts';

const here = dirname(fileURLToPath(import.meta.url));
export const SPEC_DIR = join(here, '..', '..', '..', '..', 'docs', 'api');

interface Harness {
  index: SpecIndex;
  /** `<file>\0<operationId>` -> operation */
  byId: Map<string, SpecOperation>;
}

let loaded: Harness | undefined;

/** The spec index, parsed once per test file (worker module scope). */
export function harness(): Harness {
  if (!loaded) {
    const files = loadSpecFiles(SPEC_DIR);
    const publicSpecs = [...files.values()].filter((f) => !f.file.startsWith('_'));
    const byId = new Map<string, SpecOperation>();
    for (const op of listOperations(files)) byId.set(`${op.file}\u0000${op.operationId}`, op);
    loaded = { index: buildSpecIndex(publicSpecs), byId };
  }
  return loaded;
}

/** The path parameters of `specPath` (a spec template) in the concrete `apiPath`. */
function pathParams(specPath: string, apiPath: string): Record<string, string> {
  const spec = specPath.split('/').filter(Boolean);
  const concrete = apiPath
    .replace(/^\/api(\/v1)?(?=\/|$)/, '')
    .split('/')
    .filter(Boolean);
  const params: Record<string, string> = {};
  spec.forEach((segment, i) => {
    const m = /^\{(.+)\}$/.exec(segment);
    if (m && concrete[i] !== undefined) params[m[1]] = decodeURIComponent(concrete[i]);
  });
  return params;
}

async function readBody(request: Request): Promise<unknown> {
  const text = await request.text();
  if (!text) return undefined;
  if ((request.headers.get('content-type') ?? '').includes('json')) {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
  return text;
}

function defaultResponse(op: SpecOperation, status?: number): Response {
  const response = status ? op.success.find((r) => r.status === status) : op.success[0];
  const code = status ?? response?.status ?? 200;
  if (response?.media) {
    const example = mediaExample(response.media);
    if (!example.found) {
      throw new Error(`MSW harness: ${op.key} ${code} has no spec example (examples.test.ts)`);
    }
    return HttpResponse.json(structuredClone(example.value) as never, { status: code });
  }
  if (response?.otherContentType) {
    return new HttpResponse(new Uint8Array(0), {
      status: code,
      headers: { 'Content-Type': response.otherContentType },
    });
  }
  return new HttpResponse(null, { status: code });
}

/**
 * A harness failure (spec not loadable, an operation without example) is a violation like a wrong
 * path: recorded for the test and answered as a network error. Thrown inside the resolver, MSW
 * would turn it into a 500 the code under test could swallow.
 */
const catchAll = http.all('*', async ({ request }) => {
  try {
    return await answer(request);
  } catch (e) {
    harnessState.violation(
      `${request.method.toUpperCase()} ${request.url}: MSW harness failed: ${e instanceof Error ? e.message : String(e)}`
    );
    return HttpResponse.error();
  }
});

async function answer(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const method = request.method.toUpperCase();
  const where = `${method} ${url.pathname}${url.search}`;
  if (!/^\/api(\/|$)/.test(url.pathname)) {
    harnessState.violation(
      `${method} ${url.href}: not an /api request; a test talking to another host adds its own handler (server.use)`
    );
    return HttpResponse.error();
  }
  const { index, byId } = harness();
  const match = matchOperation(index, method, `${url.pathname}${url.search}`, {
    bareApiPrefix: true,
  });
  if (!match.ok) {
    harnessState.violation(`${where}: ${match.reason}`);
    return HttpResponse.error();
  }
  const op = byId.get(`${match.file}\u0000${match.operationId}`);
  if (!op) {
    harnessState.violation(`${where}: ${match.operationId} is not indexed by the harness`);
    return HttpResponse.error();
  }
  const call: ApiCall = {
    key: op.key as ApiCall['key'],
    method,
    url,
    query: url.searchParams,
    params: pathParams(op.path, url.pathname),
    body: (await readBody(request.clone())) as ApiCall['body'],
    request,
  };
  harnessState.record(call);
  const override = harnessState.override(op.key);
  if (!override) {
    const fixture = fixtureDefault(op.key, call.query, call.params);
    if (fixture)
      return HttpResponse.json(fixture.body as never, { status: op.success[0]?.status ?? 200 });
    return defaultResponse(op);
  }
  const body = await override.resolver(call);
  if (body instanceof Response) return body;
  const status = override.status ?? op.success[0]?.status ?? 200;
  if (body === undefined) return defaultResponse(op, status);
  return HttpResponse.json(body as never, { status });
}

export const server = setupServer(catchAll);
