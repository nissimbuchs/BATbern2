/**
 * Spectral function for the BATbern API convention rules (Story 17.4), see `../.spectral.yaml`.
 *
 * Given a path item (`$.paths[*]`), it returns at most one result per operation, at
 * `paths/<path>/<method>`, so the lint wrapper (`web-frontend/scripts/api-lint.ts`) keys every
 * finding by spec file + HTTP method + path + rule. Plain JavaScript without imports: Spectral
 * bundles it from this directory, where no node_modules exist.
 *
 * options.check:
 *   kebab           every literal path segment is kebab-case (optional `.ext` suffix allowed)
 *   verb            no literal path segment is in options.verbs (`.ext` ignored); for GET, HEAD
 *                   and OPTIONS only the last segment counts (`GET /publish/status` is a read)
 *   internal        the path does not start with `/internal`
 *   events-literal  the second segment of an `/events/...` path is a parameter
 *   uuid            no path parameter has `format: uuid`, or is a string named `id` / `*Id`
 *                   without a format (an unformatted UUID such as `uploadId`)
 */
const METHODS = ['get', 'put', 'post', 'patch', 'delete', 'head', 'options'];
const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+)?$/;

const isParam = (segment) => /^\{[^}]+\}$/.test(segment);

function literalSegments(path) {
  return path.split('/').filter((s) => s.length > 0 && !isParam(s));
}

/** Path-level violation text, or null; the same for every operation of the path. */
const READ_METHODS = new Set(['get', 'head', 'options']);

function pathViolation(path, method, check, verbs) {
  const segments = path.split('/').filter((s) => s.length > 0);
  if (check === 'kebab') {
    const bad = literalSegments(path).filter((s) => !KEBAB.test(s));
    return bad.length ? `path segment(s) not kebab-case: ${bad.join(', ')}` : null;
  }
  if (check === 'verb') {
    const isVerb = (s) => (verbs || []).includes(s.replace(/\.[a-z0-9]+$/, ''));
    if (READ_METHODS.has(method)) {
      const last = segments[segments.length - 1];
      return last && !isParam(last) && isVerb(last) ? `verb as the last path segment: ${last}` : null;
    }
    const bad = literalSegments(path).filter(isVerb);
    return bad.length ? `verb path segment(s) in a write operation: ${bad.join(', ')}` : null;
  }
  if (check === 'internal') {
    return segments[0] === 'internal' ? '/internal path in a public spec' : null;
  }
  if (check === 'events-literal') {
    return segments[0] === 'events' && segments.length > 1 && !isParam(segments[1])
      ? `literal segment directly under /events/: ${segments[1]}`
      : null;
  }
  return null;
}

/** Path parameters of an operation: an operation parameter replaces the path-level one with
 *  the same name + in (OAS 3). */
function pathParams(item, operation) {
  const merged = new Map();
  for (const list of [item.parameters, operation.parameters]) {
    for (const p of Array.isArray(list) ? list : []) {
      if (p && typeof p.name === 'string') merged.set(`${p.in}:${p.name}`, p);
    }
  }
  return [...merged.values()].filter((p) => p.in === 'path');
}

function isUuidParam(p) {
  const schema = p.schema || {};
  if (schema.format === 'uuid') return true;
  const isString = schema.type === undefined || schema.type === 'string';
  return isString && !schema.format && /^(id|.*Id)$/.test(p.name);
}

function uuidParams(item, operation) {
  return pathParams(item, operation)
    .filter(isUuidParam)
    .map((p) => p.name);
}

export default function apiConventions(item, options, context) {
  if (!item || typeof item !== 'object') return [];
  const path = context.path[context.path.length - 1];
  if (typeof path !== 'string') return [];
  const check = options && options.check;
  const results = [];
  for (const method of METHODS) {
    const operation = item[method];
    if (!operation || typeof operation !== 'object') continue;
    let message = null;
    if (check === 'uuid') {
      const names = uuidParams(item, operation);
      if (names.length) {
        message = `UUID path parameter(s): ${names.join(', ')} (allowed only per the ADR-003 2026-11 amendment)`;
      }
    } else {
      message = pathViolation(path, method, check, options && options.verbs);
    }
    if (message) results.push({ message, path: [...context.path, method] });
  }
  return results;
}
