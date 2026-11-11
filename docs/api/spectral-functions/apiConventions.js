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
 *                   and OPTIONS only the last segment counts (`GET …/workflow/status` is a read).
 *                   Exempt: the ADR-013 section 1 action shape `POST /{id}/{verb}`, a verb as the
 *                   last segment of a POST directly after an instance identifier (a parameter or
 *                   `me`). The list only tells a verb from a sub-resource noun; it permits nothing.
 *   internal        the path does not start with `/internal`
 *   events-literal  the second segment of an `/events/...` path is a parameter
 *   uuid            no path parameter has `format: uuid`, or is a string named `id` / `*Id`
 *                   without a format (an unformatted UUID such as `uploadId`)
 *   personal-data   no path parameter is named `email` or ends in `Email` (case-insensitive):
 *                   personal data never goes in a path (ADR-003 amendment)
 *   speaker-key     one speaker key (Story 4.10): a path parameter directly after
 *                   `/events/{eventCode}/speakers/` is `{speakerId}` with `format: uuid` (the
 *                   speaker pool entry, an owned child of its event), and there is no second
 *                   speaker collection (`speaker-pool`, `speakers/pool`). A session's speaker
 *                   (`/sessions/{slug}/speakers/{username}`) is a user's assignment, not this key.
 */
const METHODS = ['get', 'put', 'post', 'patch', 'delete', 'head', 'options'];
const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*(\.[a-z0-9]+)?$/;

const isParam = (segment) => /^\{[^}]+\}$/.test(segment);

function literalSegments(path) {
  return path.split('/').filter((s) => s.length > 0 && !isParam(s));
}

/** ADR-013 section 1 row 4: `POST /{id}/{verb}`, the verb directly after an instance identifier
 *  (a path parameter, or `me` for the caller's own resource). Only the verb position is checked
 *  here; the caller asks whether the last segment is a verb. */
function isAdr013Action(method, segments) {
  if (method !== 'post' || segments.length < 2) return false;
  const previous = segments[segments.length - 2];
  return isParam(previous) || previous === 'me';
}

const READ_METHODS = new Set(['get', 'head', 'options']);

/** Path-level violation text, or null; the same for every operation of the path. */
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
    const lastIsAction = isAdr013Action(method, segments);
    const bad = segments.filter(
      (s, i) => !isParam(s) && isVerb(s) && !(lastIsAction && i === segments.length - 1)
    );
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

function personalDataParams(item, operation) {
  return pathParams(item, operation)
    .filter((p) => /email$/i.test(p.name))
    .map((p) => p.name);
}

/** Story 4.10: the speaker key under `/events/{eventCode}/speakers/`, or null when it holds. */
function speakerKeyViolation(path, item, operation) {
  const segments = path.split('/').filter((s) => s.length > 0);
  const problems = [];
  if (segments.includes('speaker-pool')) problems.push('second speaker collection `speaker-pool`');
  segments.forEach((s, i) => {
    if (s === 'speakers' && segments[i + 1] === 'pool') {
      problems.push('second speaker collection `speakers/pool`');
    }
  });
  const isEventSpeakers =
    segments[0] === 'events' && isParam(segments[1] || '') && segments[2] === 'speakers';
  const key = segments[3];
  if (isEventSpeakers && key && isParam(key)) {
    const name = key.slice(1, -1);
    const param = pathParams(item, operation).find((p) => p.name === name);
    const format = param && param.schema ? param.schema.format : undefined;
    if (name !== 'speakerId' || format !== 'uuid') {
      problems.push(`speaker key ${key} (expected {speakerId}, format: uuid)`);
    }
  }
  return problems.length
    ? `one speaker key under /events/{eventCode}/speakers (Story 4.10): ${problems.join('; ')}`
    : null;
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
    } else if (check === 'speaker-key') {
      message = speakerKeyViolation(path, item, operation);
    } else if (check === 'personal-data') {
      const names = personalDataParams(item, operation);
      if (names.length) {
        message = `personal data in a path parameter: ${names.join(', ')} (never in a path, ADR-003 2026-11 amendment)`;
      }
    } else {
      message = pathViolation(path, method, check, options && options.verbs);
    }
    if (message) results.push({ message, path: [...context.path, method] });
  }
  return results;
}
