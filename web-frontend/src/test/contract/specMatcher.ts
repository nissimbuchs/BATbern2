/**
 * OpenAPI spec matcher (Story 17.3).
 *
 * Answers one question: is this HTTP method + URL defined by a spec in `docs/api/`? It is shared
 * by the CI path check (`scripts/check-api-paths.ts`) and the MSW request validator (17.0c), so
 * it stays plain TypeScript: no path aliases, no React, `.ts` import extensions only, and no
 * TS-only runtime syntax (Node 22 runs it with type stripping).
 *
 * URL conventions:
 * - A leading `/api/v1` and an origin (`http://host`) are stripped; a trailing `/` too. With
 *   `{ bareApiPrefix: true }` (the MSW harness, 17.0c1) a bare `/api` is stripped as well: in tests
 *   `apiClient` keeps its initial baseURL `/api`, while the runtime base is `<host>/api/v1`. The CI
 *   path check leaves it off, so a literal `/api/...` in code is still reported there.
 * - `{*}` stands for a value the caller could not resolve (a `${...}` in a template literal).
 *   A segment that is only `{*}` matches any spec segment; `{*}` inside a segment matches any
 *   characters there. In the query, a `{*}` name or value is not checked.
 * - A path parameter in the spec is a single-segment wildcard: a literal `me` or `_global`
 *   matches `{username}` or `{eventCode}`. Parameter patterns are not checked.
 * - A literal query parameter name the operation does not declare is a mismatch, and so is a
 *   literal value outside a declared `enum`, `minimum` or `maximum`.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';

export const WILDCARD = '{*}';

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];

export interface SpecDocument {
  file: string;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  doc: any;
}

export interface QueryParamSpec {
  name: string;
  enum?: string[];
  minimum?: number;
  maximum?: number;
  /** `type: array`: a value may be a comma list, each item checked against the enum. */
  isArray: boolean;
  /** A free-form object parameter (form style, exploded): any query name is accepted. */
  freeForm: boolean;
}

export interface SpecOperation {
  file: string;
  path: string;
  method: string;
  operationId: string;
  segments: string[];
  queryParams: QueryParamSpec[];
}

export interface SpecIndex {
  operations: SpecOperation[];
}

export interface MatchOptions {
  /** Also strip a bare leading `/api` (the test baseURL of `apiClient`). */
  bareApiPrefix?: boolean;
}

export type MatchResult =
  | { ok: true; file: string; operationId: string; path: string }
  | { ok: false; kind: 'path' | 'method' | 'query'; reason: string };

/** Reads every `*.openapi.yml` in `dir` except the `_`-prefixed shared files. */
export function loadSpecDocuments(dir: string): SpecDocument[] {
  return readdirSync(dir)
    .filter((f) => /\.openapi\.ya?ml$/.test(f) && !f.startsWith('_'))
    .sort()
    .map((file) => ({ file, doc: parse(readFileSync(join(dir, file), 'utf8')) }));
}

export function loadSpecIndex(dir: string): SpecIndex {
  return buildSpecIndex(loadSpecDocuments(dir));
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function resolveRef(doc: any, node: any, depth = 0): any {
  if (!node || typeof node !== 'object' || typeof node.$ref !== 'string' || depth > 10) {
    return node;
  }
  const ref: string = node.$ref;
  if (!ref.startsWith('#/')) {
    return {}; // cross-file reference: unconstrained
  }
  let target = doc;
  for (const part of ref.slice(2).split('/')) {
    target = target?.[part.replace(/~1/g, '/').replace(/~0/g, '~')];
  }
  return resolveRef(doc, target, depth + 1);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function toQueryParam(doc: any, param: any): QueryParamSpec {
  const schema = resolveRef(doc, param.schema) ?? {};
  const items = schema.type === 'array' ? (resolveRef(doc, schema.items) ?? {}) : schema;
  const enumValues = Array.isArray(items.enum) ? items.enum.map(String) : undefined;
  const freeForm =
    schema.type === 'object' && param.style !== 'deepObject' && param.explode !== false;
  return {
    name: param.name,
    enum: enumValues,
    minimum: typeof items.minimum === 'number' ? items.minimum : undefined,
    maximum: typeof items.maximum === 'number' ? items.maximum : undefined,
    isArray: schema.type === 'array',
    freeForm,
  };
}

export function buildSpecIndex(documents: SpecDocument[]): SpecIndex {
  const operations: SpecOperation[] = [];
  for (const { file, doc } of documents) {
    const paths = doc?.paths ?? {};
    for (const [rawPath, rawItem] of Object.entries(paths)) {
      const item = resolveRef(doc, rawItem) ?? {};
      const pathLevel = Array.isArray(item.parameters) ? item.parameters : [];
      const path = normalizePath(rawPath);
      for (const method of HTTP_METHODS) {
        const op = item[method];
        if (!op) continue;
        const merged = new Map<string, QueryParamSpec>();
        for (const raw of [...pathLevel, ...(Array.isArray(op.parameters) ? op.parameters : [])]) {
          const param = resolveRef(doc, raw);
          if (typeof param?.in !== 'string' || typeof param.name !== 'string') {
            // A parameter this matcher cannot resolve (a cross-file $ref) might be a query
            // parameter: leave the operation's query unconstrained rather than guess.
            merged.set('\u0000unresolved', { name: '', isArray: false, freeForm: true });
          } else if (param.in === 'query') {
            merged.set(param.name, toQueryParam(doc, param));
          }
        }
        operations.push({
          file,
          path,
          method: method.toUpperCase(),
          operationId: op.operationId ?? `${method.toUpperCase()} ${rawPath}`,
          segments: splitSegments(path),
          queryParams: [...merged.values()],
        });
      }
    }
  }
  return { operations };
}

function normalizePath(path: string, options: MatchOptions = {}): string {
  let p = path.replace(/^https?:\/\/[^/]+/, '');
  p = p.replace(/^\/api\/v1(?=\/|$)/, '');
  if (options.bareApiPrefix) p = p.replace(/^\/api(?=\/|$)/, '');
  if (p.length > 1) p = p.replace(/\/+$/, '');
  if (!p.startsWith('/')) p = `/${p}`;
  return p;
}

function splitSegments(path: string): string[] {
  return path.split('/').filter((s) => s.length > 0);
}

function escapeRegex(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const isSpecParam = (segment: string) => /^\{[^}]+\}$/.test(segment);

/**
 * Score of a code segment against a spec segment, or -1 when they cannot match.
 * Higher is more specific: an exact literal beats a parameter.
 */
function segmentScore(code: string, spec: string): number {
  if (code === WILDCARD) return 1;
  if (isSpecParam(spec)) return code.length > 0 ? 1 : -1;
  if (code === spec) return 3;
  if (code.includes(WILDCARD)) {
    const re = new RegExp(`^${code.split(WILDCARD).map(escapeRegex).join('.*')}$`);
    return re.test(spec) ? 2 : -1;
  }
  return -1;
}

function pathScore(codeSegments: string[], op: SpecOperation): number {
  if (codeSegments.length !== op.segments.length) return -1;
  let total = 0;
  for (let i = 0; i < codeSegments.length; i++) {
    const s = segmentScore(codeSegments[i], op.segments[i]);
    if (s < 0) return -1;
    total += s;
  }
  return total;
}

function parseQuery(query: string): Array<{ name: string; value: string | undefined }> {
  return query
    .split('&')
    .filter((part) => part.length > 0)
    .map((part) => {
      const eq = part.indexOf('=');
      const name = eq < 0 ? part : part.slice(0, eq);
      const value = eq < 0 ? undefined : part.slice(eq + 1);
      return { name: safeDecode(name), value: value === undefined ? undefined : safeDecode(value) };
    });
}

function safeDecode(s: string): string {
  try {
    return decodeURIComponent(s.replace(/\+/g, ' '));
  } catch {
    return s;
  }
}

/** Returns a reason when the literal query does not fit the operation, else undefined. */
function checkQuery(query: string, op: SpecOperation): string | undefined {
  const freeForm = op.queryParams.some((p) => p.freeForm);
  for (const { name, value } of parseQuery(query)) {
    if (name.includes(WILDCARD)) continue;
    const param = op.queryParams.find((p) => p.name === name);
    if (!param) {
      if (freeForm) continue;
      return `query parameter '${name}' is not declared by ${op.operationId}`;
    }
    if (value === undefined || value.includes(WILDCARD)) continue;
    const values = param.isArray ? value.split(',') : [value];
    for (const v of values) {
      if (param.enum && !param.enum.includes(v)) {
        return `query parameter '${name}=${v}' is not in the enum [${param.enum.join(', ')}] of ${op.operationId}`;
      }
      const n = Number(v);
      if (v.trim() !== '' && !Number.isNaN(n)) {
        if (param.maximum !== undefined && n > param.maximum) {
          return `query parameter '${name}=${v}' exceeds the maximum ${param.maximum} of ${op.operationId}`;
        }
        if (param.minimum !== undefined && n < param.minimum) {
          return `query parameter '${name}=${v}' is below the minimum ${param.minimum} of ${op.operationId}`;
        }
      }
    }
  }
  return undefined;
}

/** Is `method` + `url` defined by a spec? */
export function matchOperation(
  index: SpecIndex,
  method: string,
  url: string,
  options: MatchOptions = {}
): MatchResult {
  const upper = method.toUpperCase();
  const q = url.indexOf('?');
  const rawPath = q < 0 ? url : url.slice(0, q);
  const query = q < 0 ? '' : url.slice(q + 1).replace(/#.*$/, '');
  const path = normalizePath(rawPath, options);
  const codeSegments = splitSegments(path);

  const candidates = index.operations
    .map((op) => ({ op, score: pathScore(codeSegments, op) }))
    .filter((c) => c.score >= 0);
  if (candidates.length === 0) {
    return { ok: false, kind: 'path', reason: `no spec defines the path ${path}` };
  }
  const withMethod = candidates
    .filter((c) => c.op.method === upper)
    .sort((a, b) => b.score - a.score);
  if (withMethod.length === 0) {
    const methods = [...new Set(candidates.map((c) => c.op.method))].sort().join(', ');
    return {
      ok: false,
      kind: 'method',
      reason: `method ${upper} is not defined for ${path} (the spec has ${methods})`,
    };
  }
  let firstReason: string | undefined;
  for (const { op } of withMethod) {
    const reason = query ? checkQuery(query, op) : undefined;
    if (!reason) return { ok: true, file: op.file, operationId: op.operationId, path: op.path };
    firstReason ??= reason;
  }
  return { ok: false, kind: 'query', reason: firstReason ?? 'query mismatch' };
}
