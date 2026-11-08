/**
 * OpenAPI reading for the MSW harness (Story 17.0c1): the success responses of every operation in
 * `docs/api/*.openapi.yml`, their examples, and a small JSON-schema validator for those examples.
 *
 * Shared by the harness (`server.ts`), its guard (`examples.test.ts`) and the capture script
 * `scripts/dev/capture-spec-examples.ts`, so it stays plain TypeScript that Node runs with type
 * stripping: no path aliases, no React, `.ts` import extensions, `import type` for types, no
 * TS-only runtime syntax. No new dependency: the validator covers the keywords the specs use.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
type Json = any;

export interface SpecFile {
  /** File name, e.g. `companies-api.openapi.yml`. */
  file: string;
  doc: Json;
}

export interface SpecResponse {
  /** Numeric status, e.g. 200. */
  status: number;
  /** The `application/json` media object, when the response has one. */
  media?: Json;
  /** Another content type, when the response is not JSON (binary, text, CSV ...). */
  otherContentType?: string;
}

export interface SpecOperation {
  file: string;
  method: string;
  path: string;
  operationId: string;
  /**
   * The harness key: the `operationId`, or `<spec stem>/<operationId>` when two specs define the
   * same operationId (e.g. `partners-api/listTopics` and `topics-api/listTopics`).
   */
  key: string;
  /** The 2xx responses, lowest status first. */
  success: SpecResponse[];
}

/** Every public spec plus the `_`-prefixed shared component files, by file name. */
export function loadSpecFiles(dir: string): Map<string, SpecFile> {
  const files = new Map<string, SpecFile>();
  for (const file of readdirSync(dir)
    .filter((f) => /\.openapi\.ya?ml$/.test(f))
    .sort()) {
    files.set(file, { file, doc: parse(readFileSync(join(dir, file), 'utf8')) });
  }
  return files;
}

export const specStem = (file: string) => file.replace(/\.openapi\.ya?ml$/, '');

/** The operations of the public specs (not the `_`-prefixed shared files). */
export function listOperations(files: Map<string, SpecFile>): SpecOperation[] {
  const ops: SpecOperation[] = [];
  for (const { file, doc } of files.values()) {
    if (file.startsWith('_')) continue;
    for (const [path, rawItem] of Object.entries<Json>(doc?.paths ?? {})) {
      const item = resolveRef(files, file, rawItem).node ?? {};
      for (const method of HTTP_METHODS) {
        const op = item[method];
        if (!op) continue;
        const success: SpecResponse[] = [];
        for (const [status, rawResponse] of Object.entries<Json>(op.responses ?? {})) {
          if (!/^2\d\d$/.test(status)) continue;
          const response = resolveRef(files, file, rawResponse).node ?? {};
          const content = response.content ?? {};
          const media = content['application/json'];
          const other = Object.keys(content).find((t) => t !== 'application/json');
          success.push({
            status: Number(status),
            media,
            otherContentType: media ? undefined : other,
          });
        }
        success.sort((a, b) => a.status - b.status);
        const operationId: string = op.operationId ?? `${method.toUpperCase()} ${path}`;
        ops.push({
          file,
          method: method.toUpperCase(),
          path,
          operationId,
          key: operationId,
          success,
        });
      }
    }
  }
  const count = new Map<string, number>();
  for (const op of ops) count.set(op.operationId, (count.get(op.operationId) ?? 0) + 1);
  for (const op of ops) {
    if ((count.get(op.operationId) ?? 0) > 1) op.key = `${specStem(op.file)}/${op.operationId}`;
  }
  return ops;
}

/** The example of a JSON media object: `example`, else the first `examples` entry's value. */
export function mediaExample(media: Json): { found: boolean; value?: unknown } {
  if (!media || typeof media !== 'object') return { found: false };
  if (media.example !== undefined) return { found: true, value: media.example };
  if (media.examples && typeof media.examples === 'object') {
    const first = Object.values<Json>(media.examples)[0];
    if (first && first.value !== undefined) return { found: true, value: first.value };
  }
  return { found: false };
}

/** Every example of a JSON media object, by name (`example` is named `example`). */
export function mediaExamples(media: Json): Array<{ name: string; value: unknown }> {
  const out: Array<{ name: string; value: unknown }> = [];
  if (!media || typeof media !== 'object') return out;
  if (media.example !== undefined) out.push({ name: 'example', value: media.example });
  for (const [name, ex] of Object.entries<Json>(media.examples ?? {})) {
    if (ex && ex.value !== undefined) out.push({ name, value: ex.value });
  }
  return out;
}

// ---------------------------------------------------------------------------------------------
// $ref resolution (local and cross-file, e.g. `./_shared.openapi.yml#/components/schemas/X`)
// ---------------------------------------------------------------------------------------------

export interface Resolved {
  node: Json;
  /** The file the node lives in (a cross-file $ref switches it). */
  file: string;
}

export function resolveRef(
  files: Map<string, SpecFile>,
  file: string,
  node: Json,
  depth = 0
): Resolved {
  if (!node || typeof node !== 'object' || typeof node.$ref !== 'string' || depth > 20) {
    return { node, file };
  }
  const ref: string = node.$ref;
  const hash = ref.indexOf('#');
  const target = hash < 0 ? ref : ref.slice(0, hash);
  const pointer = hash < 0 ? '' : ref.slice(hash + 1);
  const targetFile = target ? target.replace(/^\.\//, '') : file;
  const doc = files.get(targetFile)?.doc;
  if (!doc) return { node: undefined, file: targetFile };
  let current = doc;
  for (const part of pointer.split('/').filter(Boolean)) {
    current = current?.[part.replace(/~1/g, '/').replace(/~0/g, '~')];
  }
  return resolveRef(files, targetFile, current, depth + 1);
}

// ---------------------------------------------------------------------------------------------
// Validation: the JSON-schema subset of OpenAPI 3.0 / 3.1 the specs use
// ---------------------------------------------------------------------------------------------

const FORMATS: Record<string, RegExp> = {
  'date-time': /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/i,
  date: /^\d{4}-\d{2}-\d{2}$/,
  uuid: /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
  email: /^[^\s@]+@[^\s@]+$/,
};

function typeOf(value: unknown): string {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  if (typeof value === 'number') return Number.isInteger(value) ? 'integer' : 'number';
  return typeof value;
}

function typeMatches(expected: string, actual: string): boolean {
  return expected === actual || (expected === 'number' && actual === 'integer');
}

/**
 * Validation errors of `value` against `schema` (as found in spec `file`); empty when valid.
 * Each error names the JSON path, e.g. `$.data[0].name: expected string, got null`.
 */
export function validateAgainstSchema(
  files: Map<string, SpecFile>,
  file: string,
  schema: Json,
  value: unknown,
  path = '$'
): string[] {
  const resolved = resolveRef(files, file, schema);
  const s = resolved.node;
  const f = resolved.file;
  if (s === undefined || s === null || s === true || typeof s !== 'object') return [];
  const errors: string[] = [];
  const types: string[] = Array.isArray(s.type) ? s.type : s.type ? [s.type] : [];
  if (value === null) {
    // OpenAPI 3.0 `nullable: true` (also next to `allOf`), 3.1 `type: [x, 'null']`, or null in the enum.
    if (s.nullable === true || types.includes('null') || (s.enum ?? []).includes(null)) return [];
  }

  if (Array.isArray(s.allOf)) {
    for (const sub of s.allOf) errors.push(...validateAgainstSchema(files, f, sub, value, path));
  }
  if (Array.isArray(s.anyOf)) {
    const ok = s.anyOf.some(
      (sub: Json) => validateAgainstSchema(files, f, sub, value, path).length === 0
    );
    if (!ok) errors.push(`${path}: matches none of anyOf`);
  }
  if (Array.isArray(s.oneOf)) {
    const matching = s.oneOf.filter(
      (sub: Json) => validateAgainstSchema(files, f, sub, value, path).length === 0
    ).length;
    if (matching === 0) errors.push(`${path}: matches none of oneOf`);
  }

  const actual = typeOf(value);
  if (value === null) {
    if (types.length > 0) errors.push(`${path}: expected ${types.join('|')}, got null`);
    return errors;
  }
  if (types.length > 0 && !types.some((t) => typeMatches(t, actual))) {
    errors.push(`${path}: expected ${types.join('|')}, got ${actual}`);
    return errors;
  }
  if (Array.isArray(s.enum) && !s.enum.some((e: unknown) => e === value)) {
    errors.push(`${path}: ${JSON.stringify(value)} is not in the enum`);
  }
  if (s.const !== undefined && s.const !== value) {
    errors.push(`${path}: expected the constant ${JSON.stringify(s.const)}`);
  }

  if (typeof value === 'string') {
    if (typeof s.minLength === 'number' && value.length < s.minLength) {
      errors.push(`${path}: shorter than ${s.minLength}`);
    }
    if (typeof s.maxLength === 'number' && value.length > s.maxLength) {
      errors.push(`${path}: longer than ${s.maxLength}`);
    }
    if (typeof s.pattern === 'string' && !new RegExp(s.pattern, 'u').test(value)) {
      errors.push(`${path}: does not match the pattern ${s.pattern}`);
    }
    const format = FORMATS[s.format];
    if (format && !format.test(value)) errors.push(`${path}: not a valid ${s.format}`);
  }
  if (typeof value === 'number') {
    if (typeof s.minimum === 'number' && value < s.minimum) {
      errors.push(`${path}: below the minimum ${s.minimum}`);
    }
    if (typeof s.maximum === 'number' && value > s.maximum) {
      errors.push(`${path}: above the maximum ${s.maximum}`);
    }
  }
  if (Array.isArray(value)) {
    if (typeof s.minItems === 'number' && value.length < s.minItems) {
      errors.push(`${path}: fewer than ${s.minItems} items`);
    }
    if (typeof s.maxItems === 'number' && value.length > s.maxItems) {
      errors.push(`${path}: more than ${s.maxItems} items`);
    }
    if (s.items) {
      value.forEach((item, i) =>
        errors.push(...validateAgainstSchema(files, f, s.items, item, `${path}[${i}]`))
      );
    }
  }
  if (actual === 'object') {
    const obj = value as Record<string, unknown>;
    for (const name of Array.isArray(s.required) ? s.required : []) {
      if (!(name in obj)) errors.push(`${path}: missing required property '${name}'`);
    }
    const props = s.properties ?? {};
    for (const [name, v] of Object.entries(obj)) {
      if (name in props) {
        errors.push(...validateAgainstSchema(files, f, props[name], v, `${path}.${name}`));
      } else if (s.additionalProperties === false) {
        errors.push(`${path}: property '${name}' is not allowed`);
      } else if (s.additionalProperties && typeof s.additionalProperties === 'object') {
        errors.push(
          ...validateAgainstSchema(files, f, s.additionalProperties, v, `${path}.${name}`)
        );
      }
    }
  }
  return errors;
}
